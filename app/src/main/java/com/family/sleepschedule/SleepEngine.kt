package com.family.sleepschedule

/**
 * Движок расписания сна для возраста 3–5 месяцев.
 *
 * Модель правок (v2):
 *  - `manual` — только ручные правки пользователя; всегда в жёстком коридоре
 *    благодаря [sanitizeAdj] (клэмп на входе, «скрытого запаса» не накапливается);
 *  - `comp` — авто-компенсация после факта сна (сон короче → следующее окно
 *    короче, до ±[COMP_MAX]);
 *  - `regime` — режим «Регресс сна» (все окна −[REGRESS_STEP]), хранится
 *    отдельно от ручных правок и в [Adjustments] не попадает.
 *
 * Инварианты: все времена кратны 5 минутам; Σбодрствований + Σснов + ночь =
 * 1440 мин (ночь = остаток суток); окна в жёстком коридоре возраста.
 */
object SleepEngine {

    enum class EventKind { WAKE, NAP_START, NAP_END, FEED, BEDTIME }

    /** Единая хронология от подъёма до следующего подъёма, включая ночь. */
    data class DayEvent(val time: Int, val kind: EventKind, val index: Int = 0)

    fun timeline(s: Schedule, feeds: List<Int>): List<DayEvent> = buildList {
        add(DayEvent(s.wake, EventKind.WAKE))
        s.naps.forEach { nap ->
            add(DayEvent(nap.start, EventKind.NAP_START, nap.index))
            add(DayEvent(nap.end, EventKind.NAP_END, nap.index))
        }
        add(DayEvent(s.bedtime, EventKind.BEDTIME))
        val wakeTod = ((s.wake % 1440) + 1440) % 1440
        feeds.forEachIndexed { index, feed ->
            val relative = ((feed - wakeTod) % 1440 + 1440) % 1440
            add(DayEvent(s.wake + relative, EventKind.FEED, index))
        }
    }.sortedWith(compareBy<DayEvent> { it.time }.thenBy {
        when (it.kind) {
            EventKind.WAKE, EventKind.NAP_END -> 0
            EventKind.NAP_START, EventKind.BEDTIME -> 1
            EventKind.FEED -> 2
        }
    })

    /** Схема на фиксированное число дневных снов. */
    data class NapPlan(
        val name: String,
        val naps: Int,
        /** Размер naps+1. */
        val wakeWindows: List<Int>,
        /** Размер naps. */
        val napDurations: List<Int>,
    )

    /** Возрастной пресет: схемы + коридоры норм для самопроверки. */
    data class Age(
        val months: Int,
        val label: String,
        val wwMin: Int,
        val wwMax: Int,
        val hardMin: Int,
        val hardMax: Int,
        val dayMin: Int,
        val dayMax: Int,
        val nightMin: Int,
        val nightMax: Int,
        val totalMin: Int,
        val totalMax: Int,
        val plans: List<NapPlan>,
    ) {
        val wwRangeLabel: String get() = "окна ${fmtDur(wwMin)}–${fmtDur(wwMax)}"
        val dayNormLabel: String get() = "день ${fmtHours(dayMin)}–${fmtHours(dayMax)} ч"
        val nightNormLabel: String get() = "ночь ${fmtHours(nightMin)}–${fmtHours(nightMax)} ч"
        val totalNormLabel: String get() = "всего ${fmtHours(totalMin)}–${fmtHours(totalMax)} ч"
        val dayNormShort: String get() = "норма ${fmtHours(dayMin)}–${fmtHours(dayMax)} ч"
        val nightNormShort: String get() = "норма ${fmtHours(nightMin)}–${fmtHours(nightMax)} ч"
        val totalNormShort: String get() = "норма ${fmtHours(totalMin)}–${fmtHours(totalMax)} ч"
    }

    /** Ручные правки дня: факты снов и ручные правки окон бодрствования. */
    data class Adjustments(
        val napOffsets: List<Int> = emptyList(),
        val wwOffsets: List<Int> = emptyList(),
    ) {
        val hasAny: Boolean get() = napOffsets.any { it != 0 } || wwOffsets.any { it != 0 }
    }

    /** Одно окно бодрствования: база + ручная правка + авто-компенсация + режим. */
    data class Window(
        val index: Int,
        val base: Int,
        val manual: Int,
        val comp: Int,
        val regime: Int,
    ) {
        val effective: Int get() = base + manual + comp + regime
        val hasManual: Boolean get() = manual != 0
        val hasAuto: Boolean get() = comp != 0
    }

    data class ScheduledNap(
        val index: Int,
        val start: Int,
        val end: Int,
        val duration: Int,
        val offset: Int,
        val window: Window,
    ) {
        val wwBefore: Int get() = window.effective
    }

    data class Schedule(
        val wake: Int,
        val naps: List<ScheduledNap>,
        val windows: List<Window>,
        val bedtime: Int,
        val daySleep: Int,
        val nightSleep: Int,
        val totalSleep: Int,
        val regress: Boolean,
    ) {
        val wwBeforeNight: Int get() = windows.last().effective

        /** Есть ли ручные правки (режим регресса — не ручная правка). */
        val hasFacts: Boolean
            get() = naps.any { it.offset != 0 } || windows.any { it.hasManual }

        /** Суммарное бодрствование за сутки. */
        val wakeTotal: Int get() = windows.sumOf { it.effective }

        val bedtimeTod: Int get() = ((bedtime % 1440) + 1440) % 1440

        val bedtimeHint: String?
            get() = when (bedtimeTod) {
                in 241..1199 ->
                    "Отбой выходит раньше 20:00 — дневного сна не хватило. " +
                        "Можно дать последний сон дольше или уложить ночь раньше."
                in 1411..1439 ->
                    "Отбой позже 23:30 — дневного сна многовато. " +
                        "Стоит укоротить последний сон."
                else -> null
            }
    }

