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
        sb.append("Подъём ").append(fmtClock(s.wake)).append('\n')
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
}
