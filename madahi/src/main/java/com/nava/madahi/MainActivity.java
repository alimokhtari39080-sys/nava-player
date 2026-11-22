package com.nava.madahi;

import android.app.Activity;
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
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private WebView webView;
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private static final String FEED_URL =
        "https://raw.githubusercontent.com/alimokhtari39080-sys/nava-player/main/madahi/src/main/assets/madahi/data/feed.json";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(18, 15, 22));
        getWindow().setNavigationBarColor(Color.rgb(18, 15, 22));

        webView = new WebView(this);
        setContentView(webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setDefaultTextEncodingName("UTF-8");
        webView.addJavascriptInterface(new FeedBridge(), "NavaBridge");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("file".equalsIgnoreCase(uri.getScheme())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) { }
                return true;
            }
        });
        webView.loadUrl("file:///android_asset/madahi/index.html");
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        networkExecutor.shutdownNow();
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    private class FeedBridge {
        @JavascriptInterface
        public void refreshFeed() {
            networkExecutor.execute(() -> {
                String payload = downloadFeed();
                String quoted = JSONObject.quote(payload);
                runOnUiThread(() -> {
                    if (webView != null) {
                        webView.evaluateJavascript("window.onRemoteFeed && window.onRemoteFeed(" + quoted + ")", null);
                    }
                });
            });
        }
    }

    private String downloadFeed() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(FEED_URL).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(9000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("User-Agent", "NavaMadahi-Android/1.0");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) return "{\"error\":\"HTTP " + status + "\"}";
            try (InputStream stream = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
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
