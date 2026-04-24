package com.example.shabbatalarm.alarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * One scheduled alarm. The [id] is stable across a weekly repeat (so the same
 * PendingIntent slot is reused).
 */
data class ScheduledAlarm(
    val id: Int,
    val triggerMillis: Long,
    val repeatWeekly: Boolean
)

/** A user-added audio file used as an alarm tone. */
data class CustomTone(
    val uri: String,
    val title: String
)

/**
 * Persists:
 *  - a LIST of scheduled alarms
 *  - global playback settings (duration, vibration, tone, reminder-on, city-index)
 *  - a "default repeat" flag used only when creating NEW alarms from the UI
 *
 * Backed by SharedPreferences. Thread-safe (commits are handled by SP internally).
 */
class AlarmRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Alarms list ────────────────────────────────────────────────────────

    /** Returns all scheduled alarms (may be empty). Handles one-time migration
     *  from the old single-alarm key. */
    fun getAlarms(): List<ScheduledAlarm> {
        migrateLegacySingleAlarmIfNeeded()
        val raw = prefs.getString(KEY_ALARMS_JSON, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        ScheduledAlarm(
                            id = obj.getInt("id"),
                            triggerMillis = obj.getLong("triggerMillis"),
                            repeatWeekly = obj.getBoolean("repeatWeekly")
                        )
                    )
                }
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    /** Returns alarms whose trigger is still in the future. Purges past one-shots
     *  automatically. */
    fun getActiveAlarms(): List<ScheduledAlarm> {
        val now = System.currentTimeMillis()
        val all = getAlarms()
        val active = all.filter { it.repeatWeekly || it.triggerMillis > now }
        if (active.size != all.size) saveAlarms(active)
        return active
    }

    /** Adds a new alarm with an auto-generated id and returns it. */
    fun addAlarm(triggerMillis: Long, repeatWeekly: Boolean): ScheduledAlarm {
        val newAlarm = ScheduledAlarm(
            id = nextId(),
            triggerMillis = triggerMillis,
            repeatWeekly = repeatWeekly
        )
        val current = getAlarms().toMutableList()
        current.add(newAlarm)
        saveAlarms(current)
        return newAlarm
    }

    /** Upserts the alarm (by id). */
    fun upsertAlarm(alarm: ScheduledAlarm) {
        val current = getAlarms().toMutableList()
        val idx = current.indexOfFirst { it.id == alarm.id }
        if (idx >= 0) current[idx] = alarm else current.add(alarm)
        saveAlarms(current)
    }

    /** Removes the alarm with the given id (no-op if absent). */
    fun removeAlarm(id: Int) {
        val filtered = getAlarms().filterNot { it.id == id }
        saveAlarms(filtered)
    }

    private fun saveAlarms(alarms: List<ScheduledAlarm>) {
        val array = JSONArray()
        alarms.forEach { a ->
            array.put(
                JSONObject().apply {
                    put("id", a.id)
                    put("triggerMillis", a.triggerMillis)
                    put("repeatWeekly", a.repeatWeekly)
                }
            )
        }
        prefs.edit().putString(KEY_ALARMS_JSON, array.toString()).apply()
    }

    private fun nextId(): Int {
        val current = prefs.getInt(KEY_NEXT_ALARM_ID, 1)
        prefs.edit().putInt(KEY_NEXT_ALARM_ID, current + 1).apply()
        return current
    }

    /** If the old single-alarm key exists, convert it to a list entry and clear it. */
    private fun migrateLegacySingleAlarmIfNeeded() {
        val legacyTrigger = prefs.getLong(LEGACY_KEY_TRIGGER_AT, -1L)
        if (legacyTrigger <= 0L) return
        if (prefs.contains(KEY_ALARMS_JSON)) {
            // List is already populated — drop the legacy key to avoid double migration.
            prefs.edit().remove(LEGACY_KEY_TRIGGER_AT).apply()
            return
        }
        val legacyRepeat = prefs.getBoolean(KEY_REPEAT_WEEKLY, false)
        val migrated = ScheduledAlarm(
            id = nextId(),
            triggerMillis = legacyTrigger,
            repeatWeekly = legacyRepeat
        )
        saveAlarms(listOf(migrated))
        prefs.edit().remove(LEGACY_KEY_TRIGGER_AT).apply()
    }

    // ── Global playback settings ───────────────────────────────────────────

    fun getDurationSeconds(): Int =
        prefs.getInt(KEY_DURATION_SECONDS, DEFAULT_DURATION_SECONDS)

    fun setDurationSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_DURATION_SECONDS, seconds).apply()
    }

    /** Default repeat-weekly flag applied to NEW alarms created via the UI. */
    fun getRepeatWeekly(): Boolean = prefs.getBoolean(KEY_REPEAT_WEEKLY, false)

    fun setRepeatWeekly(repeat: Boolean) {
        prefs.edit().putBoolean(KEY_REPEAT_WEEKLY, repeat).apply()
    }

    fun getAlarmToneUri(): String? = prefs.getString(KEY_ALARM_TONE_URI, null)

    fun setAlarmToneUri(uri: String?) {
        prefs.edit().apply {
            if (uri == null) remove(KEY_ALARM_TONE_URI) else putString(KEY_ALARM_TONE_URI, uri)
        }.apply()
    }

    // ── Custom (user-added) tones ──────────────────────────────────────────

    fun getCustomTones(): List<CustomTone> {
        val raw = prefs.getString(KEY_CUSTOM_TONES_JSON, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        CustomTone(
                            uri = obj.getString("uri"),
                            title = obj.getString("title")
                        )
                    )
                }
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    /** Returns true on success, false if the max was reached. If the URI already
     *  exists in the list, returns true (no-op). */
    fun addCustomTone(uri: String, title: String): Boolean {
        val current = getCustomTones().toMutableList()
        if (current.any { it.uri == uri }) return true
        if (current.size >= MAX_CUSTOM_TONES) return false
        current.add(CustomTone(uri, title))
        saveCustomTones(current)
        return true
    }

    fun removeCustomTone(uri: String) {
        val filtered = getCustomTones().filterNot { it.uri == uri }
        saveCustomTones(filtered)
    }

    private fun saveCustomTones(list: List<CustomTone>) {
        val array = JSONArray()
        list.forEach { ct ->
            array.put(
                JSONObject().apply {
                    put("uri", ct.uri)
                    put("title", ct.title)
                }
            )
        }
        prefs.edit().putString(KEY_CUSTOM_TONES_JSON, array.toString()).apply()
    }

    fun getVibrationEnabled(): Boolean = prefs.getBoolean(KEY_VIBRATION, false)

    fun setVibrationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIBRATION, enabled).apply()
    }

    /** Alarm playback volume in [MIN_ALARM_VOLUME, 1.0]. Acts as the ceiling for
     *  the fade-in ramp inside AlarmService and the preview volume in the UI. */
    fun getAlarmVolume(): Float =
        prefs.getFloat(KEY_ALARM_VOLUME, DEFAULT_ALARM_VOLUME)
            .coerceIn(MIN_ALARM_VOLUME, 1f)

    fun setAlarmVolume(volume: Float) {
        prefs.edit()
            .putFloat(KEY_ALARM_VOLUME, volume.coerceIn(MIN_ALARM_VOLUME, 1f))
            .apply()
    }

    // ── Pre-Shabbat reminder settings ──────────────────────────────────────

    fun getPreShabbatReminderEnabled(): Boolean =
        prefs.getBoolean(KEY_REMINDER_ENABLED, false)

    fun setPreShabbatReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDER_ENABLED, enabled).apply()
    }

    fun getDefaultCityIndex(): Int = prefs.getInt(KEY_DEFAULT_CITY_INDEX, 0)

    fun setDefaultCityIndex(index: Int) {
        prefs.edit().putInt(KEY_DEFAULT_CITY_INDEX, index).apply()
    }

    companion object {
        private const val PREFS_NAME = "shabbat_alarm_prefs"

        // New multi-alarm storage
        private const val KEY_ALARMS_JSON = "alarms_json"
        private const val KEY_NEXT_ALARM_ID = "next_alarm_id"

        // Legacy single-alarm key (kept for migration only)
        private const val LEGACY_KEY_TRIGGER_AT = "trigger_at_millis"

        // Global settings
        private const val KEY_DURATION_SECONDS = "duration_seconds"
        private const val KEY_REPEAT_WEEKLY = "repeat_weekly"
        private const val KEY_ALARM_TONE_URI = "alarm_tone_uri"
        private const val KEY_VIBRATION = "vibration_enabled"
        private const val KEY_ALARM_VOLUME = "alarm_volume"
        private const val KEY_REMINDER_ENABLED = "pre_shabbat_reminder_enabled"
        private const val KEY_DEFAULT_CITY_INDEX = "default_city_index"

        const val DEFAULT_ALARM_VOLUME = 1.0f
        const val MIN_ALARM_VOLUME = 0.1f

        const val DEFAULT_DURATION_SECONDS = 15
        const val MIN_DURATION_SECONDS = 5
        const val MAX_DURATION_SECONDS = 60
        const val DURATION_STEP_SECONDS = 5

        /** Max number of concurrent alarms the UI allows the user to schedule. */
        const val MAX_ALARMS = 5

        /** Max number of user-added audio files kept in the tone picker. */
        const val MAX_CUSTOM_TONES = 10

        private const val KEY_CUSTOM_TONES_JSON = "custom_tones_json"
    }
}
