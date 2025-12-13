#### Best Practices

Since the FLIR SDK is compiled into this package at build time, you generally do not need to manage runtime downloads. The following guidelines help when including or updating SDK artifacts:

1. Verify that the SDK artifacts are present in the package (`android/Flir/libs/` and `ios/Flir/Frameworks/`) before building your app.
2. For development builds without a vendor SDK, set `FLIR_DISABLED=1` or compile without `FLIR_ENABLED` to use stubbed behavior.
3. When updating vendor binaries, follow the vendor release and copy the updated AAR/XCFramework files in place before building.
<!-- Runtime SDK download examples removed. The FLIR SDK is included at compile-time; see 'Bundled SDK' above. -->

#### Bundled SDK: Where to get and how to update vendor binaries

The FLIR SDK native binaries (Android AAR and iOS XCFramework) are included in this package at compile time under:

- Android: `android/Flir/libs/` (AAR files)
- iOS: `ios/Flir/Frameworks/` (XCFrameworks and related dylibs)


If you need to update the vendor binaries (for example, to a newer FLIR SDK release), download the official release artifacts or rely on the built-in postinstall hook which downloads the binary zips and extracts the native files into the module folders.

- Official artifacts: https://github.com/PraveenOjha/flir-sdk-binaries/releases
- Android archive (zip): https://github.com/PraveenOjha/flir-sdk-binaries/releases/download/v1.0.1/android.zip
- iOS archive (zip): https://github.com/PraveenOjha/flir-sdk-binaries/releases/download/v1.0.1/ios.zip

Automated download on package install
------------------------------------

This package includes a `postinstall` script which attempts to download the vendor zip archives and extract the artifacts when you run `npm install`. The hook will:

- Download `android.zip` and extract `.aar` files into `android/Flir/libs/`
- Download `ios.zip` and extract `.xcframework`/`.dylib`/`.framework` files into `ios/Flir/Frameworks/`

Important notes:
- The installer expects both `android/Flir/libs/` and `ios/Flir/Frameworks/` to already exist inside the installed package — it will NOT create those directories and will fail if they are missing. Do NOT delete those directories from the package.
- To skip automated download for CI or manual installs, set `FLIR_SDK_SKIP_DOWNLOAD=1` before `npm install`.
- If you prefer not to use the postinstall hook, you can manually download and extract the release zips into the same folders.

After extracting the archives, ensure the following files are in the right place:

- `android/Flir/libs/thermalsdk-release.aar`
- `android/Flir/libs/androidsdk-release.aar`
- `ios/Flir/Frameworks/ThermalSDK.xcframework` and related frameworks/dylibs

Build the app normally. There are no runtime downloads performed — the SDK is packaged with the module.

### Advanced: Google Play Feature Delivery (Android)

For production Android apps distributed via Google Play Store, you can use **Play Feature Delivery** to have Google host and serve the SDK binaries at zero cost.

**Benefits:**
- ✅ Zero hosting and bandwidth costs (Google serves the files)
- ✅ Faster downloads via Google's CDN
- ✅ Automatic updates with your app
- ✅ Already implemented in the code!

**Setup:**

1. Create a dynamic feature module in your Android project:
   ```
   android/
   ├── app/
   └── flir_sdk/              # New feature module
       ├── build.gradle.kts
       └── src/main/AndroidManifest.xml
   ```

2. See detailed setup guide: [PLAY_FEATURE_DELIVERY_GUIDE.md](./PLAY_FEATURE_DELIVERY_GUIDE.md)

3. The code automatically uses Play Feature Delivery when available, with GitHub download as fallback.

**When to use:**
- ✅ Production apps on Google Play Store
- ✅ Want zero hosting costs
- ❌ Development builds (use GitHub download instead)
- ❌ Apps distributed outside Play Store (use GitHub download)


### Prerequisites

- React Native 0.60+
- Android: minSdk 24, compileSdk 34
- iOS: iOS 13.0+

### Quick Install

