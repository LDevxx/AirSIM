#!/bin/sh
set -eu

# 兼容转发入口。
#
# 历史上 android-standalone/test.sh 只测试唯一变体。现在本 fork 在该目录下维护
# 两个变体：AirSim-LDev（完整版）与 AirSim-Lite-LDev（Lite 版）。
# 默认测试 Lite 版；需要完整版请显式调用
# ./android-standalone/AirSim-LDev/test.sh，或设置 AIRSIM_STANDALONE_VARIANT=full。

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
  exec "$ROOT/AirSim-LDev/test.sh" "$@"
fi
exec "$ROOT/AirSim-Lite-LDev/test.sh" "$@"
