package com.mutazyounes.prayerathan.engine

/** Calculation conventions the wall can use. Angles live in adhan-kotlin. */
enum class CalcMethod(val label: String) {
    MUSLIM_WORLD_LEAGUE("Muslim World League"),
    UMM_AL_QURA("Umm al-Qura, Makkah"),
    DUBAI("Dubai"),
    KUWAIT("Kuwait"),
    QATAR("Qatar"),
    EGYPTIAN("Egyptian General Authority"),
    KARACHI("Karachi"),
    NORTH_AMERICA("ISNA, North America"),
    MOON_SIGHTING_COMMITTEE("Moonsighting Committee"),
    SINGAPORE("Singapore"),
    ;

    companion object {
        fun fromStored(value: String?): CalcMethod? =
            entries.firstOrNull { it.name == value }
    }
}

enum class AsrSchool(val label: String) {
    SHAFI("Shafi"),
    HANAFI("Hanafi"),
    ;

    companion object {
        fun fromStored(value: String?): AsrSchool =
            entries.firstOrNull { it.name == value } ?: SHAFI
    }
}

/**
 * What the user picked. A null [method] means "Auto": follow the saved
 * location's region, so a new install is sensible before anyone opens settings.
 */
data class MethodSettings(
    val method: CalcMethod? = null,
    val asr: AsrSchool = AsrSchool.SHAFI,
) {
    companion object {
        val AUTO = MethodSettings()
    }
}

object PrayerMethod {

    fun resolve(settings: MethodSettings, timeZoneId: String): CalcMethod =
        settings.method ?: suggestedFor(timeZoneId)

    /** Region default from the IANA zone that every saved location carries. */
    fun suggestedFor(timeZoneId: String): CalcMethod = when {
        timeZoneId in DUBAI_ZONES -> CalcMethod.DUBAI
        timeZoneId in UMM_AL_QURA_ZONES -> CalcMethod.UMM_AL_QURA
        timeZoneId == "Asia/Kuwait" -> CalcMethod.KUWAIT
        timeZoneId == "Asia/Qatar" -> CalcMethod.QATAR
        timeZoneId in EGYPTIAN_ZONES -> CalcMethod.EGYPTIAN
        timeZoneId in KARACHI_ZONES -> CalcMethod.KARACHI
        timeZoneId in SINGAPORE_ZONES -> CalcMethod.SINGAPORE
        timeZoneId.startsWith("America/") ||
            timeZoneId.startsWith("US/") ||
            timeZoneId.startsWith("Canada/") -> CalcMethod.NORTH_AMERICA
        else -> CalcMethod.MUSLIM_WORLD_LEAGUE
    }

    fun settingsLine(settings: MethodSettings, timeZoneId: String): String {
        val method = resolve(settings, timeZoneId)
        val auto = if (settings.method == null) " (auto)" else ""
        return "Times: ${method.label}$auto. Asr is ${settings.asr.label}."
    }

    private val DUBAI_ZONES = setOf("Asia/Dubai", "Asia/Muscat")
    private val UMM_AL_QURA_ZONES = setOf("Asia/Riyadh", "Asia/Bahrain", "Asia/Aden")
    private val EGYPTIAN_ZONES = setOf(
        "Africa/Cairo",
        "Africa/Tripoli",
        "Africa/Khartoum",
        "Asia/Damascus",
        "Asia/Beirut",
    )
    private val KARACHI_ZONES = setOf(
        "Asia/Karachi",
        "Asia/Kolkata",
        "Asia/Calcutta",
        "Asia/Dhaka",
        "Asia/Kabul",
    )
    private val SINGAPORE_ZONES = setOf(
        "Asia/Singapore",
        "Asia/Kuala_Lumpur",
        "Asia/Kuching",
        "Asia/Jakarta",
        "Asia/Pontianak",
        "Asia/Makassar",
        "Asia/Jayapura",
        "Asia/Brunei",
    )
}