#### Android (via local AARs / npm)

1. Ensure the AAR files are present under `android/Flir/libs/` (the package includes them by default). If you are updating the SDK, download `android.zip` from the release artifacts and extract the `.aar` files into `android/Flir/libs/`.

2. The Gradle configuration in `android/Flir/build.gradle.kts` references those `.aar` files (e.g., `api(files("libs/thermalsdk-release.aar"))`), so you don't need to add external repositories.

3. Sync your Gradle files or rebuild your Android app.

**✅ Android permissions are automatically merged!** The library includes:
- USB host feature (for FLIR ONE USB devices)
- Camera & Internet permissions (for network-based FLIR cameras)

No manual `AndroidManifest.xml` editing required!

#### iOS (via CocoaPods)

1. Add the following to your `Podfile`:

```ruby
# From npm package (recommended)
# The pod will be auto-linked via react-native config

# OR from GitHub repository
pod 'Flir', :git => 'https://github.com/PraveenOjha/Flir.git', :tag => '2.0.0'

# OR for local development
pod 'Flir', :podspec => '../path/to/Flir/Flir.podspec'
```

2. Run:

```bash
cd ios
pod install
```

Note: If you installed `ilabs-flir` via npm, `Podfile` autolinking will declare the `Flir` pod for your app automatically. To avoid duplicates, the published npm package will not contain `Flir.podspec` or in-repo podspecs; they are excluded with `.npmignore`. See `docs/MIGRATION_TO_NPM.md` for migration details if you previously used an in-repo `Flir.podspec`.

##### Building Without FLIR SDK (No Paid License)

If you don't have a paid FLIR developer license, you can build the app without the FLIR SDK. The module will provide fallback stub implementations:

```bash
# Set environment variable before pod install
FLIR_DISABLED=1 pod install

# Or in your Podfile:
ENV['FLIR_DISABLED'] = '1'
```

When `FLIR_DISABLED=1`:
- ✅ App compiles without FLIR SDK frameworks
- ✅ All module methods still work (return stubs/fallbacks)
- ✅ Fallback thermal-style gradient images are generated
- ❌ No actual thermal camera functionality

##### Including FLIR SDK (Paid License Required)

To enable full FLIR functionality:

1. Download ThermalSDK.xcframework from FLIR
2. Copy the following xcframeworks to `node_modules/ilabs-flir/ios/Flir/Frameworks/`:
   - `ThermalSDK.xcframework`
   - `libavcodec.61.dylib.xcframework`
   - `libavdevice.61.dylib.xcframework`
   - `libavfilter.10.dylib.xcframework`
   - `libavformat.61.dylib.xcframework`
   - `libavutil.59.dylib.xcframework`
   - `liblive666.dylib.xcframework`
   - `libswresample.5.dylib.xcframework`
   - `libswscale.8.dylib.xcframework`
3. Run `pod install` (without FLIR_DISABLED)

Automatic SDK download
----------------------

If you install this package from npm/yarn, a `postinstall` hook will try to download the SDK binaries for both iOS and Android automatically using the URLs in `sdk-manifest.json`. The files are placed as follows:

- iOS: `ios/Flir/Frameworks/` (xcframeworks)
- Android: `android/Flir/libs/` (AAR files)

Controls:
- Skip automatic download by setting: `FLIR_SDK_SKIP_DOWNLOAD=1`.
- Skip downloading if an expected artifact is already present: `--skip-if-present` when running the `fetch-binaries` script.
- Manual download: `npm run fetch-binaries -- --all` to force a manual download of both targets.


3. **Choose ONE of these options for iOS permissions:**

**Option A: Automatic (Recommended)** - Using React Native Config Plugin

Add to your `app.json`:

```json
{
  "plugins": ["ilabs-flir"]
}
```

Then run:
```bash
npx expo prebuild
```

✅ All Info.plist entries are **automatically added**!

**Option B: Manual Setup** - Add these entries to your `ios/YourApp/Info.plist`:

