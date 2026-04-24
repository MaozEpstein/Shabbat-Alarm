package com.example.shabbatalarm.ui

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.example.shabbatalarm.R
import kotlinx.coroutines.CoroutineScope

/**
 * Shares a link to the latest APK on GitHub Releases via the system share sheet.
 * The URL uses /releases/latest/download/<filename> so it always points to the
 * newest release without needing an app update.
 */
object ApkSharer {

    private const val TAG = "ApkSharer"
    const val DOWNLOAD_URL =
        "https://github.com/MaozEpsein/Shabbat-Alarm/releases/latest/download/ShabbatAlarm.apk"

    @Suppress("UNUSED_PARAMETER")
    fun share(context: Context, scope: CoroutineScope) {
        val message = context.getString(R.string.share_app_message, DOWNLOAD_URL)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.app_name))
            putExtra(Intent.EXTRA_TEXT, message)
        }
        val chooser = Intent.createChooser(
            sendIntent,
            context.getString(R.string.share_chooser_title)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(chooser)
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to start share chooser", t)
            Toast.makeText(
                context,
                context.getString(R.string.share_app_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
