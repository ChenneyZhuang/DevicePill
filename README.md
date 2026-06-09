# 🏝️ 流体云胶囊（FluidPill）v2.1.8

*ColorOS 16 Fluid Cloud · Gold Badge Charging Capsule*

专为 OPPO Find X9 Ultra 等 ColorOS 16 设备优化的充电流体云应用，**无需 root**。充电时摄像头旁自动显示金标胶囊，实时展示功率、电压、电流、温度。

*Optimized for ColorOS 16 devices. No root required. Golden pill appears when charging, showing real-time power, voltage, current, and temperature.*

---

## 功能 / Features

- 🏝️ **流体云胶囊**：充电时摄像头旁金标胶囊（ColorOS 16 原生流体云 API）
- 🔋 **Active Ring**：4 色渐变圆环（动感 / 极紫 / 海洋 / 日落 四主题）
- 📈 **实时曲线**：功率 + 温度 + 电压（60 点 × 3 秒 = 3 分钟窗口，含 Y 轴刻度）
- ⚡ **充电类型识别**：⚡超级闪充 / 🔌USB充电 / 🛜无线充电 一目了然
- 📱 **智能通知**：≥1A 自动显示为 A（2.8A 而非 2800mA），紧凑排版
- 🔔 **温度提醒**：充电中超过 42°C 自动弹出提醒
- ✅ **充满提醒**：充到 100% 弹通知提醒拔掉
- 🔄 **开机自启**：重启后自动恢复流体云监控
- 🔓 **安装即用**：授权通知权限后自动启动，无需手动操作

---

## 安装 / Install

从 [Releases](../../releases) 下载最新 APK 安装。

*Download the latest APK from [Releases](../../releases).*

---

## 设置指南 / Setup Guide

> ⚠️ 安装后需完成以下步骤，否则流体云不显示。

### 1. 授权通知 / Notification Permission
打开 App → 点击红色卡片授权。*Tap the red card to grant.*

### 2. 开启流体云 / Enable Fluid Cloud ⭐
```
设置 → 通知与状态栏 → 流体云 → FluidPill → 打开
Settings → Notifications → Fluid Cloud → FluidPill → ON
```

### 3. 锁屏显示 / Lock Screen
```
设置 → 通知 → FluidPill → 锁屏通知 → 显示
Settings → Notifications → FluidPill → Lock screen → Show
```

授权后 App 自动启动流体云，插充电器即可看到金标胶囊。

---

## 主题 / Themes

- 动感 Dynamic — 青 → 橙 → 黄 → 绿
- 极紫 JIZI — 浅紫 → 紫 → 紫罗兰 → 靛蓝
- 海洋 Ocean — 浅蓝 → 中蓝 → 深蓝 → 天蓝
- 日落 Sunset — 金橙 → 橙 → 深橙 → 红橙

---

## 更新日志 / Changelog

### v2.1.8
- ✨ 新增充电类型视觉区分：⚡超级闪充 / 🔌USB充电 / 🛜无线充电
- ✨ 新增温度过高提醒：充电中 >42°C 弹出通知（每次充电提醒一次）
- ✨ 新增充电完成提醒：充到 100% 弹通知
- ✨ 新增开机自启：重启后自动恢复流体云
- 🔧 通知栏电流优化：≥1A 显示 2.8A 而非 2800mA
- 🔧 通知栏排版精简：92% · 67W · ⚡超级闪充 · 4.2V · 2.8A · 38°C
- 🔧 UI 电流卡片同步 A 化
- 🔧 曲线标题「充电功率」→「功率」
- 🧹 清理 900+ 行未使用的 info/ 死代码
- 🧹 清理 CardGroup、BatteryReceiver、currH 等残留
- 🧹 R8 混淆从全保留改为真正混淆
- 🧹 合并 UI 电池读取，减少一半 readBattery 调用
- 🐛 修复版本号硬编码 → BuildConfig.VERSION_NAME
- 🐛 修复充电完成/温度提醒重复弹窗

### v2.1.6
- 修复 LaunchedEffect 导致服务关不掉

### v2.1.5
- 修复 OPPO 充电电流显示为负

### v2.1.1
- 修复流体云 4 个 Bug

### v2.1.0
- LLMonitor parity：始终显示 V/A，完整通知，网格曲线

### v2.0.0
- 重命名：DevicePill → FluidPill（流体云胶囊）
- 金标胶囊流体云方案

---

## FAQ

**Q: 没看到流体云胶囊？**
A: 系统设置 → 通知与状态栏 → 流体云 → 打开 FluidPill

**Q: 锁屏不显示？**
A: 设置 → 通知 → FluidPill → 锁屏通知 → 显示所有

**Q: 功率 0W？**
A: 部分设备/充电器不返回电流值

**Q: 后台被杀？**
A: 设置 → 应用 → FluidPill → 电池 → 不限制

---

## 技术栈 / Tech Stack

- **语言**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **流体云**: ColorOS 16 Fluid Cloud API（ProgressStyle + PromotedOngoing）
- **最低 SDK**: Android 12 (API 31)
- **目标 SDK**: Android 16 (API 36)

---

## 参考 / Credits

- **[LLMonitor](https://github.com/lele/llmonitor)** — 流体云技术方案（双通道 + ProgressStyle + android.requestPromotedOngoing）
- **[Android Compose Samples](https://github.com/android/compose-samples)** — Material 3 设计参考
- **[OPPO 流体云开发者文档](https://open.oppomobile.com)** — ColorOS Fluid Cloud 官方 API

---

*Fluid Cloud Capsule for ColorOS 16 · Inspired by LLMonitor*
