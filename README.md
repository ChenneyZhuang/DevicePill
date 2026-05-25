# 流体云胶囊（FluidPill）

*ColorOS 16 Fluid Cloud · Gold Badge Charging Capsule*

专为 OPPO Find X9 Ultra 等 ColorOS 16 设备优化的充电流体云应用，**无需 root**。充电时摄像头旁自动显示金标胶囊，实时展示功率、电压、电流、温度。

*Optimized for ColorOS 16 devices. No root. Golden pill appears when charging, showing real-time power, voltage, current, and temperature.*

---

## 功能 / Features

- 🏝️ **流体云胶囊**：充电时摄像头旁金标胶囊（ColorOS 16 原生流体云）
- 🔋 **Active Ring**：4 色渐变圆环（动感/极紫/海洋/日落 四主题）
- 📈 **实时曲线**：功率 + 温度 + 电压（60 点 × 3 秒 = 3 分钟窗口，含 Y 轴刻度）
- ⚡ **双卡监控**：功率+电流、电压+温度、供电+健康
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

| 主题 | 渐变 |
|------|------|
| 动感 Dynamic | 青 → 橙 → 黄 → 绿 |
| 极紫 JIZI | 浅紫 → 紫 → 紫罗兰 → 靛蓝 |
| 海洋 Ocean | 浅蓝 → 中蓝 → 深蓝 → 天蓝 |
| 日落 Sunset | 金橙 → 橙 → 深橙 → 红橙 |

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

## 参考 / Credits

- **[LLMonitor](https://github.com/lele/llmonitor)** — 流体云技术方案（双通道 + ProgressStyle + android.requestPromotedOngoing）
- **[Android Compose Samples](https://github.com/android/compose-samples)** — Material 3 设计参考

---

*Fluid Cloud Capsule for ColorOS 16 · Inspired by LLMonitor*
