package com.hermes.devicepill

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.hermes.devicepill.info.*
import kotlinx.coroutines.delay

private val Accent = Color(0xFFFF6B35)
private val AccentDim = Color(0xFFFF6B35).copy(alpha = 0.15f)
private val Bg = Color(0xFF0D1117)
private val Surface = Color(0xFF161B22)
private val Border = Color(0xFF30363D)
private val TextPri = Color(0xFFE6EDF3)
private val TextSec = Color(0xFF8B949E)
private val Green = Color(0xFF3FB950)
private val Yellow = Color(0xFFD29922)
private val Red = Color(0xFFF85149)

class MainActivity : ComponentActivity() {
    private val notifLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val hasOverlay = Settings.canDrawOverlays(this)
            val hasNotif = if (Build.VERSION.SDK_INT >= 33)
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            else true
            var isRunning by remember { mutableStateOf(DeviceMonitorService.isRunning(this)) }

            MaterialTheme(colorScheme = darkColorScheme(
                primary = Accent, background = Bg, surface = Surface,
                onPrimary = Color.White, onBackground = TextPri, onSurface = TextPri
            )) {
                DashboardScreen(
                    hasOverlayPerm = hasOverlay,
                    hasNotifPerm = hasNotif,
                    isServiceRunning = isRunning,
                    onRequestOverlay = {
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")))
                    },
                    onRequestNotif = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    onToggleService = {
                        if (isRunning) DeviceMonitorService.stop(this) else DeviceMonitorService.start(this)
                        isRunning = !isRunning
                    },
                    context = this
                )
            }
        }
    }
}

