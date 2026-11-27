package com.remotemagic.app.bluetooth

import com.remotemagic.app.domain.TextResult
import com.remotemagic.app.protocols.PointerInput
import com.remotemagic.app.protocols.TextInput
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import com.remotemagic.app.domain.DiscoveredDevice
import com.remotemagic.app.domain.Protocol
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.remotemagic.app.domain.RemoteKey
import com.remotemagic.app.protocols.Credentials
import com.remotemagic.app.protocols.PairingUi
import com.remotemagic.app.protocols.TvClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.concurrent.Executors

/**
 * The phone acts as a Bluetooth keyboard / media remote (HID Device profile, Android 9+).
 * A blank [address] means: wait for the TV to find and pair with this phone.
 */
@RequiresApi(Build.VERSION_CODES.P)
@SuppressLint("MissingPermission") // permissions are requested by the connect screen
class BluetoothHidClient(private val context: Context, private val address: String) : TvClient {
    private var hid: BluetoothHidDevice? = null
    private var target: BluetoothDevice? = null
    private val executor = Executors.newSingleThreadExecutor()
    override var onClosed: (() -> Unit)? = null
    override var resolved: DiscoveredDevice? = null
        private set
    override val supportedKeys get() = HidReports.keys

    override suspend fun connect(saved: Credentials, pairing: PairingUi): Credentials {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: throw IOException("This phone has no Bluetooth")
        if (!adapter.isEnabled) throw IOException("Turn Bluetooth on first")
        val device = if (address.isEmpty()) null else adapter.getRemoteDevice(address)
        val alreadyBonded = adapter.bondedDevices.orEmpty().map { it.address }.toSet()
        val proxy = CompletableDeferred<BluetoothHidDevice>()
        val listening = adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, service: BluetoothProfile) {
                proxy.complete(service as BluetoothHidDevice)
            }
            override fun onServiceDisconnected(profile: Int) { onClosed?.invoke() }
        }, BluetoothProfile.HID_DEVICE)
        if (!listening) throw IOException("This phone does not support the Bluetooth HID remote profile")

        try {
            val service = withTimeout(5000) { proxy.await() }
            hid = service
            target = device
            val registered = CompletableDeferred<Boolean>()
            val connected = CompletableDeferred<Unit>()
            val sdp = BluetoothHidDeviceAppSdpSettings(
                "REMOTE MAGIC", "TV remote", "tayebi7",
                BluetoothHidDevice.SUBCLASS1_COMBO, HidReports.DESCRIPTOR
            )
            val callback = object : BluetoothHidDevice.Callback() {
                override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, isRegistered: Boolean) {
                    registered.complete(isRegistered)
                }
                override fun onConnectionStateChanged(changed: BluetoothDevice, state: Int) {
                    val expected = target?.address
                    if (expected != null && changed.address != expected) return
                    if (state == BluetoothProfile.STATE_CONNECTED) {
                        target = changed
                        connected.complete(Unit)
                    }
                    if (state == BluetoothProfile.STATE_DISCONNECTED && connected.isCompleted) onClosed?.invoke()
                }
            }
            if (!service.registerApp(sdp, null, null, executor, callback) || !withTimeout(5000) { registered.await() }) {
                throw IOException("Bluetooth HID registration failed on this phone")
            }
            if (device == null) {
                awaitHost(adapter, service, alreadyBonded, connected)
                val host = target ?: throw IOException("No TV connected")
                resolved = DiscoveredDevice(
                    host.name ?: host.address, Protocol.BLUETOOTH_HID, host.address, paired = true
                )
            } else if (service.getConnectionState(device) != BluetoothProfile.STATE_CONNECTED) {
                pairing.showPromptOnTv()
                // Real pairing first; Android shows the PIN / confirmation dialog itself.
                if (device.bondState != BluetoothDevice.BOND_BONDED) bond(device)
                service.connect(device)
                withTimeout(30_000) { connected.await() }
            }
            return saved
        } catch (e: Exception) {
            disconnect()
            throw IOException(e.message ?: "Bluetooth connection failed", e)
        }
    }

    /** Waits until a TV connects; a TV that only pairs gets the HID link opened from our side. */
    private suspend fun awaitHost(
        adapter: BluetoothAdapter,
        service: BluetoothHidDevice,
        alreadyBonded: Set<String>,
        connected: CompletableDeferred<Unit>
    ) = withTimeout(120_000) {
        val tried = HashSet<String>()
        while (!connected.isCompleted) {
            adapter.bondedDevices.orEmpty()
                .firstOrNull { it.address !in alreadyBonded && it.address !in tried }
                ?.let {
                    delay(1_500) // give the TV the chance to open the link itself
                    tried.add(it.address)
                    if (!connected.isCompleted) {
                        target = it
                        service.connect(it)
                    }
                }
            delay(500)
        }
    }

    private suspend fun bond(device: BluetoothDevice) {
        if (device.bondState != BluetoothDevice.BOND_BONDING && !device.createBond()) {
            throw IOException("Pairing could not start. Make the TV visible and try again.")
        }
        var sawBonding = false
        withTimeout(60_000) {
            while (device.bondState != BluetoothDevice.BOND_BONDED) {
                if (device.bondState == BluetoothDevice.BOND_BONDING) sawBonding = true
                else if (sawBonding) throw IOException("Pairing was refused or cancelled")
                delay(300)
            }
        }
    }

    private fun raw(id: Int, data: ByteArray): Boolean {
        val service = hid ?: return false
        val device = target ?: return false
        return service.sendReport(device, id, data)
    }

    private suspend fun tap(report: HidReports.Report): Boolean {
        if (!raw(report.id, report.press)) return false
        delay(20)
        return raw(report.id, report.release)
    }

    override suspend fun send(key: RemoteKey) = HidReports.report(key)?.let { tap(it) } ?: false

    override val pointer = object : PointerInput {
        override val canScroll = true
        override val canLongPress = true
        private fun mouse(buttons: Int, dx: Int = 0, dy: Int = 0, wheel: Int = 0) =
            raw(HidReports.MOUSE_ID, HidReports.mouse(buttons, dx, dy, wheel))
        private suspend fun press(millis: Long): Boolean {
            if (!mouse(1)) return false
            delay(millis)
            return mouse(0)
        }
        override suspend fun move(dx: Int, dy: Int) = mouse(0, dx, dy)
        override suspend fun click() = press(40)
        override suspend fun longClick() = press(800)
        override suspend fun scroll(steps: Int) = mouse(0, wheel = steps)
    }

    override val textInput = object : TextInput {
        override suspend fun type(text: String): TextResult {
            val reports = text.map { HidReports.character(it) ?: return TextResult.BAD_CHARACTERS }
            for (report in reports) {
                if (!tap(report)) return TextResult.FAILED
                delay(10)
            }
            return TextResult.SENT
        }
        override suspend fun backspace() = tap(HidReports.keyboardReport(0x2A))
        override suspend fun enter() = tap(HidReports.keyboardReport(0x28))
    }

    override fun disconnect() {
        val service = hid ?: return
        onClosed = null
        target?.let { service.disconnect(it) }
        service.unregisterApp()
        context.getSystemService(BluetoothManager::class.java)?.adapter
            ?.closeProfileProxy(BluetoothProfile.HID_DEVICE, service)
        hid = null
        executor.shutdown()
    }
}
