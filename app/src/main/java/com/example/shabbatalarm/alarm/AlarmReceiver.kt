package com.example.shabbatalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.shabbatalarm.widget.ShabbatAlarmWidget
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1)
        Log.d(TAG, "Alarm fired (id=$alarmId) at ${System.currentTimeMillis()}")

        // Wake the CPU through the hand-off to the service and playback window.
        AlarmWakeLock.acquire(context)

        val repo = AlarmRepository(context)
        val alarm = repo.getAlarms().firstOrNull { it.id == alarmId }

        if (alarm != null && alarm.repeatWeekly) {
            // Re-arm for the same time next week before starting the service so
            // the next occurrence is scheduled even if the service fails to start.
            val nextTrigger = advanceByOneWeek(alarm.triggerMillis)
            val rescheduled = alarm.copy(triggerMillis = nextTrigger)
            repo.upsertAlarm(rescheduled)
            AlarmScheduler(context).schedule(rescheduled)
            Log.d(TAG, "Weekly reschedule (id=$alarmId) for $nextTrigger")
        } else if (alarm != null) {
            // One-shot: remove from the persisted list; the service just plays.
            repo.removeAlarm(alarm.id)
        }

        val serviceIntent = Intent(context, AlarmService::class.java)
        ContextCompat.startForegroundService(context, serviceIntent)

        // Refresh the home-screen widget (next alarm may have changed).
        ShabbatAlarmWidget.updateAll(context)
    }

    /** Adds exactly 7 calendar days, preserving wall-clock time across DST transitions. */
    private fun advanceByOneWeek(triggerMillis: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = triggerMillis }
        cal.add(Calendar.DAY_OF_YEAR, 7)
        return cal.timeInMillis
    }

    companion object {
        private const val TAG = "AlarmReceiver"
    }
}
