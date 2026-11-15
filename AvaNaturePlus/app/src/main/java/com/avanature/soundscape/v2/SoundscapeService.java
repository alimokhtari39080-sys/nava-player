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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

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
    // Rain runs through Android's standard MediaPlayer pipeline as a fallback
    // independent from the custom PCM AudioTrack synthesizer.
    private MediaPlayer rainPlayer;
    private final Map<String, Float> rainMediaLayers = new HashMap<>();
    private float masterVolume = 0.78f;
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
                updateRainPlayerVolume();
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

    private boolean isRainSound(String id) {
        return id != null && (id.startsWith("rain") || "bass_rain".equals(id));
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private void addSound(String id, float gain) {
        float safeGain = clamp(gain);
        if (!isRainSound(id)) {
            engine.add(id, safeGain);
            return;
        }
        rainMediaLayers.put(id, safeGain);
        if (!ensureRainPlayer()) {
            rainMediaLayers.remove(id);
            engine.add(id, safeGain);
            Log.w(TAG, "Rain MediaPlayer unavailable; using synthesized AudioTrack layer.");
            return;
        }
        updateRainPlayerVolume();
    }

    private void removeSound(String id) {
        if (rainMediaLayers.remove(id) != null) {
            if (rainMediaLayers.isEmpty()) {
                releaseRainPlayer();
            } else {
                updateRainPlayerVolume();
            }
        } else {
            engine.remove(id);
        }
    }

    private void clearAudioLayers() {
        rainMediaLayers.clear();
        releaseRainPlayer();
        if (engine != null) engine.clear();
    }

    private boolean ensureRainPlayer() {
        if (rainPlayer != null) {
            try {
                if (!rainPlayer.isPlaying()) rainPlayer.start();
                return true;
            } catch (IllegalStateException ex) {
                releaseRainPlayer();
            }
        }

        MediaPlayer player = null;
        try {
            File rainFile = new File(getCacheDir(), "avanature-rain-loop.wav");
            createRainWaveIfNeeded(rainFile);

            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            player.setDataSource(rainFile.getAbsolutePath());
            player.setLooping(true);
            player.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "Rain MediaPlayer error: what=" + what + ", extra=" + extra);
                if (rainPlayer == mp) rainPlayer = null;
                for (Map.Entry<String, Float> entry : rainMediaLayers.entrySet()) {
                    engine.add(entry.getKey(), entry.getValue());
                }
                rainMediaLayers.clear();
                try { mp.release(); } catch (RuntimeException ignored) { }
                return true;
            });
            player.prepare();
            rainPlayer = player;
            updateRainPlayerVolume();
            player.start();
            Log.i(TAG, "Rain loop started through Android MediaPlayer; duration=" + player.getDuration() + "ms");
            return true;
        } catch (IOException | RuntimeException ex) {
            Log.e(TAG, "Could not prepare the independent rain audio loop.", ex);
            if (player != null) {
                try { player.release(); } catch (RuntimeException ignored) { }
            }
            if (rainPlayer == player) rainPlayer = null;
            return false;
        }
    }

    private void updateRainPlayerVolume() {
        if (rainPlayer == null || rainMediaLayers.isEmpty()) return;
        float gain = 0f;
        for (float layerGain : rainMediaLayers.values()) gain = Math.max(gain, layerGain);
        float output = clamp(masterVolume * gain);
        try { rainPlayer.setVolume(output, output); }
        catch (RuntimeException ex) { Log.w(TAG, "Could not update rain volume", ex); }
    }

    private void releaseRainPlayer() {
        MediaPlayer player = rainPlayer;
        rainPlayer = null;
        if (player != null) {
            try {
                if (player.isPlaying()) player.stop();
            } catch (RuntimeException ignored) { }
            try { player.release(); } catch (RuntimeException ignored) { }
        }
    }

    // Build a deterministic, loopable rain ambience file locally. This keeps
    // the rain sound available offline and avoids relying on the PCM engine
    // for the most commonly used preset.
    private void createRainWaveIfNeeded(File file) throws IOException {
        final int sampleRate = 44100;
        final int seconds = 12;
        final int count = sampleRate * seconds;
        final int crossfade = sampleRate / 5;
        final int loopCount = count - crossfade;
        final int dataBytes = loopCount * 2;
        if (file.isFile() && file.length() == 44L + dataBytes) return;

        float[] raw = new float[count];
        Random random = new Random(0xA11CE5L);
        float low = 0f, mid = 0f, deep = 0f;
        for (int i = 0; i < count; i++) {
            float white = random.nextFloat() * 2f - 1f;
            low += 0.009f * (white - low);
            mid += 0.075f * (white - mid);
            deep += 0.0015f * (white - deep);
            double time = i / (double) sampleRate;
            float swell = (float) (0.82 + 0.12 * Math.sin(2 * Math.PI * time / 8.2)
                    + 0.06 * Math.sin(2 * Math.PI * time / 3.7));
            raw[i] = (deep * 0.28f + low * 0.42f + (mid - low) * 0.48f
                    + (white - mid) * 0.17f) * swell;
        }

        // Add many small, irregular droplet impacts over the continuous rain bed.
        for (int event = 0; event < 260; event++) {
            int start = random.nextInt(count - 5000);
            int length = 350 + random.nextInt(1700);
            float amplitude = 0.12f + random.nextFloat() * 0.24f;
            float frequency = 650f + random.nextFloat() * 3600f;
            float phase = random.nextFloat() * (float) (2 * Math.PI);
            float localLow = random.nextFloat() * 2f - 1f;
            for (int k = 0; k < length && start + k < count; k++) {
                float envelope = (float) Math.exp(-k / (length * 0.28f))
                        * (1f - k / (float) length);
                localLow += 0.16f * ((random.nextFloat() * 2f - 1f) - localLow);
                float tick = (float) Math.sin(2 * Math.PI * frequency * k / sampleRate + phase);
                raw[start + k] += amplitude * envelope * (0.72f * tick + 0.28f * localLow);
            }
        }

        // Move the loop seam into a cross-faded section so playback repeats smoothly.
        float[] loop = new float[loopCount];
        System.arraycopy(raw, crossfade, loop, 0, count - 2 * crossfade);
        for (int k = 0; k < crossfade; k++) {
            float t = k / (float) (crossfade - 1);
            float tail = raw[count - crossfade + k];
            float head = raw[k];
            loop[count - 2 * crossfade + k] = tail * (1f - t) + head * t;
        }

        float peak = 0.0001f;
        for (float sample : loop) peak = Math.max(peak, Math.abs(sample));
        float scale = 0.72f / peak;
        byte[] pcm = new byte[dataBytes];
        for (int i = 0; i < loopCount; i++) {
            int sample = (int) (Math.tanh(loop[i] * scale * 1.35f) * 26000f);
            pcm[i * 2] = (byte) (sample & 0xff);
            pcm[i * 2 + 1] = (byte) ((sample >> 8) & 0xff);
        }

        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create audio cache directory");
        }
        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
            out.write(new byte[]{'R','I','F','F'});
            writeIntLE(out, 36 + dataBytes);
            out.write(new byte[]{'W','A','V','E','f','m','t',' '});
            writeIntLE(out, 16);
            writeShortLE(out, 1);
            writeShortLE(out, 1);
            writeIntLE(out, sampleRate);
            writeIntLE(out, sampleRate * 2);
            writeShortLE(out, 2);
            writeShortLE(out, 16);
            out.write(new byte[]{'d','a','t','a'});
            writeIntLE(out, dataBytes);
            out.write(pcm);
        }
    }

    private static void writeIntLE(BufferedOutputStream out, int value) throws IOException {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
        out.write((value >> 16) & 0xff);
        out.write((value >> 24) & 0xff);
    }

    private static void writeShortLE(BufferedOutputStream out, int value) throws IOException {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
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
        rainMediaLayers.clear();
        releaseRainPlayer();
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
