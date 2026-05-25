package com.hermes.devicepill

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Paint
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
// Theme — matching LLMonitor's dark Material 3 aesthetic
// ============================================================
private val DeepBackground = Color(0xFF0A0A0C)
private val SurfaceCard = Color(0xFF141418)
private val SurfaceCardBorder = Color(0xFF222228)
private val TextPrimary = Color(0xFFEEEEF0)
private val TextSecondary = Color(0xFF8E8E96)
private val TextMuted = Color(0xFF5C5C64)

// Active Ring palette — JIZI (极紫) inspired
private val RingStart = Color(0xFFA855F7)  // purple
private val RingMid = Color(0xFF6366F1)    // indigo
private val RingEnd = Color(0xFF06B6D4)    // cyan
private val GlowColor = Color(0xFFA855F7)

@Composable
fun ChargingPillApp() {
    val context = LocalContext.current

    // Battery state
    var batteryPct by remember { mutableIntStateOf(0) }
    var isCharging by remember { mutableStateOf(false) }
    var watts by remember { mutableDoubleStateOf(0.0) }
    var voltage by remember { mutableDoubleStateOf(0.0) }
    var currentMa by remember { mutableIntStateOf(0) }
    var tempC by remember { mutableFloatStateOf(0f) }
    var chargingType by remember { mutableStateOf("未充电") }
    var batteryHealth by remember { mutableStateOf("正常") }
    var batteryTech by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(DeviceMonitorService.isRunning(context)) }

    val hasNotifPermission = if (Build.VERSION.SDK_INT >= 33)
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    else true

    // History for simple power curve
    val powerHistory = remember { mutableStateListOf<Float>() }
    val maxHistoryPoints = 30

    // Battery data receiver
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
                    val raw = runCatching {
                        bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                    }.getOrDefault(Int.MIN_VALUE)
                    if (raw != Int.MIN_VALUE) {
                        var ma = raw
                        if (abs(ma) > 10000) ma /= 1000
                        currentMa = abs(ma)
                        watts = voltage * abs(ma) / 1000.0
                    }
                }
                if (!isCharging) {
                    watts = 0.0; currentMa = 0
                }

                // Add to power history
                if (powerHistory.size >= maxHistoryPoints) powerHistory.removeFirst()
                powerHistory.add(watts.toFloat())
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }

    // Periodic refresh for running state
    DisposableEffect(Unit) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val ticker = object : Runnable {
            override fun run() {
                isRunning = DeviceMonitorService.isRunning(context)
                handler.postDelayed(this, 2000)
            }
        }
        handler.post(ticker)
        onDispose { handler.removeCallbacks(ticker) }
    }

    // Gradient backgrounds
    val pageGradient = Brush.verticalGradient(
        listOf(DeepBackground, Color(0xFF0C0C10), Color(0xFF0E0E14))
    )

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = DeepBackground,
            surface = SurfaceCard,
            primary = RingStart,
            onBackground = TextPrimary,
            onSurface = TextPrimary,
        )
    ) {
        Scaffold(containerColor = Color.Transparent) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(pageGradient)
                    .padding(padding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(40.dp))

                    // ═══ Header ═══
                    Text(
                        "充电·岛",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = 2.sp
                    )
                    Text(
                        "ColorOS 流体云 · 金标充电",
                        fontSize = 12.sp,
                        color = RingStart.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // ═══ Active Ring ═══
                    ActiveRing(
                        percentage = batteryPct,
                        isCharging = isCharging,
                        watts = watts,
                        tempC = tempC,
                        modifier = Modifier.size(210.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // ═══ Charging badge ═══
                    if (isCharging) {
                        ChargingTypeBadge(chargingType, watts)
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // ═══ Stats Grid ═══
                    StatCardGrid(
                        voltage = voltage,
                        current = currentMa,
                        temp = tempC,
                        health = batteryHealth,
                        tech = batteryTech,
                        pct = batteryPct,
                        isCharging = isCharging
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // ═══ Power Curve ═══
                    if (powerHistory.size >= 2) {
                        PowerCurveCard(
                            history = powerHistory.toList(),
                            currentWatts = watts,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // ═══ Permission warning ═══
                    if (!hasNotifPermission && Build.VERSION.SDK_INT >= 33) {
                        NotifPermissionCard(context)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // ═══ Toggle ═══
                    GradientToggleButton(
                        isRunning = isRunning,
                        enabled = hasNotifPermission || Build.VERSION.SDK_INT < 33,
                        onToggle = {
                            if (isRunning) DeviceMonitorService.stop(context)
                            else DeviceMonitorService.start(context)
                            isRunning = !isRunning
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // ═══ Status ═══
                    if (isRunning) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AnimatedDot()
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("流体云运行中", fontSize = 13.sp, color = TextSecondary)
                        }
                    } else {
                        Text("点击启动后插上充电器", fontSize = 13.sp, color = TextMuted)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // ═══ Setup Guide ═══
                    SetupGuideCompact(context)

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("DevicePill v9.0", fontSize = 11.sp, color = TextMuted.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

// ============================================================
// Active Ring — the centerpiece battery gauge
// ============================================================
@Composable
fun ActiveRing(
    percentage: Int,
    isCharging: Boolean,
    watts: Double,
    tempC: Float,
    modifier: Modifier = Modifier
) {
    val animProgress by animateFloatAsState(
        targetValue = percentage / 100f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 200f),
        label = "ring"
    )
    val pulseScale by rememberInfiniteTransition(label = "pulseS").animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1500, easing = EaseInOutCubic), RepeatMode.Reverse),
        label = "ps"
    )
    val glowAlpha by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.07f, targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(2000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "ga"
    )

    val ringBrush = Brush.sweepGradient(
        listOf(RingStart, RingMid, RingEnd, RingMid, RingStart),
        center = Offset(0.5f, 0.5f)
    )
    val bgColor = Color(0xFF1C1C24)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Glow behind the ring
        if (isCharging) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            GlowColor.copy(alpha = glowAlpha),
                            GlowColor.copy(alpha = 0.0f)
                        )
                    ),
                    radius = size.minDimension * 0.52f
                )
            }
        }

        // Pulsing outer ring (charging only)
        if (isCharging) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val c = size.width / 2f
                val r = c * pulseScale
                drawCircle(
                    color = RingStart.copy(alpha = 0.04f),
                    radius = r
                )
            }
        }

        // Background track
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeW = size.minDimension * 0.09f
            val pad = strokeW / 2f
            val radius = (size.minDimension - strokeW) / 2f
            val topLeft = Offset(pad, pad)
            val arcSize = Size(radius * 2f, radius * 2f)
            drawArc(bgColor, 135f, 270f, false, topLeft, arcSize,
                style = Stroke(strokeW, cap = StrokeCap.Round))
        }

        // Progress arc
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeW = size.minDimension * 0.09f
            val pad = strokeW / 2f
            val radius = (size.minDimension - strokeW) / 2f
            drawArc(
                brush = ringBrush, 135f, 270f * animProgress, false,
                Offset(pad, pad), Size(radius * 2f, radius * 2f),
                style = Stroke(strokeW, cap = StrokeCap.Round)
            )
        }

        // Center text
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$percentage",
                fontSize = 46.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = (-1).sp
            )
            Text("%", fontSize = 15.sp, color = TextSecondary,
                modifier = Modifier.offset(y = (-3).dp))
            if (isCharging && watts >= 0.5) {
                Text(
                    "${"%.1f".format(watts)} W",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = RingMid
                )
            } else if (isCharging) {
                Text("⚡", fontSize = 14.sp, color = RingStart.copy(alpha = 0.6f))
            }
        }
    }
}

