package com.hermes.devicepill

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ChargingPillApp() }
    }
}

// ============================================================
// LLMonitor color palettes (extracted from decompiled APK)
// ============================================================
object LLColors {
    // DYNAMIC theme ring: cyan → orange → yellow → green
    val dynamicRing = listOf(
        Color(0xFF40C0F4), Color(0xFFE86535), Color(0xFFF9E804), Color(0xFF32D653)
    )
    // JIZI theme ring (dark): purple → violet → indigo
    val jiziRing = listOf(
        Color(0xFFE4BFFF), Color(0xFFCCA3FF), Color(0xFF8E57FF), Color(0xFF6346FF)
    )
    // OCEAN theme ring (dark): blue spectrum
    val oceanRing = listOf(
        Color(0xFF6BAFFF), Color(0xFF1B8FFF), Color(0xFF0070FF), Color(0xFF2F99F0)
    )
    // SUNSET theme ring (dark): orange/warm spectrum
    val sunsetRing = listOf(
        Color(0xFFFFB366), Color(0xFFFF8A47), Color(0xFFFF6B35), Color(0xFFFF4D1A)
    )

    // Background
    val bg = Color(0xFF0A0A0C)
    val surface = Color(0xFF141418)
    val surfaceBorder = Color(0xFF202028)
    val textPrimary = Color(0xFFEEEEF0)
    val textSecondary = Color(0xFF8A8A92)
    val textMuted = Color(0xFF5A5A64)
}

