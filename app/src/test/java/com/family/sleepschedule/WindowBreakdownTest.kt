package com.family.sleepschedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** Расшифровка окна бодрствования: план ± компенсация ± regress (§запрос v1.18). */
class WindowBreakdownTest {

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")
    private val age3 = SleepEngine.AGES[0]
    private val plan4 = age3.plans[0]

    private fun ms(d: String, t: String): Long = LocalDateTime.parse("${d}T${t}")
        .atZone(zone).toInstant().toEpochMilli()

    private fun state(woke: String, naps: Int, lastNapMin: Int?): LiveTracker.State =
        LiveTracker.State(
            phase = LiveTracker.Phase.AWAKE,
            since = ms("2026-11-08", woke),
            dayStart = ms("2026-11-08", woke),
            completedNaps = naps,
            lastNapMinutes = lastNapMin,
        )

    @Test
    fun `окно по плану без поправок`() {
        // 0 завершённых снов → окно 1 (85 мин), без компенсаций
        val bd = SleepEngine.windowBreakdown(state("09:05", 0, null), age3, plan4, false)!!
        assertEquals(85, bd.baseMin)
        assertEquals(0, bd.compMin)
        assertEquals(0, bd.regimeMin)
        assertEquals(85, bd.totalMin)
        assertTrue(bd.reasons.any { it.contains("без поправок") })
    }

    @Test
    fun `короткий сон - окно короче с пояснением`() {
        // после Сна 2: план окна 100, факт сна 50 мин → компенсация −15 (макс)
        val bd = SleepEngine.windowBreakdown(state("15:05", 2, 50), age3, plan4, false)!!
        assertEquals(100, bd.baseMin)
        assertEquals(-15, bd.compMin)
        assertEquals(85, bd.totalMin)
        assertTrue(bd.reasons.any { it.contains("короче плана") })
    }

    @Test
    fun `длинный сон - окно длиннее с пояснением`() {
        // после Сна 1: план окна 95, факт сна 95 → +15 (макс)
        val bd = SleepEngine.windowBreakdown(state("11:40", 1, 95), age3, plan4, false)!!
        assertEquals(95, bd.baseMin)
        assertEquals(15, bd.compMin)
        assertEquals(110, bd.totalMin)
        assertTrue(bd.reasons.any { it.contains("длиннее плана") })
    }

    @Test
    fun `regress добавляет своё минус`() {
        val bd = SleepEngine.windowBreakdown(state("09:05", 0, null), age3, plan4, true)!!
        assertEquals(-10, bd.regimeMin)
        assertEquals(75, bd.totalMin)
        assertTrue(bd.reasons.any { it.contains("режим сокращения") })
    }

    @Test
    fun `comp и regress вместе`() {
        // после Сна 2, факт 50 → −15; regress −10; всего 100−15−10=75
        val bd = SleepEngine.windowBreakdown(state("15:05", 2, 50), age3, plan4, true)!!
        assertEquals(75, bd.totalMin)
        assertEquals(-15, bd.compMin)
        assertEquals(-10, bd.regimeMin)
    }

    @Test
    fun `не в бодрствовании - null`() {
        val st = LiveTracker.State(phase = LiveTracker.Phase.NAP, since = ms("2026-11-08", "13:05"))
        assertNull(SleepEngine.windowBreakdown(st, age3, plan4, false))
    }
}
