package com.family.sleepschedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Проверка правил консультанта и движка v1.5: схемы по числу снов сходятся
 * в коридорах, кормления ложатся между снами, факт сна авто-компенсирует
 * следующее бодрствование, «сейчас» определяется корректно, экспорт
 * собирается по формату с абзацами.
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
        assertEquals(listOf(80, 80, 70, 60), s.naps.map { it.duration })
        assertEquals(290, s.daySleep)           // 4:50
        assertEquals(1295, s.bedtime)           // 21:35
        assertEquals(685, s.nightSleep)         // 11:25
        assertEquals(975, s.totalSleep)         // 16:15
    }

    @Test
    fun `схема 5 снов — тот же отбой 21_35 и ночь 11_25`() {
        val plan = age3.plans[1]
        val s = SleepEngine.build(age3, plan, 9 * 60)

        assertEquals(5, s.naps.size)
        assertEquals(listOf(620, 770, 920, 1085, 1190), s.naps.map { it.start })
        assertEquals(listOf(695, 840, 980, 1115, 1220), s.naps.map { it.end })
        assertEquals(265, s.daySleep)
        assertEquals(1295, s.bedtime)           // 21:35
        assertEquals(685, s.nightSleep)         // 11:25
    }

    @Test
    fun `кормления ложатся в окна бодрствования в обеих схемах`() {
        for (plan in age3.plans) {
            val s = SleepEngine.build(age3, plan, 9 * 60)
            val p = SleepEngine.placeFeeds(s, SleepEngine.DEFAULT_FEEDS)

            assertEquals(7, p.size)
            assertEquals(SleepEngine.FeedKind.AT_WAKE, p[2].kind)   // 09:00
            assertEquals(SleepEngine.FeedKind.IN_WINDOW, p[3].kind) // 12:00
            assertEquals(2, p[3].slot)
            assertEquals(SleepEngine.FeedKind.IN_WINDOW, p[4].kind) // 15:00
            assertEquals(3, p[4].slot)
            assertEquals(SleepEngine.FeedKind.IN_WINDOW, p[5].kind) // 18:00
            assertEquals(4, p[5].slot)
            assertEquals(SleepEngine.FeedKind.IN_WINDOW, p[6].kind) // 21:00 — перед ночью
            assertEquals(plan.naps + 1, p[6].slot)
            assertEquals(SleepEngine.FeedKind.NIGHT, p[0].kind)     // 00:00
            assertEquals(SleepEngine.FeedKind.NIGHT, p[1].kind)     // 04:30
            assertTrue(
                "наездов кормлений на сны нет (${plan.name})",
                p.none { it.kind == SleepEngine.FeedKind.IN_NAP },
            )
        }
    }

    @Test
    fun `факт сна авто-компенсирует следующее бодрствование`() {
        val plan = age3.plans[0]
        val s = SleepEngine.build(age3, plan, 9 * 60, SleepEngine.Adjustments(napOffsets = listOf(-10)))
        assertEquals(695, s.naps[0].end)
        assertEquals(-10, s.windows[1].comp)
        assertEquals(85, s.windows[1].effective)
        assertEquals(780, s.naps[1].start)     // 13:00
    }

    @Test
    fun `компенсация ограничена 15 минутами`() {
        val plan = age3.plans[0]
        val s = SleepEngine.build(age3, plan, 9 * 60, SleepEngine.Adjustments(napOffsets = listOf(-45)))
        assertEquals(-15, s.windows[1].comp)
        assertEquals(80, s.windows[1].effective)
        assertEquals(35, s.naps[0].duration)
        assertNotNull(SleepEngine.napWarning(s.naps[0]))
    }

    @Test
    fun `режим регресса — все бодрствования минус 10, отбой раньше`() {
        val plan = age3.plans[0]
        val adj = SleepEngine.Adjustments(wwOffsets = List(5) { -SleepEngine.REGRESS_STEP })
        val s = SleepEngine.build(age3, plan, 9 * 60, adj)
        assertEquals(listOf(75, 85, 90, 95, 70), s.windows.map { it.effective })
        assertEquals(1245, s.bedtime)          // 20:45 — на 50 мин раньше
        assertTrue(s.hasFacts)
    }

    @Test
    fun `ручная правка бодрствования держит жёсткий коридор`() {
        val plan = age3.plans[0]
        val base = SleepEngine.build(age3, plan, 9 * 60)
        val s = SleepEngine.build(age3, plan, 9 * 60, SleepEngine.Adjustments(wwOffsets = listOf(5)))
        assertEquals(90, s.windows[0].effective)
        assertEquals(base.naps.map { it.start + 5 }, s.naps.map { it.start })
        assertEquals(base.bedtime + 5, s.bedtime)

        val clamped = SleepEngine.build(
            age3, plan, 9 * 60,
            SleepEngine.Adjustments(wwOffsets = listOf(0, 0, 0, 1000, 0)),
        )
        assertEquals(110, clamped.windows[3].effective)
    }

    @Test
    fun `сейчас — определения фаз`() {
        val plan = age3.plans[0]
        val s = SleepEngine.build(age3, plan, 9 * 60)

        // 10:00 — первое бодрствование, до укладывания 25 мин
        val w = SleepEngine.nowInfo(s, 10 * 60)
        assertEquals(SleepEngine.Phase.WINDOW, w.phase)
        assertEquals(0, w.windowIndex)
        assertEquals(25, w.minutesLeft)
        assertEquals("укладывание", w.nextLabel)

        // 11:00 — внутри Сна 1, до подъёма 45 мин
        val n = SleepEngine.nowInfo(s, 11 * 60)
        assertEquals(SleepEngine.Phase.NAP, n.phase)
        assertEquals(1, n.napIndex)
        assertEquals(45, n.minutesLeft)
        assertEquals("подъём", n.nextLabel)

        // 22:00 — ночь, до подъёма 11 ч
        val night = SleepEngine.nowInfo(s, 22 * 60)
        assertEquals(SleepEngine.Phase.NIGHT, night.phase)
        assertEquals(660, night.minutesLeft)

        // 05:00 — ночь (через полночь), до подъёма 4 ч
        val early = SleepEngine.nowInfo(s, 5 * 60)
        assertEquals(SleepEngine.Phase.NIGHT, early.phase)
        assertEquals(240, early.minutesLeft)
    }

    @Test
    fun `экспорт — формат с абзацами и кормлениями в строках бодрствования`() {
        val plan = age3.plans[0]
        val s = SleepEngine.build(age3, plan, 9 * 60)
        val text = SleepEngine.exportText(s, age3, plan, SleepEngine.DEFAULT_FEEDS)

        assertTrue(text.startsWith("🌙 Режим сна — 3 мес · 4 сна"))
        assertTrue(text.contains("☀️ Подъём 09:00"))
        assertTrue(text.contains("🍼 09:00 · ⏱ Бодрствование 1:25"))
        assertTrue(text.contains("😴 Сон 1 — 10:25–11:45 (1:20)"))
        assertTrue(text.contains("🍼 12:00 · ⏱ Бодрствование 1:35"))
        assertTrue(text.contains("🍼 15:00 · ⏱ Бодрствование 1:40"))
        assertTrue(text.contains("🍼 18:00 · ⏱ Бодрствование 1:45"))
        assertTrue(text.contains("🍼 21:00 · ⏱ Бодрствование 1:20"))
        assertTrue(text.contains("🌃 Ночь — 21:35–09:00 (11:25)"))
        assertTrue(text.contains("🍼 Ночью: 00:00 · 04:30"))
        assertTrue(text.contains("⏱ Бодрствование всего 7:45"))
        assertTrue(text.contains("📊 Сон днём 4:50 · ночью 11:25 · всего 16:15"))
        assertFalse("без фактов строка фактов не выводится", text.contains("Факты"))
        // секции разделены пустой строкой
        assertTrue(text.contains("\n\n🍼 12:00"))
    }

    @Test
    fun `экспорт со фактами содержит строку фактов`() {
        val plan = age3.plans[0]
        val s = SleepEngine.build(
            age3, plan, 9 * 60,
            SleepEngine.Adjustments(napOffsets = listOf(-5), wwOffsets = listOf(0, 5)),
        )
        val text = SleepEngine.exportText(s, age3, plan, SleepEngine.DEFAULT_FEEDS)
        assertTrue(text.contains("✱ Факты: Сон 1 −5 мин · Бодрствование 2 +5 мин"))
    }

    @Test
    fun `кормление может попасть в сон после правок — детектируется`() {
        val plan = age3.plans[0]
        val s = SleepEngine.build(age3, plan, 9 * 60, SleepEngine.Adjustments(napOffsets = listOf(-40)))
        val p = SleepEngine.placeFeeds(s, listOf(540, 630))
        assertEquals(SleepEngine.FeedKind.AT_WAKE, p[0].kind)
        assertEquals(SleepEngine.FeedKind.IN_NAP, p[1].kind)
        assertEquals(1, p[1].slot)
    }

    @Test
    fun `при регрессе кормление 21_00 уходит после отбоя, а не в ночь`() {
        val plan = age3.plans[0]
        val s = SleepEngine.build(
            age3, plan, 9 * 60,
            SleepEngine.Adjustments(wwOffsets = List(5) { -SleepEngine.REGRESS_STEP }),
        )
        assertEquals(1245, s.bedtime) // 20:45 — раньше кормления 21:00
        val p = SleepEngine.placeFeeds(s, SleepEngine.DEFAULT_FEEDS)
        assertEquals(SleepEngine.FeedKind.AFTER_BEDTIME, p[6].kind) // 21:00
        assertEquals(SleepEngine.FeedKind.NIGHT, p[0].kind)         // 00:00
        assertEquals(SleepEngine.FeedKind.NIGHT, p[1].kind)         // 04:30

        val text = SleepEngine.exportText(s, age3, plan, SleepEngine.DEFAULT_FEEDS)
        assertTrue(text.contains("🍼 После отбоя: 21:00 — покормить до укладывания"))
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
                        assertEquals(nap.start + nap.duration, nap.end)
                        t = nap.end
                    }
                    assertEquals(t + s.windows.last().effective, s.bedtime)
                    assertEquals(1440 - (s.bedtime - wake), s.nightSleep)
                }
            }
        }
    }

    @Test
    fun `сумма бодрствований + снов + ночи всегда равна 24 часам`() {
        val plan = age3.plans[0]
        val adj = SleepEngine.Adjustments(napOffsets = listOf(-20, 30, 0, -10))
        val s = SleepEngine.build(age3, plan, 9 * 60, adj)
        assertEquals(1440, s.wakeTotal + s.daySleep + s.nightSleep)
        assertEquals(110, s.windows[2].effective) // потолок 1:50
    }

    @Test
    fun `подсказка при слишком раннем отбое`() {
        val plan = age3.plans[0]
        assertNull(SleepEngine.build(age3, plan, 9 * 60).bedtimeHint)
        val s = SleepEngine.build(age3, plan, 9 * 60, SleepEngine.Adjustments(napOffsets = listOf(-40, -40, -40, -40)))
        assertNotNull(s.bedtimeHint)
    }

    @Test
    fun `поправки сна ограничены 20-240 минут`() {
        val plan = age3.plans[0]
        assertEquals(-60, SleepEngine.clampNap(plan, 0, -500))
        assertEquals(160, SleepEngine.clampNap(plan, 0, 500))
    }

    @Test
    fun `форматирование`() {
        assertEquals("1:20", SleepEngine.fmtDur(80))
        assertEquals("0:45", SleepEngine.fmtDur(45))
        assertEquals("09:05", SleepEngine.fmtClock(545))
        assertEquals("00:00", SleepEngine.fmtClock(1440))
    }

    // ── Аудит: крайние случаи ──────────────────────────────────────────────

    @Test
    fun `экстремальные правки не ломают ночь`() {
        val plan = age3.plans[0]
        // все сны и окна растянуты в максимум — отбой не должен уехать за сутки
        val s = SleepEngine.build(
            age3, plan, 5 * 60,
            SleepEngine.Adjustments(napOffsets = List(4) { 500 }, wwOffsets = List(5) { 500 }),
        )
        assertTrue("ночь не короче 5 ч: ${s.nightSleep}", s.nightSleep >= 300)
        assertTrue(s.naps.all { it.duration in 20..240 })
        assertEquals(0, s.bedtime % 5)
        assertEquals(s.daySleep + s.nightSleep, s.totalSleep)
    }

    @Test
    fun `границы фаз сейчас`() {
        val plan = age3.plans[0]
        val s = SleepEngine.build(age3, plan, 9 * 60)

        // ровно в конце Сна 1 (11:45) — уже бодрствование перед Сном 2
        val atNapEnd = SleepEngine.nowInfo(s, 705)
        assertEquals(SleepEngine.Phase.WINDOW, atNapEnd.phase)
        assertEquals(1, atNapEnd.windowIndex)
        assertEquals(95, atNapEnd.minutesLeft)

        // ровно в отбой (21:35) — ночь
        val atBedtime = SleepEngine.nowInfo(s, 1295)
        assertEquals(SleepEngine.Phase.NIGHT, atBedtime.phase)
        assertEquals(685, atBedtime.minutesLeft)

        // ровно в подъём (09:00) — первое бодрствование
        val atWake = SleepEngine.nowInfo(s, 540)
        assertEquals(SleepEngine.Phase.WINDOW, atWake.phase)
        assertEquals(0, atWake.windowIndex)
    }

    @Test
    fun `границы кормлений`() {
        val plan = age3.plans[0]
        val s = SleepEngine.build(age3, plan, 9 * 60)

        // кормление ровно в начало сна — наезд
        assertEquals(SleepEngine.FeedKind.IN_NAP, SleepEngine.placeFeeds(s, listOf(625))[0].kind)
        // ровно в отбой — «после отбоя»
        assertEquals(
            SleepEngine.FeedKind.AFTER_BEDTIME,
            SleepEngine.placeFeeds(s, listOf(1295))[0].kind,
        )
        // за минуту до отбоя — окно перед ночью
        assertEquals(
            SleepEngine.FeedKind.IN_WINDOW,
            SleepEngine.placeFeeds(s, listOf(1290))[0].kind,
        )
    }

    @Test
    fun `регресс плюс факты вместе не ломают коридоры`() {
        val plan = age3.plans[0]
        val s = SleepEngine.build(
            age3, plan, 9 * 60,
            SleepEngine.Adjustments(
                napOffsets = listOf(-30, 40, -20, 10),
                wwOffsets = listOf(-5, 10, -10, 15, -20).map { it - SleepEngine.REGRESS_STEP },
            ),
        )
        assertTrue(s.windows.all { it.effective in age3.hardMin..age3.hardMax })
        assertTrue(s.nightSleep >= 300)
        assertEquals(0, s.bedtime % 5)
    }
}
