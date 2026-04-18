package com.example.shabbatalarm.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.shabbatalarm.MainActivity
import com.example.shabbatalarm.R
import com.example.shabbatalarm.alarm.AlarmRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Home screen widget showing the next scheduled alarm. Updates:
 *  - Automatically every 30 minutes (minimum update period Android allows).
 *  - On demand via [updateAll] (called from AlarmScreen on add/cancel and from
 *    AlarmReceiver when an alarm fires).
 *  - On device boot (via the platform's onUpdate lifecycle).
 */
class ShabbatAlarmWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id -> renderWidget(context, appWidgetManager, id) }
    }

    companion object {

        /** Refresh all widget instances on the home screen. Safe to call anytime. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, ShabbatAlarmWidget::class.java)
            )
            ids.forEach { id -> renderWidget(context, manager, id) }
        }

        private fun renderWidget(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int
        ) {
            val repo = AlarmRepository(context)
            val nextAlarm = repo.getActiveAlarms().minByOrNull { it.triggerMillis }

            val views = RemoteViews(context.packageName, R.layout.widget_shabbat_alarm)

            if (nextAlarm == null) {
                views.setTextViewText(
                    R.id.widget_time,
                    context.getString(R.string.widget_no_alarm)
                )
                views.setTextViewText(R.id.widget_subtitle, "")
                views.setViewVisibility(R.id.widget_subtitle, View.GONE)
            } else {
                views.setTextViewText(R.id.widget_time, formatTime(nextAlarm.triggerMillis))
                views.setTextViewText(
                    R.id.widget_subtitle,
                    formatSubtitle(context, nextAlarm.triggerMillis)
                )
                views.setViewVisibility(R.id.widget_subtitle, View.VISIBLE)
            }

            // Tap anywhere on the widget opens the app.
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)

            manager.updateAppWidget(widgetId, views)
        }

        private fun formatTime(triggerMillis: Long): String =
            SimpleDateFormat("HH:mm", Locale.US).format(Date(triggerMillis))

        private fun formatSubtitle(context: Context, triggerMillis: Long): String {
            val target = Calendar.getInstance().apply { timeInMillis = triggerMillis }
            val today = Calendar.getInstance()
            val dayDiff =
                target.get(Calendar.DAY_OF_YEAR) - today.get(Calendar.DAY_OF_YEAR) +
                        (target.get(Calendar.YEAR) - today.get(Calendar.YEAR)) * 365

            return when {
                dayDiff == 0 -> context.getString(R.string.widget_today)
                dayDiff == 1 -> context.getString(R.string.widget_tomorrow)
                dayDiff in 2..6 ->
                    SimpleDateFormat("EEEE", Locale("iw", "IL")).format(Date(triggerMillis))
                else -> SimpleDateFormat("d/M", Locale("iw", "IL")).format(Date(triggerMillis))
            }
        }
    }
}
