package com.mutazyounes.prayerathan.smarthome

import com.mutazyounes.prayerathan.smarthome.WizProtocol.Kind

/** One saved WiZ device. [kind] is learned from the device (getSystemConfig) the first time it answers. */
data class Light(
    val id: String,
    val name: String,
    val ip: String,
    val kind: Kind = Kind.Unknown,
)

/** A device found by a network scan that isn't saved yet. */
data class FoundLight(
    val ip: String,
    val kind: Kind,
) {
    val suggestedName: String
        get() = (if (kind == Kind.Plug) "Plug " else "Light ") + ip.substringAfterLast('.')
}

/**
 * Saved lights as plain text, one per line: id, name, ip, kind separated by tabs.
 * Pure Kotlin so it can be unit tested without Android.
 */
object LightCodec {

    fun encode(lights: List<Light>): String = lights.joinToString("\n") { light ->
        listOf(light.id, clean(light.name), light.ip.trim(), light.kind.name).joinToString("\t")
    }

    fun decode(text: String?): List<Light> {
        if (text.isNullOrBlank()) return emptyList()
        return text.lines().mapNotNull { line ->
            val parts = line.split("\t")
            if (parts.size < 3 || parts[0].isBlank() || parts[2].isBlank()) return@mapNotNull null
            val kind = parts.getOrNull(3)?.let { k -> Kind.entries.firstOrNull { it.name == k } } ?: Kind.Unknown
            Light(id = parts[0], name = parts[1], ip = parts[2], kind = kind)
        }
    }

    /** Names are one line, no tabs, at most 24 characters. */
    fun clean(name: String): String =
        name.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim().take(24)
}
