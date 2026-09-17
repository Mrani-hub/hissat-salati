package ma.hissatsalati;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.FileProvider;
import androidx.core.content.pm.PackageInfoCompat;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mise à jour de l'application depuis GitHub Releases.
 *  - check()    : interroge l'API GitHub, lit l'étiquette de la dernière Release
 *                 (v<versionName>-<versionCode>) et la compare au build installé.
 *  - download() : récupère l'APK signé avec DownloadManager dans le dossier privé
 *                 de l'application et suit l'avancement.
 *  - install()  : ouvre l'installateur Android sur le fichier téléchargé.
 * Les adresses sont fixes dans le code : la page web ne peut pas en imposer une autre.
 */
public final class Updater {
    static final String API_LATEST = "https://api.github.com/repos/Mrani-hub/hissat-salati/releases/latest";
    static final String APK_URL = "https://github.com/Mrani-hub/hissat-salati/releases/latest/download/hissat-salati.apk";
    static final String APK_NAME = "hissat-salati.apk";
    /** Étiquette publiée par le workflow, ex. v1.4-5 : on n'accepte que ce format. */
    private static final Pattern TAG = Pattern.compile("^v([0-9A-Za-z.]{1,20})-(\\d{1,9})$");
    private static final int MAX_BODY = 256 * 1024;
    private static final int TIMEOUT_MS = 10000;

    interface OnResult { void onResult(JSONObject r); }
    interface OnDownload {
        void onProgress(int pct);
        void onDone(File apk);
        void onError(String why);
    }

    private Updater() {}

    static String currentName(Context c) {
        try {
            PackageInfo p = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return p.versionName == null ? "?" : p.versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    static long currentCode(Context c) {
        try {
            return PackageInfoCompat.getLongVersionCode(
                    c.getPackageManager().getPackageInfo(c.getPackageName(), 0));
        } catch (Exception e) {
            return 0;
        }
    }

    /** Interroge GitHub sur un fil séparé (jamais sur le fil d'interface). */
    static void check(Context c, OnResult cb) {
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            JSONObject out = new JSONObject();
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(API_LATEST).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setRequestProperty("User-Agent", "hissat-salati/" + currentName(app));
                int code = conn.getResponseCode();
                if (code != 200) throw new Exception("HTTP " + code);
                JSONObject rel = new JSONObject(read(conn.getInputStream()));
                Matcher m = TAG.matcher(rel.optString("tag_name", ""));
                if (!m.matches()) throw new Exception("étiquette inconnue");
                String latest = m.group(1);
                long latestCode = Long.parseLong(m.group(2));
                long cur = currentCode(app);
                out.put("ok", true);
                out.put("current", currentName(app));
                out.put("currentCode", cur);
                out.put("latest", latest);
                out.put("latestCode", latestCode);
                out.put("available", latestCode > cur);
            } catch (Exception e) {
                try {
                    out.put("ok", false);
                    out.put("error", e.getMessage() == null ? "erreur" : e.getMessage());
                } catch (Exception ignored) {}
            } finally {
                if (conn != null) conn.disconnect();
            }
            cb.onResult(out);
        }).start();
    }

    private static String read(InputStream in) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        try {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) > 0) {
                sb.append(buf, 0, n);
                if (sb.length() > MAX_BODY) throw new Exception("réponse trop longue");
            }
            return sb.toString();
        } finally {
            r.close();
        }
    }

    /** Télécharge l'APK, appelle onProgress toutes les 500 ms, puis onDone ou onError. */
    static void download(Context c, OnDownload cb) {
        final Context app = c.getApplicationContext();
        File dir = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) { cb.onError("stockage indisponible"); return; }
        final File target = new File(dir, APK_NAME);
        if (target.exists() && !target.delete()) { cb.onError("ancien fichier"); return; }

        final DownloadManager dm = (DownloadManager) app.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) { cb.onError("DownloadManager"); return; }
        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(APK_URL))
                .setTitle(app.getString(R.string.app_name))
                .setDescription(app.getString(R.string.update_downloading))
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, APK_NAME);
        final long id;
        try {
            id = dm.enqueue(req);
        } catch (Exception e) {
            cb.onError(e.getMessage() == null ? "enqueue" : e.getMessage());
            return;
        }

        final Handler h = new Handler(Looper.getMainLooper());
        h.postDelayed(new Runnable() {
            @Override public void run() {
                Cursor cur = null;
                try {
                    cur = dm.query(new DownloadManager.Query().setFilterById(id));
                    if (cur == null || !cur.moveToFirst()) { cb.onError("annulé"); return; }
                    int status = cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                    long done = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    long total = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        cb.onProgress(100);
                        cb.onDone(target);
                        return;
                    }
                    if (status == DownloadManager.STATUS_FAILED) {
                        int reason = cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                        cb.onError("code " + reason);
                        return;
                    }
                    cb.onProgress(total > 0 ? (int) (done * 100 / total) : 0);
                } catch (Exception e) {
                    cb.onError(e.getMessage() == null ? "lecture" : e.getMessage());
                    return;
                } finally {
                    if (cur != null) cur.close();
                }
                h.postDelayed(this, 500);
            }
        }, 500);
    }

    /** Ouvre l'installateur Android sur l'APK téléchargé (l'utilisateur confirme lui-même). */
    static void install(Context c, File apk) {
        Uri u = FileProvider.getUriForFile(c, c.getPackageName() + ".fileprovider", apk);
        Intent i = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(u, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }
}
