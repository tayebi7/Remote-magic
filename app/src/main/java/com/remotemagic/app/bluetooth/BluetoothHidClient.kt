package com.remotemagic.app.bluetooth

import android.annotation.SuppressLint
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

/** The phone acts as a Bluetooth keyboard / media remote (HID Device profile, Android 9+). */
@RequiresApi(Build.VERSION_CODES.P)
@SuppressLint("MissingPermission") // permissions are requested by the connect screen
class BluetoothHidClient(private val context: Context, private val address: String) : TvClient {
    private var hid: BluetoothHidDevice? = null
    private var target: BluetoothDevice? = null
    private val executor = Executors.newSingleThreadExecutor()
    override var onClosed: (() -> Unit)? = null
    override val supportedKeys get() = HidReports.keys

    override suspend fun connect(saved: Credentials, pairing: PairingUi): Credentials {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: throw IOException("This phone has no Bluetooth")
        if (!adapter.isEnabled) throw IOException("Turn Bluetooth on first")
        val device = adapter.getRemoteDevice(address)
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
                    if (changed.address != address) return
                    if (state == BluetoothProfile.STATE_CONNECTED) connected.complete(Unit)
                    if (state == BluetoothProfile.STATE_DISCONNECTED && connected.isCompleted) onClosed?.invoke()
                }
            }
            if (!service.registerApp(sdp, null, null, executor, callback) || !withTimeout(5000) { registered.await() }) {
                throw IOException("Bluetooth HID registration failed on this phone")
            }
            if (service.getConnectionState(device) != BluetoothProfile.STATE_CONNECTED) {
                pairing.showPromptOnTv()
                service.connect(device)
                withTimeout(30_000) { connected.await() }
            }
            return saved
        } catch (e: Exception) {
            disconnect()
            throw IOException(e.message ?: "Bluetooth connection failed", e)
        }
    }

    override suspend fun send(key: RemoteKey): Boolean {
        val report = HidReports.report(key) ?: return false
        val service = hid ?: return false
        val device = target ?: return false
        if (!service.sendReport(device, report.id, report.press)) return false
        delay(30)
        return service.sendReport(device, report.id, report.release)
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
