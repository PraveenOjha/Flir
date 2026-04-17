const {
  withInfoPlist,
  withAndroidManifest,
  withDangerousMod,
  withEntitlementsPlist,
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
 * Or with options:
 * {
 *   "plugins": [
 *     ["ilabs-flir", {
 *       "lightningOnly": true,  // iOS ONLY: Skip network/WiFi permissions
 *       "disableNetworkPermissions": true,  // Same as lightningOnly (iOS only)
 *       "bluetoothAlwaysUsageDescription": "Custom description",
 *       "localNetworkUsageDescription": "Custom description"
 *     }]
 *   ]
 * }
 * 
 * FLAGS:
 * - lightningOnly / disableNetworkPermissions (iOS ONLY): 
 *   Set to true to skip Local Network, Bonjour, and Bluetooth permissions on iOS.
 *   Use this if you only need Lightning-connected FLIR ONE devices
 *   and don't have a paid Apple Developer license.
 *   NOTE: This flag does NOT affect Android - Android always gets all permissions.
 */

// External Accessory Protocols for FLIR ONE Lightning devices
const EXTERNAL_ACCESSORY_PROTOCOLS = [
  'com.flir.rosebud.config',
  'com.flir.rosebud.frame',
  'com.flir.rosebud.fileio',
];

// Bonjour services for FLIR network discovery
const BONJOUR_SERVICES = [
  '_flir-ircam._tcp',
];

// Default permission descriptions
const DEFAULT_DESCRIPTIONS = {
  bluetoothAlways: 'This app requires Bluetooth to connect to FLIR ONE Edge and other wireless thermal cameras via Bluetooth Low Energy.',
  bluetoothPeripheral: 'This app uses Bluetooth to communicate with FLIR thermal imaging devices.',
  localNetwork: 'This app needs local network access to discover and connect to FLIR thermal cameras on your WiFi network.',
  camera: 'This app uses the camera to capture photos and video alongside thermal imaging.',
  accessoryConnection: 'This app connects to FLIR ONE thermal camera accessories.',
};

/**
 * Adds FLIR-specific Info.plist entries for iOS
 */
const withFlirInfoPlist = (config, props = {}) => {
  return withInfoPlist(config, (config) => {
    const infoPlist = config.modResults;

    // Check if network permissions should be skipped
    const skipNetworkPermissions = props.lightningOnly === true ||
      props.disableNetworkPermissions === true;

    if (skipNetworkPermissions) {
      console.log('[ilabs-flir] ⚠️  lightningOnly mode: Skipping network/WiFi permissions');
    }

    // =========================================================================
    // EXTERNAL ACCESSORY PROTOCOLS
    // Required for FLIR ONE devices connected via Lightning port
    // =========================================================================
    if (!infoPlist.UISupportedExternalAccessoryProtocols) {
      infoPlist.UISupportedExternalAccessoryProtocols = [];
    }

    const existingProtocols = infoPlist.UISupportedExternalAccessoryProtocols;
    EXTERNAL_ACCESSORY_PROTOCOLS.forEach((protocol) => {
      if (!existingProtocols.includes(protocol)) {
        existingProtocols.push(protocol);
      }
    });

    // =========================================================================
    // BLUETOOTH PERMISSIONS
    // Required for FLIR ONE Edge, FLIR ONE Pro, and other BLE thermal cameras
    // Skip if lightningOnly mode (BLE requires network discovery in some cases)
    // =========================================================================

    if (!skipNetworkPermissions) {
      // iOS 13+ requires NSBluetoothAlwaysUsageDescription
      if (!infoPlist.NSBluetoothAlwaysUsageDescription) {
        infoPlist.NSBluetoothAlwaysUsageDescription =
          props.bluetoothAlwaysUsageDescription || DEFAULT_DESCRIPTIONS.bluetoothAlways;
      }

      // Older iOS versions (pre-13) require NSBluetoothPeripheralUsageDescription
      if (!infoPlist.NSBluetoothPeripheralUsageDescription) {
        infoPlist.NSBluetoothPeripheralUsageDescription =
          props.bluetoothPeripheralUsageDescription || DEFAULT_DESCRIPTIONS.bluetoothPeripheral;
      }
    }

    // =========================================================================
    // LOCAL NETWORK PERMISSION (iOS 14+)
    // Required for discovering FLIR cameras over WiFi
    // NOTE: This requires a PAID Apple Developer account to work properly
    // SKIP if lightningOnly mode
    // =========================================================================
    if (!skipNetworkPermissions) {
      if (!infoPlist.NSLocalNetworkUsageDescription) {
        infoPlist.NSLocalNetworkUsageDescription =
          props.localNetworkUsageDescription || DEFAULT_DESCRIPTIONS.localNetwork;
      }

      // =========================================================================
      // BONJOUR SERVICES
      // Required for mDNS/Bonjour network discovery of FLIR cameras
      // SKIP if lightningOnly mode
      // =========================================================================
      if (!infoPlist.NSBonjourServices) {
        infoPlist.NSBonjourServices = [];
      }

      const existingBonjour = infoPlist.NSBonjourServices;
      BONJOUR_SERVICES.forEach((service) => {
        if (!existingBonjour.includes(service)) {
          existingBonjour.push(service);
        }
      });
    }

    // =========================================================================
    // CAMERA PERMISSION (always added)
    // Required if using visual camera alongside thermal
    // =========================================================================
    if (!infoPlist.NSCameraUsageDescription) {
      infoPlist.NSCameraUsageDescription =
        props.cameraUsageDescription || DEFAULT_DESCRIPTIONS.camera;
    }

    // =========================================================================
    // ACCESSORY CONNECTION (iOS 16.4+)
    // Only needed for wireless accessories - skip in lightningOnly mode
    // =========================================================================
    if (!skipNetworkPermissions) {
      if (!infoPlist.NSAccessorySetupUsageDescription) {
        infoPlist.NSAccessorySetupUsageDescription =
          props.accessorySetupUsageDescription || DEFAULT_DESCRIPTIONS.accessoryConnection;
      }
    }

    // =========================================================================
    // BACKGROUND MODES (always added for Lightning accessory)
    // Enable external accessory communication in background
    // =========================================================================
    if (!infoPlist.UIBackgroundModes) {
      infoPlist.UIBackgroundModes = [];
    }

    const backgroundModes = infoPlist.UIBackgroundModes;
    if (!backgroundModes.includes('external-accessory')) {
      backgroundModes.push('external-accessory');
    }

    return config;
  });
};

/**
 * Adds FLIR-specific entitlements for iOS
 * Required for FLIR One Edge Pro and network discovery
 */
const withFlirEntitlements = (config) => {
  return withEntitlementsPlist(config, (config) => {
    // 1. Access Wi-Fi Information
    config.modResults['com.apple.developer.networking.wifi-info'] = true;
    
    // 2. Hotspot Configuration (Required for FLIR Edge)
    config.modResults['com.apple.developer.networking.HotspotConfiguration'] = true;
    
    // 3. Wireless Accessory Configuration (Required for FLIR Edge)
    config.modResults['com.apple.external-accessory.wireless-configuration'] = true;
    
    return config;
  });
};

/**
 * Adds FLIR-specific AndroidManifest.xml entries for Android
 * NOTE: The lightningOnly flag does NOT apply to Android.
 * Android always gets all permissions since it doesn't have the same
 * Apple Developer license restrictions for network discovery.
 */
const withFlirAndroidManifest = (config, props = {}) => {
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

    // Helper to add feature
    const addFeature = (name, required = false) => {
      const hasFeature = mainApplication['uses-feature'].some(
        (f) => f.$?.['android:name'] === name
      );
      if (!hasFeature) {
        mainApplication['uses-feature'].push({
          $: {
            'android:name': name,
            'android:required': required ? 'true' : 'false',
          },
        });
      }
    };

    // Helper to add permission
    const addPermission = (name) => {
      const hasPermission = mainApplication['uses-permission'].some(
        (p) => p.$?.['android:name'] === name
      );
      if (!hasPermission) {
        mainApplication['uses-permission'].push({
          $: { 'android:name': name },
        });
      }
    };

    // USB host feature for FLIR ONE USB devices
    addFeature('android.hardware.usb.host', false);

    // Camera permission
    addPermission('android.permission.CAMERA');

    // Bluetooth features for FLIR ONE Edge/Pro
    addFeature('android.hardware.bluetooth', false);
    addFeature('android.hardware.bluetooth_le', false);

    // WiFi feature for network cameras
    addFeature('android.hardware.wifi', false);
    
    // OpenGL ES 3.0 requirement (mandatory for Atlas SDK rendering)
    const hasOpenGL = mainApplication['uses-feature'].some(
      (f) => f.$?.['android:glEsVersion'] === '0x00030000'
    );
    if (!hasOpenGL) {
      mainApplication['uses-feature'].push({
        $: {
          'android:glEsVersion': '0x00030000',
          'android:required': 'true',
        },
      });
    }

    // Network permissions (always added on Android)
    addPermission('android.permission.INTERNET');
    addPermission('android.permission.ACCESS_NETWORK_STATE');
    addPermission('android.permission.ACCESS_WIFI_STATE');
    addPermission('android.permission.CHANGE_WIFI_STATE');
    addPermission('android.permission.CHANGE_NETWORK_STATE');
    addPermission('android.permission.CHANGE_WIFI_MULTICAST_STATE');
    
    // Required for Wi-Fi discovery on Android 13+ (API 33)
    addPermission('android.permission.NEARBY_WIFI_DEVICES');
    
    addPermission('android.permission.BLUETOOTH');
    addPermission('android.permission.BLUETOOTH_ADMIN');
    addPermission('android.permission.BLUETOOTH_CONNECT');
    addPermission('android.permission.BLUETOOTH_SCAN');
    addPermission('android.permission.ACCESS_FINE_LOCATION'); // Required for BLE scanning

    // =========================================================================
    // USB AUTO-LAUNCH CONFIGURATION
    // Allows the app to open automatically when a FLIR USB device is attached.
    // =========================================================================
    const activity = mainApplication.application[0].activity[0];
    
    // 1. Add intent filter for USB_DEVICE_ATTACHED
    if (!activity['intent-filter']) activity['intent-filter'] = [];
    const hasUsbIntent = activity['intent-filter'].some(filter => 
      filter.action?.some(action => action.$['android:name'] === 'android.hardware.usb.action.USB_DEVICE_ATTACHED')
    );
    
    if (!hasUsbIntent) {
      activity['intent-filter'].push({
        action: [{ $: { 'android:name': 'android.hardware.usb.action.USB_DEVICE_ATTACHED' } }]
      });
    }
    
    // 2. Add meta-data pointing to the flir_usb_device_filter.xml
    if (!activity['meta-data']) activity['meta-data'] = [];
    const hasUsbMetadata = activity['meta-data'].some(meta => 
      meta.$['android:name'] === 'android.hardware.usb.action.USB_DEVICE_ATTACHED'
    );
    
    if (!hasUsbMetadata) {
      activity['meta-data'].push({
        $: {
          'android:name': 'android.hardware.usb.action.USB_DEVICE_ATTACHED',
          'android:resource': '@xml/flir_usb_device_filter'
        }
      });
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
      if (fs.existsSync(src)) {
        fs.copyFileSync(src, dst);
      }
      return config;
    },
  ]);
};

