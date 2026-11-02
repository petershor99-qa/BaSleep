package com.family.sleepschedule

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource as resPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        setContent { App(prefs, version) }
    }
}

/** Manrope (OFL, Google Fonts). */
private val Manrope = FontFamily(
    Font(R.font.manrope_400, FontWeight.Normal),
    Font(R.font.manrope_500, FontWeight.Medium),
    Font(R.font.manrope_600, FontWeight.SemiBold),
    Font(R.font.manrope_700, FontWeight.Bold),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9BB8FF),
    onPrimary = Color(0xFF0B1230),
    background = Color(0xFF0E1118),
    onBackground = Color(0xFFE8EBF4),
    surface = Color(0xFF161B27),
    onSurface = Color(0xFFE8EBF4),
    surfaceVariant = Color(0xFF1C2233),
    onSurfaceVariant = Color(0xFF98A1B6),
    outline = Color(0xFF273046),
)

private val CardBorder = Color(0xFF273046)
private val NightTint = Color(0xFFC9B8FF)
private val NightBg = Color(0xFF191B2E)
private val NightBorder = Color(0x40C9B8FF)
private val NapBg = Color(0xFF1B2130)
private val Amber = Color(0xFFE8C476)
private val AmberBg = Color(0x2EE8C476)
private val Subtitle = Color(0xFFAAB4C8)
private val ChipIdleFg = Color(0xFFB7C0D2)
private val FeedTint = Color(0xFF9FD8B4)
private val ActiveBorder = Color(0xFF9BB8FF)
private val ActiveBg = Color(0x1A9BB8FF)
private val MutedBg = Color(0xFF12161F)

/** Одноразовая отмена последнего действия. */
private class UndoState(val label: String, val restore: () -> Unit)

private fun nowMinutesOfDay(): Int {
    val cal = Calendar.getInstance()
    return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
}