```xml
<!-- External Accessory Protocols for FLIR ONE devices -->
<key>UISupportedExternalAccessoryProtocols</key>
<array>
    <string>com.flir.rosebud.config</string>
    <string>com.flir.rosebud.frame</string>
    <string>com.flir.rosebud.fileio</string>
</array>

<!-- Bluetooth permissions for FLIR ONE Edge/Pro -->
<key>NSBluetoothAlwaysUsageDescription</key>
<string>This app requires Bluetooth to connect to FLIR thermal cameras</string>

<key>NSBluetoothPeripheralUsageDescription</key>
<string>This app uses Bluetooth to communicate with FLIR thermal imaging devices</string>
```

## Usage

### Device Discovery

```javascript
import { NativeModules, NativeEventEmitter } from 'react-native';

const FlirModule = NativeModules.FlirIOS || NativeModules.FlirAndroid;
const FlirEmitter = new NativeEventEmitter(FlirModule);

// Listen for device events
FlirEmitter.addListener('FlirDeviceConnected', (event) => {
  console.log('FLIR device connected:', event);
});

FlirEmitter.addListener('FlirDeviceDisconnected', (event) => {
  console.log('FLIR device disconnected:', event);
});

// Start discovering FLIR devices
FlirModule.startDiscovery();

// Stop discovery
FlirModule.stopDiscovery();
```

### Camera Connection

```javascript
// Connect to discovered device
await FlirModule.connect(identityObject);

// Disconnect
FlirModule.disconnect();

// Check connection status
const isConnected = await FlirModule.isDeviceConnected();
const deviceInfo = await FlirModule.getConnectedDeviceInfo();
```

### Temperature Measurement

```javascript
// Get temperature at specific point (x, y coordinates)
const temperature = await FlirModule.getTemperatureAt(100, 200);
console.log(`Temperature: ${temperature}°C`);

// Returns null if no thermal image is available
if (temperature !== null) {
  console.log(`Detected: ${temperature.toFixed(2)}°C`);
}
```

### Emulator Mode (Development)

```javascript
// Check if running in emulator mode
const isEmulator = await FlirModule.isEmulator();

// Force start emulator mode (for testing without hardware)
await FlirModule.startEmulatorMode();

// Get device information
const deviceInfo = await FlirModule.getConnectedDeviceInfo();
// Returns: "Emulator (FLIR ONE)" or "Physical device (FLIR ONE)"
```

## API Reference

### Methods

| Method | Parameters | Returns | Description |
|--------|-----------|---------|-------------|
| **Device Discovery** |
| `startDiscovery()` | - | `void` | Start scanning forFLIR devices (USB & Emulator) |
| `stopDiscovery()` | - | `void` | Stop device discovery |
| **Connection** |
| `connect(identity)` | `identity: object` | `Promise<boolean>` | Connect to a discovered FLIR device |
| `disconnect()` | - | `void` | Disconnect from current device |
| `isDeviceConnected()` | - | `Promise<boolean>` | Check if a physical device is connected |
| `getConnectedDeviceInfo()` | - | `Promise<string>` | Get info about connected device |
| **Temperature** |
| `getTemperatureAt(x, y)` | `x: number, y: number` | `Promise<number \| null>` | Get temperature at pixel coordinates |
| **Emulator** |
| `isEmulator()` | - | `Promise<boolean>` | Check if running in emulator mode |
| `startEmulatorMode()` | - | `Promise<boolean>` | Force start emulator mode |

### Events

Listen to these events using `NativeEventEmitter`:

| Event | Payload | Description |
|-------|---------|-------------|
| `FlirDeviceConnected` | `{ identity, deviceType, isEmulator }` | Fired when a FLIR device connects |
| `FlirDeviceDisconnected` | `{ identity, wasEmulator }` | Fired when a device disconnects |
| `FlirError` | `{ error, type, interface }` | Fired on discovery or connection errors |

### Thermal Image Output