/**
 * Copies sdk-manifest.json to Android assets AND creates the USB filter XML
 */
const withFlirAndroidAssets = (config) => {
  return withDangerousMod(config, [
    'android',
    async (config) => {
      const projectRoot = config.modRequest.projectRoot;
      const platformRoot = config.modRequest.platformProjectRoot;
      
      // 1. Copy sdk-manifest.json
      const manifestSrc = path.join(__dirname, 'sdk-manifest.json');
      const manifestDst = path.join(platformRoot, 'app/src/main/assets/sdk-manifest.json');
      if (fs.existsSync(manifestSrc)) {
        fs.mkdirSync(path.dirname(manifestDst), { recursive: true });
        fs.copyFileSync(manifestSrc, manifestDst);
      }
      
      // 2. Create flir_usb_device_filter.xml (Required for auto-launch)
      const usbFilterXml = `<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- FLIR Vendor ID (2507) -->
    <usb-device vendor-id="2507" />
</resources>`;
      
      const xmlDst = path.join(platformRoot, 'app/src/main/res/xml/flir_usb_device_filter.xml');
      fs.mkdirSync(path.dirname(xmlDst), { recursive: true });
      fs.writeFileSync(xmlDst, usbFilterXml);
      
      return config;
    },
  ]);
};

/**
 * Patch Xcode project settings (Bitcode, Linker Flags)
 */
