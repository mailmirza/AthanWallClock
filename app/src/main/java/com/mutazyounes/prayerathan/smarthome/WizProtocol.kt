package com.mutazyounes.prayerathan.smarthome

import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * WiZ local control protocol: JSON over UDP port 38899 on the home network.
 * No cloud, no hub. Needs "Allow local communication" switched on in the WiZ app.
 *
 * Pure Kotlin: command builders and reply parsing only. The network lives in [WizClient].
 * Ported from the MyHome Android TV app (Wiz.java) so both apps speak the same way.
 */
object WizProtocol {

    const val PORT = 38899

    enum class Kind { Unknown, Plug, Dimmable, Tunable, Rgb }

    /** What a device reported. -1 means "not reported". */
    data class Status(
        val reachable: Boolean,
        val on: Boolean = false,
        val dimming: Int = -1,
        val temp: Int = -1,
        val r: Int = -1,
        val g: Int = -1,
        val b: Int = -1,
        val sceneId: Int = -1,
        val speed: Int = -1,
        val error: String? = null,
    ) {
        val isColour: Boolean get() = r >= 0 && g >= 0 && b >= 0 && sceneId <= 0 && temp <= 0

        companion object {
            fun unreachable(why: String?) = Status(reachable = false, error = why)
        }
    }

    private val STATE = Regex("\"state\"\\s*:\\s*(true|false)")
    private val DIMMING = Regex("\"dimming\"\\s*:\\s*(\\d+)")
    private val TEMP = Regex("\"temp\"\\s*:\\s*(\\d+)")
    private val R = Regex("\"r\"\\s*:\\s*(\\d+)")
    private val G = Regex("\"g\"\\s*:\\s*(\\d+)")
    private val B = Regex("\"b\"\\s*:\\s*(\\d+)")
    private val SCENE = Regex("\"sceneId\"\\s*:\\s*(\\d+)")
    private val SPEED = Regex("\"speed\"\\s*:\\s*(\\d+)")
    private val MODULE = Regex("\"moduleName\"\\s*:\\s*\"([^\"]*)\"")
    private val MAC = Regex("\"mac\"\\s*:\\s*\"([0-9a-fA-F]{12})\"")
    private val SUCCESS = Regex("\"success\"\\s*:\\s*true")
    private val ERROR = Regex("\"error\"\\s*:\\s*\\{[^}]*\"message\"\\s*:\\s*\"([^\"]*)\"")

    /** WiZ built-in scenes, by scene id (index = id). */
    val SCENES = listOf(
        "",
        "Ocean", "Romance", "Sunset", "Party", "Fireplace", "Cozy", "Forest", "Pastel colours",
        "Wake up", "Bedtime", "Warm white", "Daylight", "Cool white", "Night light", "Focus", "Relax",
        "True colours", "TV time", "Plant growth", "Spring", "Summer", "Fall", "Deep dive", "Jungle",
        "Mojito", "Club", "Christmas", "Halloween", "Candlelight", "Golden white", "Pulse", "Steampunk",
    )

    /** Scenes that stay still; the rest are animated and take a speed. */
    private val STATIC_SCENES = setOf(6, 11, 12, 13, 14, 15, 16, 18, 19)

    /** Scenes a white-only (tunable) bulb can show. */
    private val TUNABLE_SCENES = listOf(6, 9, 10, 11, 12, 13, 14, 15, 16, 18, 29, 30)

    /** Scenes a dimmable-only bulb can show. */
    private val DIMMABLE_SCENES = listOf(9, 10, 14, 29)

    /** White presets shown as chips: label to kelvin. */
    val WHITE_PRESETS = listOf(
        "Warm" to 2700,
        "Soft" to 3000,
        "Neutral" to 4000,
        "Cool" to 5000,
        "Daylight" to 6500,
    )

