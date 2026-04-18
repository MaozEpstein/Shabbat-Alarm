package com.example.shabbatalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.shabbatalarm.widget.ShabbatAlarmWidget
import java.util.Calendar

/**
 * Re-arms all scheduled alarms after a reboot. AlarmManager does not survive
 * reboots, so we persist each alarm and restore them here. Also re-arms the
 * weekly Shabbat reminder (self-contained).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // Re-arm the Shabbat reminder (handles its own enabled check).
        ShabbatReminderScheduler(context).scheduleNext()

        val repo = AlarmRepository(context)
        val scheduler = AlarmScheduler(context)
        val now = System.currentTimeMillis()

        repo.getAlarms().forEach { alarm ->
            val arm = when {
                alarm.triggerMillis > now -> alarm

                alarm.repeatWeekly -> {
                    // Advance by weeks until the trigger is in the future.
                    val cal = Calendar.getInstance().apply { timeInMillis = alarm.triggerMillis }
                    while (cal.timeInMillis <= now) {
                        cal.add(Calendar.DAY_OF_YEAR, 7)
                    }
                    val advanced = alarm.copy(triggerMillis = cal.timeInMillis)
                    repo.upsertAlarm(advanced)
                    advanced
                }

                else -> {
                    // One-shot whose time passed while the device was off — drop it.
                    repo.removeAlarm(alarm.id)
                    null
                }
            }

            if (arm != null) {
                Log.d(TAG, "Re-arming alarm (id=${arm.id}) for ${arm.triggerMillis}")
                scheduler.schedule(arm)
            }
        }

        // Make sure the widget reflects the re-armed state after reboot.
        ShabbatAlarmWidget.updateAll(context)
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