@Composable
fun DashboardScreen(
    hasOverlayPerm: Boolean,
    hasNotifPerm: Boolean,
    isServiceRunning: Boolean,
    onRequestOverlay: () -> Unit,
    onRequestNotif: () -> Unit,
    onToggleService: () -> Unit,
    context: android.content.Context
) {
    // Live snapshot (updates every 2s when service is running)
    var snapshot by remember { mutableStateOf(DeviceInfo.snapshot(context)) }
    LaunchedEffect(isServiceRunning) {
        if (isServiceRunning) {
            while (true) {
                delay(2000)
                snapshot = DeviceInfo.snapshot(context)
            }
        }
    }

    Scaffold(containerColor = Bg) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            // -= Header =-
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(AccentDim), contentAlignment = Alignment.Center) {
                    Text("⚡", fontSize = 22.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("金标充电岛", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPri)
                    Text("Charging Island · 悬浮窗充电显示", fontSize = 11.sp, color = TextSec)
                }
            }

            Spacer(Modifier.height(16.dp))

            // -= Permission / Service Card =-
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    // Overlay permission — most important!
                    if (!hasOverlayPerm) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = Red, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("需要「显示在其他应用上层」权限", fontSize = 14.sp, color = TextPri, modifier = Modifier.weight(1f))
                            TextButton(onClick = onRequestOverlay) { Text("去开启", color = Accent) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (!hasNotifPerm) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = Yellow, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("需要通知权限（后台运行用）", fontSize = 14.sp, color = TextPri, modifier = Modifier.weight(1f))
                            TextButton(onClick = onRequestNotif) { Text("授予", color = Accent) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val bgColor = if (isServiceRunning) Red.copy(alpha = 0.8f) else Accent
                        val enabled = hasOverlayPerm && hasNotifPerm
                        Button(
                            onClick = onToggleService,
                            enabled = enabled,
                            colors = ButtonDefaults.buttonColors(containerColor = bgColor, disabledContainerColor = bgColor.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(if (isServiceRunning) Icons.Default.Stop else Icons.Default.PlayArrow, null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text(if (isServiceRunning) "停止" else "启动", color = Color.White)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (isServiceRunning) "● 悬浮窗运行中" else "○ 已停止",
                            fontSize = 13.sp, color = if (isServiceRunning) Green else TextSec
                        )
                    }
                    if (!isServiceRunning && hasOverlayPerm && hasNotifPerm) {
                        Spacer(Modifier.height(4.dp))
                        Text("启动后在摄像头旁显示金标充电信息", fontSize = 11.sp, color = TextSec)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // -= Info Cards =-
            InfoCard("处理器", Icons.Default.Memory, color = Accent) {
                val cpu = snapshot.cpu
                InfoRow("型号", cpu.model)
                InfoRow("架构", cpu.architecture)
                InfoRow("核心数", "${cpu.cores} 核")
                if (cpu.maxFrequencyMHz > 0) InfoRow("最高频率", "${"%.0f".format(cpu.maxFrequencyMHz)} MHz")
                if (cpu.currentFrequencyMHz > 0) InfoRow("当前频率", "${"%.0f".format(cpu.currentFrequencyMHz)} MHz")
                InfoRow("使用率", "${cpu.usagePercent}%")
                if (cpu.temperatureC > 0) InfoRow("温度", "${"%.1f".format(cpu.temperatureC)}°C")
                if (cpu.governor.isNotEmpty()) InfoRow("调度器", cpu.governor)
            }

            InfoCard("内存", Icons.Default.Storage, color = Color(0xFF58A6FF)) {
                val mem = snapshot.memory
                InfoRow("总量", "${"%.1f".format(mem.totalMb / 1024.0)} GB")
                InfoRow("已用", "${"%.1f".format(mem.usedMb / 1024.0)} GB (${mem.usagePercent}%)")
                InfoRow("可用", "${"%.1f".format(mem.availableMb / 1024.0)} GB")
                if (mem.isLowMemory) InfoRow("状态", "⚠️ 内存不足")
            }

            InfoCard("存储", Icons.Default.SdCard, color = Color(0xFF7B61FF)) {
                val internal = snapshot.storage.internal
                InfoRow(internal.label, "${"%.1f".format(internal.totalMb / 1024.0)} GB")
                InfoRow("已用", "${"%.1f".format(internal.usedMb / 1024.0)} GB (${internal.usagePercent}%)")
                InfoRow("可用", "${"%.1f".format(internal.availableMb / 1024.0)} GB")
                snapshot.storage.external?.let { ext ->
                    InfoRow(ext.label, "${"%.1f".format(ext.totalMb / 1024.0)} GB")
                    InfoRow("SD已用", "${"%.1f".format(ext.usedMb / 1024.0)} GB (${ext.usagePercent}%)")
                }
            }

            InfoCard("电池", Icons.Default.BatteryChargingFull, color = Green) {
                val bat = snapshot.battery
                InfoRow("电量", "${bat.levelPercent}%")
                InfoRow("状态", bat.status)
                InfoRow("电压", "${"%.2f".format(bat.voltageV)} V")
                if (bat.powerW > 0) InfoRow("功率", "${"%.1f".format(bat.powerW)} W")
                if (bat.currentMa != 0) InfoRow("电流", "${bat.currentMa} mA")
                InfoRow("温度", "${"%.1f".format(bat.temperatureC)}°C")
                InfoRow("健康", bat.health)
                if (bat.isCharging) InfoRow("充电方式", bat.chargeType)
                if (bat.capacityTotalMah > 0) {
                    InfoRow("容量", "${bat.capacityRemainingMah} / ${bat.capacityTotalMah} mAh")
                }
            }

            InfoCard("GPU", Icons.Default.GraphicEq, color = Color(0xFFDB61A2)) {
                val gpu = snapshot.gpu
                InfoRow("渲染器", gpu.renderer)
                if (gpu.vendor != "未知") InfoRow("厂商", gpu.vendor)
                InfoRow("OpenGL", gpu.version)
                if (gpu.temperatureC > 0) InfoRow("温度", "${"%.1f".format(gpu.temperatureC)}°C")
            }

            InfoCard("屏幕", Icons.Default.Smartphone, color = Color(0xFFF0C000)) {
                val dsp = snapshot.display
                InfoRow("分辨率", "${dsp.widthPx} × ${dsp.heightPx}")
                InfoRow("密度", "${dsp.densityDpi} dpi (${"%.1f".format(dsp.density)}x)")
                InfoRow("刷新率", "${"%.1f".format(dsp.refreshRateHz)} Hz")
                if (dsp.brightness >= 0) InfoRow("亮度", "${dsp.brightness}/255")
                if (dsp.hdrCapabilities.isNotEmpty()) InfoRow("HDR", dsp.hdrCapabilities)
            }

            InfoCard("系统", Icons.Default.Info, color = TextSec) {
                val sys = snapshot.system
                InfoRow("设备", "${sys.manufacturer} ${sys.model}")
                InfoRow("代号", sys.device)
                InfoRow("系统", "${sys.androidVersion} (SDK ${sys.sdkVersion})")
                if (sys.osName != "Android" && sys.osVersion.isNotEmpty()) {
                    InfoRow("皮肤", "${sys.osName} ${sys.osVersion}")
                }
                InfoRow("安全补丁", sys.securityPatch)
                InfoRow("内核", sys.kernelVersion)
                InfoRow("Build", sys.buildId)
            }

            InfoCard("网络", Icons.Default.Wifi, color = Color(0xFF58A6FF)) {
                val net = snapshot.network
                InfoRow("状态", if (net.isConnected) "已连接" else "未连接")
                if (net.isConnected) {
                    InfoRow("类型", net.type)
                    if (net.wifiSsid.isNotEmpty()) InfoRow("Wi-Fi", net.wifiSsid)
                    if (net.signalStrength >= 0) InfoRow("信号", "${net.signalStrength}%")
                    if (net.ipAddress.isNotEmpty()) InfoRow("IP", net.ipAddress)
                }
            }

            Spacer(Modifier.height(32.dp))
            Text("Charging Island v4.0 · 悬浮窗充电显示", fontSize = 11.sp, color = TextSec.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally))
        }
    }
}

@Composable
fun InfoCard(title: String, icon: ImageVector, color: Color, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Border)
    ) {
        Column {
            Row(Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPri)
            }
            Column(Modifier.padding(16.dp, 8.dp, 16.dp, 16.dp)) { content() }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = TextSec)
        Text(value, fontSize = 13.sp, color = TextPri, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 16.dp).weight(1f, fill = false))
    }
}
