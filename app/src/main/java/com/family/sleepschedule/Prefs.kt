package com.family.sleepschedule

import android.content.SharedPreferences

/**
 * Чтение/применение сохранённого состояния — общее для приложения и виджета.
 * Хранится всё: возраст, схема, время пробуждения, кормления, факты снов,
 * ручные правки окон, режим регресса. Режим регресса хранится ОТДЕЛЬНО
 * от ручных правок и в wwOffsets не попадает.
 */
object Prefs {
    const val NAME = "sleep_schedule"

    fun loadFeeds(prefs: SharedPreferences): List<Int> =
        prefs.getString("feeds", null)
            ?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.takeIf { it.isNotEmpty() }
            ?: SleepEngine.DEFAULT_FEEDS

    fun loadAdj(prefs: SharedPreferences, plan: SleepEngine.NapPlan): SleepEngine.Adjustments {
        val naps = prefs.getString("offsets", null)
            ?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
        val wws = prefs.getString("ww", null)
            ?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
        return SleepEngine.Adjustments(
            napOffsets = List(plan.naps) { naps.getOrElse(it) { 0 } },
            wwOffsets = List(plan.naps + 1) { wws.getOrElse(it) { 0 } },
        )
    }

    fun loadRegress(prefs: SharedPreferences): Boolean = prefs.getBoolean("regress", false)

    /** Полный расчёт дня из сохранённого состояния — для виджета.
     *  Старые значения за границами коридора приводятся sanitizeAdj. */
    fun buildSchedule(prefs: SharedPreferences): SleepEngine.Schedule? {
        val ageIdx = prefs.getInt("age", 0)
        val planIdx = prefs.getInt("plan", 0)
        if (ageIdx !in SleepEngine.AGES.indices) return null
        val age = SleepEngine.AGES[ageIdx]
        if (planIdx !in age.plans.indices) return null
        val plan = age.plans[planIdx]
        val wake = prefs.getInt("wake", 9 * 60)
        val regress = loadRegress(prefs)
        val adj = SleepEngine.sanitizeAdj(age, plan, loadAdj(prefs, plan), regress)
        return SleepEngine.build(age, plan, wake, adj, regress)
    }
}