// ============================================================
// App
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChargingPillApp() {
    val context = LocalContext.current
    var themeIndex by remember { mutableIntStateOf(0) }
    val themes = listOf("动感", "极紫", "海洋", "日落")
    val ringColors = listOf(LLColors.dynamicRing, LLColors.jiziRing, LLColors.oceanRing, LLColors.sunsetRing)
    val currentRing = ringColors[themeIndex]

    // Battery state
    var batteryPct by remember { mutableIntStateOf(0) }
    var isCharging by remember { mutableStateOf(false) }
    var watts by remember { mutableDoubleStateOf(0.0) }
    var voltage by remember { mutableDoubleStateOf(0.0) }
    var currentMa by remember { mutableIntStateOf(0) }
    var tempC by remember { mutableFloatStateOf(0f) }
    var chargingType by remember { mutableStateOf("未充电") }
    var batteryHealth by remember { mutableStateOf("良好") }
    var batteryTech by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(DeviceMonitorService.isRunning(context)) }

    val hasNotif = if (Build.VERSION.SDK_INT >= 33)
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    else true

    // Battery receiver
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                batteryPct = if (scale > 0) level * 100 / scale else 0
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                isCharging = plugged != 0
                chargingType = when (plugged) {
                    BatteryManager.BATTERY_PLUGGED_AC -> "超级闪充"
                    BatteryManager.BATTERY_PLUGGED_USB -> "USB 充电"
                    BatteryManager.BATTERY_PLUGGED_WIRELESS -> "无线充电"
                    else -> "未充电"
                }
                val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
                batteryHealth = when (health) {
                    BatteryManager.BATTERY_HEALTH_GOOD -> "良好"
                    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "过热"
                    BatteryManager.BATTERY_HEALTH_DEAD -> "损坏"
                    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "过压"
                    BatteryManager.BATTERY_HEALTH_COLD -> "过冷"
                    else -> "正常"
                }
                batteryTech = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: ""
                val tempDeci = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                tempC = tempDeci / 10f
                val mv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
                voltage = mv / 1000.0
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                if (bm != null && isCharging) {
                    val raw = runCatching { bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) }.getOrDefault(Int.MIN_VALUE)
                    if (raw != Int.MIN_VALUE) {
                        var ma = raw
                        if (abs(ma) > 10000) ma /= 1000
                        currentMa = abs(ma)
                        watts = voltage * abs(ma) / 1000.0
                    }
                }
                if (!isCharging) { watts = 0.0; currentMa = 0 }
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }

    // Running state ticker
    DisposableEffect(Unit) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val ticker = object : Runnable { override fun run() { isRunning = DeviceMonitorService.isRunning(context); handler.postDelayed(this, 2000) } }
        handler.post(ticker)
        onDispose { handler.removeCallbacks(ticker) }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = LLColors.bg,
            surface = LLColors.surface,
            primary = currentRing[1],
            onBackground = LLColors.textPrimary,
            onSurface = LLColors.textPrimary,
        )
    ) {
        Scaffold(
            containerColor = LLColors.bg,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⚡", fontSize = 20.sp)
                            Spacer(Modifier.width(8.dp))
                            Text("充电·岛", fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = LLColors.bg)
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(8.dp))

                // ── Theme chips ──
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    themes.forEachIndexed { i, name ->
                        FilterChip(
                            selected = i == themeIndex,
                            onClick = { themeIndex = i },
                            label = { Text(name, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = currentRing[0].copy(alpha = 0.15f),
                                selectedLabelColor = currentRing[1]
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true, selected = i == themeIndex,
                                borderColor = if (i == themeIndex) currentRing[1].copy(alpha = 0.3f) else Color.Transparent,
                                selectedBorderColor = currentRing[1].copy(alpha = 0.3f)
                            )
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ═══ Active Ring ═══
                ActiveRing(
                    percentage = batteryPct,
                    isCharging = isCharging,
                    watts = watts,
                    ringColors = currentRing,
                    modifier = Modifier.size(200.dp)
                )

                Spacer(Modifier.height(12.dp))

                // ═══ Charging badge ═══
                AnimatedVisibility(isCharging) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ChargingBadge(chargingType, watts, currentRing[1])
                        Spacer(Modifier.height(18.dp))
                    }
                }

                // ═══ Stats Cards (LLMonitor exact specs: 78dp height, 16dp h-pad, 10dp v-pad) ═══
                StatsGrid(
                    voltage, currentMa, tempC, batteryHealth, batteryTech, batteryPct,
                    isCharging, currentRing[1]
                )

                Spacer(Modifier.height(20.dp))

                // ═══ Permission ═══
                if (!hasNotif && Build.VERSION.SDK_INT >= 33) {
                    NotifPermissionCard(context)
                    Spacer(Modifier.height(16.dp))
                }

                // ═══ Toggle ═══
                ToggleButton(isRunning, hasNotif, currentRing) {
                    if (isRunning) DeviceMonitorService.stop(context) else DeviceMonitorService.start(context)
                    isRunning = !isRunning
                }

                Spacer(Modifier.height(10.dp))

                // Status
                if (isRunning) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedDot()
                        Spacer(Modifier.width(6.dp))
                        Text("流体云运行中", fontSize = 12.sp, color = LLColors.textSecondary)
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ═══ Setup guide ═══
                SetupGuide(context, currentRing[1])

                Spacer(Modifier.height(12.dp))
                Text("DevicePill · v10.0", fontSize = 10.sp, color = LLColors.textMuted.copy(alpha = 0.4f))
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

// ============================================================
// Active Ring — LLMonitor's 4-color sweep gradient
// ============================================================
@Composable
fun ActiveRing(
    percentage: Int, isCharging: Boolean, watts: Double,
    ringColors: List<Color>, modifier: Modifier = Modifier
) {
    val animPct by animateFloatAsState(percentage / 100f, spring(dampingRatio = 0.55f, stiffness = 300f), label = "pct")
    val ringBrush = Brush.sweepGradient(ringColors)

    // Glow pulse
    val glowAlpha by rememberInfiniteTransition(label = "glow").animateFloat(
        0.04f, 0.12f, infiniteRepeatable(tween(1800, easing = EaseInOutSine), RepeatMode.Reverse), label = "g"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Radial glow behind ring
        if (isCharging) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    Brush.radialGradient(listOf(ringColors[0].copy(alpha = glowAlpha), Color.Transparent)),
                    radius = size.minDimension * 0.48f
                )
            }
        }

        // Track
        Canvas(Modifier.fillMaxSize()) {
            val sw = size.minDimension * 0.095f
            val r = (size.minDimension - sw) / 2f
            val tl = Offset(sw / 2f, sw / 2f)
            drawArc(Color(0xFF1C1C26), 135f, 270f, false, tl, Size(r * 2f, r * 2f), style = Stroke(sw, cap = StrokeCap.Round))
        }

        // Progress
        Canvas(Modifier.fillMaxSize()) {
            val sw = size.minDimension * 0.095f
            val r = (size.minDimension - sw) / 2f
            val tl = Offset(sw / 2f, sw / 2f)
            drawArc(ringBrush, 135f, 270f * animPct, false, tl, Size(r * 2f, r * 2f), style = Stroke(sw, cap = StrokeCap.Round))
        }

        // Center
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$percentage", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = LLColors.textPrimary, letterSpacing = (-1).sp)
            Text("%", fontSize = 14.sp, color = LLColors.textSecondary, modifier = Modifier.offset(y = (-2).dp))
            if (isCharging && watts >= 0.5) {
                Text("${"%.1f".format(watts)}W", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ringColors[1])
            }
        }
    }
}

// ============================================================
// Charging Badge
// ============================================================
@Composable
fun ChargingBadge(type: String, watts: Double, accent: Color) {
    Surface(shape = RoundedCornerShape(18.dp), color = accent.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.15f))) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("⚡", fontSize = 13.sp)
            Spacer(Modifier.width(6.dp))
            Text(type, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = accent)
            if (watts >= 0.5) {
                Text(" · ", color = accent.copy(alpha = 0.4f))
                Text("${"%.1f".format(watts)}W", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = accent)
            }
        }
    }
}

