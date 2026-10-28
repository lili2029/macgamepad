# MacGamepad 定制版：把小米 15 变成王国之泪的无线手柄

> 完成时间：2026-10-23
> 成品：`~/Downloads/totk-pad/MacGamepad-TOTK手柄版.apk`
> 源码：`~/Documents/my/Controller-main`（上游 `Raheedpasha10/Controller`）
> 实测机型：小米 15（24129PN74C / Android 17 / HyperOS 4.0）→ MacBook Air M4

---

## 一、为什么要改

上游 MacGamepad v1.0.1 在王国之泪（MacNativeGames 移植版）上用不了，原因有三层，逐层揭开：

| # | 现象 | 真正原因 |
|---|---|---|
| 1 | App 一启动就闪退 | Android 14+ 强制校验前台服务类型的权限，而 App 在申请权限**之前**就调 `startForeground` |
| 2 | 不闪退了，但 Mac 认成键盘 | 用的是「键盘+鼠标+手柄」复合描述符，`PrimaryUsage` 被第一个 collection（键盘）占走 |
| 3 | 左摇杆能用了，右摇杆和 ABXY 没反应 | 右摇杆被放在 `Rx/Ry` 而游戏读 `Z/Rz`；按钮位序是 Xbox 序而游戏按 Switch usage 取位 |

第 2 层还有连带效应：复合描述符含键盘/鼠标 collection，macOS 会要求「输入监控」权限，**普通 App（游戏）没有该权限就静默打不开设备**——所以必须换成纯手柄描述符。

---

## 二、改了四处

全部在 `android/app/src/main/java/com/macgamepad/controller/`：

**1. `bt/BluetoothGamepadService.kt`** — 换成纯手柄描述符

```kotlin
- val descriptor = KeyboardHidDescriptor.descriptor        // 键盘+鼠标+手柄，PrimaryUsage=键盘
+ val descriptor = GamepadHidDescriptor.descriptor         // 纯手柄，PrimaryUsage=GamePad

- hid.sendReport(device, KeyboardHidDescriptor.GAMEPAD_REPORT_ID.toInt(), reportData)  // Report ID 3
+ hid.sendReport(device, GamepadHidDescriptor.REPORT_ID.toInt(), reportData)           // Report ID 1
```

**2. `bt/GamepadHidDescriptor.kt`** — 右摇杆让出 Z/Rz

```kotlin
- 0x09, 0x33,   // Rx — Right Stick Horizontal
- 0x09, 0x34,   // Ry — Right Stick Vertical
+ 0x09, 0x32,   // Z  — Right Stick Horizontal
+ 0x09, 0x35,   // Rz — Right Stick Vertical
```

对应地，模拟扳机从 `Z/Rz` 挪到 `Rx/Ry`（游戏不读这两个，ZL/ZR 改走按钮位）。

原因：游戏 `runtime/gamepad.m` 里 `hidPad.rx = hidZ`、`hidPad.ry = hidRz`，即**它把 Z/Rz 当右摇杆**。

**3. `input/InputModel.kt`** — 按钮位序改成 Switch

```kotlin
if (buttonB) mask |= (1 shl 0)              // usage 1  B(下)
if (buttonA) mask |= (1 shl 1)              // usage 2  A(右)
if (buttonY) mask |= (1 shl 3)              // usage 4  Y(左)
if (buttonX) mask |= (1 shl 4)              // usage 5  X(上)
if (buttonL1) mask |= (1 shl 6)             // usage 7
if (buttonR1) mask |= (1 shl 7)             // usage 8
if (leftTrigger > 0.5f) mask |= (1 shl 8)   // usage 9  ZL
if (rightTrigger > 0.5f) mask |= (1 shl 9)  // usage 10 ZR
if (buttonSelect) mask |= (1 shl 10)        // usage 11 minus
if (buttonStart) mask |= (1 shl 11)         // usage 12 plus
if (buttonL3) mask |= (1 shl 13)            // usage 14
if (buttonR3) mask |= (1 shl 14)            // usage 15
```

