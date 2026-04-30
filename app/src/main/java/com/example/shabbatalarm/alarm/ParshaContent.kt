package com.example.shabbatalarm.alarm

import android.content.Context
import android.util.Log
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone

/**
 * One pre-written dvar torah, loaded from assets/parshiyot.json. Includes both
 * the full body and the metadata (title, opening pasuk reference) so the UI
 * can render the entry consistently across parshiyot.
 */
data class DvarTorahEntry(
    val id: String,
    val nameHe: String,
    val title: String,
    val pasukRef: String,
    val body: String,
    val isHoliday: Boolean
)

/**
 * Loads the bundled dvar torah catalog from assets and resolves the entry
 * appropriate for the upcoming Shabbat. The catalog is parsed once and cached.
 *
 * Resolution order:
 *  1. KosherJava's [JewishCalendar.getUpcomingParshah] — handles the regular
 *     54-parsha cycle plus the 7 doubled-up combinations.
 *  2. If no parsha is read this Shabbat (e.g., Yom Tov on Shabbat or Chol
 *     Hamoed Shabbat), match the upcoming kedusha day to a holiday entry.
 */
object ParshaRepository {

    private const val TAG = "ParshaRepository"
    private const val ASSET_FILE = "parshiyot.json"
    private val TIMEZONE: TimeZone = TimeZone.getTimeZone("Asia/Jerusalem")

    @Volatile private var cached: List<DvarTorahEntry>? = null

    /** Loads and caches all entries. Returns empty list on parse failure. */
    fun loadAll(context: Context): List<DvarTorahEntry> {
        cached?.let { return it }
        return try {
            val text = context.applicationContext.assets.open(ASSET_FILE)
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val obj = JSONObject(text)
            val arr = obj.getJSONArray("parshiyot")
            val list = buildList {
                for (i in 0 until arr.length()) {
                    val p = arr.getJSONObject(i)
                    add(
                        DvarTorahEntry(
                            id = p.getString("id"),
                            nameHe = p.getString("name_he"),
                            title = p.optString("title", ""),
                            pasukRef = p.optString("pasukRef", ""),
                            body = p.getString("body"),
                            isHoliday = p.optBoolean("isHoliday", false)
                        )
                    )
                }
            }
            cached = list
            list
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load parshiyot.json", t)
            emptyList()
        }
    }

    /**
     * Returns the dvar torah entry the user should see for the upcoming
     * Shabbat. Null is returned only in the rare case where neither a parsha
     * nor a recognized holiday match — the caller should show an empty state.
     */
    fun findForUpcomingShabbat(context: Context): DvarTorahEntry? {
        val all = loadAll(context)
        if (all.isEmpty()) return null

        val saturday = nextSaturday()
        val jc = JewishCalendar(saturday).apply { inIsrael = true }

        val parsha = jc.upcomingParshah ?: JewishCalendar.Parsha.NONE
        if (parsha != JewishCalendar.Parsha.NONE) {
            val targetId = mapParshaToId(parsha)
            if (targetId != null) {
                val entry = all.firstOrNull { it.id == targetId }
                if (entry != null) return entry
            }
        }

        // No parsha read (Yom Tov on Shabbat / Chol Hamoed Shabbat etc.) —
        // try to surface a holiday entry instead.
        return findHolidayEntry(jc, all)
    }

    /** ISO-style key for the Friday paired with the upcoming Saturday. Used by
     *  the UI to detect whether a stored user-override is still relevant. */
    fun upcomingFridayKey(): String = ShabbatTimesCalculator.upcomingFridayKey()

    private fun nextSaturday(): Calendar {
        val cal = Calendar.getInstance(TIMEZONE)
        val today = cal.get(Calendar.DAY_OF_WEEK)
        val daysOffset = if (today == Calendar.SATURDAY) 0 else (Calendar.SATURDAY - today + 7) % 7
        cal.add(Calendar.DAY_OF_MONTH, daysOffset)
        cal.set(Calendar.HOUR_OF_DAY, 12)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal
    }

    private fun findHolidayEntry(jc: JewishCalendar, all: List<DvarTorahEntry>): DvarTorahEntry? {
        // Walk forward up to 8 days from this Saturday looking for the next
        // Yom Tov day; fall back to whichever holiday entry name-matches.
        val probe = (jc.gregorianCalendar.clone() as Calendar)
        for (offset in 0..8) {
            val tov = JewishCalendar(probe.time).apply { inIsrael = true }
            if (tov.isYomTov || tov.isCholHamoed) {
                val name = runCatching {
                    HebrewDateFormatter().apply { isHebrewFormat = true }.formatYomTov(tov)
                }.getOrNull().orEmpty()
                val id = matchHolidayName(name)
                if (id != null) {
                    val entry = all.firstOrNull { it.id == id }
                    if (entry != null) return entry
                }
            }
            probe.add(Calendar.DAY_OF_MONTH, 1)
        }
        return null
    }

    private fun matchHolidayName(hebrewName: String): String? = when {
        hebrewName.contains("ראש השנה") -> "rosh_hashana"
        hebrewName.contains("יום הכפור") || hebrewName.contains("יום הכיפור") ||
            hebrewName.contains("יום כיפור") -> "yom_kippur"
        hebrewName.contains("שמיני עצרת") || hebrewName.contains("שמחת תורה") -> "shemini_atzeret"
        hebrewName.contains("סוכות") -> "sukkot"
        hebrewName.contains("שבועות") -> "shavuot"
        hebrewName.contains("פסח") && (
            hebrewName.contains("שביעי") || hebrewName.contains("אחרון")
        ) -> "shvii_pesach"
        hebrewName.contains("פסח") -> "pesach"
        else -> null
    }

