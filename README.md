# NAVA Player

A Persian-first Android music player with a premium dark interface and a local-first smart music assistant.

## Version 1.3.0

- Smart DJ mood mixes: calm, energy, focus, nostalgia, spiritual and personal taste.
- On-device music discovery using track metadata, genre detection, favorites and listening history.
- Natural-language-style smart search for moods and music categories.
- Genre-specific player themes and manual genre correction.
- Local listening profile saved on the device; no account or cloud upload is required for these features.
- Background playback with AndroidX Media3, lock-screen controls, shuffle, repeat, playback speed and sleep timer.
- Consistent multicolor NAVA launcher artwork for adaptive and legacy Android launchers.
- Google Play build target: Android 16 (API 36).

**AI note:** NAVA AI in this version is a local recommendation engine, not a connected generative-AI service. It does not upload your music or analyze audio with a cloud model.

## Build outputs

GitHub Actions creates two outputs on every push to `main`:

- `NAVA-Player-debug`: debug APK for direct device testing.
- `NAVA-Player-release-unsigned`: release AAB when upload-signing secrets have not been configured.

When the four upload-signing secrets are configured in the repository, the release workflow instead publishes `NAVA-Player-Google-Play-AAB`, a signed Android App Bundle for Play Console.

Required GitHub Actions secrets:

- `ANDROID_KEYSTORE_BASE64`: base64-encoded upload keystore file.
- `ANDROID_KEYSTORE_PASSWORD`: keystore password.
- `ANDROID_KEY_ALIAS`: upload key alias.
- `ANDROID_KEY_PASSWORD`: upload key password.

Never commit the keystore or its passwords to the repository. Keep a secure backup of the upload keystore. If the app has already been enrolled in Play App Signing or previously uploaded, preserve its existing upload key or follow Google's upload-key reset process; do not replace it casually.

## Google Play note

The release AAB must be signed with the app's registered upload key before it can be submitted to Play Console. A debug APK or an unsigned AAB is not a Play Store release package.
