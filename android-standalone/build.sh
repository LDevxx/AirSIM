#!/bin/sh
set -eu

# 兼容转发入口。
#
# 历史上 android-standalone/build.sh 直接构建唯一变体。现在本 fork 在该目录下
# 维护两个变体：AirSim-LDev（完整版）与 AirSim-Lite-LDev（Lite 版）。
# 默认构建 Lite 版，保持与旧脚本行为一致；需要完整版请显式调用
# ./android-standalone/AirSim-LDev/build.sh，或设置 AIRSIM_STANDALONE_VARIANT=full。

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
VARIANT=${AIRSIM_STANDALONE_VARIANT:-lite}

case "$VARIANT" in
  full|lite)
    ;;
  *)
    echo "AIRSIM_STANDALONE_VARIANT must be 'full' or 'lite'" >&2
    exit 1
    ;;
esac

if [ "$VARIANT" = "full" ]; then
  exec "$ROOT/AirSim-LDev/build.sh" "$@"
fi
exec "$ROOT/AirSim-Lite-LDev/build.sh" "$@"
