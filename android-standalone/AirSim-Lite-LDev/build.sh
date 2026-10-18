#!/bin/sh
set -eu

# AirSim-Lite-LDev —— Lite 轻量版变体（AirSim-Lite V0.9.7.2 LDev 1）
# 能力：仅 VoWLAN（已移除 VoBLE 蓝牙与云端 Relay）
# 与 AirSim-LDev 共用 android/phone-control-app/src/ 源码，差异仅在本目录的 Manifest。

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
VARIANT_ROOT=$(CDPATH= cd -- "$ROOT/.." && pwd)

AIRSIM_ANDROID_MANIFEST="$ROOT/AndroidManifest.xml" \
AIRSIM_ANDROID_OUTPUT_DIR="$ROOT/build/android" \
AIRSIM_ANDROID_OUTPUT_BASENAME="AirSim-Lite-LDev" \
exec "$VARIANT_ROOT/../android/phone-control-app/build.sh"
