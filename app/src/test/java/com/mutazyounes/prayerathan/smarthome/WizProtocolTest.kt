package com.mutazyounes.prayerathan.smarthome

import com.mutazyounes.prayerathan.smarthome.WizProtocol.Kind
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WizProtocolTest {

    @Test
    fun parsesWhiteStatus() {
        val s = WizProtocol.parseStatus(
            """{"method":"getPilot","env":"pro","result":{"mac":"a8bb50aabbcc","rssi":-55,"state":true,"sceneId":0,"temp":2700,"dimming":80}}""",
        )
        assertTrue(s.reachable)
        assertTrue(s.on)
        assertEquals(80, s.dimming)
        assertEquals(2700, s.temp)
        assertEquals(0, s.sceneId)
        assertFalse(s.isColour)
        assertEquals("On · 80% · 2700K", WizProtocol.statusLine(s, Kind.Tunable))
    }

    @Test
    fun parsesColourAndSceneStatus() {
        val colour = WizProtocol.parseStatus(
            """{"result":{"state":true,"sceneId":0,"r":255,"g":96,"b":0,"c":0,"w":0,"dimming":50}}""",
        )
        assertTrue(colour.isColour)
        assertEquals("On · 50% · Colour", WizProtocol.statusLine(colour, Kind.Rgb))

        val scene = WizProtocol.parseStatus("""{"result":{"state":true,"sceneId":6,"dimming":60}}""")
        assertEquals("On · 60% · Cozy", WizProtocol.statusLine(scene, Kind.Rgb))
    }

    @Test
    fun offPlugAndErrors() {
        val off = WizProtocol.parseStatus("""{"result":{"state":false,"dimming":100}}""")
        assertEquals("Off", WizProtocol.statusLine(off, Kind.Rgb))
        val plugOn = WizProtocol.parseStatus("""{"result":{"state":true}}""")
        assertEquals("On", WizProtocol.statusLine(plugOn, Kind.Plug))

        val err = WizProtocol.parseStatus("""{"error":{"code":-32601,"message":"Method not found"}}""")
        assertFalse(err.reachable)
        assertEquals("Method not found", err.error)
        assertEquals("Not responding", WizProtocol.statusLine(err, Kind.Rgb))
        assertEquals("Checking…", WizProtocol.statusLine(null, Kind.Rgb))
        assertFalse(WizProtocol.parseStatus("garbage").reachable)
    }

    @Test
    fun kindsFromModuleNames() {
        assertEquals(Kind.Rgb, WizProtocol.kindOf("ESP01_SHRGB1C_31"))
        assertEquals(Kind.Tunable, WizProtocol.kindOf("ESP01_SHTW1C_31"))
        assertEquals(Kind.Dimmable, WizProtocol.kindOf("ESP01_SHDW1_31"))
        assertEquals(Kind.Plug, WizProtocol.kindOf("ESP10_SOCKET_06"))
        assertEquals(Kind.Unknown, WizProtocol.kindOf(""))
        assertEquals(Kind.Unknown, WizProtocol.kindOf(null))
        assertEquals("ESP10_SOCKET_06", WizProtocol.parseModule("""{"result":{"moduleName":"ESP10_SOCKET_06","mac":"A8BB50112233"}}"""))
        assertEquals("a8bb50112233", WizProtocol.parseMac("""{"result":{"mac":"A8BB50112233"}}"""))
        assertNull(WizProtocol.parseMac("{}"))
    }

    @Test
    fun capabilitiesPerKind() {
        assertTrue(WizProtocol.scenesFor(Kind.Plug).isEmpty())
        assertEquals(32, WizProtocol.scenesFor(Kind.Rgb).size)
        assertEquals(listOf(9, 10, 14, 29), WizProtocol.scenesFor(Kind.Dimmable))
        assertFalse(WizProtocol.supportsBrightness(Kind.Plug))
        assertFalse(WizProtocol.supportsColour(Kind.Tunable))
        assertTrue(WizProtocol.supportsWhite(Kind.Tunable))
        assertFalse(WizProtocol.supportsWhite(Kind.Dimmable))
    }

    @Test
    fun commandsClampValues() {
        assertEquals("""{"id":1,"method":"setPilot","params":{"state":false}}""", WizProtocol.setState(false))
        assertTrue(WizProtocol.setBrightness(3).contains("\"dimming\":10"))
        assertTrue(WizProtocol.setWhite(9000, 150).contains("\"temp\":6500,\"dimming\":100"))
        assertTrue(WizProtocol.setColour(300, -4, 20, 50).contains("\"r\":255,\"g\":0,\"b\":20"))
        assertFalse(WizProtocol.setScene(6, 100, 50).contains("speed")) // Cozy is static
        assertTrue(WizProtocol.setScene(4, 500, 50).contains("\"speed\":200")) // Party is animated
        assertTrue(WizProtocol.isSuccess("""{"method":"setPilot","result":{"success":true}}"""))
        assertFalse(WizProtocol.isSuccess("""{"result":{"success":false}}"""))
    }

    @Test
    fun allTurnsOffWhenAnyIsOn() {
        val on = WizProtocol.Status(reachable = true, on = true)
        val off = WizProtocol.Status(reachable = true, on = false)
        val gone = WizProtocol.Status.unreachable("x")
        assertFalse(WizProtocol.allTarget(listOf(off, on)))
        assertTrue(WizProtocol.allTarget(listOf(off, gone, null)))
    }

    @Test
    fun colourConversionsRoundTrip() {
        assertEquals(Triple(255, 0, 0), WizProtocol.hsvToRgb(0, 100))
        assertEquals(Triple(0, 255, 0), WizProtocol.hsvToRgb(120, 100))
        assertEquals(Triple(255, 255, 255), WizProtocol.hsvToRgb(200, 0))
        assertEquals(Pair(240, 100), WizProtocol.rgbToHs(0, 0, 255))
        val (h, s) = WizProtocol.rgbToHs(255, 96, 0)
        val (r, g, b) = WizProtocol.hsvToRgb(h, s)
        assertEquals(255, r)
        assertTrue(g in 92..100)
        assertEquals(0, b)
    }

    @Test
    fun ipCheck() {
        assertTrue(WizProtocol.isIpv4("192.168.1.238"))
        assertTrue(WizProtocol.isIpv4(" 10.0.0.5 "))
        assertFalse(WizProtocol.isIpv4("192.168.1"))
        assertFalse(WizProtocol.isIpv4("192.168.1.256"))
        assertFalse(WizProtocol.isIpv4("bulb.local"))
    }

    @Test
    fun clientTalksToAFakeBulbOverUdp() {
        val bulb = DatagramSocket(0, InetAddress.getLoopbackAddress())
        val received = mutableListOf<String>()
        val server = thread {
            val buf = ByteArray(2048)
            repeat(2) {
                val p = DatagramPacket(buf, buf.size)
                bulb.receive(p)
                val text = String(p.data, 0, p.length)
                received += text
                val reply = if (text.contains("getPilot")) {
                    """{"method":"getPilot","result":{"state":true,"dimming":40,"temp":3000}}"""
                } else {
                    """{"method":"setPilot","result":{"success":true}}"""
                }.toByteArray()
                bulb.send(DatagramPacket(reply, reply.size, p.address, p.port))
            }
        }
        val client = WizClient(port = bulb.localPort)
        val status = client.getStatus("127.0.0.1")
        assertTrue(status.reachable)
        assertEquals(40, status.dimming)
        assertTrue(client.command("127.0.0.1", WizProtocol.setState(false)))
        server.join(3000)
        bulb.close()
        assertTrue(received[1].contains("\"state\":false"))
    }

    @Test
    fun clientReportsSilenceAsUnreachable() {
        val silent = DatagramSocket(0, InetAddress.getLoopbackAddress())
        val client = WizClient(port = silent.localPort)
        val status = client.getStatus("127.0.0.1")
        silent.close()
        assertFalse(status.reachable)
    }
}
