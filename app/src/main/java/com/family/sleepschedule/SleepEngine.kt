package com.family.sleepschedule

/**
 * Движок расписания сна для возраста 3–5 месяцев.
 *
 * База для 3 мес — правила консультанта семьи: окна бодрствования 1:15–1:45
 * (жёстко 1:10–1:50), 4 дневных сна 1:20/1:20/1:10/1:00 (день 4:50), ночь
 * 11–12 ч, уход в ночь 21:30–21:50 при подъёме 09:00, всего 15–17 ч,
 * шаг всех правок ±5 мин, ΣВБ + ΣДС + ночь = 24 ч.
 *
 * Схемы выбираются числом снов: «4 сна» (базовая, отбой 21:35), «5 снов»
 * (для младших, тот же отбой 21:35), у 4–5 мес — переходы на 3 сна.
 *
 * Правки: факт сна (короче/длиннее) пересчитывает хвост и авто-компенсирует
 * следующее ВБ (± до 15 мин); ручные правки ВБ клэмпятся в жёсткий коридор;
 * ночь поглощает остаток суток.
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

    /** Все ручные правки дня: факты снов и правки окон бодрствования. */
    data class Adjustments(
        val napOffsets: List<Int> = emptyList(),
        val wwOffsets: List<Int> = emptyList(),
    )

    /** Одно окно бодрствования: база + ручная правка + авто-компенсация. */
    data class Window(
        val index: Int,
        val base: Int,
        val manual: Int,
        val comp: Int,
    ) {
        val effective: Int get() = base + manual + comp
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
    ) {
        val wwBeforeNight: Int get() = windows.last().effective
        val hasFacts: Boolean
            get() = naps.any { it.offset != 0 } || windows.any { it.manual != 0 }

        val bedtimeTod: Int get() = ((bedtime % 1440) + 1440) % 1440

        /** Суммарное бодрствование за сутки (Σ окон). */
        val wakeTotal: Int get() = windows.sumOf { it.effective }

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

    /** Максимальная авто-компенсация следующего ВБ после факта сна, мин. */
    const val COMP_MAX = 15

    /** Шаг режима регресса: все ВБ минус столько минут. */
    const val REGRESS_STEP = 10

    /** Схема кормлений семьи: 00:00, 04:30, 09:00, 12:00, 15:00, 18:00, 21:00. */
    val DEFAULT_FEEDS: List<Int> = listOf(0, 270, 540, 720, 900, 1080, 1260)

    val AGES: List<Age> = listOf(
        Age(
            months = 3,
            label = "3 мес",
            // правила консультанта: окна 1:15–1:45 (жёстко 1:10–1:50),
            // день 4–5 ч, ночь 11–12 ч, всего 15–17 ч
            wwMin = 75, wwMax = 105,
            hardMin = 70, hardMax = 110,
            dayMin = 4 * 60, dayMax = 5 * 60,
            nightMin = 11 * 60, nightMax = 12 * 60,
            totalMin = 15 * 60, totalMax = 17 * 60,
            plans = listOf(
                // база: 09:00 → сны 10:25, 13:20, 16:20, 19:15 → ночь 21:35
                NapPlan("4 сна", 4, listOf(85, 95, 100, 105, 80), listOf(80, 80, 70, 60)),
                // 5 снов (для младших): тот же отбой 21:35, ночь 11:25
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

    fun build(age: Age, plan: NapPlan, wakeMinutes: Int, adj: Adjustments = Adjustments()): Schedule {
        val wwManual = List(plan.naps + 1) { adj.wwOffsets.getOrElse(it) { 0 } }
        var napOff = List(plan.naps) { i -> clampNap(plan, i, adj.napOffsets.getOrElse(i) { 0 }) }
        var result = chain(age, plan, wakeMinutes, napOff, wwManual)
        // Защита от экстремальных правок: если отбой уехал за следующие сутки
        // и ночь стала короче 5 часов — ужимаем растянутые сны с конца по 5 мин.
        while (result.nightSleep < 300) {
            val idx = napOff.indexOfLast { it > 0 }
            if (idx == -1) break
            napOff = napOff.toMutableList().also { it[idx] -= 5 }
            result = chain(age, plan, wakeMinutes, napOff, wwManual)
        }
        return result
    }

    private fun chain(
        age: Age,
        plan: NapPlan,
        wakeMinutes: Int,
        napOff: List<Int>,
        wwManual: List<Int>,
    ): Schedule {
        val windows = List(plan.naps + 1) { i ->
            val base = plan.wakeWindows[i]
            val manual0 = wwManual.getOrElse(i) { 0 }
            val comp = if (i == 0) 0 else napOff[i - 1].coerceIn(-COMP_MAX, COMP_MAX)
            val eff0 = base + manual0 + comp
            val manual = when {
                eff0 < age.hardMin -> manual0 + (age.hardMin - eff0)
                eff0 > age.hardMax -> manual0 - (eff0 - age.hardMax)
                else -> manual0
            }
            Window(i, base, manual, comp)
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
        )
    }

    /** Поправка факта сна ограничена: фактический сон 20–240 минут. */
    fun clampNap(plan: NapPlan, index: Int, value: Int): Int =
        value.coerceIn(20 - plan.napDurations[index], 240 - plan.napDurations[index])

    /** Куда попало кормление относительно расчётного дня. */
    enum class FeedKind { AT_WAKE, IN_WINDOW, IN_NAP, AFTER_BEDTIME, NIGHT }

    data class FeedPlacement(
        val feed: Int,
        val kind: FeedKind,
        /** Для IN_WINDOW/IN_NAP — перед сном с этим номером (naps+1 = перед ночью). */
        val slot: Int,
    )

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
                    // вечернее кормление (после полудня по часам) — «после отбоя»,
                    // его стоит дать до укладывания; ночное (до полудня) — не будим
                    if (f >= 720) FeedPlacement(f, FeedKind.AFTER_BEDTIME, 0)
                    else FeedPlacement(f, FeedKind.NIGHT, 0)
                else -> {
                    val next = s.naps.firstOrNull { it.start > abs }
                    FeedPlacement(f, FeedKind.IN_WINDOW, next?.index ?: (s.naps.size + 1))
                }
            }
        }
    }

    /** Фаза текущего момента. */
    enum class Phase { NAP, WINDOW, NIGHT }

    /** Где мы сейчас и что дальше (для подсветки «сейчас» и ночного экрана). */
    data class NowInfo(
        val phase: Phase,
        /** 1-based номер сна при phase=NAP, иначе 0. */
        val napIndex: Int,
        /** 0-based номер окна при phase=WINDOW, иначе −1. */
        val windowIndex: Int,
        /** Абсолютные минуты следующего события. */
        val nextAbs: Int,
        /** Минут до следующего события. */
        val minutesLeft: Int,
        /** «укладывание», «подъём», «укладывание на ночь». */
        val nextLabel: String,
    ) {
        /** Родительный падеж для ночного экрана: «до подъёма», «до укладывания». */
        val nextGenitive: String
            get() = if (nextLabel == "подъём") "подъёма" else "укладывания"

        val title: String
            get() = when (phase) {
                Phase.NAP -> "😴 Сон $napIndex"
                Phase.WINDOW -> "⏱ Бодрствование"
                Phase.NIGHT -> "🌃 Ночь"
            }
    }

    /** [nowTod] — текущие минуты суток (0–1439). */
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

    /** Предупреждение по окну бодрствования; null — норма. */
    fun wwWarning(age: Age, w: Window): String? = when {
        w.effective < age.wwMin -> "короче нормы — ребёнок может недогулять"
        w.effective > age.wwMax -> "длиннее нормы — следи за признаками усталости"
        else -> null
    }

    /** Предупреждение по фактической длительности сна; null — норма. */
    fun napWarning(nap: ScheduledNap): String? = when {
        nap.duration < 40 -> "короткий сон — следующее ВБ сокращено, при повторе уложи ночь раньше"
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
        sb.append("🌙 Режим сна — ").append(age.label).append(" · ").append(plan.name).append('\n')
        sb.append("☀️ Подъём ").append(fmtClock(s.wake)).append('\n')
        for ((i, nap) in s.naps.withIndex()) {
            sb.append('\n')
            val winFeeds = p.filter {
                (it.kind == FeedKind.IN_WINDOW && it.slot == nap.index) ||
                    (nap.index == 1 && it.kind == FeedKind.AT_WAKE)
            }
            val head = "⏱ Бодрствование ${fmtDur(s.windows[i].effective)}"
            sb.append(
                if (winFeeds.isEmpty()) head
                else winFeeds.joinToString(" · ") { "🍼 ${fmtClock(it.feed)}" } + " · " + head,
            )
            sb.append('\n')
            sb.append("😴 Сон ${nap.index} — ${fmtClock(nap.start)}–${fmtClock(nap.end)}")
            sb.append(" (${fmtDur(nap.duration)})").append('\n')
        }
        sb.append('\n')
        val lastFeeds = p.filter { it.kind == FeedKind.IN_WINDOW && it.slot == s.naps.size + 1 }
        val nightHead = "⏱ Бодрствование ${fmtDur(s.wwBeforeNight)}"
        sb.append(
            if (lastFeeds.isEmpty()) nightHead
            else lastFeeds.joinToString(" · ") { "🍼 ${fmtClock(it.feed)}" } + " · " + nightHead,
        )
        sb.append('\n')
        sb.append("🌃 Ночь — ${fmtClock(s.bedtime)}–${fmtClock(s.wake)} (${fmtDur(s.nightSleep)})").append('\n')
        val nf = p.filter { it.kind == FeedKind.NIGHT }
        if (nf.isNotEmpty()) {
            sb.append("🍼 Ночью: ").append(nf.joinToString(" · ") { fmtClock(it.feed) }).append('\n')
        }
        val evening = p.filter { it.kind == FeedKind.AFTER_BEDTIME }
        if (evening.isNotEmpty()) {
            sb.append("🍼 После отбоя: ")
                .append(evening.joinToString(" · ") { fmtClock(it.feed) })
                .append(" — покормить до укладывания")
                .append('\n')
        }
        sb.append('\n')
        sb.append("⏱ Бодрствование всего ").append(fmtDur(s.wakeTotal)).append('\n')
        sb.append("📊 Сон днём ").append(fmtDur(s.daySleep))
            .append(" · ночью ").append(fmtDur(s.nightSleep))
            .append(" · всего ").append(fmtDur(s.totalSleep)).append('\n')
        val facts = buildList {
            s.naps.filter { it.offset != 0 }.forEach {
                add("Сон ${it.index} ${if (it.offset < 0) "−" else "+"}${kotlin.math.abs(it.offset)} мин")
            }
            s.windows.forEachIndexed { i, w ->
                if (w.manual != 0) {
                    add("Бодрствование ${i + 1} ${if (w.manual < 0) "−" else "+"}${kotlin.math.abs(w.manual)} мин")
                }
            }
        }
        if (facts.isNotEmpty()) sb.append("✱ Факты: ").append(facts.joinToString(" · "))
        return sb.toString().trimEnd()
    }

    /** Часы:Минуты для момента суток (с переходом через полночь). */
    fun fmtClock(absMinutes: Int): String {
        val m = ((absMinutes % 1440) + 1440) % 1440
        return String.format("%02d:%02d", m / 60, m % 60)
    }

    /** Длительность в формате Ч:ММ (1:20, 0:45). */
    fun fmtDur(minutes: Int): String =
        String.format("%d:%02d", minutes / 60, minutes % 60)

    private fun fmtHours(minutes: Int): String =
        if (minutes % 60 == 0) (minutes / 60).toString()
        else String.format("%.1f", minutes / 60.0).replace('.', ',')
}
