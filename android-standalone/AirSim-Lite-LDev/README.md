# AirSim-Lite-LDev —— Android Standalone 轻量版

本目录是 [android-standalone](../README.md) 下的 **Lite 轻量版**变体：
在完整修改版的基础上裁掉 VoBLE 蓝牙与云端 Relay，只保留局域网直连。

| 项目 | 值 |
| --- | --- |
| 包名 | `com.airsim.phonecontrol.standalone` |
| versionName | `AirSim-Lite V0.9.7.2 LDev 1` |
| versionCode | `91` |
| 上游基线 | `0.9.7.2` |
| 修改次数 | `1` |

## 能力范围

| 能力 | 状态 |
| --- | --- |
| VoWLAN 局域网直连通话（控制 + PCM） | ✅ 保留 |
| Android Telecom 拨号、接听、拒接、挂断、DTMF | ✅ 保留 |
| 短信收发（含可选收件箱兜底） | ✅ 保留 |
| DSDS 双卡卡槽探测与默认通话卡／短信卡选择 | ✅ 保留 |
| Shizuku 通话 PCM 桥 | ✅ 保留 |
| **VoBLE 蓝牙直连** | ❌ 已移除 |
| **云端 Relay（心跳 / 命令 / 云端 PCM）** | ❌ 已移除 |

移除方式是**在 Manifest 层不声明**，不是删代码。源类仍然存在于
`android/phone-control-app/src/com/airsim/phonecontrol/voble/` 与 Relay 相关文件中，
但 Lite 的 APK 不会包含对应的服务声明、蓝牙权限或 `CLOUD_RELAY` 标记，
因此系统不会授予蓝牙能力，内置 Agent 也不会启动云端链路。

## 与完整版的区别

| 差异点 | AirSim-Lite-LDev（本目录） | AirSim-LDev |
| --- | --- | --- |
| `VoBLEGatewayService` 服务声明 | ❌ 无 | ✅ 有 |
| 蓝牙权限（4 项） | ❌ 无 | ✅ 有 |
| `com.airsim.phonecontrol.CLOUD_RELAY` | ❌ 无 | ✅ `true` |
| VoWLAN | ✅ | ✅ |
| 短信 / Telecom / PCM 桥 | ✅ | ✅ |

`FeatureFlags.voBLEAvailable()` 返回 `false` 时，设置页不会渲染「VoBLE 蓝牙配对」卡片，
状态页不显示蓝牙诊断卡，VoBLE 认证页也无法从任何路径打开。
`FeatureFlags.cloudRelayAvailable()` 返回 `false` 时，内置 Agent 只维持本地模式。

## 构建与测试

```sh
./android-standalone/AirSim-Lite-LDev/build.sh
./android-standalone/AirSim-Lite-LDev/test.sh
```

调试 APK 输出：

```text
android-standalone/AirSim-Lite-LDev/build/android/AirSim-Lite-LDev-debug.apk
```

测试会校验：

1. 本 Manifest **不得**出现 `VoBLEGatewayService`、`permission.BLUETOOTH` 或 `CLOUD_RELAY`；
2. 构建出的 APK 内确实没有这些声明（用 `apkanalyzer` 读实际 APK）；
3. 必须保留 `VoWLANGatewayService`、内置 Agent、短信接收器；
4. 上游全部共享测试与 PCM 桥构建通过。

## 安装注意

本变体与完整版：

- 包名相同（`com.airsim.phonecontrol.standalone`）
- versionCode 相同（`91`）

**两者不能共存，也不能互相覆盖升级。** 切换必须先卸载，
这会清除已保存的 AirChat 配对凭据与默认电话角色。

## 适用场景

- 只需要同网（同一 Wi-Fi / 三星热点）直连，不使用公网远程
- 希望 APK 不申请蓝牙权限，减少权限面
- 不希望设备向云端注册心跳

## 验收边界

本变体**尚未完成真机验收**。已完成的验证仅为：

- 两个变体的 debug APK 均构建成功并完成签名校验
- APK 内的 Manifest 与源码 Manifest 一致（VoBLE / 蓝牙 / Relay 的取舍符合预期）
- 共享的 Android 单元测试与 PCM 桥构建通过

未验证：真机安装、默认电话角色、Shizuku 授权、VoWLAN 实际通话与双向音频。