    const val COMP_MAX = 15
    const val REGRESS_STEP = 10

    /** Кормление в пределах этого времени до подъёма считается «до подъёма». */
    const val BEFORE_WAKE_MAX = 90

    /** Схема кормлений семьи: 00:00, 04:30, 09:00, 12:00, 15:00, 18:00, 21:00. */
    val DEFAULT_FEEDS: List<Int> = listOf(0, 270, 540, 720, 900, 1080, 1260)

    val AGES: List<Age> = listOf(
        Age(
            months = 3,
            label = "3 мес",
            wwMin = 75, wwMax = 105,
            hardMin = 70, hardMax = 110,
            dayMin = 4 * 60, dayMax = 5 * 60,
            nightMin = 11 * 60, nightMax = 12 * 60,
            totalMin = 15 * 60, totalMax = 17 * 60,
            plans = listOf(
                NapPlan("4 сна", 4, listOf(85, 95, 100, 105, 80), listOf(80, 80, 70, 60)),
                NapPlan("5 снов", 5, listOf(80, 75, 80, 105, 75, 75), listOf(75, 70, 60, 30, 30)),
            ),
        ),
        Age(
            months = 4,
            label = "4 мес",
            wwMin = 100, wwMax = 150,
            hardMin = 95, hardMax = 160,
            dayMin = 210, dayMax = 270,
            nightMin = 600, nightMax = 720,
            totalMin = 12 * 60, totalMax = 16 * 60,
            plans = listOf(
                NapPlan("4 сна", 4, listOf(100, 105, 115, 125, 135), listOf(80, 70, 55, 40)),
                NapPlan("3 сна", 3, listOf(120, 125, 135, 150), listOf(90, 75, 60)),
            ),
        ),
        Age(
            months = 5,
            label = "5 мес",
            wwMin = 130, wwMax = 165,
            hardMin = 125, hardMax = 175,
            dayMin = 150, dayMax = 270,
            nightMin = 600, nightMax = 720,
            totalMin = 12 * 60, totalMax = 16 * 60,
            plans = listOf(
                NapPlan("3 сна", 3, listOf(130, 140, 150, 165), listOf(85, 70, 50)),
            ),
        ),
    )

    /**
     * Приводит правки в допустимый вид: факты снов — в 20–240 мин сна,
     * ручные правки окон — так, чтобы окно (база+comp+режим+manual) осталось
     * в жёстком коридоре возраста. Вызывать на входе и при загрузке старых
     * сохранённых значений: «скрытый запас» за границей не копится.
     */
    fun sanitizeAdj(age: Age, plan: NapPlan, adj: Adjustments, regress: Boolean): Adjustments {
        val napOff = List(plan.naps) { i -> clampNap(plan, i, adj.napOffsets.getOrElse(i) { 0 }) }
        val regime = if (regress) -REGRESS_STEP else 0
        val ww = List(plan.naps + 1) { i ->
            manualRange(age, plan, napOff, regime, i)
                .let { range -> adj.wwOffsets.getOrElse(i) { 0 }.coerceIn(range.first, range.last) }
        }
        return Adjustments(napOffsets = napOff, wwOffsets = ww)
    }

    /** Диапазон допустимой ручной правки окна [index] при текущих фактах и режиме. */
    fun manualRange(age: Age, plan: NapPlan, napOff: List<Int>, regime: Int, index: Int): IntRange {
        val base = plan.wakeWindows[index]
        val comp = if (index == 0) 0 else napOff[index - 1].coerceIn(-COMP_MAX, COMP_MAX)
        return (age.hardMin - base - comp - regime)..(age.hardMax - base - comp - regime)
    }

    fun build(
        age: Age,
        plan: NapPlan,
        wakeMinutes: Int,
        adj: Adjustments = Adjustments(),
        regress: Boolean = false,
    ): Schedule {
        val sane = sanitizeAdj(age, plan, adj, regress)
        var napOff = sane.napOffsets
        var result = chain(age, plan, wakeMinutes, napOff, sane.wwOffsets, if (regress) -REGRESS_STEP else 0)
        // Защита от экстремальных правок: отбой уехал за сутки (ночь < 5 ч) —
        // ужимаем растянутые сны с конца по 5 мин.
        while (result.nightSleep < 300) {
            val idx = napOff.indexOfLast { it > 0 }
            if (idx == -1) break
            napOff = napOff.toMutableList().also { it[idx] -= 5 }
            result = chain(age, plan, wakeMinutes, napOff, sane.wwOffsets, if (regress) -REGRESS_STEP else 0)
        }
        return result.copy(regress = regress)
    }

    private fun chain(
        age: Age,
        plan: NapPlan,
        wakeMinutes: Int,
        napOff: List<Int>,
        wwManual: List<Int>,
        regime: Int,
    ): Schedule {
        val windows = List(plan.naps + 1) { i ->
            val base = plan.wakeWindows[i]
            val manual0 = wwManual.getOrElse(i) { 0 }
            val comp = if (i == 0) 0 else napOff[i - 1].coerceIn(-COMP_MAX, COMP_MAX)
            // в норме manual уже в коридоре (sanitizeAdj); коррекция срабатывает
            // только в ветке защиты ночи, когда компенсация уменьшилась
            val eff0 = base + manual0 + comp + regime
            val manual = when {
                eff0 < age.hardMin -> manual0 + (age.hardMin - eff0)
                eff0 > age.hardMax -> manual0 - (eff0 - age.hardMax)
                else -> manual0
            }
            Window(i, base, manual, comp, regime)
        }

        var t = wakeMinutes
        val naps = mutableListOf<ScheduledNap>()
        for (i in 0 until plan.naps) {
            t += windows[i].effective
            val start = t
            t += plan.napDurations[i] + napOff[i]
            naps += ScheduledNap(
                index = i + 1,
                start = start,
                end = t,
                duration = plan.napDurations[i] + napOff[i],
                offset = napOff[i],
                window = windows[i],
            )
        }
        val bedtime = t + windows[plan.naps].effective
        val daySleep = plan.napDurations.sum() + napOff.sum()
        val nightSleep = wakeMinutes + 1440 - bedtime
        return Schedule(
            wake = wakeMinutes,
            naps = naps,
            windows = windows,
            bedtime = bedtime,
            daySleep = daySleep,
            nightSleep = nightSleep,
            totalSleep = daySleep + nightSleep,
            regress = false,
        )
    }

