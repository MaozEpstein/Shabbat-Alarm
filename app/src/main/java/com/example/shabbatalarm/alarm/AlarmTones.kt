package com.example.shabbatalarm.alarm

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log

data class AlarmTone(
    val title: String,
    val uri: Uri,
    /** True for user-added files (removable), false for system ringtones. */
    val isCustom: Boolean = false
)

object AlarmTones {

    private const val TAG = "AlarmTones"

    /**
     * Lists:
     *   1. User-added custom tones at the top (removable).
     *   2. System alarm ringtones after, with the current default placed first.
     */
    fun loadAvailable(context: Context): List<AlarmTone> {
        val custom = AlarmRepository(context).getCustomTones().map { ct ->
            AlarmTone(title = ct.title, uri = Uri.parse(ct.uri), isCustom = true)
        }
        val system = loadSystemTones(context)
        return custom + system
    }

    private fun loadSystemTones(context: Context): List<AlarmTone> {
        val tones = mutableListOf<AlarmTone>()
        val defaultUri: Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        try {
            val manager = RingtoneManager(context)
            manager.setType(RingtoneManager.TYPE_ALARM)
            val cursor = manager.cursor
            while (cursor.moveToNext()) {
                val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                val uri = manager.getRingtoneUri(cursor.position)
                if (!title.isNullOrBlank() && uri != null) {
                    tones.add(AlarmTone(title = title, uri = uri))
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to enumerate alarm ringtones", t)
        }

        // Pull the system default to the top of the system section.
        if (defaultUri != null) {
            val defaultIndex = tones.indexOfFirst { it.uri == defaultUri }
            if (defaultIndex > 0) {
                val defaultTone = tones.removeAt(defaultIndex)
                tones.add(0, defaultTone)
            } else if (defaultIndex == -1) {
                tones.add(0, AlarmTone(title = "System default", uri = defaultUri))
            }
        }

        return tones
    }
}