    /** Colour swatches shown as chips, as 0xRRGGBB. */
    val COLOUR_SWATCHES = listOf(
        0xFF0000, 0xFF6000, 0xFFB000, 0xFFFF00, 0x80FF00, 0x00FF40,
        0x00FFC0, 0x00C0FF, 0x0040FF, 0x6000FF, 0xC000FF, 0xFF0080,
    )

    const val MIN_DIMMING = 10
    const val MIN_KELVIN = 2200
    const val MAX_KELVIN = 6500

    // ------------------------------------------------------------------ commands

    val GET_PILOT = """{"method":"getPilot","params":{}}"""
    val GET_SYSTEM_CONFIG = """{"method":"getSystemConfig","params":{}}"""

    /** Discovery broadcast. Every WiZ device on the subnet answers with its MAC. */
    val REGISTRATION =
        """{"method":"registration","params":{"phoneMac":"AAAAAAAAAAAA","register":false,"phoneIp":"1.2.3.4","id":"1"}}"""

    private fun setPilot(params: String) = """{"id":1,"method":"setPilot","params":{$params}}"""

    fun setState(on: Boolean) = setPilot("\"state\":$on")

    fun setBrightness(percent: Int) =
        setPilot("\"state\":true,\"dimming\":${clamp(percent, MIN_DIMMING, 100)}")

    fun setWhite(kelvin: Int, percent: Int) = setPilot(
        "\"state\":true,\"temp\":${clamp(kelvin, MIN_KELVIN, MAX_KELVIN)}" +
            ",\"dimming\":${clamp(percent, MIN_DIMMING, 100)}",
    )

    fun setColour(r: Int, g: Int, b: Int, percent: Int) = setPilot(
        "\"state\":true,\"r\":${clamp(r, 0, 255)},\"g\":${clamp(g, 0, 255)}" +
            ",\"b\":${clamp(b, 0, 255)},\"dimming\":${clamp(percent, MIN_DIMMING, 100)}",
    )

    fun setScene(sceneId: Int, speed: Int, percent: Int): String {
        var p = "\"state\":true,\"sceneId\":$sceneId,\"dimming\":${clamp(percent, MIN_DIMMING, 100)}"
        if (!isStaticScene(sceneId)) p += ",\"speed\":${clamp(speed, 10, 200)}"
        return setPilot(p)
    }

    // ------------------------------------------------------------------ parsing

    fun parseStatus(reply: String): Status {
        ERROR.find(reply)?.let { return Status.unreachable(it.groupValues[1]) }
        val state = STATE.find(reply) ?: return Status.unreachable("unexpected reply")
        return Status(
            reachable = true,
            on = state.groupValues[1] == "true",
            dimming = num(DIMMING, reply),
            temp = num(TEMP, reply),
            r = num(R, reply),
            g = num(G, reply),
            b = num(B, reply),
            sceneId = num(SCENE, reply),
            speed = num(SPEED, reply),
        )
    }

    fun isSuccess(reply: String): Boolean = SUCCESS.containsMatchIn(reply)

    fun parseModule(reply: String): String = MODULE.find(reply)?.groupValues?.get(1).orEmpty()

    fun parseMac(reply: String): String? = MAC.find(reply)?.groupValues?.get(1)?.lowercase(Locale.ENGLISH)

    private fun num(p: Regex, s: String): Int = p.find(s)?.groupValues?.get(1)?.toIntOrNull() ?: -1

    /**
     * Module names look like ESP01_SHRGB1C_31 (colour), ESP01_SHTW1C_31 (tunable white),
     * ESP01_SHDW1_31 (dimmable white) or ESP10_SOCKET_06 (smart plug).
     */
    fun kindOf(module: String?): Kind {
        if (module.isNullOrEmpty()) return Kind.Unknown
        val m = module.uppercase(Locale.ENGLISH)
        return when {
            "SOCKET" in m || "PLUG" in m -> Kind.Plug
            "RGB" in m -> Kind.Rgb
            "TW" in m -> Kind.Tunable
            "DW" in m -> Kind.Dimmable
            else -> Kind.Rgb // unknown bulb: offer everything, the bulb ignores what it can't do
        }
    }