// ============================================================
// Stats Grid — LLMonitor exact specs: 78dp height, 16dp H, 10dp V padding
// ============================================================
data class StatCardData(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String, val value: String, val warn: Boolean = false)

@Composable
fun StatsGrid(
    voltage: Double, current: Int, temp: Float,
    health: String, tech: String, pct: Int,
    isCharging: Boolean, accent: Color
) {
    val items = listOf(
        StatCardData(Icons.Outlined.Bolt, "电压", if (voltage > 0) "${"%.2f".format(voltage)}V" else "--"),
        StatCardData(Icons.Outlined.Speed, "电流", if (current > 0) "${current}mA" else "--"),
        StatCardData(Icons.Filled.DeviceThermostat, "温度", "${"%.1f".format(temp)}℃", temp > 40),
        StatCardData(Icons.Outlined.FavoriteBorder, "健康", health),
        StatCardData(Icons.Outlined.Memory, "技术", tech.ifEmpty { "Li-ion" }),
        StatCardData(Icons.Outlined.BatteryChargingFull, "状态", if (isCharging) "充电中" else "未充电"),
    )

    // LLMonitor: 78dp fixed card height • 16dp horizontal • 10dp vertical
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { data ->
                    StatCard(data, isCharging, accent, Modifier.weight(1f))
                }
                // If odd number, fill with empty space
                if (row.size < 2) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun StatCard(data: StatCardData, isCharging: Boolean, accent: Color, modifier: Modifier = Modifier) {
    val tint = when {
        data.warn -> Color(0xFFEF4444)
        isCharging -> accent
        else -> LLColors.textSecondary
    }
    Card(
        modifier = modifier.height(78.dp),  // LLMonitor exact: 77.7dp → 78dp
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = LLColors.surface),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, LLColors.surfaceBorder)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),  // LLMonitor exact
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(data.icon, null, tint = tint.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
            Spacer(Modifier.height(2.dp))  // LLMonitor: 2dp title-value gap
            Text(data.value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LLColors.textPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(data.label, fontSize = 10.sp, color = LLColors.textSecondary, modifier = Modifier.padding(top = 1.dp))
        }
    }
}

// ============================================================
// Permission Card
// ============================================================
@Composable
fun NotifPermissionCard(context: Context) {
    val act = context as? ComponentActivity
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.06f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.2f))
    ) {
        Row(Modifier.fillMaxWidth().clickable { act?.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1) }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.NotificationsOff, null, tint = Color(0xFFEF4444).copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("需要通知权限", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LLColors.textPrimary)
                Text("点击授权 · 否则流体云无法显示", fontSize = 11.sp, color = LLColors.textSecondary)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = Color(0xFFEF4444).copy(alpha = 0.3f))
        }
    }
}

// ============================================================
// Toggle Button
// ============================================================
@Composable
fun ToggleButton(isRunning: Boolean, hasNotif: Boolean, ringColors: List<Color>, onToggle: () -> Unit) {
    val brush = if (isRunning)
        Brush.horizontalGradient(listOf(Color(0xFFDC2626), Color(0xFFB91C1C)))
    else
        Brush.horizontalGradient(ringColors)

    Button(onClick = onToggle,
        enabled = hasNotif || Build.VERSION.SDK_INT < 33,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(Modifier.fillMaxSize().background(brush, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isRunning) "停止监控" else "启动流体云", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

// ============================================================
// Animated Dot
// ============================================================
@Composable
fun AnimatedDot() {
    val alpha by rememberInfiniteTransition().animateFloat(0.5f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "ad")
    Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF22C55E).copy(alpha = alpha)))
}

// ============================================================
// Setup Guide
// ============================================================
@Composable
fun SetupGuide(context: Context, accent: Color) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = LLColors.surface.copy(alpha = 0.5f)),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, LLColors.surfaceBorder)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("设置指南", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LLColors.textPrimary)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    })
                }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)) {
                    Text("系统设置", fontSize = 12.sp, color = accent)
                    Icon(Icons.Filled.OpenInNew, null, tint = accent, modifier = Modifier.size(11.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            GuideStep("1", "授权通知权限", "App 内红色卡片点击授权", accent)
            GuideStep("2", "开启流体云", "系统设置 → 通知 → 流体云 → DevicePill", accent)
            GuideStep("3", "允许锁屏显示", "系统设置 → 通知 → DevicePill → 锁屏", accent)
            GuideStep("4", "启动并充电", "点击启动 → 插充电器 → 金标胶囊出现", accent)
        }
    }
}

@Composable
fun GuideStep(num: String, title: String, desc: String, accent: Color) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Text(num, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = accent)
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = LLColors.textPrimary)
            Text(desc, fontSize = 10.sp, color = LLColors.textMuted, modifier = Modifier.padding(top = 1.dp))
        }
    }
}