// ============================================================
// Charging Type Badge
// ============================================================
@Composable
fun ChargingTypeBadge(type: String, watts: Double) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = RingStart.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, RingStart.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⚡", fontSize = 13.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(type, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = RingStart)
            if (watts >= 0.5) {
                Text(" · ", color = RingStart.copy(alpha = 0.4f))
                Text("${"%.1f".format(watts)}W", fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, color = RingMid)
            }
        }
    }
}

// ============================================================
// Stats Grid — LLMonitor-style info cards
// ============================================================
@Composable
fun StatCardGrid(
    voltage: Double, current: Int, temp: Float,
    health: String, tech: String, pct: Int,
    isCharging: Boolean
) {
    // 2-column staggered layout for visual interest
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        userScrollEnabled = false
    ) {
        // Row 1: Voltage (full width), Current (half)
        item { StatTile("电压", if (voltage > 0) "${"%.2f".format(voltage)}V" else "--", Icons.Outlined.Bolt, isCharging) }
        item { StatTile("电流", if (current > 0) "${current}mA" else "--", Icons.Outlined.Speed, isCharging) }

        // Row 2: Temperature, Battery Health
        item { StatTile("温度", "${"%.1f".format(temp)}℃", Icons.Outlined.Thermostat, isCharging, warnIf = temp > 40) }
        item { StatTile("健康", health, Icons.Outlined.FavoriteBorder, isCharging) }

        // Row 3: Battery Tech, Capacity
        item {
            if (tech.isNotEmpty()) StatTile("技术", tech, Icons.Outlined.Memory, isCharging)
            else StatTile("容量", if (pct > 0) "$pct%" else "--", Icons.Outlined.BatteryFull, isCharging)
        }
        item { StatTile("状态", if (isCharging) "充电中" else "未充电", Icons.Outlined.Power, isCharging) }
    }
}

