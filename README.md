# FLIR Thermal SDK - React Native

A React Native wrapper for the FLIR Thermal SDK, providing thermal imaging capabilities for both Android and iOS applications.

[![](https://jitpack.io/v/PraveenOjha/Flir.svg)](https://jitpack.io/#PraveenOjha/Flir)

## Features

- 📱 Cross-platform support (Android & iOS)
- 🔥 Real-time thermal imaging
- 📸 Thermal image capture and processing
- 🎨 Customizable color palettes
- 📊 Temperature measurement and analysis

## Installation

### Prerequisites

- React Native 0.60+
- Android: minSdk 24, compileSdk 34
- iOS: iOS 13.0+

### Android Installation (via JitPack)

1. Add JitPack repository to your root `build.gradle`:

```gradle
allprojects {
    repositories {
        // ... other repositories
        maven { url 'https://jitpack.io' }
    }
}
```

2. Add the dependency to your app's `build.gradle`:

```gradle
dependencies {
    implementation 'com.github.PraveenOjha:Flir:1.0.0'
}
```

3. Sync your Gradle files.

### iOS Installation (via CocoaPods)

1. Add the following to your `Podfile`:

```ruby
# From GitHub repository (recommended)
pod 'Flir', :git => 'https://github.com/PraveenOjha/Flir.git', :tag => '1.0.0'

# OR for local development (adjust path to point to ios/flir/)
pod 'Flir', :podspec => '../path/to/Flir/ios/flir/Flir.podspec'
```

2. Run:

```bash
cd ios
pod install
```

**Note:** The podspec file is located at `ios/flir/Flir.podspec` in the repository.

## Usage

### Basic Setup

```javascript
import FlirModule from 'flir-thermal-sdk';

// Initialize FLIR camera
await FlirModule.initialize();

// Start streaming
await FlirModule.startStream();

// Stop streaming
await FlirModule.stopStream();

// Capture thermal image
const image = await FlirModule.captureImage();
```

### Camera Control

```javascript
// Connect to camera
await FlirModule.connectCamera();

// Disconnect camera
await FlirModule.disconnectCamera();

// Get camera status
const status = await FlirModule.getCameraStatus();
```

### Temperature Measurement

```javascript
// Get temperature at specific point
const temp = await FlirModule.getTemperatureAtPoint(x, y);

// Get temperature statistics
const stats = await FlirModule.getTemperatureStats();
```

### Color Palettes

```javascript
// Set color palette
await FlirModule.setPalette('iron'); // Options: iron, rainbow, arctic, lava

// Get available palettes
const palettes = await FlirModule.getAvailablePalettes();
```

## API Reference

### Methods

| Method | Parameters | Returns | Description |
|--------|-----------|---------|-------------|
| `initialize()` | - | `Promise<void>` | Initialize the FLIR SDK |
| `connectCamera()` | - | `Promise<void>` | Connect to FLIR camera |
| `disconnectCamera()` | - | `Promise<void>` | Disconnect from camera |
| `startStream()` | - | `Promise<void>` | Start thermal image streaming |
| `stopStream()` | - | `Promise<void>` | Stop thermal image streaming |
| `captureImage()` | - | `Promise<Image>` | Capture a thermal image |
| `getCameraStatus()` | - | `Promise<Status>` | Get current camera status |
| `getTemperatureAtPoint(x, y)` | `x: number, y: number` | `Promise<number>` | Get temperature at coordinates |
| `setPalette(name)` | `name: string` | `Promise<void>` | Set color palette |
| `getAvailablePalettes()` | - | `Promise<string[]>` | Get available color palettes |

## Publishing to JitPack

To publish a new version to JitPack:

1. Commit all your changes:
```bash
git add .
git commit -m "Release version 1.0.0"
```

2. Create a git tag:
```bash
git tag 1.0.0
git push origin 1.0.0
```

3. JitPack will automatically build your library when someone requests it for the first time.

### JitPack / CI notes for local AAR dependencies

If your module includes local AARs (for example the FLIR SDK binaries under `android/Flir/libs/`), CI environments such as JitPack will not automatically resolve file-based dependencies. To publish or build on JitPack you must ensure those AARs are available to the build system.

Two options:
- Publish the AARs to a repository (mavenLocal or a remote Maven repo) before building the module.
- Bundle the AARs into the final AAR using a "fat-AAR" approach.

The repository is configured to publish the FLIR SDK AARs into `mavenLocal` during the JitPack build (see `jitpack.yml`). That lets the `Flir` module resolve them by coordinates (`com.flir:thermalsdk:1.0.0` and `com.flir:androidsdk:1.0.0`) and prevents missing-class failures when JitPack builds the library.

### SLF4J duplicate-class conflict

If you see build errors about duplicate classes in `org.slf4j.*` (for example `Duplicate class org.slf4j.Logger`), this happens when:

- The vendor AAR (androidsdk/thermalsdk) embeds SLF4J classes inside the AAR's classes.jar;
- And your project or another dependency brings `org.slf4j:slf4j-api:...` as a separate jar. Gradle fails because the same classes exist twice.

Two ways to resolve this without editing the vendor AAR:

1) Exclude SLF4J API from your build (preferred when vendor AAR bundles SLF4J classes):

    In your module's build.gradle.kts (or in the consuming app), add:

    ```kotlin
    configurations.all {
         exclude(group = "org.slf4j", module = "slf4j-api")
    }
    ```

    This prevents Gradle from pulling `slf4j-api` into the classpath and avoids duplicates.

2) Provide a single canonical SLF4J provider at runtime (if your app needs the slf4j API):

    Add a single SLF4J implementation/binding (for example `org.slf4j:slf4j-android` or an appropriate binding) and ensure other copies are excluded.

