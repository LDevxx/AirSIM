#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
MANIFEST="$ROOT/AndroidManifest.xml"

assert_contains() {
  if ! grep -Fq "$2" "$1"; then
    echo "FAIL: $3" >&2
    exit 1
  fi
}

assert_not_contains() {
  if grep -Fq "$2" "$1"; then
    echo "FAIL: $3" >&2
    exit 1
  fi
}

assert_contains "$MANIFEST" 'package="com.airsim.phonecontrol.standalone"' \
  'standalone package id is missing'
assert_contains "$MANIFEST" 'android:versionCode="81"' \
  'standalone version code does not match the Apps release'
assert_contains "$MANIFEST" 'android:versionName="0.9.7.1"' \
  'standalone version name does not match the Apps release'
assert_contains "$MANIFEST" 'com.airsim.phonecontrol.STANDALONE_AGENT' \
  'standalone runtime flag is missing'
assert_contains "$MANIFEST" 'com.airsim.phonecontrol.StandaloneAgentService' \
  'embedded Agent foreground service is missing'
assert_contains "$MANIFEST" 'android.permission.RECEIVE_SMS' \
  'standalone cannot receive carrier SMS'
assert_contains "$MANIFEST" 'android.permission.READ_SMS' \
  'standalone cannot use the optional inbox fallback'
assert_contains "$MANIFEST" 'com.airsim.phonecontrol.IncomingSMSReceiver' \
  'standalone carrier SMS receiver is missing'
assert_not_contains "$MANIFEST" 'com.android.virtualization.terminal' \
  'standalone APK must not query or depend on Linux Terminal'
assert_not_contains "$MANIFEST" 'AgentWatchdogService' \
  'standalone APK must not declare the AVF watchdog'

"$ROOT/../android/phone-control-app/test.sh"
"$ROOT/../android/phone-audio-bridge/build.sh"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "${JAVA_HOME}/bin/javac" ]; then
  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    JAVA_HOME=$(/usr/libexec/java_home 2>/dev/null || true)
  fi
fi
JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home}
export JAVA_HOME

"$ROOT/build.sh" >/dev/null
APK="$ROOT/build/android/AirSIM-Android-Standalone-debug.apk"
APKANALYZER=${APKANALYZER:-$(command -v apkanalyzer || true)}
if [ -z "$APKANALYZER" ] && [ -x "$HOME/Library/Android/sdk/cmdline-tools/latest/bin/apkanalyzer" ]; then
  APKANALYZER="$HOME/Library/Android/sdk/cmdline-tools/latest/bin/apkanalyzer"
fi
if [ -z "$APKANALYZER" ]; then
  echo "FAIL: apkanalyzer is required to verify the built role manifest" >&2
  exit 1
fi
JAVA_HOME="$JAVA_HOME" \
  "$APKANALYZER" manifest print "$APK" | python3 "$ROOT/test/verify_dialer_manifest.py"
echo "Standalone Bridge tests passed"
