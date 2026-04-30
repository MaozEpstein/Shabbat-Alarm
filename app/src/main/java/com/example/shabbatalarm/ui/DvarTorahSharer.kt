package com.example.shabbatalarm.ui

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.example.shabbatalarm.R
import com.example.shabbatalarm.alarm.DvarTorahEntry

/**
 * Shares the currently displayed dvar torah via the system share sheet —
 * which surfaces WhatsApp prominently when installed, plus SMS, email, and
 * any other text-receiving app the user has.
 *
 * The shared message is plain text with a header naming the parsha (or the
 * Friday date for user-written entries), followed by the body, followed by
 * the AI-attribution footer when sharing catalog content.
 */
object DvarTorahSharer {

    private const val TAG = "DvarTorahSharer"

    fun share(
        context: Context,
        fridayLabel: String,
        userText: String?,
        repoEntry: DvarTorahEntry?
    ) {
        val message = buildMessage(context, fridayLabel, userText, repoEntry)
        if (message.isBlank()) return

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_SUBJECT,
                context.getString(R.string.dvar_torah_share_subject)
            )
            putExtra(Intent.EXTRA_TEXT, message)
        }
        val chooser = Intent.createChooser(
            sendIntent,
            context.getString(R.string.dvar_torah_share_chooser)
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

    private fun buildMessage(
        context: Context,
        fridayLabel: String,
        userText: String?,
        repoEntry: DvarTorahEntry?
    ): String {
        val sb = StringBuilder()
        when {
            userText != null -> {
                sb.append(context.getString(R.string.dvar_torah_share_header_user, fridayLabel))
                sb.append("\n\n")
                sb.append(userText.trim())
            }
            repoEntry != null -> {
                sb.append(context.getString(R.string.dvar_torah_share_header_parsha, repoEntry.nameHe))
                sb.append("\n\n")
                if (repoEntry.title.isNotBlank()) {
                    sb.append(repoEntry.title)
                    sb.append("\n\n")
                }
                if (repoEntry.pasukRef.isNotBlank()) {
                    sb.append(repoEntry.pasukRef)
                    sb.append("\n\n")
                }
                sb.append(repoEntry.body.trim())
                sb.append("\n\n—\n")
                sb.append(context.getString(R.string.dvar_torah_ai_attribution))
            }
            else -> return ""
        }
        return sb.toString()
    }
}
