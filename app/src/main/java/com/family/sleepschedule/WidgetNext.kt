package com.family.sleepschedule

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.util.Calendar

/**
 * Виджет «следующее событие»: показывает текущий этап и время до
 * следующего укладывания/подъёма. Обновляется сам раз в 30 минут,
 * при каждом изменении в приложении и по тапу (тап открывает приложение).
 */
class WidgetNext : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val state = Prefs.loadLive(prefs).state
        val age = SleepEngine.AGES[prefs.getInt("age", 0).coerceIn(SleepEngine.AGES.indices)]
        val plan = age.plans[prefs.getInt("plan", 0).coerceIn(age.plans.indices)]
        val next = LiveTracker.forecasts(state, age, plan, Prefs.loadRegress(prefs)).firstOrNull()
        val main = when (state.phase) {
            LiveTracker.Phase.READY -> "Нажми «Проснулся» в приложении"
            LiveTracker.Phase.AWAKE -> "Бодрствует"
            LiveTracker.Phase.NAP -> "Спит"
            LiveTracker.Phase.NIGHT -> "Ночной сон"
            LiveTracker.Phase.NIGHT_AWAKE -> "Проснулся ночью"
        }
        val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        fun clock(at: Long) = java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault()).format(formatter)
        val sub = if (next != null) {
            val left = ((next.at - System.currentTimeMillis()) / LiveTracker.MINUTE).toInt()
            "${next.title} · ${clock(next.at)} · " +
                if (left >= 0) "осталось ~${SleepEngine.fmtDurHuman(left)}" else "ориентир прошёл"
        } else if (state.phase != LiveTracker.Phase.READY) "С ${clock(state.since)}" else "Время запишется с телефона"

        val views = RemoteViews(context.packageName, R.layout.widget_next).apply {
            setTextViewText(R.id.widget_title, "Режим сна")
            setTextViewText(R.id.widget_main, main)
            setTextViewText(R.id.widget_sub, sub)
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widget_root, open)

        appWidgetManager.updateAppWidget(appWidgetIds, views)
    }
}
