#!/usr/bin/env bash
# Builds the signed release APK + AAB of the Android app.
#
# The Android Gradle plugin refuses project paths with non-ASCII characters
# (this repo lives under "Рабочий стол"), so the project is copied to an
# ASCII path first. Needs: a JDK 17+ (JAVA_HOME, or Android Studio's JBR in
# ~/.jdks), the Android SDK (ANDROID_HOME or %LOCALAPPDATA%\Android\Sdk), and
# the release keystore (see README.md).
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
work="${KASSIRON_ANDROID_WORK:-$HOME/.kassiron-android}"
props="${KASSIRON_KEYSTORE_PROPERTIES:-$work/keystore.properties}"

if [ -z "${JAVA_HOME:-}" ]; then
    jbr="$(ls -d "$HOME"/.jdks/jbr-* 2>/dev/null | tail -1 || true)"
    [ -n "$jbr" ] && export JAVA_HOME="$jbr"
fi
sdk="${ANDROID_HOME:-${LOCALAPPDATA:-}/Android/Sdk}"

mkdir -p "$work/src"
rm -rf "$work/src/app/src"
cp -r "$here/app" "$here/build.gradle" "$here/settings.gradle" "$here/gradle.properties" "$here/gradle" "$here/gradlew" "$here/gradlew.bat" "$work/src/"
if command -v cygpath >/dev/null 2>&1; then
    echo "sdk.dir=$(cygpath -m "$sdk")" > "$work/src/local.properties"
    export KASSIRON_KEYSTORE_PROPERTIES="$(cygpath -m "$props")"
else
    echo "sdk.dir=$sdk" > "$work/src/local.properties"
    export KASSIRON_KEYSTORE_PROPERTIES="$props"
fi

cd "$work/src"
./gradlew --no-daemon -q assembleRelease bundleRelease

mkdir -p "$here/dist"
cp app/build/outputs/apk/release/app-release.apk "$here/dist/KassirON.apk"
cp app/build/outputs/bundle/release/app-release.aab "$here/dist/KassirON.aab"
echo "Built: $here/dist/KassirON.apk and KassirON.aab"
