# ilabs-flir (Modular Thermal Camera Plugin)

A professional, high-fidelity React Native wrapper for the FLIR Thermal SDK (iOS & Android). This package is designed as a standalone, "Lego-like" module that can be dropped into any React Native project to add professional-grade thermal imaging capabilities.

## 🚀 Installation

```bash
npm install ilabs-flir
```

### Automatic Native Configuration
This package includes a sync script that automatically patches your iOS `Info.plist` and Android `AndroidManifest.xml` with required FLIR permissions and hardware protocols.

```bash
node node_modules/ilabs-flir/scripts/sync-native.js
```

---

## 📺 Usage

### 1. The Thermal Preview Component
The most efficient way to display a live thermal stream. This component renders directly from the native buffer, bypassing the React Native bridge for maximum performance.

```javascript
import { ThermalPreview } from 'ilabs-flir';

const MyCameraApp = () => {
  return (
    <ThermalPreview 
      style={{ flex: 1 }} 
      onFrameUpdate={(event) => {
        // Optional: track frame metadata
        console.log("Frame size:", event.nativeEvent.width, event.nativeEvent.height);
      }}
    />
  );
}
```

### 2. Controlling the Camera
The `FlirModule` provides a robust API for managing the thermal lifecycle.

```javascript
import { FlirModule } from 'ilabs-flir';

// 1. Start discovery (finds USB-C, Lightning, and Edge/Wireless devices)
await FlirModule.startDiscovery();

// 2. Connect once a device is found
await FlirModule.connectToDevice(deviceId);

// 3. Change Palette
// Supported: "WhiteHot", "Iron", "Rainbow", "Arctic", "Lava", "Coldest", "Hottest", "Wheel"
await FlirModule.setPalette("Iron");

// 4. Get Spot Temperature
const temp = await FlirModule.getTemperatureAt(320, 240);
console.log(`Surface Temp: ${temp.toFixed(1)}°C`);
```

### 3. Accessing the Stream (Bitmap)
If you need to process thermal frames manually (e.g., for AI/ML or custom overlays), you can access the latest frame as a bitmap.

```javascript
// Get the latest frame as a temporary file path
const path = await FlirModule.getLatestFramePath();

// Get the latest frame as a Base64 string
const { base64 } = await FlirModule.getLatestFrameBitmap();
```

---

## 🏗 Modular Architecture (Zero-Entanglement)

`ilabs-flir` is built to be "Toggleable". In your main app, you can use **Reflection-based bridges** (Java/Objective-C) to interact with this module. This allows your main application to compile and run perfectly even if the `ilabs-flir` module is physically removed from the project.

### Why this matters:
- **Build Speed**: Disable thermal features in dev builds to speed up compilation.
- **App Store Compliance**: Toggle thermal permissions on/off based on build targets.
- **Modular Maintenance**: Update the FLIR SDK within this package without touching your core application logic.

---

## 📋 Requirements
- **iOS**: 13.0+, Physical device required (FLIR ONE/Edge).
- **Android**: SDK 24+, Kotlin 1.9+, Java 21+.
- **Hardware**: FLIR ONE Gen 3, FLIR ONE Pro, or FLIR ONE Edge Pro.

---

## 📄 License
**Plugin Wrapper**: MIT. 
**FLIR SDK**: Proprietary. You must comply with [FLIR's Developer Terms](https://www.flir.com/developer/mobile-sdk/).

---

Made with ❤️ by [Praveen Ojha](https://github.com/PraveenOjha)
