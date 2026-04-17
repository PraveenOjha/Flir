#!/usr/bin/env node
'use strict';
const https = require('https');
const http = require('http');
const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

let IOS_URL = 'https://github.com/PraveenOjha/flir-sdk-binaries/releases/download/v1.0.2/ios.zip';
let ANDROID_URL = 'https://github.com/PraveenOjha/flir-sdk-binaries/releases/download/v1.0.2/android.zip';
// Manifest override
try {
  const manifest = require(path.join(__dirname, '..', 'sdk-manifest.json'));
  if (manifest && manifest.ios && manifest.ios.directDownload && manifest.ios.directDownload.downloadUrl) {
    IOS_URL = manifest.ios.directDownload.downloadUrl;
  } else if (manifest && manifest.ios && manifest.ios.downloadUrl) {
    IOS_URL = manifest.ios.downloadUrl;
  }
  if (manifest && manifest.android && manifest.android.directDownload && manifest.android.directDownload.downloadUrl) {
    ANDROID_URL = manifest.android.directDownload.downloadUrl;
  }
} catch (err) {
  // ignore - fall back to embedded constants
}
const TMP_DIR = path.join(__dirname, '..', '.tmp-fetch-binaries');
const DEST_IOS = path.join(__dirname, '..', 'ios', 'Flir', 'Frameworks');
const DEST_ANDROID = path.join(__dirname, '..', 'android', 'Flir', 'libs');

function ensureTmp() {
  if (!fs.existsSync(TMP_DIR)) fs.mkdirSync(TMP_DIR, { recursive: true });
}

function httpGetWithRedirect(url) {
  return new Promise((resolve, reject) => {
    const getter = url.startsWith('https://') ? https : http;
    getter.get(url, (res) => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        resolve(httpGetWithRedirect(res.headers.location));
      } else if (res.statusCode === 200) {
        resolve(res);
      } else {
        reject(new Error(`Request failed. Status code: ${res.statusCode}`));
      }
    }).on('error', reject);
  });
}

async function download(url, outPath) {
  return new Promise(async (resolve, reject) => {
    console.log(`Downloading ${url}`);
    try {
      const res = await httpGetWithRedirect(url);
      const file = fs.createWriteStream(outPath);
      res.pipe(file);
      res.on('end', () => file.end());
      file.on('finish', () => {
        console.log(`Saved to ${outPath}`);
        resolve(outPath);
      });
      file.on('error', reject);
    } catch (err) {
      reject(err);
    }
  });
}

function extractZip(zipPath, dest) {
  // We do NOT create `dest` if missing (user insisted). Fail if not exist.
  if (!fs.existsSync(dest)) {
    throw new Error(`Destination folder ${dest} does not exist. Please create it and re-run installation.`);
  }
  // Preferred: use a pure-Node extractor (adm-zip) so the script is platform-independent.
  try {
    const AdmZip = require('adm-zip');
    const zip = new AdmZip(zipPath);
    zip.extractAllTo(TMP_DIR, true);
    return;
  } catch (e) {
    // If adm-zip isn't available, fall back to system tools.
    console.log('adm-zip not available, falling back to system extraction tools');
  }
  // Next preference: system `unzip` (macOS, Linux, and many developer environments). If `unzip` is not available,
  // try `tar -xf` (some platforms provide bsdtar which can extract zip files), otherwise fail with a helpful message.
  const cmdExists = (cmd) => {
    try {
      if (process.platform === 'win32') {
        execSync(`where ${cmd}`, { stdio: 'ignore' });
      } else {
        execSync(`which ${cmd}`, { stdio: 'ignore' });
      }
      return true;
    } catch (_err) {
      return false;
    }
  };

  if (cmdExists('unzip')) {
    try {
      execSync(`unzip -o "${zipPath}" -d "${TMP_DIR}"`, { stdio: 'inherit' });
      return;
    } catch (err) {
      throw new Error(`Failed to extract zip using 'unzip'. Consider installing 'unzip' or adding 'adm-zip' package. Original error: ${err.message}`);
    }
  }

  // 'tar' can sometimes extract zip files (via bsdtar). Try it as a last resort.
  if (cmdExists('tar')) {
    try {
      if (process.platform === 'win32') {
        execSync(`tar -xf "${zipPath}" -C "${TMP_DIR}"`, { stdio: 'inherit' });
      } else {
        execSync(`tar -xf '${zipPath}' -C '${TMP_DIR}'`, { stdio: 'inherit' });
      }
      return;
    } catch (err) {
      throw new Error(`Failed to extract zip using 'tar'. Consider installing 'unzip' or add 'adm-zip' package. Original error: ${err.message}`);
    }
  }

  throw new Error(`No extractor found: please install 'unzip' or add the 'adm-zip' dependency in your project so the fetch script can extract SDK binaries.`);
}

