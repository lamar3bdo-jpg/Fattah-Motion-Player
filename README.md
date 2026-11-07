# Fattah Motion Player 1.1 (Android source)

Kotlin + Jetpack Compose + AndroidX Media3.

## Implemented
- MediaStore video library, folders, search, local thumbnails (Android 10+)
- ExoPlayer playback, resume, speed, picture-in-picture
- Touch gestures: horizontal seek, left vertical brightness, right vertical volume
- Embedded subtitle on/off where available
- Optional preference for 60 Hz display refresh

## Important limitations
This source does **not** implement SVP-style optical-flow frame interpolation. Setting 60 Hz does not convert 24/30 fps videos to true 60 fps. Real-time interpolation requires a dedicated GPU/native interpolation engine and device-specific performance testing. No APK has been compiled or verified in this environment.

## Build
Push the contents of this folder to a GitHub repository root, then run the included GitHub Actions workflow. Download the generated APK artifact and test on your device.