    /** Поправка факта сна ограничена: фактический сон 20–240 минут. */
    fun clampNap(plan: NapPlan, index: Int, value: Int): Int =
        value.coerceIn(20 - plan.napDurations[index], 240 - plan.napDurations[index])

    /** Куда попало кормление относительно расчётного дня. */
    enum class FeedKind { AT_WAKE, IN_WINDOW, IN_NAP, BEFORE_WAKE, AFTER_BEDTIME, NIGHT }

    data class FeedPlacement(
        val feed: Int,
        val kind: FeedKind,
        /** Для IN_WINDOW/IN_NAP — перед сном с этим номером (naps+1 = перед ночью). */
        val slot: Int,
    ) {
        val isWarning: Boolean get() = kind == FeedKind.IN_NAP || kind == FeedKind.AFTER_BEDTIME
    }

    fun placeFeeds(s: Schedule, feeds: List<Int>): List<FeedPlacement> {
        val wakeTod = ((s.wake % 1440) + 1440) % 1440
        return feeds.sorted().map { f ->
            val abs = if (f >= wakeTod) f else f + 1440
            when {
                abs == s.wake -> FeedPlacement(f, FeedKind.AT_WAKE, 0)
                s.naps.any { abs >= it.start && abs < it.end } -> {
                    val nap = s.naps.first { abs >= it.start && abs < it.end }
                    FeedPlacement(f, FeedKind.IN_NAP, nap.index)
                }
                abs >= s.bedtime ->
                    // утреннее кормление незадолго до подъёма — нейтральное
                    // «до подъёма», без указаний; остальное — ночь
                    if (f < wakeTod && wakeTod - f <= BEFORE_WAKE_MAX) {
                        FeedPlacement(f, FeedKind.BEFORE_WAKE, 0)
                    } else if (f >= 720) {
                        FeedPlacement(f, FeedKind.AFTER_BEDTIME, 0)
                    } else {
                        FeedPlacement(f, FeedKind.NIGHT, 0)
                    }
                else -> {
                    val next = s.naps.firstOrNull { it.start > abs }
                    FeedPlacement(f, FeedKind.IN_WINDOW, next?.index ?: (s.naps.size + 1))
                }
            }
        }
    }

    /** Фаза текущего момента. */
    enum class Phase { NAP, WINDOW, NIGHT }

    /** Где мы сейчас и что дальше. */
    data class NowInfo(
        val phase: Phase,
        val napIndex: Int,
        val windowIndex: Int,
        val nextAbs: Int,
        val minutesLeft: Int,
        val nextLabel: String,
    ) {
        val nextGenitive: String
            get() = if (nextLabel == "подъём") "подъёма" else "укладывания"

        val title: String
            get() = when (phase) {
                Phase.NAP -> "Сон $napIndex"
                Phase.WINDOW -> "Бодрствование"
                Phase.NIGHT -> "Ночь"
            }
    }

    fun nowInfo(s: Schedule, nowTod: Int): NowInfo {
        val wakeTod = ((s.wake % 1440) + 1440) % 1440
        val abs = if (nowTod >= wakeTod) nowTod else nowTod + 1440
        val nap = s.naps.firstOrNull { abs >= it.start && abs < it.end }
        return when {
            nap != null -> NowInfo(
                phase = Phase.NAP,
                napIndex = nap.index,
                windowIndex = -1,
                nextAbs = nap.end,
                minutesLeft = nap.end - abs,
                nextLabel = "подъём",
            )
            abs >= s.bedtime -> NowInfo(
                phase = Phase.NIGHT,
                napIndex = 0,
                windowIndex = -1,
                nextAbs = s.wake + 1440,
                minutesLeft = s.wake + 1440 - abs,
                nextLabel = "подъём",
            )
            else -> {
                val next = s.naps.firstOrNull { it.start > abs }
                val nextAbs = next?.start ?: s.bedtime
                NowInfo(
                    phase = Phase.WINDOW,
                    napIndex = 0,
                    windowIndex = next?.let { it.index - 1 } ?: s.windows.lastIndex,
                    nextAbs = nextAbs,
                    minutesLeft = nextAbs - abs,
                    nextLabel = if (next == null) "укладывание на ночь" else "укладывание",
                )
            }
        }
    }

    fun wwWarning(age: Age, w: Window): String? = when {
        w.effective < age.wwMin -> "короче нормы — ребёнок может недогулять"
        w.effective > age.wwMax -> "длиннее нормы — следи за признаками усталости"
        else -> null
    }

    fun napWarning(nap: ScheduledNap): String? = when {
        nap.duration < 40 -> "короткий сон — следующее окно сокращено, при повторе уложи ночь раньше"
        nap.duration > 90 -> "длинный сон — будить по таймеру, иначе следующие сны будут короче"
        else -> null
    }

