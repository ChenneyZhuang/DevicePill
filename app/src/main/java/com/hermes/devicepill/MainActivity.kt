package com.hermes.devicepill

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.*
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.hermes.devicepill.BuildConfig
import kotlin.math.*

class MainActivity : ComponentActivity() {
    private var hasNotificationPermission = mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission.value = granted
    }

    fun launchPermissionRequest() {
        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasNotificationPermission.value = if (Build.VERSION.SDK_INT >= 33)
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            else true
        setContent {
            val activity = this
            FluidPillApp(
                hasN = hasNotificationPermission.value,
                requestPermission = { activity.launchPermissionRequest() }
            )
        }
    }
}

// ============================================================
// LLMonitor-inspired palettes
// ============================================================
object LL {
    val dynamic = listOf(Color(0xFF40C0F4), Color(0xFFE86535), Color(0xFFF9E804), Color(0xFF32D653))
    val jizi = listOf(Color(0xFFE4BFFF), Color(0xFFCCA3FF), Color(0xFF8E57FF), Color(0xFF6346FF))
    val ocean = listOf(Color(0xFF6BAFFF), Color(0xFF1B8FFF), Color(0xFF0070FF), Color(0xFF2F99F0))
    val sunset = listOf(Color(0xFFFFB366), Color(0xFFFF8A47), Color(0xFFFF6B35), Color(0xFFFF4D1A))
    val bg = Color(0xFF0A0A0C); val surface = Color(0xFF141418)
    val border = Color(0xFF202028); val t1 = Color(0xFFEEEEF0)
    val t2 = Color(0xFF8A8A92); val t3 = Color(0xFF5A5A64)
}

data class BatteryData(
    val pct: Int, val charging: Boolean, val watts: Float, val voltage: Double,
    val currentMa: Int, val tempC: Float, val chargeType: String, val health: String, val tech: String
)

fun readBattery(ctx: Context): BatteryData {
    val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
    val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val pct = if (scale > 0) level * 100 / scale else 0
    val plugged = i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
    val chg = plugged != 0
    val ct = when (plugged) {
        BatteryManager.BATTERY_PLUGGED_AC -> "⚡超级闪充"; BatteryManager.BATTERY_PLUGGED_USB -> "🔌USB充电"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "🛜无线充电"; else -> if (chg) "充电中" else "未充电"
    }
    val hl = when (i?.getIntExtra(BatteryManager.EXTRA_HEALTH, 1) ?: 1) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "良好"; BatteryManager.BATTERY_HEALTH_OVERHEAT -> "过热"
        BatteryManager.BATTERY_HEALTH_DEAD -> "损坏"; BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "过压"
        BatteryManager.BATTERY_HEALTH_COLD -> "过冷"; else -> "正常"
    }
    val tech = i?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: ""
    val tc = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
    val mv = i?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0; val v = mv / 1000.0
    var w = 0f; var ma = 0
    val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    if (bm != null) {
        val raw = runCatching { bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) }.getOrDefault(Int.MIN_VALUE)
        if (raw != Int.MIN_VALUE) { var m = raw; if (abs(m) > 10000) m /= 1000; m = if (chg) abs(m) else -abs(m); ma = m; w = (v * m / 1000.0).toFloat() }
    }
    return BatteryData(pct, chg, w, v, ma, tc, ct, hl, tech)
}

// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FluidPillApp(hasN: Boolean, requestPermission: () -> Unit = {}) {
    val ctx = LocalContext.current
    var ti by remember { mutableIntStateOf(0) }
    val tn = listOf("动感", "极紫", "海洋", "日落")
    val rings = listOf(LL.dynamic, LL.jizi, LL.ocean, LL.sunset)
    val ring = rings[ti]

    var pct by remember { mutableIntStateOf(0) }; var chg by remember { mutableStateOf(false) }
    var watts by remember { mutableFloatStateOf(0f) }; var v by remember { mutableDoubleStateOf(0.0) }
    var ma by remember { mutableIntStateOf(0) }; var tc by remember { mutableFloatStateOf(0f) }
    var ct by remember { mutableStateOf("未充电") }; var hl by remember { mutableStateOf("良好") }
    var tech by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(DeviceMonitorService.isRunning()) }
    var skipTicker by remember { mutableStateOf(false) }
    var autoStarted by remember { mutableStateOf(false) }

    // Curve history (60 points, 3s each = 3 min window)
    val powerH = remember { mutableStateListOf<Float>() }
    val tempH = remember { mutableStateListOf<Float>() }
    val voltH = remember { mutableStateListOf<Float>() }

    // Merged battery read: BroadcastReceiver triggers both UI update AND curve collection
    DisposableEffect(ctx) {
        val rcv = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val d = readBattery(ctx)
                // Update UI cards
                pct = d.pct; chg = d.charging; watts = d.watts
                v = d.voltage; ma = d.currentMa; tc = d.tempC; ct = d.chargeType
                hl = d.health; tech = d.tech
                // Update curves (same source, no extra readBattery call)
                if (powerH.size >= 60) powerH.removeFirst(); powerH.add(d.watts)
                if (tempH.size >= 60) tempH.removeFirst(); tempH.add(d.tempC)
                if (voltH.size >= 60) voltH.removeFirst(); voltH.add(d.voltage.toFloat())
            }
        }
        ctx.registerReceiver(rcv, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { ctx.unregisterReceiver(rcv) }
    }

    DisposableEffect(Unit) {
        val h = Handler(Looper.getMainLooper())
        val tick = object : Runnable { override fun run() {
            if (skipTicker) { skipTicker = false; h.postDelayed(this, 2000); return }
            running = DeviceMonitorService.isRunning(); h.postDelayed(this, 2000)
        } }
        h.post(tick); onDispose { h.removeCallbacks(tick) }
    }

    MaterialTheme(colorScheme = darkColorScheme(background = LL.bg, surface = LL.surface, primary = ring[1])) {
        Scaffold(containerColor = LL.bg,
            topBar = { TopAppBar(title = { Row { Text("⚡", fontSize = 20.sp); Spacer(Modifier.width(8.dp)); Text("充电·岛", fontWeight = FontWeight.Bold) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = LL.bg)) }
        ) { pad ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pad).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(4.dp))
                // Theme chips
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tn.forEachIndexed { i, n -> val sel = i == ti; val col = if (sel) ring[1] else LL.t2
                        Surface(onClick = { ti = i }, shape = RoundedCornerShape(20.dp), color = if (sel) ring[0].copy(alpha = 0.12f) else Color.Transparent, border = if (sel) androidx.compose.foundation.BorderStroke(1.dp, ring[1].copy(alpha = 0.25f)) else androidx.compose.foundation.BorderStroke(1.dp, Color.Transparent)) { Text(n, Modifier.padding(horizontal = 14.dp, vertical = 7.dp), fontSize = 12.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, color = col) } }
                }
                Spacer(Modifier.height(20.dp))

                // Active Ring
                ActiveRing(pct, chg, watts, ring, Modifier.size(200.dp))
                Spacer(Modifier.height(10.dp))
                AnimatedVisibility(chg) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Badge(ct, watts, ring[1]); Spacer(Modifier.height(14.dp)) } }

                if (!hasN && Build.VERSION.SDK_INT >= 33) { NotifCard(ctx, requestPermission); Spacer(Modifier.height(14.dp)) }

                // Auto-start service when permission is granted
                LaunchedEffect(hasN) {
                    if (hasN && !autoStarted && Build.VERSION.SDK_INT >= 33) {
                        autoStarted = true; skipTicker = true; DeviceMonitorService.start(ctx); running = true
                    }
                }
                DualCard("功率", if (watts != 0f) "${"%.1f".format(watts)}W" else "--W", "电流",
                    if (ma != 0) { val a = abs(ma); if (a >= 1000) "${"%.1f".format(a/1000f)}A" else "${a}mA" } else "--", Icons.Outlined.Bolt, Icons.Outlined.Speed, chg, ring[1])
                Spacer(Modifier.height(10.dp))

                DualCard("电压", if (v > 0) "${"%.2f".format(v)}V" else "--V", "温度", "${"%.1f".format(tc)}℃", Icons.Filled.BatteryChargingFull, Icons.Filled.DeviceThermostat, chg, ring[1], (tc > 40))
                Spacer(Modifier.height(10.dp))

                DualCard("供电", ct, "健康", hl, Icons.Outlined.Power, Icons.Outlined.FavoriteBorder, chg, ring[1])
                Spacer(Modifier.height(14.dp))

                if (powerH.size >= 2) { CurveCard("功率", powerH, ring, "W", watts) { Spacer(Modifier.height(12.dp)) } }
                if (tempH.size >= 2) { CurveCard("温度", tempH, listOf(Color(0xFF22C55E), Color(0xFFEAB308), Color(0xFFEF4444)), "℃", tc) { Spacer(Modifier.height(12.dp)) } }
                if (voltH.size >= 2) { CurveCard("电压", voltH, listOf(Color(0xFF3B82F6), Color(0xFF6366F1)), "V", v.toFloat()) { Spacer(Modifier.height(12.dp)) } }

                Spacer(Modifier.height(10.dp))
                ToggleBtn(running, hasN, ring) { skipTicker = true; if (running) DeviceMonitorService.stop(ctx) else DeviceMonitorService.start(ctx); running = !running }
                Spacer(Modifier.height(8.dp))
                if (running) Row(verticalAlignment = Alignment.CenterVertically) { Dot(); Spacer(Modifier.width(6.dp)); Text("流体云运行中", fontSize = 12.sp, color = LL.t2) }
                Spacer(Modifier.height(16.dp))
                Guide(ctx, ring[1])
                Spacer(Modifier.height(10.dp))
                Text("FluidPill v${BuildConfig.VERSION_NAME}", fontSize = 10.sp, color = LL.t3.copy(alpha = 0.4f))
                Spacer(Modifier.height(36.dp))
            }
        }
    }
}

