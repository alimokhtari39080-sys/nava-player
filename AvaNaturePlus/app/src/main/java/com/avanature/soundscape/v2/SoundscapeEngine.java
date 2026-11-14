package com.avanature.soundscape.v2;

import android.media.AudioTrack;
import android.media.AudioManager;
import android.os.Process;
import android.util.Log;

import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

final class SoundscapeEngine {
    private static final String TAG = "AvaNatureAudio";
    private static final int RATE = 44100;
    private static final int BLOCK = 1024;

    private final Map<String, Layer> layers = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private volatile boolean running;
    private volatile float master = 0.78f;
    private volatile AudioTrack track;
    private Thread worker;

    synchronized void add(String id, float gain) {
        if (id == null || id.trim().isEmpty()) return;
        if (!layers.containsKey(id)) {
            layers.put(id, new Layer(Math.max(0f, Math.min(1f, gain))));
        }
        ensureStarted();
    }

    void remove(String id) {
        if (id != null) layers.remove(id);
    }

    void setVolume(float value) {
        master = Math.max(0f, Math.min(1f, value));
    }

    void clear() {
        layers.clear();
    }

    private synchronized void ensureStarted() {
        if (running) return;
        running = true;
        worker = new Thread(this::renderLoop, "AvaNature-Audio");
        worker.setPriority(Process.THREAD_PRIORITY_AUDIO);
        worker.start();
    }

