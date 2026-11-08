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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.platform.LocalFocusManager
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

private fun clock(time: Long): String = java.time.Instant.ofEpochMilli(time)
    .atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun App(prefs: SharedPreferences, version: String) {
    var session by remember { mutableStateOf(Prefs.loadLive(prefs)) }
    var history by remember { mutableStateOf(Prefs.loadHistory(prefs)) }
    var ageIndex by remember { mutableIntStateOf(prefs.getInt("age", 0).coerceIn(SleepEngine.AGES.indices)) }
    var planIndex by remember { mutableIntStateOf(prefs.getInt("plan", 0)) }
    var regress by remember { mutableStateOf(Prefs.loadRegress(prefs)) }
    var feeds by remember { mutableStateOf(Prefs.loadFeeds(prefs)) }
    var childName by remember {
        mutableStateOf(prefs.getString("childName", null)?.takeIf { it.isNotBlank() } ?: "Малыш")
    }
    var settings by rememberSaveable { mutableStateOf(false) }
    var editor by rememberSaveable { mutableStateOf(false) }
    var manualEntry by rememberSaveable { mutableStateOf(false) }
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    var planOpen by rememberSaveable { mutableStateOf(false) }
    var dim by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var lastTap by remember { mutableLongStateOf(0L) }
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) now = System.currentTimeMillis()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            val sec = Calendar.getInstance().get(Calendar.SECOND)
            delay(((60 - sec) * 1000L + 50L).coerceAtLeast(200L))
        }
    }
    BackHandler(settings || dim || editor || planOpen || historyOpen || manualEntry) {
        when {
            editor -> editor = false
            manualEntry -> manualEntry = false
            settings -> settings = false
            historyOpen -> historyOpen = false
            dim -> dim = false
            else -> planOpen = false
        }
    }
    val age = SleepEngine.AGES[ageIndex]
    val plan = age.plans[planIndex.coerceIn(age.plans.indices)]
    val state = session.state
    val forecasts = remember(state, age, plan, regress) { LiveTracker.forecasts(state, age, plan, regress) }
    fun updateWidget() {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, WidgetNext::class.java))
        if (ids.isNotEmpty()) context.sendBroadcast(Intent(context, WidgetNext::class.java)
            .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids))
    }
    /** Сохранение сессии и журнала одной операцией. */
    fun store(next: Prefs.Session, newHistory: List<History.Interval>, label: String) {
        if (Prefs.saveLive(prefs, next) && Prefs.saveHistory(prefs, newHistory)) {
            session = next; history = newHistory; message = label; error = null
            editor = false; manualEntry = false; now = System.currentTimeMillis()
            focus.clearFocus()
            updateWidget()
        } else error = "Не удалось сохранить. Попробуй ещё раз."
    }
    /**
     * Записать действие. [at] — null (время телефона) или выбранное вручную
     * время. Возвращает текст ошибки (для редактора времени) или null.
     */
    fun act(change: (LiveTracker.State, Long) -> LiveTracker.State, at: Long? = null): String? {
        val tap = android.os.SystemClock.elapsedRealtime()
        if (tap - lastTap < 900) return null
        lastTap = tap
        val time = at ?: System.currentTimeMillis()
        return try {
            val prev = state
            val next = change(prev, time)
            val logged = History.transition(prev, next, time)
            store(Prefs.Session(next, prev, logged = logged.size),
                History.keepRecent(history + logged, time), "Записано ${clock(next.since)}")
            error
        } catch (e: IllegalArgumentException) { e.message ?: "Проверь время телефона" }
    }
    fun undo() {
        val u = session.undo ?: return
        store(Prefs.Session(u), History.dropLast(history, session.logged), "Действие отменено")
    }
    fun resetDay() {
        if (state.phase == LiveTracker.Phase.READY) return
        store(Prefs.Session(LiveTracker.State(), state), History.removeDay(history, state.dayStart), "День сброшен")
    }
    fun config(newAge: Int = ageIndex, newPlan: Int = planIndex, newRegress: Boolean = regress, newFeeds: List<Int> = feeds) {
        if (prefs.edit().putInt("age", newAge).putInt("plan", newPlan)
                .putBoolean("regress", newRegress).putString("feeds", newFeeds.joinToString(",")).commit()) {
            ageIndex = newAge; planIndex = newPlan; regress = newRegress; feeds = newFeeds; updateWidget()
        } else error = "Не удалось сохранить настройки"
    }
    val night = state.phase == LiveTracker.Phase.NIGHT || state.phase == LiveTracker.Phase.NIGHT_AWAKE
    val localHour = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).hour
    val nightAvailable = localHour >= 21 || localHour < 6
    val nightSleepNow = state.phase == LiveTracker.Phase.AWAKE && nightAvailable
    val accent by animateColorAsState(
        if (state.phase == LiveTracker.Phase.NAP || night || nightSleepNow) NightTint else DarkScheme.primary,
        label = "stateColor",
    )
    DisposableEffect(dim) {
        val window = (context as? Activity)?.window
        val before = window?.attributes?.screenBrightness ?: -1f
        if (dim) window?.attributes = window?.attributes?.apply { screenBrightness = .08f }
        onDispose { if (dim) window?.attributes = window?.attributes?.apply { screenBrightness = before } }
    }
    MaterialTheme(colorScheme = DarkScheme, typography = Typography(
        bodyLarge = TextStyle(fontFamily = Manrope), bodyMedium = TextStyle(fontFamily = Manrope),
        labelLarge = TextStyle(fontFamily = Manrope), titleLarge = TextStyle(fontFamily = Manrope))) {
        Surface(Modifier.fillMaxSize(), color = if (dim) Color.Black else DarkScheme.background) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (settings) "Настройки" else if (historyOpen) "История" else childName, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (settings) "BaSleep $version"
                            else if (historyOpen) "Завершённые сны по дням"
                            else "${age.label} · ${plan.name}",
                            fontSize = 13.sp, color = Subtitle,
                        )
                    }
                    IconPressable(if (settings || historyOpen) R.drawable.ic_back else R.drawable.ic_settings,
                        "Настройки",
                        if (settings || historyOpen) "Вернуться к трекеру" else "Открыть настройки") {
                        if (historyOpen) historyOpen = false else { settings = !settings; editor = false }
                    }
                }
                if (error != null) Text(error!!, color = Amber, fontSize = 14.sp)
                if (session.corrupt) Text(
                    "Не удалось прочитать предыдущее состояние — начали с чистого листа",
                    color = Amber, fontSize = 13.sp,
                )
                if (settings) {
                    Text("Имя ребёнка", fontSize = 16.sp)
                    var nameDraft by remember(childName) { mutableStateOf(childName) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            nameDraft,
                            { nameDraft = it.take(20) },
                            label = { Text("Как зовут малыша") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        FactPill("Сохранить", {
                            childName = nameDraft.trim().ifEmpty { "Малыш" }
                            prefs.edit().putString("childName", childName).apply()
                            updateWidget()
                        })
                    }
                    Text("Схема для прогноза", fontSize = 16.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SleepEngine.AGES.forEachIndexed { i, a ->
                            FactPill((if (i == ageIndex) "✓ " else "") + a.label, { config(newAge = i, newPlan = 0) })
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        age.plans.forEachIndexed { i, p -> FactPill((if (p == plan) "✓ " else "") + p.name, { config(newPlan = i) }) }
                    }
                    FactPill(if (regress) "Сокращение окон: включено" else "Сокращение окон: выключено", { config(newRegress = !regress) })
                    Text("Схема меняет только прогноз. Записанное время остаётся.", color = Subtitle, fontSize = 13.sp)
                    Text("Кормления · ориентиры", fontSize = 16.sp)
                    feeds.forEachIndexed { i, f ->
                        var open by remember(f, i) { mutableStateOf(false) }
                        TextButton(onClick = { open = !open }) { Text("${SleepEngine.fmtClock(f)} · Изменить") }
                        if (open) ClockEditor(f / 60, f % 60, onCancel = { open = false }) { h, m ->
                            config(newFeeds = feeds.toMutableList().also { it[i] = h * 60 + m }.sorted()); open = false; null
                        }
                    }
                    if (state.phase == LiveTracker.Phase.AWAKE || state.phase == LiveTracker.Phase.NAP) {
                        FactPill("Начать ночной сон", { settings = false; act(LiveTracker::startNight) })
                    }
                    TextButton(onClick = { resetDay() }) {
                        Text(if (state.phase == LiveTracker.Phase.READY) "Запись уже чистая" else "Начать новый день (сброс записи)")
                    }
                    TextButton(onClick = { dim = !dim; settings = false }) { Text(if (dim) "Обычная яркость" else "Тусклый экран") }
                    TextButton(onClick = {
                        val text = buildString {
                            append("$childName · ").append(phaseTitle(state.phase))
                            if (state.phase != LiveTracker.Phase.READY) append(" с ").append(clock(state.since))
                            forecasts.forEach { append("\n").append(clock(it.at)).append(" · ").append(it.title).append(" (прогноз)") }
                        }
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Поделиться планом"))
                    }) { Text("Поделиться планом") }
                } else if (historyOpen) {
                    val dayList = remember(history) { History.days(history) }
                    if (dayList.isEmpty()) Text(
                        "Пока пусто. Завершённые сны появятся здесь после первого «Проснулся».",
                        color = Subtitle, fontSize = 14.sp,
                    )
                    dayList.forEach { d ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(DarkScheme.surface).padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(dayLabel(d.wakeAt), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("Подъём ${clock(d.wakeAt)}", fontSize = 13.sp, color = Subtitle)
                            d.naps.forEachIndexed { i, nap ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Сон ${i + 1}", fontSize = 13.sp, color = NightTint)
                                    Text("${clock(nap.start)}–${clock(nap.end)} · ${SleepEngine.fmtDurHuman(nap.minutes)}",
                                        fontSize = 13.sp, color = Subtitle)
                                }
                            }
                            d.nightWakes.forEach { w ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Проснулся ночью", fontSize = 13.sp, color = Amber)
                                    Text("${clock(w.start)}–${clock(w.end)} · ${SleepEngine.fmtDurHuman(w.minutes)}",
                                        fontSize = 13.sp, color = Subtitle)
                                }
                            }
                            d.night?.let { n ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Ночь", fontSize = 13.sp, color = NightTint)
                                    Text("${clock(n.start)}–${clock(n.end)} · ${SleepEngine.fmtDurHuman(n.minutes)}",
                                        fontSize = 13.sp, color = Subtitle)
                                }
                            }
                            NormLine("Дневной сон", d.daySleepMin, age.dayMin, age.dayMax, age.dayNormShort)
                            NormLine("Ночь", d.nightSleepMin, age.nightMin, age.nightMax, age.nightNormShort)
                            NormLine("Всего сна", d.totalSleepMin, age.totalMin, age.totalMax, age.totalNormShort)
                            d.dayAwakeMin?.let {
                                Text("Бодрствование днём ${SleepEngine.fmtDurHuman(it)}", fontSize = 12.sp, color = Subtitle)
                            }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(DarkScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            Icon(resPainter(if (state.phase == LiveTracker.Phase.NAP || state.phase == LiveTracker.Phase.NIGHT) R.drawable.ic_moon else R.drawable.ic_sun),
                                contentDescription = null, tint = accent, modifier = Modifier.size(26.dp))
                        }
                        AnimatedContent(state.phase, label = "phase") { phase -> Text(phaseTitle(phase), fontSize = 24.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center) }
                        if (state.phase != LiveTracker.Phase.READY) {
                            val duration = ((now - state.since).coerceAtLeast(0) / LiveTracker.MINUTE).toInt()
                            RollingText(SleepEngine.fmtDurHuman(duration), 44.sp, FontWeight.Medium, DarkScheme.onSurface)
                            Text("С ${clock(state.since)}" + daySuffix(state.since, now), color = Subtitle, fontSize = 14.sp)
                            phaseHint(state, age, forecasts.firstOrNull(), now)?.let { hint ->
                                Spacer(Modifier.height(2.dp))
                                Text(hint.text, color = if (hint.over) Amber else Subtitle,
                                    fontSize = 14.sp, textAlign = TextAlign.Center)
                                NormBar(hint.fraction, if (hint.over) Amber else accent, Modifier.width(220.dp))
                            }
                        } else Text("Нажми, когда начнётся ваш день", fontSize = 14.sp, color = Subtitle)
                    }
                    if (state.phase == LiveTracker.Phase.READY) {
                        var baseOpen by rememberSaveable { mutableStateOf(false) }
                        val base = remember(age, plan) {
                            SleepEngine.build(age, plan, prefs.getInt("wake", 9 * 60))
                        }
                        TextButton(onClick = { baseOpen = !baseOpen }) {
                            Text(if (baseOpen) "Скрыть базовое расписание" else "Базовое расписание · ${plan.name}")
                        }
                        if (baseOpen) {
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DarkScheme.surface).padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Подъём ${SleepEngine.fmtClock(base.wake)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                base.naps.forEach { nap ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Сон ${nap.index}", fontSize = 13.sp, color = NightTint)
                                        Text(
                                            "${SleepEngine.fmtClock(nap.start)}–${SleepEngine.fmtClock(nap.end)} · ${SleepEngine.fmtDurHuman(nap.duration)}",
                                            fontSize = 13.sp, color = Subtitle,
                                        )
                                    }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Ночь", fontSize = 13.sp, color = NightTint)
                                    Text(
                                        "${SleepEngine.fmtClock(base.bedtime)}–${SleepEngine.fmtClock(base.wake)} · ${SleepEngine.fmtDurHuman(base.nightSleep)}",
                                        fontSize = 13.sp, color = Subtitle,
                                    )
                                }
                                Text(
                                    "После первого «Проснулся» прогноз построится от фактического времени.",
                                    fontSize = 11.sp, color = Subtitle,
                                )
                            }
                        }
                    }
                    val morning = localHour >= 6 && localHour < 21
                    val startDayNow = state.phase == LiveTracker.Phase.NIGHT && morning
                    val action = when {
                        startDayNow -> "Начать день"
                        nightSleepNow -> "Ночной сон"
                        state.phase == LiveTracker.Phase.READY ||
                            state.phase == LiveTracker.Phase.NAP ||
                            state.phase == LiveTracker.Phase.NIGHT -> "Проснулся"
                        else -> "Уснул"
                    }
                    val mainAction: (LiveTracker.State, Long) -> LiveTracker.State = when {
                        startDayNow -> LiveTracker::startDay
                        nightSleepNow -> LiveTracker::startNight
                        else -> LiveTracker::toggle
                    }
                    Pressable(RoundedCornerShape(24.dp), accent, Modifier.fillMaxWidth().heightIn(min = 72.dp),
                        a11y = when {
                            startDayNow -> "Начать день — записать пробуждение"
                            nightSleepNow -> "Ночной сон — записать уход в ночь"
                            else -> "$action — записать время телефона"
                        },
                        onClick = { act(mainAction) }) {
                        Text(action, fontSize = 24.sp, color = DarkScheme.onPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(18.dp))
                    }
                    TextButton(onClick = { manualEntry = !manualEntry; editor = false }) {
                        Text("«$action» в другое время…")
                    }
                    if (state.phase != LiveTracker.Phase.READY) {
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { editor = !editor }) { Text("Исправить время") }
                            if (night && startDayNow) {
                                TextButton(onClick = { act(LiveTracker::toggle) }) { Text("Проснулся ночью") }
                            }
                            if (state.phase == LiveTracker.Phase.NAP) {
                                TextButton(onClick = { act(LiveTracker::startNight) }) {
                                    Text("Ночной сон (сохранит начало)", color = NightTint)
                                }
                            }
                        }
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { resetDay() }) { Text("Начать заново") }
                        }
                    }
                    val longRunning = state.phase != LiveTracker.Phase.READY &&
                        now - state.since >= 16L * 60 * 60_000
                    if (longRunning) {
                        FactPill(
                            "Начать новый день (сброс записи)",
                            { resetDay() },
                            Modifier.fillMaxWidth(),
                            a11y = "Сбросить запись дня и начать заново",
                        )
                    }
                    if (editor) ClockEditor(
                        java.time.Instant.ofEpochMilli(state.since).atZone(java.time.ZoneId.systemDefault()).hour,
                        java.time.Instant.ofEpochMilli(state.since).atZone(java.time.ZoneId.systemDefault()).minute,
                        title = "Начало текущего события",
                        onCancel = { editor = false }) { h, m ->
                        try {
                            val current = System.currentTimeMillis()
                            val at = LiveTracker.clockTime(h, m, current, java.time.ZoneId.systemDefault())
                            val next = LiveTracker.correct(state, at, current)
                            store(Prefs.Session(next, state), history, "Время исправлено: ${clock(at)}"); null
                        } catch (e: IllegalArgumentException) { e.message ?: "Проверь время" }
                    }
                    if (manualEntry) ClockEditor(
                        java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).hour,
                        java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).minute,
                        title = "Записать «$action» с указанием времени",
                        onCancel = { manualEntry = false }) { h, m ->
                        try {
                            val at = LiveTracker.clockTime(h, m, System.currentTimeMillis(), java.time.ZoneId.systemDefault())
                            act(mainAction, at)
                        } catch (e: IllegalArgumentException) { e.message ?: "Проверь время" }
                    }
                    if (night && state.phase == LiveTracker.Phase.NIGHT_AWAKE) {
                        FactPill("Начать день", { act(LiveTracker::startDay) }, Modifier.fillMaxWidth())
                    }
                    if (session.undo != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(message ?: "Последнее действие", fontSize = 13.sp, color = Subtitle, modifier = Modifier.weight(1f))
                            TextButton(onClick = { undo() }) { Text("Отменить") }
                        }
                    }
                    val next = forecasts.firstOrNull()
                    if (next != null) {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(DarkScheme.surface).padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Ориентир", fontSize = 12.sp, color = Subtitle)
                                    Text(next.title, fontSize = 16.sp)
                                }
                                Text(clock(next.at), fontSize = 22.sp, color = accent)
                            }
                            if (next.at < now) Text("Ориентир прошёл. Запиши событие, когда оно случится.", fontSize = 12.sp, color = Subtitle)
                            val feed = LiveTracker.nextFeed(feeds, now, java.time.ZoneId.systemDefault())
                            if (feed != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Кормление · ориентир", fontSize = 13.sp, color = FeedTint, modifier = Modifier.weight(1f))
                                Text(clock(feed), fontSize = 14.sp, color = FeedTint, softWrap = false)
                            }
                        }
                    } else if (state.phase != LiveTracker.Phase.READY) {
                        LiveTracker.nextFeed(feeds, now, java.time.ZoneId.systemDefault())?.let {
                            Text("Кормление · ${clock(it)} · ориентир", color = FeedTint, fontSize = 14.sp)
                        }
                    }
                    if (state.dayStart > 0L) {
                        val t = History.totals(history, state, now)
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(DarkScheme.surface).padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Итог дня", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text("нормы · ${age.label}", fontSize = 11.sp, color = Subtitle)
                            }
                            NormLine("Дневной сон", t.daySleepMin, age.dayMin, age.dayMax, age.dayNormShort, live = true)
                            NormLine("Ночь", t.nightSleepMin, age.nightMin, age.nightMax, age.nightNormShort, live = true)
                            NormLine("Всего сна", t.totalSleepMin, age.totalMin, age.totalMax, age.totalNormShort, live = true)
                            t.dayAwakeMin?.let {
                                Text("Бодрствование днём ${SleepEngine.fmtDurHuman(it)}", fontSize = 12.sp, color = Subtitle)
                            }
                        }
                    }
                    if (forecasts.size > 1) {
                        TextButton(onClick = { planOpen = !planOpen }) { Text(if (planOpen) "Скрыть план" else "План дальше") }
                        if (planOpen) forecasts.forEach { f ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(clock(f.at), color = accent, fontSize = 15.sp)
                                Text(f.title, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    TextButton(onClick = { historyOpen = true }) { Text("История по дням") }
                    if (dim) TextButton(onClick = { dim = false }) { Text("Обычная яркость") }
                }
            }
        }
    }
}