// ============================================================
// Active Ring
// ============================================================
@Composable
fun ActiveRing(pct: Int, chg: Boolean, watts: Float, ring: List<Color>, mod: Modifier) {
    val ap by animateFloatAsState(pct / 100f, spring(0.55f, 300f), label = "ap")
    val ga by rememberInfiniteTransition(label = "g").animateFloat(0.04f, 0.12f, infiniteRepeatable(tween(1800, easing = EaseInOutSine), RepeatMode.Reverse), label = "ga")
    Box(mod, contentAlignment = Alignment.Center) {
        if (chg) Canvas(Modifier.fillMaxSize()) { drawCircle(Brush.radialGradient(listOf(ring[0].copy(alpha = ga), Color.Transparent)), size.minDimension * 0.48f) }
        Canvas(Modifier.fillMaxSize()) { val sw = size.minDimension * 0.095f; val r = (size.minDimension - sw) / 2f; val tl = Offset(sw / 2f, sw / 2f); drawArc(Color(0xFF1C1C26), 135f, 270f, false, tl, Size(r * 2f, r * 2f), style = Stroke(sw, cap = StrokeCap.Round)) }
        Canvas(Modifier.fillMaxSize()) { val sw = size.minDimension * 0.095f; val r = (size.minDimension - sw) / 2f; val tl = Offset(sw / 2f, sw / 2f); drawArc(Brush.sweepGradient(ring), 135f, 270f * ap, false, tl, Size(r * 2f, r * 2f), style = Stroke(sw, cap = StrokeCap.Round)) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$pct", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = LL.t1, letterSpacing = (-1).sp)
            Text("%", fontSize = 14.sp, color = LL.t2, modifier = Modifier.offset(y = (-2).dp))
            if (watts != 0f) Text("${"%.1f".format(watts)}W", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (chg) ring[1] else LL.t3)
        }
    }
}

@Composable
fun Badge(type: String, watts: Float, acc: Color) {
    Surface(shape = RoundedCornerShape(18.dp), color = acc.copy(alpha = 0.08f), border = androidx.compose.foundation.BorderStroke(1.dp, acc.copy(alpha = 0.15f))) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(type, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = acc)
            if (watts >= 0.5f) { Text(" · ", color = acc.copy(alpha = 0.4f)); Text("${"%.1f".format(watts)}W", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = acc) }
        }
    }
}

// ============================================================
// Dual Card
// ============================================================
@Composable
fun DualCard(l1: String, v1: String, l2: String, v2: String, i1: androidx.compose.ui.graphics.vector.ImageVector, i2: androidx.compose.ui.graphics.vector.ImageVector, chg: Boolean, acc: Color, warn: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        InfoCard(l1, v1, i1, chg, acc, Modifier.weight(1f), warn)
        InfoCard(l2, v2, i2, chg, acc, Modifier.weight(1f))
    }
}

@Composable
fun InfoCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, chg: Boolean, acc: Color, mod: Modifier, warn: Boolean = false) {
    val tint = if (warn) Color(0xFFEF4444) else if (chg) acc else LL.t2
    Card(mod.height(84.dp), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = LL.surface), border = androidx.compose.foundation.BorderStroke(0.5.dp, LL.border)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
            Icon(icon, null, tint = tint.copy(alpha = 0.45f), modifier = Modifier.size(16.dp))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LL.t1, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, fontSize = 10.sp, color = LL.t2)
        }
    }
}

