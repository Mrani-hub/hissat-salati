package ma.hissatsalati;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;

public class MainActivity extends Activity {

    private WebView web;
    private ValueCallback<Uri[]> filePath;
    private static final int PICK_FILE = 101;

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

        // sélecteur de fichier pour « Lire l'image »
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb,
                                             FileChooserParams params) {
                if (filePath != null) filePath.onReceiveValue(null);
                filePath = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                try {
                    startActivityForResult(Intent.createChooser(i, getString(R.string.choose_image)), PICK_FILE);
                } catch (Exception e) {
                    filePath = null;
                    return false;
                }
                return true;
            }
        });

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.loadUrl("file:///android_asset/index.html");
        setContentView(web);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == PICK_FILE) {
            if (filePath == null) return;
            Uri[] out = null;
            if (res == RESULT_OK && data != null && data.getData() != null) {
                out = new Uri[]{data.getData()};
            }
            filePath.onReceiveValue(out);
            filePath = null;
            return;
        }
        super.onActivityResult(req, res, data);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    private void toast(String m) {
        runOnUiThread(() -> Toast.makeText(this, m, Toast.LENGTH_SHORT).show());
    }

    /** Reçoit les fichiers produits par la page (image du mois, fichier à partager, JSON). */
    private class Bridge {
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