    /**
     * Текст дня для отправки в Telegram: секции разделены пустыми строками,
     * кормление и следующее за ним бодрствование — в одной строке.
     */
    fun exportText(s: Schedule, age: Age, plan: NapPlan, feeds: List<Int>): String {
        val p = placeFeeds(s, feeds)
        val sb = StringBuilder()
        sb.append("Режим сна — ").append(age.label).append(" · ").append(plan.name).append('\n')
        sb.append("Подъём ").append(SleepEngine.fmtClock(s.wake)).append('\n')
        for ((i, nap) in s.naps.withIndex()) {
            sb.append('\n')
            val winFeeds = p.filter {
                (it.kind == FeedKind.IN_WINDOW && it.slot == nap.index) ||
                    (nap.index == 1 && it.kind == FeedKind.AT_WAKE)
            }
            val head = "Бодрствование ${fmtDurHuman(s.windows[i].effective)}"
            sb.append(
                if (winFeeds.isEmpty()) head
                else winFeeds.joinToString(" · ") { "🍼 ${fmtClock(it.feed)}" } + " · " + head,
            )
            sb.append('\n')
            sb.append("😴 Сон ${nap.index} — ${fmtClock(nap.start)}–${fmtClock(nap.end)}")
            sb.append(" (${fmtDurHuman(nap.duration)})").append('\n')
        }
        sb.append('\n')
        val lastFeeds = p.filter { it.kind == FeedKind.IN_WINDOW && it.slot == s.naps.size + 1 }
        val nightHead = "Бодрствование ${fmtDurHuman(s.wwBeforeNight)}"
        sb.append(
            if (lastFeeds.isEmpty()) nightHead
            else lastFeeds.joinToString(" · ") { "🍼 ${fmtClock(it.feed)}" } + " · " + nightHead,
        )
        sb.append('\n')
        sb.append("🌙 Ночь — ${fmtClock(s.bedtime)}–${fmtClock(s.wake)} (${fmtDurHuman(s.nightSleep)})").append('\n')
        val night = p.filter { it.kind == FeedKind.NIGHT }
        if (night.isNotEmpty()) {
            sb.append("🍼 Ночью: ").append(night.joinToString(" · ") { fmtClock(it.feed) }).append('\n')
        }
        val before = p.filter { it.kind == FeedKind.BEFORE_WAKE }
        if (before.isNotEmpty()) {
            sb.append("🍼 До подъёма: ")
                .append(before.joinToString(" · ") { fmtClock(it.feed) })
                .append('\n')
        }
        val evening = p.filter { it.kind == FeedKind.AFTER_BEDTIME }
        if (evening.isNotEmpty()) {
            sb.append("🍼 После отбоя: ")
                .append(evening.joinToString(" · ") { fmtClock(it.feed) })
                .append(" — покормить до укладывания")
                .append('\n')
        }
        sb.append('\n')
        sb.append("⏱ Бодрствование всего ").append(fmtDurHuman(s.wakeTotal)).append('\n')
        sb.append("📊 Сон днём ").append(fmtDurHuman(s.daySleep))
            .append(" · ночью ").append(fmtDurHuman(s.nightSleep))
            .append(" · всего ").append(fmtDurHuman(s.totalSleep)).append('\n')
        if (s.regress) {
            sb.append("📉 Режим регресса сна: все окна −").append(REGRESS_STEP).append(" мин").append('\n')
        }
        val facts = buildList {
            s.naps.filter { it.offset != 0 }.forEach {
                add("Сон ${it.index} ${if (it.offset < 0) "−" else "+"}${kotlin.math.abs(it.offset)} мин")
            }
            s.windows.forEachIndexed { i, w ->
                if (w.hasManual) {
                    add("Бодрствование ${i + 1} ${if (w.manual < 0) "−" else "+"}${kotlin.math.abs(w.manual)} мин")
                }
            }
        }
        if (facts.isNotEmpty()) sb.append("✱ Правки: ").append(facts.joinToString(" · "))
        return sb.toString().trimEnd()
    }

    /** Часы:Минуты для момента суток (с переходом через полночь). */
    fun fmtClock(absMinutes: Int): String {
        val m = ((absMinutes % 1440) + 1440) % 1440
        return String.format("%02d:%02d", m / 60, m % 60)
    }

    /** Длительность в формате Ч:ММ (1:20, 0:45) — для служебных строк. */
    fun fmtDur(minutes: Int): String =
        String.format("%d:%02d", minutes / 60, minutes % 60)

    /** Длительность по-человечески: «1 ч 20 мин», «45 мин», «1 ч». */
    fun fmtDurHuman(minutes: Int): String = when {
        minutes < 60 -> "$minutes мин"
        minutes % 60 == 0 -> "${minutes / 60} ч"
        else -> "${minutes / 60} ч ${minutes % 60} мин"
    }

    /** Отсчёт: «через 14 мин», «через 1 ч 20 мин». */
    fun fmtIn(minutes: Int): String = "через ${fmtDurHuman(minutes)}"

    private fun fmtHours(minutes: Int): String =
        if (minutes % 60 == 0) (minutes / 60).toString()
        else String.format("%.1f", minutes / 60.0).replace('.', ',')
    // ── Полоса дня: чистая модель главного экрана (§3.1–3.5) ──

    /** Сегмент полосы дня. relStart/relEnd — минуты от подъёма. */
    data class DaySegment(
        val relStart: Int,
        val relEnd: Int,
        val isNap: Boolean,
        val label: String,
    ) {
        val dur: Int get() = relEnd - relStart
    }

    data class DayBarModel(
        val segments: List<DaySegment>,
        /** Минуты суток подъёма в локальной зоне, напр. 540 для 09:00. */
        val wakeTod: Int,
        val bedtimeTod: Int,
        val dayLen: Int,
        /** Минуты от подъёма до «сейчас», ограничено днём (0..dayLen). */
        val nowRel: Int,
        val markerFrac: Float,
        /** Индекс текущего сегмента; −1 после отбоя. Ровно один внутри дня. */
        val currentIndex: Int,
        val remainingSleepMin: Int,
        val untilBedMin: Int,
    )

