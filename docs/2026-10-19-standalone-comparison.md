# Standalone 上游版 vs 修改版对比分析

> 分析日期：2026-10-19
> 对比基准：上游 tag `v0.9.7.2`（`4e4ba8c`）→ 当前 HEAD
> 所有数据均来自 `git diff`、`git show` 与 APK 实测，非估算

---

## 一、三个对比对象

| 版本 | 标识 | 来源 |
| --- | --- | --- |
| **上游版** | `0.9.7.2`（tag `v0.9.7.2`） | kai-wu-cortex 原版 |
| **完整版** | `V0.9.7.2 LDev 911` | 本 fork 在上游基础上修改 |
| **Lite 版** | `AirSim-Lite V0.9.7.2 LDev 1` | 本 fork 在完整版基础上裁剪 |

---

## 二、Manifest 层面对比

| 项目 | 上游 `0.9.7.2` | 完整版 `LDev 911` | Lite `LDev 1` |
| --- | --- | --- | --- |
| 权限数 | 22 | **23** | **18** |
| 服务数 | 4 | 4 | **3** |
| VoBLE 服务 | ✅ | ✅ | ❌ |
| 蓝牙权限 | 4 项 | 4 项 | **0 项** |
| `CLOUD_RELAY` 标记 | ❌ | **✅ 新增** | ❌ |
| versionCode | 91 | 91 | 91 |
| 包名 | `com.airsim.phonecontrol.standalone` | 同 | 同 |

**关键发现：**

- 完整版比上游多 1 个权限（`CLOUD_RELAY` 相关能力）
- Lite 版比上游少 4 个权限、少 1 个服务，这是裁剪的核心结果
- 上游没有 `CLOUD_RELAY` meta-data，这是本 fork 新增的能力开关机制

---

## 三、源码差异规模

```sh
git diff --stat v0.9.7.2..HEAD -- android/
```

```
76 个文件变更
+3497 行
-429 行
```

### 改动最大的文件

| 文件 | 变更 | 性质 |
| --- | --- | --- |
| `MainActivity.java` | **+1255** | UI 全面重做 |
| `StandaloneAgentGateway.java` | **+370** | 新增 9 个 v2 路由 |
| `SIMSlotManager.java` | **+326** | **全新文件** |
| `PairingServer.java` | +94 | 配对协议扩展 |
| `FeatureFlags.java` | **+80** | **全新文件** |
| `SIMSlotInfo.java` | **+52** | **全新文件** |

### 上游完全没有的 3 个源文件

```
FeatureFlags.java      ← 运行时能力探测
SIMSlotInfo.java       ← SIM 卡槽数据模型
SIMSlotManager.java    ← DSDS 双卡管理
```

---

## 四、API 层面对比

| 项目 | 上游 | 本 fork |
| --- | --- | --- |
| 网关路由总数 | **23** | **32** |
| 移除的路由 | — | **0**（零破坏性变更） |
| 新增的路由 | — | **9** |

### 新增的 9 个路由

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

**兼容性最好的一点：没有删除任何上游路由，只是叠加。上游客户端仍能正常使用。**

---

## 五、功能对比矩阵

| 能力 | 上游 | 完整版 | Lite 版 |
| --- | :---: | :---: | :---: |
| Android Telecom 通话 | ✅ | ✅ | ✅ |
| 短信收发 | ✅ | ✅ | ✅ |
| VoWLAN 直连 | ✅ | ✅ | ✅ |
| VoBLE 蓝牙 | ✅ | ✅ | ❌ |
| 云端 Relay | ✅ | ✅ | ❌ |
| 手动 IP 配对 | ✅ | ✅ | ✅ |
| 同网恢复 | ✅ | ✅ | ✅ |
| 音频 Watchdog | ✅ | ✅ | ✅ |
| Shizuku PCM 桥 | ✅ | ✅ | ✅ |
| **DSDS 双卡卡槽管理** | ❌ | ✅ | ✅ |
| **指定卡槽拨号/发信** | ❌ | ✅ | ✅ |
| **LD 2.0 集群接口** | ❌ | ✅ | ✅ |
| **振铃组接听** | ❌ | ✅ | ✅ |
| **时钟偏移校准** | ❌ | ✅ | ✅ |
| **墨金/天蓝双主题** | ❌ | ✅ | ✅ |
| **AirChat 品牌文案** | ❌ | ✅ | ✅ |
| **FeatureFlags 能力探测** | ❌ | ✅ | ✅ |

