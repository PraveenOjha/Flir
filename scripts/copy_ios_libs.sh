#!/bin/bash
set -euo pipefail

# Script to copy necessary iOS frameworks into ios/Flir/libs
# by default ThermalSDK.framework is located under ios/Flir/

DEST_DIR="$(pwd)/ios/Flir/libs"
mkdir -p "$DEST_DIR"

# copy ThermalSDK.framework if it exists
SRC_THERMAL="$PWD/ios/Flir/ThermalSDK.framework"
if [ -d "$SRC_THERMAL" ]; then
  echo "Copying ThermalSDK.framework -> $DEST_DIR"
  rm -rf "$DEST_DIR/ThermalSDK.framework"
  cp -R "$SRC_THERMAL" "$DEST_DIR/"
else
  echo "Warning: $SRC_THERMAL not found. Please add ThermalSDK.framework to ios/Flir/libs manually or place it at ios/Flir/"
fi

# Optionally copy FFmpeg-ish frameworks if present in project root (paths in sample PBX):
for lib in libswscale.5.dylib.framework libavfilter.7.dylib.framework liblive666.dylib.framework libavdevice.58.dylib.framework libavcodec.58.dylib.framework libavformat.58.dylib.framework libswresample.3.dylib.framework libavutil.56.dylib.framework; do
  SRC_LIB="$PWD/../$lib"
  if [ -d "$SRC_LIB" ]; then
    echo "Copying $lib -> $DEST_DIR"
    rm -rf "$DEST_DIR/$lib"
    cp -R "$SRC_LIB" "$DEST_DIR/"
  else
    echo "Note: $lib not found at $SRC_LIB. Add it to ios/Flir/libs if required."
  fi
done

echo "Done copying frameworks. The podspec references `ios/Flir/libs` — run 'pod install' from ios after adding frameworks."
