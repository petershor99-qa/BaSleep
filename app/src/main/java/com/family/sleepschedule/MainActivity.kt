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
    var ageIndex by remember { mutableIntStateOf(prefs.getInt("age", 0).coerceIn(SleepEngine.AGES.indices)) }
    var planIndex by remember { mutableIntStateOf(prefs.getInt("plan", 0)) }
    var regress by remember { mutableStateOf(Prefs.loadRegress(prefs)) }
    var feeds by remember { mutableStateOf(Prefs.loadFeeds(prefs)) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var editor by rememberSaveable { mutableStateOf(false) }
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
    BackHandler(settings || dim || editor || planOpen) {
        when { editor -> editor = false; settings -> settings = false; dim -> dim = false; else -> planOpen = false }
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
    fun store(next: Prefs.Session, label: String) {
        if (Prefs.saveLive(prefs, next)) {
            session = next; message = label; error = null; editor = false; now = System.currentTimeMillis()
            focus.clearFocus()
            updateWidget()
        } else error = "Не удалось сохранить. Попробуй ещё раз."
    }
    fun act(change: (LiveTracker.State, Long) -> LiveTracker.State) {
        val tap = android.os.SystemClock.elapsedRealtime()
        if (tap - lastTap < 900) return
        lastTap = tap
        try {
            val next = change(state, System.currentTimeMillis())
            store(Prefs.Session(next, state), "Записано ${clock(next.since)}")
        } catch (e: IllegalArgumentException) { error = e.message ?: "Проверь время телефона" }
    }
    fun resetDay() {
        if (state.phase == LiveTracker.Phase.READY) return
        store(Prefs.Session(LiveTracker.State(), state), "День сброшен")
    }
    fun config(newAge: Int = ageIndex, newPlan: Int = planIndex, newRegress: Boolean = regress, newFeeds: List<Int> = feeds) {
        if (prefs.edit().putInt("age", newAge).putInt("plan", newPlan)
                .putBoolean("regress", newRegress).putString("feeds", newFeeds.joinToString(",")).commit()) {
            ageIndex = newAge; planIndex = newPlan; regress = newRegress; feeds = newFeeds; updateWidget()
        } else error = "Не удалось сохранить настройки"
    }
    val night = state.phase == LiveTracker.Phase.NIGHT || state.phase == LiveTracker.Phase.NIGHT_AWAKE
    val accent by animateColorAsState(if (state.phase == LiveTracker.Phase.NAP || night) NightTint else DarkScheme.primary, label = "stateColor")
    val localHour = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).hour
    val nightAvailable = localHour >= 21 || localHour < 6
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
                        Text(if (settings) "Настройки" else "Дима", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (settings) "BaSleep $version" else "${age.label} · ${plan.name}", fontSize = 13.sp, color = Subtitle)
                    }
                    IconPressable(if (settings) R.drawable.ic_back else R.drawable.ic_sliders,
                        "Настройки", if (settings) "Вернуться к трекеру" else "Открыть настройки") { settings = !settings; editor = false }
                }
                if (error != null) Text(error!!, color = Amber, fontSize = 14.sp)
                if (session.corrupt) Text(
                    "Не удалось прочитать предыдущее состояние — начали с чистого листа",
                    color = Amber, fontSize = 13.sp,
                )
                if (settings) {
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
                            append("Дима · ").append(phaseTitle(state.phase))
                            if (state.phase != LiveTracker.Phase.READY) append(" с ").append(clock(state.since))
                            forecasts.forEach { append("\n").append(clock(it.at)).append(" · ").append(it.title).append(" (прогноз)") }
                        }
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Поделиться планом"))
                    }) { Text("Поделиться планом") }
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
                        } else Text("Нажми, когда начнётся ваш день", fontSize = 14.sp, color = Subtitle)
                    }
                    val morning = localHour >= 6 && localHour < 21
                    val startDayNow = state.phase == LiveTracker.Phase.NIGHT && morning
                    val action = when {
                        startDayNow -> "Начать день"
                        state.phase == LiveTracker.Phase.READY ||
                            state.phase == LiveTracker.Phase.NAP ||
                            state.phase == LiveTracker.Phase.NIGHT -> "Проснулся"
                        else -> "Уснул"
                    }
                    Pressable(RoundedCornerShape(24.dp), accent, Modifier.fillMaxWidth().heightIn(min = 72.dp),
                        a11y = if (startDayNow) "Начать день — записать пробуждение" else "$action — записать время телефона",
                        onClick = { if (startDayNow) act(LiveTracker::startDay) else act(LiveTracker::toggle) }) {
                        Text(action, fontSize = 24.sp, color = DarkScheme.onPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(18.dp))
                    }
                    if (state.phase != LiveTracker.Phase.READY) {
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { editor = !editor }) { Text("Исправить время") }
                            if (night && startDayNow) {
                                TextButton(onClick = { act(LiveTracker::toggle) }) { Text("Проснулся ночью") }
                            }
                            if (!night && nightAvailable) {
                                TextButton(onClick = { act(LiveTracker::startNight) }) { Text("Ночной сон", color = NightTint) }
                            }
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
                        onCancel = { editor = false }) { h, m ->
                        try {
                            val current = System.currentTimeMillis()
                            val at = LiveTracker.clockTime(h, m, current, java.time.ZoneId.systemDefault())
                            val next = LiveTracker.correct(state, at, current)
                            store(Prefs.Session(next, state), "Время исправлено: ${clock(at)}"); null
                        } catch (e: IllegalArgumentException) { e.message ?: "Проверь время" }
                    }
                    if (night && state.phase == LiveTracker.Phase.NIGHT_AWAKE) {
                        FactPill("Начать день", { act(LiveTracker::startDay) }, Modifier.fillMaxWidth())
                    }
                    if (session.undo != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(message ?: "Последнее действие", fontSize = 13.sp, color = Subtitle, modifier = Modifier.weight(1f))
                            TextButton(onClick = { session.undo?.let { store(Prefs.Session(it), "Действие отменено") } }) { Text("Отменить") }
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
                    if (forecasts.size > 1) {
                        TextButton(onClick = { planOpen = !planOpen }) { Text(if (planOpen) "Скрыть план" else "План дальше") }
                        if (planOpen) forecasts.forEach { f ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(clock(f.at), color = accent, fontSize = 15.sp)
                                Text(f.title, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            }
                        }
                    }
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

@Composable
private fun ClockEditor(initialHour: Int, initialMinute: Int, onCancel: () -> Unit, onSave: (Int, Int) -> String?) {
    var h by rememberSaveable(initialHour, initialMinute) { mutableStateOf("%02d".format(initialHour)) }
    var m by rememberSaveable(initialHour, initialMinute) { mutableStateOf("%02d".format(initialMinute)) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DarkScheme.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