const withFlirXcodeSettings = (config) => {
  return withDangerousMod(config, [
    'ios',
    async (config) => {
      const { modRequest } = config;
      const xcodeProjectPath = path.join(modRequest.platformProjectRoot, 'ThermalCameraFx.xcodeproj/project.pbxproj');
      
      if (!fs.existsSync(xcodeProjectPath)) return config;
      
      let pbxproj = fs.readFileSync(xcodeProjectPath, 'utf8');
      
      // 1. Disable Bitcode (Required for FLIR SDK)
      // Matches: ENABLE_BITCODE = YES; -> ENABLE_BITCODE = NO;
      pbxproj = pbxproj.replace(/ENABLE_BITCODE = YES/g, 'ENABLE_BITCODE = NO');
      
      // 2. Add -ObjC to OTHER_LDFLAGS if not present
      // This is a bit tricky with regex, so we look for the OTHER_LDFLAGS line
      if (!pbxproj.includes('"-ObjC"') && !pbxproj.includes('-ObjC')) {
          pbxproj = pbxproj.replace(
              /OTHER_LDFLAGS = \(\n/g,
              'OTHER_LDFLAGS = (\n\t\t\t\t"-ObjC",\n'
          );
      }
      
      fs.writeFileSync(xcodeProjectPath, pbxproj, 'utf8');
      return config;
    },
  ]);
};

/**
 * Ensure the Flir Android Gradle module is included in generated projects.
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
          if (!/project\('\:Flir'\)/.test(buildTxt)) {
            const depSnippet = `\n    // ilabs-flir: include :Flir when available\n    if (new File(rootDir.getParent(), '${moduleRelPath}').exists()) {\n        implementation project(':Flir')\n    }\n`;

            const depIndex = buildTxt.search(/\bdependencies\s*\{/);
            if (depIndex !== -1) {
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
                buildTxt = buildTxt.slice(0, insertPos) + depSnippet + buildTxt.slice(insertPos);
                fs.writeFileSync(appBuildGradlePath, buildTxt, 'utf8');
              } else {
                fs.appendFileSync(appBuildGradlePath, '\n' + depSnippet, 'utf8');
              }
            } else {
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
  config = withFlirEntitlements(config);
  config = withFlirXcodeSettings(config);
  config = withFlirManifest(config);

  // Apply Android modifications
  config = withFlirAndroidManifest(config, props);
  config = withFlirAndroidAssets(config);
  config = withFlirAndroidGradle(config);

  return config;
};

module.exports = createRunOncePlugin(
  withFlirThermalSDK,
  'ilabs-flir',
  '2.0.3'
);
