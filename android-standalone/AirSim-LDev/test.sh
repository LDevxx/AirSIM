#!/bin/sh
set -eu

# AirSim-LDev 变体测试：完整修改版（VoWLAN + VoBLE + 云端 Relay）

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
VARIANT_ROOT=$(CDPATH= cd -- "$ROOT/.." && pwd)
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

# 身份与版本
assert_contains "$MANIFEST" 'package="com.airsim.phonecontrol.standalone"' \
  'standalone package id is missing'
assert_contains "$MANIFEST" 'android:versionCode="91"' \
  'version code does not match the AirSim-LDev release'
assert_contains "$MANIFEST" 'android:versionName="V0.9.7.2 LDev' \
  'version name does not match the AirSim-LDev release'

# 完整版必须保留被 Lite 裁掉的能力
assert_contains "$MANIFEST" 'com.airsim.phonecontrol.voble.VoBLEGatewayService' \
  'full variant must declare the VoBLE gateway service'
assert_contains "$MANIFEST" 'android.permission.BLUETOOTH_CONNECT' \
  'full variant must request BLUETOOTH_CONNECT'
assert_contains "$MANIFEST" 'android.permission.BLUETOOTH_ADVERTISE' \
  'full variant must request BLUETOOTH_ADVERTISE'
assert_contains "$MANIFEST" 'com.airsim.phonecontrol.CLOUD_RELAY' \
  'full variant must enable the CLOUD_RELAY capability flag'

# 内置 Agent 与短信能力
assert_contains "$MANIFEST" 'com.airsim.phonecontrol.STANDALONE_AGENT' \
  'standalone runtime flag is missing'
assert_contains "$MANIFEST" 'com.airsim.phonecontrol.StandaloneAgentService' \
  'embedded Agent foreground service is missing'
assert_contains "$MANIFEST" 'android.permission.RECEIVE_SMS' \
  'standalone cannot receive carrier SMS'
assert_contains "$MANIFEST" 'com.airsim.phonecontrol.IncomingSMSReceiver' \
  'standalone carrier SMS receiver is missing'

# 不得依赖 AVF
assert_not_contains "$MANIFEST" 'com.android.virtualization.terminal' \
  'standalone APK must not query or depend on Linux Terminal'
assert_not_contains "$MANIFEST" 'AgentWatchdogService' \
  'standalone APK must not declare the AVF watchdog'

"$VARIANT_ROOT/../android/phone-control-app/test.sh"
"$VARIANT_ROOT/../android/phone-audio-bridge/build.sh"
"$ROOT/build.sh" >/dev/null
AIRSIM_STANDALONE_CLASSES="$ROOT/build/android/classes" \
  sh "$VARIANT_ROOT/../android/phone-control-app/test-gateway-lifecycle.sh"

APK="$ROOT/build/android/AirSim-LDev-debug.apk"
APKANALYZER=${APKANALYZER:-$(command -v apkanalyzer || true)}
if [ -z "$APKANALYZER" ] && [ -x "$HOME/Library/Android/sdk/cmdline-tools/latest/bin/apkanalyzer" ]; then
  APKANALYZER="$HOME/Library/Android/sdk/cmdline-tools/latest/bin/apkanalyzer"
fi
if [ -z "$APKANALYZER" ]; then
  echo "FAIL: apkanalyzer is required to verify the built role manifest" >&2
  exit 1
fi
if [ ! -f "$APK" ]; then
  echo "FAIL: expected APK not found at $APK" >&2
  exit 1
fi

MANIFEST_PRINT=$("$APKANALYZER" manifest print "$APK")
printf '%s' "$MANIFEST_PRINT" | python3 "$VARIANT_ROOT/test/verify_dialer_manifest.py"

# 完整版必须真的把蓝牙与 Relay 打进 APK。
if ! printf '%s' "$MANIFEST_PRINT" | grep -Fq 'VoBLEGatewayService'; then
  echo "FAIL: AirSim-LDev APK must ship the VoBLE gateway service" >&2
  exit 1
fi
if ! printf '%s' "$MANIFEST_PRINT" | grep -Fq 'CLOUD_RELAY'; then
  echo "FAIL: AirSim-LDev APK must enable CLOUD_RELAY" >&2
  exit 1
fi

echo "AirSim-LDev tests passed"
