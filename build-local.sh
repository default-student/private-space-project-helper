#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK_DIR="${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK path}"
PLATFORM="${ANDROID_PLATFORM:-android-37.2}"
BUILD_TOOLS="${ANDROID_BUILD_TOOLS:-37.0.0}"
KEYSTORE="${ANDROID_DEBUG_KEYSTORE:?Set ANDROID_DEBUG_KEYSTORE to a local debug keystore}"
KEY_ALIAS="${ANDROID_DEBUG_KEY_ALIAS:-androiddebugkey}"
: "${ANDROID_DEBUG_KEYSTORE_PASSWORD:?Set the debug keystore password in the environment}"
: "${ANDROID_DEBUG_KEY_PASSWORD:?Set the debug key password in the environment}"

TOOLS_DIR="$SDK_DIR/build-tools/$BUILD_TOOLS"
ANDROID_JAR="$SDK_DIR/platforms/$PLATFORM/android.jar"
BUILD_DIR="$PROJECT_DIR/build/local-manual"
APP_DIR="$PROJECT_DIR/app"
APK_OUT="$PROJECT_DIR/artifacts/private-space-project-helper.apk"
VERSION_NAME="${VERSION_NAME:-0.1.0}"
VERSION_CODE="${VERSION_CODE:-100002}"

mkdir -p "$BUILD_DIR/classes" "$BUILD_DIR/generated" "$BUILD_DIR/dex" \
    "$(dirname "$APK_OUT")"
rm -rf "$BUILD_DIR/classes" "$BUILD_DIR/generated" "$BUILD_DIR/dex"
mkdir -p "$BUILD_DIR/classes" "$BUILD_DIR/generated" "$BUILD_DIR/dex"

"$TOOLS_DIR/aapt2" compile --dir "$APP_DIR/src/main/res" \
    -o "$BUILD_DIR/resources.zip"
"$TOOLS_DIR/aapt2" link -o "$BUILD_DIR/package-unsigned.apk" \
    --manifest "$APP_DIR/src/main/AndroidManifest.xml" \
    --java "$BUILD_DIR/generated" \
    --custom-package com.example.privatespaceprojects \
    --min-sdk-version 35 --target-sdk-version 35 \
    --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" --auto-add-overlay \
    -I "$ANDROID_JAR" "$BUILD_DIR/resources.zip"

mapfile -d '' JAVA_FILES < <(find "$APP_DIR/src/main/java" "$BUILD_DIR/generated" -name '*.java' -print0)
javac -source 8 -target 8 -classpath "$ANDROID_JAR" \
    -d "$BUILD_DIR/classes" "${JAVA_FILES[@]}"

mapfile -d '' CLASS_FILES < <(find "$BUILD_DIR/classes" -name '*.class' -print0)
"$TOOLS_DIR/d8" --min-api 35 --lib "$ANDROID_JAR" \
    --output "$BUILD_DIR/dex" "${CLASS_FILES[@]}"
touch -t 198001010000 "$BUILD_DIR/dex/classes.dex"
zip -X -q -j "$BUILD_DIR/package-unsigned.apk" "$BUILD_DIR/dex/classes.dex"
"$TOOLS_DIR/zipalign" -f 4 "$BUILD_DIR/package-unsigned.apk" \
    "$BUILD_DIR/package-aligned.apk"
"$TOOLS_DIR/apksigner" sign \
    --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
    --ks-pass env:ANDROID_DEBUG_KEYSTORE_PASSWORD \
    --key-pass env:ANDROID_DEBUG_KEY_PASSWORD \
    --out "$APK_OUT" "$BUILD_DIR/package-aligned.apk"
"$TOOLS_DIR/apksigner" verify --print-certs "$APK_OUT"
sha256sum "$APK_OUT"
