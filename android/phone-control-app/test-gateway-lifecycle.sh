#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
BASE="$ROOT/build/jvm-test"
OUT="$BASE/gateway-lifecycle"
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
ANDROID_JAR="$SDK/platforms/android-35/android.jar"
BUILT="$ROOT/../../android-standalone/build/android/classes"
mkdir -p "$OUT"
"$TASK_JAVA_HOME/bin/javac" --release 17 -cp "$BUILT:$BASE:$ANDROID_JAR" -d "$OUT" \
  "$ROOT"/test/android/Manifest.java \
  "$ROOT"/test/android/content/Context.java \
  "$ROOT"/test/android/content/Intent.java \
  "$ROOT"/test/gateway-lifecycle/android/app/Service.java \
  "$ROOT"/test/gateway-lifecycle/com/airsim/phonecontrol/RuntimeMode.java \
  "$ROOT"/src/com/airsim/phonecontrol/voble/VoBLEGatewayService.java \
  "$ROOT"/src/com/airsim/phonecontrol/voble/VoBLEMediaSession.java \
  "$ROOT"/test/com/airsim/phonecontrol/VoBLEGatewayMuteLifecycleTests.java
"$TASK_JAVA_HOME/bin/java" -cp "$OUT:$BASE/audio-policy:$BASE:$BUILT:$ANDROID_JAR" \
  com.airsim.phonecontrol.VoBLEGatewayMuteLifecycleTests
