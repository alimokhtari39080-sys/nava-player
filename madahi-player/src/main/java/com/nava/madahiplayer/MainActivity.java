package com.nava.madahiplayer;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.webkit.WebViewAssetLoader;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final String FEED_URL =
        "https://raw.githubusercontent.com/alimokhtari39080-sys/nava-player/main/madahi/src/main/assets/madahi/data/feed.json";

    private WebView webView;
    private TextView backButton;
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private WebViewAssetLoader assetLoader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(18, 15, 22));
        getWindow().setNavigationBarColor(Color.rgb(18, 15, 22));

        buildScreen();
        configureWebView();
        scheduleBackgroundRefresh();
        askNotificationPermission();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(18, 15, 22));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(16), dp(7), dp(16), dp(7));
        toolbar.setBackgroundColor(Color.rgb(27, 22, 32));

        backButton = new TextView(this);
        backButton.setText("‹");
        backButton.setTextColor(Color.rgb(240, 214, 154));
        backButton.setTextSize(30);
        backButton.setGravity(Gravity.CENTER);
        backButton.setContentDescription("بازگشت");
        backButton.setPadding(dp(9), 0, dp(9), 0);
        backButton.setOnClickListener(v -> goBack());

        TextView title = new TextView(this);
        title.setText("نوای هیئت پلاس");
        title.setTextColor(Color.rgb(246, 238, 228));
        title.setTextSize(15);
        title.setPadding(dp(8), 0, 0, 0);

        toolbar.addView(backButton, new LinearLayout.LayoutParams(dp(42), dp(46)));
        toolbar.addView(title, new LinearLayout.LayoutParams(0, dp(46), 1));
        root.addView(toolbar, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(58)));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(18, 15, 22));
        root.addView(webView, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    private void configureWebView() {
        assetLoader = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
            .build();

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setDefaultTextEncodingName("UTF-8");
        settings.setSupportMultipleWindows(false);

        webView.addJavascriptInterface(new PlayerBridge(), "NavaPlayer");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(
                    WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme() == null ? "" : uri.getScheme();
                String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();

                if ("https".equalsIgnoreCase(scheme) && isAllowedInAppHost(host)) {
                    return false;
                }

                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) {
                    // An unsupported external URL should not crash the player.
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                backButton.setVisibility(view.canGoBack() ? View.VISIBLE : View.INVISIBLE);
            }
        });

        webView.loadUrl("https://appassets.androidplatform.net/assets/player/index.html");
    }

    private boolean isAllowedInAppHost(String host) {
        return host.equals("appassets.androidplatform.net")
            || host.equals("youtube.com")
            || host.endsWith(".youtube.com")
            || host.equals("youtube-nocookie.com")
            || host.endsWith(".youtube-nocookie.com")
            || host.equals("ytimg.com")
            || host.endsWith(".ytimg.com")
            || host.equals("googlevideo.com")
            || host.endsWith(".googlevideo.com")
            || host.equals("radioneynava.com")
            || host.equals("www.radioneynava.com");
    }

    private void goBack() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) goBack();
        else super.onBackPressed();
    }

    private void scheduleBackgroundRefresh() {
        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
            CatalogRefreshWorker.class, 12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            CatalogRefreshWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        );
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 4102);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        networkExecutor.shutdownNow();
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    private class PlayerBridge {
        @JavascriptInterface
        public void refreshCatalog() {
            networkExecutor.execute(() -> {
                String payload = downloadCatalog();
                String quoted = JSONObject.quote(payload);
                runOnUiThread(() -> {
                    if (webView != null) {
                        webView.evaluateJavascript(
                            "window.onRemoteCatalog && window.onRemoteCatalog(" + quoted + ")", null);
                    }
                });
            });
        }

        @JavascriptInterface
        public void openArchive(String rawUrl) {
            try {
                Uri uri = Uri.parse(rawUrl);
                if (!"https".equalsIgnoreCase(uri.getScheme())) return;
                String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
                if (!host.equals("radioneynava.com") && !host.equals("www.radioneynava.com")) return;
                runOnUiThread(() -> webView.loadUrl(uri.toString()));
            } catch (Exception ignored) { }
        }

        @JavascriptInterface
        public void requestNotificationPermission() {
            runOnUiThread(MainActivity.this::askNotificationPermission);
        }

        @JavascriptInterface
        public void goBackToCatalog() {
            runOnUiThread(MainActivity.this::goBack);
        }
    }

    private String downloadCatalog() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(FEED_URL).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(9000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("User-Agent", "NavaMadahiPlayer-Android/1.0");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) return "{\"error\":\"HTTP " + status + "\"}";
            try (InputStream stream = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(
                     new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                StringBuilder body = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
                return body.toString();
            }
        } catch (Exception e) {
            return "{\"error\":\"offline\"}";
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
