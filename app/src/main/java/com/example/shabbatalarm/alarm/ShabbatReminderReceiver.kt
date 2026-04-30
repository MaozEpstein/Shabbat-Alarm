package com.example.shabbatalarm.alarm

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.shabbatalarm.MainActivity
import com.example.shabbatalarm.R
import com.example.shabbatalarm.ShabbatAlarmApp

/**
 * Posts the pre-Shabbat / pre-Yom Tov reminder notification and re-arms the
 * next reminder. Picks notification wording based on which kind of holy day
 * is imminent; user-customized text overrides all three defaults.
 */
class ShabbatReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Reminder fired at ${System.currentTimeMillis()}")
        postNotification(context)
        // Re-arm for the next holy day (Shabbat or Yom Tov, whichever is sooner).
        ShabbatReminderScheduler(context).scheduleNext()
    }

    private fun postNotification(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Log.d(TAG, "POST_NOTIFICATIONS not granted — skipping reminder notification")
                return
            }
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val repo = AlarmRepository(context)
        val offset = repo.getPreShabbatReminderOffsetMinutes()
        val customText = repo.getPreShabbatReminderText()

        // Recompute the upcoming kedusha kind at fire-time. This is more robust
        // than threading state through the PendingIntent — even if the device
        // rebooted or the clock shifted, we use the current best guess.
        val jerusalem = ShabbatTimesCalculator.CITIES.firstOrNull { it.nameEn == "Jerusalem" }
            ?: ShabbatTimesCalculator.CITIES[0]
        val kind = ShabbatTimesCalculator.computeNextKedushaTarget(jerusalem)?.kind
            ?: KedushaKind.SHABBAT

        val titleResId = when (kind) {
            KedushaKind.SHABBAT -> R.string.reminder_notification_title_shabbat
            KedushaKind.YOM_TOV -> R.string.reminder_notification_title_yomtov
            KedushaKind.SHABBAT_YOM_TOV_COMBINED -> R.string.reminder_notification_title_combined
        }
        val bodyResId = when (kind) {
            KedushaKind.SHABBAT -> R.string.reminder_notification_text
            KedushaKind.YOM_TOV -> R.string.reminder_notification_text_yomtov
            KedushaKind.SHABBAT_YOM_TOV_COMBINED -> R.string.reminder_notification_text_combined
        }

        val title = context.getString(titleResId)
        val body = customText ?: context.getString(bodyResId, offset)

        val notification = NotificationCompat.Builder(context, ShabbatAlarmApp.REMINDER_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(context)
            .notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "ShabbatReminderRx"
        private const val NOTIFICATION_ID = 2002
    }
}
