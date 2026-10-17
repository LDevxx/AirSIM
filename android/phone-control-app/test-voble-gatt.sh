#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
OUT="$ROOT/build/voble-gatt-tests"
TASK_JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home}
# 与 build.sh 相同的 SDK 解析链：避免无 ANDROID_SDK_ROOT 时落到不存在的默认路径。
if [ -z "${ANDROID_SDK_ROOT:-}" ] || [ ! -d "${ANDROID_SDK_ROOT:-}" ]; then
  if [ -n "${ANDROID_HOME:-}" ] && [ -d "${ANDROID_HOME}" ]; then
    ANDROID_SDK_ROOT="$ANDROID_HOME"
  elif [ -d "$HOME/Library/Android/sdk" ]; then
    ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
  fi
fi
SDK=${ANDROID_SDK_ROOT:-/opt/homebrew/share/android-commandlinetools}
mkdir -p "$OUT"
"$TASK_JAVA_HOME/bin/javac" --release 17 -cp "$ROOT/build/jvm-test:$SDK/platforms/android-35/android.jar" -d "$OUT" \
  "$ROOT"/test/voble-gatt/android/bluetooth/*.java \
  "$ROOT"/test/voble-gatt/android/bluetooth/le/*.java \
  "$ROOT"/test/voble-gatt/android/os/*.java \
  "$ROOT"/src/com/airsim/phonecontrol/voble/VoBLEGattServer.java \
  "$ROOT"/test/com/airsim/phonecontrol/VoBLEGattLifecycleTests.java
"$TASK_JAVA_HOME/bin/java" -cp "$OUT:$ROOT/build/jvm-test:$SDK/platforms/android-35/android.jar" \
  com.airsim.phonecontrol.VoBLEGattLifecycleTests