    /**
     * Модель полосы дня от фактического подъёма ([dayStart]) до расчётного отбоя.
     * Все времена — локальная зона [zone] (никакого UTC-остатка от epoch, §3.1).
     * Полночь: nowTod < wakeTod → +1440, единая относительная шкала (§3.2).
     * [regress] учитывается: при включённом режиме отбой полосы раньше (§3.5).
     * Границы сегментов [relStart, relEnd): подъём → первое окно, начало сна → сон,
     * конец сна → окно, после отбоя currentIndex = −1 (§3.4).
     */
    fun dayBar(
        age: Age,
        plan: NapPlan,
        regress: Boolean,
        dayStart: Long,
        now: Long,
        zone: java.time.ZoneId,
    ): DayBarModel {
        val base = build(age, plan, todOf(dayStart, zone), Adjustments(), regress)
        val wakeTod = base.wake
        val dayLen = base.bedtime - base.wake
        val nowTod = todOf(now, zone)
        val rawRel = if (nowTod < wakeTod) nowTod + 1440 - wakeTod else nowTod - wakeTod
        val nowRel = rawRel.coerceIn(0, dayLen)
        val segments = buildList {
            var cursor = 0
            for (i in base.naps.indices) {
                val w = base.windows[i].effective
                add(DaySegment(cursor, cursor + w, false, "Бодрствование ${i + 1}"))
                cursor += w
                val n = base.naps[i].duration
                add(DaySegment(cursor, cursor + n, true, "Сон ${i + 1}"))
                cursor += n
            }
            val lastW = base.windows.last().effective
            add(DaySegment(cursor, cursor + lastW, false, "В ночь"))
        }
        // Ровно один текущий сегмент: [relStart, relEnd); после отбоя — ни одного.
        var currentIndex = -1
        segments.forEachIndexed { i, s -> if (nowRel >= s.relStart && nowRel < s.relEnd) currentIndex = i }
        val elapsedSleep = segments.filter { it.isNap }
            .sumOf { (minOf(it.relEnd, nowRel) - maxOf(it.relStart, 0)).coerceAtLeast(0) }
        return DayBarModel(
            segments = segments,
            wakeTod = wakeTod,
            bedtimeTod = base.bedtime,
            dayLen = dayLen,
            nowRel = nowRel,
            markerFrac = if (dayLen <= 0) 0f else nowRel.toFloat() / dayLen,
            currentIndex = currentIndex,
            remainingSleepMin = (base.daySleep - elapsedSleep).coerceAtLeast(0),
            untilBedMin = (dayLen - nowRel).coerceAtLeast(0),
        )
    }

    /** Минуты суток для момента времени в заданной зоне (§3.1). */
    fun todOf(epochMillis: Long, zone: java.time.ZoneId): Int {
        val z = java.time.Instant.ofEpochMilli(epochMillis).atZone(zone)
        return z.hour * 60 + z.minute
    }

    /**
     * Ночной сон текущей ночи для плитки «Ночью сейчас» (§3.7): от nightStart до
     * [until] минус зарегистрированные ночные пробуждения (они — не сон).
     * null — ночи нет (нет nightStart или фаза не ночная).
     */
    fun nightSleepMinutes(
        intervals: List<History.Interval>,
        state: LiveTracker.State,
        until: Long,
    ): Int? {
        val ns = state.nightStart
        if (ns <= 0L) return null
        val untilMin = LiveTracker.minute(until)
        val startMin = LiveTracker.minute(ns)
        if (untilMin <= startMin) return 0
        val wakes = intervals.filter { it.kind == History.Kind.NIGHT_WAKE && it.start >= ns }
            .sumOf { ((minOf(it.end, untilMin) - it.start).coerceAtLeast(0) / LiveTracker.MINUTE).toInt() }
        return (((untilMin - startMin) / LiveTracker.MINUTE).toInt()) - wakes
    }

    /** Прогресс текущего этапа для виджета: 0..1000 (§F). */
    fun phaseProgress(state: LiveTracker.State, next: LiveTracker.Forecast?, now: Long): Int {
        if (next == null || next.at <= state.since) return 0
        return (((now - state.since).toFloat() / (next.at - state.since)).coerceIn(0f, 1f) * 1000).toInt()
    }

    /** Возраст текстом по дате рождения: «3 мес 22 дн», «110 дн» в первый месяц. */
    fun childAgeText(birth: Long?, today: java.time.LocalDate): String? {
        if (birth == null || birth <= 0L) return null
        val b = java.time.Instant.ofEpochMilli(birth).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val totalDays = java.time.temporal.ChronoUnit.DAYS.between(b, today)
        if (totalDays < 0) return null
        val months = java.time.temporal.ChronoUnit.MONTHS.between(b, today)
        val daysRest = totalDays - java.time.temporal.ChronoUnit.DAYS.between(b, b.plusMonths(months))
        return when {
            months < 1 -> "$totalDays дн"
            else -> "$months мес${if (daysRest > 0) " $daysRest дн" else ""}"
        }
    }