@Composable
fun App(prefs: SharedPreferences, version: String) {
    var ageIdx by remember { mutableIntStateOf(prefs.getInt("age", 0)) }
    var planIdx by remember { mutableIntStateOf(prefs.getInt("plan", 0)) }
    var wake by remember { mutableIntStateOf(prefs.getInt("wake", 9 * 60)) }
    var feeds by remember { mutableStateOf(Prefs.loadFeeds(prefs)) }
    var regress by remember { mutableStateOf(Prefs.loadRegress(prefs)) }

    var nightMode by remember { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }

    val age = SleepEngine.AGES[ageIdx.coerceIn(SleepEngine.AGES.indices)]
    val plan = age.plans[planIdx.coerceIn(age.plans.indices)]

    // Ручные правки: грузим один раз (со старыми значениями за границей —
    // приведёт sanitizeAdj), дальше любые изменения идут через setAdj.
    var adj by remember { mutableStateOf(SleepEngine.Adjustments()) }
    var adjLoaded by remember { mutableStateOf(false) }
    if (!adjLoaded) {
        adj = SleepEngine.sanitizeAdj(age, plan, Prefs.loadAdj(prefs, plan), regress)
        adjLoaded = true
    }
    fun setAdj(new: SleepEngine.Adjustments) {
        adj = SleepEngine.sanitizeAdj(age, plan, new, regress)
    }

    val schedule = remember(plan, wake, adj, regress) {
        SleepEngine.build(age, plan, wake, adj, regress)
    }
    val placements = remember(schedule, feeds) { SleepEngine.placeFeeds(schedule, feeds) }

    // Текущее время: на границе минуты + при возврате приложения на экран.
    var nowMin by remember { mutableIntStateOf(nowMinutesOfDay()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) nowMin = nowMinutesOfDay()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            nowMin = nowMinutesOfDay()
            val sec = Calendar.getInstance().get(Calendar.SECOND)
            delay(((60 - sec) * 1000L + 60L).coerceAtLeast(200L))
        }
    }
    val now = remember(schedule, nowMin) { SleepEngine.nowInfo(schedule, nowMin) }

    // ── Состояние UI ────────────────────────────────────────────────────────
    var undo by remember { mutableStateOf<UndoState?>(null) }
    fun showUndo(label: String, restore: () -> Unit) {
        undo = UndoState(label, restore)
    }

    var wakeOpen by rememberSaveable { mutableStateOf(false) }
    var eventKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pastOpen by rememberSaveable { mutableStateOf(false) }
    var totalsOpen by rememberSaveable { mutableStateOf(false) }
    var editingFeed by rememberSaveable { mutableIntStateOf(-1) }

    // ── Действия ───────────────────────────────────────────────────────────
    fun changeNap(i: Int, d: Int) {
        setAdj(adj.copy(napOffsets = adj.napOffsets.toMutableList().also {
            it[i] = (it[i] + d).coerceIn(
                20 - plan.napDurations[i],
                240 - plan.napDurations[i],
            )
        }))
    }

    fun resetNap(i: Int) = setAdj(adj.copy(napOffsets = adj.napOffsets.toMutableList().also { it[i] = 0 }))

    fun changeWw(i: Int, d: Int) {
        val range = SleepEngine.manualRange(age, plan, adj.napOffsets, if (regress) -SleepEngine.REGRESS_STEP else 0, i)
        setAdj(adj.copy(wwOffsets = adj.wwOffsets.toMutableList().also {
            it[i] = (it[i] + d).coerceIn(range.first, range.last)
        }))
    }

    fun resetWw(i: Int) = setAdj(adj.copy(wwOffsets = adj.wwOffsets.toMutableList().also { it[i] = 0 }))

    fun newDay() {
        val prev = adj
        setAdj(SleepEngine.Adjustments(List(plan.naps) { 0 }, List(plan.naps + 1) { 0 }))
        showUndo("Новый день: правки снов и окон сброшены") { setAdj(prev) }
    }

    fun selectAge(option: SleepEngine.Age) {
        if (option === age) return  // повторный выбор — no-op, правки не трогаем
        val prevAge = ageIdx
        val prevPlan = planIdx
        val prevAdj = adj
        ageIdx = SleepEngine.AGES.indexOf(option)
        planIdx = 0
        adj = SleepEngine.Adjustments(
            List(option.plans.first().naps) { 0 },
            List(option.plans.first().naps + 1) { 0 },
        )
        showUndo("Возраст изменён — правки дня сброшены") {
            ageIdx = prevAge; planIdx = prevPlan; adj = prevAdj
        }
    }

    fun selectPlan(option: SleepEngine.NapPlan) {
        if (option === plan) return
        val prevPlan = planIdx
        val prevAdj = adj
        planIdx = age.plans.indexOf(option)
        adj = SleepEngine.Adjustments(List(option.naps) { 0 }, List(option.naps + 1) { 0 })
        showUndo("Схема изменена — правки дня сброшены") {
            planIdx = prevPlan; adj = prevAdj
        }
    }

    fun toggleRegress() {
        regress = !regress
        setAdj(adj)  // границы ручных правок сдвинулись — пересанитизировать
    }

    fun changeFeed(value: Int, d: Int) {
        val nv = (((value + d) % 1440) + 1440) % 1440
        feeds = feeds.toMutableList().also { list ->
            val i = list.indexOf(value)
            if (i >= 0) list[i] = nv
        }.sorted()
        editingFeed = nv  // редактор следует за значением, а не за индексом
    }

    fun resetFeeds() {
        val prev = feeds
        feeds = SleepEngine.DEFAULT_FEEDS
        showUndo("Кормления сброшены на схему по умолчанию") { feeds = prev }
    }

    val context = LocalContext.current
    fun shareDay() {
        val text = SleepEngine.exportText(schedule, age, plan, feeds)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, "Отправить расписание"))
    }

    // ── Сохранение + виджет ────────────────────────────────────────────────
    LaunchedEffect(ageIdx, planIdx, wake) {
        prefs.edit().putInt("age", ageIdx).putInt("plan", planIdx).putInt("wake", wake).apply()
        pushWidget(context)
    }
    LaunchedEffect(feeds) {
        prefs.edit().putString("feeds", feeds.joinToString(",")).apply()
        pushWidget(context)
    }
    LaunchedEffect(adj) {
        prefs.edit()
            .putString("offsets", adj.napOffsets.joinToString(","))
            .putString("ww", adj.wwOffsets.joinToString(","))
            .apply()
        pushWidget(context)
    }
    LaunchedEffect(regress) {
        prefs.edit().putBoolean("regress", regress).apply()
        pushWidget(context)
    }

    MaterialTheme(
        colorScheme = DarkScheme,
        typography = Typography(bodyLarge = TextStyle(fontFamily = Manrope)),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = DarkScheme.background) {
            BackHandler(enabled = nightMode || settingsOpen) {
                if (nightMode) nightMode = false else settingsOpen = false
            }
            when {
                nightMode -> NightScreen(schedule, nowMin, onExit = { nightMode = false })
                settingsOpen -> SettingsScreen(
                    age = age,
                    plan = plan,
                    feeds = feeds,
                    regress = regress,
                    version = version,
                    editingFeed = editingFeed,
                    onEditingFeed = { editingFeed = it },
                    onSelectAge = ::selectAge,
                    onSelectPlan = ::selectPlan,
                    onToggleRegress = ::toggleRegress,
                    onChangeFeed = ::changeFeed,
                    onResetFeeds = ::resetFeeds,
                    onNewDay = ::newDay,
                    onBack = { settingsOpen = false },
                )
                else -> MainScreen(
                    schedule = schedule,
                    age = age,
                    plan = plan,
                    now = now,
                    nowMin = nowMin,
                    feeds = feeds,
                    placements = placements,
                    undoLabel = undo?.label,
                    onUndo = { undo?.restore(); undo = null },
                    onDismissUndo = { undo = null },
                    wakeOpen = wakeOpen,
                    onWakeOpen = { wakeOpen = !wakeOpen },
                    onWakeDelta = { d -> wake = (wake + d).coerceIn(300, 720) },
                    eventKey = eventKey,
                    onEventKey = { eventKey = if (eventKey == it) null else it },
                    pastOpen = pastOpen,
                    onPastOpen = { pastOpen = !pastOpen },
                    totalsOpen = totalsOpen,
                    onTotalsOpen = { totalsOpen = !totalsOpen },
                    onChangeNap = ::changeNap,
                    onResetNap = ::resetNap,
                    onChangeWw = ::changeWw,
                    onResetWw = ::resetWw,
                    onOpenSettings = { settingsOpen = true },
                    onNightMode = { nightMode = true },
                    onShare = ::shareDay,
                )
            }
        }
    }
}

