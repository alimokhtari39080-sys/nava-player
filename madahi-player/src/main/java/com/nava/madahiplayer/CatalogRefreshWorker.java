package com.nava.madahiplayer;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class CatalogRefreshWorker extends Worker {
    public static final String UNIQUE_WORK_NAME = "nava-madahi-player-catalog-refresh";
    private static final String FEED_URL =
        "https://raw.githubusercontent.com/alimokhtari39080-sys/nava-player/main/madahi/src/main/assets/madahi/data/feed.json";
    private static final String PREFS = "nava_madahi_player";
    private static final String CHANNEL_ID = "new_madahi";
    private static final int NOTIFICATION_ID = 7314;

    public CatalogRefreshWorker(Context context, WorkerParameters params) {
        super(context, params);
    }

    @Override
    public Result doWork() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(FEED_URL).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("User-Agent", "NavaMadahiPlayer-Android/1.0");

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) return Result.retry();

            StringBuilder body = new StringBuilder();
            try (InputStream stream = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(
                     new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
            }

            JSONObject catalog = new JSONObject(body.toString());
            JSONArray items = catalog.optJSONArray("items");
            if (items == null) return Result.retry();

            Set<String> currentIds = new HashSet<>();
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                String id = item.optString("id", "");
                if (id.isEmpty()) id = item.optString("videoId", "");
                if (!id.isEmpty()) currentIds.add(id);
            }

            if (currentIds.isEmpty()) return Result.retry();

            Context context = getApplicationContext();
            android.content.SharedPreferences prefs =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            boolean initialized = prefs.getBoolean("catalog_initialized", false);
            Set<String> previous = prefs.getStringSet("known_ids", new HashSet<>());

            Set<String> newIds = new HashSet<>(currentIds);
            if (initialized && previous != null) newIds.removeAll(previous);

            prefs.edit()
                .putBoolean("catalog_initialized", true)
                .putStringSet("known_ids", new HashSet<>(currentIds))
                .putString("last_checked_at", String.valueOf(System.currentTimeMillis()))
                .apply();

            if (initialized && !newIds.isEmpty()) {
                postNewRecitationsNotification(context, newIds.size());
            }

            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void postNewRecitationsNotification(Context context, int count) {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) return;

        NotificationManager manager =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "مداحی‌های تازه", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("اعلان انتشار مداحی‌های جدید در آرشیو نوای هیئت پلاس");
            manager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, 7314, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String message = count == 1
            ? "یک مداحی تازه به آرشیو اضافه شده است."
            : count + " مداحی تازه به آرشیو اضافه شده است.";

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(context, CHANNEL_ID)
            : new Notification.Builder(context);

        Notification notification = builder
            .setSmallIcon(R.drawable.ic_player)
            .setContentTitle("مداحی تازه رسید")
            .setContentText(message)
            .setStyle(new Notification.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build();

        manager.notify(NOTIFICATION_ID, notification);
    }
}