    fun scenesFor(kind: Kind): List<Int> = when (kind) {
        Kind.Tunable -> TUNABLE_SCENES
        Kind.Dimmable -> DIMMABLE_SCENES
        Kind.Plug -> emptyList()
        Kind.Rgb, Kind.Unknown -> (1..32).toList()
    }

    fun supportsWhite(kind: Kind) = kind == Kind.Tunable || kind == Kind.Rgb || kind == Kind.Unknown
    fun supportsColour(kind: Kind) = kind == Kind.Rgb || kind == Kind.Unknown
    fun supportsBrightness(kind: Kind) = kind != Kind.Plug

    fun isStaticScene(id: Int) = id in STATIC_SCENES

    fun sceneName(id: Int): String = if (id in 1 until SCENES.size) SCENES[id] else "Scene $id"

    /** Tile line: "On · 80% · 2700K", "On · 60% · Cozy", "Off", "Not responding". Plugs show On/Off. */
    fun statusLine(status: Status?, kind: Kind): String {
        if (status == null) return "Checking…"
        if (!status.reachable) return "Not responding"
        if (!status.on) return "Off"
        if (kind == Kind.Plug) return "On"
        val parts = mutableListOf("On")
        if (status.dimming >= 0) parts += "${status.dimming}%"
        when {
            status.sceneId > 0 -> parts += sceneName(status.sceneId)
            status.temp > 0 -> parts += "${status.temp}K"
            status.isColour -> parts += "Colour"
        }
        return parts.joinToString(" · ")
    }

    /** "All": if any reachable device is on, turn every device off; otherwise turn them all on. */
    fun allTarget(states: List<Status?>): Boolean = states.none { it != null && it.reachable && it.on }

    fun clamp(v: Int, lo: Int, hi: Int) = v.coerceIn(lo, hi)

    /** Hue 0–359, saturation 0–100, full value → (r, g, b). */
    fun hsvToRgb(hue: Int, sat: Int): Triple<Int, Int, Int> {
        val h = (((hue % 360) + 360) % 360) / 60f
        val s = clamp(sat, 0, 100) / 100f
        val i = floor(h).toInt()
        val f = h - i
        val p = 1 - s
        val q = 1 - s * f
        val t = 1 - s * (1 - f)
        val (r, g, b) = when (i) {
            0 -> Triple(1f, t, p)
            1 -> Triple(q, 1f, p)
            2 -> Triple(p, 1f, t)
            3 -> Triple(p, q, 1f)
            4 -> Triple(t, p, 1f)
            else -> Triple(1f, p, q)
        }
        return Triple((r * 255).roundToInt(), (g * 255).roundToInt(), (b * 255).roundToInt())
    }

    /** (r, g, b) → (hue 0–359, saturation 0–100). */
    fun rgbToHs(r: Int, g: Int, b: Int): Pair<Int, Int> {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val max = maxOf(rf, gf, bf)
        val min = minOf(rf, gf, bf)
        val d = max - min
        var hue = when {
            d == 0f -> 0f
            max == rf -> 60 * (((gf - bf) / d) % 6)
            max == gf -> 60 * ((bf - rf) / d + 2)
            else -> 60 * ((rf - gf) / d + 4)
        }
        if (hue < 0) hue += 360
        val sat = if (max == 0f) 0 else (d / max * 100).roundToInt()
        return Pair(hue.roundToInt() % 360, sat)
    }

    /** Loose IPv4 check for the "add by IP" field. */
    fun isIpv4(text: String): Boolean {
        val parts = text.trim().split(".")
        if (parts.size != 4) return false
        return parts.all { p -> p.isNotEmpty() && p.length <= 3 && p.all(Char::isDigit) && p.toInt() in 0..255 }
    }
}