private fun phaseTitle(phase: LiveTracker.Phase): String = when (phase) {
    LiveTracker.Phase.READY -> "Начнём день"
    LiveTracker.Phase.AWAKE -> "Бодрствует"
    LiveTracker.Phase.NAP -> "Спит"
    LiveTracker.Phase.NIGHT -> "Ночной сон"
    LiveTracker.Phase.NIGHT_AWAKE -> "Проснулся ночью"
}
private fun daySuffix(at: Long, now: Long): String {
    val zone = java.time.ZoneId.systemDefault()
    val days = java.time.temporal.ChronoUnit.DAYS.between(
        java.time.Instant.ofEpochMilli(at).atZone(zone).toLocalDate(),
        java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate())
    return when { days == 1L -> " · вчера"; days > 1 -> " · ранее"; else -> "" }
}

/** Заголовок дня в истории: «Сегодня · чт, 6 ноября». */
private fun dayLabel(dayStart: Long): String {
    val zone = java.time.ZoneId.systemDefault()
    val z = java.time.Instant.ofEpochMilli(dayStart).atZone(zone)
    val today = java.time.LocalDate.now(zone)
    val formatted = z.format(java.time.format.DateTimeFormatter.ofPattern("EE, d MMMM", java.util.Locale("ru")))
    return when (z.toLocalDate()) {
        today -> "Сегодня · $formatted"
        today.minusDays(1) -> "Вчера · $formatted"
        else -> formatted
    }
}

