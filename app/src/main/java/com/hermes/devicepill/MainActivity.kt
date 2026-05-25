     1|     1|package com.hermes.devicepill
     2|     2|
     3|     3|import android.Manifest
     4|     4|import android.content.*
     5|     5|import android.content.pm.PackageManager
     6|     6|import android.os.*
     7|     7|import android.provider.Settings
     8|     8|import androidx.activity.ComponentActivity
     9|     9|import androidx.activity.compose.setContent
    10|    10|import androidx.compose.animation.*
    11|    11|import androidx.compose.animation.core.*
    12|    12|import androidx.compose.foundation.Canvas
    13|    13|import androidx.compose.foundation.background
    14|    14|import androidx.compose.foundation.border
    15|    15|import androidx.compose.foundation.clickable
    16|    16|import androidx.compose.foundation.layout.*
    17|    17|import androidx.compose.foundation.rememberScrollState
    18|    18|import androidx.compose.foundation.shape.CircleShape
    19|    19|import androidx.compose.foundation.shape.RoundedCornerShape
    20|    20|import androidx.compose.foundation.verticalScroll
    21|    21|import androidx.compose.material.icons.Icons
    22|    22|import androidx.compose.material.icons.filled.*
    23|    23|import androidx.compose.material.icons.outlined.*
    24|    24|import androidx.compose.material3.*
    25|    25|import androidx.compose.runtime.*
    26|    26|import androidx.compose.ui.Alignment
    27|    27|import androidx.compose.ui.Modifier
    28|    28|import androidx.compose.ui.draw.clip
    29|    29|import androidx.compose.ui.draw.clipToBounds
    30|    30|import androidx.compose.ui.geometry.Offset
    31|    31|import androidx.compose.ui.geometry.Size
    32|    32|import androidx.compose.ui.graphics.*
    33|    33|import androidx.compose.ui.graphics.drawscope.Stroke
    34|    34|import androidx.compose.ui.platform.LocalContext
    35|    35|import androidx.compose.ui.text.font.FontWeight
    36|    36|import androidx.compose.ui.text.style.TextOverflow
    37|    37|import androidx.compose.ui.unit.dp
    38|    38|import androidx.compose.ui.unit.sp
    39|    39|import androidx.core.content.ContextCompat
    40|    40|import kotlin.math.*
    41|    41|import kotlinx.coroutines.delay
    42|    42|
    43|    43|class MainActivity : ComponentActivity() {
    44|    44|    private var hasNotificationPermission = mutableStateOf(false)
    45|    45|
    46|    46|    override fun onCreate(savedInstanceState: Bundle?) {
    47|    47|        super.onCreate(savedInstanceState)
    48|    48|        hasNotificationPermission.value = if (Build.VERSION.SDK_INT >= 33)
    49|    49|            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    50|    50|            else true
    51|    51|        setContent { DevicePillApp(hasN = hasNotificationPermission.value, onPermissionResult = { granted ->
    52|    52|            hasNotificationPermission.value = granted
    53|    53|        }) }
    54|    54|    }
    55|    55|
    56|    56|    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    57|    57|        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    58|    58|        if (requestCode == 1) {
    59|    59|            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
    60|    60|            hasNotificationPermission.value = granted
    61|    61|        }
    62|    62|    }
    63|    63|}
    64|    64|
    65|    65|// ============================================================
    66|    66|// LLMonitor-inspired palettes
    67|    67|// ============================================================
    68|    68|object LL {
    69|    69|    val dynamic = listOf(Color(0xFF40C0F4), Color(0xFFE86535), Color(0xFFF9E804), Color(0xFF32D653))
    70|    70|    val jizi = listOf(Color(0xFFE4BFFF), Color(0xFFCCA3FF), Color(0xFF8E57FF), Color(0xFF6346FF))
    71|    71|    val ocean = listOf(Color(0xFF6BAFFF), Color(0xFF1B8FFF), Color(0xFF0070FF), Color(0xFF2F99F0))
    72|    72|    val sunset = listOf(Color(0xFFFFB366), Color(0xFFFF8A47), Color(0xFFFF6B35), Color(0xFFFF4D1A))
    73|    73|    val bg = Color(0xFF0A0A0C); val surface = Color(0xFF141418)
    74|    74|    val border = Color(0xFF202028); val t1 = Color(0xFFEEEEF0)
    75|    75|    val t2 = Color(0xFF8A8A92); val t3 = Color(0xFF5A5A64)
    76|    76|}
    77|    77|
    78|    78|data class BatteryData(
    79|    79|    val pct: Int, val charging: Boolean, val watts: Float, val voltage: Double,
    80|    80|    val currentMa: Int, val tempC: Float, val chargeType: String, val health: String, val tech: String
    81|    81|)
    82|    82|
    83|    83|fun readBattery(ctx: Context): BatteryData {
    84|    84|    val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    85|    85|    val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
    86|    86|    val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    87|    87|    val pct = if (scale > 0) level * 100 / scale else 0
    88|    88|    val plugged = i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
    89|    89|    val chg = plugged != 0
    90|    90|    val ct = when (plugged) {
    91|    91|        BatteryManager.BATTERY_PLUGGED_AC -> "超级闪充"; BatteryManager.BATTERY_PLUGGED_USB -> "USB充电"
    92|    92|        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "无线充电"; else -> if (chg) "充电中" else "未充电"
    93|    93|    }
    94|    94|    val hl = when (i?.getIntExtra(BatteryManager.EXTRA_HEALTH, 1) ?: 1) {
    95|    95|        BatteryManager.BATTERY_HEALTH_GOOD -> "良好"; BatteryManager.BATTERY_HEALTH_OVERHEAT -> "过热"
    96|    96|        BatteryManager.BATTERY_HEALTH_DEAD -> "损坏"; BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "过压"
    97|    97|        BatteryManager.BATTERY_HEALTH_COLD -> "过冷"; else -> "正常"
    98|    98|    }
    99|    99|    val tech = i?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: ""
   100|   100|    val tc = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
   101|   101|    val mv = i?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0; val v = mv / 1000.0
   102|   102|    var w = 0f; var ma = 0
   103|   103|    val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
   104|   104|    if (bm != null) {
   105|   105|        val raw = runCatching { bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) }.getOrDefault(Int.MIN_VALUE)
   106|   106|        if (raw != Int.MIN_VALUE) { var m = raw; if (abs(m) > 10000) m /= 1000; ma = m; w = (v * m / 1000.0).toFloat() }
   107|   107|    }
   108|   108|    return BatteryData(pct, chg, w, v, ma, tc, ct, hl, tech)
   109|   109|}
   110|   110|
   111|   111|// ============================================================
   112|   112|@OptIn(ExperimentalMaterial3Api::class)
   113|   113|@Composable
   114|   114|fun DevicePillApp(hasN: Boolean, onPermissionResult: (Boolean) -> Unit = {}) {
   115|   115|    val ctx = LocalContext.current
   116|   116|    var ti by remember { mutableIntStateOf(0) }
   117|   117|    val tn = listOf("动感", "极紫", "海洋", "日落")
   118|   118|    val rings = listOf(LL.dynamic, LL.jizi, LL.ocean, LL.sunset)
   119|   119|    val ring = rings[ti]
   120|   120|
   121|   121|    var pct by remember { mutableIntStateOf(0) }; var chg by remember { mutableStateOf(false) }
   122|   122|    var watts by remember { mutableFloatStateOf(0f) }; var v by remember { mutableDoubleStateOf(0.0) }
   123|   123|    var ma by remember { mutableIntStateOf(0) }; var tc by remember { mutableFloatStateOf(0f) }
   124|   124|    var ct by remember { mutableStateOf("未充电") }; var hl by remember { mutableStateOf("良好") }
   125|   125|    var tech by remember { mutableStateOf("") }
   126|   126|    var running by remember { mutableStateOf(DeviceMonitorService.isRunning(ctx)) }
   127|   127|
   128|   128|    // Curve history (60 points, 3s each = 3 min window)
   129|   129|    val powerH = remember { mutableStateListOf<Float>() }; val tempH = remember { mutableStateListOf<Float>() }
   130|   130|    val voltH = remember { mutableStateListOf<Float>() }; val currH = remember { mutableStateListOf<Float>() }
   131|   131|
   132|   132|    LaunchedEffect(Unit) {
   133|   133|        while (true) {
   134|   134|            val d = readBattery(ctx)
   135|   135|            if (powerH.size >= 60) powerH.removeFirst(); powerH.add(d.watts)
   136|   136|            if (tempH.size >= 60) tempH.removeFirst(); tempH.add(d.tempC)
   137|   137|            if (voltH.size >= 60) voltH.removeFirst(); voltH.add(d.voltage.toFloat())
   138|   138|            if (currH.size >= 60) currH.removeFirst(); currH.add(d.currentMa.toFloat())
   139|   139|            delay(3000)
   140|   140|        }
   141|   141|    }
   142|   142|
   143|   143|    DisposableEffect(ctx) {
   144|   144|        val rcv = object : BroadcastReceiver() {
   145|   145|            override fun onReceive(c: Context?, intent: Intent?) {
   146|   146|                val d = readBattery(ctx); pct = d.pct; chg = d.charging; watts = d.watts
   147|   147|                v = d.voltage; ma = d.currentMa; tc = d.tempC; ct = d.chargeType
   148|   148|                hl = d.health; tech = d.tech
   149|   149|            }
   150|   150|        }
   151|   151|        ctx.registerReceiver(rcv, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
   152|   152|        onDispose { ctx.unregisterReceiver(rcv) }
   153|   153|    }
   154|   154|
   155|   155|    DisposableEffect(Unit) {
   156|   156|        val h = Handler(Looper.getMainLooper())
   157|   157|        val tick = object : Runnable { override fun run() { running = DeviceMonitorService.isRunning(ctx); h.postDelayed(this, 2000) } }
   158|   158|        h.post(tick); onDispose { h.removeCallbacks(tick) }
   159|   159|    }
   160|   160|
   161|   161|    MaterialTheme(colorScheme = darkColorScheme(background = LL.bg, surface = LL.surface, primary = ring[1])) {
   162|   162|        Scaffold(containerColor = LL.bg,
   163|   163|            topBar = { TopAppBar(title = { Row { Text("⚡", fontSize = 20.sp); Spacer(Modifier.width(8.dp)); Text("充电·岛", fontWeight = FontWeight.Bold) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = LL.bg)) }
   164|   164|        ) { pad ->
   165|   165|            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pad).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
   166|   166|                Spacer(Modifier.height(4.dp))
   167|   167|                // Theme chips
   168|   168|                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
   169|   169|                    tn.forEachIndexed { i, n -> val sel = i == ti; val col = if (sel) ring[1] else LL.t2
   170|   170|                        Surface(onClick = { ti = i }, shape = RoundedCornerShape(20.dp), color = if (sel) ring[0].copy(alpha = 0.12f) else Color.Transparent, border = if (sel) androidx.compose.foundation.BorderStroke(1.dp, ring[1].copy(alpha = 0.25f)) else androidx.compose.foundation.BorderStroke(1.dp, Color.Transparent)) { Text(n, Modifier.padding(horizontal = 14.dp, vertical = 7.dp), fontSize = 12.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, color = col) } }
   171|   171|                }
   172|   172|                Spacer(Modifier.height(20.dp))
   173|   173|
   174|   174|                // Active Ring
   175|   175|                ActiveRing(pct, chg, watts, ring, Modifier.size(200.dp))
   176|   176|                Spacer(Modifier.height(10.dp))
   177|   177|                AnimatedVisibility(chg) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Badge(ct, watts, ring[1]); Spacer(Modifier.height(14.dp)) } }
   178|   178|
   179|   179|                // ── LLMonitor layout: card groups ──
   180|   180|                // Power + Current (LLMonitor: card_power_current)
   181|   181|                CardGroup(hasN, Build.VERSION.SDK_INT)
   182|   182|                if (!hasN && Build.VERSION.SDK_INT >= 33) { NotifCard(ctx, onPermissionResult); Spacer(Modifier.height(14.dp)) }
   183|   183|
   184|   184|                // Auto-start service when permission is granted and not running yet
   185|   185|                LaunchedEffect(hasN, running) {
   186|   186|                    if (hasN && !running && Build.VERSION.SDK_INT >= 33) {
   187|   187|                        DeviceMonitorService.start(ctx); running = true
   188|   188|                    }
   189|   189|                }
   190|   190|                DualCard("功率", if (watts != 0f) "${"%.1f".format(watts)}W" else "--W", "电流", if (ma != 0) "${ma}mA" else "--", Icons.Outlined.Bolt, Icons.Outlined.Speed, chg, ring[1])
   191|   191|                Spacer(Modifier.height(10.dp))
   192|   192|
   193|   193|                // Voltage + Temperature (LLMonitor: card_voltage_temp)
   194|   194|                DualCard("电压", if (v > 0) "${"%.2f".format(v)}V" else "--V", "温度", "${"%.1f".format(tc)}℃", Icons.Filled.BatteryChargingFull, Icons.Filled.DeviceThermostat, chg, ring[1], (tc > 40))
   195|   195|                Spacer(Modifier.height(10.dp))
   196|   196|
   197|   197|                // Supply + Health (LLMonitor: card_supply_health)
   198|   198|                DualCard("供电", ct, "健康", hl, Icons.Outlined.Power, Icons.Outlined.FavoriteBorder, chg, ring[1])
   199|   199|                Spacer(Modifier.height(14.dp))
   200|   200|
   201|   201|                // Power Curve
   202|   202|                if (powerH.size >= 2) { CurveCard("充电功率", powerH, ring, "W", watts) { Spacer(Modifier.height(12.dp)) } }
   203|   203|                // Temperature Curve
   204|   204|                if (tempH.size >= 2) { CurveCard("温度", tempH, listOf(Color(0xFF22C55E), Color(0xFFEAB308), Color(0xFFEF4444)), "℃", tc) { Spacer(Modifier.height(12.dp)) } }
   205|   205|                // Voltage Curve
   206|   206|                if (voltH.size >= 2) { CurveCard("电压", voltH, listOf(Color(0xFF3B82F6), Color(0xFF6366F1)), "V", v.toFloat()) { Spacer(Modifier.height(12.dp)) } }
   207|   207|
   208|   208|                Spacer(Modifier.height(10.dp))
   209|   209|                ToggleBtn(running, hasN, ring) { if (running) DeviceMonitorService.stop(ctx) else DeviceMonitorService.start(ctx); running = !running }
   210|   210|                Spacer(Modifier.height(8.dp))
   211|   211|                if (running) Row(verticalAlignment = Alignment.CenterVertically) { Dot(); Spacer(Modifier.width(6.dp)); Text("流体云运行中", fontSize = 12.sp, color = LL.t2) }
   212|   212|                Spacer(Modifier.height(16.dp))
   213|   213|                Guide(ctx, ring[1])
   214|   214|                Spacer(Modifier.height(10.dp))
   215|   215|                Text("DevicePill v2.1.1", fontSize = 10.sp, color = LL.t3.copy(alpha = 0.4f))
   216|   216|                Spacer(Modifier.height(36.dp))
   217|   217|            }
   218|   218|        }
   219|   219|    }
   220|   220|}
   221|   221|
   222|   222|// ============================================================
   223|   223|// Active Ring
   224|   224|// ============================================================
   225|   225|@Composable
   226|   226|fun ActiveRing(pct: Int, chg: Boolean, watts: Float, ring: List<Color>, mod: Modifier) {
   227|   227|    val ap by animateFloatAsState(pct / 100f, spring(0.55f, 300f), label = "ap")
   228|   228|    val ga by rememberInfiniteTransition(label = "g").animateFloat(0.04f, 0.12f, infiniteRepeatable(tween(1800, easing = EaseInOutSine), RepeatMode.Reverse), label = "ga")
   229|   229|    Box(mod, contentAlignment = Alignment.Center) {
   230|   230|        if (chg) Canvas(Modifier.fillMaxSize()) { drawCircle(Brush.radialGradient(listOf(ring[0].copy(alpha = ga), Color.Transparent)), size.minDimension * 0.48f) }
   231|   231|        Canvas(Modifier.fillMaxSize()) { val sw = size.minDimension * 0.095f; val r = (size.minDimension - sw) / 2f; val tl = Offset(sw / 2f, sw / 2f); drawArc(Color(0xFF1C1C26), 135f, 270f, false, tl, Size(r * 2f, r * 2f), style = Stroke(sw, cap = StrokeCap.Round)) }
   232|   232|        Canvas(Modifier.fillMaxSize()) { val sw = size.minDimension * 0.095f; val r = (size.minDimension - sw) / 2f; val tl = Offset(sw / 2f, sw / 2f); drawArc(Brush.sweepGradient(ring), 135f, 270f * ap, false, tl, Size(r * 2f, r * 2f), style = Stroke(sw, cap = StrokeCap.Round)) }
   233|   233|        Column(horizontalAlignment = Alignment.CenterHorizontally) {
   234|   234|            Text("$pct", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = LL.t1, letterSpacing = (-1).sp)
   235|   235|            Text("%", fontSize = 14.sp, color = LL.t2, modifier = Modifier.offset(y = (-2).dp))
   236|   236|            if (watts != 0f) Text("${"%.1f".format(watts)}W", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (chg) ring[1] else LL.t3)
   237|   237|        }
   238|   238|    }
   239|   239|}
   240|   240|
   241|   241|@Composable
   242|   242|fun Badge(type: String, watts: Float, acc: Color) {
   243|   243|    Surface(shape = RoundedCornerShape(18.dp), color = acc.copy(alpha = 0.08f), border = androidx.compose.foundation.BorderStroke(1.dp, acc.copy(alpha = 0.15f))) {
   244|   244|        Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
   245|   245|            Text("⚡", fontSize = 13.sp); Spacer(Modifier.width(6.dp))
   246|   246|            Text(type, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = acc)
   247|   247|            if (watts >= 0.5f) { Text(" · ", color = acc.copy(alpha = 0.4f)); Text("${"%.1f".format(watts)}W", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = acc) }
   248|   248|        }
   249|   249|    }
   250|   250|}
   251|   251|
   252|   252|// ============================================================
   253|   253|// Dual Card (LLMonitor: InfoCard pair)
   254|   254|// ============================================================
   255|   255|@Composable
   256|   256|fun DualCard(l1: String, v1: String, l2: String, v2: String, i1: androidx.compose.ui.graphics.vector.ImageVector, i2: androidx.compose.ui.graphics.vector.ImageVector, chg: Boolean, acc: Color, warn: Boolean = false) {
   257|   257|    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
   258|   258|        InfoCard(l1, v1, i1, chg, acc, Modifier.weight(1f), warn)
   259|   259|        InfoCard(l2, v2, i2, chg, acc, Modifier.weight(1f))
   260|   260|    }
   261|   261|}
   262|   262|
   263|   263|@Composable
   264|   264|fun InfoCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, chg: Boolean, acc: Color, mod: Modifier, warn: Boolean = false) {
   265|   265|    val tint = if (warn) Color(0xFFEF4444) else if (chg) acc else LL.t2
   266|   266|    Card(mod.height(84.dp), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = LL.surface), border = androidx.compose.foundation.BorderStroke(0.5.dp, LL.border)) {
   267|   267|        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
   268|   268|            Icon(icon, null, tint = tint.copy(alpha = 0.45f), modifier = Modifier.size(16.dp))
   269|   269|            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LL.t1, maxLines = 1, overflow = TextOverflow.Ellipsis)
   270|   270|            Text(label, fontSize = 10.sp, color = LL.t2)
   271|   271|        }
   272|   272|    }
   273|   273|}
   274|   274|
   275|   275|// ============================================================
   276|   276|// Curve Card (LLMonitor: PowerCurveCard with grid lines)
   277|   277|// ============================================================
   278|   278|@Composable
   279|   279|fun CurveCard(title: String, vals: List<Float>, cols: List<Color>, unit: String, cur: Float, spacer: @Composable () -> Unit = {}) {
   280|   280|    val maxV = vals.maxOrNull()?.coerceAtLeast(0.1f) ?: 0.1f; val minV = vals.minOrNull() ?: 0f
   281|   281|    val range = (maxV - minV).coerceAtLeast(0.1f)
   282|   282|    val lc = cols[min(cols.size - 1, if (cur > 40f) 2 else 1)]
   283|   283|    val fill = Brush.verticalGradient(listOf(lc.copy(alpha = 0.1f), lc.copy(alpha = 0.01f)))
   284|   284|
   285|   285|    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = LL.surface), border = androidx.compose.foundation.BorderStroke(0.5.dp, LL.border)) {
   286|   286|        Column(Modifier.padding(14.dp)) {
   287|   287|            Row(verticalAlignment = Alignment.CenterVertically) {
   288|   288|                Icon(Icons.Outlined.ShowChart, null, tint = lc.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
   289|   289|                Spacer(Modifier.width(8.dp))
   290|   290|                Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = LL.t1)
   291|   291|                Spacer(Modifier.weight(1f))
   292|   292|                Text("${"%.1f".format(cur)}$unit", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = lc)
   293|   293|            }
   294|   294|            Spacer(Modifier.height(10.dp))
   295|   295|            Canvas(Modifier.fillMaxWidth().height(60.dp).clipToBounds()) {
   296|   296|                if (vals.size < 2) return@Canvas
   297|   297|                val w = size.width; val h = size.height; val pad = 6f
   298|   298|                val stepX = (w - pad * 2) / (vals.size - 1)
   299|   299|
   300|   300|                // Grid lines (LLMonitor: MAX_POWER_GRID_LINES)
   301|   301|                val gridLines = 4
   302|   302|                for (g in 0..gridLines) {
   303|   303|                    val y = pad + (h - pad * 2) * g / gridLines
   304|   304|                    drawLine(Color(0xFF2A2A30), Offset(pad, y), Offset(w - pad, y), 0.5f)
   305|   305|                }
   306|   306|
   307|   307|                // Fill
   308|   308|                val fp = Path().apply {
   309|   309|                    moveTo(pad, h - pad)
   310|   310|                    vals.forEachIndexed { i, v -> lineTo(pad + i * stepX, h - pad - ((v - minV) / range * (h - pad * 2))) }
   311|   311|                    lineTo(pad + (vals.size - 1) * stepX, h - pad); close()
   312|   312|                }
   313|   313|                drawPath(fp, fill)
   314|   314|
   315|   315|                // Line
   316|   316|                val lp = Path()
   317|   317|                vals.forEachIndexed { i, v ->
   318|   318|                    val x = pad + i * stepX; val y = h - pad - ((v - minV) / range * (h - pad * 2))
   319|   319|                    if (i == 0) lp.moveTo(x, y) else lp.lineTo(x, y)
   320|   320|                }
   321|   321|                drawPath(lp, lc, style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
   322|   322|
   323|   323|                // End dot
   324|   324|                val lx = pad + (vals.size - 1) * stepX; val ly = h - pad - ((vals.last() - minV) / range * (h - pad * 2))
   325|   325|                drawCircle(lc, 3.5f, Offset(lx, ly)); drawCircle(lc.copy(alpha = 0.2f), 7f, Offset(lx, ly))
   326|   326|            }
   327|   327|        }
   328|   328|    }
   329|   329|    spacer()
   330|   330|}
   331|   331|
   332|   332|@Composable
   333|   333|fun CardGroup(hasN: Boolean, sdk: Int) {}
   334|   334|
   335|   335|// ============================================================
   336|   336|@Composable fun NotifCard(ctx: Context, onResult: (Boolean) -> Unit = {}) {
   337|   337|    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.06f)), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.2f))) {
   338|   338|        Row(Modifier.fillMaxWidth().clickable { (ctx as? ComponentActivity)?.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
   339|   339|            Icon(Icons.Filled.NotificationsOff, null, tint = Color(0xFFEF4444).copy(alpha = 0.6f), modifier = Modifier.size(20.dp)); Spacer(Modifier.width(10.dp))
   340|   340|            Column(Modifier.weight(1f)) { Text("需要通知权限", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LL.t1); Text("点击授权 · 授权后自动启动流体云", fontSize = 11.sp, color = LL.t2) }
   341|   341|            Icon(Icons.Filled.ChevronRight, null, tint = Color(0xFFEF4444).copy(alpha = 0.3f))
   342|   342|        }
   343|   343|    }
   344|   344|}
   345|   345|
   346|   346|@Composable fun ToggleBtn(run: Boolean, hn: Boolean, ring: List<Color>, onClick: () -> Unit) {
   347|   347|    val brush = if (run) Brush.horizontalGradient(listOf(Color(0xFFDC2626), Color(0xFFB91C1C))) else Brush.horizontalGradient(ring)
   348|   348|    Button(onClick, enabled = hn || Build.VERSION.SDK_INT < 33, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent), contentPadding = PaddingValues(0.dp)) {
   349|   349|        Box(Modifier.fillMaxSize().background(brush, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
   350|   350|            Row(verticalAlignment = Alignment.CenterVertically) { Icon(if (run) Icons.Filled.Stop else Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (run) "停止监控" else "启动流体云", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White) }
   351|   351|        }
   352|   352|    }
   353|   353|}
   354|   354|
   355|   355|@Composable fun Dot() { val a by rememberInfiniteTransition().animateFloat(0.5f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "ad"); Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF22C55E).copy(alpha = a))) }
   356|   356|
   357|   357|@Composable fun Guide(ctx: Context, acc: Color) {
   358|   358|    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = LL.surface.copy(alpha = 0.5f)), border = androidx.compose.foundation.BorderStroke(0.5.dp, LL.border)) {
   359|   359|        Column(Modifier.padding(14.dp)) {
   360|   360|            Row(verticalAlignment = Alignment.CenterVertically) {
   361|   361|                Icon(Icons.Filled.Info, null, tint = acc, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp))
   362|   362|                Text("设置指南", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LL.t1); Spacer(Modifier.weight(1f))
   363|   363|                TextButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply { putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName) }) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)) { Text("系统设置", fontSize = 12.sp, color = acc); Icon(Icons.Filled.OpenInNew, null, tint = acc, modifier = Modifier.size(11.dp)) }
   364|   364|            }
   365|   365|            Spacer(Modifier.height(8.dp))
   366|   366|            Step("1", "授权通知权限", "App 内红色卡片点击授权", acc); Step("2", "开启流体云", "系统设置 → 通知 → 流体云 → DevicePill", acc)
   367|   367|            Step("3", "允许锁屏显示", "系统设置 → 通知 → DevicePill → 锁屏", acc); Step("4", "启动并充电", "点击启动 → 插充电器 → 金标胶囊出现", acc)
   368|   368|        }
   369|   369|    }
   370|   370|}
   371|   371|
   372|   372|@Composable fun Step(num: String, t: String, d: String, acc: Color) {
   373|   373|    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
   374|   374|        Box(Modifier.size(20.dp).clip(CircleShape).background(acc.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { Text(num, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = acc) }
   375|   375|        Spacer(Modifier.width(8.dp)); Column { Text(t, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = LL.t1); Text(d, fontSize = 10.sp, color = LL.t3, modifier = Modifier.padding(top = 1.dp)) }
   376|   376|    }
   377|   377|}
   378|   378|