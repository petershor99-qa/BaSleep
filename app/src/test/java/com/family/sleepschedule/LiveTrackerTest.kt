package com.family.sleepschedule

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class LiveTrackerTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private fun at(value: String) = ZonedDateTime.parse(value + "+03:00[Europe/Moscow]").toInstant().toEpochMilli()
    private val morning = at("2026-11-03T09:03:00")
    private val age = SleepEngine.AGES[0]
    private val plan = age.plans.first()

    @Test fun `кнопки записывают минуту телефона без округления до пяти`() {
        val awake = LiveTracker.toggle(LiveTracker.State(), morning + 35_000)
        assertEquals(LiveTracker.Phase.AWAKE, awake.phase)
        assertEquals(morning, awake.since)
        val nap = LiveTracker.toggle(awake, morning + 91 * LiveTracker.MINUTE)
        assertEquals(morning + 91 * LiveTracker.MINUTE, nap.since)
    }
    @Test fun `фактическое бодрствование шесть часов и сон восемь часов не ужимаются`() {
        val awake = LiveTracker.toggle(LiveTracker.State(), morning)
        val nap = LiveTracker.toggle(awake, morning + 360 * LiveTracker.MINUTE)
        val after = LiveTracker.toggle(nap, nap.since + 480 * LiveTracker.MINUTE)
        assertEquals(480, after.lastNapMinutes)
        assertEquals(nap.since + 480 * LiveTracker.MINUTE, after.since)
        assertEquals(1, after.completedNaps)
    }
    @Test fun `сон короче двадцати минут сохраняется точно`() {
        val awake = LiveTracker.toggle(LiveTracker.State(), morning)
        val nap = LiveTracker.toggle(awake, morning + 20 * LiveTracker.MINUTE)
        val after = LiveTracker.toggle(nap, nap.since + 2 * LiveTracker.MINUTE)
        assertEquals(2, after.lastNapMinutes)
    }
    @Test fun `коррекция времени свободна в пределах хронологии`() {
        val awake = LiveTracker.toggle(LiveTracker.State(), morning)
        val nap = LiveTracker.toggle(awake, morning + 300 * LiveTracker.MINUTE)
        val corrected = LiveTracker.correct(nap, morning + LiveTracker.MINUTE, nap.since)
        assertEquals(morning + LiveTracker.MINUTE, corrected.since)
        val woke = LiveTracker.toggle(corrected, nap.since)
        val amended = LiveTracker.correct(woke, corrected.since + LiveTracker.MINUTE, nap.since)
        assertEquals(1, amended.lastNapMinutes)
    }
    @Test fun `коррекция через полночь выбирает вчерашнее время`() {
        val now = at("2026-11-04T00:10:00")
        assertEquals(at("2026-11-03T23:55:00"), LiveTracker.clockTime(23, 55, now, zone))
        assertEquals(at("2026-11-04T00:05:00"), LiveTracker.clockTime(0, 5, now, zone))
    }
    @Test fun `будущее и пересечение предыдущего события отклоняются`() {
        val awake = LiveTracker.toggle(LiveTracker.State(), morning)
        val nap = LiveTracker.toggle(awake, morning + 60 * LiveTracker.MINUTE)
        assertThrows(IllegalArgumentException::class.java) { LiveTracker.correct(nap, morning - 1 * LiveTracker.MINUTE, nap.since) }
        assertThrows(IllegalArgumentException::class.java) { LiveTracker.correct(nap, nap.since + LiveTracker.MINUTE, nap.since) }
        assertThrows(IllegalArgumentException::class.java) { LiveTracker.clockTime(24, 0, nap.since, zone) }
    }
    @Test fun `дневной сон переводится в ночной без потери начала`() {
        val awake = LiveTracker.toggle(LiveTracker.State(), morning)
        val nap = LiveTracker.toggle(awake, at("2026-11-03T21:03:00"))
        val night = LiveTracker.startNight(nap, at("2026-11-03T22:00:00"))
        assertEquals(nap.since, night.since)
        assertEquals(nap.since, night.nightStart)
        assertEquals(nap.previousAt, night.previousAt)
    }
    @Test fun `ночное пробуждение не начинает день и сохраняет начало ночи`() {
        val night = LiveTracker.startNight(LiveTracker.toggle(LiveTracker.State(), morning), at("2026-11-03T22:00:00"))
        val woke = LiveTracker.toggle(night, at("2026-11-04T02:00:00"))
        assertEquals(LiveTracker.Phase.NIGHT_AWAKE, woke.phase)
        val sleeping = LiveTracker.toggle(woke, at("2026-11-04T02:20:00"))
        assertEquals(LiveTracker.Phase.NIGHT, sleeping.phase)
        assertEquals(night.nightStart, sleeping.nightStart)
        assertEquals(night.dayStart, sleeping.dayStart)
    }
    @Test fun `утро начинается со времени пробуждения а не нажатия начать день`() {
        val night = LiveTracker.startNight(LiveTracker.toggle(LiveTracker.State(), morning), at("2026-11-03T22:00:00"))
        val woke = LiveTracker.toggle(night, at("2026-11-04T08:30:00"))
        val day = LiveTracker.startDay(woke, at("2026-11-04T08:45:00"))
        assertEquals(woke.since, day.since)
        assertEquals(woke.since, day.dayStart)
        assertEquals(0, day.completedNaps)
        assertEquals(0L, day.nightStart)
    }
    @Test fun `прогноз отталкивается от факта но не меняет его`() {
        val awake = LiveTracker.toggle(LiveTracker.State(), morning)
        val shifted = awake.copy(since = morning + 180 * LiveTracker.MINUTE)
        val first = LiveTracker.forecasts(awake, age, plan, false)
        val second = LiveTracker.forecasts(shifted, age, plan, false)
        assertEquals(first.map { it.at + 180 * LiveTracker.MINUTE }, second.map { it.at })
        assertEquals(morning, awake.since)
        assertEquals(shifted.since + plan.wakeWindows.first() * LiveTracker.MINUTE, second.first().at)
    }
    @Test fun `прогноз после длинного и короткого сна компенсируется а факт сохраняется`() {
        for (duration in listOf(2, 480)) {
            val nap = LiveTracker.toggle(LiveTracker.toggle(LiveTracker.State(), morning), morning + 60 * LiveTracker.MINUTE)
            val awake = LiveTracker.toggle(nap, nap.since + duration * LiveTracker.MINUTE)
            val target = LiveTracker.forecasts(awake, age, plan, false).first().at
            val delta = ((target - awake.since) / LiveTracker.MINUTE).toInt()
            assertTrue(delta in age.hardMin..age.hardMax)
            assertEquals(duration, awake.lastNapMinutes)
        }
    }
    @Test fun `дополнительный сон после конца схемы разрешён и имеет прогноз ночи`() {
        val awake = LiveTracker.State(LiveTracker.Phase.AWAKE, morning, dayStart = morning, completedNaps = plan.naps + 2)
        val nap = LiveTracker.toggle(awake, morning + 90 * LiveTracker.MINUTE)
        val forecasts = LiveTracker.forecasts(nap, age, plan, false)
        assertEquals(listOf("Пробуждение", "Ночной сон"), forecasts.map { it.title })
    }
    @Test fun `ночные кормления переходят через полночь без сдвига из-за сна`() {
        val now = at("2026-11-03T23:55:00")
        assertEquals(at("2026-11-04T00:00:00"), LiveTracker.nextFeed(listOf(0, 270, 540), now, zone))
        assertEquals(at("2026-11-04T04:30:00"), LiveTracker.nextFeed(listOf(0, 270, 540), at("2026-11-04T00:01:00"), zone))
        assertNull(LiveTracker.nextFeed(emptyList(), now, zone))
    }
    @Test fun `смена часов назад не записывает отрицательный сон`() {
        val awake = LiveTracker.toggle(LiveTracker.State(), morning)
        assertThrows(IllegalArgumentException::class.java) { LiveTracker.toggle(awake, morning - LiveTracker.MINUTE) }
    }
    @Test fun `все схемы дают упорядоченный прогноз без изменения фактов`() {
        for (a in SleepEngine.AGES) for (p in a.plans) for (regress in listOf(false, true)) {
            val state = LiveTracker.State(LiveTracker.Phase.AWAKE, morning, dayStart = morning)
            val forecast = LiveTracker.forecasts(state, a, p, regress)
            assertEquals(forecast.map { it.at }.sorted(), forecast.map { it.at })
            assertEquals(p.naps * 2 + 1, forecast.size)
            assertEquals("Ночной сон", forecast.last().title)
            assertEquals(morning, state.since)
        }
    }
}
