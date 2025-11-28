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
 *   "plugins": ["ilabs-flir"]
 * }
 * 
 * Or with custom descriptions:
 * {
 *   "plugins": [
 *     ["ilabs-flir", {
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
 * Ensure the Flir Android Gradle module is included in generated projects.
 *
 * Some workflows (Expo prebuild) won't add node_modules subprojects to
 * settings.gradle automatically unless the package exposes autolinking or
 * the plugin performs the edit. When a consumer installs the package and
 * runs prebuild, include the Flir module and wire an app dependency so the
 * native bridge (FlirDownloadManager) is compiled into the app.
 */
const withFlirAndroidGradle = (config) => {
  return withDangerousMod(config, [
    'android',
    async (config) => {
      try {
        const projectRoot = config.modRequest.platformProjectRoot;
        const settingsGradlePath = path.join(projectRoot, 'settings.gradle');
        const appBuildGradlePath = path.join(projectRoot, 'app', 'build.gradle');

        const moduleRelPath = '../node_modules/ilabs-flir/android/Flir';
        const includeSnippet = `\n// ilabs-flir: include Flir module\nif (new File(rootProject.projectDir, '${moduleRelPath}').exists()) {\n    include ':Flir'\n    project(':Flir').projectDir = new File(rootProject.projectDir, '${moduleRelPath}')\n}\n`;

        if (fs.existsSync(settingsGradlePath)) {
          let settingsTxt = fs.readFileSync(settingsGradlePath, 'utf8');
          if (!/include\s*':Flir'/.test(settingsTxt)) {
            fs.appendFileSync(settingsGradlePath, includeSnippet, 'utf8');
          }
        }

        if (fs.existsSync(appBuildGradlePath)) {
          let buildTxt = fs.readFileSync(appBuildGradlePath, 'utf8');
          // Only add implementation project(':Flir') if it's not already present
          if (!/project\('\:Flir'\)/.test(buildTxt)) {
            const depSnippet = `\n    // ilabs-flir: include :Flir when available\n    if (new File(rootDir.getParent(), '${moduleRelPath}').exists()) {\n        implementation project(':Flir')\n    }\n`;

            // Find the first 'dependencies {' occurrence and find its matching closing brace
            const depIndex = buildTxt.search(/\bdependencies\s*\{/);
            if (depIndex !== -1) {
              // Walk forward to find the matching closing brace for the dependencies block
              let depth = 0;
              let insertPos = -1;
              for (let i = depIndex; i < buildTxt.length; i++) {
                const ch = buildTxt[i];
                if (ch === '{') depth++;
                else if (ch === '}') {
                  depth--;
                  if (depth === 0) { insertPos = i; break; }
                }
              }

              if (insertPos !== -1) {
                // Insert snippet just before the closing '}' of the dependencies block
                buildTxt = buildTxt.slice(0, insertPos) + depSnippet + buildTxt.slice(insertPos);
                fs.writeFileSync(appBuildGradlePath, buildTxt, 'utf8');
              } else {
                // Fallback: append to the file
                fs.appendFileSync(appBuildGradlePath, '\n' + depSnippet, 'utf8');
              }
            } else {
              // No dependencies block found! Append a new one with the snippet.
              fs.appendFileSync(appBuildGradlePath, '\n' + 'dependencies {' + depSnippet + '\n}\n', 'utf8');
            }
          }
        }
      } catch (err) {
        console.warn('[flir-config-plugin] Failed to patch Android Gradle files:', err && err.message);
      }

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
  // Ensure the Flir Gradle module is included in generated native projects
  config = withFlirAndroidGradle(config);

  return config;
};

module.exports = createRunOncePlugin(
  withFlirThermalSDK,
  'ilabs-flir',
  '2.0.2'
);
