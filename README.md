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
pod 'Flir', :git => 'https://github.com/PraveenOjha/Flir.git', :tag => '1.0.0'
```

Or for local development:

```ruby
pod 'Flir', :path => '../node_modules/flir-thermal-sdk/ios/flir'
```

2. Run:

```bash
cd ios
pod install
```

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

4. Check build status at: `https://jitpack.io/#PraveenOjha/Flir`

## Publishing to CocoaPods

To publish to CocoaPods Trunk:

1. Register your CocoaPods account (first time only):
```bash
pod trunk register your-email@example.com 'Your Name'
```

2. Validate your podspec:
```bash
cd ios/flir
pod spec lint Flir.podspec
```

3. Push to CocoaPods:
```bash
pod trunk push Flir.podspec
```

4. Verify publication:
```bash
pod search Flir
```

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

## License

MIT License - see LICENSE file for details

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
