# NAVA Player

A Persian-first Android music player with a premium dark interface and a local-first smart music assistant.

## Version 1.2.0 — NAVA AI

- Smart DJ mood mixes: calm, energy, focus, nostalgia, spiritual and personal taste.
- On-device music discovery using track metadata, genre detection, favorites and listening history.
- Natural-language-style smart search for moods and music categories.
- Genre-specific player themes and manual genre correction.
- Local listening profile saved on the device; no account or cloud upload is required for these features.
- Background playback with AndroidX Media3, lock-screen controls, shuffle, repeat, playback speed and sleep timer.

**AI note:** NAVA AI in this version is a local recommendation engine, not a connected generative-AI service. It does not upload your music or analyze audio with a cloud model.

## Build

GitHub Actions builds a debug APK on every push to `main`.

1. Open the **Actions** tab in this repository.
2. Select the latest **Android APK** workflow run.
3. Download the `NAVA-Player-debug` artifact.
4. Extract the ZIP and install `app-debug.apk` on an Android device.

The debug APK is for testing. A separately signed release build is required for store distribution.
