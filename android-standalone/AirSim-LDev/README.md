# AirSim-LDev —— Android Standalone 完整修改版

本目录是 [android-standalone](../README.md) 下的**完整修改版**变体，基于上游 Android Standalone 修改而来。

| 项目 | 值 |
| --- | --- |
| 包名 | `com.airsim.phonecontrol.standalone` |
| versionName | `V0.9.7.2 LDev 911` |
| versionCode | `91` |
| 上游基线 | `0.9.7.2` |
| 修改次数 | `911` |

## 能力范围

保留上游 Android Standalone 的**全部能力**：

- VoWLAN 局域网直连通话（控制 + PCM）
- **VoBLE 蓝牙直连**（BLE GATT + AES-256-GCM + G.711 μ-law）
- **云端 Relay**（设备注册、30 秒心跳、WSS 命令、云端 PCM）
- Android Telecom 拨号、接听、拒接、挂断、DTMF
- 短信收发（含可选收件箱兜底）
- DSDS 双卡卡槽探测与默认通话卡／短信卡选择
- Shizuku 通话 PCM 桥

## 与 Lite 版的区别

本目录与 [`AirSim-Lite-LDev`](../AirSim-Lite-LDev/README.md) **共用同一份源码**
（`android/phone-control-app/src/`），差异只在这两份 Manifest：

| 差异点 | AirSim-LDev（本目录） | AirSim-Lite-LDev |
| --- | --- | --- |
| `VoBLEGatewayService` 服务声明 | ✅ 有 | ❌ 无 |
| 蓝牙权限（4 项） | ✅ 有 | ❌ 无 |
| `com.airsim.phonecontrol.CLOUD_RELAY` | ✅ `true` | ❌ 无 |
| VoWLAN | ✅ | ✅ |
| 短信 / Telecom / PCM 桥 | ✅ | ✅ |

运行时由 `FeatureFlags` 读取 Manifest 判断能力：

- `FeatureFlags.voBLEAvailable()` → 控制蓝牙配对 UI、诊断卡与 `VoBLEGatewayService` 启动
- `FeatureFlags.cloudRelayAvailable()` → 控制内置 Agent 是否启动 Relay 心跳与命令轮询

因此**不需要两套源码，也不会出现逻辑漂移**。新增变体只需再写一份 Manifest。

## 构建与测试

```sh
./android-standalone/AirSim-LDev/build.sh
./android-standalone/AirSim-LDev/test.sh
```

调试 APK 输出：

```text
android-standalone/AirSim-LDev/build/android/AirSim-LDev-debug.apk
```

测试会校验：

1. 本 Manifest 必须声明 VoBLE 服务、蓝牙权限与 `CLOUD_RELAY`；
2. 构建出的 APK 内确实包含这些声明（用 `apkanalyzer` 读实际 APK，而非只看源码）；
3. 上游全部共享测试与 PCM 桥构建通过。

正式签名构建沿用仓库统一的 `AIRSIM_ANDROID_*` 环境变量，见 [android-standalone](../README.md)。

## 安装注意

本变体与 Lite 版：

- 包名相同（`com.airsim.phonecontrol.standalone`）
- versionCode 相同（`91`）

**两者不能共存，也不能互相覆盖升级。** 从 Lite 切到完整版（或反向）必须先卸载，
这会清除已保存的 AirChat 配对凭据与默认电话角色，需要重新配置。

若需两版并行安装，必须给其中一版改 `applicationId`（例如
`com.airsim.phonecontrol.lite`），代价是旧数据无法直接迁移。

## 验收边界

本变体**尚未完成真机验收**。以下能力沿用上游与既有测试结论，不代表本 fork 已复验：

- 三星最低通话音量的物理听感
- VoBLE 在目标机型上的后台唤醒与断链恢复
- 云端 Relay 的真实 APNs 与弱网表现

真机测试矩阵见 [VoBLE 真机测试矩阵](../../docs/voble-real-device-test-matrix.md)。
