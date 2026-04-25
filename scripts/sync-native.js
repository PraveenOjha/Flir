const fs = require('fs');
const path = require('path');

// This script applies native configuration (Info.plist, AndroidManifest) 
// to the parent project. This is used in "bare" React Native projects 
// that don't rely solely on Expo Prebuild.

const findProjectRoot = () => {
  let curr = __dirname;
  while (curr !== path.parse(curr).root) {
    if (fs.existsSync(path.join(curr, 'package.json'))) {
      const pkg = JSON.parse(fs.readFileSync(path.join(curr, 'package.json'), 'utf8'));
      if (pkg.name !== 'ilabs-flir') return curr;
    }
    curr = path.dirname(curr);
  }
  return null;
};

const projectRoot = findProjectRoot();
if (!projectRoot) {
  console.log('[ilabs-flir] Could not find parent project root. Skipping native sync.');
  process.exit(0);
}

const flirEnabled = true; // If this script is running, it's because the package is included.

console.log(`[ilabs-flir] Syncing native configuration for project at: ${projectRoot}`);

// 1. iOS: Info.plist
const findIosProjectName = () => {
  const iosDir = path.join(projectRoot, 'ios');
  if (!fs.existsSync(iosDir)) return null;
  const dirs = fs.readdirSync(iosDir);
  const xcodeProj = dirs.find(d => d.endsWith('.xcodeproj'));
  return xcodeProj ? xcodeProj.replace('.xcodeproj', '') : null;
};

const iosProjectName = findIosProjectName();
if (iosProjectName) {
  const infoPlistPath = path.join(projectRoot, 'ios', iosProjectName, 'Info.plist');
  if (fs.existsSync(infoPlistPath)) {
    let content = fs.readFileSync(infoPlistPath, 'utf8');
    
    // Add External Accessory Protocols if missing
    if (!content.includes('com.flir.rosebud.config')) {
      const protocols = `
	<key>UISupportedExternalAccessoryProtocols</key>
	<array>
		<string>com.flir.rosebud.config</string>
		<string>com.flir.rosebud.frame</string>
		<string>com.flir.rosebud.fileio</string>
	</array>`;
      
      if (content.includes('</dict>')) {
        content = content.replace('</dict>', `${protocols}\n</dict>`);
        console.log('[ilabs-flir] Added External Accessory protocols to Info.plist');
      }
    }
    
    // Add Background Mode if missing
    if (!content.includes('external-accessory') && content.includes('<key>UIBackgroundModes</key>')) {
        content = content.replace(/<key>UIBackgroundModes<\/key>\s*<array>/, '<key>UIBackgroundModes</key>\n\t<array>\n\t\t<string>external-accessory</string>');
        console.log('[ilabs-flir] Added external-accessory background mode');
    } else if (!content.includes('external-accessory')) {
        const bgMode = `
	<key>UIBackgroundModes</key>
	<array>
		<string>external-accessory</string>
	</array>`;
        content = content.replace('</dict>', `${bgMode}\n</dict>`);
    }

    fs.writeFileSync(infoPlistPath, content);
  }
}

// 2. Android: AndroidManifest.xml
const manifestPath = path.join(projectRoot, 'android/app/src/main/AndroidManifest.xml');
if (fs.existsSync(manifestPath)) {
  let content = fs.readFileSync(manifestPath, 'utf8');
  
  const permissions = [
    'android.permission.BLUETOOTH',
    'android.permission.BLUETOOTH_ADMIN',
    'android.permission.BLUETOOTH_CONNECT',
    'android.permission.BLUETOOTH_SCAN',
    'android.permission.ACCESS_FINE_LOCATION',
    'android.permission.INTERNET',
    'android.permission.ACCESS_NETWORK_STATE',
    'android.permission.ACCESS_WIFI_STATE',
    'android.permission.CHANGE_WIFI_STATE',
    'android.permission.CHANGE_WIFI_MULTICAST_STATE'
  ];

  permissions.forEach(perm => {
    if (!content.includes(perm)) {
      content = content.replace('</manifest>', `    <uses-permission android:name="${perm}" />\n</manifest>`);
    }
  });

  fs.writeFileSync(manifestPath, content);
  console.log('[ilabs-flir] Updated AndroidManifest.xml with required permissions');
}

console.log('[ilabs-flir] Native sync complete.');
