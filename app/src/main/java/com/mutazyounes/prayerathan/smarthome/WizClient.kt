package com.mutazyounes.prayerathan.smarthome

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

/**
 * UDP transport for [WizProtocol]. Blocking calls: run them off the main thread.
 *
 * A device that stays silent is retried [TRIES] times ([TIMEOUT_MS] each, about 2 s in all),
 * the same as the TV app.
 */
class WizClient(
    private val port: Int = WizProtocol.PORT,
) {

    /** Sends one JSON command and waits for that device's reply, retrying on silence. Null = no reply. */
    fun send(ip: String, json: String): String? {
        return try {
            DatagramSocket().use { socket ->
                val out = json.toByteArray(Charsets.UTF_8)
                val addr = InetAddress.getByName(ip)
                val packet = DatagramPacket(out, out.size, addr, port)
                val buf = ByteArray(2048)
                repeat(TRIES) {
                    socket.send(packet)
                    val until = System.currentTimeMillis() + TIMEOUT_MS
                    while (true) {
                        val left = (until - System.currentTimeMillis()).toInt()
                        if (left <= 0) break
                        socket.soTimeout = left
                        val reply = DatagramPacket(buf, buf.size)
                        try {
                            socket.receive(reply)
                        } catch (_: SocketTimeoutException) {
                            break // silence: send again
                        }
                        if (reply.address != addr) continue // ignore stray packets
                        return String(reply.data, 0, reply.length, Charsets.UTF_8)
                    }
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun getStatus(ip: String): WizProtocol.Status {
        val reply = send(ip, WizProtocol.GET_PILOT) ?: return WizProtocol.Status.unreachable("no reply")
        return WizProtocol.parseStatus(reply)
    }

    /** Module name such as ESP01_SHRGB1C_31, "" if it answered without one, null if it didn't answer. */
    fun getModule(ip: String): String? {
        val reply = send(ip, WizProtocol.GET_SYSTEM_CONFIG) ?: return null
        return WizProtocol.parseModule(reply)
    }

    fun command(ip: String, json: String): Boolean {
        val reply = send(ip, json) ?: return false
        return WizProtocol.isSuccess(reply)
    }

    data class Found(val ip: String, val mac: String?)

    /**
     * Broadcasts a WiZ registration message on every local subnet and collects the devices that
     * answer within [listenMs]. Only finds devices on the tablet's own subnet; devices behind
     * another router or a separate VLAN must be added by IP.
     */
    fun discover(listenMs: Int = DISCOVER_MS): List<Found> {
        val found = linkedMapOf<String, Found>()
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                val out = WizProtocol.REGISTRATION.toByteArray(Charsets.UTF_8)
                val targets = broadcastAddresses()
                repeat(DISCOVER_ROUNDS) {
                    for (target in targets) {
                        runCatching { socket.send(DatagramPacket(out, out.size, target, port)) }
                    }
                    val until = System.currentTimeMillis() + listenMs / DISCOVER_ROUNDS
                    val buf = ByteArray(2048)
                    while (true) {
                        val left = (until - System.currentTimeMillis()).toInt()
                        if (left <= 0) break
                        socket.soTimeout = left
                        val reply = DatagramPacket(buf, buf.size)
                        try {
                            socket.receive(reply)
                        } catch (_: SocketTimeoutException) {
                            break
                        }
                        val text = String(reply.data, 0, reply.length, Charsets.UTF_8)
                        val ip = reply.address.hostAddress ?: continue
                        if (!text.contains("\"result\"") && !text.contains("\"mac\"")) continue
                        found[ip] = Found(ip, WizProtocol.parseMac(text))
                    }
                }
            }
        } catch (_: Exception) {
            // No network: nothing found.
        }
        return found.values.toList()
    }

    private fun broadcastAddresses(): List<InetAddress> {
        val list = mutableListOf<InetAddress>()
        runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.interfaceAddresses }
                .mapNotNull { it.broadcast }
                .forEach { if (it !in list) list += it }
        }
        runCatching { list += InetAddress.getByName("255.255.255.255") }
        return list
    }

    companion object {
        const val TIMEOUT_MS = 700
        const val TRIES = 3
        const val DISCOVER_MS = 3000
        const val DISCOVER_ROUNDS = 3
    }
}