    private void renderLoop() {
        AudioTrack localTrack = null;
        try {
            int minimum = AudioTrack.getMinBufferSize(
                    RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            if (minimum <= 0) minimum = BLOCK * 4;
            int bufferBytes = Math.max(minimum, BLOCK * 8);

            // Use the long-supported STREAM_MUSIC constructor and mono PCM.
            // This avoids device-specific Builder/stereo initialization failures.
            localTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    RATE,
                    android.media.AudioFormat.CHANNEL_OUT_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(bufferBytes, minimum),
                    AudioTrack.MODE_STREAM);

            if (localTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "AudioTrack could not initialize; audio disabled for this session.");
                return;
            }

            track = localTrack;
            localTrack.setVolume(1.0f);
            localTrack.play();
            Log.i(TAG, "AudioTrack playing as mono PCM on STREAM_MUSIC; state=" + localTrack.getPlayState() + ", session=" + localTrack.getAudioSessionId());

            short[] output = new short[BLOCK];
            while (running) {
                if (layers.isEmpty()) {
                    Arrays.fill(output, (short) 0);
                } else {
                    for (int i = 0; i < BLOCK; i++) {
                        float mixed = 0f;
                        for (Map.Entry<String, Layer> entry : layers.entrySet()) {
                            mixed += render(entry.getKey(), entry.getValue());
                        }
                        // Apply substantial make-up gain and a soft-knee limiter so quiet synthesized layers are audible.
                        mixed *= master * 12.0f;
                        mixed = (float) Math.tanh(mixed) * 0.82f;
                        short sample = (short) (mixed * 32767f);
                        output[i] = sample;
                    }
                }
                int written = localTrack.write(output, 0, output.length);
                if (written < 0) {
                    Log.e(TAG, "AudioTrack write failed: " + written);
                    break;
                }
            }
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ex) {
            Log.e(TAG, "Audio playback unavailable; keeping the app alive.", ex);
        } catch (RuntimeException ex) {
            Log.e(TAG, "Unexpected audio device error; keeping the app alive.", ex);
        } finally {
            running = false;
            track = null;
            if (localTrack != null) {
                try {
                    if (localTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                        localTrack.pause();
                    }
                    localTrack.flush();
                } catch (RuntimeException ignored) { }
                try {
                    localTrack.release();
                } catch (RuntimeException ignored) { }
            }
        }
    }

    private float render(String id, Layer layer) {
        float noise = random.nextFloat() * 2f - 1f;
        double time = layer.phase;
        layer.phase += 1.0 / RATE;

        if (id.equals("test_tone")) {
            return (float) (Math.sin(2 * Math.PI * 523.25 * time) * 0.72
                    + Math.sin(2 * Math.PI * 784.88 * time) * 0.12) * layer.gain;
        }

        if (id.startsWith("rain") || id.equals("bass_rain") || id.equals("storm_wind")) {
            layer.low += 0.012f * (noise - layer.low);
            layer.mid += 0.075f * (noise - layer.mid);
            float drops = random.nextFloat() > (id.equals("rain_heavy") ? 0.986f : 0.996f)
                    ? (random.nextFloat() * 2f - 1f) * 0.7f : 0f;
            float value = layer.low * 0.72f + (layer.mid - layer.low) * 0.5f + noise * 0.08f + drops;
            if (id.equals("bass_rain")) {
                value = layer.low * 0.18f
                        + (float) Math.sin(2 * Math.PI * 47 * time) * 0.40f
                        + (float) Math.sin(2 * Math.PI * 71 * time) * 0.14f
                        + drops * 0.20f;
            }
            return value * layer.gain;
        }

        if (id.contains("thunder") || id.equals("sub_bass")) {
            layer.low += 0.0018f * (noise - layer.low);
            double swell = 0.38 + 0.62 * Math.pow((Math.sin(2 * Math.PI * time * 0.085) + 1) / 2, 3);
            double frequency = id.equals("sub_bass") ? 34 : 39;
            float value = layer.low * 2.2f + (float) Math.sin(2 * Math.PI * frequency * time) * 0.25f;
            return (float) (value * swell) * layer.gain;
        }

        if (id.equals("heartbeat")) {
            double cycle = time % 1.08;
            double beat1 = Math.exp(-Math.pow((cycle - 0.10) / 0.035, 2));
            double beat2 = Math.exp(-Math.pow((cycle - 0.28) / 0.055, 2));
            return (float) (Math.sin(2 * Math.PI * 48 * time) * (beat1 * 0.65 + beat2 * 0.5)) * layer.gain;
        }

        if (id.equals("cat_purr")) {
            return ((float) Math.sin(2 * Math.PI * 27 * time) * 0.16f
                    + (float) Math.sin(2 * Math.PI * 54 * time) * 0.07f + noise * 0.02f) * layer.gain;
        }

        if (id.equals("wolf_howl") || id.equals("whale")) {
            double duration = id.equals("wolf_howl") ? 5.5 : 7.0;
            double cycle = time % duration;
            double envelope = Math.max(0, Math.sin(Math.PI * cycle / (duration * 0.78)));
            double frequency = id.equals("wolf_howl")
                    ? 340 + 120 * Math.sin(cycle * 0.65)
                    : 420 + 70 * Math.sin(cycle * 0.3);
            return (float) (Math.sin(2 * Math.PI * frequency * time) * envelope * 0.32
                    + Math.sin(2 * Math.PI * frequency * 1.48 * time) * envelope * 0.06) * layer.gain;
        }

        if (id.equals("dog_bark") || id.equals("lion") || id.equals("horse")) {
            double cycle = time % 2.6;
            double pulse = Math.max(0, 1 - Math.abs(cycle - 0.28) * 8);
            double frequency = id.equals("lion") ? 105 : (id.equals("horse") ? 210 : 165);
            return (float) (Math.sin(2 * Math.PI * frequency * time) * pulse * 0.40
                    + noise * pulse * 0.05) * layer.gain;
        }

        if (id.equals("owl") || id.equals("frog") || id.equals("cricket") || id.equals("birdsong")) {
            double frequency = id.equals("owl") ? 430 : (id.equals("frog") ? 620 : (id.equals("cricket") ? 3800 : 1850));
            double cycle = time % (id.equals("cricket") ? 0.22 : 2.8);
            double pulse = id.equals("cricket")
                    ? (Math.sin(2 * Math.PI * 4.5 * time) > 0.72 ? 1 : 0.12)
                    : Math.exp(-Math.pow((cycle - 0.3) / 0.22, 2));
            return (float) (Math.sin(2 * Math.PI * frequency * time) * pulse * 0.18) * layer.gain;
        }

        if (id.equals("waves") || id.equals("beach") || id.equals("ocean_underwater")) {
            layer.low += 0.004f * (noise - layer.low);
            float swell = (float) (0.18 + 0.82 * (0.5 + 0.5 * Math.sin(2 * Math.PI * time * 0.11)));
            return (layer.low * 1.7f + noise * 0.10f) * swell * layer.gain;
        }

        if (id.equals("forest_wind") || id.equals("low_wind") || id.equals("night_air") || id.equals("moon_wind")) {
            layer.low += 0.0025f * (noise - layer.low);
            layer.mid += 0.018f * (noise - layer.mid);
            return (layer.low * 1.6f + layer.mid * 0.22f) * layer.gain;
        }

        if (id.equals("stream") || id.equals("waterfall") || id.equals("rain_drips") || id.equals("rain_window")
                || id.equals("rain_roof") || id.equals("rain_car") || id.equals("rain_tent") || id.equals("rain_puddle")
                || id.equals("bass_drips")) {
            layer.mid += 0.09f * (noise - layer.mid);
            float drops = random.nextFloat() > 0.992f ? noise * 0.6f : 0f;
            return (layer.mid * 0.68f + noise * 0.08f + drops) * layer.gain;
        }

        if (id.equals("fire") || id.equals("campfire") || id.equals("embers")) {
            layer.low += 0.015f * (noise - layer.low);
            float crackle = random.nextFloat() > 0.997f ? noise * 0.8f : 0f;
            return (layer.low * 0.65f + noise * 0.10f + crackle) * layer.gain;
        }

        if (id.equals("pink_noise") || id.equals("brown_noise")) {
            layer.low += (id.equals("brown_noise") ? 0.0012f : 0.018f) * (noise - layer.low);
            return (id.equals("brown_noise") ? layer.low * 2.5f : layer.low * 1.05f + noise * 0.10f) * layer.gain;
        }

        if (id.equals("white_noise")) return noise * 0.35f * layer.gain;

        if (id.equals("breathing")) {
            layer.low += 0.02f * (noise - layer.low);
            float envelope = (float) (0.15 + 0.75 * (0.5 + 0.5 * Math.sin(2 * Math.PI * time * 0.18)));
            return layer.low * envelope * layer.gain;
        }

        if (id.equals("singing_bowl") || id.equals("space_drone") || id.equals("cosmic_noise")) {
            double base = id.equals("singing_bowl") ? 220 : 83;
            return ((float) Math.sin(2 * Math.PI * base * time) * 0.25f
                    + (float) Math.sin(2 * Math.PI * base * 1.5 * time) * 0.09f
                    + noise * 0.025f) * layer.gain;
        }

        if (id.equals("cafe") || id.equals("crowd") || id.equals("city") || id.equals("train") || id.equals("footsteps")
                || id.equals("leaves") || id.equals("hail")) {
            layer.low += 0.006f * (noise - layer.low);
            return (layer.low * 0.9f + noise * 0.06f) * layer.gain;
        }

        layer.low += 0.01f * (noise - layer.low);
        return (layer.low * 0.8f + noise * 0.08f) * layer.gain;
    }

    synchronized void close() {
        running = false;
        Thread oldWorker = worker;
        if (oldWorker != null) {
            oldWorker.interrupt();
            try { oldWorker.join(250); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
        worker = null;
        AudioTrack current = track;
        track = null;
        if (current != null) {
            try { current.pause(); } catch (RuntimeException ignored) { }
            try { current.flush(); } catch (RuntimeException ignored) { }
            try { current.release(); } catch (RuntimeException ignored) { }
        }
        layers.clear();
    }

    private static final class Layer {
        final float gain;
        double phase;
        float low;
        float mid;
        Layer(float gain) { this.gain = gain; }
    }
}