    /** Парс даты рождения «ДД.ММ.ГГГГ»; null при ошибке или дате в будущем. */
    fun parseBirthDate(text: String, today: java.time.LocalDate): Long? = try {
        val d = java.time.LocalDate.parse(text.trim(), java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        if (d.isAfter(today)) null
        else d.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    } catch (_: Exception) { null }

    /**
     * Расшифровка окна бодрствования перед ближайшим сном (§«почему в 16:39»):
     * из чего складывается окно — план, компенсация за короткий/длинный сон,
     * режим сокращения. [reasons] — человеческие пояснения по каждой добавке.
     * null — расшифровка неприменима (фаза не «бодрствую»).
     */
    data class WindowBreakdown(
        val baseMin: Int,
        val compMin: Int,
        val regimeMin: Int,
        val totalMin: Int,
        val reasons: List<String>,
    )

    fun windowBreakdown(
        state: LiveTracker.State,
        age: Age,
        plan: NapPlan,
        regress: Boolean,
    ): WindowBreakdown? {
        if (state.phase != LiveTracker.Phase.AWAKE) return null
        val idx = state.completedNaps.coerceAtMost(plan.naps)
        val base = plan.wakeWindows[idx]
        val comp = if (state.completedNaps > 0 && state.lastNapMinutes != null)
            (state.lastNapMinutes - plan.napDurations[(state.completedNaps - 1).coerceAtMost(plan.naps - 1)])
                .coerceIn(-COMP_MAX, COMP_MAX) else 0
        val regime = if (regress) -REGRESS_STEP else 0
        val reasons = mutableListOf<String>()
        if (comp < 0) reasons.add("−${-comp} мин — прошлый сон был короче плана")
        if (comp > 0) reasons.add("+${comp} мин — прошлый сон был длиннее плана")
        if (regime != 0) reasons.add("−${-regime} мин — режим сокращения окон")
        if (reasons.isEmpty()) reasons.add("окно по плану, без поправок")
        return WindowBreakdown(base, comp, regime, base + comp + regime, reasons)
    }

    /** Подсказка под таймером: норма фазы, остаток, over/near. */
    data class PhaseHint(val text: String, val fraction: Float, val over: Boolean, val near: Boolean)

    fun phaseHint(
        state: LiveTracker.State,
        age: Age,
        next: LiveTracker.Forecast?,
        now: Long,
    ): PhaseHint? {
        val duration = ((now - state.since).coerceAtLeast(0) / LiveTracker.MINUTE).toInt()
        return when (state.phase) {
            LiveTracker.Phase.AWAKE -> {
                val base = "Окно ${fmtDurHuman(duration)} · норма ${fmtDur(age.wwMin)}–${fmtDur(age.wwMax)}"
                if (next == null) {
                    PhaseHint(base, duration.toFloat() / age.hardMax, duration > age.hardMax, age.hardMax - duration in 1..10)
                } else {
                    val left = ((next.at - now) / LiveTracker.MINUTE).toInt()
                    val total = ((next.at - state.since).coerceAtLeast(LiveTracker.MINUTE) / LiveTracker.MINUTE).toInt()
                    if (left >= 0) PhaseHint(
                        "$base · осталось ~${fmtDurHuman(left)}",
                        duration.toFloat() / total, duration > age.hardMax, left in 1..10)
                    else PhaseHint("$base · окно вышло на ${fmtDurHuman(-left)}", 1f, true, false)
                }
            }
            LiveTracker.Phase.NAP -> {
                if (next == null) PhaseHint("Спит ${fmtDurHuman(duration)}", 0f, false, false)
                else {
                    val left = ((next.at - now) / LiveTracker.MINUTE).toInt()
                    val total = ((next.at - state.since).coerceAtLeast(LiveTracker.MINUTE) / LiveTracker.MINUTE).toInt()
                    if (left >= 0) PhaseHint(
                        "Ориентир сна ${fmtDurHuman(total)} · осталось ~${fmtDurHuman(left)}",
                        duration.toFloat() / total, false, left in 1..10)
                    else PhaseHint("Спит дольше ориентира на ${fmtDurHuman(-left)}", 1f, true, false)
                }
            }
            LiveTracker.Phase.NIGHT ->
                PhaseHint("Ночь ${fmtDurHuman(duration)} · ${age.nightNormShort}",
                    duration.toFloat() / age.nightMax, duration > age.nightMax, false)
            LiveTracker.Phase.NIGHT_AWAKE ->
                PhaseHint("Ночное бодрствование ${fmtDurHuman(duration)}", 0f, false, false)
            LiveTracker.Phase.READY -> null
        }
    }

}


/** Текущая сессия без журнала и статистики. Факты никогда не клэмпятся нормами. */
object LiveTracker {
    const val MINUTE = 60_000L
    enum class Phase { READY, AWAKE, NAP, NIGHT, NIGHT_AWAKE }
    data class State(
        val phase: Phase = Phase.READY,
        val since: Long = 0,
        val previousAt: Long = 0,
        val dayStart: Long = 0,
        val completedNaps: Int = 0,
        val lastNapMinutes: Int? = null,
        val nightStart: Long = 0,
    )
    data class Forecast(val at: Long, val title: String)
    fun minute(time: Long): Long = Math.floorDiv(time, MINUTE) * MINUTE
    fun toggle(s: State, time: Long): State {
        val at = minute(time)
        require(s.phase == Phase.READY || at >= s.since) { "Время телефона раньше начала события" }
        return when (s.phase) {
            Phase.READY -> State(Phase.AWAKE, at, dayStart = at)
            Phase.AWAKE -> s.copy(phase = Phase.NAP, since = at, previousAt = s.since)
            Phase.NAP -> s.copy(phase = Phase.AWAKE, since = at, previousAt = s.since,
                completedNaps = s.completedNaps + 1, lastNapMinutes = ((at - s.since) / MINUTE).toInt())
            Phase.NIGHT -> s.copy(phase = Phase.NIGHT_AWAKE, since = at, previousAt = s.since)
            Phase.NIGHT_AWAKE -> s.copy(phase = Phase.NIGHT, since = at, previousAt = s.since)
        }
    }
    fun startNight(s: State, time: Long): State {
        require(s.phase == Phase.AWAKE || s.phase == Phase.NAP)
        val at = if (s.phase == Phase.NAP) s.since else minute(time)
        require(at >= s.since)
        return s.copy(phase = Phase.NIGHT, since = at, nightStart = at,
            previousAt = if (s.phase == Phase.NAP) s.previousAt else s.since)
    }
    fun startDay(s: State, time: Long): State {
        require(s.phase == Phase.NIGHT || s.phase == Phase.NIGHT_AWAKE)
        val at = if (s.phase == Phase.NIGHT_AWAKE) s.since else minute(time)
        require(at >= s.since)
        return State(phase = Phase.AWAKE, since = at, dayStart = at)
    }
    /** Последнее прошедшее вхождение часов/минут; переход через полночь явный. */
    fun clockTime(hour: Int, minute: Int, now: Long, zone: java.time.ZoneId): Long {
        require(hour in 0..23 && minute in 0..59) { "Укажи часы 00–23 и минуты 00–59" }
        val current = java.time.Instant.ofEpochMilli(now).atZone(zone)
        var selected = current.toLocalDate().atTime(hour, minute).atZone(zone)
        if (selected.toInstant().toEpochMilli() > now) selected = selected.minusDays(1)
        return selected.toInstant().toEpochMilli()
    }
    fun correct(s: State, at: Long, now: Long): State {
        require(s.phase != Phase.READY)
        val value = minute(at)
        require(value <= minute(now)) { "Нельзя записать будущее время" }
        require(s.previousAt == 0L || value >= s.previousAt) { "Это раньше предыдущего события" }
        return s.copy(since = value,
            dayStart = if (s.since == s.dayStart) value else s.dayStart,
            nightStart = if (s.since == s.nightStart) value else s.nightStart,
            lastNapMinutes = if (s.phase == Phase.AWAKE && s.completedNaps > 0)
                ((value - s.previousAt) / MINUTE).toInt() else s.lastNapMinutes)
    }
    fun forecasts(s: State, age: SleepEngine.Age, plan: SleepEngine.NapPlan, regress: Boolean): List<Forecast> {
        if (s.phase == Phase.READY || s.phase == Phase.NIGHT_AWAKE) return emptyList()
        val regime = if (regress) SleepEngine.REGRESS_STEP else 0
        if (s.phase == Phase.NIGHT) {
            val night = 1440 - plan.wakeWindows.sum() - plan.napDurations.sum()
            return listOf(Forecast(s.nightStart + night * MINUTE, "Утренний подъём"))
        }
        val result = mutableListOf<Forecast>()
        var cursor = s.since
        var index = s.completedNaps.coerceAtMost(plan.naps)
        if (s.phase == Phase.NAP) {
            val duration = plan.napDurations.getOrElse(index) { plan.napDurations.last() }
            cursor += duration * MINUTE
            result += Forecast(cursor, "Пробуждение")
            index++
            if (index > plan.naps) result += Forecast(cursor + plan.wakeWindows.last() * MINUTE, "Ночной сон")
        }
        var firstWindow = true
        while (index <= plan.naps) {
            val base = plan.wakeWindows[index.coerceAtMost(plan.naps)]
            val comp = if (firstWindow && s.phase == Phase.AWAKE && s.completedNaps > 0 && s.lastNapMinutes != null) {
                (s.lastNapMinutes - plan.napDurations[(s.completedNaps - 1).coerceAtMost(plan.naps - 1)])
                    .coerceIn(-SleepEngine.COMP_MAX, SleepEngine.COMP_MAX)
            } else 0
            val window = (base + comp - regime).coerceIn(age.hardMin, age.hardMax)
            cursor += window * MINUTE
            if (index >= plan.naps) {
                result += Forecast(cursor, "Ночной сон")
                break
            }
            result += Forecast(cursor, "Сон ${index + 1}")
            cursor += plan.napDurations[index] * MINUTE
            result += Forecast(cursor, "Пробуждение")
            index++
            firstWindow = false
        }
        // Дополнительный дневной сон не блокируется даже после конца выбранной схемы.
        if (result.isEmpty()) result += Forecast(cursor + plan.wakeWindows.last() * MINUTE, "Ночной сон")
        return result
    }
    fun nextFeed(feeds: List<Int>, now: Long, zone: java.time.ZoneId): Long? {
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return feeds.filter { it in 0..1439 }.map { f ->
            var at = today.atTime(f / 60, f % 60).atZone(zone)
            if (at.toInstant().toEpochMilli() < minute(now)) at = at.plusDays(1)
            at.toInstant().toEpochMilli()
        }.minOrNull()
    }
    // ── Лента дня: факт слева, прогноз справа (v1.18) ──

