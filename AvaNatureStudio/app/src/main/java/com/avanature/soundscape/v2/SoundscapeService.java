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

    /*
     * All catalog items use the same Android MediaPlayer output path.
     * Rain was the only group previously routed through MediaPlayer; every
     * other group fell back to the less reliable PCM AudioTrack synthesizer.
     * These stable CC0 MP3 links now cover the complete visible sound catalog.
     */
    private String getOnlineSoundUrl(String id) {
        switch (id) {
            // Rain
            case "rain_soft": return "https://sfxmint.com/dl/ambience-rain-62.mp3";
            case "rain_heavy": return "https://sfxmint.com/dl/ambience-rain-63.mp3";
            case "rain_window": return "https://sfxmint.com/dl/ambience-rain-21.mp3";
            case "rain_roof": return "https://sfxmint.com/dl/ambience-rain-15.mp3";
            case "rain_car": return "https://sfxmint.com/dl/ambience-rain-17.mp3";
            case "rain_tent": return "https://sfxmint.com/dl/ambience-rain-32.mp3";
            case "rain_drips": return "https://sfxmint.com/dl/ambience-rain-03.mp3";
            case "rain_puddle": return "https://sfxmint.com/dl/ambience-rain-34.mp3";
            case "bass_rain": return "https://sfxmint.com/dl/ambience-rain-42.mp3";

            // Forest, birds, insects and running water
            case "forest_wind": return "https://sfxmint.com/dl/windy-leafy-forest-ambience-01.mp3";
            case "birdsong": return "https://sfxmint.com/dl/ambience-forest-40.mp3";
            case "stream": return "https://sfxmint.com/dl/river-ambience-11.mp3";
            case "waterfall": return "https://sfxmint.com/dl/water-stream-82.mp3";
            case "leaves": return "https://sfxmint.com/dl/ambience-forest-50.mp3";
            case "cricket": return "https://sfxmint.com/dl/animal-insect-70.mp3";
            case "owl": return "https://sfxmint.com/dl/animal-owl-01.mp3";
            case "night_air": return "https://sfxmint.com/dl/ambience-forest-50.mp3";

            // Thunder and storm
            case "thunder_far": return "https://sfxmint.com/dl/ambience-thunder-12.mp3";
            case "thunder_close": return "https://sfxmint.com/dl/ambience-thunder-08.mp3";
            case "storm_wind": return "https://sfxmint.com/dl/ambience-wind-34.mp3";
            case "hail": return "https://sfxmint.com/dl/hail-02.mp3";
            case "deep_thunder": return "https://sfxmint.com/dl/ambience-thunder-06.mp3";

            // Animal calls
            case "wolf_howl": return "https://sfxmint.com/dl/wolf-howl-02.mp3";
            case "cat_purr": return "https://sfxmint.com/dl/cat-purr-04.mp3";
            case "dog_bark": return "https://sfxmint.com/dl/animal-dog-bark-25.mp3";
            case "frog": return "https://sfxmint.com/dl/animal-frog-14.mp3";
            case "horse": return "https://sfxmint.com/dl/horse-neigh-03.mp3";
            case "whale": return "https://sfxmint.com/dl/whale-song-03.mp3";
            case "lion": return "https://sfxmint.com/dl/lion-roar-02.mp3";

            // Sea and underwater
            case "waves": return "https://sfxmint.com/dl/ambience-ocean-31.mp3";
            case "beach": return "https://sfxmint.com/dl/ambience-ocean-11.mp3";
            case "ocean_underwater": return "https://sfxmint.com/dl/underwater-ambience-01.mp3";

            // Bass, noise and calm
            case "heartbeat": return "https://sfxmint.com/dl/horror-heartbeat-44.mp3";
            case "brown_noise": return "https://sfxmint.com/dl/tension-drone-08.mp3";
            case "pink_noise": return "https://sfxmint.com/dl/white-noise-02.mp3";
            case "white_noise": return "https://sfxmint.com/dl/white-noise-01.mp3";
            case "low_wind": return "https://sfxmint.com/dl/ambience-wind-34.mp3";
            case "sub_bass": return "https://sfxmint.com/dl/tension-drone-08.mp3";
            case "bass_drips": return "https://sfxmint.com/dl/water-drip-76.mp3";
            case "breathing": return "https://sfxmint.com/dl/breathing-02.mp3";
            case "singing_bowl": return "https://sfxmint.com/dl/instrument-singing-bowl-53.mp3";

            // Human, city, fire and space
            case "cafe": return "https://sfxmint.com/dl/cafe-interior-ambience-03.mp3";
            case "crowd": return "https://sfxmint.com/dl/crowd-murmur-81.mp3";
            case "footsteps": return "https://sfxmint.com/dl/footsteps-wood-60.mp3";
            case "train": return "https://sfxmint.com/dl/steam-train-motion-01.mp3";
            case "city": return "https://sfxmint.com/dl/city-night-ambience-03.mp3";
            case "fire": return "https://sfxmint.com/dl/ambience-fireplace-50.mp3";
            case "campfire": return "https://sfxmint.com/dl/ambience-fireplace-50.mp3";
            case "embers": return "https://sfxmint.com/dl/ambience-fireplace-50.mp3";
            case "space_drone": return "https://sfxmint.com/dl/tension-drone-01.mp3";
            case "cosmic_noise": return "https://sfxmint.com/dl/tension-drone-07.mp3";
            case "moon_wind": return "https://sfxmint.com/dl/ambience-wind-34.mp3";
            default: return null;
        }
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    // Animal calls are short effects, not long ambience beds. Leave a natural
    // pause before repeating them instead of machine-gunning the same call.
    private boolean isOneShotCall(String id) {
        return "wolf_howl".equals(id) || "dog_bark".equals(id) || "frog".equals(id)
                || "horse".equals(id) || "whale".equals(id) || "lion".equals(id)
                || "owl".equals(id);
    }

    private void replayOneShotAfterPause(String id, MediaPlayer player) {
        long pauseMs = 1800L + (Math.abs(id.hashCode()) % 1800L);
        playbackHandler.postDelayed(() -> {
            if (onlinePlayers.get(id) != player) return;
            try {
                player.seekTo(0);
                player.start();
            } catch (RuntimeException ex) {
                Log.e(TAG, "Could not replay animal sample: " + id, ex);
                Float gain = onlineGains.get(id);
                fallbackToSynth(id, gain == null ? 0.90f : gain, player);
            }
        }, pauseMs);
    }

    private void addSound(String id, float gain) {
        float safeGain = clamp(gain);
        String url = getOnlineSoundUrl(id);
        if (url == null) {
            Log.w(TAG, "No online sample mapping for catalog ID: " + id);
            if (engine != null) engine.add(id, safeGain);
            Toast.makeText(this, "برای این صدا نمونهٔ آنلاین ثبت نشده است.", Toast.LENGTH_SHORT).show();
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
            player.setLooping(!isOneShotCall(id));
            float output = clamp(masterVolume * safeGain);
            player.setVolume(output, output);
            player.setOnPreparedListener(prepared -> {
                if (onlinePlayers.get(id) != prepared) return;
                try {
                    prepared.start();
                    Log.i(TAG, "CC0 sample started via MediaPlayer: " + id + " from " + url);
                } catch (RuntimeException ex) {
                    Log.e(TAG, "Could not start online sample: " + id, ex);
                    fallbackToSynth(id, safeGain, prepared);
                }
            });
            player.setOnCompletionListener(completed -> {
                if (isOneShotCall(id) && onlinePlayers.get(id) == completed) {
                    replayOneShotAfterPause(id, completed);
                }
            });
            player.setOnErrorListener((failed, what, extra) -> {
                Log.e(TAG, "Online sample failed: " + id + ", what=" + what + ", extra=" + extra + ", url=" + url);
                fallbackToSynth(id, safeGain, failed);
                Toast.makeText(this, "دریافت صدای «" + id + "» ناموفق بود؛ اتصال اینترنت را بررسی کن.", Toast.LENGTH_LONG).show();
                return true;
            });
            player.prepareAsync();
            Log.i(TAG, "Preparing sound sample via MediaPlayer: " + id);
        } catch (IOException | RuntimeException ex) {
            Log.e(TAG, "Could not prepare online sample: " + id + " from " + url, ex);
            fallbackToSynth(id, safeGain, player);
            Toast.makeText(this, "باز کردن نمونهٔ صوتی ناموفق بود؛ اینترنت را بررسی کن.", Toast.LENGTH_LONG).show();
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
