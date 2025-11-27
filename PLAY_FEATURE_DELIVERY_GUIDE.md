# Google Play Feature Delivery Setup Guide

## What is Play Feature Delivery?

Google Play Feature Delivery allows you to upload large SDK modules (up to 150MB each) that are hosted on Google's servers and downloaded on-demand. **No GitHub needed for Android!**

## Benefits

- ✅ **No bandwidth costs** - Google hosts and serves the files
- ✅ **Faster downloads** - Google's CDN is worldwide
- ✅ **Automatic updates** - Updates with your app
- ✅ **150MB limit per module** (your SDK is 100MB)
- ✅ **Already implemented in the code!**

## Setup Steps

### 1. Create Feature Module

Create a new module in your Android project:

```
android/
├── app/
├── flir_sdk/              # NEW - Feature module
│   ├── build.gradle.kts
│   └── src/main/
│       └── AndroidManifest.xml
```

**File: `android/flir_sdk/build.gradle.kts`**
```kotlin
plugins {
    id("com.android.dynamic-feature")
}

android {
    namespace = "com.flir.sdk.feature"
    compileSdk = 34
    
    defaultConfig {
        minSdk = 24
    }
}

dependencies {
    implementation(project(":app"))
    
    // Include the FLIR SDK AARs
    implementation(files("libs/thermalsdk-release.aar"))
    implementation(files("libs/androidsdk-release.aar"))
}
```

**File: `android/flir_sdk/src/main/AndroidManifest.xml`**
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:dist="http://schemas.android.com/apk/distribution">
    
    <dist:module
        dist:instant="false"
        dist:title="@string/flir_sdk_title">
        <dist:delivery>
            <dist:on-demand />
        </dist:delivery>
        <dist:fusing dist:include="true" />
    </dist:module>
</manifest>
```

### 2. Copy SDK Files to Feature Module

```bash
mkdir -p android/flir_sdk/libs
cp android/Flir/libs/*.aar android/flir_sdk/libs/
```

### 3. Update App's build.gradle

**File: `android/app/build.gradle.kts`**
```kotlin
android {
    // ... existing config
    
    dynamicFeatures = mutableSetOf(":flir_sdk")
}
```

### 4. Update settings.gradle

**File: `android/settings.gradle.kts`**
```kotlin
include(":app")
include(":flir_sdk")  // Add this line
```

### 5. Build and Upload to Play Console

```bash
# Build the app bundle (includes feature module)
cd android
./gradlew bundleRelease

# The .aab file will include the flir_sdk module
# Upload to Google Play Console as usual
```

### 6. How It Works

When users install your app:
1. **Base app installs** (~2MB without SDK)
2. User opens thermal camera feature
3. Your code calls: `FlirSDKLoader.downloadViaPlayStore(...)`
4. **Google Play downloads the SDK module** (100MB)
5. SDK is available!

### 7. Update Your React Native Code

The code already supports this! Just use:

```typescript
import { FlirDownload } from 'flir-thermal-sdk';

// This will automatically use Play Feature Delivery on Android
await FlirDownload.download((progress) => {
  console.log(`Downloading: ${progress.percent}%`);
});
```

## Fallback to Direct Download

The current implementation uses **direct download from GitHub** as a fallback. This works for:
- Development builds
- Apps not distributed via Google Play
- Testing

You can keep both methods:
- **Production (Play Store)**: Uses Play Feature Delivery
- **Development/Testing**: Uses GitHub direct download

## Cost Comparison

| Method | Hosting Cost | Bandwidth Cost | Speed |
|--------|-------------|----------------|-------|
| **Play Feature Delivery** | $0 (Google hosts) | $0 (Google serves) | Fast (Google CDN) |
| **GitHub Releases** | $0 (free tier) | $0 (unlimited for public repos) | Good |
| **Your Server** | $$$ | $$$ per GB | Varies |

## Limitations

- **150MB per module** (you're at 100MB ✅)
- **Only works for Play Store apps** (not for APKs distributed outside Play Store)
- **Requires Google Play Services** on device

---

## For iOS: There's No Official Equivalent

Apple doesn't have an equivalent to Play Feature Delivery. Options:

1. **App Store Asset Packs** (iOS 15+)
   - Limited to game assets
   - Not suitable for SDK binaries
   - Max 20GB total

2. **On-Demand Resources** (ODR)
   - For game assets, images, sounds
   - **Not for frameworks/SDKs** ❌
   
3. **Background Assets** (iOS 16+)
   - For ML models and large data
   - **Not for frameworks** ❌

**Best for iOS: GitHub Releases** (what we implemented)
- Free, unlimited bandwidth
- Works everywhere
- Simple implementation

---

## Recommended Approach

### Android
Use **Play Feature Delivery** for Play Store builds:
- Zero cost
- Google's infrastructure
- Already implemented in your code!

### iOS
Use **GitHub Releases** (current implementation):
- Free, unlimited bandwidth
- Works for all distribution methods
- Simple and reliable

### Both
Keep the direct download fallback for:
- Development builds
- Testing
- Non-Play Store Android distributions
