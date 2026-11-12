package com.avanature.soundscape.v2;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Process;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

final class SoundscapeEngine {
    private static final int RATE = 44100;
    private static final int BLOCK = 1024;
    private final Map<String, Layer> layers = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private volatile boolean running = false;
    private volatile float master = 0.78f;
    private AudioTrack track;
    private Thread worker;

    synchronized void add(String id, float gain) {
        Layer layer = layers.get(id);
        if (layer == null) layers.put(id, new Layer(Math.max(0f, Math.min(1f, gain))));
        ensureStarted();
    }

    void remove(String id) { layers.remove(id); }
    void setVolume(float v) { master = Math.max(0f, Math.min(1f, v)); }
    void clear() { layers.clear(); }

    private synchronized void ensureStarted() {
        if (running) return;
        int min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int size = Math.max(min, BLOCK * 4);
        track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(size)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
        track.play();
        running = true;
        worker = new Thread(this::renderLoop, "AvaNature-Audio");
        worker.setPriority(Process.THREAD_PRIORITY_AUDIO);
        worker.start();
    }

    private void renderLoop() {
        short[] out = new short[BLOCK];
        while (running) {
            if (layers.isEmpty()) {
                java.util.Arrays.fill(out, (short) 0);
            } else {
                for (int i = 0; i < BLOCK; i++) {
                    float mixed = 0f;
                    for (Map.Entry<String, Layer> entry : layers.entrySet()) {
                        mixed += render(entry.getKey(), entry.getValue());
                    }
                    mixed *= master * 0.58f;
                    mixed = Math.max(-0.98f, Math.min(0.98f, mixed));
                    out[i] = (short) (mixed * 32767f);
                }
            }
            AudioTrack t = track;
            if (t != null && t.getState() == AudioTrack.STATE_INITIALIZED) {
                t.write(out, 0, out.length, AudioTrack.WRITE_BLOCKING);
            } else {
                try { Thread.sleep(10); } catch (InterruptedException ignored) { }
            }
        }
    }

    private float render(String id, Layer l) {
        float n = random.nextFloat() * 2f - 1f;
        float t = l.sample++ / (float) RATE;
        l.phase += 1.0 / RATE;
        double p = l.phase;
        float value;

        if (id.startsWith("rain") || id.equals("bass_rain") || id.equals("storm_wind")) {
            l.low += 0.012f * (n - l.low);
            l.mid += 0.075f * (n - l.mid);
            float drops = random.nextFloat() > (id.equals("rain_heavy") ? 0.986f : 0.996f)
                    ? (random.nextFloat() * 2f - 1f) * 0.9f : 0f;
            value = l.low * 0.72f + (l.mid - l.low) * 0.5f + n * 0.10f + drops;
            if (id.equals("bass_rain")) value = value * 0.25f + (float)Math.sin(2*Math.PI*47*p)*0.48f
                    + (float)Math.sin(2*Math.PI*71*p)*0.17f + drops*0.25f;
            if (id.equals("storm_wind")) value = value * 0.72f;
        } else if (id.equals("deep_thunder") || id.startsWith("thunder") || id.equals("sub_bass")) {
            l.low += 0.0018f * (n - l.low);
            double swell = 0.38 + 0.62 * Math.pow((Math.sin(2*Math.PI*p*0.085)+1)/2, 3);
            value = l.low * 2.8f + (float)Math.sin(2*Math.PI*(id.equals("sub_bass") ? 34 : 39)*p)*0.35f;
            value *= (float)swell;
            if (id.equals("thunder_close") && random.nextFloat() > 0.9994f) value += (n > 0 ? 1f : -1f) * 0.8f;
        } else if (id.equals("heartbeat")) {
            double q = p % 1.08;
            double beat1 = Math.exp(-Math.pow((q-0.10)/0.035,2));
            double beat2 = Math.exp(-Math.pow((q-0.28)/0.055,2));
            value = (float)(Math.sin(2*Math.PI*48*p) * (beat1*0.8 + beat2*0.65));
        } else if (id.equals("birdsong") || id.equals("owl") || id.equals("frog") || id.equals("cricket")
                || id.equals("wolf_howl") || id.equals("dog_bark") || id.equals("lion") || id.equals("horse")
                || id.equals("whale") || id.equals("cat_purr")) {
            value = animalTone(id, p, n, l);
        } else if (id.equals("waves") || id.equals("beach") || id.equals("ocean_underwater")) {
            l.low += 0.004f * (n-l.low);
            float swell = (float)(0.18 + 0.82 * (0.5 + 0.5*Math.sin(2*Math.PI*p*0.11)));
            value = (l.low*1.9f + n*0.12f) * swell;
        } else if (id.equals("forest_wind") || id.equals("low_wind") || id.equals("night_air") || id.equals("moon_wind")) {
            l.low += 0.0025f * (n-l.low);
            l.mid += 0.018f * (n-l.mid);
            value = l.low*1.9f + l.mid*0.25f;
        } else if (id.equals("stream") || id.equals("waterfall")) {
            l.mid += 0.09f * (n-l.mid);
            value = l.mid*0.78f + n*0.12f;
        } else if (id.equals("fire") || id.equals("campfire") || id.equals("embers")) {
            l.low += 0.015f * (n-l.low);
            float crackle = random.nextFloat() > 0.997f ? n*1.5f : 0f;
            value = l.low*0.7f + n*0.13f + crackle;
        } else if (id.equals("pink_noise") || id.equals("brown_noise")) {
            l.low += (id.equals("brown_noise") ? 0.0012f : 0.018f) * (n-l.low);
            value = id.equals("brown_noise") ? l.low*3.4f : l.low*1.15f + n*0.12f;
        } else if (id.equals("white_noise")) {
            value = n*0.42f;
        } else if (id.equals("breathing")) {
            l.low += 0.02f*(n-l.low);
            float envelope = (float)(0.15 + 0.75*(0.5+0.5*Math.sin(2*Math.PI*p*0.18)));
            value = l.low*envelope;
        } else if (id.equals("singing_bowl") || id.equals("space_drone") || id.equals("cosmic_noise")) {
            value = (float)(Math.sin(2*Math.PI*(id.equals("singing_bowl") ? 220 : 83)*p)*0.30
                    + Math.sin(2*Math.PI*(id.equals("singing_bowl") ? 331 : 127)*p)*0.12);
            value += n*0.04f;
        } else if (id.equals("cafe") || id.equals("crowd") || id.equals("city") || id.equals("train") || id.equals("footsteps")) {
            l.low += 0.006f*(n-l.low);
            value = l.low*1.2f + (float)Math.sin(2*Math.PI*(id.equals("footsteps")?1.8:2.7)*p)*0.08f + n*0.07f;
        } else {
            l.low += 0.01f*(n-l.low);
            value = l.low*0.8f + n*0.12f;
        }
        return value * l.gain;
    }

