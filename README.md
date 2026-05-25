# 充电·岛（DevicePill）

ColorOS 16 充电流体云 · 金标充电显示

**无需 root**，专为 OPPO Find X9 Ultra 等 ColorOS 16 设备优化。

---

## 功能

- ⚡ **充电实时监控**：功率（W）、电压、电流、温度
- 🔋 **电量环动画**：金色渐变圆环，实时显示电量百分比
- 🏝️ **流体云胶囊**：充电时在摄像头旁显示 `⚡ 8.0W · 92% · 32°C`
- 🏝️ **锁屏岛**：展开显示完整充电参数
- 🔔 **常亮显示**：锁屏常驻，随时查看
- 🚀 **开机自启**：重启自动恢复监控

---

## 安装

### 直接装 APK

从 [Releases](../../releases) 下载最新 `app-debug.apk` → 安装 → 按下方设置指南操作。

### 自己编译

```bash
git clone https://github.com/ChenneyZhuang/DevicePill.git
cd DevicePill
echo "sdk.dir=/path/to/Android/sdk" > local.properties
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

---

## ⚠️ 设置指南（必须！）

安装后**不会自动生效**，需要完成以下 4 步：

### 1. 授权通知权限
首次打开 App 会弹窗，点击「允许」。  
如果错过了，App 内红色卡片会提示，点击即可重新授权。

### 2. 开启流体云显示 ⭐ **最关键**
这是让充电信息出现在摄像头旁「金标胶囊」的关键设置！

**路径：**
```
设置 → 通知与状态栏 → 流体云 → 找到「DevicePill」→ 打开
```

不同 ColorOS 版本可能的路径：
- `设置 → 通知与状态栏 → 更多设置 → 流体云`
- `设置 → 特色功能 → 流体云`

### 3. 允许锁屏显示
```
设置 → 通知与状态栏 → App 通知管理 → DevicePill → 锁屏通知 → 显示所有通知
```

### 4. 启动并验证
回到 App → 点击「启动流体云」→ 插上充电器 → 摄像头旁应出现金标充电胶囊！

---

## 需要的权限

| 权限 | 用途 |
|------|------|
| 通知 | 流体云/锁屏岛显示实时充电数据 |
| 开机自启 | 重启后自动恢复监控 |

不需要 root，不需要 BATTERY_STATS，不需要悬浮窗、无障碍。

---

## 设备支持

- **最佳**：ColorOS 16（OPPO Find X9 Ultra / X8 Pro / Reno 系列）
- **兼容**：Android 12+ 均可运行（流体云为 ColorOS 独占）
- **其他品牌**：通知栏会显示充电数据，但无流体云胶囊效果

---

## 常见问题

**Q: 启动后没看到流体云胶囊？**
A: 确保已完成设置指南第 2 步——在系统设置里打开 DevicePill 的流体云开关。

**Q: 锁屏上没显示？**
A: 去 `设置 → 通知 → DevicePill → 锁屏通知 → 显示所有通知`。

**Q: 功率显示 0W？**
A: 部分设备/充电器组合下，系统 API 不返回电流值，属正常现象。电压和温度仍可正常显示。

**Q: 流体云不更新？**
A: ColorOS 后台管理可能限制了 App。去 `设置 → 应用 → DevicePill → 电池 → 不限制`。

---

## 技术架构

```
DevicePill/
├── MainActivity.kt           → Compose UI（电池环 + 实时数据）
├── DeviceMonitorService.kt   → 前台服务（流体云通知）
│   ├── battery_monitor 通道   → IMPORTANCE_LOW（金标胶囊）
│   └── battery_live_update_v2 → IMPORTANCE_DEFAULT（灵动岛）
└── BootReceiver.kt           → 开机自启
```

**流体云原理**（LLMonitor 验证）：
- 双通知通道 + `android.requestPromotedOngoing` extras
- Android 16+ `ProgressStyle` 显示电量进度
- 无需 OPPO 私有 SDK

---

**Inspired by ColorOS 16 金标充电 · LLMonitor**
