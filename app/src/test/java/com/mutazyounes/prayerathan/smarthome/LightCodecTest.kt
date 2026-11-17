package com.mutazyounes.prayerathan.smarthome

import com.mutazyounes.prayerathan.smarthome.WizProtocol.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LightCodecTest {

    @Test
    fun roundTrip() {
        val lights = listOf(
            Light("a", "Pendant 1", "192.168.1.238", Kind.Rgb),
            Light("b", "Table Lights", "192.168.1.126", Kind.Plug),
            Light("c", "New", "192.168.1.9"),
        )
        assertEquals(lights, LightCodec.decode(LightCodec.encode(lights)))
    }

    @Test
    fun cleansNamesAndSkipsBadLines() {
        val encoded = LightCodec.encode(listOf(Light("a", "Bad\tname\nhere", "10.0.0.2")))
        assertEquals("Bad name here", LightCodec.decode(encoded).single().name)
        assertTrue(LightCodec.decode("").isEmpty())
        assertTrue(LightCodec.decode(null).isEmpty())
        assertEquals(1, LightCodec.decode("x\tA\t10.0.0.1\tWeird\nbroken line\n\t\t").size)
        assertEquals(Kind.Unknown, LightCodec.decode("x\tA\t10.0.0.1\tWeird").single().kind)
        assertEquals(24, LightCodec.clean("x".repeat(40)).length)
    }

    @Test
    fun suggestedNames() {
        assertEquals("Plug 126", FoundLight("192.168.1.126", Kind.Plug).suggestedName)
        assertEquals("Light 238", FoundLight("192.168.1.238", Kind.Rgb).suggestedName)
    }
}