FLIR cameras provide two image types:
- **Thermal Image (MSX)**: Color-mapped thermal data with visual details
- **Photo Image (DC)**: Standard visible light image

**Color Palettes**: The SDK supports multiple palettes:
- `iron` - Rainbow color map (red=hot, blue=cold) - Default
- `gray` - Grayscale/black-white temperature map
- `arctic`, `rainbow`, etc. - Additional palettes

> **Note**: Palette switching requires accessing the native ThermalImage API directly. This may be exposed in future versions.

## Detailed Usage Guide

### Complete Setup Example

Here's a complete React Native component that demonstrates the full FLIR workflow:

```javascript
import React, { useEffect, useState } from 'react';
import {
  View,
  Text,
  Button,
  StyleSheet,
  NativeModules,
  NativeEventEmitter,
  Alert,
} from 'react-native';

const FlirModule = NativeModules.FlirIOS || NativeModules.FlirAndroid;
const FlirEmitter = new NativeEventEmitter(FlirModule);

const FlirThermalCamera = () => {
  const [isDiscovering, setIsDiscovering] = useState(false);
  const [isConnected, setIsConnected] = useState(false);
  const [deviceInfo, setDeviceInfo] = useState('Not connected');
  const [temperature, setTemperature] = useState(null);
  const [isEmulator, setIsEmulator] = useState(false);

  useEffect(() => {
    // Set up event listeners
    const deviceConnected = FlirEmitter.addListener(
      'FlirDeviceConnected',
      (event) => {
        console.log('Device connected:', event);
        setIsConnected(true);
        setIsDiscovering(false);
        
        // Get device info after connection
        FlirModule.getConnectedDeviceInfo().then(info => {
          setDeviceInfo(info);
        });
      }
    );

    const deviceDisconnected = FlirEmitter.addListener(
      'FlirDeviceDisconnected',
      (event) => {
        console.log('Device disconnected:', event);
        setIsConnected(false);
        setDeviceInfo('Not connected');
        setTemperature(null);
      }
    );

    const deviceError = FlirEmitter.addListener(
      'FlirError',
      (event) => {
        console.error('FLIR Error:', event);
        Alert.alert('FLIR Error', event.error || 'Unknown error');
      }
    );

    // Check if we're in emulator mode
    FlirModule.isEmulator().then(setIsEmulator);

    // Cleanup listeners on unmount
    return () => {
      deviceConnected.remove();
      deviceDisconnected.remove();
      deviceError.remove();
      
      // Disconnect on unmount
      if (isConnected) {
        FlirModule.disconnect();
      }
    };
  }, []);

  const handleStartDiscovery = () => {
    setIsDiscovering(true);
    FlirModule.startDiscovery();
  };

  const handleStopDiscovery = () => {
    setIsDiscovering(false);
    FlirModule.stopDiscovery();
  };

  const handleDisconnect = () => {
    FlirModule.disconnect();
  };

  const handleStartEmulator = async () => {
    try {
      await FlirModule.startEmulatorMode();
      Alert.alert('Success', 'Emulator mode started');
    } catch (error) {
      Alert.alert('Error', 'Failed to start emulator mode');
    }
  };

  const handleGetTemperature = async () => {
    try {
      // Get temperature at center of image (adjust coordinates as needed)
      const temp = await FlirModule.getTemperatureAt(160, 120);
      
      if (temp !== null) {
        setTemperature(temp);
      } else {
        Alert.alert('Info', 'No thermal data available');
      }
    } catch (error) {
      Alert.alert('Error', 'Failed to get temperature');
    }
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>FLIR Thermal Camera</Text>
      
      <View style={styles.statusContainer}>
        <Text style={styles.statusLabel}>Status:</Text>
        <Text style={styles.statusValue}>
          {isConnected ? 'Connected' : isDiscovering ? 'Discovering...' : 'Disconnected'}
        </Text>
      </View>

      <View style={styles.statusContainer}>
        <Text style={styles.statusLabel}>Device:</Text>
        <Text style={styles.statusValue}>{deviceInfo}</Text>
      </View>

      {temperature !== null && (
        <View style={styles.statusContainer}>
          <Text style={styles.statusLabel}>Temperature:</Text>
          <Text style={styles.tempValue}>{temperature.toFixed(2)}°C</Text>
        </View>
      )}

      <View style={styles.buttonContainer}>
        {!isConnected ? (
          <>
            <Button
              title={isDiscovering ? 'Stop Discovery' : 'Start Discovery'}
              onPress={isDiscovering ? handleStopDiscovery : handleStartDiscovery}
            />
            <Button
              title="Start Emulator"
              onPress={handleStartEmulator}
            />
          </>
        ) : (
          <>
            <Button
              title="Get Temperature"
              onPress={handleGetTemperature}
            />
            <Button
              title="Disconnect"
              onPress={handleDisconnect}
              color="#d9534f"
            />
          </>
        )}
      </View>

      {isEmulator && (
        <Text style={styles.emulatorNote}>
          ℹ️ Running in emulator mode
        </Text>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 20,
    backgroundColor: '#fff',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    marginBottom: 20,
  },
  statusContainer: {
    flexDirection: 'row',
    marginBottom: 10,
  },
  statusLabel: {
    fontWeight: 'bold',
    width: 100,
  },
  statusValue: {
    flex: 1,
  },
  tempValue: {
    flex: 1,
    fontSize: 18,
    fontWeight: 'bold',
    color: '#f44336',
  },
  buttonContainer: {
    marginTop: 20,
    gap: 10,
  },
  emulatorNote: {
    marginTop: 20,
    fontStyle: 'italic',
    color: '#666',
  },
});

export default FlirThermalCamera;
```

