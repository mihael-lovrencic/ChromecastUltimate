# Chromecast Ultimate

Android app for Chromecast device discovery and screen mirroring.

## Features

- **Chromecast Discovery**: Scan and discover nearby Chromecast devices
- **Media Casting**: Cast videos to Chromecast devices
- **Screen Mirroring**: Mirror Android screen using MediaProjection API
- **Remote Control**: Play/Pause/Stop/Seek controls
- **Firefox Extension Support**: Works with [firefox-chromecast-ultimate](https://github.com/mihael-lovrencic/firefox-chromecast-ultimate) extension

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

## Firefox Extension Integration

The app includes an HTTP server (port 5000) that integrates with the [Firefox Chromecast Ultimate](https://github.com/mihael-lovrencic/firefox-chromecast-ultimate) extension.

### Usage:
1. Install the Firefox extension from [firefox-chromecast-ultimate](https://github.com/mihael-lovrencic/firefox-chromecast-ultimate)
2. Run the Android app and tap "Start Server"
3. Make sure your Android device and Firefox browser are on the same network
4. The extension will discover your Android device as a Chromecast

### Server API Endpoints:
- `GET /devices` - List available Chromecast devices
- `POST /cast` - Cast video URL (body: `{"url": "..."}`)
- `POST /control` - Control playback (body: `{"action": "play|pause|stop"}`)
- `POST /seek` - Seek position (body: `{"value": milliseconds}`)
- `POST /volume` - Set volume (body: `{"value": 0.0-1.0}`)
- `POST /mirror` - Start tab mirroring
- `GET /status` - Get connection status

## Project Structure

```
ChromecastUltimate/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/castultimate/
│   │   │   ├── MainActivity.kt      # Main activity
│   │   │   ├── CastManager.kt       # Chromecast management
│   │   │   ├── CastServer.kt        # HTTP server for Firefox extension
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
- `ACCESS_NETWORK_STATE` - For network detection
- `ACCESS_WIFI_STATE` - For WiFi state
- `FOREGROUND_SERVICE` - For background screen capture
- `RECORD_AUDIO` - For audio capture during mirroring
- `CAMERA` - Not used, reserved for future
- `SYSTEM_ALERT_WINDOW` - For overlay features

## Usage

1. **Start Server**: Tap "Start Server" to enable Firefox extension integration
2. **Discover Devices**: Tap "Discover & Cast" to scan for Chromecast devices
3. **Mirror Screen**: Tap "Mirror Tab/Screen" to start screen mirroring

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
- Creates GitHub release with APK

## Tech Stack

- Kotlin 2.0.0
- Android SDK 34
- Google Play Services Cast Framework 21.2.0
- AndroidX AppCompat 1.6.1
- NanoHTTPD 2.3.1

## License

MIT License
