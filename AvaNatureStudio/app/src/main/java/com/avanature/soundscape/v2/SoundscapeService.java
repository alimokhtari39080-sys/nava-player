package com.avanature.soundscape.v2;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

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
    private final Map<String, MediaPlayer> onlinePlayers = new HashMap<>();
    private final Map<String, Float> onlineGains = new HashMap<>();
    private float masterVolume = 0.90f;
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
                clearAudioLayers();
                if (id != null) addSound(id, gain);
            } else if (ACTION_ADD.equals(action)) {
                String id = intent.getStringExtra(EXTRA_ID);
                float gain = intent.getFloatExtra(EXTRA_GAIN, 0.60f);
                if (id != null) addSound(id, gain);
            } else if (ACTION_REMOVE.equals(action)) {
                String id = intent.getStringExtra(EXTRA_ID);
                if (id != null) removeSound(id);
            } else if (ACTION_VOLUME.equals(action)) {
                masterVolume = clamp(intent.getFloatExtra(EXTRA_VOLUME, 0.78f));
                engine.setVolume(masterVolume);
                updateOnlineVolumes();
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
        clearAudioLayers();
        if (engine != null) {
            try { engine.close(); } catch (RuntimeException ex) {
                Log.w(TAG, "Audio engine cleanup failed", ex);
            }
        }
        engine = new SoundscapeEngine();
    }

    private String getOnlineRainUrl(String id) {
        switch (id) {
            case "rain_soft": return "https://sfxmint.com/dl/ambience-rain-62.mp3";
            case "rain_heavy": return "https://sfxmint.com/dl/ambience-rain-63.mp3";
            case "rain_window": return "https://sfxmint.com/dl/ambience-rain-21.mp3";
            case "rain_roof": return "https://sfxmint.com/dl/ambience-rain-15.mp3";
            case "rain_car": return "https://sfxmint.com/dl/ambience-rain-17.mp3";
            case "rain_tent": return "https://sfxmint.com/dl/ambience-rain-32.mp3";
            case "rain_drips": return "https://sfxmint.com/dl/ambience-rain-03.mp3";
            case "rain_puddle": return "https://sfxmint.com/dl/ambience-rain-34.mp3";
            case "bass_rain": return "https://sfxmint.com/dl/ambience-rain-42.mp3";
            default: return null;
        }
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private void addSound(String id, float gain) {
        float safeGain = clamp(gain);
        String url = getOnlineRainUrl(id);
        if (url == null) {
            engine.add(id, safeGain);
            return;
        }
        if (onlinePlayers.containsKey(id)) return;
        MediaPlayer player = new MediaPlayer();
        onlinePlayers.put(id, player);
        onlineGains.put(id, safeGain);
        try {
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            player.setDataSource(url);
            player.setLooping(true);
            float output = clamp(masterVolume * safeGain);
            player.setVolume(output, output);
            player.setOnPreparedListener(prepared -> {
                if (onlinePlayers.get(id) != prepared) return;
                try {
                    prepared.start();
                    Log.i(TAG, "CC0 rain sample started: " + id + " from " + url);
                } catch (RuntimeException ex) {
                    Log.e(TAG, "Could not start online rain sample: " + id, ex);
                    fallbackToSynth(id, safeGain, prepared);
                }
            });
            player.setOnErrorListener((failed, what, extra) -> {
                Log.e(TAG, "Online rain sample failed: " + id + ", what=" + what + ", extra=" + extra);
                fallbackToSynth(id, safeGain, failed);
                return true;
            });
            player.prepareAsync();
        } catch (IOException | RuntimeException ex) {
            Log.e(TAG, "Could not prepare online rain sample: " + id, ex);
            fallbackToSynth(id, safeGain, player);
        }
    }

    private void fallbackToSynth(String id, float gain, MediaPlayer player) {
        if (onlinePlayers.get(id) != player) return;
        onlinePlayers.remove(id);
        onlineGains.remove(id);
        try { player.release(); } catch (RuntimeException ignored) { }
        if (engine != null) engine.add(id, gain);
    }

    private void removeSound(String id) {
        MediaPlayer player = onlinePlayers.remove(id);
        onlineGains.remove(id);
        if (player != null) {
            try { player.release(); } catch (RuntimeException ignored) { }
        } else if (engine != null) {
            engine.remove(id);
        }
    }

    private void clearAudioLayers() {
        for (MediaPlayer player : new ArrayList<>(onlinePlayers.values())) {
            try { player.release(); } catch (RuntimeException ignored) { }
        }
        onlinePlayers.clear();
        onlineGains.clear();
        if (engine != null) engine.clear();
    }

    private void updateOnlineVolumes() {
        for (Map.Entry<String, MediaPlayer> entry : onlinePlayers.entrySet()) {
            Float gain = onlineGains.get(entry.getKey());
            if (gain == null) continue;
            float output = clamp(masterVolume * gain);
            try { entry.getValue().setVolume(output, output); }
            catch (RuntimeException ex) { Log.w(TAG, "Could not update online rain volume", ex); }
        }
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
        clearAudioLayers();
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
