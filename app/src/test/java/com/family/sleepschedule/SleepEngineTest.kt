package com.family.sleepschedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Движок v2: ручные правки отделены от режима регресса, клэмп на входе
 * (скрытого запаса нет), утреннее кормление «до подъёма», человеческие
 * форматы длительностей, все прежние инварианты сохранены.
 */
class SleepEngineTest {

    private val age3 = SleepEngine.AGES[0]

    @Test
    fun `все схемы всех возрастов сходятся в коридорах норм`() {
        for (age in SleepEngine.AGES) {
            for (plan in age.plans) {
                assertEquals("сны ${age.label}/${plan.name}", plan.naps, plan.napDurations.size)
                assertEquals("окна ${age.label}/${plan.name}", plan.naps + 1, plan.wakeWindows.size)
                assertTrue(
                    "окна в норме ${age.label}/${plan.name}: ${plan.wakeWindows}",
                    plan.wakeWindows.all { it in age.wwMin..age.wwMax },
                )
                val day = plan.napDurations.sum()
                val ww = plan.wakeWindows.sum()
                val night = 1440 - day - ww
                assertTrue("день ${age.label}/${plan.name}: $day", day in age.dayMin..age.dayMax)
                assertTrue("ночь ${age.label}/${plan.name}: $night", night in age.nightMin..age.nightMax)
                assertTrue(
                    "всего ${age.label}/${plan.name}: ${day + night}",
                    day + night in age.totalMin..age.totalMax,
                )
            }
        }
    }

    @Test
    fun `схема 4 сна — контрольный день по правилам консультанта`() {
        val plan = age3.plans[0]
        val s = SleepEngine.build(age3, plan, 9 * 60)
        assertEquals(listOf(625, 800, 980, 1155), s.naps.map { it.start })
        assertEquals(listOf(705, 880, 1050, 1215), s.naps.map { it.end })
        assertEquals(290, s.daySleep)
        assertEquals(1295, s.bedtime)           // 21:35
        assertEquals(685, s.nightSleep)         // 11:25
        assertEquals(975, s.totalSleep)         // 16:15
        assertFalse(s.regress)
        assertFalse(s.hasFacts)
    }

    @Test
    fun `схема 5 снов — тот же отбой и ночь`() {
        val plan = age3.plans[1]
        val s = SleepEngine.build(age3, plan, 9 * 60)
        assertEquals(listOf(620, 770, 920, 1085, 1190), s.naps.map { it.start })
        assertEquals(265, s.daySleep)
        assertEquals(1295, s.bedtime)
        assertEquals(685, s.nightSleep)
    }

    @Test
    fun `кормления ложатся в окна в обеих схемах`() {
        for (plan in age3.plans) {
            val s = SleepEngine.build(age3, plan, 9 * 60)
            val p = SleepEngine.placeFeeds(s, SleepEngine.DEFAULT_FEEDS)
            assertEquals(SleepEngine.FeedKind.AT_WAKE, p[2].kind)
            assertEquals(SleepEngine.FeedKind.IN_WINDOW, p[3].kind)
            assertEquals(2, p[3].slot)
            assertEquals(SleepEngine.FeedKind.IN_WINDOW, p[4].kind)
            assertEquals(SleepEngine.FeedKind.IN_WINDOW, p[5].kind)
            assertEquals(SleepEngine.FeedKind.IN_WINDOW, p[6].kind)
            assertEquals(plan.naps + 1, p[6].slot)
            assertEquals(SleepEngine.FeedKind.NIGHT, p[0].kind)
            assertEquals(SleepEngine.FeedKind.NIGHT, p[1].kind)
            assertTrue("наездов нет (${plan.name})", p.none { it.kind == SleepEngine.FeedKind.IN_NAP })
        }
    }

    @Test
    fun `режим регресса отделён от ручных правок`() {
        val plan = age3.plans[0]
        val s = SleepEngine.build(age3, plan, 9 * 60, SleepEngine.Adjustments(), regress = true)
        assertEquals(listOf(75, 85, 90, 95, 70), s.windows.map { it.effective })
        // режим — не ручная правка: manual чист, hasFacts нет
        assertTrue(s.windows.all { !it.hasManual })
        assertEquals(-SleepEngine.REGRESS_STEP, s.windows[0].regime)
        assertFalse(s.hasFacts)
        assertTrue(s.regress)
        assertEquals(1245, s.bedtime)          // 20:45
    }