### Step-by-Step Workflow

#### 1. Initialize Event Listeners

Always set up event listeners before starting discovery:

```javascript
import { NativeModules, NativeEventEmitter } from 'react-native';

const FlirModule = NativeModules.FlirIOS || NativeModules.FlirAndroid;
const FlirEmitter = new NativeEventEmitter(FlirModule);

// Listen for device connection
FlirEmitter.addListener('FlirDeviceConnected', (event) => {
  console.log('Connected:', event);
  // event.identity - Device identity object
  // event.deviceType - "device" or "emulator"
  // event.isEmulator - boolean
});

// Listen for disconnection
FlirEmitter.addListener('FlirDeviceDisconnected', (event) => {
  console.log('Disconnected:', event);
});

// Listen for errors
FlirEmitter.addListener('FlirError', (event) => {
  console.error('Error:', event.error);
  // event.type - "discovery" or "connection"
  // event.interface - Communication interface
});
```

#### 2. Start Device Discovery

```javascript
// Start scanning for FLIR devices
FlirModule.startDiscovery();

// Discovery will automatically emit events when devices are found
// On Android: Scans for USB devices and emulators
// On iOS: Scans for Lightning, BLE, and emulator devices
```

#### 3. Handle Device Connection

Devices connect automatically when discovered. You'll receive a `FlirDeviceConnected` event:

```javascript
FlirEmitter.addListener('FlirDeviceConnected', async (event) => {
  // Device is now connected
  console.log('Device ID:', event.identity.deviceId);
  console.log('Is Emulator:', event.isEmulator);
  
  // Get additional device information
  const info = await FlirModule.getConnectedDeviceInfo();
  console.log('Device Info:', info);
  
  // Check connection status
  const connected = await FlirModule.isDeviceConnected();
  console.log('Is Connected:', connected);
});
```

#### 4. Measure Temperature

Once connected, you can measure temperature at any point:

```javascript
// Get temperature at pixel coordinates (x, y)
const temp = await FlirModule.getTemperatureAt(160, 120);

if (temp !== null) {
  console.log(`Temperature: ${temp.toFixed(2)}°C`);
} else {
  console.log('No thermal data available');
}
```

