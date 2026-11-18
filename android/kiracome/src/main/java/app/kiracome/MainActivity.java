package app.kiracome;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

/** キラコメ辞書: the web dictionary bundled in the app, with a native clipboard bridge. */
public class MainActivity extends Activity {

    private static final String PAGE = "file:///android_asset/kiracome/index.html";

    private WebView web;
    private boolean pageReady = false;
    private String pendingQuery = null;

    /** Exposed to the page as window.AndroidBridge */
    public class Bridge {
        @JavascriptInterface
        public boolean copy(String text) {
            try {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm == null) return false;
                cm.setPrimaryClip(ClipData.newPlainText("kiracome", text));
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        web.setBackgroundColor(Color.WHITE);
        web.setFitsSystemWindows(true);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // favorites, マイコメ, name, history
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(false);
        s.setTextZoom(100);

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("file".equals(u.getScheme())) return false;
                // Any outside link opens in the browser instead of inside the app.
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) {
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                applyPendingQuery();
            }
        });

        setContentView(web);
        handleIntent(getIntent());
        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(PAGE);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
        applyPendingQuery();
    }

    /** "キラコメでデコる" from another app: search / decorate the selected words. */
    private void handleIntent(Intent in) {
        if (in == null) return;
        CharSequence cs = null;
        if (Intent.ACTION_PROCESS_TEXT.equals(in.getAction())) {
            cs = in.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        } else if (Intent.ACTION_SEND.equals(in.getAction())) {
            cs = in.getCharSequenceExtra(Intent.EXTRA_TEXT);
        }
        if (cs != null) {
            String t = cs.toString().trim();
            if (t.length() > 60) t = t.substring(0, 60);
            if (!t.isEmpty()) pendingQuery = t;
        }
    }

    private void applyPendingQuery() {
        if (!pageReady || pendingQuery == null || web == null) return;
        String js = "(function(t){var q=document.getElementById('q');if(!q)return;"
                + "q.value=t;q.dispatchEvent(new Event('input'));window.scrollTo(0,0);})("
                + JSONObject.quote(pendingQuery) + ");";
        pendingQuery = null;
        web.evaluateJavascript(js, null);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (web != null) web.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
