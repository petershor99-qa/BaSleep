package com.family.sleepschedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Полоса дня (§3.1–3.5), ночной сон «Ночью сейчас» (§3.7), phaseHint,
 * возраст по дате рождения. Все времена фиксированы: Europe/Moscow,
 * никаких реальных «сейчас».
 */
class DayBarNightTest {

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")
    private val age3 = SleepEngine.AGES[0]
    private val plan4 = age3.plans[0]

    /** «2026-11-06», «09:00» → epoch millis в Europe/Moscow. */
    private fun ms(date: String, time: String): Long {
        val dt = LocalDateTime.parse("${date}T${time}")
        return dt.atZone(zone).toInstant().toEpochMilli()
    }

    // ── §3.1: локальная зона вместо UTC ──

    @Test
    fun `московский подъём 09_00 даёт wakeTod 540, а не 360`() {
        val dayStart = ms("2026-11-06", "09:00")
        val bar = SleepEngine.dayBar(age3, plan4, false, dayStart, ms("2026-11-06", "10:00"), zone)
        assertEquals(540, bar.wakeTod)
        assertEquals("09:00", SleepEngine.fmtClock(bar.wakeTod))
    }

    // ── §3.2: сдвинутый день и полночь ──

    /** Схема «3 мес · 4 сна» с подъёмом 11:53 даёт отбой 00:28 (через полночь). */
    private fun dayShifted(): Long {
        val probe = SleepEngine.build(age3, plan4, 11 * 60 + 53)
        assertEquals(28, probe.bedtime % 1440)
        return ms("2026-11-05", "11:53")
    }

    @Test
    fun `подъём 11_53 отбой 00_28 сейчас 00_10 - прошло 737 до отбоя 18`() {
        val dayStart = dayShifted()
        val bar = SleepEngine.dayBar(age3, plan4, false, dayStart, ms("2026-11-06", "00:10"), zone)
        assertEquals(737, bar.nowRel)
        assertEquals(18, bar.untilBedMin)
        assertTrue(bar.currentIndex >= 0)           // маркер не на конце дня
    }

    @Test
    fun `подъём 11_53 сейчас 01_00 - после отбоя, без лишних суток`() {
        val dayStart = dayShifted()
        val bar = SleepEngine.dayBar(age3, plan4, false, dayStart, ms("2026-11-06", "01:00"), zone)
        assertEquals(bar.dayLen, bar.nowRel)
        assertEquals(-1, bar.currentIndex)
        assertEquals(0, bar.untilBedMin)
    }

    @Test
    fun `время ровно в полночь - 727 минут от подъёма`() {
        val dayStart = dayShifted()
        val bar = SleepEngine.dayBar(age3, plan4, false, dayStart, ms("2026-11-06", "00:00"), zone)
        assertEquals(727, bar.nowRel)               // 11:53 → 00:00 = 727 мин
    }

    @Test
    fun `обычный день 09_00 - 21_35 не регрессирует`() {
        val dayStart = ms("2026-11-06", "09:00")
        val bar = SleepEngine.dayBar(age3, plan4, false, dayStart, ms("2026-11-06", "12:01"), zone)
        assertEquals(181, bar.nowRel)               // 09:00 → 12:01
        assertEquals(2, bar.currentIndex)           // второе окно бодрствования
        assertEquals("21:35", SleepEngine.fmtClock(bar.bedtimeTod))
    }

    // ── §3.4: ровно один текущий сегмент, границы ──

    @Test
    fun `границы сегментов - подъём сон сон-конец отбой`() {
        val dayStart = ms("2026-11-06", "09:00")
        assertEquals(0, SleepEngine.dayBar(age3, plan4, false, dayStart, ms("2026-11-06", "09:00"), zone).currentIndex)
        assertEquals(1, SleepEngine.dayBar(age3, plan4, false, dayStart, ms("2026-11-06", "10:25"), zone).currentIndex)
        assertEquals(2, SleepEngine.dayBar(age3, plan4, false, dayStart, ms("2026-11-06", "11:45"), zone).currentIndex)
        val atBedtime = SleepEngine.dayBar(age3, plan4, false, dayStart, ms("2026-11-06", "21:35"), zone)
        assertEquals(-1, atBedtime.currentIndex)
        assertEquals(755, atBedtime.nowRel)         // 09:00 → 21:35 = 755 мин
    }