**4. `app/build.gradle.kts`** — 修闪退

```kotlin
- targetSdk = 35
+ targetSdk = 33
```

---

## 三、怎么用

### 首次（或改了描述符之后）

1. 手机安装 `MacGamepad-TOTK手柄版.apk`
2. App 里 `Controller Skin` 选 DualSense、`Output Protocol` 选 **Gamepad**
3. 点 **Initialize Controller**
4. **Mac**：系统设置 → 蓝牙 → `Wireless Controller` → 配对
5. App 点 **Launch DualSense**，进横屏手柄界面

### 以后日常

手机开 App → 点 Launch → Mac 自动重连。**不需要再配对。**

---

## 四、以后再改代码，怎么重新打包

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=~/Library/Android/sdk
cd ~/Documents/my/Controller-main/android
./gradlew assembleDebug --no-daemon
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

首次约 3.5 分钟，增量约 20 秒。

> `java_home -v 17` 会错误地返回 Java 11 的路径，必须手写上面对应 `/opt/homebrew/opt/openjdk@17` 的路径。

---

## 五、故障排查

### 改了描述符却不生效

**HID 描述符是在配对时协商并缓存在 macOS 侧的。** 改完必须：

1. Mac：蓝牙 → 忽略此设备
2. Mac：**关掉蓝牙，等 5 秒再开**（清内存里的 SDP 缓存）
3. 手机：App 里 Disconnect，手机蓝牙也关一下
4. 重新配对

### 一条命令判断有没有接上

```bash
# 设备是否纯手柄、是否只有 Report ID 1
ioreg -r -c IOHIDDevice -w0 | grep -A30 '"SerialNumber" = "3C:38:24:BC:FC:1E"' \
  | grep -oE 'DeviceUsagePairs.*' | head -1

# 正确结果：DeviceUsagePairs" = ({"DeviceUsagePage"=1,"DeviceUsage"=5})   ← 只有一项
# 错误结果：包含 {1,6} 或 {1,2}  ← Mac 还在用旧的复合描述符

# usage 应为 5（GamePad），不能是 6（Keyboard）
hidutil list | awk '$4==1 {print "usage="$5}'
```

### 游戏到底认没认这个手柄

```bash
grep -a "HID gamepad connected" ~/Library/Logs/MacNativeGames/totk.log | tail -2
# 出现 [totk-input] HID gamepad connected: Wireless Controller (unknown) 才算接入
```

### 现象：手柄有反应但游戏里「事件乱飞」

说明 Mac 用旧描述符解析新数据，字节错位。**重新配对**即可，不用改代码。

---

## 六、已知限制

- **没有震动**：这条 HID 通道没做 rumble 输出（游戏日志会显示 `no controller with haptics`）。
- **没有陀螺仪**：手机 App 不发 gyro 数据。想用体感瞄准还是得靠游戏内的**鼠标模式（7 键）**。
- **ZL/ZR 是数字量**：因为 Z/Rz 被右摇杆占用，扳机只能走按钮位，没有半按。
- **ABXY 按 Switch 位置映射**：手机界面上的 A 是 Switch 的 A（右侧）。如果觉得别扭，改 `InputModel.buttonBitmask()` 里的位即可。

---

## 七、这套方案的意外收获

流行的说法是「小米、一加、华为等厂商阉割了蓝牙 HID Device，手机当不了手柄」。

**实测证明不是。** 小米 15 在 Android 17 / HyperOS 4.0 下完全可以，之前所有失败都来自 App 自身的实现问题：

- 描述符的 collection 顺序错了
- 描述符混了键盘鼠标，触发权限限制
- 轴的 usage 与目标程序约定不一致
- 按钮位序用了 Xbox 而非 Switch

换句话说，凡是「HID 注册失败」的国产机，值得先用本文的诊断命令确认到底是系统限制还是 App 的问题。
