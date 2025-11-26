const {
  withInfoPlist,
  createRunOncePlugin,
} = require('@expo/config-plugins');

/**
 * FLIR Thermal SDK Config Plugin
 * 
 * Automatically adds required iOS Info.plist entries for FLIR device support.
 * This eliminates the need for manual Info.plist editing.
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
 * Adds FLIR-specific Info.plist entries
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

module.exports = createRunOncePlugin(
  withFlirInfoPlist,
  'flir-thermal-sdk',
  '1.0.0'
);