    @Test
    fun `ровно один сегмент подсвечен во всех точках дня`() {
        val dayStart = ms("2026-11-06", "09:00")
        val count = SleepEngine.dayBar(age3, plan4, false, dayStart, dayStart, zone).segments.size
        var t = ms("2026-11-06", "09:00")
        val end = ms("2026-11-06", "21:34")
        while (t <= end) {
            val idx = SleepEngine.dayBar(age3, plan4, false, dayStart, t, zone).currentIndex
            assertTrue("index $idx в $t", idx in 0 until count)
            t += 5 * 60_000L
        }
    }

    // ── §3.5: regress на полосе ──

    @Test
    fun `regress сдвигает отбой полосы 21_35 - 20_45`() {
        val dayStart = ms("2026-11-06", "09:00")
        val with = SleepEngine.dayBar(age3, plan4, true, dayStart, ms("2026-11-06", "12:01"), zone)
        val without = SleepEngine.dayBar(age3, plan4, false, dayStart, ms("2026-11-06", "12:01"), zone)
        assertEquals("20:45", SleepEngine.fmtClock(with.bedtimeTod))
        assertEquals("21:35", SleepEngine.fmtClock(without.bedtimeTod))
        assertEquals(with.dayLen, with.segments.sumOf { it.dur })
    }

    // ── §3.7: ночной сон «Ночью сейчас» ──

    /** Ночь 21:35, ночное бодрствование 02:00–02:20. */
    private fun nightState(phase: LiveTracker.Phase, since: String, now: String):
            Triple<LiveTracker.State, List<History.Interval>, Long> {
        val nightStart = ms("2026-11-05", "21:35")
        val wakeStart = ms("2026-11-06", "02:00")
        val wakeEnd = ms("2026-11-06", "02:20")
        val state = LiveTracker.State(
            phase = phase,
            since = ms("2026-11-06", since),
            previousAt = wakeStart,
            dayStart = ms("2026-11-05", "09:00"),
            completedNaps = 4,
            nightStart = nightStart,
        )
        val intervals = listOf(History.Interval(state.dayStart, wakeStart, wakeEnd, History.Kind.NIGHT_WAKE))
        return Triple(state, intervals, ms("2026-11-06", now))
    }

    @Test
    fun `ночь 21_35 бодрствование 02_00-02_20 сейчас 03_00 - ночной сон 305 минут`() {
        val (state, intervals, now) = nightState(LiveTracker.Phase.NIGHT, "02:20", "03:00")
        // 21:35→02:00 = 265 мин + 02:20→03:00 = 40 мин − 0 = 305.
        // (Постановка называла 185 — арифметика сценария даёт ровно 305.)
        assertEquals(305, SleepEngine.nightSleepMinutes(intervals, state, now))
    }

    @Test
    fun `в 02_10 во время ночного бодрствования - ночной сон 265 минут`() {
        val (state, intervals, now) = nightState(LiveTracker.Phase.NIGHT_AWAKE, "02:00", "02:10")
        // NIGHT_AWAKE: ночь считается до момента пробуждения (02:00).
        assertEquals(265, SleepEngine.nightSleepMinutes(intervals, state, now))
    }

    @Test
    fun `два ночных пробуждения не задваиваются`() {
        val nightStart = ms("2026-11-05", "21:35")
        val state = LiveTracker.State(
            phase = LiveTracker.Phase.NIGHT,
            since = ms("2026-11-06", "03:00"),
            previousAt = ms("2026-11-06", "02:40"),
            dayStart = ms("2026-11-05", "09:00"),
            completedNaps = 4,
            nightStart = nightStart,
        )
        val intervals = listOf(
            History.Interval(state.dayStart, ms("2026-11-06", "02:00"), ms("2026-11-06", "02:20"), History.Kind.NIGHT_WAKE),
            History.Interval(state.dayStart, ms("2026-11-06", "02:40"), ms("2026-11-06", "02:50"), History.Kind.NIGHT_WAKE),
        )
        // 325 мин ночи − 20 − 10 = 295
        assertEquals(295, SleepEngine.nightSleepMinutes(intervals, state, ms("2026-11-06", "03:00")))
    }