private fun pushWidget(context: Context) {
    val man = AppWidgetManager.getInstance(context) ?: return
    val ids = man.getAppWidgetIds(ComponentName(context, WidgetNext::class.java))
    if (ids.isEmpty()) return
    val intent = Intent(context, WidgetNext::class.java).apply {
        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
    }
    context.sendBroadcast(intent)
}

// ══════════════════════════ ГЛАВНЫЙ ЭКРАН ═════════════════════════════════

@Composable
private fun MainScreen(
    schedule: SleepEngine.Schedule,
    age: SleepEngine.Age,
    plan: SleepEngine.NapPlan,
    now: SleepEngine.NowInfo,
    nowMin: Int,
    feeds: List<Int>,
    placements: List<SleepEngine.FeedPlacement>,
    undoLabel: String?,
    onUndo: () -> Unit,
    onDismissUndo: () -> Unit,
    wakeOpen: Boolean,
    onWakeOpen: () -> Unit,
    onWakeDelta: (Int) -> Unit,
    eventKey: String?,
    onEventKey: (String) -> Unit,
    pastOpen: Boolean,
    onPastOpen: () -> Unit,
    totalsOpen: Boolean,
    onTotalsOpen: () -> Unit,
    onChangeNap: (Int, Int) -> Unit,
    onResetNap: (Int) -> Unit,
    onChangeWw: (Int, Int) -> Unit,
    onResetWw: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onNightMode: () -> Unit,
    onShare: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // Шапка: Сегодня + схема, справа вход в настройки
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Сегодня", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${age.label} · ${plan.name}",
                        fontSize = 13.sp,
                        color = Subtitle,
                    )
                }
                IconPressable(R.drawable.ic_share, "Поделиться", "Поделиться расписанием", onShare)
                Spacer(Modifier.width(8.dp))
                IconPressable(
                    icon = R.drawable.ic_sliders,
                    label = "Настройки",
                    a11y = "Открыть настройки",
                    onClick = onOpenSettings,
                )
            }

            // Подъём — компактная строка с раскрывающимся редактором
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onWakeOpen)
                    .padding(vertical = 6.dp)
                    .semantics { contentDescription = "Изменить время подъёма" },
            ) {
                Icon(
                    resPainter(R.drawable.ic_sun),
                    contentDescription = null,
                    tint = Amber,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    "Подъём ${SleepEngine.fmtClock(schedule.wake)}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (wakeOpen) "Свернуть" else "Изменить",
                    fontSize = 13.sp,
                    color = ActiveBorder,
                )
            }
            if (wakeOpen) {
                WakeEditor(wake = schedule.wake, onDelta = onWakeDelta)
            }

            if (undoLabel != null) {
                UndoBar(label = undoLabel, onUndo = onUndo, onDismiss = onDismissUndo)
            }

            HeroBlock(
                schedule = schedule,
                now = now,
                onEditNap = { onEventKey("nap$it") },
                onEditLastWindow = { onEventKey("ww${schedule.windows.lastIndex}") },
                onEditWake = onWakeOpen,
            )

            Timeline(
                schedule = schedule,
                age = age,
                now = now,
                placements = placements,
                eventKey = eventKey,
                onEventKey = onEventKey,
                pastOpen = pastOpen,
                onPastOpen = onPastOpen,
                onChangeNap = onChangeNap,
                onResetNap = onResetNap,
                onChangeWw = onChangeWw,
                onResetWw = onResetWw,
            )

            TotalsBlock(
                schedule = schedule,
                age = age,
                open = totalsOpen,
                onToggle = onTotalsOpen,
            )

            Spacer(Modifier.height(8.dp))
        }

        Surface(color = DarkScheme.surface, tonalElevation = 0.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clickable(onClick = onNightMode).padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(resPainter(R.drawable.ic_night), contentDescription = null,
                    tint = NightTint, modifier = Modifier.size(18.dp))
                Text("Ночной экран", fontSize = 14.sp, color = NightTint)
            }
        }
    }
}

