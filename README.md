# AI 桌宠 · Android 版

十只 AI 品牌桌宠，**悬浮在系统上层**（真正的桌面宠物），形象与台词和 Windows / macOS 版完全一致。

| 项 | 值 |
| --- | --- |
| 最低版本 | **Android 7.0（API 24）** |
| 目标版本 | Android 16（API 36） |
| 构建工具 | build-tools 34.0.0 / AGP 8.5.2 / Gradle 8.7 / JDK 17 |
| 依赖 | **零第三方依赖**（不引 AndroidX，不碰 Google Maven） |
| 实测机型 | 三星 Galaxy Tab S10 FE（One UI 8.5 / Android 16 / Exynos 1580） |

![预览](../DesktopPetsMac/images/grok.png)

## 功能
- 十只同时出现，各自走动 + 上下浮动（30fps）
- 点一下 = 戳它（冒爱心 + 说一句中文台词，台词读的是同一份 `lines.json`）
- 拖动 = 挪位置；长按 = 让这一只回去休息
- 通知栏常驻「**全部收回**」按钮 —— 永远不会出现关不掉的情况

## 安装
1. 允许「安装未知应用」
2. 安装 APK，打开后依次点：
   - ① 开启悬浮窗权限（`SYSTEM_ALERT_WINDOW`，必须）
   - ② 开启通知权限（前台服务必需，否则被系统回收）
   - ③ 忽略电池优化（**三星 One UI 必做**，后台管理很激进）
3. 点「放出来（十只）」

## 构建（国内网络也能全程不翻墙）
```bash
# JDK 17 + Android SDK(platform-36 / build-tools 34.0.0 / platform-tools)
gradle assembleDebug
```
依赖走**阿里云 Maven 镜像**，Gradle 分发走**腾讯云镜像**，SDK 从**腾讯云 AndroidSDK 镜像**获取 —— 因此不需要访问 Google。

## 平台支持
| 平台 | 状态 |
| --- | --- |
| Windows 7 SP1 / 8.1 / 10 / 11 | ✓ 支持（另有专门的 Win7 适配版与四种安装包） |
| macOS 10.13+（Intel 与 Apple 芯片） | ✓ 支持（见 DesktopPetsMac） |
| Android 7.0+ | ✓ 支持（本仓库） |
| **iOS / iPadOS / watchOS / tvOS / visionOS** | ✗ **没有版本，也不计划做** |
| **Linux 图形界面（GNOME / KDE / X11 / Wayland 等）** | ✗ **没有图形化版本**（Windows 版依赖 PowerShell + WPF；macOS 版依赖 Tkinter，均无法覆盖 Linux 桌面），无计划 |

### ⚠ 关于骁龙 888 / 8 Gen 1
这两颗 SoC **可以运行**，但**不推荐长期开启**：它们在持续动画负载下容易发热降频（三星 5nm 工艺），桌宠是常驻 30fps 的悬浮窗，会带来卡顿与额外耗电。
（作为对比：骁龙 865 及更新一代的中端芯片 —— 例如三星 Exynos 1580 —— 反而更稳。）

## 已知限制
- 宠物之间暂时**不会互相打招呼**（v0.2 计划：本地广播实现联动）
- 对战功能未移植（Windows 版有）
- DeX 模式下悬浮窗行为未验证

## License
MIT