@Composable
fun StatTile(
    label: String, value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isCharging: Boolean,
    warnIf: Boolean = false
) {
    val accent = when {
        warnIf -> Color(0xFFEF4444)
        isCharging -> RingMid
        else -> TextSecondary
    }
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, SurfaceCardBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = accent.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(label, fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

// ============================================================
// Power Curve Card
// ============================================================
@Composable
fun PowerCurveCard(history: List<Float>, currentWatts: Double, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, SurfaceCardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ShowChart, null, tint = RingMid.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("充电功率", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                Spacer(modifier = Modifier.weight(1f))
                Text("${"%.1f".format(currentWatts)}W", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RingMid)
            }
            Spacer(modifier = Modifier.height(10.dp))
            // Simple bar-style curve
            val maxVal = history.maxOrNull()?.coerceAtLeast(1f) ?: 1f
            val barColor = Brush.verticalGradient(listOf(RingStart, RingMid))
            Row(
                modifier = Modifier.fillMaxWidth().height(52.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                for (v in history) {
                    val heightFrac = (v / maxVal).coerceIn(0.02f, 1f)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(heightFrac)
                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            .background(barColor)
                    )
                }
            }
        }
    }
}

// ============================================================
// Notification Permission Card
// ============================================================
@Composable
fun NotifPermissionCard(context: Context) {
    val activity = context as? ComponentActivity
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.06f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { activity?.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1) }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.NotificationsOff, null, tint = Color(0xFFEF4444).copy(alpha = 0.5f), modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("需要通知权限", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text("点击授权 · 流体云/锁屏岛才能显示", fontSize = 11.sp, color = TextSecondary)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = Color(0xFFEF4444).copy(alpha = 0.3f))
        }
    }
}

// ============================================================
// Gradient Toggle Button
// ============================================================
@Composable
fun GradientToggleButton(isRunning: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val brush = if (isRunning)
        Brush.horizontalGradient(listOf(Color(0xFFDC2626), Color(0xFFB91C1C)))
    else
        Brush.horizontalGradient(listOf(RingStart, RingMid, RingEnd))

    Button(
        onClick = onToggle, enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(brush, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    null, tint = Color.White, modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (isRunning) "停止监控" else "启动流体云",
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White
                )
            }
        }
    }
}

// ============================================================
// Animated Dot
// ============================================================
@Composable
fun AnimatedDot() {
    val alpha by rememberInfiniteTransition(label = "dot").animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "da"
    )
    Box(
        modifier = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(Color(0xFF22C55E).copy(alpha = alpha))
    )
}

// ============================================================
// Setup Guide — compact version
// ============================================================
@Composable
fun SetupGuideCompact(context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard.copy(alpha = 0.5f)),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, SurfaceCardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lightbulb, null, tint = RingStart, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("设置指南", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        })
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("通知设置", fontSize = 12.sp, color = RingMid)
                    Icon(Icons.Filled.OpenInNew, null, tint = RingMid, modifier = Modifier.size(12.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Steps
            GuideStep("1", "授权通知权限", "首次打开会弹窗，或点击上方红色卡片")
            GuideStep("2", "开启流体云", "设置 → 通知与状态栏 → 流体云 → DevicePill")
            GuideStep("3", "允许锁屏显示", "设置 → 通知 → DevicePill → 锁屏通知")
            GuideStep("4", "启动并充电", "点击启动 → 插充电器 → 摄像头旁出现金标胶囊")
        }
    }
}

@Composable
fun GuideStep(num: String, title: String, desc: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.size(20.dp).clip(CircleShape).background(RingStart.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(num, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RingStart)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Text(desc, fontSize = 10.sp, color = TextMuted, modifier = Modifier.padding(top = 1.dp))
        }
    }
}
