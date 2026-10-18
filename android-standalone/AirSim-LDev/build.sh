#!/bin/sh
set -eu

# AirSim-LDev —— 完整修改版变体（V0.9.7.2 LDev 911）
# 能力：VoWLAN + VoBLE 蓝牙 + 云端 Relay
# 与 AirSim-Lite-LDev 共用 android/phone-control-app/src/ 源码，差异仅在本目录的 Manifest。

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
VARIANT_ROOT=$(CDPATH= cd -- "$ROOT/.." && pwd)

AIRSIM_ANDROID_MANIFEST="$ROOT/AndroidManifest.xml" \
AIRSIM_ANDROID_OUTPUT_DIR="$ROOT/build/android" \
AIRSIM_ANDROID_OUTPUT_BASENAME="AirSim-LDev" \
exec "$VARIANT_ROOT/../android/phone-control-app/build.sh"
