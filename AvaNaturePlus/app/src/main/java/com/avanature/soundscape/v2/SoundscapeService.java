package com.avanature.soundscape.v2;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

public class SoundscapeService extends Service {
    static final String ACTION_ADD = "com.avanature.soundscape.v2.ADD";
    static final String ACTION_REMOVE = "com.avanature.soundscape.v2.REMOVE";
    static final String ACTION_CLEAR = "com.avanature.soundscape.v2.CLEAR";
    static final String ACTION_VOLUME = "com.avanature.soundscape.v2.VOLUME";
    static final String EXTRA_ID = "sound_id";
    static final String EXTRA_GAIN = "sound_gain";
    static final String EXTRA_VOLUME = "master_volume";
    private static final String CHANNEL = "avanature_playback";
    private SoundscapeEngine engine;

    @Override public void onCreate() {
        super.onCreate();
        engine = new SoundscapeEngine();
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_ADD : intent.getAction();
        if (ACTION_CLEAR.equals(action)) {
            if (engine != null) engine.clear();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(2402, makeNotification());
        if (ACTION_ADD.equals(action)) {
            String id = intent.getStringExtra(EXTRA_ID);
            float gain = intent.getFloatExtra(EXTRA_GAIN, 0.65f);
            if (id != null && engine != null) engine.add(id, gain);
        } else if (ACTION_REMOVE.equals(action)) {
            String id = intent.getStringExtra(EXTRA_ID);
            if (id != null && engine != null) engine.remove(id);
        } else if (ACTION_VOLUME.equals(action)) {
            if (engine != null) engine.setVolume(intent.getFloatExtra(EXTRA_VOLUME, 0.78f));
        }
        return START_STICKY;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "پخش آواطبیعت", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("پخش صداهای طبیعت در پس‌زمینه");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification makeNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Intent stop = new Intent(this, SoundscapeService.class).setAction(ACTION_CLEAR);
        PendingIntent stopPending = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return b.setSmallIcon(R.drawable.ic_launcher_art)
                .setContentTitle("آواطبیعت پلاس")
                .setContentText("در حال پخش ترکیب صداهای انتخاب‌شده")
                .setContentIntent(content)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_media_pause, "توقف", stopPending)
                .build();
    }

    @Override public void onDestroy() {
        if (engine != null) engine.close();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
