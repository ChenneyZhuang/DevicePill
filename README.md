# 充电·岛（DevicePill）

*ColorOS 16 Charging Fluid Cloud · Gold Badge Charging Display*

专为 OPPO Find X9 Ultra 等 ColorOS 16 设备优化的充电流体云应用，**无需 root**。

*Optimized for ColorOS 16 devices (OPPO Find X9 Ultra, etc.). No root required.*

---

## 功能 / Features

- ⚡ **充电监控**：功率 (W)、电压、电流、温度、健康度、电池技术
- 🔋 **Active Ring**：4 色渐变圆环（动感/极紫/海洋/日落 四主题）
- 📈 **功率曲线**：60 秒历史折线图
- 🌡️ **温度曲线**：60 秒温度趋势
- 🏝️ **流体云胶囊**：充电时摄像头旁金标胶囊显示实时数据
- 🏝️ **锁屏岛**：展开显示完整参数
- 🚀 **开机自启**

> ⚡ **Charging Monitor**: Power (W), voltage, current, temperature, health, battery tech
> 🔋 **Active Ring**: 4-theme sweep gradient battery gauge
> 📈 **Power Curve**: 60-second line chart
> 🌡️ **Temperature Curve**: 60-second trend
> 🏝️ **Fluid Cloud Capsule**: Real-time data in ColorOS golden pill
> 🏝️ **Lock Screen Island**: Expanded charging details

---

## 安装 / Install

从 [Releases](../../releases) 下载最新 APK → 安装。

*Download the latest APK from [Releases](../../releases).*

---

## 设置指南 / Setup Guide

> ⚠️ 安装后需手动完成以下步骤，否则流体云不显示。
> *Manual steps required after install — fluid cloud won't show otherwise.*

### 1. 授权通知 / Notification Permission
首次打开 App 弹窗点「允许」。*Grant permission when prompted.*

### 2. 开启流体云 / Enable Fluid Cloud ⭐
```
设置 → 通知与状态栏 → 流体云 → DevicePill → 打开
Settings → Notifications → Fluid Cloud → DevicePill → ON
```

### 3. 锁屏显示 / Lock Screen
```
设置 → 通知与状态栏 → DevicePill → 锁屏通知 → 显示
Settings → Notifications → DevicePill → Lock screen → Show
```

### 4. 启动 / Start
点击「启动流体云」→ 插充电器 → 摄像头旁出现金标胶囊。
*Tap "Start" → plug in charger → golden pill appears.*

---

## 主题 / Themes

| 主题 Theme | 环形渐变 Ring Gradient |
|-----------|----------------------|
| 动感 Dynamic | 青 → 橙 → 黄 → 绿 Cyan → Orange → Yellow → Green |
| 极紫 JIZI | 浅紫 → 紫 → 紫罗兰 → 靛蓝 Lilac → Purple → Violet → Indigo |
| 海洋 Ocean | 浅蓝 → 中蓝 → 深蓝 → 天蓝 Light → Medium → Deep → Sky Blue |
| 日落 Sunset | 金橙 → 橙 → 深橙 → 红橙 Gold → Orange → Deep → Red Orange |

---

## FAQ

**Q: 没看到流体云胶囊？ / No fluid cloud pill?**
A: 系统设置 → 通知与状态栏 → 流体云 → 打开 DevicePill
*Settings → Notifications → Fluid Cloud → Enable DevicePill*

**Q: 锁屏不显示？ / Not showing on lock screen?**
A: 设置 → 通知 → DevicePill → 锁屏通知 → 显示所有
*Settings → Notifications → DevicePill → Lock screen → Show all*

**Q: 功率 0W？ / Power shows 0W?**
A: 部分设备/充电器不返回电流值。*Some devices/chargers don't report current.*

**Q: 后台被杀？ / Killed in background?**
A: 设置 → 应用 → DevicePill → 电池 → 不限制
*Settings → Apps → DevicePill → Battery → Unrestricted*

---

## 参考 / Credits

- **[LLMonitor](https://github.com/lele/llmonitor)** — 流体云技术方案（双通道 + `ProgressStyle` + `android.requestPromotedOngoing`）参考自 LLMonitor 的实现；配色及卡片设计规格亦学习自该项目。*Fluid cloud technique, color palettes, and card design specs referenced from LLMonitor.*
- **[Android Compose Samples](https://github.com/android/compose-samples)** — Material 3 设计参考
- **[Now in Android](https://github.com/android/nowinandroid)** — Compose 最佳实践

---

*Inspired by ColorOS 16 金标充电 · LLMonitor*