    private float animalTone(String id, double p, float noise, Layer l) {
        double cycle;
        double freq;
        if (id.equals("cat_purr")) {
            return (float)(Math.sin(2*Math.PI*27*p)*0.18 + Math.sin(2*Math.PI*54*p)*0.08) + noise*0.035f;
        }
        if (id.equals("wolf_howl") || id.equals("whale")) {
            cycle = p % (id.equals("wolf_howl") ? 5.5 : 7.0);
            double env = Math.max(0, Math.sin(Math.PI*cycle/(id.equals("wolf_howl") ? 4.2 : 5.5)));
            freq = id.equals("wolf_howl") ? 340 + 150*Math.sin(cycle*0.65) : 420 + 95*Math.sin(cycle*0.3);
            return (float)(Math.sin(2*Math.PI*freq*p)*env*0.42 + Math.sin(2*Math.PI*freq*1.48*p)*env*0.08);
        }
        if (id.equals("dog_bark") || id.equals("lion") || id.equals("horse")) {
            cycle = p % 2.6;
            double pulse = Math.max(0, 1 - Math.abs(cycle-(id.equals("lion")?0.45:0.28))*8);
            freq = id.equals("lion") ? 105 : (id.equals("horse") ? 210 : 165);
            return (float)(Math.sin(2*Math.PI*freq*p)*pulse*0.55 + noise*pulse*0.08);
        }
        if (id.equals("owl")) {
            cycle = p % 3.5;
            double pulse = Math.exp(-Math.pow((cycle-0.3)/0.18,2)) + 0.8*Math.exp(-Math.pow((cycle-1.0)/0.18,2));
            return (float)(Math.sin(2*Math.PI*430*p)*pulse*0.34);
        }
        if (id.equals("frog")) {
            cycle = p % 1.9;
            double pulse = Math.exp(-Math.pow((cycle-0.25)/0.16,2));
            return (float)(Math.sin(2*Math.PI*620*p)*pulse*0.25);
        }
        if (id.equals("cricket")) {
            double pulse = (Math.sin(2*Math.PI*4.5*p)>0.72)?1:0.12;
            return (float)(Math.sin(2*Math.PI*3800*p)*pulse*0.12);
        }
        cycle = p % 2.8;
        double local = cycle < 0.42 ? cycle : -1;
        if (local >= 0) {
            double env = Math.sin(Math.PI*local/0.42);
            freq = 1450 + 1300*(local/0.42);
            return (float)(Math.sin(2*Math.PI*freq*local)*env*0.28);
        }
        return noise*0.015f;
    }

    synchronized void close() {
        running = false;
        if (worker != null) {
            worker.interrupt();
            try { worker.join(300); } catch (InterruptedException ignored) { }
            worker = null;
        }
        if (track != null) {
            try { track.pause(); track.flush(); track.release(); } catch (Exception ignored) { }
            track = null;
        }
        layers.clear();
    }

    private static final class Layer {
        final float gain;
        long sample = 0;
        double phase = 0;
        float low = 0, mid = 0;
        Layer(float gain) { this.gain = gain; }
    }
}
