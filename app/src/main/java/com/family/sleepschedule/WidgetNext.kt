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
        val s = Prefs.buildSchedule(prefs) ?: return
        val cal = Calendar.getInstance()
        val nowTod = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val info = SleepEngine.nowInfo(s, nowTod)

        val main = when (info.phase) {
            SleepEngine.Phase.NAP -> "😴 Сон ${info.napIndex}"
            SleepEngine.Phase.WINDOW -> "⏱ Бодрствование"
            SleepEngine.Phase.NIGHT -> "🌃 Ночь"
        }
        val sub = "${info.nextLabel} в ${SleepEngine.fmtClock(info.nextAbs)} " +
            "(через ${if (info.minutesLeft < 60) "${info.minutesLeft} мин" else SleepEngine.fmtDur(info.minutesLeft)})"

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