    @Test
    fun `sanitize не даёт копить скрытый запас за границей`() {
        val plan = age3.plans[0]
        // безумная правка +1000 клэмпится на входе…
        val adj = SleepEngine.sanitizeAdj(
            age3, plan,
            SleepEngine.Adjustments(wwOffsets = listOf(1000, 0, 0, 0, 0)),
            regress = false,
        )
        assertEquals(25, adj.wwOffsets[0])  // максимум для окна 1: 110 − 85
        // …и обратный тап сразу меняет длительность
        val back = SleepEngine.sanitizeAdj(
            age3, plan, adj.copy(wwOffsets = listOf(20, 0, 0, 0, 0)), regress = false,
        )
        assertEquals(20, back.wwOffsets[0])
        val s = SleepEngine.build(age3, plan, 9 * 60, back)
        assertEquals(105, s.windows[0].effective)
    }

    @Test
    fun `sanitize учитывает режим при границе ручной правки`() {
        val plan = age3.plans[0]
        val adj = SleepEngine.sanitizeAdj(
            age3, plan, SleepEngine.Adjustments(wwOffsets = listOf(30, 0, 0, 0, 0)), regress = true,
        )
        // с регрессом (−10) потолок для окна 1: 110 − 85 + 10 = 35
        assertTrue(adj.wwOffsets[0] <= 35)
        val s = SleepEngine.build(age3, plan, 9 * 60, adj, regress = true)
        assertTrue(s.windows.all { it.effective in age3.hardMin..age3.hardMax })
    }

    @Test
    fun `утреннее кормление до подъёма — не ночь`() {
        val plan = age3.plans[0]
        val s = SleepEngine.build(age3, plan, 9 * 60 + 5)  // подъём 09:05
        val p = SleepEngine.placeFeeds(s, listOf(540, 270, 0))
        // список отсортирован: [00:00, 04:30, 09:00]
        assertEquals(SleepEngine.FeedKind.BEFORE_WAKE, p[2].kind)  // 09:00
        assertEquals(SleepEngine.FeedKind.NIGHT, p[1].kind)        // 04:30
        assertEquals(SleepEngine.FeedKind.NIGHT, p[0].kind)        // 00:00
    }

    @Test
    fun `факт сна авто-компенсирует следующее окно`() {
        val plan = age3.plans[0]
        val s = SleepEngine.build(age3, plan, 9 * 60, SleepEngine.Adjustments(napOffsets = listOf(-10)))
        assertEquals(695, s.naps[0].end)
        assertEquals(-10, s.windows[1].comp)
        assertEquals(85, s.windows[1].effective)
        assertEquals(780, s.naps[1].start)
        assertTrue(s.hasFacts)
    }

    @Test
    fun `ручная правка окна сдвигает хвост`() {
        val plan = age3.plans[0]
        val base = SleepEngine.build(age3, plan, 9 * 60)
        val s = SleepEngine.build(age3, plan, 9 * 60, SleepEngine.Adjustments(wwOffsets = listOf(5)))
        assertEquals(90, s.windows[0].effective)
        assertTrue(s.windows[0].hasManual)
        assertEquals(base.bedtime + 5, s.bedtime)
        assertEquals(base.nightSleep - 5, s.nightSleep)
    }

    @Test
    fun `экстремальные правки не ломают ночь`() {
        val plan = age3.plans[0]
        val s = SleepEngine.build(
            age3, plan, 5 * 60,
            SleepEngine.Adjustments(napOffsets = List(4) { 500 }, wwOffsets = List(5) { 500 }),
        )
        assertTrue("ночь не короче 5 ч: ${s.nightSleep}", s.nightSleep >= 300)
        assertTrue(s.naps.all { it.duration in 20..240 })
        assertEquals(0, s.bedtime % 5)
    }

    @Test
    fun `границы фаз сейчас`() {
        val plan = age3.plans[0]
        val s = SleepEngine.build(age3, plan, 9 * 60)
        val atNapEnd = SleepEngine.nowInfo(s, 705)
        assertEquals(SleepEngine.Phase.WINDOW, atNapEnd.phase)
        assertEquals(95, atNapEnd.minutesLeft)
        assertEquals(SleepEngine.Phase.NIGHT, SleepEngine.nowInfo(s, 1295).phase)
        val atWake = SleepEngine.nowInfo(s, 540)
        assertEquals(SleepEngine.Phase.WINDOW, atWake.phase)
        assertEquals(0, atWake.windowIndex)
    }

