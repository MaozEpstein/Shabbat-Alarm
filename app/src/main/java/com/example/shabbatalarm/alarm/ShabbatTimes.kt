package com.example.shabbatalarm.alarm

import android.util.Log
import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.util.GeoLocation
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * One of the major Israeli cities plus its standard candle-lighting offset (minutes before sunset).
 * Jerusalem uses 40 min, Haifa 30 min; most cities follow the standard 18 min.
 */
data class IsraeliCity(
    val nameHe: String,
    val nameEn: String,
    val latitude: Double,
    val longitude: Double,
    val elevation: Double,
    val candleLightingOffsetMinutes: Int
)

data class ShabbatTimes(
    val city: IsraeliCity,
    val candleLighting: String, // "HH:mm"
    val havdalah: String        // "HH:mm"
)

/**
 * Info about an upcoming Yom Tov (full holiday, not Chol Hamoed).
 * [combinedWithShabbat] is true when the holiday also falls on a Saturday.
 */
data class HolidayInfo(
    val hebrewName: String,
    val combinedWithShabbat: Boolean
)

/** What kind of holy day a given reminder is for. Drives notification wording. */
enum class KedushaKind {
    SHABBAT,
    YOM_TOV,
    SHABBAT_YOM_TOV_COMBINED
}

/** A future candle-lighting moment + the kind of holy day it ushers in. */
data class NextReminderTarget(
    val candleLighting: Date,
    val kind: KedushaKind
)

/** Advanced zmanim for Shabbat day (Saturday), all as "HH:mm" strings. */
data class AdvancedZmanim(
    val city: IsraeliCity,
    val saturdayDate: Date,
    val sofZmanShma: String,     // סוף זמן קריאת שמע (GRA)
    val sofZmanTfila: String,    // סוף זמן תפילה (GRA)
    val chatzos: String,         // חצות היום
    val minchaGedola: String,    // מנחה גדולה
    val minchaKetana: String,    // מנחה קטנה
    val plagHamincha: String,    // פלג המנחה
    val shekia: String           // שקיעה
)

object ShabbatTimesCalculator {

    private const val TAG = "ShabbatTimes"

    val CITIES: List<IsraeliCity> = listOf(
        IsraeliCity("ירושלים", "Jerusalem", 31.7683, 35.2137, 800.0, 40),
        IsraeliCity("תל אביב", "Tel Aviv", 32.0853, 34.7818, 34.0, 18),
        IsraeliCity("חיפה", "Haifa", 32.7940, 34.9896, 250.0, 30),
        IsraeliCity("באר שבע", "Beer Sheva", 31.2518, 34.7913, 280.0, 18),
        IsraeliCity("אילת", "Eilat", 29.5581, 34.9482, 12.0, 18),
        IsraeliCity("טבריה", "Tiberias", 32.7959, 35.5308, 40.0, 18),
        IsraeliCity("נתניה", "Netanya", 32.3328, 34.8599, 33.0, 18),
        IsraeliCity("אשדוד", "Ashdod", 31.8044, 34.6553, 50.0, 18),
    )

    private val TIMEZONE: TimeZone = TimeZone.getTimeZone("Asia/Jerusalem")

    /**
     * Returns candle-lighting and Havdalah times for the upcoming holy day —
     * either the next Shabbat, or a Yom Tov if one is sooner (e.g. Rosh Hashana
     * on Tuesday). Handles multi-day Yom Tov (e.g. 2 days of Rosh Hashana) by
     * extending the havdalah calculation to the last consecutive holy day.
     */
    fun calculateForUpcomingShabbat(): ShabbatResult {
        val kedusha = detectNextKedushaInfo()

        val times = CITIES.map { city ->
            calculateForCity(city, kedusha.entryErev, kedusha.exitDay)
        }

        val holidayInfo = kedusha.yomTovName?.let {
            HolidayInfo(it, kedusha.combinedWithShabbat)
        }

        return ShabbatResult(
            fridayDate = kedusha.entryErev,
            times = times,
            holidayInfo = holidayInfo
        )
    }

