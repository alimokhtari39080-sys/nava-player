#!/usr/bin/env python3
"""Generate compact, original procedural ambience WAVs for offline app playback."""
import math
import os
import random
import wave

ROOT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")
os.makedirs(ROOT, exist_ok=True)
RATE = 22050
SECONDS = 12
COUNT = RATE * SECONDS
random.seed(20261115)


def lowpass(src, cutoff):
    alpha = 1.0 - math.exp(-2.0 * math.pi * cutoff / RATE)
    out = [0.0] * len(src)
    prev = 0.0
    for i, value in enumerate(src):
        prev += alpha * (value - prev)
        out[i] = prev
    return out


def noise():
    return [random.uniform(-1.0, 1.0) for _ in range(COUNT)]


def save(name, samples, peak=0.72):
    maximum = max(max(abs(x) for x in samples), 1e-9)
    fade = int(RATE * 0.06)
    path = os.path.join(ROOT, name + ".wav")
    with wave.open(path, "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(RATE)
        frames = bytearray()
        for i, value in enumerate(samples):
            env = 1.0
            if i < fade:
                env = i / fade
            elif i >= COUNT - fade:
                env = (COUNT - 1 - i) / fade
            value = max(-1.0, min(1.0, value / maximum * peak * env))
            sample = int(value * 32767)
            frames.extend((sample & 255, (sample >> 8) & 255))
        out.writeframes(frames)


white = noise()
low = lowpass(white, 420)
high = [white[i] - low[i] for i in range(COUNT)]

# Rain: continuous broadband patter with many small irregular drops.
rain = [0.36 * high[i] + 0.14 * low[i] for i in range(COUNT)]
for _ in range(1900):
    start = random.randrange(COUNT - 160)
    length = random.randrange(20, 120)
    amp = random.uniform(0.08, 0.32)
    sign = random.choice((-1.0, 1.0))
    for j in range(length):
        rain[start + j] += sign * amp * math.sin(math.pi * j / length)
save("rain_soft", rain)

# Wind through trees: slow gusts, soft low rumble and airy leaf hiss.
wind_raw = noise()
wind_low = lowpass(wind_raw, 520)
wind_high = [wind_raw[i] - wind_low[i] for i in range(COUNT)]
wind = []
for i in range(COUNT):
    t = i / RATE
    gust = 0.46 + 0.27 * math.sin(2 * math.pi * t / 5.8) + 0.12 * math.sin(2 * math.pi * t / 2.7)
    wind.append(gust * (0.7 * wind_low[i] + 0.22 * wind_high[i]))
save("forest_wind", wind)

# Forest birds: quiet leafy bed plus short, varied rising chirps.
birds = [0.025 * high[i] for i in range(COUNT)]
for phrase in (0.6, 1.7, 3.2, 4.1, 6.6, 7.4, 9.3, 10.4):
    for chirp in range(random.randint(2, 4)):
        start = int((phrase + chirp * 0.19 + random.random() * 0.08) * RATE)
        length = int(random.uniform(0.07, 0.16) * RATE)
        f0 = random.uniform(1800, 3100)
        sweep = random.uniform(500, 1200)
        for j in range(length):
            t = j / RATE
            phase = 2 * math.pi * (f0 * t + sweep * t * t / (2 * length / RATE))
            env = math.sin(math.pi * j / length) ** 2
            birds[start + j] += 0.22 * math.sin(phase) * env
save("forest_birds", birds)

# Running water: bright rush with a gentle low current and small bubbling accents.
water = [0.26 * high[i] + 0.20 * low[i] for i in range(COUNT)]
for _ in range(260):
    start = random.randrange(COUNT - 500)
    length = random.randrange(70, 380)
    freq = random.uniform(420, 2100)
    amp = random.uniform(0.06, 0.19)
    decay = random.uniform(4.0, 12.0)
    for j in range(length):
        t = j / RATE
        water[start + j] += amp * math.sin(2 * math.pi * freq * t) * math.exp(-decay * t)
save("water_ambience", water)

# Distant thunder: deep filtered rumble with two broad swells.
low_high = lowpass(high, 180)
thunder = [0.24 * low[i] + 0.09 * low_high[i] for i in range(COUNT)]
for center, width, amp in ((2.8, 1.5, 0.55), (8.3, 1.9, 0.42)):
    for i in range(COUNT):
        t = i / RATE
        swell = math.exp(-((t - center) / width) ** 2)
        thunder[i] += amp * swell * low[i]
save("thunder_ambience", thunder)

# Fireplace: warm low flame bed and scattered sharp crackles.
fire = [0.22 * low[i] + 0.06 * high[i] for i in range(COUNT)]
for _ in range(280):
    start = random.randrange(COUNT - 150)
    length = random.randrange(15, 100)
    amp = random.uniform(0.12, 0.48)
    for j in range(length):
        fire[start + j] += amp * math.sin(math.pi * j / length) * random.choice((-1, 1))
save("fireplace", fire)

# Sleep/focus white noise.
save("white_noise", white)
print("Generated 7 offline WAV ambience assets in", ROOT)
