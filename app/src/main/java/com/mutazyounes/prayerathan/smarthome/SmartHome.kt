package com.mutazyounes.prayerathan.smarthome

import android.content.Context
import com.mutazyounes.prayerathan.smarthome.WizProtocol.Kind
import com.mutazyounes.prayerathan.smarthome.WizProtocol.Status
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Smart-home panel state and actions: saved WiZ lights, their live status, and commands.
 * Hub-less WiZ local control on the home network, the same as the MyHome TV app.
 *
 * Commands to one device run one at a time, so a slider release and a tap can't cross.
 */
class SmartHome(
    context: Context,
    private val client: WizClient = WizClient(),
) {
    data class LightView(
        val light: Light,
        val status: Status? = null,
        val busy: Boolean = false,
    )

    sealed interface Scan {
        data object Idle : Scan
        data object Running : Scan
        data class Done(val found: List<FoundLight>) : Scan
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val locks = ConcurrentHashMap<String, Mutex>()

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _lights = MutableStateFlow(
        LightCodec.decode(prefs.getString(KEY_LIGHTS, null)).map { LightView(it) },
    )
    val lights: StateFlow<List<LightView>> = _lights.asStateFlow()

    private val _scan = MutableStateFlow<Scan>(Scan.Idle)
    val scan: StateFlow<Scan> = _scan.asStateFlow()

    fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, on).apply()
        _enabled.value = on
    }

    // ------------------------------------------------------------------ status

    /** Reads every device in parallel. Learns a device's kind the first time it answers. */
    suspend fun refreshAll() = coroutineScope {
        _lights.value.map { view -> async(Dispatchers.IO) { refresh(view.light.id) } }.awaitAll()
        Unit
    }

    private suspend fun refresh(id: String) {
        lockFor(id).withLock {
            var light = find(id) ?: return
            if (light.kind == Kind.Unknown) {
                val module = client.getModule(light.ip)
                if (module != null) {
                    light = light.copy(kind = WizProtocol.kindOf(module))
                    updateLight(id) { light }
                }
            }
            val status = client.getStatus(light.ip)
            updateView(id) { it.copy(status = status) }
        }
    }

    // ------------------------------------------------------------------ commands

    /** Smart toggle: reads the real state, then flips it. */
    fun toggle(id: String) = act(id) { ip ->
        val now = client.getStatus(ip)
        if (now.reachable) {
            updateView(id) { it.copy(status = now.copy(on = !now.on)) } // feel instant
            client.command(ip, WizProtocol.setState(!now.on))
        }
    }

    fun setOn(id: String, on: Boolean) = act(id) { ip -> client.command(ip, WizProtocol.setState(on)) }

    fun setBrightness(id: String, percent: Int) =
        act(id) { ip -> client.command(ip, WizProtocol.setBrightness(percent)) }

    fun setWhite(id: String, kelvin: Int, percent: Int) =
        act(id) { ip -> client.command(ip, WizProtocol.setWhite(kelvin, percent)) }

    fun setColour(id: String, r: Int, g: Int, b: Int, percent: Int) =
        act(id) { ip -> client.command(ip, WizProtocol.setColour(r, g, b, percent)) }

    fun setScene(id: String, sceneId: Int, speed: Int, percent: Int) =
        act(id) { ip -> client.command(ip, WizProtocol.setScene(sceneId, speed, percent)) }

    /** "All": if any light is on, turn everything off; otherwise turn everything on. */
    fun toggleAll() {
        scope.launch {
            val target = WizProtocol.allTarget(_lights.value.map { it.status })
            coroutineScope {
                _lights.value.map { view ->
                    async(Dispatchers.IO) {
                        lockFor(view.light.id).withLock {
                            client.command(view.light.ip, WizProtocol.setState(target))
                        }
                    }
                }.awaitAll()
            }
            refreshAll()
        }
    }

    private fun act(id: String, block: (ip: String) -> Unit) {
        scope.launch {
            lockFor(id).withLock {
                val light = find(id) ?: return@withLock
                updateView(id) { it.copy(busy = true) }
                block(light.ip)
                val status = client.getStatus(light.ip)
                updateView(id) { it.copy(status = status, busy = false) }
            }
        }
    }

    // ------------------------------------------------------------------ editing

    fun add(name: String, ip: String, kind: Kind = Kind.Unknown) {
        val trimmed = ip.trim()
        if (!WizProtocol.isIpv4(trimmed)) return
        if (_lights.value.any { it.light.ip == trimmed }) return
        val label = LightCodec.clean(name).ifEmpty { FoundLight(trimmed, kind).suggestedName }
        val light = Light(UUID.randomUUID().toString(), label, trimmed, kind)
        _lights.update { it + LightView(light) }
        save()
        _scan.update { scan ->
            if (scan is Scan.Done) Scan.Done(scan.found.filterNot { it.ip == trimmed }) else scan
        }
        scope.launch { refresh(light.id) }
    }

    fun rename(id: String, name: String) {
        val label = LightCodec.clean(name)
        if (label.isEmpty()) return
        updateLight(id) { it.copy(name = label) }
    }

    fun remove(id: String) {
        _lights.update { list -> list.filterNot { it.light.id == id } }
        locks.remove(id)
        save()
    }

    fun move(id: String, delta: Int) {
        _lights.update { list ->
            val from = list.indexOfFirst { it.light.id == id }
            val to = from + delta
            if (from < 0 || to !in list.indices) return@update list
            list.toMutableList().apply { add(to, removeAt(from)) }
        }
        save()
    }

    /** Looks for WiZ devices on the tablet's subnet that aren't saved yet. */
    fun startScan() {
        if (_scan.value == Scan.Running) return
        _scan.value = Scan.Running
        scope.launch {
            val saved = _lights.value.map { it.light.ip }.toSet()
            val found = client.discover()
                .filter { it.ip !in saved }
                .map { f -> FoundLight(f.ip, WizProtocol.kindOf(client.getModule(f.ip))) }
                .sortedBy { it.ip.substringAfterLast('.').toIntOrNull() ?: 0 }
            _scan.value = Scan.Done(found)
        }
    }

    fun clearScan() {
        _scan.value = Scan.Idle
    }

    // ------------------------------------------------------------------ helpers

    private fun find(id: String): Light? = _lights.value.firstOrNull { it.light.id == id }?.light

    private fun lockFor(id: String): Mutex = locks.getOrPut(id) { Mutex() }

    private fun updateView(id: String, change: (LightView) -> LightView) {
        _lights.update { list -> list.map { if (it.light.id == id) change(it) else it } }
    }

    private fun updateLight(id: String, change: (Light) -> Light) {
        updateView(id) { it.copy(light = change(it.light)) }
        save()
    }

    private fun save() {
        prefs.edit().putString(KEY_LIGHTS, LightCodec.encode(_lights.value.map { it.light })).apply()
    }

    companion object {
        private const val PREFS = "prayerathan_smarthome"
        private const val KEY_ENABLED = "panel_enabled"
        private const val KEY_LIGHTS = "lights"
    }
}