private fun Modifier.semanticsClick(desc: String): Modifier =
    semantics { contentDescription = desc }

@Composable
private fun WakeEditor(wake: Int, onDelta: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MutedBg)
            .padding(12.dp),
    ) {
        RollingText(
            text = SleepEngine.fmtClock(wake),
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = DarkScheme.primary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            StepButton("−1 ч", Modifier.weight(1f), "Уменьшить время подъёма на час") { onDelta(-60) }
            StepButton("−5 мин", Modifier.weight(1f), "Уменьшить время подъёма на 5 минут") { onDelta(-5) }
            StepButton("+5 мин", Modifier.weight(1f), "Увеличить время подъёма на 5 минут") { onDelta(5) }
            StepButton("+1 ч", Modifier.weight(1f), "Увеличить время подъёма на час") { onDelta(60) }
        }
    }
}

@Composable
private fun UndoBar(label: String, onUndo: () -> Unit, onDismiss: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AmberBg)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(label, fontSize = 13.sp, color = Amber, modifier = Modifier.weight(1f))
        FactPill("Отменить", onUndo, a11y = "Отменить последнее действие")
        Text("✕", fontSize = 15.sp, color = Amber,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 8.dp, vertical = 10.dp))
    }
}

/** Самый заметный блок: ближайшее событие. */
@Composable
private fun HeroBlock(
    schedule: SleepEngine.Schedule,
    now: SleepEngine.NowInfo,
    onEditNap: (Int) -> Unit,
    onEditLastWindow: () -> Unit,
    onEditWake: () -> Unit,
) {
    val title: String
    val time: Int
    val onEdit: () -> Unit
    when (now.phase) {
        SleepEngine.Phase.NAP -> {
            title = "По плану сейчас сон ${now.napIndex}"
            time = now.nextAbs
            onEdit = { onEditNap(now.napIndex) }
        }
        SleepEngine.Phase.WINDOW -> {
            val nap = schedule.naps.firstOrNull { it.start == now.nextAbs }
            title = if (nap != null) "Следующий сон по плану" else "Ночной сон по плану"
            time = now.nextAbs
            onEdit = if (nap != null) ({ onEditNap(nap.index) }) else onEditLastWindow
        }
        SleepEngine.Phase.NIGHT -> {
            title = "По плану сейчас ночь"
            time = schedule.wake
            onEdit = onEditWake
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(DarkScheme.surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, fontSize = 14.sp, color = Subtitle)
        Text(
            (if (now.phase == SleepEngine.Phase.WINDOW) "В " else "До ") + SleepEngine.fmtClock(time),
            fontSize = 28.sp, fontWeight = FontWeight.Bold, color = DarkScheme.onSurface,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (now.phase == SleepEngine.Phase.WINDOW) SleepEngine.fmtIn(now.minutesLeft)
                else "Осталось ${SleepEngine.fmtDurHuman(now.minutesLeft)}", fontSize = 14.sp,
                color = ActiveBorder, modifier = Modifier.weight(1f))
            FactPill("Изменить", onEdit, a11y = "Изменить текущее событие расписания")
        }
    }
}

