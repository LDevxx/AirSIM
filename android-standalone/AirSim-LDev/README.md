# AirSim-LDev —— Android Standalone 完整修改版

本目录是 [android-standalone](../README.md) 下的**完整修改版**变体，基于上游 `v0.9.7.2` 的稳定通信能力（通话拦截、Shizuku `UserService`、PCM 媒体桥）做深度定制增强。

| 项目 | 值 |
| --- | --- |
| 包名 | `com.airsim.phonecontrol.standalone` |
| versionName | `V0.9.7.2 LDev 911` |
| versionCode | `91` |
| 上游基线 | `0.9.7.2` |

## 相对上游的增强

以下四项均在 `git diff v0.9.7.2..HEAD` 中可核实。**上游原有的网关路由一条未删**，全部为叠加式扩展。

### 1. 新增 DSDS 双卡卡槽管理（上游完全没有）

上游只读取 `SubscriptionManager.getDefaultVoiceSubscriptionId()`，没有卡槽枚举能力。本版本新增两个源文件：

| 文件 | 行数 | 职责 |
| --- | --- | --- |
| `SIMSlotManager.java` | 326 | 卡槽探测、匹配与拨号参数生成 |
| `SIMSlotInfo.java` | 52 | 卡槽数据模型 |

能力清单：

- `getActiveSlots()` 枚举所有活跃 SIM/eSIM
- `getSlotByIndex()` / `getSlotBySubId()` 双路查找
- `slotForCall()` / `slotForSMS()` / `slotForAccountHandle()` 按场景匹配
- `dialExtrasForCommand()` 生成 Telecom 拨号参数
- `registerChangeListener()` 监听卡槽热插拔

对应新增路由：`/api/android/sims`、`/api/v2/sims`

> **边界**：尚未完成真机验收；JVM 测试仅有 `SIMSlotManagerTests`，覆盖范围有限。

### 2. 扩展远程直连与时钟校准

**Tailscale 说明**：上游 `v0.9.7.2` **已内置** Tailscale 支持（`VoWLANNetworkPolicy` 识别 `tun` / `tailscale` 接口，
`PairingServer` 已有 Tailscale 配对地址提示）。本版本的改动属于**扩展**而非修复：

- 把 overlay 判定扩展到 WireGuard（`wg`）接口
- 统一走 `isOverlayIPv4()` 判断，避免散落各处

**时钟偏移校准是本版本新增**：

```
上游 v0.9.7.2：/pair/v1/info 无 server_time 字段
本版本     ：返回 server_time，客户端据此计算 clockOffsetMillis
```

用途：设备时钟漂移会导致 HMAC 签名时间戳超出服务端 60 秒容差而失败。客户端用服务端时间校正后即可正常签名。

对应提交：`7909425 fix: precise clock offset via server_time in /pair/v1/info`

### 3. 新增 LD 2.0 集群接口（上游完全没有）

上游网关提供 23 个路由，本版本提供 32 个，**移除 0 个**。新增的 9 个：

| 路由 | 方法 | 用途 |
| --- | --- | --- |
| `/api/android/sims` | GET | 卡槽列表 |
| `/api/v2/sims` | GET | v2 卡槽（集群用） |
| `/api/v2/sms` | GET | v2 短信列表 |
| `/api/v2/sms/send` | POST | v2 短信发送（支持指定卡槽） |
| `/api/v2/calls/dial` | POST | v2 拨号（回传 Android 权威 `call_id`） |
| `/api/v2/calls/hangup` | POST | v2 挂断 |
| `/api/v2/calls/reject` | POST | v2 拒接 |
| `/api/v2/ring_group/answer` | POST | 振铃组接听 |
| `/api/v2/cluster/events` | GET | 集群事件长轮询 |

这些接口供 **AirChat macOS** 的虚拟卡池与集群功能使用。协议细节见 [AirChat 协议契约](../../docs/airchat-protocol.md)。

### 4. 视觉系统与命名体系规范化

- 墨金 `#C2A878` / 天蓝 `#2563EB` 双主题
- SIM 卡图标全套密度（48–192 dpi + anydpi-v26）
- 全项目面向用户文案由 `iPhone` 统一为 `AirChat`
- 设置页重构：配对入口集中到「设置 → 配对连接」
- `MainActivity.java` 净增约 1055 行 UI 代码

### 5. 能力探测机制（上游没有）

新增 `FeatureFlags.java`（80 行），从 Manifest 读取能力开关：

```java
FeatureFlags.voBLEAvailable(context)       // 是否声明 VoBLEGatewayService
FeatureFlags.cloudRelayAvailable(context)  // 是否有 CLOUD_RELAY meta-data
```

这让两个变体能共用同一份源码，Lite 版无需维护第二套代码。

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