**Important Notes**:
- Coordinates are in pixels relative to the thermal image
- Returns `null` if no thermal image is available
- Temperature is in Celsius
- For FLIR ONE: Thermal image is typically 160×120 pixels
- For other cameras: Check device specifications

#### 5. Disconnect

```javascript
// Disconnect from current device
FlirModule.disconnect();

// This will trigger a FlirDeviceDisconnected event
```

#### 6. Stop Discovery

```javascript
// Stop scanning for devices
FlirModule.stopDiscovery();
```

### Development Without Hardware (Emulator Mode)

You can test your app without a physical FLIR device:

```javascript
// Check if already in emulator mode
const isEmu = await FlirModule.isEmulator();

if (!isEmu) {
  // Force start emulator mode
  await FlirModule.startEmulatorMode();
}

// Emulator will provide simulated thermal data
// All APIs work the same as with real hardware
```

### Best Practices

#### 1. Always Clean Up Listeners

```javascript
useEffect(() => {
  const listeners = [
    FlirEmitter.addListener('FlirDeviceConnected', handleConnect),
    FlirEmitter.addListener('FlirDeviceDisconnected', handleDisconnect),
    FlirEmitter.addListener('FlirError', handleError),
  ];

  return () => {
    // Remove all listeners on unmount
    listeners.forEach(listener => listener.remove());
    
    // Disconnect device
    FlirModule.disconnect();
  };
}, []);
```

#### 2. Handle Connection State

```javascript
const [connectionState, setConnectionState] = useState('disconnected');
// States: 'disconnected', 'discovering', 'connected'

const handleStartDiscovery = () => {
  setConnectionState('discovering');
  FlirModule.startDiscovery();
};

FlirEmitter.addListener('FlirDeviceConnected', () => {
  setConnectionState('connected');
});

FlirEmitter.addListener('FlirDeviceDisconnected', () => {
  setConnectionState('disconnected');
});
```

#### 3. Error Handling

```javascript
try {
  const temp = await FlirModule.getTemperatureAt(x, y);
  
  if (temp === null) {
    // No thermal data (device not streaming yet)
    console.log('Waiting for thermal data...');
  } else {
    // Valid temperature
    setTemperature(temp);
  }
} catch (error) {
  console.error('Temperature measurement failed:', error);
}
```

#### 4. Temperature Sampling Rate

Avoid calling `getTemperatureAt` too frequently:

```javascript
// ❌ Bad: Calling too frequently
setInterval(() => {
  FlirModule.getTemperatureAt(x, y);
}, 16); // 60 FPS - too fast!

// ✅ Good: Reasonable sampling rate
setInterval(async () => {
  const temp = await FlirModule.getTemperatureAt(x, y);
  if (temp !== null) {
    setTemperature(temp);
  }
}, 500); // 2 Hz - good for most applications
```

### Common Use Cases

#### Use Case 1: Continuous Temperature Monitoring

```javascript
const [centerTemp, setCenterTemp] = useState(null);

useEffect(() => {
  if (!isConnected) return;

  // Poll temperature every 500ms
  const interval = setInterval(async () => {
    const temp = await FlirModule.getTemperatureAt(160, 120);
    if (temp !== null) {
      setCenterTemp(temp);
    }
  }, 500);

  return () => clearInterval(interval);
}, [isConnected]);
```

#### Use Case 2: Multi-Point Temperature Measurement

```javascript
const measureMultiplePoints = async () => {
  const points = [
    { x: 80, y: 60, name: 'Top Left' },
    { x: 240, y: 60, name: 'Top Right' },
    { x: 160, y: 120, name: 'Center' },
  ];

  const results = await Promise.all(
    points.map(async (point) => {
      const temp = await FlirModule.getTemperatureAt(point.x, point.y);
      return { ...point, temperature: temp };
    })
  );

  console.log('Temperature readings:', results);
  return results;
};
```

#### Use Case 3: Auto-Connect on App Start