    @Test
    fun `нет nightStart - null, плитка покажет прочерк`() {
        val state = LiveTracker.State(phase = LiveTracker.Phase.NIGHT, since = ms("2026-11-06", "23:00"))
        assertNull(SleepEngine.nightSleepMinutes(emptyList(), state, ms("2026-11-06", "23:30")))
    }

    // ── phaseHint: near/over ──

    @Test
    fun `phaseHint - осталось 10 минут это near, 11 - нет, перебор это over`() {
        val dayStart = ms("2026-11-06", "09:00")
        val state = LiveTracker.State(phase = LiveTracker.Phase.AWAKE, since = dayStart, dayStart = dayStart)
        fun hint(at: String) = SleepEngine.phaseHint(
            state, age3, LiveTracker.Forecast(ms("2026-11-06", "10:25"), "Сон 1"), ms("2026-11-06", at))!!

        val near = hint("10:15")                    // осталось 10 мин
        assertTrue(near.near && !near.over)
        val notNear = hint("10:14")                 // осталось 11 мин
        assertTrue(!notNear.near && !notNear.over)
        val over = hint("10:26")                    // окно вышло
        assertTrue(over.over && !over.near)
        val exact = hint("10:25")                   // ровно в ориентир: окно израсходовано
        assertEquals(1f, exact.fraction)
        assertTrue(!exact.over && !exact.near)
    }

    // ── childAgeText / parseBirthDate ──

    @Test
    fun `возраст - ровно месяц, месяц плюс дни, первый месяц, будущее`() {
        assertEquals("3 мес", SleepEngine.childAgeText(ms("2026-07-17", "12:00"), LocalDate.of(2026, 10, 17)))
        assertEquals("3 мес 20 дн", SleepEngine.childAgeText(ms("2026-07-17", "12:00"), LocalDate.of(2026, 11, 6)))
        assertEquals("20 дн", SleepEngine.childAgeText(ms("2026-07-17", "12:00"), LocalDate.of(2026, 8, 6)))
        assertNull(SleepEngine.childAgeText(ms("2026-07-17", "12:00"), LocalDate.of(2026, 7, 16)))
    }

    @Test
    fun `parseBirthParts - валид, мусор, будущее, несуществующая дата`() {
        val today = LocalDate.of(2026, 11, 6)
        assertNotNull(SleepEngine.parseBirthParts("17", "7", "2026", today))
        assertNull(SleepEngine.parseBirthParts("99", "13", "2026", today))    // день/месяц вне диапазона
        assertNull(SleepEngine.parseBirthParts("30", "2", "2026", today))     // 30 февраля
        assertNull(SleepEngine.parseBirthParts("17", "7", "2999", today))     // будущее
        assertNull(SleepEngine.parseBirthParts("", "", "", today))            // пустые
        assertNull(SleepEngine.parseBirthParts("1", "1", "199", today))       // неполный год
    }

    // ── phaseProgress для виджета ──

    @Test
    fun `phaseProgress - границы 0 и 1000, без отрицательных`() {
        val since = ms("2026-11-06", "09:00")
        val next = LiveTracker.Forecast(ms("2026-11-06", "10:30"), "Сон 1")
        assertEquals(0, SleepEngine.phaseProgress(LiveTracker.State(since = since), next, since))
        assertEquals(1000, SleepEngine.phaseProgress(LiveTracker.State(since = since), next, ms("2026-11-06", "10:30")))
        assertEquals(1000, SleepEngine.phaseProgress(LiveTracker.State(since = since), next, ms("2026-11-06", "11:00")))
        assertEquals(500, SleepEngine.phaseProgress(LiveTracker.State(since = since), next, ms("2026-11-06", "09:45")))
        assertEquals(0, SleepEngine.phaseProgress(LiveTracker.State(since = since), null, ms("2026-11-06", "10:00")))
    }
}
