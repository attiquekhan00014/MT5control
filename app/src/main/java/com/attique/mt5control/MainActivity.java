package com.attique.mt5control;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends AppCompatActivity {
    private WebView web;
    private SwipeRefreshLayout swipe;
    private ValueCallback<Uri[]> fileCb;

    private final ActivityResultLauncher<Intent> picker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), res -> {
                if (fileCb == null) return;
                Uri[] r = WebChromeClient.FileChooserParams.parseResult(res.getResultCode(), res.getData());
                fileCb.onReceiveValue(r);
                fileCb = null;
            });

    /** functions the web app can call: window.MT5Android.watch(...) etc. */
    public class Bridge {
        @JavascriptInterface public String version() { return "3"; }

        /** Google blocks sign-in inside a WebView, so the sign-in page opens in the phone's browser and comes back via mt5control://signin?code=... */
        @JavascriptInterface public void signIn() {
            runOnUiThread(() -> {
                try {
                    String base = BuildConfig.APP_URL.replaceAll("/app/?$", "");
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(base + "/gsign")));
                } catch (Exception ignored) {}
            });
        }

        @JavascriptInterface public String paste() {
            try {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData d = cm.getPrimaryClip();
                if (d != null && d.getItemCount() > 0 && d.getItemAt(0).getText() != null) return d.getItemAt(0).getText().toString();
            } catch (Exception ignored) {}
            return "";
        }

        @JavascriptInterface public void watch(String base, String token, String dev) {
            SharedPreferences.Editor e = getSharedPreferences("mt5", Context.MODE_PRIVATE).edit();
            e.putString("base", base).putString("token", token).putString("dev", dev).putBoolean("watch", true).apply();
            runOnUiThread(() -> {
                askNotifications();
                startWatcher(MainActivity.this);
            });
        }

        @JavascriptInterface public void unwatch() {
            getSharedPreferences("mt5", Context.MODE_PRIVATE).edit().putBoolean("watch", false).apply();
            stopService(new Intent(MainActivity.this, AlarmService.class));
        }

        @JavascriptInterface public void stopRing() { AlarmService.silence(); }

        @JavascriptInterface public void test() {
            Intent i = new Intent(MainActivity.this, AlarmService.class).setAction(AlarmService.ACTION_TEST);
            ContextCompat.startForegroundService(MainActivity.this, i);
        }

        @JavascriptInterface public void batteryHelp() {
            runOnUiThread(() -> {
                try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); } catch (Exception ignored) {}
            });
        }
    }

    static void startWatcher(Context c) {
        try {
            ContextCompat.startForegroundService(c, new Intent(c, AlarmService.class));
        } catch (Exception ignored) { }
    }

    private void askNotifications() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        web = findViewById(R.id.web);
        swipe = findViewById(R.id.swipe);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // keeps the login session (localStorage)
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setUserAgentString(s.getUserAgentString() + " MT5App/3");
        web.addJavascriptInterface(new Bridge(), "MT5Android");
        final String host = Uri.parse(BuildConfig.APP_URL).getHost();
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                try {
                    picker.launch(p.createIntent());
                } catch (Exception e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (host != null && host.equalsIgnoreCase(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                return true;
            }
            @Override public void onPageStarted(WebView v, String url, Bitmap f) { swipe.setRefreshing(true); }
            @Override public void onPageFinished(WebView v, String url) {
                swipe.setRefreshing(false);
                runPendingSignIn();
            }
            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) {
                    swipe.setRefreshing(false);
                    String html = "<body style='background:#0f1420;color:#e8ecf5;font-family:sans-serif;text-align:center;padding:40px'>"
                        + "<h2>Cannot reach MT5 Control</h2><p>Check your internet, and that the laptop is ON with MT5 Control running.</p>"
                        + "<p>Pull down to retry.</p></body>";
                    v.loadDataWithBaseURL(null, html, "text/html", "utf-8", BuildConfig.APP_URL);
                }
            }
        });
        swipe.setOnRefreshListener(() -> web.loadUrl(BuildConfig.APP_URL));
        if (b != null) web.restoreState(b); else web.loadUrl(BuildConfig.APP_URL);
        if (getSharedPreferences("mt5", Context.MODE_PRIVATE).getBoolean("watch", false)) startWatcher(this);
        handleIntent(getIntent());
    }

    private String pendingCode;

    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); setIntent(i); handleIntent(i); }

    private void handleIntent(Intent i) {
        if (i == null || i.getData() == null) return;
        Uri u = i.getData();
        if ("mt5control".equals(u.getScheme()) && "signin".equals(u.getHost())) {
            String c = u.getQueryParameter("code");
            if (c != null && c.matches("[0-9a-f]{16,64}")) { pendingCode = c; runPendingSignIn(); }
        }
    }

    private void runPendingSignIn() {
        if (pendingCode == null || web == null) return;
        String c = pendingCode; pendingCode = null;
        web.post(() -> web.evaluateJavascript("if(window.gmailFromApp)gmailFromApp('" + c + "')", null));
    }

    @Override protected void onSaveInstanceState(Bundle o) { super.onSaveInstanceState(o); web.saveState(o); }

    @Override public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