/** Подсказка под таймером: норма текущей фазы и сколько осталось. */
private data class PhaseHint(val text: String, val fraction: Float, val over: Boolean)

private fun phaseHint(
    state: LiveTracker.State,
    age: SleepEngine.Age,
    next: LiveTracker.Forecast?,
    now: Long,
): PhaseHint? {
    val duration = ((now - state.since).coerceAtLeast(0) / LiveTracker.MINUTE).toInt()
    return when (state.phase) {
        LiveTracker.Phase.AWAKE -> {
            val base = "Окно ${SleepEngine.fmtDurHuman(duration)} · норма ${SleepEngine.fmtDur(age.wwMin)}–${SleepEngine.fmtDur(age.wwMax)}"
            if (next == null) {
                PhaseHint(base, duration.toFloat() / age.hardMax, duration > age.hardMax)
            } else {
                val left = ((next.at - now) / LiveTracker.MINUTE).toInt()
                val total = ((next.at - state.since).coerceAtLeast(LiveTracker.MINUTE) / LiveTracker.MINUTE).toInt()
                if (left >= 0) PhaseHint("$base · осталось ~${SleepEngine.fmtDurHuman(left)}",
                    duration.toFloat() / total, duration > age.hardMax)
                else PhaseHint("$base · окно вышло на ${SleepEngine.fmtDurHuman(-left)}", 1f, true)
            }
        }
        LiveTracker.Phase.NAP -> {
            if (next == null) PhaseHint("Спит ${SleepEngine.fmtDurHuman(duration)}", 0f, false)
            else {
                val left = ((next.at - now) / LiveTracker.MINUTE).toInt()
                val total = ((next.at - state.since).coerceAtLeast(LiveTracker.MINUTE) / LiveTracker.MINUTE).toInt()
                if (left >= 0) PhaseHint(
                    "Ориентир сна ${SleepEngine.fmtDurHuman(total)} · осталось ~${SleepEngine.fmtDurHuman(left)}",
                    duration.toFloat() / total, false)
                else PhaseHint("Спит дольше ориентира на ${SleepEngine.fmtDurHuman(-left)}", 1f, true)
            }
        }
        LiveTracker.Phase.NIGHT ->
            PhaseHint("Ночь ${SleepEngine.fmtDurHuman(duration)} · ${age.nightNormShort}",
                duration.toFloat() / age.nightMax, duration > age.nightMax)
        LiveTracker.Phase.NIGHT_AWAKE ->
            PhaseHint("Ночное бодрствование ${SleepEngine.fmtDurHuman(duration)}", 0f, false)
        LiveTracker.Phase.READY -> null
    }
}

