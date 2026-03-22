#!/usr/bin/env bash
# Build the FlashPhoto APK for Honor NEM-L21
# Requires: nix-shell (for jdk17 + aapt), python3
# Downloads: android.jar (API 13/33), d8.jar (from Android build-tools 33)
#
# Artifacts will be in /tmp/flashphoto-build/

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="/tmp/flashphoto-build"
ANDROID_JAR="/tmp/android-sdk/android-13/android.jar"
D8_JAR="/tmp/android-sdk/android-13/lib/d8.jar"

mkdir -p "$BUILD_DIR"/{classes,dex}

# Check prerequisites
if [ ! -f "$ANDROID_JAR" ]; then
    echo "Downloading android.jar (API 33)..."
    mkdir -p /tmp/android-sdk
    curl -sL "https://dl.google.com/android/repository/platform-33_r02.zip" -o /tmp/platform-33.zip
    unzip -o /tmp/platform-33.zip "android-13/android.jar" -d /tmp/android-sdk/
fi

if [ ! -f "$D8_JAR" ]; then
    echo "Downloading d8.jar (build-tools 33)..."
    mkdir -p /tmp/android-sdk/android-13/lib
    curl -sL "https://dl.google.com/android/repository/build-tools_r33.0.1-linux.zip" -o /tmp/build-tools-33.zip
    unzip -o /tmp/build-tools-33.zip "android-13/lib/d8.jar" -d /tmp/android-sdk/
fi

echo "Step 1: Compile Java..."
nix-shell -p jdk17 --run "
javac -source 1.8 -target 1.8 \
  -cp $ANDROID_JAR \
  -d $BUILD_DIR/classes \
  $SCRIPT_DIR/src/com/flashphoto/FlashReceiver.java
"

echo "Step 2: Convert to DEX..."
nix-shell -p jdk17 --run "
java -cp $D8_JAR com.android.tools.r8.D8 \
  --min-api 24 --output $BUILD_DIR/dex/ \
  $BUILD_DIR/classes/com/flashphoto/*.class
"

echo "Step 3: Package APK..."
nix-shell -p aapt --run "
aapt2 link -o $BUILD_DIR/flashphoto-base.apk \
  --manifest $SCRIPT_DIR/AndroidManifest.xml \
  -I $ANDROID_JAR
"

echo "Step 4: Add DEX..."
python3 -c "
import zipfile, shutil
shutil.copy('$BUILD_DIR/flashphoto-base.apk', '$BUILD_DIR/flashphoto.apk')
with zipfile.ZipFile('$BUILD_DIR/flashphoto.apk', 'a') as z:
    z.write('$BUILD_DIR/dex/classes.dex', 'classes.dex')
"

echo "Step 5: Sign..."
nix-shell -p jdk17 --run "
jarsigner -sigalg SHA256withRSA -digestalg SHA-256 \
  -keystore $SCRIPT_DIR/debug.keystore -storepass android -keypass android \
  $BUILD_DIR/flashphoto.apk androiddebugkey
"

echo "Built: $BUILD_DIR/flashphoto.apk"
ls -la "$BUILD_DIR/flashphoto.apk"
