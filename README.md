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


## Cardino — Persian animated greeting cards

A separate Android app module for creating personalized cards for weddings, condolences, birthdays, anniversaries, Nowruz and congratulations.

- Persian RTL editor with live animated preview.
- Editable recipient, headline, message and sender.
- Add a personal photo and an audio track from the phone.
- Export a still image or an animated WebM greeting where supported by the Android WebView.
- Share the generated file with WhatsApp and other Android sharing targets.
- The original NAVA Player app remains a separate module and is not replaced.

### Build Cardino

GitHub Actions builds a directly installable debug APK on changes to the `cardino/` module.

Open **Actions → Cardino Android APK → latest successful run → Artifacts → Cardino-Android-debug** and download the APK ZIP. Extract it on the Android phone and install the APK. You may need to allow installation from the browser or file manager used to open it.

The Cardino source is in `cardino/`; its build workflow is `.github/workflows/cardino.yml`.


### Cardino interactive delivery flow

Cardino now creates a **shareable interactive invitation page**, not a flattened picture or ordinary video:

1. The sender chooses an occasion and edits the recipient, message and event details.
2. A photo and a short audio excerpt can be embedded in the invitation data.
3. Cardino creates a unique link to `cardino-web/view.html`.
4. The recipient opens the link in a browser, sees a sealed envelope, and taps the seal.
5. The flap opens, the invitation rises into view, visual particles animate, and audio playback begins only after that tap.

The viewer is a responsive web page and does not require the recipient to install Cardino. GitHub Actions publishes the viewer to GitHub Pages through `.github/workflows/cardino-pages.yml`. **One-time setup is required:** in repository Settings → Pages, set Build and deployment → Source to **GitHub Actions**, then re-run the failed “Publish Cardino interactive cards” workflow. The connected GitHub integration cannot grant itself the repository-level permission to enable Pages automatically. The Android app uses the editor at `cardino/src/main/assets/creator.html`.

The cloud-backed Cardino flow stores card text and metadata in Supabase Postgres and uploads photo/audio files to Supabase Storage. The SMS/WhatsApp URL contains only a random 10-character code (served through the short /c/<code> route); it does not contain the music or image bytes. The recipient opens the public card page, taps the seal, and the full uploaded audio track starts after that user gesture. Legacy #d= links continue to work.

#### One-time cloud setup (required for short links and full songs)

1. Create a Supabase project.
2. In the Supabase SQL Editor, run the complete script in cardino-backend/supabase-setup.sql.
3. Copy your Supabase Project URL and Publishable key (the key beginning sb_publishable_) into both:
   - cardino/src/main/assets/cardino-config.js
   - cardino-web/cardino-config.js
4. Do not put a Supabase Secret key or service-role key in either file. The publishable key is public by design; access is limited by the included Row Level Security policies.
5. Rebuild the Android APK and allow the GitHub Pages workflow to publish the viewer.

Cards and media are public to people who have the link or the media URL. Do not use Cardino for private or sensitive material. The current client-side public-upload prototype has basic file-size, MIME-type and database-payload limits; before a public commercial launch, add server-side rate limiting, abuse protection, expiry/cleanup and stronger ownership controls. Each audio file is currently limited to 20 MiB by the Supabase bucket.


## Nava Madahi — نوای هیئت

A separate Persian RTL Android app for browsing online recitations, with a dedicated Hamid Alimi section and discovery cards for other maddahs.

- Search by reciter, title, and occasion; save favorites locally.
- Refreshes recent videos automatically from configured YouTube RSS channels.
- Keeps audio at the original publishing source; it does not mirror copyrighted recordings.
- Build the installable debug APK from **Actions → Nava Madahi Android APK → latest successful run → Artifacts → Nava-Madahi-Android-debug**.
- Source module: `madahi/`; catalog channels: `madahi/data/sources.json`.
- Automatic catalog refresh workflow: `.github/workflows/madahi-feed.yml`.

See [madahi/README.md](madahi/README.md) for details.


## Nava Madahi Plus — in-app recitation player

A separate installable Android app in `madahi-player/`, alongside (not replacing) the original `madahi/` app. It uses the official YouTube embedded player inside the app, opens supported radio archive pages in its own WebView, keeps old catalog entries and favorites, and checks for newly published catalog items about every 12 hours using Android WorkManager. Notifications require user permission and background timing is managed by Android.

Build: `gradle --no-daemon :madahi-player:assembleDebug`

APK workflow: **Actions → Nava Madahi Plus Android APK → Artifacts → Nava-Madahi-Plus-Android-debug**. See [madahi-player/README.md](madahi-player/README.md).
