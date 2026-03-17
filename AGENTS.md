# AGENTS.md - Chromecast Ultimate Development Guide

## Overview
This is an Android Kotlin project for Chromecast device discovery and screen mirroring. It integrates with a Firefox extension via an embedded HTTP server.

## Build Commands

### Local Build
```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Clean build
./gradlew clean assembleDebug
```

### Running Tests
```bash
# Run all unit tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.example.castultimate.CastManagerTest"

# Run a single test method
./gradlew test --tests "com.example.castultimate.CastManagerTest.testIsConnected_whenNoSession_returnsFalse"

# Run tests with verbose output
./gradlew test --info

# Run Android instrumented tests
./gradlew connectedAndroidTest
```

### Linting
```bash
# Run Android lint
./gradlew lint

# Run lint with fix suggestions
./gradlew lintAnalyze
```

### Docker Build (for CI)
```bash
# Build with Docker
docker-compose run android-builder

# Run tests in Docker
docker-compose run android-test

# Full build
docker-compose up --build
```

## Code Style Guidelines

### Kotlin Conventions

#### Naming
- **Classes/Objects**: PascalCase (`CastManager`, `MediaProjectionManager`)
- **Functions**: camelCase (`startDiscovery`, `castVideo`)
- **Properties/Variables**: camelCase (`isConnected`, `serverUrl`)
- **Constants**: UPPER_SNAKE_CASE (`REQUEST_CODE_SCREEN_CAPTURE`)
- **Packages**: lowercase with dots (`com.example.castultimate`)

#### Formatting
- Use 4 spaces for indentation (Kotlin default)
- Maximum line length: 120 characters
- No semicolons at end of statements
- Use trailing commas for collections
- Use `//` for single-line comments, `/* */` for multi-line

#### Imports
- Sort imports alphabetically
- Group: android, kotlin, third-party, project
- No wildcard imports except for kotlin stdlib when necessary

#### Types
- Use nullable types with `?` explicitly
- Prefer `val` over `var`
- Use explicit return types for public functions
- Use `object` for singletons (see `CastManager`)
- Use companion objects for static members

#### Error Handling
- Use try-catch for operations that may fail
- Log errors with appropriate level (`Log.e`, `Log.w`)
- Provide meaningful error messages
- Never expose exceptions to UI without user-friendly messages

### Android-Specific Guidelines

#### Context Usage
- Pass `Context` explicitly to functions that need it
- Use `applicationContext` for long-lived operations
- Avoid memory leaks by not holding Activity context in long-lived objects

#### Lifecycle
- Release resources in `onDestroy` or `onDestroyView`
- Use `isCapturing` flag to track state
- Stop servers/services in lifecycle methods

#### Permissions
- Declare all permissions in `AndroidManifest.xml`
- Check permissions at runtime before sensitive operations
- Handle permission denial gracefully

#### UI
- Use `runOnUiThread` for UI updates from background threads
- Use ViewBinding or findViewById consistently
- Handle nullability of views properly

### Project Structure
```
app/src/main/java/com/example/castultimate/
├── MainActivity.kt          # Main entry point
├── CastManager.kt           # Chromecast device management (singleton)
├── CastServer.kt            # HTTP server for Firefox extension
└── MediaProjectionManager.kt # Screen capture functionality

app/src/test/java/com/example/castultimate/
└── CastManagerTest.kt       # Unit tests
```

### Testing Guidelines
- Write unit tests for business logic
- Test edge cases (null values, empty collections)
- Use descriptive test names: `test<Method>_<ExpectedBehavior>`
- Mock Android dependencies in unit tests

### Git Conventions
- Commit messages: imperative mood ("Add feature" not "Added feature")
- Branch naming: `feature/description` or `fix/description`
- One feature per PR
- Include issue numbers in commit messages when applicable

## Key Dependencies
- Kotlin 2.0.0
- Android SDK 34
- Google Play Services Cast Framework 21.2.0
- NanoHTTPD 2.3.1 (HTTP server)
- JUnit 4.13.2 (testing)

## Important Notes
- The app uses Java 21 for compilation but targets Android API 24+
- Server runs on port 5000 for Firefox extension integration
- MediaProjection requires runtime permission handling
- Chromecast API uses deprecated methods (acceptable for now)