// Лента строится из единого списка событий, а не из блоков сна с кормлениями в конце.
@Composable
private fun Timeline(
    schedule: SleepEngine.Schedule,
    age: SleepEngine.Age,
    now: SleepEngine.NowInfo,
    placements: List<SleepEngine.FeedPlacement>,
    eventKey: String?,
    onEventKey: (String) -> Unit,
    pastOpen: Boolean,
    onPastOpen: () -> Unit,
    onChangeNap: (Int, Int) -> Unit,
    onResetNap: (Int) -> Unit,
    onChangeWw: (Int, Int) -> Unit,
    onResetWw: (Int) -> Unit,
) {
    val events = SleepEngine.timeline(schedule, placements.map { it.feed })
    val nowAbs = now.nextAbs - now.minutesLeft
    fun isPast(event: SleepEngine.DayEvent): Boolean {
        // Начало текущего сна остаётся рядом с его окончанием и редактором.
        if (event.kind == SleepEngine.EventKind.NAP_START &&
            now.phase == SleepEngine.Phase.NAP && event.index == now.napIndex) return false
        if (event.kind == SleepEngine.EventKind.WAKE && now.phase == SleepEngine.Phase.WINDOW && now.windowIndex == 0) return false
        if (event.kind == SleepEngine.EventKind.NAP_END && now.phase == SleepEngine.Phase.WINDOW && event.index == now.windowIndex) return false
        if (event.kind == SleepEngine.EventKind.BEDTIME && now.phase == SleepEngine.Phase.NIGHT) return false
        return event.time < nowAbs
    }
    val past = events.filter { isPast(it) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (past.isNotEmpty()) {
            Text(
                (if (pastOpen) "▾ " else "▸ ") + "Ранее по плану · ${past.size}",
                fontSize = 13.sp, color = Subtitle,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onPastOpen)
                    .heightIn(min = 48.dp).padding(vertical = 12.dp),
            )
        }
        val visible = if (pastOpen) events else events.filter {
            !isPast(it) || (it.kind == SleepEngine.EventKind.WAKE && eventKey == "ww0") ||
                (it.kind == SleepEngine.EventKind.NAP_END && eventKey == "ww${it.index}") ||
                (it.kind == SleepEngine.EventKind.NAP_START && eventKey == "nap${it.index}")
        }
        visible.forEach { event ->
            val nap = schedule.naps.firstOrNull { it.index == event.index }
            val windowIndex = when (event.kind) {
                SleepEngine.EventKind.WAKE -> 0
                SleepEngine.EventKind.NAP_END -> event.index
                else -> null
            }
            val key = when (event.kind) {
                SleepEngine.EventKind.NAP_START -> "nap${event.index}"
                SleepEngine.EventKind.WAKE, SleepEngine.EventKind.NAP_END -> "ww$windowIndex"
                else -> null
            }
            val active = when (event.kind) {
                SleepEngine.EventKind.NAP_START -> now.phase == SleepEngine.Phase.NAP && now.napIndex == event.index
                SleepEngine.EventKind.WAKE, SleepEngine.EventKind.NAP_END -> now.phase == SleepEngine.Phase.WINDOW && now.windowIndex == windowIndex
                SleepEngine.EventKind.BEDTIME -> now.phase == SleepEngine.Phase.NIGHT
                else -> false
            }
            val title = when (event.kind) {
                SleepEngine.EventKind.WAKE -> "Подъём"
                SleepEngine.EventKind.NAP_START -> "Сон ${event.index}"
                SleepEngine.EventKind.NAP_END -> "Пробуждение"
                SleepEngine.EventKind.FEED -> "Кормление"
                SleepEngine.EventKind.BEDTIME -> "Ночной сон"
            }
            val detail = when (event.kind) {
                SleepEngine.EventKind.NAP_START -> SleepEngine.fmtDurHuman(nap!!.duration)
                SleepEngine.EventKind.WAKE, SleepEngine.EventKind.NAP_END -> {
                    val window = schedule.windows[windowIndex!!]
                    "Бодрствование " + SleepEngine.fmtDurHuman(window.effective)
                }
                SleepEngine.EventKind.BEDTIME -> "До ${SleepEngine.fmtClock(schedule.wake)} · ${SleepEngine.fmtDurHuman(schedule.nightSleep)}"
                SleepEngine.EventKind.FEED -> {
                    val overlap = schedule.naps.firstOrNull { feedTimeInNap(event.time, it) }
                    if (overlap != null) "Во время сна ${overlap.index} · до конца ${SleepEngine.fmtDurHuman(overlap.end - event.time)}" else null
                }
            }
            val tint = when (event.kind) {
                SleepEngine.EventKind.FEED -> FeedTint
                SleepEngine.EventKind.NAP_START, SleepEngine.EventKind.BEDTIME -> NightTint
                else -> Subtitle
            }
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(if (active) ActiveBg else Color.Transparent)
                    .then(if (key != null) Modifier.clickable { onEventKey(key) } else Modifier)
                    .heightIn(min = 52.dp).padding(horizontal = 8.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(SleepEngine.fmtClock(event.time), fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold, color = tint,
                    modifier = Modifier.width((62 * LocalDensity.current.fontScale).dp), softWrap = false)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = tint)
                    if (detail != null) Text(detail, fontSize = 12.sp, color = Subtitle)
                }
                if (key != null) Icon(resPainter(R.drawable.ic_edit), contentDescription = null, tint = Subtitle, modifier = Modifier.size(16.dp))
            }
            if (key != null && eventKey == key) {
                if (event.kind == SleepEngine.EventKind.NAP_START) {
                    NapEditor(nap!!, { onChangeNap(nap.index - 1, it) }, { onResetNap(nap.index - 1) })
                } else if (windowIndex != null) {
                    WindowEditor(age, schedule.windows[windowIndex],
                        { onChangeWw(windowIndex, it) }, { onResetWw(windowIndex) })
                }
            }
        }
    }
}

private fun feedTimeInNap(time: Int, nap: SleepEngine.ScheduledNap): Boolean =
    time >= nap.start && time < nap.end

// ══════════════════════════ РЕДАКТОРЫ ═════════════════════════════════════

