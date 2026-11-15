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
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
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
    static final String EXTRA_IDS = "sound_ids";
    static final String EXTRA_GAIN = "sound_gain";
    static final String EXTRA_VOLUME = "master_volume";
    private static final String CHANNEL = "avanature_playback";
    private static final String TAG = "AvaNatureService";

    private SoundscapeEngine engine;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private final Map<String, MediaPlayer> onlinePlayers = new HashMap<>();
    private final Map<String, Float> onlineGains = new HashMap<>();
    private final Handler playbackHandler = new Handler(Looper.getMainLooper());
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
                float gain = intent.getFloatExtra(EXTRA_GAIN, 0.90f);
                clearAudioLayers();
                ArrayList<String> ids = intent.getStringArrayListExtra(EXTRA_IDS);
                if (ids != null && !ids.isEmpty()) {
                    for (String id : ids) {
                        if (id != null && !id.trim().isEmpty()) addSound(id, gain);
                    }
                } else {
                    String id = intent.getStringExtra(EXTRA_ID);
                    if (id != null) addSound(id, gain);
                }
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

    // All ambience is packaged inside the APK: playback works without mobile data or Wi-Fi.
    private int getBundledSoundResource(String id) {
        switch (id) {
            case "rain_soft": case "rain_window": case "rain_tent": case "rain_puddle":
            case "rain_drips": case "rain_roof": case "rain_car": case "rain_heavy":
            case "bass_rain": return R.raw.rain_soft;
            case "forest_wind": case "leaves": case "low_wind": case "storm_wind":
            case "moon_wind": return R.raw.forest_wind;
            case "birdsong": case "owl": case "cricket": case "night_air":
            case "frog": case "cat_purr": case "dog_bark": case "wolf_howl":
            case "horse": case "whale": case "lion": return R.raw.forest_birds;
            case "stream": case "waterfall": case "waves": case "beach":
            case "ocean_underwater": case "bass_drips": return R.raw.water_ambience;
            case "thunder_far": case "thunder_close": case "deep_thunder":
            case "hail": return R.raw.thunder_ambience;
            case "fire": case "campfire": case "embers": return R.raw.fireplace;
            case "white_noise": case "pink_noise": return R.raw.white_noise;
            case "brown_noise": case "sub_bass": case "heartbeat": case "space_drone":
            case "cosmic_noise": case "breathing": case "singing_bowl":
            case "cafe": case "crowd": case "city": case "train": case "footsteps":
                return R.raw.forest_wind;
            default: return R.raw.rain_soft;
        }
    }

    private static float clamp(float value) {\n        return Math.max(0f, Math.min(1f, value));\n    }\n\n    private void addSound(String id, float gain) {
        float safeGain = clamp(gain);
        if (onlinePlayers.containsKey(id)) return;
        int resourceId = getBundledSoundResource(id);
        MediaPlayer player = null;
        try {
            // MediaPlayer.create handles compressed and uncompressed APK raw resources.
            player = MediaPlayer.create(this, resourceId);
            if (player == null) throw new IllegalStateException("Could not decode bundled audio resource " + resourceId);
            player.setLooping(true);
            float output = clamp(masterVolume * safeGain);
            player.setVolume(output, output);
            onlinePlayers.put(id, player);
            onlineGains.put(id, safeGain);
            MediaPlayer activePlayer = player;
            player.setOnErrorListener((failed, what, extra) -> {
                Log.e(TAG, "Bundled sample failed: " + id + ", what=" + what + ", extra=" + extra);
                reportSampleFailure(id, failed);
                Toast.makeText(this, "خطا در پخش صدای داخلی برنامه: " + id, Toast.LENGTH_LONG).show();
                return true;
            });
            player.start();
            Log.i(TAG, "Bundled offline sample started: " + id + ", resource=" + resourceId
                    + ", playing=" + activePlayer.isPlaying());
        } catch (RuntimeException ex) {
            Log.e(TAG, "Could not start bundled offline sample: " + id, ex);
            if (player != null) {
                onlinePlayers.remove(id);
                onlineGains.remove(id);
                try { player.release(); } catch (RuntimeException ignored) { }
            }
            Toast.makeText(this, "پخش صدای داخلی ناموفق بود: " + id, Toast.LENGTH_LONG).show();
        }
    }

    private void reportSampleFailure(String id, MediaPlayer player) {
        if (onlinePlayers.get(id) != player) return;
        onlinePlayers.remove(id);
        onlineGains.remove(id);
        try { player.release(); } catch (RuntimeException ignored) { }
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
            catch (RuntimeException ex) { Log.w(TAG, "Could not update MediaPlayer volume", ex); }
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
                .setContentTitle("آواطبیعت استودیو")
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