function copyIosExtractedFiles(tmpFolder, destFolder) {
  if (!fs.existsSync(tmpFolder)) return;
  const entries = fs.readdirSync(tmpFolder, { withFileTypes: true });
  entries.forEach(entry => {
    const fullPath = path.join(tmpFolder, entry.name);
    if (entry.isDirectory()) {
      // xcframeworks are directories *.xcframework
      if (entry.name.endsWith('.xcframework') || entry.name.endsWith('.framework') || entry.name.endsWith('.dylib')) {
        const dest = path.join(destFolder, entry.name);
        console.log(`Copy/overwrite ${entry.name} -> ${destFolder}`);
        // remove existing
        if (fs.existsSync(dest)) fs.rmSync(dest, { recursive: true, force: true });
        fs.renameSync(fullPath, dest);
      } else {
        // recursively copy from nested folders
        copyIosExtractedFiles(fullPath, destFolder);
      }
    }
  });
}

function copyAndroidExtractedFiles(tmpFolder, destFolder) {
  if (!fs.existsSync(tmpFolder)) return;
  const entries = fs.readdirSync(tmpFolder, { withFileTypes: true });
  entries.forEach(entry => {
    const fullPath = path.join(tmpFolder, entry.name);
    if (entry.isFile() && entry.name.endsWith('.aar')) {
      const dest = path.join(destFolder, entry.name);
      console.log(`Copy/overwrite ${entry.name} -> ${destFolder}`);
      if (!fs.existsSync(destFolder)) {
        throw new Error(`Destination folder ${destFolder} does not exist. Please create it and re-run installation.`);
      }
      fs.copyFileSync(fullPath, dest);
    } else if (entry.isDirectory()) {
      copyAndroidExtractedFiles(fullPath, destFolder);
    }
  });
}

function hasAndroidAar(folder) {
  if (!fs.existsSync(folder)) return false;
  return fs.readdirSync(folder).some(f => f.endsWith('.aar'));
}

function hasIosFrameworks(folder) {
  if (!fs.existsSync(folder)) return false;
  return fs.readdirSync(folder).some(f => f.endsWith('.xcframework') || f.endsWith('.framework') || f.endsWith('.dylib'));
}

