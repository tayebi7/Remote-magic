package com.remotemagic.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import com.remotemagic.app.domain.ConnectionStatus
import kotlinx.coroutines.delay
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.remotemagic.app.R
import com.remotemagic.app.RemoteMagicApp
import com.remotemagic.app.data.HistoryEntry
import com.remotemagic.app.data.KeystoreCrypto
import com.remotemagic.app.data.SavedDevice
import com.remotemagic.app.discovery.DiscoveryService
import com.remotemagic.app.domain.DiscoveredDevice
import com.remotemagic.app.domain.Protocol
import com.remotemagic.app.protocols.Credentials
import com.remotemagic.app.protocols.PairingUi
import com.remotemagic.app.wifi.WakeOnLan
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PairingDialog {
    data class Code(val answer: CompletableDeferred<String?>) : PairingDialog
    data object Prompt : PairingDialog
}

fun SavedDevice.toDiscovered() = DiscoveredDevice(
    name, Protocol.entries.firstOrNull { it.name == protocol } ?: Protocol.ROKU, address, manufacturer, mac
)

class ConnectionViewModel(application: Application) : AndroidViewModel(application), PairingUi {
    private val app = application as RemoteMagicApp
    private val dao = app.database.dao()
    val connection = app.controller.state
    val saved = dao.devices().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val history = dao.history().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val found = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val scanning = MutableStateFlow(false)
    val pairing = MutableStateFlow<PairingDialog?>(null)
    /** One-shot message as a string resource id. */
    val message = MutableStateFlow<Int?>(null)

    private var scan: Job? = null
    private var connecting: Job? = null

    private var retry: Job? = null

    init {
        viewModelScope.launch {
            if (app.settings.settings.first().autoConnect && connection.value.device == null) {
                dao.lastDevice()?.toDiscovered()?.takeIf { it.protocol.wifi || bluetoothAllowed() }?.let { connect(it) }
            }
        }
        // Smart retry after an unexpected drop: a few attempts with growing delays.
        viewModelScope.launch {
            connection.collect { state ->
                val device = state.device
                if (state.lost && device != null && retry?.isActive != true) {
                    retry = viewModelScope.launch {
                        if (!app.settings.settings.first().autoConnect) return@launch
                        for (wait in longArrayOf(2_000, 5_000, 15_000, 30_000)) {
                            delay(wait)
                            attempt(device)
                            if (connection.value.status == ConnectionStatus.CONNECTED) break
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                app.controller.refreshPower()
                delay(8_000)
            }
        }
    }

    private fun bluetoothAllowed() = Build.VERSION.SDK_INT < 31 ||
        app.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun add(device: DiscoveredDevice) = found.update { list ->
        if (list.any { it.id == device.id }) list else list + device
    }

    private fun startScan(block: suspend () -> Unit) {
        stopScan()
        found.value = emptyList()
        scanning.value = true
        scan = viewModelScope.launch {
            try {
                block()
            } catch (e: SecurityException) {
                message.value = R.string.permission_denied
            } finally {
                if (scan === coroutineContext[Job]) scanning.value = false
            }
        }
    }

    fun scanWifi() = startScan { app.discovery.discover().collect { add(it) } }
    fun scanBluetooth() = startScan { app.bluetooth.scan().collect { add(it) } }

    fun probe(host: String) {
        val ip = host.trim()
        if (!DiscoveryService.isValidIpv4(ip)) {
            message.value = R.string.invalid_ip
            return
        }
        startScan {
            val devices = app.discovery.probe(ip)
            devices.forEach { add(it) }
            if (devices.isEmpty()) message.value = R.string.nothing_at_ip
        }
    }

    fun stopScan() {
        scan?.cancel()
        scan = null
        scanning.value = false
    }

    fun connect(device: DiscoveredDevice) {
        if (!device.supported) {
            message.value = R.string.ble_unsupported
            return
        }
        stopScan()
        retry?.cancel()
        connecting?.cancel()
        connecting = viewModelScope.launch { attempt(device) }
    }

    private suspend fun attempt(device: DiscoveredDevice) {
        val old = dao.device(device.id)
        val credentials = Credentials(
            KeystoreCrypto.decrypt(old?.secret.orEmpty()), old?.certPin.orEmpty(),
            old?.mac.orEmpty().ifEmpty { device.mac }
        )
        val result = try {
            app.controller.connect(device, credentials, this@ConnectionViewModel)
        } finally {
            pairing.value = null
        }
        val now = System.currentTimeMillis()
        // A TV that found the phone is only known after connecting.
        val target = connection.value.device?.takeIf { result != null } ?: device
        dao.addHistory(
            HistoryEntry(
                name = device.name, protocol = device.protocol.label, time = now,
                success = result != null, detail = connection.value.error.orEmpty()
            )
        )
        if (result != null) {
            dao.save(
                SavedDevice(
                    target.id, old?.name ?: target.name, target.protocol.name, target.address,
                    target.manufacturer.ifEmpty { old?.manufacturer.orEmpty() }, result.mac,
                    KeystoreCrypto.encrypt(result.token), result.certPin, old?.favorite ?: false, now
                )
            )
        }
    }

    fun disconnect() {
        retry?.cancel()
        connecting?.cancel()
        app.controller.disconnect()
    }

    fun wake(device: SavedDevice) = viewModelScope.launch {
        message.value =
            if (WakeOnLan.wake(device.mac, device.address)) R.string.wake_sent else R.string.send_failed
    }

    fun setFavorite(device: SavedDevice, value: Boolean) = viewModelScope.launch { dao.setFavorite(device.id, value) }
    fun setMac(device: SavedDevice, mac: String) = viewModelScope.launch { dao.setMac(device.id, mac.trim()) }
    fun delete(device: SavedDevice) = viewModelScope.launch {
        if (connection.value.device?.id == device.id) disconnect()
        dao.delete(device.id)
    }
    fun deleteAll() = viewModelScope.launch { disconnect(); dao.deleteDevices() }
    fun clearHistory() = viewModelScope.launch { dao.clearHistory() }

    suspend fun screenshot(): ByteArray? = app.controller.client?.screenshot()
    val canScreenshot: Boolean get() = app.controller.client?.supportsScreenshot == true

    override suspend fun requestCode(): String? {
        val answer = CompletableDeferred<String?>()
        pairing.value = PairingDialog.Code(answer)
        return answer.await().also { pairing.value = null }
    }

    override fun showPromptOnTv() { pairing.value = PairingDialog.Prompt }
}