Notes:
- We updated the Flir module to exclude `org.slf4j:slf4j-api` so it will not bring the API transitively. If you're still seeing duplicates in your app, check other dependencies and exclude slf4j there or use option 1.
- If you want me to, I can help you create a more robust fix (publishing Android AAR wrappers without embedded SLF4J or shading/relocating SLF4J) depending on your distribution needs.

4. Check build status at: `https://jitpack.io/#PraveenOjha/Flir`

### Notes for CI / JitPack

If the Android build succeeds locally but fails on JitPack/CI with an error like:

```
Error: Unable to access jarfile /home/jitpack/build/gradle/wrapper/gradle-wrapper.jar
```

this typically means the Gradle wrapper files are missing from the published repository. JitPack runs builds in a clean VM and expects the wrapper files to be present in the repo. To make the build reproducible on JitPack, ensure you commit the following files:

- `gradle/wrapper/gradle-wrapper.jar`
- `gradle/wrapper/gradle-wrapper.properties`
- `gradlew` (ensure executable bit is set)
- `gradlew.bat`

After committing those files, trigger a new JitPack build (or push a new tag). Avoid committing `local.properties` — it contains developer-specific SDK paths and will break CI if present.

## Publishing to CocoaPods

To publish to CocoaPods Trunk:

1. Register your CocoaPods account (first time only):
```bash
pod trunk register your-email@example.com 'Your Name'
```

2. Validate your podspec (run from repository root):
```bash
pod spec lint ios/flir/Flir.podspec --allow-warnings
```

3. Push to CocoaPods (run from repository root):
```bash
pod trunk push ios/flir/Flir.podspec --allow-warnings
```

4. Verify publication:
```bash
pod search Flir
```

**Note:** The `--allow-warnings` flag may be needed for vendored frameworks.

## Development

### Building Locally

#### Android

```bash
cd android
./gradlew build
```

#### iOS

```bash
cd ios/flir
pod install
xcodebuild -workspace Flir.xcworkspace -scheme Flir -configuration Release
```

### Testing

```bash
npm test
```

## Emulator Mode

This wrapper supports emulator mode for development and testing without requiring a physical FLIR device.

### Features

- **Device Detection**: Automatically detect if running on an emulator
- **Fallback Mode**: Use FLIR's built-in emulator when no physical device is available
- **Consistent API**: Same API calls work for both emulator and physical devices

### Usage

```javascript
import FlirModule from 'react-native-flir';

// Check if running in emulator mode
const isEmulator = await FlirModule.isEmulator();
console.log('Running in emulator:', isEmulator);

// Check if a physical device is connected
const isDeviceConnected = await FlirModule.isDeviceConnected();
console.log('Physical device connected:', isDeviceConnected);

// Get device information
const deviceInfo = await FlirModule.getConnectedDeviceInfo();
console.log('Device info:', deviceInfo);

// Force start emulator mode (useful for testing)
await FlirModule.startEmulatorMode();
```

### Emulator Features

- **Simulated Thermal Data**: Provides mock thermal imaging data
- **Temperature Readings**: Returns simulated temperature values
- **Device Events**: Emits connection/disconnection events like physical devices
- **Testing Environment**: Perfect for CI/CD and development without hardware

### Android Emulator Detection

The Android implementation detects emulators by checking:
- Build properties (`ro.build.fingerprint`, `ro.kernel.qemu`)
- Hardware characteristics
- Build model and manufacturer

### iOS Simulator Detection

The iOS implementation uses:
- FLIR SDK's built-in emulator interface
- Runtime environment detection
- Simulator-specific device identifiers

## Requirements

### Android
- Android SDK 24+
- Kotlin 1.9.0+
- Gradle 8.1.0+
- Java 21

### iOS
- iOS 13.0+
- Xcode 14+
- CocoaPods 1.10+

## FLIR SDK Licensing

This React Native wrapper is provided under the MIT license, but the FLIR thermal imaging SDKs have their own licensing requirements:

1. **Commercial Use**: Requires a commercial license from FLIR
2. **Development License**: Required even for development and testing
3. **Distribution**: You cannot distribute FLIR SDK libraries without proper licensing

**Important**: The FLIR SDK libraries included in this repository are placeholders. You must:
- Register at [FLIR Developer Portal](https://www.flir.com/developer/mobile-sdk/)
- Download your licensed SDK versions
- Replace the placeholder files with your licensed libraries

## License

**Wrapper Code**: MIT License (this React Native wrapper)
**FLIR SDK**: Proprietary license from FLIR Systems (see FLIR developer portal)

By using this wrapper, you agree to comply with FLIR's licensing terms and conditions. License - see LICENSE file for details

### Disclaimer

Flir SDKs may not be used to create apps intended for medical or health purposes; please refer to the SDK license agreement for more detailed information.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Support

For issues and questions:
- 🐛 [Report a bug](https://github.com/PraveenOjha/Flir/issues)
- 💡 [Request a feature](https://github.com/PraveenOjha/Flir/issues)
- 📖 [Documentation](https://github.com/PraveenOjha/Flir/wiki)

## Credits

Built with the FLIR Thermal SDK

---

Made with ❤️ by [Praveen Ojha](https://github.com/PraveenOjha)