async function run() {
  try {
    if (process.env.FLIR_SDK_SKIP_DOWNLOAD === '1' || process.env.FLIR_SDK_SKIP_DOWNLOAD === 'true') {
      console.log('FLIR_SDK_SKIP_DOWNLOAD set; skipping binary fetch.');
      return;
    }

    // (previous 'ensure target folders exist' logic removed; handling now occurs after args parsing)

    ensureTmp();

    const argv = process.argv.slice(2);
    const args = {};
    for (let i = 0; i < argv.length; i++) {
      const cur = argv[i];
      if (cur.startsWith('--')) {
        if (cur.includes('=')) {
          const parts = cur.split('=');
          const key = parts.shift().replace(/^--/, '');
          const value = parts.join('=');
          args[key] = value;
        } else {
          // if following arg exists and doesn't start with '-', use it as the value
          const next = argv[i + 1];
          if (next && !next.startsWith('-')) {
            args[cur.replace(/^--/, '')] = next;
            i++;
          } else {
            args[cur.replace(/^--/, '')] = true;
          }
        }
      } else if (cur.startsWith('-')) {
        const key = cur.replace(/^-+/, '');
        const next = argv[i + 1];
        if (next && !next.startsWith('-')) {
          args[key] = next;
          i++;
        } else {
          args[key] = true;
        }
      } else {
        args[cur] = true;
      }
    }

    // Skip if present per-platform
    const skipIfPresent = args['skip-if-present'] || args['skipIfPresent'] || false;
    const platformArg = args['platform'] || args['p'] || 'all';

    // Determine whether to create missing dest directories automatically; default is true unless explicitly disabled
    const noCreate = process.env.FLIR_SDK_NO_CREATE_DEST === '1' || process.env.FLIR_SDK_NO_CREATE_DEST === 'true' || args['no-create-dest'] || args['noCreateDest'];
    if ((platformArg === 'all' || platformArg === 'android') && !fs.existsSync(DEST_ANDROID)) {
      if (noCreate) {
        throw new Error(`Android libs folder ${DEST_ANDROID} does not exist. Please create it (e.g. npm pack or ensure published package includes it).`);
      } else {
        console.log(`Android libs folder ${DEST_ANDROID} not found — creating it.`);
        fs.mkdirSync(DEST_ANDROID, { recursive: true });
      }
    }
    if ((platformArg === 'all' || platformArg === 'ios') && !fs.existsSync(DEST_IOS)) {
      if (noCreate) {
        throw new Error(`iOS Frameworks folder ${DEST_IOS} does not exist. Please create it before install.`);
      } else {
        console.log(`iOS Frameworks folder ${DEST_IOS} not found — creating it.`);
        fs.mkdirSync(DEST_IOS, { recursive: true });
      }
    }

    // Short circuit checks: if skipIfPresent set and files exist, skip per platform
    if (skipIfPresent && platformArg !== 'ios' && hasAndroidAar(DEST_ANDROID)) {
      console.log('Android AAR(s) detected in libs folder; skipping Android fetch.');
    } else if (platformArg === 'all' || platformArg === 'android') {
      // Download Android zip
      const androidZip = path.join(TMP_DIR, 'android.zip');
      await download(ANDROID_URL, androidZip);
      // Extract to TMP_DIR
      extractZip(androidZip, DEST_ANDROID);
      // Copy AARs from TMP_DIR to libs
      copyAndroidExtractedFiles(TMP_DIR, DEST_ANDROID);
      fs.rmSync(androidZip);
    }

    // Clean out TMP_DIR (remove all contents) to avoid conflicts for iOS
    fs.readdirSync(TMP_DIR).forEach(f => {
      const fp = path.join(TMP_DIR, f);
      try { fs.rmSync(fp, { recursive: true, force: true }); } catch (e) { }
    });

    if (skipIfPresent && platformArg !== 'android' && hasIosFrameworks(DEST_IOS)) {
      console.log('iOS frameworks detected in Frameworks folder; skipping iOS fetch.');
    } else if (platformArg === 'all' || platformArg === 'ios') {
      // Download iOS zip
      const iosZip = path.join(TMP_DIR, 'ios.zip');
      await download(IOS_URL, iosZip);
      extractZip(iosZip, DEST_IOS);
      // Move xcframeworks and dylibs
      copyIosExtractedFiles(TMP_DIR, DEST_IOS);
      fs.rmSync(iosZip);
    }

    // Cleanup tmp
    try { fs.rmSync(TMP_DIR, { recursive: true, force: true }); } catch (e) { }

    console.log('FLIR SDK binaries fetched and installed into the package folders.');
  } catch (err) {
    console.error('Failed to fetch binaries:', err.message);
    process.exit(1);
  } finally {
    // Always attempt to remove temporary folder to avoid leaving cruft
    try { fs.rmSync(TMP_DIR, { recursive: true, force: true }); } catch (e) { }
  }
}

run();
