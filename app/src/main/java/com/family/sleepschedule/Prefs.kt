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

    data class Session(val state: LiveTracker.State, val undo: LiveTracker.State? = null)
    private fun encode(s: LiveTracker.State) = org.json.JSONObject().apply {
        put("phase", s.phase.name); put("since", s.since); put("previousAt", s.previousAt)
        put("dayStart", s.dayStart); put("completedNaps", s.completedNaps)
        put("lastNapMinutes", s.lastNapMinutes ?: org.json.JSONObject.NULL); put("nightStart", s.nightStart)
    }
    private fun decode(j: org.json.JSONObject): LiveTracker.State {
        val s = LiveTracker.State(LiveTracker.Phase.valueOf(j.getString("phase")), j.getLong("since"),
            j.getLong("previousAt"), j.getLong("dayStart"), j.getInt("completedNaps"),
            if (j.isNull("lastNapMinutes")) null else j.getInt("lastNapMinutes"), j.getLong("nightStart"))
        require(s.completedNaps >= 0 && s.since >= 0 && s.previousAt >= 0)
        require(s.previousAt <= s.since)
        return s
    }
    fun loadLive(prefs: SharedPreferences): Session = try {
        val j = org.json.JSONObject(prefs.getString("live_session", null) ?: "{}")
        Session(decode(j.getJSONObject("state")), if (j.isNull("undo")) null else decode(j.getJSONObject("undo")))
    } catch (_: Exception) { Session(LiveTracker.State()) }

    /** Одна атомарная запись: состояние и однократная отмена, без истории. */
    fun saveLive(prefs: SharedPreferences, session: Session): Boolean = prefs.edit().putString("live_session",
        org.json.JSONObject().put("state", encode(session.state))
            .put("undo", session.undo?.let { encode(it) } ?: org.json.JSONObject.NULL).toString()).commit()
}
