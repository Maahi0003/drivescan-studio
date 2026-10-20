#!/usr/bin/env bash
# DriveScan Studio (KMP) — manual APK build for this sandbox, where the
# Gradle daemon cannot run. Mirrors exactly what AGP would do:
#   aapt2 compile/link -> kotlinc -> d8 -> zipalign -> apksigner
# On a normal machine use Gradle instead (see README.md).
#
# Usage: ./build-apk.sh [versionCode] [versionName]
set -euo pipefail

VERSION_CODE="${1:-2}"
VERSION_NAME="${2:-1.0.0}"

export JAVA_HOME=/home/hatch/tools/jdk-21
export PATH="$JAVA_HOME/bin:/home/hatch/tools/kotlinc/bin:$PATH"

BT=/home/hatch/tools/android-sdk/build-tools/34.0.0
AJAR=/home/hatch/tools/android-sdk/platforms/android-34/android.jar
STDLIB=/home/hatch/tools/kotlinc/lib/kotlin-stdlib.jar
K=/home/hatch/workspace/drivescan-kmp
KEYSTORE=/home/hatch/workspace/drivescan-apk/debug.keystore
B=$K/build-manual

rm -rf "$B"
mkdir -p "$B/gen" "$B/classes" "$B/dex"

echo "== aapt2 compile + link (v$VERSION_NAME, code $VERSION_CODE) =="
"$BT/aapt2" compile --dir "$K/androidApp/src/main/res" -o "$B/compiled.zip"
"$BT/aapt2" link -o "$B/unsigned.apk" -I "$AJAR" \
  --manifest "$K/androidApp/src/main/AndroidManifest.xml" \
  --java "$B/gen" \
  --min-sdk-version 26 --target-sdk-version 34 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  "$B/compiled.zip"

echo "== kotlinc =="
COMMON=$(find "$K/shared/src/commonMain" -name '*.kt' ! -path '*ui*')
JVM=$(find "$K/shared/src/jvmMain" -name '*.kt')
ANDR=$(find "$K/androidApp/src/main/java" -name '*.kt')
RJAVA=$(find "$B/gen" -name '*.java')
COMMON_CSV=$(printf '%s' "$COMMON" | paste -sd, -)
# shellcheck disable=SC2086
kotlinc -Xmulti-platform -Xcommon-sources="$COMMON_CSV" -nowarn \
  -cp "$AJAR" -d "$B/classes" $COMMON $JVM $ANDR $RJAVA
echo "   classes: $(find "$B/classes" -name '*.class' | wc -l)"

echo "== d8 =="
jar cf "$B/classes.jar" -C "$B/classes" .
"$BT/d8" --lib "$AJAR" --min-api 26 --output "$B/dex" "$B/classes.jar" "$STDLIB"

echo "== package + align + sign =="
cd "$B"
cp unsigned.apk app.apk
zip -q -j app.apk dex/classes.dex
"$BT/zipalign" -p -f 4 app.apk aligned.apk
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android \
  --ks-key-alias androiddebugkey --key-pass pass:android \
  --out "$B/drivescan-studio-final.apk" aligned.apk

echo "== verify =="
"$BT/apksigner" verify "$B/drivescan-studio-final.apk" && echo "signature OK"
"$BT/aapt2" dump badging "$B/drivescan-studio-final.apk" | \
  grep -E "^package:|application-label:|launchable-activity:" || true
cp "$B/drivescan-studio-final.apk" /home/hatch/workspace/your_files/drivescan-studio-final.apk
ls -la "$B/drivescan-studio-final.apk"
echo "DONE -> $B/drivescan-studio-final.apk (copy in ~/workspace/your_files/)"
