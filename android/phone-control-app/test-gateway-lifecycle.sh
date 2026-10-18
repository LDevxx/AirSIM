#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
BASE="$ROOT/build/jvm-test"
OUT="$BASE/gateway-lifecycle"
TASK_JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home}
# 若 JAVA_HOME 不可用（未导出或机器上没有该路径），回退到系统 Java，避免构建产物已就绪却卡在解释器路径。
if [ ! -x "$TASK_JAVA_HOME/bin/javac" ]; then
  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    DETECTED_JAVA_HOME=$(/usr/libexec/java_home 2>/dev/null || true)
    if [ -n "$DETECTED_JAVA_HOME" ] && [ -x "$DETECTED_JAVA_HOME/bin/javac" ]; then
      TASK_JAVA_HOME="$DETECTED_JAVA_HOME"
    fi
  fi
fi
if [ ! -x "$TASK_JAVA_HOME/bin/javac" ]; then
  echo "FAIL: no usable JDK found; set JAVA_HOME" >&2
  exit 1
fi
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
# 构建产物路径随变体目录变化：允许调用方通过 AIRSIM_STANDALONE_CLASSES 覆盖，
# 默认仍指向历史位置以兼容直接调用本脚本的场景。
BUILT=${AIRSIM_STANDALONE_CLASSES:-"$ROOT/../../android-standalone/build/android/classes"}
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
