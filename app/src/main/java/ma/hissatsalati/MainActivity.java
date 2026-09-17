package ma.hissatsalati;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class MainActivity extends Activity {

    private WebView web;
    private boolean askedPerms = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);      // localStorage : horaires, suivi, favoris
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);

        // les liens externes (habous.gov.ma) partent dans le navigateur
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if ("file".equals(u.getScheme())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception e) {
                    toast(getString(R.string.no_app_for_link));
                }
                return true;
            }
        });

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.loadUrl("file:///android_asset/index.html");
        setContentView(web);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    /** Notifications (Android 13+) et alarmes exactes (Android 12) : demandées une fois par ouverture. */
    private void ensurePermissions() {
        if (askedPerms) return;
        askedPerms = true;
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        }
        if (Build.VERSION.SDK_INT >= 31 && Build.VERSION.SDK_INT < 33) {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (!am.canScheduleExactAlarms()) {
                try {
                    startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + getPackageName())));
                } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        Schedule.scheduleNext(this);
    }

    private void toast(String m) {
        runOnUiThread(() -> Toast.makeText(this, m, Toast.LENGTH_SHORT).show());
    }

    /** Appelle une fonction de la page avec un argument texte (échappé comme chaîne JS). */
    private void js(String fn, String arg) {
        final String code = "typeof " + fn + "==='function'&&" + fn + "("
                + JSONObject.quote(arg == null ? "" : arg) + ")";
        runOnUiThread(() -> { if (web != null) web.evaluateJavascript(code, null); });
    }

    /** Reçoit les fichiers produits par la page (image du mois, fichier à partager, JSON). */
    private class Bridge {
        /** La page envoie les horaires du mois et les réglages ; on programme la prochaine alarme. */
        @JavascriptInterface
        public void setSchedule(String json) {
            Schedule.save(MainActivity.this, json);
            Schedule.scheduleNext(MainActivity.this);
            boolean wants = json != null
                    && (json.contains("\"adhan\":true") || json.contains("\"notify\":true"));
            if (wants) runOnUiThread(MainActivity.this::ensurePermissions);
        }

        /** Version installée, pour l'afficher dans la page : {"name":"1.4","code":5}. */
        @JavascriptInterface
        public String appInfo() {
            try {
                return new JSONObject()
                        .put("name", Updater.currentName(MainActivity.this))
                        .put("code", Updater.currentCode(MainActivity.this)).toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        /** Demande à GitHub la dernière Release ; la réponse arrive dans onUpdateResult(json). */
        @JavascriptInterface
        public void checkUpdate() {
            Updater.check(MainActivity.this, r -> js("onUpdateResult", r.toString()));
        }

        /** Télécharge l'APK de la dernière Release ; l'installation se fait depuis la notification. */
        @JavascriptInterface
        public void downloadUpdate() {
            runOnUiThread(() -> Updater.download(MainActivity.this, new Updater.OnDownload() {
                @Override public void onProgress(int pct) { js("onUpdateProgress", String.valueOf(pct)); }
                @Override public void onDone(File apk) { js("onUpdateDone", ""); toast(getString(R.string.update_done)); }
                @Override public void onError(String why) { js("onUpdateError", why); }
            }));
        }

        /** Ouvre l'écran des téléchargements pour retrouver l'APK et l'installer. */
        @JavascriptInterface
        public void openDownloads() {
            runOnUiThread(() -> {
                try { Updater.openDownloads(MainActivity.this); }
                catch (Exception e) { toast(getString(R.string.update_no_downloads)); }
            });
        }

        /** Joue l'adhan tout de suite, pour vérifier le son et l'arrêt par les boutons de volume. */
        @JavascriptInterface
        public void testAdhan() {
            Intent s = new Intent(MainActivity.this, AdhanService.class)
                    .putExtra("prayer", getString(R.string.adhan_test)).putExtra("time", "");
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(s);
            else startService(s);
        }

        /**
         * Envoie l'APK de l'application elle-même (WhatsApp, Bluetooth…) sous le nom
         * « حصة صلاتي.apk ». Le fichier est copié depuis l'installation vers le cache
         * de partage, puis exposé par le FileProvider comme les autres fichiers partagés.
         */
        @JavascriptInterface
        public void shareApp() {
            try {
                File src = new File(getApplicationInfo().sourceDir);
                File dir = new File(getCacheDir(), "partage");
                if (!dir.exists() && !dir.mkdirs()) throw new Exception("dossier");
                File f = new File(dir, getString(R.string.apk_share_name));
                try (FileInputStream in = new FileInputStream(src);
                     FileOutputStream out = new FileOutputStream(f)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                Uri uri = FileProvider.getUriForFile(
                        MainActivity.this, getPackageName() + ".fileprovider", f);
                Intent send = new Intent(Intent.ACTION_SEND)
                        .setType("application/vnd.android.package-archive")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(send, getString(R.string.share)));
            } catch (Exception e) {
                toast(getString(R.string.share_failed, e.getMessage()));
            }
        }

        @JavascriptInterface
        public void saveFile(String base64, String name, String mime, String title) {
            try {
                File dir = new File(getCacheDir(), "partage");
                if (!dir.exists() && !dir.mkdirs()) throw new Exception("dossier");
                // sécurité : on garde uniquement le nom de base (pas de "../", pas de dossier)
                String safeName = new File(name == null ? "" : name).getName()
                        .replaceAll("[^A-Za-z0-9._-]", "_");
                if (safeName.isEmpty() || safeName.startsWith(".")) safeName = "partage.bin";
                File f = new File(dir, safeName);
                FileOutputStream out = new FileOutputStream(f);
                out.write(Base64.decode(base64, Base64.DEFAULT));
                out.close();

                Uri uri = FileProvider.getUriForFile(
                        MainActivity.this, getPackageName() + ".fileprovider", f);

                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType(mime == null || mime.isEmpty() ? "*/*" : mime);
                send.putExtra(Intent.EXTRA_STREAM, uri);
                if (title != null && !title.isEmpty()) send.putExtra(Intent.EXTRA_SUBJECT, title);
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(send, getString(R.string.share)));
            } catch (Exception e) {
                toast(getString(R.string.share_failed, e.getMessage()));
            }
        }
    }
}
