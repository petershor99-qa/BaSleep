package com.family.sleepschedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Лента дня «факт + прогноз» (v1.18): записанные отрезки слева, прогноз
 * справа, маркер «сейчас» — по фактическому времени, а не по плану.
 * Сценарии — с реальных скриншотов владельца (день 09:05, Дима).
 */
class DayTimelineTest {

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")
    private val age3 = SleepEngine.AGES[0]
    private val plan4 = age3.plans[0]

    private fun ms(d: String, t: String): Long = LocalDateTime.parse("${d}T${time(d, t)}")
        .atZone(zone).toInstant().toEpochMilli()

    private fun time(d: String, t: String) = t

    private fun state(since: String, phase: LiveTracker.Phase, nightStart: Long? = null): LiveTracker.State =
        LiveTracker.State(
            phase = phase,
            since = ms("2026-11-08", since),
            previousAt = ms("2026-11-08", "14:30"),
            dayStart = ms("2026-11-08", "09:05"),
            completedNaps = 2,
            nightStart = nightStart ?: 0L,
        )

    /** День владельца: подъём 09:05, Сон 1 10:20–11:40, Сон 2 13:05–15:05. */
    private fun intervals(): List<History.Interval> = listOf(
        History.Interval(ms("2026-11-08", "09:05"), ms("2026-11-08", "10:20"), ms("2026-11-08", "11:40"), History.Kind.NAP),
        History.Interval(ms("2026-11-08", "09:05"), ms("2026-11-08", "13:05"), ms("2026-11-08", "15:05"), History.Kind.NAP),
    )

    private fun forecasts() = listOf(
        LiveTracker.Forecast(ms("2026-11-08", "16:39"), "Сон 3"),
        LiveTracker.Forecast(ms("2026-11-08", "17:49"), "Пробуждение"),
        LiveTracker.Forecast(ms("2026-11-08", "19:24"), "Сон 4"),
        LiveTracker.Forecast(ms("2026-11-08", "20:50"), "Ночной сон"),
    )

    @Test
    fun `лента - факт и прогноз, маркер в тёмном при бодрствовании`() {
        val now = ms("2026-11-08", "16:00")
        val bar = LiveTracker.dayTimeline(intervals(), state("15:05", LiveTracker.Phase.AWAKE), now, forecasts())!!
        // факт: два сна фиолетовым, дыры — тёмным
        val naps = bar.segments.filter { it.isSleep && it.isFact }
        assertEquals(2, naps.size)
        assertEquals(80, naps[0].dur)               // 10:20–11:40
        assertEquals(120, naps[1].dur)              // 13:05–15:05
        // сейчас (16:00) — в тёмном отрезке бодрствования, не во сне
        val live = bar.segments[bar.liveIndex]
        assertTrue(!live.isSleep)
        assertTrue(live.isFact)
        // маркер внутри живого сегмента
        assertTrue(bar.markerFrac in 0f..1f)
    }

    @Test
    fun `лента - прогнозные сны справа после факта`() {
        val now = ms("2026-11-08", "16:00")
        val bar = LiveTracker.dayTimeline(intervals(), state("15:05", LiveTracker.Phase.AWAKE), now, forecasts())!!
        val planSleep = bar.segments.filter { it.isSleep && !it.isFact }
        // Сон 3 (16:39–17:49) и Сон 4 (19:24–20:50) — 2 прогнозных куска
        assertEquals(2, planSleep.size)
        assertEquals(70, planSleep[0].dur)          // 16:39→17:49
        assertTrue(bar.segments.last().relEnd >= 695) // до Ночного сна 20:50
    }

    @Test
    fun `лента - сейчас во сне при фазе NAP`() {
        val now = ms("2026-11-08", "14:00")          // внутри Сна 2 по плану... факт: спит с 13:05
        val bar = LiveTracker.dayTimeline(
            intervals(), state("13:05", LiveTracker.Phase.NAP), now, forecasts())!!
        val live = bar.segments[bar.liveIndex]
        assertTrue(live.isSleep)
        assertTrue(live.isFact)
    }