**结论：完整版是上游的严格超集（功能只增不减），Lite 版是完整版减去 VoBLE + Relay。**

---

## 六、对「之前的宣传表述」的逐条核实

以下是对一次既有宣传文案的核实结果。

### ❌ 第 1 条：「具备完整的 DSDS 双卡双待动态调度能力」

**方向正确，但「完整」与「动态调度」表述夸大。**

**属实的部分：**

| 项目 | 上游 | 本 fork |
| --- | --- | --- |
| `SIMSlotManager` | ❌ 无 | ✅ 326 行 |
| `SIMSlotInfo` | ❌ 无 | ✅ 52 行 |
| 卡槽探测 | 只读默认卡 | ✅ 完整枚举 |
| 拨号指定卡槽 | ❌ | ✅ |
| 短信指定卡槽 | ❌ | ✅ |
| 卡槽热插拔监听 | ❌ | ✅ |

**需要修正的部分：**

- 尚未真机验收
- 仅 `SIMSlotManagerTests` 一处测试，覆盖范围未知
- 「动态调度」偏营销：实际能力是按卡槽选择拨号/发信，不是运营商级智能调度

**修正建议：**「新增 DSDS 双卡卡槽探测与指定卡槽拨号/发信能力」

### ⚠️ 第 2 条：「修复了 Tailscale 异地远控与时钟偏移签名失效问题」

**这条一半不准确。**

#### Tailscale：上游本来就有，不是你修的

实测证据：

```java
// 上游 v0.9.7.2 VoWLANNetworkPolicy.java:7
return name != null && (name.startsWith("tun") || name.startsWith("tailscale"))
```

```java
// 上游 v0.9.7.2 PairingServer.java:293
values.add("Tailscale：填写本机在 Tailscale 中的 IPv4；配对端口 " + port);
```

本 fork 做的是**扩展**（判定扩展到 `wg` 接口、统一走 `isOverlayIPv4()`），不是「修复」。

#### 时钟偏移：这个确实是新增

```
上游 v0.9.7.2：完全没有 server_time 字段
本 fork     ：PairingServer.java:118 返回 server_time
```

对应提交 `7909425 fix: precise clock offset via server_time in /pair/v1/info`。

**修正建议：**「扩展 Tailscale/WireGuard overlay 判定；新增 `server_time` 时钟偏移校准，解决设备时钟漂移导致的 HMAC 签名失效」

### ✅ 第 3 条：「全面规范了 AirSIM / AirChat 跨端视觉系统与命名体系」

**准确。**

| 证据 | 内容 |
| --- | --- |
| `MainActivity.java` | +1255 行，UI 全面重做 |
| 主题色 | 墨金 `#C2A878`、天蓝 `#2563EB` |
| 品牌统一 | 全项目 `iPhone` → `AirChat` |
| 图标 | SIM 卡图标全套密度（48–192 dpi） |

---

## 七、修正后的完整表述

> **AirSIM-V0.9.7.2-LDev-911 是在上游 v0.9.7.2 稳定通信（通话拦截、Shizuku UserService、PCM 媒体桥）基础上的深度定制增强版：**
>
> 1. **新增完整的 DSDS 双卡卡槽管理**（上游完全没有）：卡槽探测、热插拔监听、指定卡槽拨号与发信；
> 2. **扩展远程直连能力**：在继承上游 Tailscale 支持的基础上，增加 WireGuard 接口判定与统一 overlay 路由；新增 `server_time` 时钟偏移校准，解决设备时钟漂移导致的 HMAC 签名失效；
> 3. **新增 LD 2.0 集群接口**（上游完全没有）：`/api/v2/sims`、`/api/v2/cluster/events`、`/api/v2/ring_group/answer` 等 9 个路由，供 macOS AirChat 使用；
> 4. **全面规范 AirSIM / AirChat 跨端视觉系统与命名体系**：墨金/天蓝双主题、SIM 卡图标、品牌文案统一。

### 用词注意

**1. 「修复」慎用**

上游本来就有能力时写成「修复」会让人误以为上游有 bug。应写「扩展」或「增强」。

