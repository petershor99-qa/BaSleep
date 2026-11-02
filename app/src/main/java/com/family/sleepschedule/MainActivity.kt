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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** Manrope (OFL, Google Fonts) — статические начертания, кириллица+латиница. */
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
private val BadgeEarlyBg = Color(0x2EFFB59E)
private val BadgeEarlyFg = Color(0xFFFFC9B5)
private val BadgeLateBg = Color(0x2E9FD8B4)
private val BadgeLateFg = Color(0xFFB9E4C9)

private fun nowMinutesOfDay(): Int {
    val cal = Calendar.getInstance()
    return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
}

private fun zeroAdj(plan: SleepEngine.NapPlan): SleepEngine.Adjustments =
    SleepEngine.Adjustments(List(plan.naps) { 0 }, List(plan.naps + 1) { 0 })

private fun fmtLeft(minutes: Int): String =
    if (minutes < 60) "$minutes мин" else SleepEngine.fmtDur(minutes)

@Composable
fun App(prefs: SharedPreferences, version: String) {
    var ageIdx by remember { mutableIntStateOf(prefs.getInt("age", 0)) }
    var planIdx by remember { mutableIntStateOf(prefs.getInt("plan", 0)) }
    var wake by remember { mutableIntStateOf(prefs.getInt("wake", 9 * 60)) }
    var feeds by remember { mutableStateOf(Prefs.loadFeeds(prefs)) }
    var regress by remember { mutableStateOf(Prefs.loadRegress(prefs)) }
    var nightMode by remember { mutableStateOf(false) }

    // Системный «Назад» в ночном режиме выходит из него, а не из приложения.
    BackHandler(enabled = nightMode) { nightMode = false }

    val age = SleepEngine.AGES[ageIdx.coerceIn(SleepEngine.AGES.indices)]
    val plan = age.plans[planIdx.coerceIn(age.plans.indices)]

    // Правки дня грузятся один раз для стартовой схемы; смена схемы — новый чистый план.
    var adj by remember { mutableStateOf(Prefs.loadAdj(prefs, plan)) }
    val effAdj = remember(adj, regress) { Prefs.withRegress(adj, regress) }
    val schedule = remember(plan, wake, effAdj) { SleepEngine.build(age, plan, wake, effAdj) }

    // Тикаем текущее время для подсветки «сейчас».
    var nowMin by remember { mutableIntStateOf(nowMinutesOfDay()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowMin = nowMinutesOfDay()
        }
    }
    val now = remember(schedule, nowMin) { SleepEngine.nowInfo(schedule, nowMin) }

    // Сохраняем всё и обновляем виджет.
    val context = LocalContext.current
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

    val changeNap: (Int, Int) -> Unit = { i, d ->
        adj = adj.copy(napOffsets = adj.napOffsets.toMutableList().also {
            it[i] = SleepEngine.clampNap(plan, i, it[i] + d)
        })
    }
    val resetNap: (Int) -> Unit = { i ->
        adj = adj.copy(napOffsets = adj.napOffsets.toMutableList().also { it[i] = 0 })
    }
    val changeWw: (Int, Int) -> Unit = { i, d ->
        adj = adj.copy(wwOffsets = adj.wwOffsets.toMutableList().also { it[i] = it[i] + d })
    }
    val resetWw: (Int) -> Unit = { i ->
        adj = adj.copy(wwOffsets = adj.wwOffsets.toMutableList().also { it[i] = 0 })
    }
    val resetAll: () -> Unit = {
        adj = SleepEngine.Adjustments(List(plan.naps) { 0 }, List(plan.naps + 1) { 0 })
    }
    val changeFeed: (Int, Int) -> Unit = { i, d ->
        feeds = feeds.toMutableList()
            .also { it[i] = (((it[i] + d) % 1440) + 1440) % 1440 }
            .sorted()
    }
    val resetFeeds: () -> Unit = { feeds = SleepEngine.DEFAULT_FEEDS }

    MaterialTheme(
        colorScheme = DarkScheme,
        typography = Typography(bodyLarge = TextStyle(fontFamily = Manrope)),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = DarkScheme.background) {
            if (nightMode) {
                NightScreen(schedule, nowMin, onExit = { nightMode = false })
                return@Surface
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Режим сна",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkScheme.onBackground,
                )
                Text(
                    "младенец 3–5 месяцев · расписание от времени пробуждения",
                    fontSize = 13.sp,
                    color = Subtitle,
                )
                Spacer(Modifier.height(4.dp))

                SectionCard("Время пробуждения") {
                    RollingText(
                        text = SleepEngine.fmtClock(wake),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkScheme.primary,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StepButton("−1 ч", Modifier.weight(1f)) { wake = (wake - 60).coerceIn(300, 720) }
                        StepButton("−5 мин", Modifier.weight(1f)) { wake = (wake - 5).coerceIn(300, 720) }
                        StepButton("+5 мин", Modifier.weight(1f)) { wake = (wake + 5).coerceIn(300, 720) }
                        StepButton("+1 ч", Modifier.weight(1f)) { wake = (wake + 60).coerceIn(300, 720) }
                    }
                }

                SectionCard("Расписание") {
                    ScheduleRows(
                        s = schedule,
                        age = age,
                        now = now,
                        feeds = feeds,
                        onNapDelta = changeNap,
                        onNapReset = resetNap,
                        onWwDelta = changeWw,
                        onWwReset = resetWw,
                        onResetAll = resetAll,
                        regress = regress,
                        onRegress = { regress = !regress },
                        onNightMode = { nightMode = true },
                        onExport = {
                            val text = SleepEngine.exportText(schedule, age, plan, feeds)
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(send, "Отправить расписание"))
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "нормы: ${age.wwRangeLabel} · ${age.dayNormLabel} · ${age.nightNormLabel} · ${age.totalNormLabel}",
                        fontSize = 12.sp,
                        color = DarkScheme.onSurfaceVariant,
                    )
                }

                SectionCard("Итоги дня") {
                    SummaryRow("😴 Сон днём", SleepEngine.fmtDur(schedule.daySleep), age.dayNormShort)
                    SummaryRow(
                        "😴 Сон ночью",
                        SleepEngine.fmtDur(schedule.nightSleep),
                        age.nightNormShort,
                        warn = schedule.nightSleep !in age.nightMin..age.nightMax,
                    )
                    SummaryRow(
                        "😴 Всего сна",
                        SleepEngine.fmtDur(schedule.totalSleep),
                        age.totalNormShort,
                        highlight = true,
                    )
                    SummaryRow("⏱ Бодрствование", SleepEngine.fmtDur(schedule.wakeTotal), null)
                }

                SectionCard("Настройки") {
                    MiniLabel("Возраст")
                    ChipRow(
                        options = SleepEngine.AGES,
                        selected = age,
                        label = { it.label },
                    ) {
                        ageIdx = SleepEngine.AGES.indexOf(it)
                        planIdx = 0
                        adj = zeroAdj(it.plans.first())
                    }
                    Spacer(Modifier.height(14.dp))
                    MiniLabel("Дневных снов")
                    ChipRow(
                        options = age.plans,
                        selected = plan,
                        label = { it.name },
                    ) {
                        planIdx = age.plans.indexOf(it)
                        adj = zeroAdj(it)
                    }
                    Spacer(Modifier.height(14.dp))
                    MiniLabel("Кормления")
                    for ((i, f) in feeds.withIndex()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(vertical = 2.dp),
                        ) {
                            Text(
                                "🍼 ${SleepEngine.fmtClock(f)}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DarkScheme.onSurface,
                            )
                            Spacer(Modifier.weight(1f))
                            FactPill("−5") { changeFeed(i, -5) }
                            FactPill("+5") { changeFeed(i, +5) }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    FactPill("сбросить кормления", resetFeeds)
                }

                Text(
                    "Ориентир по нормам, а не расписание по минутам. Проснулся раньше — " +
                        "поправь его сон кнопками ±5 мин: следующее бодрствование подстроится само, " +
                        "остаток дня пересчитается. Все времена кратны 5 минутам.",
                    fontSize = 12.sp,
                    color = DarkScheme.onSurfaceVariant,
                )
                Text(
                    "v$version",
                    fontSize = 11.sp,
                    color = DarkScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Просит виджет обновиться после изменения состояния. */
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

/** Текст, значение которого «прокатывается» при изменении — анимация видна всегда. */
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

/**
 * Кнопка: пружинное сжатие + подсветка + рябь + хаптика.
 */
@Composable
private fun Pressable(
    shape: Shape,
    container: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = 400f,
        ),
        label = "pressScale",
    )
    val bg by animateColorAsState(
        targetValue = if (pressed) lerp(container, Color.White, 0.18f) else container,
        animationSpec = spring(stiffness = 400f),
        label = "pressBg",
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
    ) {
        content()
    }
}

@Composable
private fun MiniLabel(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = DarkScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (option in options) {
            val isSelected = option == selected
            val container by animateColorAsState(
                targetValue = if (isSelected) DarkScheme.primary else DarkScheme.surfaceVariant,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "chipColor",
            )
            Pressable(
                shape = RoundedCornerShape(50),
                container = container,
                onClick = { onSelect(option) },
            ) {
                Text(
                    label(option),
                    color = if (isSelected) DarkScheme.onPrimary else ChipIdleFg,
                    fontSize = 16.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun StepButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Pressable(
        shape = RoundedCornerShape(14.dp),
        container = DarkScheme.surfaceVariant,
        modifier = modifier,
        onClick = onClick,
    ) {
        Text(
            text,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            color = DarkScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
        )
    }
}

/** Кнопка шага ±5 и мелких действий. */
@Composable
private fun FactPill(text: String, onClick: () -> Unit) {
    Pressable(
        shape = RoundedCornerShape(50),
        container = DarkScheme.surfaceVariant,
        onClick = onClick,
    ) {
        Text(
            text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = DarkScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}

/** Бейдж текущей правки; тап — вернуть план. */
@Composable
private fun OffsetBadge(offset: Int, onReset: () -> Unit) {
    Pressable(
        shape = RoundedCornerShape(50),
        container = if (offset < 0) BadgeEarlyBg else BadgeLateBg,
        onClick = onReset,
    ) {
        Text(
            "${if (offset < 0) "−" else "+"}${abs(offset)} мин · по плану",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (offset < 0) BadgeEarlyFg else BadgeLateFg,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(DarkScheme.surface)
            .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Text(
            title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = DarkScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun ScheduleRows(
    s: SleepEngine.Schedule,
    age: SleepEngine.Age,
    now: SleepEngine.NowInfo,
    feeds: List<Int>,
    onNapDelta: (Int, Int) -> Unit,
    onNapReset: (Int) -> Unit,
    onWwDelta: (Int, Int) -> Unit,
    onWwReset: (Int) -> Unit,
    onResetAll: () -> Unit,
    regress: Boolean,
    onRegress: () -> Unit,
    onNightMode: () -> Unit,
    onExport: () -> Unit,
) {
    val placements = remember(s, feeds) { SleepEngine.placeFeeds(s, feeds) }

    NowBanner(now)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    ) {
        Pressable(
            shape = RoundedCornerShape(50),
            container = if (regress) AmberBg else DarkScheme.surfaceVariant,
            onClick = onRegress,
        ) {
            Text(
                if (regress) "Регресс сна: вкл" else "Регресс сна",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (regress) Amber else DarkScheme.onSurface,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        if (s.hasFacts) {
            FactPill("сбросить всё", onResetAll)
        }
    }
    if (regress) {
        Text(
            "все бодрствования −10 мин, отбой раньше (режим 3–7 дней)",
            fontSize = 12.sp,
            color = Amber,
            modifier = Modifier.padding(bottom = 2.dp),
        )
    }

    for ((i, nap) in s.naps.withIndex()) {
        val w = s.windows[i]
        placements
            .filter {
                (it.kind == SleepEngine.FeedKind.IN_WINDOW && it.slot == nap.index) ||
                    (nap.index == 1 && it.kind == SleepEngine.FeedKind.AT_WAKE)
            }
            .forEach {
                FeedLine(
                    if (nap.index == 1 && it.kind == SleepEngine.FeedKind.AT_WAKE)
                        "🍼 ${SleepEngine.fmtClock(it.feed)} — сразу после пробуждения"
                    else "🍼 кормление ${SleepEngine.fmtClock(it.feed)}",
                )
            }
        WwBlock(
            age = age,
            window = w,
            active = now.phase == SleepEngine.Phase.WINDOW && now.windowIndex == i,
            onDelta = { d -> onWwDelta(i, d) },
            onReset = { onWwReset(i) },
        )
        NapBlock(
            nap = nap,
            active = now.phase == SleepEngine.Phase.NAP && now.napIndex == nap.index,
            collisions = placements.filter {
                it.kind == SleepEngine.FeedKind.IN_NAP && it.slot == nap.index
            },
            onDelta = { d -> onNapDelta(i, d) },
            onReset = { onNapReset(i) },
        )
    }

    placements
        .filter { it.kind == SleepEngine.FeedKind.IN_WINDOW && it.slot == s.naps.size + 1 }
        .forEach { FeedLine("🍼 кормление ${SleepEngine.fmtClock(it.feed)}") }

    WwBlock(
        age = age,
        window = s.windows.last(),
        label = "Бодрствование ${s.naps.size + 1} — в ночь",
        active = now.phase == SleepEngine.Phase.WINDOW && now.windowIndex == s.windows.lastIndex,
        onDelta = { d -> onWwDelta(s.windows.lastIndex, d) },
        onReset = { onWwReset(s.windows.lastIndex) },
    )
    NightBlock(
        s = s,
        placements = placements,
        active = now.phase == SleepEngine.Phase.NIGHT,
    )

    if (s.bedtimeHint != null) {
        Text(
            "⚠️ ${s.bedtimeHint}",
            fontSize = 12.sp,
            color = Amber,
            modifier = Modifier.padding(top = 6.dp),
        )
    }

    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StepButton("📤 Отправить день", Modifier.weight(1f), onExport)
        StepButton("🌙 Ночной экран", Modifier.weight(1f), onNightMode)
    }
}

/** Баннер «сейчас»: текущий этап и время до следующего события. */
@Composable
private fun NowBanner(now: SleepEngine.NowInfo) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ActiveBg)
            .border(1.dp, ActiveBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text("▶ Сейчас: ${now.title}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DarkScheme.onSurface)
        Spacer(Modifier.weight(1f))
        RollingText(
            text = "${now.nextLabel} через ${fmtLeft(now.minutesLeft)}",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = ActiveBorder,
        )
    }
}

/** Блок бодрствования: карточка с длительностью и правками ±5. */
@Composable
private fun WwBlock(
    age: SleepEngine.Age,
    window: SleepEngine.Window,
    active: Boolean,
    label: String = "Бодрствование ${window.index + 1}",
    onDelta: (Int) -> Unit,
    onReset: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) ActiveBg else DarkScheme.surface)
            .border(
                1.dp,
                if (active) ActiveBorder else CardBorder,
                RoundedCornerShape(14.dp),
            )
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DarkScheme.onSurface)
            Spacer(Modifier.weight(1f))
            RollingText(
                text = SleepEngine.fmtDur(window.effective),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = if (active) ActiveBorder else DarkScheme.primary,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            FactPill("−5") { onDelta(-5) }
            FactPill("+5") { onDelta(5) }
            if (window.manual != 0) {
                OffsetBadge(window.manual, onReset)
            }
        }
        if (window.comp != 0) {
            Text(
                "авто ${if (window.comp < 0) "−" else "+"}${abs(window.comp)} мин после " +
                    "${if (window.comp < 0) "короткого" else "длинного"} сна",
                fontSize = 12.sp,
                color = DarkScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        SleepEngine.wwWarning(age, window)?.let {
            Text("⚠️ $it", fontSize = 12.sp, color = Amber, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Карточка одного дневного сна. */
@Composable
private fun NapBlock(
    nap: SleepEngine.ScheduledNap,
    active: Boolean,
    collisions: List<SleepEngine.FeedPlacement>,
    onDelta: (Int) -> Unit,
    onReset: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) lerp(NapBg, ActiveBorder, 0.14f) else NapBg)
            .border(
                1.dp,
                if (active) ActiveBorder else Color.Transparent,
                RoundedCornerShape(14.dp),
            )
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "🌙  Сон ${nap.index}",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = DarkScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                RollingText(
                    text = "${SleepEngine.fmtClock(nap.start)} – ${SleepEngine.fmtClock(nap.end)}",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkScheme.primary,
                )
                RollingText(
                    text = SleepEngine.fmtDur(nap.duration),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = DarkScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            FactPill("−5 мин") { onDelta(-5) }
            FactPill("+5 мин") { onDelta(5) }
            if (nap.offset != 0) {
                OffsetBadge(nap.offset, onReset)
            }
        }
        SleepEngine.napWarning(nap)?.let {
            Text("⚠️ $it", fontSize = 12.sp, color = Amber, modifier = Modifier.padding(top = 6.dp))
        }
        collisions.forEach {
            Text(
                "⚠️ кормление ${SleepEngine.fmtClock(it.feed)} попадает на этот сон",
                fontSize = 12.sp,
                color = Amber,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Карточка ночи с ночными кормлениями. */
@Composable
private fun NightBlock(
    s: SleepEngine.Schedule,
    placements: List<SleepEngine.FeedPlacement>,
    active: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(NightBg)
            .border(
                1.dp,
                if (active) NightTint else NightBorder,
                RoundedCornerShape(14.dp),
            )
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "🌃  Ночь",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = DarkScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                RollingText(
                    text = "${SleepEngine.fmtClock(s.bedtime)} – ${SleepEngine.fmtClock(s.wake)}",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NightTint,
                )
                RollingText(
                    text = SleepEngine.fmtDur(s.nightSleep),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = DarkScheme.onSurfaceVariant,
                )
            }
        }
        val nightFeeds = placements.filter { it.kind == SleepEngine.FeedKind.NIGHT }
        if (nightFeeds.isNotEmpty()) {
            Text(
                "🍼 ночью (не будим): " +
                    nightFeeds.joinToString(" · ") { SleepEngine.fmtClock(it.feed) },
                fontSize = 13.sp,
                color = FeedTint,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        val eveningFeeds = placements.filter { it.kind == SleepEngine.FeedKind.AFTER_BEDTIME }
        if (eveningFeeds.isNotEmpty()) {
            Text(
                "⚠️ кормление " + eveningFeeds.joinToString(" · ") { SleepEngine.fmtClock(it.feed) } +
                    " попало после отбоя — покормить до укладывания",
                fontSize = 13.sp,
                color = Amber,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Ночной экран: одна большая цифра — сколько до следующего события. */
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
            .clickable { onExit() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${info.title} · до ${info.nextGenitive}",
                fontSize = 16.sp,
                color = Color(0xFF8A93A8),
            )
            RollingText(
                text = if (info.minutesLeft < 60) "${info.minutesLeft} мин" else SleepEngine.fmtDur(info.minutesLeft),
                fontSize = 88.sp,
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

@Composable
private fun FeedLine(text: String, warn: Boolean = false) {
    Text(
        text,
        fontSize = 13.sp,
        color = if (warn) Amber else FeedTint,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/** Строка итогов: подпись слева, значение и норма справа. */
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
            .padding(vertical = 4.dp),
    ) {
        Text(label, fontSize = 15.sp, color = DarkScheme.onSurface)
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            RollingText(
                text = value,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    warn -> Amber
                    highlight -> DarkScheme.primary
                    else -> DarkScheme.onSurface
                },
            )
            if (norm != null) {
                Text(norm, fontSize = 12.sp, color = DarkScheme.onSurfaceVariant)
            }
        }
    }
}
