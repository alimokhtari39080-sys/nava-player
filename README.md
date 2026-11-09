# NAVA Player

A Persian-first Android music player with a dark, premium interface and a custom NAVA launcher icon.

## Version 1.0

- Reads music stored on the device (with permission).
- Search by track title or artist.
- Favorites (long-press a track to add/remove).
- Background playback through AndroidX Media3 MediaSessionService.
- Notification / lock-screen media controls provided by the media session.
- Shuffle and repeat modes.
- Playback speed selection.
- Sleep timer.

## Build

GitHub Actions builds a debug APK on every push to `main`.

1. Open the **Actions** tab in this repository.
2. Select the latest **Android APK** workflow run.
3. Download the `NAVA-Player-debug` artifact.
4. Extract the ZIP and install `app-debug.apk` on an Android device.

The debug APK is for testing. A separately signed release build is required for store distribution.


## دسته‌بندی و تم هوشمند
نسخه 1.1.0 آهنگ‌ها را با توجه به عنوان، نام خواننده و نام آلبوم به دسته‌های مداحی، قدیمی، سنتی، پاپ، بی‌کلام و سایر تقسیم می‌کند. هر دسته رنگ و نشان مخصوص خود را دارد و تم پخش‌کننده با آهنگ در حال پخش هماهنگ می‌شود. برای اصلاح تشخیص، روی آهنگ نگه دارید و «تغییر سبک آهنگ» را انتخاب کنید؛ انتخاب دستی روی دستگاه ذخیره می‌شود.