    /** Сегмент ленты. relStart/relEnd — минуты от подъёма. isFact — записано, иначе план. */
    data class TlSeg(
        val relStart: Int,
        val relEnd: Int,
        val isSleep: Boolean,
        val isFact: Boolean,
        val label: String,
    ) {
        val dur: Int get() = relEnd - relStart
    }

    data class DayTimelineModel(
        val segments: List<TlSeg>,
        /** Минуты от подъёма до конца шкалы. */
        val endRel: Int,
        val markerFrac: Float,
        /** Индекс живого сегмента (содержит «сейчас»); −1 если шкала закончилась. */
        val liveIndex: Int,
    )

    /**
     * Лента дня: записанные отрезки (факт) + прогноз будущих (§UX-запрос v1.18).
     * Шкала — минуты от фактического подъёма (state.dayStart) до конца последнего
     * прогноза. Прошедшие ориентиры кусков плана отбрасываются — факт первичен.
     * Пробелы между фактами — бодрствование. Ночные пробуждения — бодрствование.
     */
        /** Часы:минуты для epoch-времени в заданной зоне. */
    private fun clockEpoch(time: Long, zone: java.time.ZoneId): String =
        java.time.Instant.ofEpochMilli(time).atZone(zone)
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

fun dayTimeline(
        intervals: List<History.Interval>,
        state: State,
        now: Long,
        forecasts: List<Forecast>,
    ): DayTimelineModel? {
        val anchor = state.dayStart
        if (anchor <= 0L) return null
        val rel = { t: Long -> ((t - anchor) / MINUTE).toInt() }
        val nowRel = rel(now).coerceAtLeast(0)

        val raw = mutableListOf<TlSeg>()
        val mine = intervals.filter { it.day == anchor }.sortedBy { it.start }
        for (iv in mine) {
            val st = rel(iv.start).coerceAtLeast(0)
            val en = rel(iv.end)
            if (en <= st) continue
            when (iv.kind) {
                History.Kind.NAP -> raw.add(TlSeg(st, en, true, true, "Сон"))
                History.Kind.NIGHT_WAKE -> raw.add(TlSeg(st, en, false, true, "Ночное бодрствование"))
                History.Kind.NIGHT -> {}
            }
        }
        // Живой отрезок (текущая фаза)
        if (state.phase != Phase.READY) {
            val st = rel(state.since).coerceAtLeast(0)
            val en = rel(now)
            if (en > st) {
                val isSleep = state.phase == Phase.NAP || state.phase == Phase.NIGHT
                raw.add(TlSeg(st, en, isSleep, true, if (isSleep) "Спит сейчас" else "Бодрствует сейчас"))
            }
        }
        // Будущее по прогнозу: пары «событие → следующее событие»
        val future = forecasts.filter { it.at >= minute(now) }.sortedBy { it.at }
        var prevAt = -1L
        var prevTitle = ""
        for (f in future) {
            if (prevAt >= 0) {
                val st = rel(prevAt).coerceAtLeast(nowRel)
                val en = rel(f.at)
                if (en > st) {
                    val isSleep = prevTitle.startsWith("Сон") || prevTitle.startsWith("Ночной")
                    raw.add(TlSeg(st, en, isSleep, false, if (isSleep) prevTitle else "Бодрствование"))
                }
            }
            prevAt = f.at; prevTitle = f.title
        }

        // Сортировка, устранение пересечений (факт приоритетен), дыры → бодрствование
        raw.sortBy { it.relStart }
        val segments = mutableListOf<TlSeg>()
        var cursor = 0
        for (r in raw) {
            val st = maxOf(r.relStart, cursor)
            val en = maxOf(r.relEnd, st)
            if (en > st) segments.add(TlSeg(st, en, r.isSleep, r.isFact, r.label))
            cursor = maxOf(cursor, en)
        }
        val endRel = maxOf(cursor, nowRel)
        val filled = mutableListOf<TlSeg>()
        var fill = 0
        for (s in segments) {
            if (s.relStart > fill) filled.add(TlSeg(fill, s.relStart, false, true, "Бодрствование"))
            filled.add(s); fill = s.relEnd
        }
        if (fill < endRel) filled.add(TlSeg(fill, endRel, false, false, "План"))

        // Живой сегмент — тот, что содержит «сейчас»
        var liveIndex = -1
        filled.forEachIndexed { i, s -> if (nowRel >= s.relStart && nowRel < s.relEnd) liveIndex = i }

        return DayTimelineModel(
            segments = filled,
            endRel = endRel,
            markerFrac = if (endRel <= 0) 0f else nowRel.toFloat() / endRel,
            liveIndex = liveIndex,
        )
    }

