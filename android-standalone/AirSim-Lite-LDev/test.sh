#!/bin/sh
set -eu

# AirSim-Lite-LDev 变体测试：Lite 轻量版（仅 VoWLAN）

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
  'version code does not match the AirSim-Lite release'
assert_contains "$MANIFEST" 'android:versionName="AirSim-Lite V0.9.7.2 LDev' \
  'version name does not match the AirSim-Lite release'

# Lite 必须裁掉 VoBLE 与云端 Relay
assert_not_contains "$MANIFEST" 'VoBLEGatewayService' \
  'AirSim-Lite must not declare the VoBLE gateway service'
assert_not_contains "$MANIFEST" 'android.permission.BLUETOOTH' \
  'AirSim-Lite must not request Bluetooth permissions'
assert_not_contains "$MANIFEST" 'com.airsim.phonecontrol.CLOUD_RELAY' \
  'AirSim-Lite must not enable the CLOUD_RELAY capability flag'

# 必须保留 VoWLAN 与本地能力
assert_contains "$MANIFEST" 'com.airsim.phonecontrol.VoWLANGatewayService' \
  'AirSim-Lite must keep the VoWLAN gateway service'
assert_contains "$MANIFEST" 'com.airsim.phonecontrol.STANDALONE_AGENT' \
  'standalone runtime flag is missing'
assert_contains "$MANIFEST" 'com.airsim.phonecontrol.StandaloneAgentService' \
  'embedded Agent foreground service is missing'
assert_contains "$MANIFEST" 'android.permission.RECEIVE_SMS' \
  'AirSim-Lite cannot receive carrier SMS'
assert_contains "$MANIFEST" 'com.airsim.phonecontrol.IncomingSMSReceiver' \
  'AirSim-Lite carrier SMS receiver is missing'

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

APK="$ROOT/build/android/AirSim-Lite-LDev-debug.apk"
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

# Lite 版必须真的把蓝牙与 Relay 排除在 APK 之外。
if printf '%s' "$MANIFEST_PRINT" | grep -Fq 'VoBLEGatewayService'; then
  echo "FAIL: AirSim-Lite-LDev APK must not ship the VoBLE gateway service" >&2
  exit 1
fi
if printf '%s' "$MANIFEST_PRINT" | grep -Fq 'permission.BLUETOOTH'; then
  echo "FAIL: AirSim-Lite-LDev APK must not request Bluetooth permissions" >&2
  exit 1
fi

echo "AirSim-Lite-LDev tests passed"