    /**
     * Maps KosherJava's Parsha enum to an entry id. Combined parshiyot resolve
     * to the first half — the second half's content is implicitly "covered"
     * by sharing the week. The user can always override with custom text.
     */
    private fun mapParshaToId(parsha: JewishCalendar.Parsha): String? = when (parsha) {
        JewishCalendar.Parsha.BERESHIS -> "bereshit"
        JewishCalendar.Parsha.NOACH -> "noach"
        JewishCalendar.Parsha.LECH_LECHA -> "lech_lecha"
        JewishCalendar.Parsha.VAYERA -> "vayera"
        JewishCalendar.Parsha.CHAYEI_SARA -> "chayei_sarah"
        JewishCalendar.Parsha.TOLDOS -> "toldot"
        JewishCalendar.Parsha.VAYETZEI -> "vayetze"
        JewishCalendar.Parsha.VAYISHLACH -> "vayishlach"
        JewishCalendar.Parsha.VAYESHEV -> "vayeshev"
        JewishCalendar.Parsha.MIKETZ -> "miketz"
        JewishCalendar.Parsha.VAYIGASH -> "vayigash"
        JewishCalendar.Parsha.VAYECHI -> "vayechi"
        JewishCalendar.Parsha.SHEMOS -> "shemot"
        JewishCalendar.Parsha.VAERA -> "vaera"
        JewishCalendar.Parsha.BO -> "bo"
        JewishCalendar.Parsha.BESHALACH -> "beshalach"
        JewishCalendar.Parsha.YISRO -> "yitro"
        JewishCalendar.Parsha.MISHPATIM -> "mishpatim"
        JewishCalendar.Parsha.TERUMAH -> "terumah"
        JewishCalendar.Parsha.TETZAVEH -> "tetzaveh"
        JewishCalendar.Parsha.KI_SISA -> "ki_tisa"
        JewishCalendar.Parsha.VAYAKHEL -> "vayakhel"
        JewishCalendar.Parsha.PEKUDEI -> "pekudei"
        JewishCalendar.Parsha.VAYIKRA -> "vayikra"
        JewishCalendar.Parsha.TZAV -> "tzav"
        JewishCalendar.Parsha.SHMINI -> "shemini"
        JewishCalendar.Parsha.TAZRIA -> "tazria"
        JewishCalendar.Parsha.METZORA -> "metzora"
        JewishCalendar.Parsha.ACHREI_MOS -> "acharei_mot"
        JewishCalendar.Parsha.KEDOSHIM -> "kedoshim"
        JewishCalendar.Parsha.EMOR -> "emor"
        JewishCalendar.Parsha.BEHAR -> "behar"
        JewishCalendar.Parsha.BECHUKOSAI -> "bechukotai"
        JewishCalendar.Parsha.BAMIDBAR -> "bamidbar"
        JewishCalendar.Parsha.NASSO -> "naso"
        JewishCalendar.Parsha.BEHAALOSCHA -> "behaalotcha"
        JewishCalendar.Parsha.SHLACH -> "shelach"
        JewishCalendar.Parsha.KORACH -> "korach"
        JewishCalendar.Parsha.CHUKAS -> "chukat"
        JewishCalendar.Parsha.BALAK -> "balak"
        JewishCalendar.Parsha.PINCHAS -> "pinchas"
        JewishCalendar.Parsha.MATOS -> "matot"
        JewishCalendar.Parsha.MASEI -> "masei"
        JewishCalendar.Parsha.DEVARIM -> "devarim"
        JewishCalendar.Parsha.VAESCHANAN -> "vaetchanan"
        JewishCalendar.Parsha.EIKEV -> "ekev"
        JewishCalendar.Parsha.REEH -> "reeh"
        JewishCalendar.Parsha.SHOFTIM -> "shoftim"
        JewishCalendar.Parsha.KI_SEITZEI -> "ki_tetzei"
        JewishCalendar.Parsha.KI_SAVO -> "ki_tavo"
        JewishCalendar.Parsha.NITZAVIM -> "nitzavim"
        JewishCalendar.Parsha.VAYEILECH -> "vayelech"
        JewishCalendar.Parsha.HAAZINU -> "haazinu"
        JewishCalendar.Parsha.VZOS_HABERACHA -> "vezot_haberacha"
        // Combined parshiyot — show the first half. The user can override.
        JewishCalendar.Parsha.VAYAKHEL_PEKUDEI -> "vayakhel"
        JewishCalendar.Parsha.TAZRIA_METZORA -> "tazria"
        JewishCalendar.Parsha.ACHREI_MOS_KEDOSHIM -> "acharei_mot"
        JewishCalendar.Parsha.BEHAR_BECHUKOSAI -> "behar"
        JewishCalendar.Parsha.CHUKAS_BALAK -> "chukat"
        JewishCalendar.Parsha.MATOS_MASEI -> "matot"
        JewishCalendar.Parsha.NITZAVIM_VAYEILECH -> "nitzavim"
        // NONE / special-Shabbatot fallthrough — no direct match.
        else -> null
    }
}