```javascript
useEffect(() => {
  // Auto-start discovery when app loads
  FlirModule.startDiscovery();

  // Or use emulator if no device available
  setTimeout(async () => {
    const connected = await FlirModule.isDeviceConnected();
    if (!connected) {
      console.log('No device found, starting emulator');
      await FlirModule.startEmulatorMode();
    }
  }, 5000); // Wait 5 seconds for device

  return () => {
    FlirModule.stopDiscovery();
    FlirModule.disconnect();
  };
}, []);
```

### Troubleshooting

#### Problem: "No devices found"

**Android**:
- Ensure USB debugging is enabled
- Check USB cable is data-capable (not charge-only)
- Grant USB permissions when prompted
- Try unplugging and replugging the FLIR device

**iOS**:
- Ensure Lightning connector is clean
- Check Info.plist has External Accessory protocols
- For BLE devices: Enable Bluetooth and grant permissions
- Try force-quitting and restarting the app

#### Problem: "Permission denied"

**Android**:
```xml
<!-- Ensure these are in AndroidManifest.xml (auto-added by library) -->
<uses-feature android:name="android.hardware.usb.host" />
<uses-permission android:name="android.permission.CAMERA"/>
```

**iOS**:
```xml
<!-- Ensure these are in Info.plist -->
<key>NSBluetoothAlwaysUsageDescription</key>
<string>Required for FLIR cameras</string>
```

#### Problem: "Temperature returns null"

```javascript
// Wait for device to start streaming
FlirEmitter.addListener('FlirDeviceConnected', async (event) => {
  // Wait a moment for streaming to start
  setTimeout(async () => {
    const temp = await FlirModule.getTemperatureAt(160, 120);
    console.log('Temperature:', temp);
  }, 1000);
});
```

#### Problem: "App crashes on disconnect"

```javascript
// Always check connection before API calls
const getTemperatureSafely = async (x, y) => {
  const connected = await FlirModule.isDeviceConnected();
  
  if (!connected) {
    console.log('Device not connected');
    return null;
  }
  
  return await FlirModule.getTemperatureAt(x, y);
};
```

#### Problem: "Events not firing"

```javascript
// Ensure NativeEventEmitter is created with the module
const FlirModule = NativeModules.FlirIOS || NativeModules.FlirAndroid;
const FlirEmitter = new NativeEventEmitter(FlirModule); // ✅ Pass module

// ❌ Wrong:
const FlirEmitter = new NativeEventEmitter(); // No events will fire!
```

### Platform-Specific Notes

#### Android
- Supports USB FLIR ONE cameras
- Supports network-based FLIR cameras (ACE series)
- Requires physical device (emulator for development only)
- USB permissions handled automatically via `UsbPermissionHandler`

#### iOS
- Supports Lightning interface (FLIR ONE Classic)
- Supports Bluetooth LE (FLIR ONE Edge/Pro)
- Works on both device and simulator (with emulator mode)
- Requires Info.plist entries (auto-added via config plugin)

### Performance Tips

1. **Limit temperature polling frequency**: 1-2 Hz is sufficient for most apps
2. **Disconnect when not in use**: Save battery by disconnecting in background
3. **Use emulator for UI development**: Build UI without physical hardware
4. **Cache device info**: Don't call `getConnectedDeviceInfo()` repeatedly

## Publishing & CI

This library is distributed primarily via npm (JavaScript package). Android and iOS native binaries (AAR & XCFramework) are included in the package for compile-time integration.

Publishing and CI notes:
- If you need to publish the Android artifacts to a Maven repository (for example, for internal use), push the AARs to a Maven repository and update the Gradle configuration accordingly.
- If you run into duplicate-class issues such as `org.slf4j.*`, prefer excluding `org.slf4j:slf4j-api` at the app or module configuration:

```kotlin
configurations.all {
   exclude(group = "org.slf4j", module = "slf4j-api")
}
```

Additional changes may include publishing the AARs into `mavenLocal` during your DR/CI pipeline if you use CI that expects Maven artifacts.

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
