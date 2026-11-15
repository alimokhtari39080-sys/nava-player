# AvaNature Studio — آواطبیعت استودیو

A completely separate Android app that can be installed alongside the existing AvaNature and AvaNature Plus apps.

## Differences
- Separate Android application ID: `com.avanature.soundscape.studio`.
- Its own app title and custom leaf/headphone/rain launcher icon.
- Rain choices use separate CC0 audio tracks hosted by SFXMint, each with its own URL and independent MediaPlayer.
- Other sound categories continue using the local PCM sound engine.
- If an online rain track cannot be loaded, the app automatically falls back to its local synthesized rain layer.
- Internet is needed to stream the CC0 rain tracks.

## Rain source and licence
SFXMint states that its sounds are CC0 1.0, may be used in apps, and may be hotlinked:
- Gentle steady rain (62): https://sfxmint.com/dl/ambience-rain-62.mp3
- Heavy pouring rain (63): https://sfxmint.com/dl/ambience-rain-63.mp3
- Soft window-pattering rain (21): https://sfxmint.com/dl/ambience-rain-21.mp3
- Metal-roof drumming rain (15): https://sfxmint.com/dl/ambience-rain-15.mp3
- Light drizzle (17, 03): https://sfxmint.com/dl/ambience-rain-17.mp3 and https://sfxmint.com/dl/ambience-rain-03.mp3
- Leafy forest rain (32): https://sfxmint.com/dl/ambience-rain-32.mp3
- Distant rumbling rain (34): https://sfxmint.com/dl/ambience-rain-34.mp3

Build workflow: `.github/workflows/avanature-studio-android.yml`.