    /**
     * Scans up to 14 days from today to find the first "kedusha day" — a day
     * on which melacha is forbidden (Shabbat or full Yom Tov, excluding Chol Hamoed).
     * If multiple consecutive kedusha days follow (Rosh Hashana's 2 days, or a
     * Yom Tov that abuts Shabbat), the exitDay is the last of them.
     */
    private fun detectNextKedushaInfo(): KedushaInfo {
        val today = Calendar.getInstance(TIMEZONE).apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        for (offset in 0..14) {
            val check = Calendar.getInstance(TIMEZONE).apply {
                time = today.time
                add(Calendar.DAY_OF_MONTH, offset)
            }
            val (isShabbat, isYomTov) = classifyKedusha(check)
            if (!isShabbat && !isYomTov) continue

            // Walk forward to find the last consecutive kedusha day.
            var lastDay = check
            while (true) {
                val next = Calendar.getInstance(TIMEZONE).apply {
                    time = lastDay.time
                    add(Calendar.DAY_OF_MONTH, 1)
                }
                val (nextSh, nextYt) = classifyKedusha(next)
                if (nextSh || nextYt) lastDay = next else break
            }

            val entryErev = Calendar.getInstance(TIMEZONE).apply {
                time = check.time
                add(Calendar.DAY_OF_MONTH, -1)
            }.time

            val yomTovName = if (isYomTov) {
                try {
                    HebrewDateFormatter()
                        .apply { isHebrewFormat = true }
                        .formatYomTov(JewishCalendar(check.time))
                        .ifBlank { null }
                } catch (t: Throwable) {
                    Log.e(TAG, "formatYomTov failed", t)
                    null
                }
            } else null

            return KedushaInfo(
                entryErev = entryErev,
                exitDay = lastDay.time,
                isYomTov = isYomTov,
                combinedWithShabbat = isShabbat && isYomTov,
                yomTovName = yomTovName
            )
        }

        // Fallback (should never happen — Shabbat comes at most every 7 days).
        val friday = nextFriday()
        val saturday = Calendar.getInstance(TIMEZONE).apply {
            time = friday
            add(Calendar.DAY_OF_MONTH, 1)
        }.time
        return KedushaInfo(friday, saturday, isYomTov = false, combinedWithShabbat = false, yomTovName = null)
    }

    private fun classifyKedusha(cal: Calendar): Pair<Boolean, Boolean> {
        val isShabbat = cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY
        val isYomTov = try {
            val jc = JewishCalendar(cal.time)
            jc.isYomTov && jc.isAssurBemelacha
        } catch (t: Throwable) {
            false
        }
        return isShabbat to isYomTov
    }

    private data class KedushaInfo(
        val entryErev: Date,
        val exitDay: Date,
        val isYomTov: Boolean,
        val combinedWithShabbat: Boolean,
        val yomTovName: String?
    )

