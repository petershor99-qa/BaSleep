package com.family.sleepschedule

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class HistoryTest {
    private fun at(value: String) = ZonedDateTime.parse(value + "+03:00[Europe/Moscow]").toInstant().toEpochMilli()
    private val dayStart = at("2026-11-03T09:00:00")
    private val zone = ZoneId.of("Europe/Moscow")

    private fun awake(): LiveTracker.State =
        LiveTracker.toggle(LiveTracker.State(), dayStart)

    @Test fun `сериализация и разбор — круговой обход без потерь`() {
        val list = listOf(
            History.Interval(dayStart, at("2026-11-03T10:25:00"), at("2026-11-03T11:45:00"), History.Kind.NAP),
            History.Interval(dayStart, at("2026-11-03T21:35:00"), at("2026-11-04T09:00:00"), History.Kind.NIGHT),
            History.Interval(dayStart, at("2026-11-04T02:00:00"), at("2026-11-04T02:20:00"), History.Kind.NIGHT_WAKE),
        )
        assertEquals(list, History.parse(History.serialize(list)))
    }

    @Test fun `разбор терпит мусор и пустые строки`() {
        assertEquals(emptyList<History.Interval>(), History.parse(null))
        assertEquals(emptyList<History.Interval>(), History.parse(""))
        assertEquals(emptyList<History.Interval>(), History.parse("мусор\n1|2|3|NOPE\na|b|c|NAP"))
        assertEquals(1, History.parse("мусор\n${dayStart}|${dayStart}|${dayStart + 60_000}|NAP").size)
    }

    @Test fun `завершение дневного сна пишет интервал NAP`() {
        val awake = awake()
        val nap = LiveTracker.toggle(awake, at("2026-11-03T10:25:00"))
        val woke = LiveTracker.toggle(nap, at("2026-11-03T11:45:00"))
        val logged = History.transition(nap, woke, woke.since)
        assertEquals(1, logged.size)
        assertEquals(History.Kind.NAP, logged[0].kind)
        assertEquals(80, logged[0].minutes)
        assertEquals(dayStart, logged[0].day)
        // начало сна ничего не пишет
        assertTrue(History.transition(awake, nap, nap.since).isEmpty())
    }

    @Test fun `начало дня пишет ночь от её фактического начала`() {
        val night = LiveTracker.startNight(awake(), at("2026-11-03T21:35:00"))
        val day = LiveTracker.startDay(night, at("2026-11-04T08:50:00"))
        val logged = History.transition(night, day, at("2026-11-04T08:50:00"))
        assertEquals(1, logged.size)
        assertEquals(History.Kind.NIGHT, logged[0].kind)
        assertEquals(at("2026-11-03T21:35:00"), logged[0].start)
        assertEquals(at("2026-11-04T08:50:00"), logged[0].end)
        assertEquals(dayStart, logged[0].day)
    }

    @Test fun `ночные пробуждения пишутся отдельно и не обрезают ночь`() {
        val night = LiveTracker.startNight(awake(), at("2026-11-03T21:35:00"))
        val woke = LiveTracker.toggle(night, at("2026-11-04T02:00:00"))
        val sleeping = LiveTracker.toggle(woke, at("2026-11-04T02:20:00"))
        val wakeLogged = History.transition(woke, sleeping, sleeping.since)
        assertEquals(1, wakeLogged.size)
        assertEquals(History.Kind.NIGHT_WAKE, wakeLogged[0].kind)
        assertEquals(20, wakeLogged[0].minutes)
        val day = LiveTracker.startDay(sleeping, at("2026-11-04T09:00:00"))
        val nightLogged = History.transition(sleeping, day, at("2026-11-04T09:00:00"))
        assertEquals(at("2026-11-03T21:35:00"), nightLogged[0].start)
    }

    @Test fun `день после ночного пробуждения закрывает ночь на момент пробуждения`() {
        val night = LiveTracker.startNight(awake(), at("2026-11-03T21:35:00"))
        val woke = LiveTracker.toggle(night, at("2026-11-04T08:30:00"))
        val day = LiveTracker.startDay(woke, at("2026-11-04T08:45:00"))
        val logged = History.transition(woke, day, at("2026-11-04T08:45:00"))
        assertEquals(1, logged.size)
        assertEquals(at("2026-11-04T08:30:00"), logged[0].end)
    }

    @Test fun `итоги текущего дня учитывают журнал и текущую фазу`() {
        // День: подъём 09:00, сон 10:25–11:45 (80), сейчас спит с 13:20, сейчас 13:55.
        val logged = listOf(
            History.Interval(dayStart, at("2026-11-03T10:25:00"), at("2026-11-03T11:45:00"), History.Kind.NAP),
        )
        val state = LiveTracker.State(LiveTracker.Phase.NAP, at("2026-11-03T13:20:00"),
            dayStart = dayStart, completedNaps = 1)
        val t = History.totals(logged, state, at("2026-11-03T13:55:00"))
        assertEquals(80 + 35, t.daySleepMin)
        assertEquals(0, t.nightSleepMin)
        // бодрствование: 09:00–13:55 (295) минус сны (115)
        assertEquals(295 - 115, t.dayAwakeMin)
    }

    @Test fun `итоги после ухода в ночь считают бодрствование до начала ночи`() {
        val logged = listOf(
            History.Interval(dayStart, at("2026-11-03T10:25:00"), at("2026-11-03T11:45:00"), History.Kind.NAP),
        )
        val night = LiveTracker.startNight(
            LiveTracker.State(LiveTracker.Phase.AWAKE, at("2026-11-03T20:15:00"), dayStart = dayStart, completedNaps = 1),
            at("2026-11-03T21:35:00"))
        val t = History.totals(logged, night, at("2026-11-04T02:00:00"))
        assertEquals(80, t.daySleepMin)
        // ночь: 21:35 → 02:00 = 265 мин (текущая фаза)
        assertEquals(265, t.nightSleepMin)
        // бодрствование днём: 09:00–21:35 (755) минус сон (80)
        assertEquals(755 - 80, t.dayAwakeMin)
    }

    @Test fun `отмена убирает ровно столько интервалов сколько добавило действие`() {
        val logged = listOf(
            History.Interval(dayStart, at("2026-11-03T10:25:00"), at("2026-11-03T11:45:00"), History.Kind.NAP),
            History.Interval(dayStart, at("2026-11-03T13:20:00"), at("2026-11-03T14:40:00"), History.Kind.NAP),
        )
        assertEquals(1, History.dropLast(logged, 1).size)
        assertEquals(0, History.dropLast(logged, 5).size)
        assertEquals(2, History.dropLast(logged, 0).size)
    }

    @Test fun `сброс дня удаляет только его интервалы`() {
        val other = at("2026-11-02T09:00:00")
        val logged = listOf(
            History.Interval(other, at("2026-11-02T10:25:00"), at("2026-11-02T11:45:00"), History.Kind.NAP),
            History.Interval(dayStart, at("2026-11-03T10:25:00"), at("2026-11-03T11:45:00"), History.Kind.NAP),
        )
        val kept = History.removeDay(logged, dayStart)
        assertEquals(1, kept.size)
        assertEquals(other, kept[0].day)
    }

    @Test fun `журнал обрезается до четырнадцати дней`() {
        val now = at("2026-11-17T12:00:00")
        val fresh = History.Interval(at("2026-11-10T09:00:00"), now - 3600_000, now - 1800_000, History.Kind.NAP)
        val stale = History.Interval(at("2026-10-20T09:00:00"), at("2026-10-20T10:00:00"), at("2026-10-20T11:00:00"), History.Kind.NAP)
        val kept = History.keepRecent(listOf(fresh, stale), now)
        assertEquals(listOf(fresh), kept)
    }

    @Test fun `сводка дня считает сны ночь и бодрствование`() {
        val logged = listOf(
            History.Interval(dayStart, at("2026-11-03T10:25:00"), at("2026-11-03T11:45:00"), History.Kind.NAP),
            History.Interval(dayStart, at("2026-11-03T13:20:00"), at("2026-11-03T14:40:00"), History.Kind.NAP),
            History.Interval(dayStart, at("2026-11-03T21:35:00"), at("2026-11-04T09:00:00"), History.Kind.NIGHT),
            History.Interval(dayStart, at("2026-11-04T02:00:00"), at("2026-11-04T02:20:00"), History.Kind.NIGHT_WAKE),
        )
        val days = History.days(logged)
        assertEquals(1, days.size)
        val d = days[0]
        assertEquals(2, d.naps.size)
        assertEquals(160, d.daySleepMin)
        assertEquals(685, d.nightSleepMin)
        assertEquals(20, d.nightWakeMin)
        assertEquals(845, d.totalSleepMin)
        // бодрствование днём: 09:00–21:35 (755) минус сны (160)
        assertEquals(755 - 160, d.dayAwakeMin)
    }
}
