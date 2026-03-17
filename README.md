# Chromecast Ultimate

Android app for Chromecast device discovery and screen mirroring.

## Features

- **Chromecast Discovery**: Scan and discover nearby Chromecast devices
- **Media Casting**: Cast videos to Chromecast devices
- **Screen Mirroring**: Mirror Android screen using MediaProjection API
- **Remote Control**: Play/Pause/Stop/Seek controls

## Requirements

- Android SDK 34
- Android Studio Arctic Fox or higher
- JDK 17+
- Gradle 8.5

## Setup

1. Clone the repository:
```bash
git clone https://github.com/mihael-lovrencic/ChromecastUltimate.git
```

2. Open in Android Studio or run:
```bash
./gradlew assembleDebug
```

3. Install APK:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Project Structure

```
ChromecastUltimate/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/castultimate/
│   │   │   ├── MainActivity.kt      # Main activity
│   │   │   ├── CastManager.kt       # Chromecast management
│   │   │   └── MediaProjectionManager.kt  # Screen capture
│   │   ├── res/
│   │   │   ├── layout/activity_main.xml
│   │   │   └── values/strings.xml
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
├── settings.gradle
└── .github/workflows/android-ci.yml
```

## Permissions

- `INTERNET` - For Chromecast communication
- `FOREGROUND_SERVICE` - For background screen capture
- `RECORD_AUDIO` - For audio capture during mirroring
- `CAMERA` - Not used, reserved for future
- `SYSTEM_ALERT_WINDOW` - For overlay features

## Usage

1. **Discover Devices**: Tap "Discover & Cast" to scan for Chromecast devices
2. **Mirror Screen**: Tap "Mirror Tab/Screen" to start screen mirroring

## Build

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run tests
./gradlew test
```

## CI/CD

GitHub Actions workflow runs on every push to `main` branch:
- Runs unit tests
- Builds debug APK
- Uploads artifacts

## Tech Stack

- Kotlin 2.0.0
- Android SDK 34
- Google Play Services Cast Framework 21.2.0
- AndroidX AppCompat 1.6.1

## License

MIT License
