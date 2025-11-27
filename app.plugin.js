const {
  withInfoPlist,
  withAndroidManifest,
  withDangerousMod,
  createRunOncePlugin,
} = require('@expo/config-plugins');
const fs = require('fs');
const path = require('path');

/**
 * FLIR Thermal SDK Config Plugin
 * 
 * Automatically adds required iOS Info.plist and Android AndroidManifest.xml 
 * entries for FLIR device support. This eliminates the need for manual editing.
 * 
 * Usage in app.json:
 * {
 *   "plugins": ["flir-thermal-sdk"]
 * }
 * 
 * Or with custom descriptions:
 * {
 *   "plugins": [
 *     ["flir-thermal-sdk", {
 *       "bluetoothAlwaysUsageDescription": "Custom description here",
 *       "bluetoothPeripheralUsageDescription": "Custom description here"
 *     }]
 *   ]
 * }
 */

const EXTERNAL_ACCESSORY_PROTOCOLS = [
  'com.flir.rosebud.config',
  'com.flir.rosebud.frame',
  'com.flir.rosebud.fileio',
];

const DEFAULT_BLUETOOTH_ALWAYS_DESCRIPTION =
  'This app requires Bluetooth to connect to FLIR thermal cameras via Bluetooth Low Energy';

const DEFAULT_BLUETOOTH_PERIPHERAL_DESCRIPTION =
  'This app uses Bluetooth to communicate with FLIR thermal imaging devices';

/**
 * Adds FLIR-specific Info.plist entries for iOS
 */
const withFlirInfoPlist = (config, props = {}) => {
  return withInfoPlist(config, (config) => {
    const infoPlist = config.modResults;

    // Add External Accessory Protocols for FLIR ONE devices
    // These protocols enable Lightning interface communication (FLIR ONE Classic)
    // and prepare for Bluetooth LE devices (FLIR ONE Edge/Pro)
    if (!infoPlist.UISupportedExternalAccessoryProtocols) {
      infoPlist.UISupportedExternalAccessoryProtocols = [];
    }

    // Merge protocols without duplicates
    const existingProtocols = infoPlist.UISupportedExternalAccessoryProtocols;
    EXTERNAL_ACCESSORY_PROTOCOLS.forEach((protocol) => {
      if (!existingProtocols.includes(protocol)) {
        existingProtocols.push(protocol);
      }
    });

    // Add Bluetooth permissions for FLIR ONE Edge/Pro (BLE devices)
    // iOS 13+ requires NSBluetoothAlwaysUsageDescription
    if (!infoPlist.NSBluetoothAlwaysUsageDescription) {
      infoPlist.NSBluetoothAlwaysUsageDescription =
        props.bluetoothAlwaysUsageDescription ||
        DEFAULT_BLUETOOTH_ALWAYS_DESCRIPTION;
    }

    // Older iOS versions (pre-13) require NSBluetoothPeripheralUsageDescription
    if (!infoPlist.NSBluetoothPeripheralUsageDescription) {
      infoPlist.NSBluetoothPeripheralUsageDescription =
        props.bluetoothPeripheralUsageDescription ||
        DEFAULT_BLUETOOTH_PERIPHERAL_DESCRIPTION;
    }

    return config;
  });
};

/**
 * Adds FLIR-specific AndroidManifest.xml entries for Android
 */
const withFlirAndroidManifest = (config) => {
  return withAndroidManifest(config, (config) => {
    const androidManifest = config.modResults;
    const mainApplication = androidManifest.manifest;

    // Ensure uses-feature array exists
    if (!mainApplication['uses-feature']) {
      mainApplication['uses-feature'] = [];
    }

    // Ensure uses-permission array exists
    if (!mainApplication['uses-permission']) {
      mainApplication['uses-permission'] = [];
    }

    // Add USB host feature for FLIR ONE USB devices
    const usbHostFeature = {
      $: {
        'android:name': 'android.hardware.usb.host',
        'android:required': 'false',
      },
    };

    // Check if USB host feature already exists
    const hasUsbHost = mainApplication['uses-feature'].some(
      (feature) => feature.$?.['android:name'] === 'android.hardware.usb.host'
    );

    if (!hasUsbHost) {
      mainApplication['uses-feature'].push(usbHostFeature);
    }

    // Add camera permission for FLIR cameras
    const cameraPermission = {
      $: {
        'android:name': 'android.permission.CAMERA',
      },
    };

    // Check if camera permission already exists
    const hasCameraPermission = mainApplication['uses-permission'].some(
      (permission) => permission.$?.['android:name'] === 'android.permission.CAMERA'
    );

    if (!hasCameraPermission) {
      mainApplication['uses-permission'].push(cameraPermission);
    }

    return config;
  });
};

/**
 * Copies sdk-manifest.json to iOS project
 */
const withFlirManifest = (config) => {
  return withDangerousMod(config, [
    'ios',
    async (config) => {
      const src = path.join(__dirname, 'sdk-manifest.json');
      const dst = path.join(config.modRequest.platformProjectRoot, 'sdk-manifest.json');
      fs.copyFileSync(src, dst);
      return config;
    },
  ]);
};

/**
 * Copies sdk-manifest.json to Android assets
 */
const withFlirAndroidAssets = (config) => {
  return withDangerousMod(config, [
    'android',
    async (config) => {
      const src = path.join(__dirname, 'sdk-manifest.json');
      const dst = path.join(config.modRequest.platformProjectRoot, 'app/src/main/assets/sdk-manifest.json');
      fs.mkdirSync(path.dirname(dst), { recursive: true });
      fs.copyFileSync(src, dst);
      return config;
    },
  ]);
};

/**
 * Main plugin that combines iOS and Android configurations
 */
const withFlirThermalSDK = (config, props = {}) => {
  // Apply iOS modifications
  config = withFlirInfoPlist(config, props);
  config = withFlirManifest(config);

  // Apply Android modifications
  config = withFlirAndroidManifest(config);
  config = withFlirAndroidAssets(config);

  return config;
};

module.exports = createRunOncePlugin(
  withFlirThermalSDK,
  'flir-thermal-sdk',
  '2.0.0'
);
