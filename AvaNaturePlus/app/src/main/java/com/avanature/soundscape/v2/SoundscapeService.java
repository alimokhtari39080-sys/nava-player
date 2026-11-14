package com.avanature.soundscape.v2;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

public class SoundscapeService extends Service {
    static final String ACTION_ADD = "com.avanature.soundscape.v2.ADD";
    static final String ACTION_REMOVE = "com.avanature.soundscape.v2.REMOVE";
    static final String ACTION_CLEAR = "com.avanature.soundscape.v2.CLEAR";
    static final String ACTION_REPLACE = "com.avanature.soundscape.v2.REPLACE";
    static final String ACTION_VOLUME = "com.avanature.soundscape.v2.VOLUME";
    static final String EXTRA_ID = "sound_id";
    static final String EXTRA_GAIN = "sound_gain";
    static final String EXTRA_VOLUME = "master_volume";
    private static final String CHANNEL = "avanature_playback";
    private static final String TAG = "AvaNatureService";

    private SoundscapeEngine engine;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private final AudioManager.OnAudioFocusChangeListener focusListener = change ->
            Log.i(TAG, "Audio focus changed: " + change);

    @Override public void onCreate() {
        super.onCreate();
        engine = new SoundscapeEngine();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioManager != null && Build.VERSION.SDK_INT >= 26) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setOnAudioFocusChangeListener(focusListener)
                    .setWillPauseWhenDucked(false)
                    .build();
        }
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();

        if (ACTION_CLEAR.equals(action)) {
            resetEngine();
            try {
                if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
                else stopForeground(true);
            } catch (RuntimeException ex) {
                Log.w(TAG, "Could not remove playback notification", ex);
            }
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        try {
            if (audioManager != null) {
                int focus = Build.VERSION.SDK_INT >= 26 && focusRequest != null
                        ? audioManager.requestAudioFocus(focusRequest)
                        : audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
                Log.i(TAG, "Audio focus request result: " + focus
                        + "; media volume=" + audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        + "/" + audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            }
            startForeground(2402, makeNotification());
        } catch (RuntimeException ex) {
            Log.e(TAG, "Could not start foreground playback service; app will remain open.", ex);
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        try {
            if (engine == null) engine = new SoundscapeEngine();
            if (ACTION_REPLACE.equals(action)) {
                String id = intent.getStringExtra(EXTRA_ID);
                float gain = intent.getFloatExtra(EXTRA_GAIN, 0.90f);
                engine.clear();
                if (id != null) engine.add(id, gain);
            } else if (ACTION_ADD.equals(action)) {
                String id = intent.getStringExtra(EXTRA_ID);
                float gain = intent.getFloatExtra(EXTRA_GAIN, 0.60f);
                if (id != null) engine.add(id, gain);
            } else if (ACTION_REMOVE.equals(action)) {
                String id = intent.getStringExtra(EXTRA_ID);
                if (id != null) engine.remove(id);
            } else if (ACTION_VOLUME.equals(action)) {
                engine.setVolume(intent.getFloatExtra(EXTRA_VOLUME, 0.78f));
            }
        } catch (RuntimeException ex) {
            Log.e(TAG, "Playback request failed; keeping UI alive.", ex);
            resetEngine();
            try {
                if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
                else stopForeground(true);
            } catch (RuntimeException ignored) { }
        }
        return START_NOT_STICKY;
    }

    private void resetEngine() {
        if (audioManager != null) {
            try {
                if (Build.VERSION.SDK_INT >= 26 && focusRequest != null) audioManager.abandonAudioFocusRequest(focusRequest);
                else audioManager.abandonAudioFocus(focusListener);
            } catch (RuntimeException ignored) { }
        }
        if (engine != null) {
            try { engine.close(); } catch (RuntimeException ex) {
                Log.w(TAG, "Audio engine cleanup failed", ex);
            }
        }
        engine = new SoundscapeEngine();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL, "پخش آواطبیعت", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("پخش صداهای طبیعت در پس‌زمینه");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification makeNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

        Intent stop = new Intent(this, SoundscapeService.class).setAction(ACTION_CLEAR);
        PendingIntent stopPending = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);

        return builder.setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("آواطبیعت پلاس")
                .setContentText("در حال پخش ترکیب صداهای انتخاب‌شده")
                .setContentIntent(content)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_media_pause, "توقف", stopPending)
                .build();
    }

    @Override public void onDestroy() {
        if (engine != null) {
            try { engine.close(); } catch (RuntimeException ex) {
                Log.w(TAG, "Error while releasing audio engine", ex);
            }
            engine = null;
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