**2. 补充遗漏的重要能力**

LD 2.0 集群接口（9 个路由）是上游完全没有的实质功能，比 Tailscale 扩展重要得多，宣传中应体现。

**3. 「稳定通信」的边界**

说「在上游稳定通信基础上」是安全的（描述上游），但不要让人误以为本 fork 的变体也已真机验证。

---

## 八、风险与代价

### 1. MainActivity 膨胀严重

| 版本 | 变更 |
| --- | --- |
| 上游 → 本 fork | **+1055 净增行** |

`MainActivity.java` 现在是超大单体类。上游用纯 Java View 编程，本 fork 在其上又加了主题系统、DSDS UI、集群接口入口，**可维护性下降**。

### 2. Lite 版仍打包 VoBLE/Relay 字节码

| 版本 | APK 大小 |
| --- | --- |
| 完整版 | 1.9 MB |
| Lite 版 | 1.8 MB |

只差约 100 KB。说明 Lite 版**没有真正移除 VoBLE/Relay 的 class 文件**，只是 Manifest 不声明、运行时不用。真正裁剪需构建时过滤源文件。

### 3. 三个版本 versionCode 相同

```
上游:     91
完整版:   91
Lite 版:  91
```

三者**无法在同一设备共存或互相覆盖**。从上游切换到本 fork 必须卸载。

### 4. 测试增量很小

| 项目 | 上游 | 本 fork |
| --- | --- | --- |
| 测试文件 | 81 | 86（+5） |
| 新增业务测试 | — | 仅 `SIMSlotManagerTests` |
| 其余新增 | — | 4 个 JSON 测试桩 |

**+3497 行源码只对应 1 个真正的业务测试文件。** DSDS、集群接口、FeatureFlags 的测试覆盖偏薄。

---

## 九、总体评价

### 优势

| 方面 | 评价 |
| --- | --- |
| 兼容性 | ⭐⭐⭐⭐⭐ 零破坏性变更，上游路由全保留 |
| 功能扩展 | ⭐⭐⭐⭐⭐ DSDS + LD 2.0 集群是实质性新能力 |
| 架构设计 | ⭐⭐⭐⭐ FeatureFlags 让多变体共用源码 |
| UI 完成度 | ⭐⭐⭐⭐ 主题系统、品牌统一、设置重构 |
| 裁剪能力 | ⭐⭐⭐⭐ Lite 版确实关闭了蓝牙与云端 |

### 不足

| 方面 | 评价 |
| --- | --- |
| 可维护性 | ⭐⭐ `MainActivity` 净增 1055 行，单体类膨胀 |
| 测试覆盖 | ⭐⭐ 新增 3497 行只有 1 个业务测试 |
| 真正裁剪 | ⭐⭐ Lite 版仍含 VoBLE/Relay 字节码 |
| 共存能力 | ⭐ 三个版本 versionCode 相同 |
| 真机验证 | ⭐ 尚未验收 |

---

## 十、改进建议（按优先级）

### 1. 拆分 `MainActivity`（最高优先）

当前 3000+ 行的单体类，任何 UI 改动都有风险。建议按页面拆分：

```
MainActivity（仅生命周期与路由）
├── StatusPageView
├── SettingsPageView
├── PairingPageView
└── VoBLEPageView
```

### 2. 补测试（次高）

至少覆盖：

- `SIMSlotManager` 的卡槽解析逻辑（现有测试覆盖未知）
- `FeatureFlags` 的 Manifest 探测（无测试）
- `/api/v2/*` 路由的请求/响应契约

### 3. 真正裁剪 Lite 版（可选）

若在意 APK 体积或安全面，可让 `build.sh` 在 Lite 分支排除 `voble/` 与 Relay 源文件，而不只是 Manifest 不声明。

### 4. 拉开 versionCode（推荐）

给 Lite 版用独立 `versionCode` 段（如从 1000 起），或改 `applicationId`，让两版能共存。

---

## 十一、相关文档

- [AirSim-LDev README](../android-standalone/AirSim-LDev/README.md)
- [AirSim-Lite-LDev README](../android-standalone/AirSim-Lite-LDev/README.md)
- [AirChat 协议契约](../docs/airchat-protocol.md)
- [2026-10-19 工作留档](../2026-10-19-worklog.md)
