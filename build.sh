#!/bin/bash

set -e

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ ! -f "$JNI_LIBS_ZIP_TARGET" ] || [ ! -f "$XCFRAMEWORK_ZIP_TARGET" ]; then
    echo "Native library zips not found, building from source..."
    
    echo "Pulling from Cactus core repo..."
    rm -rf cactus_temp
    git clone --depth 1 -b legacy https://github.com/cactus-compute/cactus.git cactus_temp || {
        echo "Error: Failed to clone cactus repository (legacy branch)"
        exit 1
    }
    
    if [ ! -d "cactus_temp" ]; then
        echo "Error: Clone directory not created"
        exit 1
    fi
    
    cd cactus_temp
    
    echo "Building Android JNILibs..."
    if [ -f "scripts/build-android.sh" ]; then
        chmod +x scripts/build-android.sh
        if ./scripts/build-android.sh; then
            echo "Android build succeeded, copying JNILibs..."
            if [ -d "./android/src/main/jniLibs" ]; then
                cp -R ./android/src/main/jniLibs "$ROOT_DIR/library/src/commonMain/resources/android/"
            else
                echo "Warning: JNILibs directory not found after build"
            fi
        else
            echo "Error: Android build failed!"
            exit 1
        fi
    else
        echo "Error: build-android.sh script not found!"
        exit 1
    fi
    
    echo "Copying iOS frameworks..."
    if [ -d "./ios/cactus.xcframework" ]; then
        cp -R ./ios/cactus.xcframework "$ROOT_DIR/library/src/commonMain/resources/ios/"
    else
        echo "Warning: iOS xcframework not found"
    fi
    
    echo "Cleaning up temporary clone..."
    cd "$ROOT_DIR"
    rm -rf cactus_temp
else
    echo "Native library zips already exist, skipping build from source..."
fi

########################

echo "Cleaning previous builds..."
./gradlew clean --no-daemon --no-configuration-cache

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
