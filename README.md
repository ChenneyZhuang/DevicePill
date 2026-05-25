# DevicePill（设备岛）

ColorOS 16 手机硬件性能检测 + 流体云/锁屏岛实时监控。

**无需 root**，全部数据通过系统 API 读取。

## 功能

### 硬件信息检测
- **CPU**：型号、核心数、最高频率、当前频率、使用率、温度、调度器
- **GPU**：渲染器、厂商、OpenGL ES 版本、温度
- **内存**：总量、已用、可用、百分比
- **存储**：内部存储 + SD 卡容量
- **电池**：电量、电压、电流、功率、温度、健康、充电方式、容量
- **屏幕**：分辨率、刷新率、DPI、HDR 支持
- **系统**：设备型号、Android 版本、ColorOS 版本、内核
- **网络**：Wi-Fi SSID、信号强度、IP

### 流体云 & 锁屏岛（ColorOS 16）
启动监控后，通知自动出现在：
- **流体云胶囊**：充电时显示 `⚡ 8.0W · 92% · 32°C`
- **锁屏岛**：展开显示完整参数（功率/电压/电流/温度/容量）
- **常亮显示**：锁屏常驻

ColorOS 16 的流体云兼容 Android 16 Live Updates API，无需 OPPO 私有 SDK。

### 开机自启
停止前如果监控在运行，重启后自动恢复。

## 安装

### 方法一：直接装 APK

1. 从 [Releases](../../releases) 下载 `app-debug.apk`
2. 手机打开 APK → 允许「未知来源」安装
3. 启动 App → 授权**通知权限**（必须，否则流体云不显示）
4. 点「启动监控」即可

### 方法二：自己编译

```bash
# 需要 JDK 17 + Android SDK
git clone https://github.com/ChenneyZhuang/DevicePill.git
cd DevicePill
echo "sdk.dir=/path/to/Android/sdk" > local.properties
./gradlew assembleDebug
# APK 在 app/build/outputs/apk/debug/app-debug.apk
```

## 权限说明

| 权限 | 用途 |
|------|------|
| 通知 | 流体云/锁屏岛显示实时数据 |
| 开机自启 | 重启自动恢复监控 |
| 网络状态 | 读取 Wi-Fi SSID |

不需要 root，不需要 BATTERY_STATS，不需要悬浮窗。

## 技术架构

```
DevicePill/
├── info/                      # 8 个信息模块
│   ├── CpuInfo.kt            → /proc/cpuinfo + /proc/stat + /sys/class/thermal
│   ├── BatteryInfo.kt        → BatteryManager
│   ├── MemoryInfo.kt         → ActivityManager
│   ├── StorageInfo.kt        → StatFs
│   ├── GpuInfo.kt           → EGL14 + GLES20
│   ├── DisplayInfo.kt       → WindowManager
│   ├── SystemInfo.kt        → SystemProperties 反射
│   ├── NetworkInfo.kt       → ConnectivityManager
│   └── DeviceInfo.kt        → 聚合器
├── DeviceMonitorService.kt   → 前台通知（流体云/锁屏岛）
├── BootReceiver.kt           → 开机自启
└── MainActivity.kt           → Compose 仪表盘
```

## 设备支持

- **系统**：Android 12+（SDK 31+）
- **最佳体验**：ColorOS 16（OPPO Find X9 Ultra / X8 / Reno 系列）
- **其他**：任何 Android 12+ 设备均可运行，但流体云为 ColorOS 独占

---

**Inspired by ColorOS 16 Charging Island · 金标充电显示**
