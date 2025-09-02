#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "Building Cactus Kotlin Multiplatform Library"

cd "$ROOT_DIR/kotlin"

echo "Cleaning previous builds..."
./gradlew clean --no-daemon --no-configuration-cache

echo "Copying native dependencies..."

# Copy iOS XCFramework
if [ -d "$ROOT_DIR/ios/cactus.xcframework" ]; then
    rm -rf library/src/commonMain/resources/ios/cactus.xcframework
    mkdir -p library/src/commonMain/resources/ios
    cp -R "$ROOT_DIR/ios/cactus.xcframework" library/src/commonMain/resources/ios/
    echo "iOS XCFramework copied"
else
    echo "Warning: iOS XCFramework not found at $ROOT_DIR/ios/cactus.xcframework"
fi

# Copy Android JNI libraries
if [ -d "$ROOT_DIR/flutter/android/src/main/jniLibs" ]; then
    rm -rf library/src/commonMain/resources/android/jniLibs
    mkdir -p library/src/commonMain/resources/android
    cp -R "$ROOT_DIR/flutter/android/src/main/jniLibs" library/src/commonMain/resources/android/
    echo "Android JNI libraries copied"
else
    echo "Warning: Android JNI libraries not found at $ROOT_DIR/flutter/android/src/main/jniLibs"
fi

echo "Building KMP library..."
./gradlew build --no-daemon --no-configuration-cache

echo "Publishing to Maven Local (triggers release XCFramework build with embedded C++)..."
./gradlew publishToMavenLocal --no-daemon --no-configuration-cache

echo "Updating Package.swift to point to release XCFramework..."
cd "$ROOT_DIR"
sed -i '' 's|/debug/|/release/|g' Package.swift

echo "Build completed successfully"
echo "Library published to Maven Local with release XCFramework"
echo "Package.swift updated to point to release XCFramework with embedded C++ for SPM integration"