/** Тонкий прогресс-бар под таймером. */
@Composable
private fun NormBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.height(6.dp).clip(RoundedCornerShape(50)).background(DarkScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp)
            .clip(RoundedCornerShape(50)).background(color))
    }
}

/** Строка «показатель — значение · норма», янтарным при выходе за норму.
 *  [live] — текущий незавершённый день: ниже нормы ещё нормально, янтарный
 *  только при превышении максимума. */
@Composable
private fun NormLine(label: String, valueMin: Int, min: Int, max: Int, normShort: String, live: Boolean = false) {
    val out = if (live) valueMin > max else valueMin !in min..max
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = Subtitle)
        Text("${SleepEngine.fmtDurHuman(valueMin)} · $normShort",
            fontSize = 13.sp, color = if (out) Amber else FeedTint)
    }
}

@Composable
private fun ClockEditor(initialHour: Int, initialMinute: Int, title: String? = null, onCancel: () -> Unit, onSave: (Int, Int) -> String?) {
    var h by rememberSaveable(initialHour, initialMinute) { mutableStateOf("%02d".format(initialHour)) }
    var m by rememberSaveable(initialHour, initialMinute) { mutableStateOf("%02d".format(initialMinute)) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DarkScheme.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (title != null) Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(h, { h = it.filter(Char::isDigit).take(2) }, label = { Text("Часы") },
                modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            OutlinedTextField(m, { m = it.filter(Char::isDigit).take(2) }, label = { Text("Минуты") },
                modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
        }
        if (error != null) Text(error!!, color = Amber, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FactPill("Сохранить", {
                val hour = h.toIntOrNull(); val min = m.toIntOrNull()
                error = if (hour == null || min == null || hour !in 0..23 || min !in 0..59) "Укажи часы 00–23 и минуты 00–59" else onSave(hour, min)
            })
            TextButton(onClick = onCancel) { Text("Закрыть") }
        }
        Text("Время позже текущего считаем вчерашним.", fontSize = 12.sp, color = Subtitle)
    }
}

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