    private fun calculateForCity(
        city: IsraeliCity,
        friday: Date,
        saturday: Date
    ): ShabbatTimes {
        return try {
            // KosherJava rejects negative elevations; clamp defensively.
            val safeElevation = city.elevation.coerceAtLeast(0.0)
            val location = GeoLocation(
                city.nameEn, city.latitude, city.longitude, safeElevation, TIMEZONE
            )

            val fridayCalc = ComplexZmanimCalendar(location).apply {
                calendar.time = friday
            }
            val saturdayCalc = ComplexZmanimCalendar(location).apply {
                calendar.time = saturday
            }

            val sunsetFriday = fridayCalc.sunset
            val candleLighting = sunsetFriday?.let {
                Date(it.time - city.candleLightingOffsetMinutes * 60_000L)
            }

            // Tzais (stars emerge) — 8.5° below horizon, standard for Havdalah in Israel
            val havdalah = saturdayCalc.tzais

            ShabbatTimes(
                city = city,
                candleLighting = formatTime(candleLighting),
                havdalah = formatTime(havdalah)
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to compute Shabbat times for ${city.nameEn}", t)
            ShabbatTimes(city = city, candleLighting = "—", havdalah = "—")
        }
    }

    /**
     * Returns advanced zmanim for Shabbat day (Saturday) in the given city.
     * Saturday is derived from the same `nextFriday()` logic + 1 day, so the
     * result follows the same semantics:
     *   - Sun–Thu: next Shabbat
     *   - Fri: tomorrow (the upcoming Shabbat)
     *   - Sat: today (current Shabbat)
     */
    fun computeAdvancedZmanim(city: IsraeliCity): AdvancedZmanim {
        val saturday = Calendar.getInstance(TIMEZONE).apply {
            time = nextFriday()
            add(Calendar.DAY_OF_MONTH, 1)
        }.time

        val czc = try {
            val safeElevation = city.elevation.coerceAtLeast(0.0)
            val location = GeoLocation(
                city.nameEn, city.latitude, city.longitude, safeElevation, TIMEZONE
            )
            ComplexZmanimCalendar(location).apply { calendar.time = saturday }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to build zmanim calendar for ${city.nameEn}", t)
            return AdvancedZmanim(city, saturday, "—", "—", "—", "—", "—", "—", "—")
        }

        return AdvancedZmanim(
            city = city,
            saturdayDate = saturday,
            sofZmanShma = formatTime(czc.sofZmanShmaGRA),
            sofZmanTfila = formatTime(czc.sofZmanTfilaGRA),
            chatzos = formatTime(czc.chatzos),
            minchaGedola = formatTime(czc.minchaGedola),
            minchaKetana = formatTime(czc.minchaKetana),
            plagHamincha = formatTime(czc.plagHamincha),
            shekia = formatTime(czc.sunset)
        )
    }

    /**
     * Returns the candle-lighting moment for the given city and Friday, or null
     * if sunset cannot be computed.
     */
    fun computeCandleLighting(city: IsraeliCity, fridayDate: Date): Date? {
        return try {
            val safeElevation = city.elevation.coerceAtLeast(0.0)
            val location = GeoLocation(
                city.nameEn, city.latitude, city.longitude, safeElevation, TIMEZONE
            )
            val zmanim = ComplexZmanimCalendar(location).apply {
                calendar.time = fridayDate
            }
            val sunset = zmanim.sunset ?: return null
            Date(sunset.time - city.candleLightingOffsetMinutes * 60_000L)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to compute candle lighting for ${city.nameEn}", t)
            null
        }
    }

    /**
     * Returns the next future candle-lighting for whichever holy day comes
     * first — Shabbat or Yom Tov — along with the kind of day it is. This is
     * the entry point used by the pre-Shabbat/Yom Tov reminder.
     *
     * Skips kedusha entries whose candle-lighting has already passed (so when
     * called on Shabbat afternoon it returns NEXT week's Shabbat / next Yom Tov,
     * not today's, whose entry was yesterday).
     */
    fun computeNextKedushaTarget(city: IsraeliCity): NextReminderTarget? {
        val today = Calendar.getInstance(TIMEZONE).apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = System.currentTimeMillis()

        // Scan up to 60 days for the next future kedusha entry. 60 covers the
        // longest realistic gap between consecutive holy days and a defensive
        // buffer (e.g. a Yom Tov immediately after a Shabbat that's already passed).
        for (offset in 0..60) {
            val check = Calendar.getInstance(TIMEZONE).apply {
                time = today.time
                add(Calendar.DAY_OF_MONTH, offset)
            }
            val (isShabbat, isYomTov) = classifyKedusha(check)
            if (!isShabbat && !isYomTov) continue

            val entryErev = Calendar.getInstance(TIMEZONE).apply {
                time = check.time
                add(Calendar.DAY_OF_MONTH, -1)
            }.time
            val candle = computeCandleLighting(city, entryErev) ?: continue
            if (candle.time <= now) continue

            val kind = when {
                isShabbat && isYomTov -> KedushaKind.SHABBAT_YOM_TOV_COMBINED
                isYomTov -> KedushaKind.YOM_TOV
                else -> KedushaKind.SHABBAT
            }
            return NextReminderTarget(candle, kind)
        }
        return null
    }

    /**
     * Stable string key (yyyy-MM-dd in Asia/Jerusalem) for the upcoming Shabbat's
     * Friday. Used to anchor weekly-rotating user content (Dvar Torah, etc.) so
     * the app can tell whether a saved entry still matches the current week.
     */
    fun upcomingFridayKey(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TIMEZONE
        }
        return formatter.format(nextFriday())
    }

    private fun nextFriday(): Date {
        val cal = Calendar.getInstance(TIMEZONE)
        val today = cal.get(Calendar.DAY_OF_WEEK)
        val daysOffset = if (today == Calendar.SATURDAY) {
            // We're currently in Shabbat — use yesterday's Friday so Havdalah shows today.
            -1
        } else {
            // Sunday–Thursday: days until next Friday. Friday: 0 (today).
            (Calendar.FRIDAY - today + 7) % 7
        }
        cal.add(Calendar.DAY_OF_MONTH, daysOffset)
        cal.set(Calendar.HOUR_OF_DAY, 12)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    private fun formatTime(date: Date?): String {
        if (date == null) return "—"
        val formatter = SimpleDateFormat("HH:mm", Locale.US)
        formatter.timeZone = TIMEZONE
        return formatter.format(date)
    }

    data class ShabbatResult(
        val fridayDate: Date,
        val times: List<ShabbatTimes>,
        val holidayInfo: HolidayInfo? = null
    ) {
        /**
         * Returns a combined Gregorian + Hebrew date string, e.g.
         * "יום שישי, 25 באפריל · י״א באייר התשפ״ו".
         */
        fun formatFridayDate(): String {
            val gregorian = formatGregorian()
            val hebrew = formatHebrew()
            return if (hebrew.isNotBlank()) "$gregorian · $hebrew" else gregorian
        }

        private fun formatGregorian(): String {
            val formatter = SimpleDateFormat("EEEE, d בMMMM", Locale("iw", "IL"))
            formatter.timeZone = TIMEZONE
            return formatter.format(fridayDate)
        }

        private fun formatHebrew(): String = try {
            val jewishCal = JewishCalendar(fridayDate)
            HebrewDateFormatter().apply { isHebrewFormat = true }.format(jewishCal)
        } catch (t: Throwable) {
            Log.e("ShabbatTimes", "Failed to format Hebrew date", t)
            ""
        }
    }
}
