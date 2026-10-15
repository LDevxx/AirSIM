#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "${JAVA_HOME}/bin/javac" ]; then
  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    JAVA_HOME=$(/usr/libexec/java_home 2>/dev/null || true)
  fi
fi
JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home}

if [ -z "${ANDROID_SDK_ROOT:-}" ] || [ ! -d "${ANDROID_SDK_ROOT}" ]; then
  if [ -n "${ANDROID_HOME:-}" ] && [ -d "${ANDROID_HOME}" ]; then
    ANDROID_SDK_ROOT="$ANDROID_HOME"
  elif [ -d "$HOME/Library/Android/sdk" ]; then
    ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
  else
    ANDROID_SDK_ROOT="/opt/homebrew/share/android-commandlinetools"
  fi
fi
ANDROID_JAR=${ANDROID_JAR:-$ANDROID_SDK_ROOT/platforms/android-35/android.jar}
D8=${D8:-$ANDROID_SDK_ROOT/build-tools/35.0.0/d8}
JAVAC=$JAVA_HOME/bin/javac
JAVA=$JAVA_HOME/bin/java
JAR=$JAVA_HOME/bin/jar
BUILD_DIR=$SCRIPT_DIR/build
TEST_CLASSES=$BUILD_DIR/test-classes
PROD_CLASSES=$BUILD_DIR/classes
DEX_DIR=$BUILD_DIR/dex
OUTPUT_JAR=$BUILD_DIR/airsim-phone-audio-bridge.jar

for required in "$JAVAC" "$JAVA" "$JAR" "$D8" "$ANDROID_JAR"; do
    if [ ! -e "$required" ]; then
        echo "missing build dependency: $required" >&2
        exit 1
    fi
done

rm -rf "$TEST_CLASSES" "$PROD_CLASSES" "$DEX_DIR" "$OUTPUT_JAR"
mkdir -p "$TEST_CLASSES" "$PROD_CLASSES" "$DEX_DIR"

find "$SCRIPT_DIR/src" -name '*.java' -type f -print | sort | sed 's/.*/"&"/' > "$BUILD_DIR/sources.args"
find "$SCRIPT_DIR/test" -name '*.java' -type f -print | sort | sed 's/.*/"&"/' > "$BUILD_DIR/tests.args"

"$JAVAC" --release 17 -cp "$ANDROID_JAR" -d "$TEST_CLASSES" \
    @"$BUILD_DIR/sources.args" @"$BUILD_DIR/tests.args"
"$JAVA" -cp "$TEST_CLASSES:$ANDROID_JAR" com.airsim.bridge.BridgeUnitTests

"$JAVAC" --release 17 -cp "$ANDROID_JAR" -d "$PROD_CLASSES" \
    @"$BUILD_DIR/sources.args"
"$JAR" --create --file "$BUILD_DIR/classes.jar" -C "$PROD_CLASSES" .
JAVA_HOME=$JAVA_HOME "$D8" --min-api 34 --output "$DEX_DIR" "$BUILD_DIR/classes.jar"
(cd "$DEX_DIR" && zip -q "$OUTPUT_JAR" classes.dex)

HASH=$(shasum -a 256 "$OUTPUT_JAR" | awk '{print $1}')
printf 'JAR=%s\nSHA256=%s\n' "$OUTPUT_JAR" "$HASH"