    @Test
    fun `границы кормлений`() {
        val plan = age3.plans[0]
        val s = SleepEngine.build(age3, plan, 9 * 60)
        assertEquals(SleepEngine.FeedKind.IN_NAP, SleepEngine.placeFeeds(s, listOf(625))[0].kind)
        assertEquals(SleepEngine.FeedKind.AFTER_BEDTIME, SleepEngine.placeFeeds(s, listOf(1295))[0].kind)
        assertEquals(SleepEngine.FeedKind.IN_WINDOW, SleepEngine.placeFeeds(s, listOf(1290))[0].kind)
    }

    @Test
    fun `экспорт — формат с абзацами и человеческими длительностями`() {
        val plan = age3.plans[0]
        val s = SleepEngine.build(age3, plan, 9 * 60)
        val text = SleepEngine.exportText(s, age3, plan, SleepEngine.DEFAULT_FEEDS)
        assertTrue(text.startsWith("Режим сна — 3 мес · 4 сна"))
        assertTrue(text.contains("Подъём 09:00"))
        assertTrue(text.contains("🍼 09:00 · Бодрствование 1 ч 25 мин"))
        assertTrue(text.contains("😴 Сон 1 — 10:25–11:45 (1 ч 20 мин)"))
        assertTrue(text.contains("🍼 12:00 · Бодрствование 1 ч 35 мин"))
        assertTrue(text.contains("🍼 21:00 · Бодрствование 1 ч 20 мин"))
        assertTrue(text.contains("🌙 Ночь — 21:35–09:00 (11 ч 25 мин)"))
        assertTrue(text.contains("🍼 Ночью: 00:00 · 04:30"))
        assertTrue(text.contains("⏱ Бодрствование всего 7 ч 45 мин"))
        assertTrue(text.contains("📊 Сон днём 4 ч 50 мин · ночью 11 ч 25 мин · всего 16 ч 15 мин"))
        assertFalse("без правок строка не выводится", text.contains("Правки"))
        assertTrue("секции разделены пустой строкой", text.contains("\n\n🍼 12:00"))
    }

    @Test
    fun `экспорт с регрессом и правками помечает их отдельно`() {
        val plan = age3.plans[0]
        val s = SleepEngine.build(
            age3, plan, 9 * 60,
            SleepEngine.Adjustments(napOffsets = listOf(-5), wwOffsets = listOf(0, 5)),
            regress = true,
        )
        val text = SleepEngine.exportText(s, age3, plan, SleepEngine.DEFAULT_FEEDS)
        assertTrue(text.contains("📉 Режим регресса сна: все окна −10 мин"))
        assertTrue(text.contains("✱ Правки: Сон 1 −5 мин · Бодрствование 2 +5 мин"))
    }

    @Test
    fun `экспорт с кормлением до подъёма`() {
        val plan = age3.plans[0]
        val s = SleepEngine.build(age3, plan, 9 * 60 + 5)
        val text = SleepEngine.exportText(s, age3, plan, SleepEngine.DEFAULT_FEEDS)
        assertTrue(text.contains("🍼 До подъёма: 09:00"))
    }

    @Test
    fun `все времена кратны 5 минутам при любых правках`() {
        val plan = age3.plans[0]
        val adj = SleepEngine.Adjustments(
            napOffsets = listOf(5, -10, 0, 15),
            wwOffsets = listOf(5, -5, 0, 10, -5),
        )
        val s = SleepEngine.build(age3, plan, 9 * 60, adj)
        assertTrue(s.naps.all { it.start % 5 == 0 && it.end % 5 == 0 })
        assertEquals(0, s.bedtime % 5)
    }

    @Test
    fun `сдвиг пробуждения сдвигает день целиком`() {
        val plan = age3.plans[0]
        val a = SleepEngine.build(age3, plan, 9 * 60)
        val b = SleepEngine.build(age3, plan, 9 * 60 - 15)
        assertEquals(a.naps.map { it.start - 15 }, b.naps.map { it.start })
        assertEquals(a.bedtime - 15, b.bedtime)
        assertEquals(a.nightSleep, b.nightSleep)
    }

    @Test
    fun `переход через полночь`() {
        val plan = age3.plans[0]
        val s = SleepEngine.build(age3, plan, 12 * 60)
        assertEquals(1475, s.bedtime)
        assertEquals("00:35", SleepEngine.fmtClock(s.bedtime))
    }

