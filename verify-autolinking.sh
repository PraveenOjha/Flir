#!/bin/bash

# Autolinking Verification Script
# This script helps verify that the autolinking configuration is correct

set -e

echo "🔍 FLIR Thermal SDK - Autolinking Verification"
echo "=============================================="
echo ""

# Check if required files exist
echo "📋 Checking required files..."
FILES=(
    "expo-module.config.json"
    "react-native.config.js"
    "android/Flir/src/main/java/flir/android/FlirPackage.kt"
    "android/Flir/src/main/AndroidManifest.xml"
    "Flir.podspec"
    "android/Flir/libs/flir-stubs.jar"
    "ios/Flir/Framework/ThermalSDK/ThermalSDK.h"
)

ALL_EXIST=true
for file in "${FILES[@]}"; do
    if [ -f "$file" ]; then
        echo "  ✅ $file"
    else
        echo "  ❌ $file (MISSING!)"
        ALL_EXIST=false
    fi
done

if [ "$ALL_EXIST" = false ]; then
    echo ""
    echo "❌ Some required files are missing!"
    exit 1
fi

echo ""
echo "✅ All required files exist!"
echo ""

# Validate JSON files
echo "🔍 Validating JSON files..."

if command -v node &> /dev/null; then
    # Validate expo-module.config.json
    if node -e "JSON.parse(require('fs').readFileSync('expo-module.config.json', 'utf8'))" 2>/dev/null; then
        echo "  ✅ expo-module.config.json is valid JSON"
    else
        echo "  ❌ expo-module.config.json has invalid JSON"
        exit 1
    fi
    
    # Validate package.json
    if node -e "JSON.parse(require('fs').readFileSync('package.json', 'utf8'))" 2>/dev/null; then
        echo "  ✅ package.json is valid JSON"
    else
        echo "  ❌ package.json has invalid JSON"
        exit 1
    fi
else
    echo "  ⚠️  Node.js not found, skipping JSON validation"
fi

echo ""

# Validate react-native.config.js
echo "🔍 Validating react-native.config.js..."
if command -v node &> /dev/null; then
    if node -e "require('./react-native.config.js')" 2>/dev/null; then
        echo "  ✅ react-native.config.js is valid JavaScript"
    else
        echo "  ❌ react-native.config.js has syntax errors"
        exit 1
    fi
else
    echo "  ⚠️  Node.js not found, skipping JS validation"
fi

echo ""

# Check package.json files array
echo "🔍 Checking package.json files array..."
if command -v node &> /dev/null; then
    HAS_EXPO_CONFIG=$(node -e "
        const pkg = JSON.parse(require('fs').readFileSync('package.json', 'utf8'));
        console.log(pkg.files.includes('expo-module.config.json'));
    ")
    
    HAS_RN_CONFIG=$(node -e "
        const pkg = JSON.parse(require('fs').readFileSync('package.json', 'utf8'));
        console.log(pkg.files.includes('react-native.config.js'));
    ")
    
    if [ "$HAS_EXPO_CONFIG" = "true" ]; then
        echo "  ✅ expo-module.config.json is in files array"
    else
        echo "  ❌ expo-module.config.json is NOT in files array"
        exit 1
    fi
    
    if [ "$HAS_RN_CONFIG" = "true" ]; then
        echo "  ✅ react-native.config.js is in files array"
    else
        echo "  ❌ react-native.config.js is NOT in files array"
        exit 1
    fi
else
    echo "  ⚠️  Node.js not found, skipping package.json validation"
fi

echo ""
echo "=============================================="
echo "✅ All autolinking configuration checks passed!"
echo ""
echo "Next steps:"
echo "1. Test in a sample project:"
echo "   npx create-expo-app test-flir"
echo "   cd test-flir"
echo "   npm install /path/to/Flir"
echo "   npx expo-modules-autolinking verify --verbose"
echo ""
echo "2. Publish to npm:"
echo "   npm version patch"
echo "   npm publish"
echo ""