    /** Полный экспорт дня: подъём, отрезки с временами и длительностями, ночь. */
    fun exportDay(
        intervals: List<History.Interval>,
        state: State,
        now: Long,
        childName: String,
        zone: java.time.ZoneId,
    ): String {
        val sb = StringBuilder()
        val day = state.dayStart
        val dateText = java.time.Instant.ofEpochMilli(day).atZone(zone).toLocalDate()
            .format(java.time.format.DateTimeFormatter.ofPattern("d MMMM", java.util.Locale("ru")))
        sb.append(childName).append(" · ").append(dateText).append('\n')
        sb.append("Подъём ").append(clockEpoch(day, zone)).append('\n')
        val mine = intervals.filter { it.day == day }.sortedBy { it.start }
        var cursor = day
        var napN = 0
        for (iv in mine) {
            if (iv.start > cursor) {
                sb.append("⏱ Бодрствование — ").append(clockEpoch(cursor, zone)).append("–")
                    .append(clockEpoch(iv.start, zone)).append(" (")
                    .append(SleepEngine.fmtDurHuman(((iv.start - cursor) / LiveTracker.MINUTE).toInt())).append(")\n")
            }
            when (iv.kind) {
                History.Kind.NAP -> { napN++; sb.append("😴 Сон ").append(napN).append(" — ") }
                History.Kind.NIGHT_WAKE -> sb.append("⏱ Ночное бодрствование — ")
                History.Kind.NIGHT -> sb.append("🌙 Ночь — ")
            }
            sb.append(clockEpoch(iv.start, zone)).append("–").append(clockEpoch(iv.end, zone))
                .append(" (").append(SleepEngine.fmtDurHuman(iv.minutes)).append(")\n")
            cursor = iv.end
        }
        when (state.phase) {
            Phase.AWAKE -> sb.append("⏱ Бодрствует с ").append(clockEpoch(state.since, zone)).append(" — идёт\n")
            Phase.NAP -> sb.append("😴 Спит с ").append(clockEpoch(state.since, zone)).append(" — идёт\n")
            Phase.NIGHT -> sb.append("🌙 Ночной сон с ").append(clockEpoch(state.nightStart, zone)).append(" — идёт\n")
            Phase.NIGHT_AWAKE -> sb.append("⏱ Проснулся ночью в ").append(clockEpoch(state.since, zone)).append(" — идёт\n")
            Phase.READY -> {}
        }
        val lastNight = intervals.filter { it.kind == History.Kind.NIGHT }.maxByOrNull { it.end }
        if (lastNight != null) {
            sb.append("🌙 Прошлой ночью — ").append(clockEpoch(lastNight.start, zone)).append("–")
                .append(clockEpoch(lastNight.end, zone)).append(" (").append(SleepEngine.fmtDurHuman(lastNight.minutes)).append(")\n")
        }
        val t = History.totals(intervals, state, now)
        sb.append("⏱ Бодрствование днём: ").append(SleepEngine.fmtDurHuman(t.dayAwakeMin ?: 0)).append('\n')
        sb.append("😴 Сон днём: ").append(SleepEngine.fmtDurHuman(t.daySleepMin)).append('\n')
        if (lastNight != null) sb.append("😴 За сутки (день + прошлая ночь): ")
            .append(SleepEngine.fmtDurHuman(t.daySleepMin + lastNight.minutes)).append('\n')
        return sb.toString().trimEnd()
    }
}
