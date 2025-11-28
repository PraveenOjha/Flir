# Build Fix - On-Demand SDK Support

## Problem
The Android build was failing with `Could not find com.flir:thermalsdk:1.0.0` because it was trying to download the FLIR SDK from Maven repositories. Since this project uses an **on-demand download** architecture, the full SDK binaries are not bundled and should not be dependencies.

## Solution
We implemented a **Stub-based compilation** approach:

1. **Created `flir-stubs.jar`**
   - Contains empty "stub" classes matching the FLIR SDK signatures used in the code.
   - Allows `CameraHandler.java` and other files to compile without the real SDK.
   - Located in `android/Flir/libs/flir-stubs.jar`.

2. **Updated `build.gradle.kts`**
   - Removed `implementation` dependencies on `com.flir:...`
   - Added `compileOnly` dependency on local jars in `libs/`.
   - **Crucial**: `compileOnly` means the stubs are used for compilation but **NOT** included in the final app. This prevents runtime conflicts.

3. **Updated `package.json`**
   - Included `android/Flir/libs/` in the published package so consumers can also compile the project.

## How it works
1. **Build Time**: The compiler uses `flir-stubs.jar` to verify types and method calls.
2. **Runtime**: The stubs are NOT present. The app uses `FlirSDKLoader` to download the real SDK and load it.
3. **Result**: Small package size, no build errors, and full functionality when SDK is downloaded.

## Verification
To verify the fix:
1. Run `./verify-autolinking.sh` (I updated it to check for stubs too).
2. Try to build the Android project: `cd android && ./gradlew assembleRelease`.

## Files Created/Modified
- `android/Flir/libs/flir-stubs.jar` (New)
- `create_stubs.py` (Script to generate stubs)
- `android/Flir/build.gradle.kts` (Updated dependencies)
- `package.json` (Updated files list)
