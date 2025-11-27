#!/bin/bash

# FLIR SDK Binaries Preparation Script
# This script prepares SDK binaries for upload to the flir-sdk-binaries repository

set -e

echo "🔧 FLIR SDK Binaries Preparation"
echo "=================================="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Create temporary directory for binaries
TEMP_DIR="$(pwd)/temp-sdk-binaries"
mkdir -p "$TEMP_DIR"

echo "📦 Step 1: Creating iOS SDK archive..."
cd ios/Flir/libs
zip -r "$TEMP_DIR/ios-sdk.zip" *.framework -x "*.gitkeep"
cd ../../..

echo "✅ iOS SDK archive created"
echo ""

echo "📦 Step 2: Creating Android SDK archive..."
cd android/Flir/libs
zip -r "$TEMP_DIR/android-sdk.zip" *.aar -x "*.gitkeep"
cd ../../..

echo "✅ Android SDK archive created"
echo ""

echo "🔐 Step 3: Calculating SHA256 checksums..."
echo ""

IOS_HASH=$(shasum -a 256 "$TEMP_DIR/ios-sdk.zip" | awk '{print $1}')
ANDROID_HASH=$(shasum -a 256 "$TEMP_DIR/android-sdk.zip" | awk '{print $1}')

IOS_SIZE=$(stat -f%z "$TEMP_DIR/ios-sdk.zip" 2>/dev/null || stat -c%s "$TEMP_DIR/ios-sdk.zip")
ANDROID_SIZE=$(stat -f%z "$TEMP_DIR/android-sdk.zip" 2>/dev/null || stat -c%s "$TEMP_DIR/android-sdk.zip")

echo -e "${GREEN}iOS SDK:${NC}"
echo "  File: $TEMP_DIR/ios-sdk.zip"
echo "  Size: $(numfmt --to=iec-i --suffix=B $IOS_SIZE 2>/dev/null || echo "$IOS_SIZE bytes")"
echo "  SHA256: $IOS_HASH"
echo ""

echo -e "${GREEN}Android SDK:${NC}"
echo "  File: $TEMP_DIR/android-sdk.zip"
echo "  Size: $(numfmt --to=iec-i --suffix=B $ANDROID_SIZE 2>/dev/null || echo "$ANDROID_SIZE bytes")"
echo "  SHA256: $ANDROID_HASH"
echo ""

echo "📝 Step 4: Creating updated sdk-manifest.json..."
cat > "$TEMP_DIR/sdk-manifest.json" << EOF
{
  "version": "1.0.0",
  "sdkVersion": "4.16.0",
  "ios": {
    "downloadUrl": "https://github.com/PraveenOjha/flir-sdk-binaries/releases/download/v1.0.0/ios-sdk.zip",
    "sha256": "$IOS_HASH",
    "sizeBytes": $IOS_SIZE,
    "frameworks": [
      "ThermalSDK.framework",
      "MeterLink.framework",
      "libavcodec.58.dylib.framework",
      "libavformat.58.dylib.framework",
      "libavutil.56.dylib.framework",
      "libswscale.5.dylib.framework",
      "libswresample.3.dylib.framework",
      "libavfilter.7.dylib.framework",
      "libavdevice.58.dylib.framework",
      "liblive666.dylib.framework"
    ]
  },
  "android": {
    "playFeatureModule": "flir_sdk",
    "directDownload": {
      "downloadUrl": "https://github.com/PraveenOjha/flir-sdk-binaries/releases/download/v1.0.0/android-sdk.zip",
      "sha256": "$ANDROID_HASH",
      "sizeBytes": $ANDROID_SIZE,
      "files": ["thermalsdk-release.aar", "androidsdk-release.aar"]
    }
  }
}
EOF

echo "✅ Updated manifest created at: $TEMP_DIR/sdk-manifest.json"
echo ""

echo "📋 Step 5: Creating GitHub release instructions..."
cat > "$TEMP_DIR/RELEASE_INSTRUCTIONS.md" << 'EOF'
# FLIR SDK Binaries Release Instructions

## 1. Create the Repository

```bash
# Create new repository on GitHub
gh repo create PraveenOjha/flir-sdk-binaries --public --description "FLIR SDK binaries for on-demand download"
```

## 2. Create GitHub Release

```bash
cd temp-sdk-binaries

# Create release
gh release create v1.0.0 \
  --repo PraveenOjha/flir-sdk-binaries \
  --title "FLIR SDK Binaries v1.0.0" \
  --notes "Initial release of FLIR SDK binaries for React Native wrapper on-demand download"

# Upload iOS SDK
gh release upload v1.0.0 ios-sdk.zip \
  --repo PraveenOjha/flir-sdk-binaries

# Upload Android SDK
gh release upload v1.0.0 android-sdk.zip \
  --repo PraveenOjha/flir-sdk-binaries
```

## 3. Update Main Repository

```bash
# Copy updated manifest to main repository
cp sdk-manifest.json ../sdk-manifest.json

# Commit and push
cd ..
git add sdk-manifest.json
git commit -m "Update sdk-manifest.json with actual SHA256 checksums"
git push
```

## 4. Verify Downloads

Test the download URLs:
- https://github.com/PraveenOjha/flir-sdk-binaries/releases/download/v1.0.0/ios-sdk.zip
- https://github.com/PraveenOjha/flir-sdk-binaries/releases/download/v1.0.0/android-sdk.zip

## 5. Publish to npm

```bash
npm publish
```
EOF

echo "✅ Release instructions created at: $TEMP_DIR/RELEASE_INSTRUCTIONS.md"
echo ""

echo -e "${GREEN}✨ All Done!${NC}"
echo ""
echo "📁 Files created in: $TEMP_DIR"
echo "   - ios-sdk.zip"
echo "   - android-sdk.zip"
echo "   - sdk-manifest.json (with real checksums)"
echo "   - RELEASE_INSTRUCTIONS.md"
echo ""
echo -e "${YELLOW}Next steps:${NC}"
echo "1. Review the files in $TEMP_DIR"
echo "2. Follow instructions in RELEASE_INSTRUCTIONS.md"
echo "3. Create GitHub repository and release"
echo "4. Update main repository with new sdk-manifest.json"
echo "5. Test the download functionality"
echo "6. Publish to npm"
echo ""
