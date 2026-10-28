# 构建与调试

本文说明如何从源码构建 APK，以及 `tools/` 下几个 macOS 侧诊断工具怎么用。这些工具是当初排查「App 闪退 / 被认成键盘 / 摇杆错位」时写下来的，遇到 HID 问题比盲改代码有效得多。

---

## 一、环境要求

| 项目 | 要求 | 说明 |
| :--- | :--- | :--- |
| JDK | **17** | `compileOptions` 与 `jvmTarget` 均为 17，低版本会在 Gradle 配置阶段直接失败 |
| Android SDK | **compileSdk 35** / minSdk 28 / targetSdk 33 | 用 Android Studio 的 SDK Manager 装 `Android 15 (API 35)` 平台 |
| Gradle | 8.11.1 | 由 `android/gradle/wrapper` 自带，无需单独安装 |
| macOS 工具链 | Xcode Command Line Tools | 提供 `clang` 与 IOKit / CoreFoundation 框架，仅调试工具需要 |
| SDL2 | 可选 | 只有 `gamepad_tester` 需要，`brew install sdl2` |

应用信息：`applicationId = com.macgamepad.controller`，`versionName = 0.1.0-alpha`。

---

## 二、构建 APK

```bash
git clone https://github.com/lili2029/macgamepad.git
cd macgamepad/android
./gradlew assembleDebug
```

产物在 `android/app/build/outputs/apk/debug/app-debug.apk`，装到 Android 9（API 28）及以上设备：

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

`android/app/build/` 与 `*.apk` 都在 `.gitignore` 里，整个仓库真正跟踪的内容只有约 800K，构建产物不会污染仓库。

**JDK 版本报错对照**：

- `Unsupported class file major version` → 你在用 JDK 21+ 或 11− 跑 Gradle，切到 17。
- `Android Gradle plugin requires Java 17` → 同上。

---

## 三、macOS 侧诊断工具

先编译（`print_reports` 与 `gamepad_bridge` 由 Makefile 管）：

```bash
cd tools
make              # 产出 gamepad_bridge 与 print_reports
```

两个需要单独编译的：

```bash
# SDL2 手柄测试器，验证系统层是否把手柄认成了 SDL 游戏手柄
cd tools && cc -O3 -o gamepad_tester gamepad_tester.c $(sdl2-config --cflags --libs)

# 用与游戏完全相同的匹配条件打开 HID 设备，打印收到的报文
clang -framework Foundation -framework IOKit -o probe_hid probe_hid.m
```

### 各工具用途

| 工具 | 它回答什么问题 |
| :--- | :--- |
| `print_reports` | **手机到底有没有在发报文？发的什么？** 监听并打印手机发来的 HID input report。仓库里的 `tools/diagnose.log` 就是它的输出样例 |
| `probe_hid` | **游戏读不到，是描述符的问题还是权限的问题？** 它复刻游戏侧 `IOHIDManager` 的匹配条件，能打开就说明设备没问题、问题在别处 |
| `gamepad_tester` | **系统认出的是不是游戏手柄？** 走 SDL2 的游戏手柄 API，能看到按键/摇杆实时值就说明枚举正确 |
| `gamepad_bridge` | **没有手机时怎么验证描述符？** 用 `IOHIDUserDeviceCreateWithProperties` 在本机注册一个用户态虚拟 HID 设备，描述符与手机端那份统一描述符一致，可用来快速试描述符改动 |

### 看懂 `print_reports` 的输出

`tools/diagnose.log` 里的典型一行：

```
[Diagnose] Received Report (ID: 1, Len: 14): 01 00 00 00 00 00 00 00 00 0F 00 01 00 00
```

- **ID 必须是 1**。`1` 是纯手柄描述符的 Report ID；如果打印出来是 `3`，说明还在用旧的「键盘 + 鼠标 + 手柄」复合描述符——这正是手机被 macOS 认成键盘的根因。
- **Len 是 14**。长度和描述符里的字段数对不上时，主机侧很可能整个报文被丢弃，表现就是「设备连上了但完全没有输入」。
- 一行都没有 → 先确认手机端服务是否真的在发（App 里连上了吗），再查 macOS 的输入监控权限。

---

## 四、没有手机时的排查顺序

1. `make` 出 `gamepad_bridge`，跑起来 —— 它会在本机注册一个虚拟 HID 手柄。
2. 另开窗口跑 `probe_hid` —— 能收到报文，说明描述符本身没问题，可以把问题推到手机端。
3. 跑 `gamepad_tester` —— 确认系统把它当手柄而不是键盘。
4. 再上真机，用 `print_reports` 对比两侧报文，看是 ID、长度还是位序不一致。

---

## 五、现象 → 原因速查

| 现象 | 根因 | 处置 |
| :--- | :--- | :--- |
| App 一启动就闪退（Android 14+） | 前台服务类型权限还没申请就调了 `startForeground` | 先申请 `FOREGROUND_SERVICE_CONNECTED_DEVICE`，再启动服务 |
| Mac 把手机识别成键盘 | 复合描述符的 `PrimaryUsage` 被第一个 collection（键盘）占走 | 换成纯手柄描述符 |
| 系统能识别，但游戏里设备打不开 | 复合描述符触发「输入监控」权限要求，普通游戏没有该权限 | 同上，换纯手柄描述符 |
| 左摇杆正常，右摇杆和 ABXY 无反应 | 右摇杆放在 `Rx/Ry`，而移植版 runtime 把 Z/Rz 当右摇杆 | 右摇杆改到 Z/Rz，扳机挪到 Rx/Ry |
| A/B、X/Y 错位 | 用 Xbox 位序，游戏按 Switch usage 取位 | 位序改 1=B、2=A、4=Y、5=X |

四处改动的完整成因与代码对照见 [MacGamepad-TOTK定制说明.md](MacGamepad-TOTK定制说明.md)。

---

## 六、提交前自查

- `git status` 里不应出现 `android/app/build/`、`*.apk`、`tools/gamepad_bridge`、`tools/print_reports`（都已忽略）。
- 改动涉及描述符或位序时，至少用 `print_reports` 抓一次真实报文贴进提交说明或 issue，省得下次重新推。
