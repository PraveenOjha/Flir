# 🚀 Manual Steps to Create FLIR SDK Binaries Repository

Since GitHub CLI (`gh`) is not installed, follow these manual steps:

## Step 1: Create Repository on GitHub

1. Go to https://github.com/new
2. Fill in:
   - **Repository name**: `flir-sdk-binaries`
   - **Description**: `FLIR SDK binaries for React Native on-demand download`
   - **Visibility**: Public
3. **DO NOT** initialize with README, .gitignore, or license
4. Click "Create repository"

## Step 2: Create a GitHub Release

1. Go to your new repository: https://github.com/PraveenOjha/flir-sdk-binaries
2. Click on "Releases" (right sidebar)
3. Click "Create a new release"
4. Fill in:
   - **Tag version**: `v1.0.0`
   - **Release title**: `FLIR SDK Binaries v1.0.0`
   - **Description**:
     ```
     Initial release of FLIR SDK binaries for React Native wrapper on-demand download.

     ## Contents

     - **ios-sdk.zip** (27 MiB) - iOS frameworks for FLIR Thermal SDK
       - ThermalSDK.framework
       - MeterLink.framework
       - FFmpeg libraries (libavcodec, libavformat, libavutil, etc.)
       
     - **android-sdk.zip** (100 MiB) - Android AARs for FLIR Thermal SDK
       - thermalsdk-release.aar
       - androidsdk-release.aar

     ## SHA256 Checksums

     - iOS: `3a349e5ea7d5868256d7f2e3da818909a6ed6f098e114bf435aac4ada1d7855b`
     - Android: `d4901615e39e396126bde0dbccd4a234e64cda1d6f9464a30e317e1c5cb9c5e9`

     ## Usage

     These binaries are automatically downloaded by the `flir-thermal-sdk` npm package when needed.
     ```

## Step 3: Upload Binaries

1. In the release creation page, scroll to "Attach binaries"
2. Drag and drop or click to upload:
   - `/home/praveen/Desktop/Flir/temp-sdk-binaries/ios-sdk.zip` (27 MiB)
   - `/home/praveen/Desktop/Flir/temp-sdk-binaries/android-sdk.zip` (100 MiB)
3. Wait for uploads to complete
4. Click "Publish release"

## Step 4: Verify Download URLs

After publishing, verify these URLs work:
- https://github.com/PraveenOjha/flir-sdk-binaries/releases/download/v1.0.0/ios-sdk.zip
- https://github.com/PraveenOjha/flir-sdk-binaries/releases/download/v1.0.0/android-sdk.zip

Test with:
```bash
curl -I https://github.com/PraveenOjha/flir-sdk-binaries/releases/download/v1.0.0/ios-sdk.zip
curl -I https://github.com/PraveenOjha/flir-sdk-binaries/releases/download/v1.0.0/android-sdk.zip
```

You should see `HTTP/2 302` (redirect) or `HTTP/2 200` (success).

## Step 5: Test the Download in Your App

Once the binaries are uploaded, test the download functionality:

```bash
# In your main Flir repository
cd /home/praveen/Desktop/Flir

# Test manual download
npm run download-sdk ios
npm run download-sdk android
```

Or test in a React Native app:

```typescript
import { FlirDownload } from 'flir-thermal-sdk';

const testDownload = async () => {
  const available = await FlirDownload.isAvailable();
  console.log('SDK Available:', available);
  
  if (!available) {
    const size = await FlirDownload.getDownloadSizeFormatted();
    console.log('Will download:', size);
    
    await FlirDownload.download((progress) => {
      console.log(`Progress: ${progress.percent.toFixed(0)}%`);
    });
    
    console.log('Download complete!');
  }
};
```

## ✅ What's Already Done

- ✅ Main repository pushed to GitHub
- ✅ Version tagged as v2.0.0
- ✅ SDK binaries prepared with checksums
- ✅ sdk-manifest.json updated with real SHA256 hashes

## 🎯 What You Need to Do

1. Create `flir-sdk-binaries` repository (5 minutes)
2. Upload the two ZIP files (5-10 minutes depending on internet)
3. Test the download URLs work
4. Optionally: Publish to npm with `npm publish`

## 📁 Files to Upload

Located in: `/home/praveen/Desktop/Flir/temp-sdk-binaries/`

- `ios-sdk.zip` (27 MiB)
- `android-sdk.zip` (100 MiB)

---

**That's it!** Once the binaries are uploaded, the on-demand download will work automatically. Users will download the SDKs from GitHub releases when they first call `FlirDownload.download()`.