    @Test
    fun `цепочка сходится для всех схем и крайних времён`() {
        for (age in SleepEngine.AGES) {
            for (plan in age.plans) {
                for (wake in listOf(5 * 60, 6 * 60, 9 * 60, 12 * 60)) {
                    val s = SleepEngine.build(age, plan, wake)
                    var t = wake
                    for ((i, nap) in s.naps.withIndex()) {
                        assertEquals(s.windows[i].effective, nap.wwBefore)
                        assertEquals(t + nap.wwBefore, nap.start)
                        t = nap.end
                    }
                    assertEquals(t + s.windows.last().effective, s.bedtime)
                    assertEquals(1440 - (s.bedtime - wake), s.nightSleep)
                }
            }
        }
    }

    @Test
    fun `сумма бодрствований + снов + ночи равна 24 часам`() {
        val plan = age3.plans[0]
        val s = SleepEngine.build(
            age3, plan, 9 * 60,
            SleepEngine.Adjustments(napOffsets = listOf(-20, 30, 0, -10)),
        )
        assertEquals(1440, s.wakeTotal + s.daySleep + s.nightSleep)
    }

    @Test
    fun `подсказка при слишком раннем отбое`() {
        val plan = age3.plans[0]
        assertNull(SleepEngine.build(age3, plan, 9 * 60).bedtimeHint)
        val s = SleepEngine.build(age3, plan, 9 * 60, SleepEngine.Adjustments(napOffsets = listOf(-40, -40, -40, -40)))
        assertNotNull(s.bedtimeHint)
    }

    @Test
    fun `форматирование`() {
        assertEquals("1:20", SleepEngine.fmtDur(80))
        assertEquals("09:05", SleepEngine.fmtClock(545))
        assertEquals("1 ч 20 мин", SleepEngine.fmtDurHuman(80))
        assertEquals("45 мин", SleepEngine.fmtDurHuman(45))
        assertEquals("1 ч", SleepEngine.fmtDurHuman(60))
        assertEquals("через 14 мин", SleepEngine.fmtIn(14))
        assertEquals("через 1 ч 20 мин", SleepEngine.fmtIn(80))
    }

    @Test
    fun `кормления между снами стоят в хронологии а не после следующего сна`() {
        val s = SleepEngine.build(age3, age3.plans[0], 9 * 60)
        val events = SleepEngine.timeline(s, listOf(18 * 60, 15 * 60, 21 * 60))
        assertEquals(events.map { it.time }.sorted(), events.map { it.time })
        val feed18 = events.indexOfFirst { it.kind == SleepEngine.EventKind.FEED && it.time == 1080 }
        val nap4 = events.indexOfFirst { it.kind == SleepEngine.EventKind.NAP_START && it.index == 4 }
        assertTrue(feed18 < nap4)
        assertEquals(3, events.count { it.kind == SleepEngine.EventKind.FEED })
    }

    @Test
    fun `кормление внутри сна находится между началом и пробуждением`() {
        val s = SleepEngine.build(age3, age3.plans[0], 540)
        val nap = s.naps[1]
        val events = SleepEngine.timeline(s, listOf(nap.start + 5))
        val start = events.indexOfFirst { it.kind == SleepEngine.EventKind.NAP_START && it.index == 2 }
        val feed = events.indexOfFirst { it.kind == SleepEngine.EventKind.FEED }
        val end = events.indexOfFirst { it.kind == SleepEngine.EventKind.NAP_END && it.index == 2 }
        assertTrue(start < feed && feed < end)
    }

    @Test
    fun `ночные и утренние кормления не теряются после полуночи`() {
        val s = SleepEngine.build(age3, age3.plans[0], 545)
        val events = SleepEngine.timeline(s, listOf(0, 270, 540, 545, 1320))
        assertEquals(listOf(545, 1320, 1440, 1710, 1980),
            events.filter { it.kind == SleepEngine.EventKind.FEED }.map { it.time })
        assertTrue(events.first().kind == SleepEngine.EventKind.WAKE)
    }

    @Test
    fun `на границе сна пробуждение показано перед кормлением для всех схем`() {
        for (age in SleepEngine.AGES) for (plan in age.plans) {
            val s = SleepEngine.build(age, plan, 540)
            val feeds = s.naps.map { it.end % 1440 }
            val events = SleepEngine.timeline(s, feeds)
            assertEquals(2 * plan.naps + feeds.size + 2, events.size)
            s.naps.forEach { nap ->
                val atEnd = events.filter { it.time == nap.end }
                assertEquals(SleepEngine.EventKind.NAP_END, atEnd.first().kind)
                assertEquals(SleepEngine.EventKind.FEED, atEnd.last().kind)
            }
        }
    }
}
