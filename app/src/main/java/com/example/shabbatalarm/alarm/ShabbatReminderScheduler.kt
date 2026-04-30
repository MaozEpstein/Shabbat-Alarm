package com.example.shabbatalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Schedules the next pre-Shabbat / pre-Yom Tov reminder. Fires N minutes before
 * Jerusalem candle lighting for whichever holy day comes first (Shabbat OR
 * Yom Tov). N is user-configurable via [AlarmRepository.getPreShabbatReminderOffsetMinutes].
 *
 * Anchoring to Jerusalem gives the rest of the country an extra ~22-minute buffer,
 * since most Israeli cities light candles ~22 minutes later than Jerusalem.
 *
 * When the reminder fires (see [ShabbatReminderReceiver]) it re-arms itself for
 * the next holy day — so a single PendingIntent covers an arbitrary mix of
 * Shabbatot and Yom Tov entries.
 */
class ShabbatReminderScheduler(private val context: Context) {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Arms the next reminder if the feature is enabled. Safe to call anytime —
     * becomes a no-op if the feature is disabled.
     */
    fun scheduleNext() {
        val repo = AlarmRepository(context)
        if (!repo.getPreShabbatReminderEnabled()) {
            cancel()
            return
        }

        // The reminder always anchors to Jerusalem candle lighting, regardless
        // of the user's preferred city for zmanim display.
        val city = ShabbatTimesCalculator.CITIES.firstOrNull { it.nameEn == "Jerusalem" }
            ?: ShabbatTimesCalculator.CITIES[0]

        val target = ShabbatTimesCalculator.computeNextKedushaTarget(city)
        if (target == null) {
            Log.e(TAG, "Could not compute next kedusha target for ${city.nameEn}")
            return
        }

        val offsetMs = repo.getPreShabbatReminderOffsetMinutes() * 60_000L
        val triggerAt = target.candleLighting.time - offsetMs
        if (triggerAt <= System.currentTimeMillis()) {
            // computeNextKedushaTarget already filters past entries, but
            // double-guard against the (offset > distance-to-entry) case.
            Log.d(TAG, "Reminder time $triggerAt is in the past; skipping")
            return
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            buildPendingIntent()
        )
        Log.d(TAG, "Reminder scheduled for $triggerAt (${target.kind} in ${city.nameHe})")
    }

    fun cancel() {
        alarmManager.cancel(buildPendingIntent())
    }

    private fun buildPendingIntent(): PendingIntent {
        val intent = Intent(context, ShabbatReminderReceiver::class.java).apply {
            action = ACTION_FIRE
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val TAG = "ShabbatReminder"
        private const val REQUEST_CODE = 2001
        const val ACTION_FIRE = "com.example.shabbatalarm.ACTION_REMINDER_FIRE"
    }
}