// ============================================================
// Curve Card
// ============================================================
@Composable
fun CurveCard(title: String, vals: List<Float>, cols: List<Color>, unit: String, cur: Float, spacer: @Composable () -> Unit = {}) {
    val maxV = vals.maxOrNull()?.coerceAtLeast(0.1f) ?: 0.1f; val minV = vals.minOrNull() ?: 0f
    val range = (maxV - minV).coerceAtLeast(0.1f)
    val lc = cols[min(cols.size - 1, if (cur > 40f) 2 else 1)]
    val fill = Brush.verticalGradient(listOf(lc.copy(alpha = 0.1f), lc.copy(alpha = 0.01f)))

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = LL.surface), border = androidx.compose.foundation.BorderStroke(0.5.dp, LL.border)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Outlined.ShowChart, null, tint = lc.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = LL.t1)
                Spacer(Modifier.weight(1f))
                Text("${"%.1f".format(cur)}$unit", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = lc)
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(60.dp)) {
                Canvas(Modifier.fillMaxSize().clipToBounds()) {
                    if (vals.size < 2) return@Canvas
                    val w = size.width; val h = size.height; val pad = 6f
                    val stepX = (w - pad * 2) / (vals.size - 1)

                    val gridLines = 4
                    for (g in 0..gridLines) {
                        val y = pad + (h - pad * 2) * g / gridLines
                        drawLine(Color(0xFF2A2A30), Offset(pad, y), Offset(w - pad, y), 0.5f)
                    }

                    val fp = Path().apply {
                        moveTo(pad, h - pad)
                        vals.forEachIndexed { i, v -> lineTo(pad + i * stepX, h - pad - ((v - minV) / range * (h - pad * 2))) }
                        lineTo(pad + (vals.size - 1) * stepX, h - pad); close()
                    }
                    drawPath(fp, fill)

                    val lp = Path()
                    vals.forEachIndexed { i, v ->
                        val x = pad + i * stepX; val y = h - pad - ((v - minV) / range * (h - pad * 2))
                        if (i == 0) lp.moveTo(x, y) else lp.lineTo(x, y)
                    }
                    drawPath(lp, lc, style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                    val lx = pad + (vals.size - 1) * stepX; val ly = h - pad - ((vals.last() - minV) / range * (h - pad * 2))
                    drawCircle(lc, 3.5f, Offset(lx, ly)); drawCircle(lc.copy(alpha = 0.2f), 7f, Offset(lx, ly))
                }
                val format: (Float) -> String = { if (range >= 100f) "%.0f".format(it) else "%.1f".format(it) }
                Column(Modifier.fillMaxSize().padding(start = 2.dp, end = 0.dp, top = 2.dp, bottom = 0.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Text("${format(maxV)}$unit", fontSize = 8.sp, color = LL.t3.copy(alpha = 0.5f))
                    Text("${format(minV)}$unit", fontSize = 8.sp, color = LL.t3.copy(alpha = 0.5f))
                }
            }
        }
    }
    spacer()
}

@Composable fun NotifCard(ctx: Context, requestPermission: () -> Unit = {}) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.06f)), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.2f))) {
        Row(Modifier.fillMaxWidth().clickable { requestPermission() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.NotificationsOff, null, tint = Color(0xFFEF4444).copy(alpha = 0.6f), modifier = Modifier.size(20.dp)); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { Text("需要通知权限", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LL.t1); Text("点击授权 · 授权后自动启动流体云", fontSize = 11.sp, color = LL.t2) }
            Icon(Icons.Filled.ChevronRight, null, tint = Color(0xFFEF4444).copy(alpha = 0.3f))
        }
    }
}

@Composable fun ToggleBtn(run: Boolean, hn: Boolean, ring: List<Color>, onClick: () -> Unit) {
    val brush = if (run) Brush.horizontalGradient(listOf(Color(0xFFDC2626), Color(0xFFB91C1C))) else Brush.horizontalGradient(ring)
    Button(onClick, enabled = hn || Build.VERSION.SDK_INT < 33, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent), contentPadding = PaddingValues(0.dp)) {
        Box(Modifier.fillMaxSize().background(brush, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(if (run) Icons.Filled.Stop else Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (run) "停止监控" else "启动流体云", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White) }
        }
    }
}

@Composable fun Dot() { val a by rememberInfiniteTransition().animateFloat(0.5f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "ad"); Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF22C55E).copy(alpha = a))) }

@Composable fun Guide(ctx: Context, acc: Color) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = LL.surface.copy(alpha = 0.5f)), border = androidx.compose.foundation.BorderStroke(0.5.dp, LL.border)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, null, tint = acc, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp))
                Text("设置指南", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LL.t1); Spacer(Modifier.weight(1f))
                TextButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply { putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName) }) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)) { Text("系统设置", fontSize = 12.sp, color = acc); Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = acc, modifier = Modifier.size(11.dp)) }
            }
            Spacer(Modifier.height(8.dp))
            Step("1", "授权通知权限", "App 内红色卡片点击授权", acc); Step("2", "开启流体云", "系统设置 → 通知 → 流体云 → FluidPill", acc)
            Step("3", "允许锁屏显示", "系统设置 → 通知 → FluidPill → 锁屏", acc); Step("4", "启动并充电", "点击启动 → 插充电器 → 金标胶囊出现", acc)
        }
    }
}

@Composable fun Step(num: String, t: String, d: String, acc: Color) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(acc.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { Text(num, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = acc) }
        Spacer(Modifier.width(8.dp)); Column { Text(t, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = LL.t1); Text(d, fontSize = 10.sp, color = LL.t3, modifier = Modifier.padding(top = 1.dp)) }
    }
}