    @Test
    fun `лента - ночные пробуждения тёмным, не сном`() {
        val dayStart = ms("2026-11-07", "09:00")
        val intervals = listOf(
            History.Interval(dayStart, ms("2026-11-07", "22:00"), ms("2026-11-08", "02:00"), History.Kind.NIGHT),
            History.Interval(dayStart, ms("2026-11-08", "02:00"), ms("2026-11-08", "02:20"), History.Kind.NIGHT_WAKE),
            History.Interval(dayStart, ms("2026-11-08", "02:20"), ms("2026-11-08", "03:00"), History.Kind.NIGHT),
        )
        val st = LiveTracker.State(
            phase = LiveTracker.Phase.NIGHT,
            since = ms("2026-11-08", "02:20"),
            dayStart = dayStart,
            nightStart = ms("2026-11-07", "22:00"),
        )
        val bar = LiveTracker.dayTimeline(intervals, st, ms("2026-11-08", "03:00"), forecasts())!!
        // Ночные пробуждения — бодрствование (тёмное), не сон
        val wakes = bar.segments.filter { it.label == "Ночное бодрствование" }
        assertEquals(1, wakes.size)
        assertTrue(!wakes[0].isSleep)
    }

    // ── exportDay ──

    @Test
    fun `экспорт - подъём, отрезки с длительностями, ночь`() {
        val now = ms("2026-11-08", "16:00")
        val text = LiveTracker.exportDay(intervals(), state("15:05", LiveTracker.Phase.AWAKE), now, "Диметрио", zone)
        assertTrue(text.contains("Диметрио"))
        assertTrue(text.contains("Подъём 09:05"))
        assertTrue(text.contains("😴 Сон 1 — 10:20–11:40 (1 ч 20 мин)"))
        assertTrue(text.contains("⏱ Бодрствование — 11:40–13:05 (1 ч 25 мин)"))
        assertTrue(text.contains("😴 Сон 2 — 13:05–15:05 (2 ч)"))
        assertTrue(text.contains("⏱ Бодрствует с 15:05 — идёт"))
    }

    @Test
    fun `экспорт - прошлая ночь с длительностью`() {
        val intervals = intervals() + History.Interval(
            ms("2026-11-07", "09:05"),
            ms("2026-11-07", "21:35"),
            ms("2026-11-08", "09:05"),
            History.Kind.NIGHT,
        )
        val text = LiveTracker.exportDay(intervals, state("15:05", LiveTracker.Phase.AWAKE), ms("2026-11-08", "16:00"), "Диметрио", zone)
        assertTrue(text.contains("🌙 Прошлой ночью — 21:35–09:05 (11 ч 30 мин)"))
    }

    @Test
    fun `экспорт - ночные пробуждения отдельными строками`() {
        val dayStart = ms("2026-11-07", "09:00")
        val intervals = listOf(
            History.Interval(dayStart, ms("2026-11-07", "22:00"), ms("2026-11-08", "02:00"), History.Kind.NIGHT),
            History.Interval(dayStart, ms("2026-11-08", "02:00"), ms("2026-11-08", "02:20"), History.Kind.NIGHT_WAKE),
            History.Interval(dayStart, ms("2026-11-08", "02:20"), ms("2026-11-08", "03:00"), History.Kind.NIGHT),
        )
        val st = LiveTracker.State(
            phase = LiveTracker.Phase.NIGHT,
            since = ms("2026-11-08", "02:20"),
            dayStart = dayStart,
            nightStart = ms("2026-11-07", "22:00"),
        )
        val text = LiveTracker.exportDay(intervals, st, ms("2026-11-08", "03:00"), "Диметрио", zone)
        assertTrue(text.contains("⏱ Ночное бодрствование — 02:00–02:20 (20 мин)"))
        assertTrue(text.contains("🌙 Ночь — 22:00–02:00 (4 ч)"))
    }

    // ── phaseProgress для виджета: не отрицательный у завершённой ночи ──

    @Test
    fun `phaseProgress у завершённой ночи - 0, не отрицательный`() {
        val st = LiveTracker.State(
            phase = LiveTracker.Phase.NIGHT_AWAKE,
            since = ms("2026-11-08", "02:20"),
            dayStart = ms("2026-11-08", "09:05"),
            nightStart = ms("2026-11-07", "21:35"),
        )
        val next = LiveTracker.Forecast(ms("2026-11-07", "09:05"), "Прошедший")
        assertEquals(0, SleepEngine.phaseProgress(st, next, ms("2026-11-08", "12:00")))
    }
}
