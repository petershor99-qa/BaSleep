package com.family.sleepschedule

/**
 * Журнал завершённых интервалов сна — v1.14.
 *
 * Хранение максимально простое, текстом: одна строка на интервал
 * «dayStart|start|end|KIND» в SharedPreferences (ключ history).
 * Ночь и ночные пробуждения относятся к дню, который начался предыдущим
 * подъёмом (поле [Interval.day] — это dayStart того дня, он же дата дня
 * и время подъёма).
 *
 * Записываются только ЗАВЕРШЁННЫЕ интервалы (сон от начала до конца).
 * Текущая незавершённая фаза в журнал не пишется, но учитывается
 * в [totals] для итогов текущего дня.
 */
object History {

    enum class Kind { NAP, NIGHT, NIGHT_WAKE }

    /** Завершённый интервал. [day] — время подъёма (dayStart) дня. */
    data class Interval(val day: Long, val start: Long, val end: Long, val kind: Kind) {
        val minutes: Int get() = ((end - start) / LiveTracker.MINUTE).toInt()
    }

    fun parse(raw: String?): List<Interval> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val p = line.split('|')
            if (p.size != 4) return@mapNotNull null
            val day = p[0].toLongOrNull() ?: return@mapNotNull null
            val start = p[1].toLongOrNull() ?: return@mapNotNull null
            val end = p[2].toLongOrNull() ?: return@mapNotNull null
            val kind = runCatching { Kind.valueOf(p[3]) }.getOrNull() ?: return@mapNotNull null
            if (day <= 0 || end < start) null else Interval(day, start, end, kind)
        }.toList()
    }

    fun serialize(list: List<Interval>): String =
        list.joinToString("\n") { "${it.day}|${it.start}|${it.end}|${it.kind.name}" }

    /** Храним только последние [days] дней, чтобы журнал не рос бесконечно. */
    fun keepRecent(list: List<Interval>, now: Long, days: Int = 14): List<Interval> {
        val cutoff = now - days * 24L * 60 * 60 * 1000
        return list.filter { it.day >= cutoff }
    }

    /** Убрать последние [count] интервалов (отмена действия). */
    fun dropLast(list: List<Interval>, count: Int): List<Interval> =
        if (count <= 0) list else list.dropLast(count.coerceAtMost(list.size))

    /** Убрать все интервалы дня («Начать заново» — запись дня была ошибочной). */
    fun removeDay(list: List<Interval>, day: Long): List<Interval> =
        if (day <= 0) list else list.filter { it.day != day }

    /**
     * Интервалы, которые надо добавить при переходе [prev] → [next] в момент
     * [at]. Пустой список — переход ничего не завершает (начало сна, исправление
     * времени и т.п.).
     */
    fun transition(prev: LiveTracker.State, next: LiveTracker.State, at: Long): List<Interval> {
        val minute = LiveTracker.minute(at)
        return when {
            // Дневной сон завершился («Проснулся» из фазы «Спит»).
            prev.phase == LiveTracker.Phase.NAP && next.phase == LiveTracker.Phase.AWAKE ->
                if (prev.dayStart > 0) listOf(Interval(prev.dayStart, prev.since, minute, Kind.NAP)) else emptyList()
            // Ночное бодрствование завершилось («Уснул» из «Проснулся ночью»).
            prev.phase == LiveTracker.Phase.NIGHT_AWAKE && next.phase == LiveTracker.Phase.NIGHT ->
                if (prev.dayStart > 0) listOf(Interval(prev.dayStart, prev.since, minute, Kind.NIGHT_WAKE)) else emptyList()
            // «Начать день» из ночного сна: ночь завершилась сейчас.
            prev.phase == LiveTracker.Phase.NIGHT && next.phase == LiveTracker.Phase.AWAKE ->
                if (prev.dayStart > 0 && prev.nightStart > 0)
                    listOf(Interval(prev.dayStart, prev.nightStart, minute, Kind.NIGHT)) else emptyList()
            // «Начать день» после ночного пробуждения: ночь длилась до пробуждения.
            prev.phase == LiveTracker.Phase.NIGHT_AWAKE && next.phase == LiveTracker.Phase.AWAKE ->
                if (prev.dayStart > 0 && prev.nightStart > 0)
                    listOf(Interval(prev.dayStart, prev.nightStart, prev.since, Kind.NIGHT)) else emptyList()
            else -> emptyList()
        }
    }

    /** Сводка одного дня для экрана истории. */
    data class DaySummary(val day: Long, val intervals: List<Interval>) {
        val wakeAt: Long get() = day
        val naps: List<Interval> get() = intervals.filter { it.kind == Kind.NAP }
        val nightWakes: List<Interval> get() = intervals.filter { it.kind == Kind.NIGHT_WAKE }
        val night: Interval? get() = intervals.filter { it.kind == Kind.NIGHT }.maxByOrNull { it.end }
        val daySleepMin: Int get() = naps.sumOf { it.minutes }
        val nightSleepMin: Int get() = intervals.filter { it.kind == Kind.NIGHT }.sumOf { it.minutes }
        val nightWakeMin: Int get() = nightWakes.sumOf { it.minutes }
        val totalSleepMin: Int get() = daySleepMin + nightSleepMin
        /** Бодрствование днём: от подъёма до начала ночи минус дневные сны. */
        val dayAwakeMin: Int? get() = night?.let {
            (((it.start - day) / LiveTracker.MINUTE).toInt() - daySleepMin).coerceAtLeast(0)
        }
    }

    /** Дни по убыванию (свежий первым). */
    fun days(list: List<Interval>): List<DaySummary> =
        list.groupBy { it.day }
            .map { (d, items) -> DaySummary(d, items.sortedBy { it.start }) }
            .sortedByDescending { it.day }

    /** Итоги текущего (незавершённого) дня: журнал + текущая фаза. */
    data class Totals(
        val daySleepMin: Int,
        val nightSleepMin: Int,
        /** Бодрствование днём до ночи (или до сейчас, если ночь не началась). */
        val dayAwakeMin: Int?,
    ) {
        val totalSleepMin: Int get() = daySleepMin + nightSleepMin
    }

    fun totals(list: List<Interval>, state: LiveTracker.State, now: Long): Totals {
        val day = state.dayStart
        if (day <= 0) return Totals(0, 0, null)
        val mine = list.filter { it.day == day }
        var nap = mine.filter { it.kind == Kind.NAP }.sumOf { it.minutes }
        var night = mine.filter { it.kind == Kind.NIGHT }.sumOf { it.minutes }
        when (state.phase) {
            LiveTracker.Phase.NAP ->
                nap += ((now - state.since).coerceAtLeast(0) / LiveTracker.MINUTE).toInt()
            LiveTracker.Phase.NIGHT ->
                night += ((now - state.since).coerceAtLeast(0) / LiveTracker.MINUTE).toInt()
            else -> {}
        }
        val end = if (state.nightStart > 0) state.nightStart else now
        val dayAwake = (((end - day).coerceAtLeast(0) / LiveTracker.MINUTE).toInt() - nap).coerceAtLeast(0)
        return Totals(nap, night, dayAwake)
    }
}