@Composable
private fun NapEditor(nap: SleepEngine.ScheduledNap, onChange: (Int) -> Unit, onReset: () -> Unit) {
    val canLess = nap.duration > 20
    val canMore = nap.duration < 240
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MutedBg)
            .padding(12.dp),
    ) {
        Text("Длительность сна ${nap.index}", fontSize = 13.sp, color = DarkScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            RollingText(
                text = SleepEngine.fmtDurHuman(nap.duration),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = DarkScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (nap.offset != 0) {
                    "${if (nap.offset < 0) "−" else "+"}${abs(nap.offset)} мин от плана"
                } else "по плану",
                fontSize = 13.sp,
                color = if (nap.offset != 0) Amber else DarkScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
            FactPill(
                "−5 мин", { onChange(-5) }, enabled = canLess,
                a11y = "Уменьшить длительность сна ${nap.index} на 5 минут",
            )
            FactPill(
                "+5 мин", { onChange(5) }, enabled = canMore,
                a11y = "Увеличить длительность сна ${nap.index} на 5 минут",
            )
            if (nap.offset != 0) {
                FactPill("Вернуть план", onReset, a11y = "Вернуть плановую длительность сна ${nap.index}")
            }
        }
        Text(
            "Последующие события пересчитаны",
            fontSize = 11.sp,
            color = DarkScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        SleepEngine.napWarning(nap)?.let {
            Text("⚠ $it", fontSize = 12.sp, color = Amber, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun WindowEditor(
    age: SleepEngine.Age,
    window: SleepEngine.Window,
    onChange: (Int) -> Unit,
    onReset: () -> Unit,
) {
    val regime = window.regime
    val canLess = window.effective > age.hardMin
    val canMore = window.effective < age.hardMax
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MutedBg)
            .padding(12.dp),
    ) {
        Text("Бодрствование ${window.index + 1}", fontSize = 13.sp, color = DarkScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            RollingText(
                text = SleepEngine.fmtDurHuman(window.effective),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = DarkScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (window.hasManual) {
                    "${if (window.manual < 0) "−" else "+"}${abs(window.manual)} мин от плана"
                } else "по плану",
                fontSize = 13.sp,
                color = if (window.hasManual) Amber else DarkScheme.onSurfaceVariant,
            )
        }
        if (window.hasAuto) {
            Text(
                "авто ${if (window.comp < 0) "−" else "+"}${abs(window.comp)} мин после " +
                    "${if (window.comp < 0) "короткого" else "длинного"} сна",
                fontSize = 12.sp, color = Subtitle, modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (regime != 0) {
            Text(
                "режим регресса −${abs(regime)} мин",
                fontSize = 12.sp, color = Amber, modifier = Modifier.padding(top = 2.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
            FactPill(
                "−5 мин", { onChange(-5) }, enabled = canLess,
                a11y = "Уменьшить бодрствование ${window.index + 1} на 5 минут",
            )
            FactPill(
                "+5 мин", { onChange(5) }, enabled = canMore,
                a11y = "Увеличить бодрствование ${window.index + 1} на 5 минут",
            )
            if (window.hasManual) {
                FactPill("Вернуть план", onReset, a11y = "Вернуть плановое бодрствование ${window.index + 1}")
            }
        }
        Text(
            "Последующие события пересчитаны",
            fontSize = 11.sp,
            color = DarkScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        SleepEngine.wwWarning(age, window)?.let {
            Text("⚠ $it", fontSize = 12.sp, color = Amber, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// ══════════════════════════ ИТОГИ ═════════════════════════════════════════

@Composable
private fun TotalsBlock(
    schedule: SleepEngine.Schedule,
    age: SleepEngine.Age,
    open: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkScheme.surface)
            .padding(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 2.dp),
        ) {
            Text("По плану", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Subtitle)
            Spacer(Modifier.weight(1f))
            Text(
                "${if (open) "▾" else "▸"}",
                fontSize = 13.sp, color = Subtitle,
            )
        }
        Text(
            "сон днём ${SleepEngine.fmtDurHuman(schedule.daySleep)} · " +
                "ночью ${SleepEngine.fmtDurHuman(schedule.nightSleep)} · " +
                "всего ${SleepEngine.fmtDurHuman(schedule.totalSleep)}",
            fontSize = 14.sp,
            color = DarkScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (open) {
            Spacer(Modifier.height(6.dp))
            SummaryRow("Сон днём", SleepEngine.fmtDurHuman(schedule.daySleep), age.dayNormShort)
            SummaryRow(
                "Сон ночью",
                SleepEngine.fmtDurHuman(schedule.nightSleep),
                age.nightNormShort,
                warn = schedule.nightSleep !in age.nightMin..age.nightMax,
            )
            SummaryRow("Всего сна", SleepEngine.fmtDurHuman(schedule.totalSleep), age.totalNormShort, highlight = true)
            SummaryRow("Бодрствование", SleepEngine.fmtDurHuman(schedule.wakeTotal), null)
            schedule.bedtimeHint?.let {
                Text("⚠ $it", fontSize = 12.sp, color = Amber, modifier = Modifier.padding(top = 4.dp))
            }
            Text(
                "Это план по нормам сна, а не журнал фактического сна.",
                fontSize = 11.sp,
                color = DarkScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    norm: String?,
    highlight: Boolean = false,
    warn: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(label, fontSize = 14.sp, color = DarkScheme.onSurface)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = when {
                warn -> Amber
                highlight -> DarkScheme.primary
                else -> DarkScheme.onSurface
            },
        )
        if (norm != null) {
            Text(
                "  ·  $norm",
                fontSize = 12.sp,
                color = DarkScheme.onSurfaceVariant,
            )
        }
    }
}

// ══════════════════════════ НАСТРОЙКИ ═════════════════════════════════════

@Composable
private fun SettingsScreen(
    age: SleepEngine.Age,
    plan: SleepEngine.NapPlan,
    feeds: List<Int>,
    regress: Boolean,
    version: String,
    editingFeed: Int,
    onEditingFeed: (Int) -> Unit,
    onSelectAge: (SleepEngine.Age) -> Unit,
    onSelectPlan: (SleepEngine.NapPlan) -> Unit,
    onToggleRegress: () -> Unit,
    onChangeFeed: (Int, Int) -> Unit,
    onResetFeeds: () -> Unit,
    onNewDay: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconPressable(icon = R.drawable.ic_back, label = "Назад", a11y = "Вернуться к расписанию", onClick = onBack)
            Text("Настройки", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        SettingsSection("Схема дня") {
            Text("Возраст", fontSize = 13.sp, color = DarkScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (option in SleepEngine.AGES) {
                    Chip(option.label, option === age, "Возраст ${option.label}") { onSelectAge(option) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Дневных снов", fontSize = 13.sp, color = DarkScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (option in age.plans) {
                    Chip(option.name, option === plan, option.name) { onSelectPlan(option) }
                }
            }
            Text(
                "Смена возраста или схемы сбрасывает правки текущего дня (подъём и кормления сохраняются).",
                fontSize = 11.sp,
                color = DarkScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        SettingsSection("Кормления") {
            for (f in feeds) {
                val editing = editingFeed == f
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = true, onClickLabel = "Изменить кормление") {
                            onEditingFeed(if (editing) -1 else f)
                        }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                ) {
                    Icon(resPainter(R.drawable.ic_bottle), contentDescription = null, tint = FeedTint, modifier = Modifier.size(16.dp))
                    Text(
                        SleepEngine.fmtClock(f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(if (editing) "Свернуть" else "Изменить", fontSize = 13.sp, color = ActiveBorder)
                }
                if (editing) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MutedBg)
                            .padding(10.dp),
                    ) {
                        FactPill("−1 ч", { onChangeFeed(f, -60) }, a11y = "Сдвинуть кормление на час раньше")
                        FactPill("−5 мин", { onChangeFeed(f, -5) }, a11y = "Сдвинуть кормление на 5 минут раньше")
                        FactPill("+5 мин", { onChangeFeed(f, 5) }, a11y = "Сдвинуть кормление на 5 минут позже")
                        FactPill("+1 ч", { onChangeFeed(f, 60) }, a11y = "Сдвинуть кормление на час позже")
                    }
                }
            }
            FactPill("Сбросить кормления", onResetFeeds, modifier = Modifier.padding(top = 4.dp), a11y = "Вернуть кормления по умолчанию")
        }

        SettingsSection("Режим сокращения окон") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Регресс сна", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Все окна бодрствования −10 мин, отбой раньше. Обычно нужен 3–7 дней.",
                        fontSize = 12.sp,
                        color = DarkScheme.onSurfaceVariant,
                    )
                }
                Chip(if (regress) "вкл" else "выкл", regress, if (regress) "Режим включён" else "Режим выключен", onToggleRegress)
            }
        }

        SettingsSection("День") {
            Text(
                "Сохранённые правки снов и окон действуют, пока их не сбросишь. «Начать новый день» сбрасывает правки, но сохраняет схему, подъём, кормления и режим.",
                fontSize = 12.sp,
                color = DarkScheme.onSurfaceVariant,
            )
            FactPill("Начать новый день", onNewDay, modifier = Modifier.padding(top = 6.dp), a11y = "Сбросить правки снов и окон")
        }

        SettingsSection("О приложении") {
            Text(
                "Приложение считает план по нормам сна для возраста: окна бодрствования, " +
                    "дневные сны, ночь и кормления. Все времена — прогноз, а не журнал " +
                    "фактического сна. Все правки кратны 5 минутам, день всегда сходится " +
                    "к 24 часам.",
                fontSize = 12.sp,
                color = DarkScheme.onSurfaceVariant,
            )
            Text("BaSleep v$version", fontSize = 12.sp, color = Subtitle, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkScheme.surface)
            .padding(14.dp),
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Subtitle)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, a11y: String, onClick: () -> Unit) {
    Pressable(
        shape = RoundedCornerShape(50),
        container = if (selected) DarkScheme.primary else DarkScheme.surfaceVariant,
        onClick = onClick,
        a11y = "$a11y${if (selected) ", выбрано" else ""}",
    ) {
        Text(
            (if (selected) "✓ " else "") + text,
            color = if (selected) DarkScheme.onPrimary else ChipIdleFg,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

// ══════════════════════════ НОЧНОЙ ЭКРАН ══════════════════════════════════

@Composable
private fun NightScreen(s: SleepEngine.Schedule, nowMin: Int, onExit: () -> Unit) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        val prev = window?.attributes?.screenBrightness ?: -1f
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window?.attributes = window?.attributes?.apply { screenBrightness = 0.10f }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.attributes = window?.attributes?.apply { screenBrightness = prev }
        }
    }
    val info = remember(s, nowMin) { SleepEngine.nowInfo(s, nowMin) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(onClickLabel = "Выйти из ночного экрана") { onExit() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${info.title} · до ${info.nextGenitive}",
                fontSize = 16.sp,
                color = Color(0xFF8A93A8),
            )
            RollingText(
                text = SleepEngine.fmtDurHuman(info.minutesLeft),
                fontSize = 80.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE8EBF4),
                modifier = Modifier.padding(vertical = 12.dp),
            )
            Text(
                "сейчас ${SleepEngine.fmtClock(nowMin)} · подъём ${SleepEngine.fmtClock(s.wake)}",
                fontSize = 14.sp,
                color = Color(0xFF8A93A8),
            )
            Text(
                "коснись экрана, чтобы выйти",
                fontSize = 12.sp,
                color = Color(0xFF4A5164),
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}

// ══════════════════════════ ОБЩИЕ КОМПОНЕНТЫ ══════════════════════════════

/** Текст, значение которого «прокатывается» при изменении. */
@Composable
private fun RollingText(
    text: String,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    color: Color,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically(spring(stiffness = 320f)) { it / 3 } + fadeIn()) togetherWith
                (slideOutVertically(spring(stiffness = 320f)) { -it / 3 } + fadeOut())
        },
        label = "roll",
        modifier = modifier,
    ) { value ->
        Text(value, fontSize = fontSize, fontWeight = fontWeight, color = color)
    }
}

/** Кнопка: пружина + подсветка + рябь + хаптика; зона нажатия от 48 dp. */
@Composable
private fun Pressable(
    shape: Shape,
    container: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    a11y: String? = null,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.90f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 400f),
        label = "pressScale",
    )
    val bg by animateColorAsState(
        targetValue = if (pressed && enabled) lerp(container, Color.White, 0.18f) else container,
        animationSpec = spring(stiffness = 400f),
        label = "pressBg",
    )
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.35f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(bg)
            .semantics {
                role = Role.Button
                if (a11y != null) contentDescription = a11y
            }
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                enabled = enabled,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** Круглая кнопка-иконка. */
@Composable
private fun IconPressable(icon: Int, label: String, a11y: String, onClick: () -> Unit) {
    Pressable(
        shape = RoundedCornerShape(50),
        container = DarkScheme.surfaceVariant,
        modifier = Modifier.size(48.dp),
        onClick = onClick,
        a11y = a11y,
    ) {
        Icon(resPainter(icon), contentDescription = null, tint = DarkScheme.onSurface, modifier = Modifier.size(20.dp))
    }
}

/** Кнопка нижней панели. */
@Composable
private fun BarButton(icon: Int, text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Pressable(
        shape = RoundedCornerShape(14.dp),
        container = DarkScheme.surfaceVariant,
        modifier = modifier,
        onClick = onClick,
        a11y = text,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 12.dp),
        ) {
            Icon(resPainter(icon), contentDescription = null, tint = DarkScheme.primary, modifier = Modifier.size(18.dp))
            Text(text, fontSize = 14.sp, color = DarkScheme.onSurface)
        }
    }
}

@Composable
private fun StepButton(
    text: String,
    modifier: Modifier = Modifier,
    a11y: String? = null,
    onClick: () -> Unit,
) {
    Pressable(
        shape = RoundedCornerShape(14.dp),
        container = DarkScheme.surfaceVariant,
        modifier = modifier,
        onClick = onClick,
        a11y = a11y ?: text,
    ) {
        Text(
            text,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = DarkScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        )
    }
}

/** Мини-кнопка шага ±5 и мелких действий. */
@Composable
private fun FactPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    a11y: String? = null,
) {
    Pressable(
        shape = RoundedCornerShape(50),
        container = DarkScheme.surfaceVariant,
        modifier = modifier,
        enabled = enabled,
        a11y = a11y ?: text,
        onClick = onClick,
    ) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = DarkScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
