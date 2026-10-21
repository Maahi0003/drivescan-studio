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

echo "== client secret (build-time only, never committed) =="
# The Desktop OAuth client requires its client_secret in token requests.
# The value lives ONLY in $K/client-secret.txt (gitignored, local to this
# machine) and is compiled in as a generated source file.
mkdir -p "$B/gen-secret/com/drivescan/kmp/auth"
SECRET=""
if [ -f "$K/client-secret.txt" ]; then
  SECRET=$(tr -d '[:space:]' < "$K/client-secret.txt")
fi
if [ -z "$SECRET" ]; then
  echo "WARNING: client-secret.txt missing/empty — Drive sign-in will fail"
fi
cat > "$B/gen-secret/com/drivescan/kmp/auth/ClientSecret.kt" <<EOF
package com.drivescan.kmp.auth

/** Generated at build time by build-apk.sh from client-secret.txt. Do not commit the real value. */
object ClientSecret {
    const val VALUE = "$SECRET"
}
EOF

echo "== ML Kit (bundled OCR) libraries =="
# AARs resolved from Google Maven (see README / v1.1.0 notes): classes.jar of
# each artifact in mlkit-libs/jars, native engines in mlkit-libs/lib/<abi>,
# TFLite models in mlkit-libs/assets. Only the two phone ABIs are packaged.
MLKIT_LIBS="$K/mlkit-libs"
MLKIT_JARS=()
if [ -d "$MLKIT_LIBS/jars" ]; then
  for j in "$MLKIT_LIBS"/jars/*.jar; do MLKIT_JARS+=("$j"); done
fi
echo "   mlkit jars: ${#MLKIT_JARS[@]}"
MLKIT_CP=""
if [ "${#MLKIT_JARS[@]}" -gt 0 ]; then
  MLKIT_CP=$(IFS=:; echo "${MLKIT_JARS[*]}")
fi

echo "== kotlinc =="
COMMON=$(find "$K/shared/src/commonMain" -name '*.kt' ! -path '*ui*')
JVM=$(find "$K/shared/src/jvmMain" -name '*.kt')
ANDR=$(find "$K/androidApp/src/main/java" -name '*.kt')
RJAVA=$(find "$B/gen" -name '*.java')
SECRET_FILE="$B/gen-secret/com/drivescan/kmp/auth/ClientSecret.kt"
COMMON_CSV=$(printf '%s' "$COMMON" | paste -sd, -)
KOTLINC_CP="$AJAR"
if [ -n "$MLKIT_CP" ]; then KOTLINC_CP="$AJAR:$MLKIT_CP"; fi
# shellcheck disable=SC2086
kotlinc -Xmulti-platform -Xcommon-sources="$COMMON_CSV" -nowarn \
  -cp "$KOTLINC_CP" -d "$B/classes" $COMMON $JVM $ANDR $RJAVA "$SECRET_FILE"
echo "   classes: $(find "$B/classes" -name '*.class' | wc -l)"

echo "== d8 =="
jar cf "$B/classes.jar" -C "$B/classes" .
"$BT/d8" --lib "$AJAR" --min-api 26 --output "$B/dex" "$B/classes.jar" "$STDLIB" "${MLKIT_JARS[@]}"

echo "== package + align + sign =="
cd "$B"
cp unsigned.apk app.apk
zip -q -j app.apk dex/*.dex
if [ -d "$MLKIT_LIBS/lib" ]; then
  # Native libraries must be stored uncompressed + page-aligned (zipalign -p below).
  (cd "$MLKIT_LIBS" && zip -q -0 "$B/app.apk" lib/arm64-v8a/*.so lib/armeabi-v7a/*.so)
fi
if [ -d "$MLKIT_LIBS/assets" ]; then
  (cd "$MLKIT_LIBS" && find assets -type f -print0 | xargs -0 zip -q "$B/app.apk")
fi
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
