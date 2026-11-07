# Fattah Motion Player — Android prototype v0.1

An Android Studio Kotlin starter project for Fattah Motion Player.

## Included
- Dark, Arabic-first starter interface and F-centered launcher icon concept
- Open local videos using Android's document picker
- Media3 ExoPlayer playback and playback speed selector (0.05x–2x requested; actual support device-dependent)
- Swipe right side vertically for system media volume, left side for window brightness
- Left-side contrast mode **indicator only** (no shader/contrast effect yet)
- System Picture-in-Picture button and automatic PiP on Home when playing (device-dependent)
- Android audio-effect equalizer bands when the audio session supports them

## Not yet implemented
- AI frame interpolation / RIFE / Motion²-like smooth super slow motion
- Actual contrast processing, network library, video catalog, subtitles menu, full branded UI
- 60fps output guarantee or HDR codec guarantee (hardware-dependent)

## Build
Open this folder in Android Studio (JDK 17, Android SDK 35). Sync Gradle and use Build > Build APK(s). The project contains no generated Gradle wrapper or prebuilt APK; a local Gradle/Android toolchain is required. On some devices, low playback speeds may be clamped by audio processing.

## License note
The F logo is a prototype artwork derived from our generated visual concept. Use a separate foreground/background adaptive icon for final release.
