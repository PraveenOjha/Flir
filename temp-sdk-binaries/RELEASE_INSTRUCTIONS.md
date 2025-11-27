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
