package com.remotemagic.app.bluetooth

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.remotemagic.app.domain.DiscoveredDevice
import com.remotemagic.app.domain.Protocol
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Lists bonded devices, then devices found by a classic Bluetooth inquiry. */
@SuppressLint("MissingPermission")
class BluetoothScanner(private val context: Context) {
    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    val isEnabled: Boolean get() = adapter?.isEnabled == true

    private fun BluetoothDevice.toTv() = DiscoveredDevice(
        name = name ?: address, protocol = Protocol.BLUETOOTH_HID, address = address,
        // The HID remote profile runs on Bluetooth Classic; BLE-only devices are listed as unsupported.
        supported = type != BluetoothDevice.DEVICE_TYPE_LE,
        paired = bondState == BluetoothDevice.BOND_BONDED
    )

    fun scan(): Flow<DiscoveredDevice> = callbackFlow {
        val bluetooth = adapter
        if (bluetooth == null || !bluetooth.isEnabled) {
            close()
            return@callbackFlow
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == BluetoothAdapter_FINISHED) {
                    channel.close()
                    return
                }
                @Suppress("DEPRECATION")
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                if (device?.name != null) trySend(device.toTv())
            }
        }
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND).apply { addAction(BluetoothAdapter_FINISHED) }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        bluetooth.bondedDevices.orEmpty().forEach { trySend(it.toTv()) }
        bluetooth.cancelDiscovery()
        if (!bluetooth.startDiscovery()) channel.close()
        val le = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (result.device.name != null) trySend(result.device.toTv())
            }
        }
        runCatching { bluetooth.bluetoothLeScanner?.startScan(le) }
        awaitClose {
            runCatching { bluetooth.bluetoothLeScanner?.stopScan(le) }
            bluetooth.cancelDiscovery()
            context.unregisterReceiver(receiver)
        }
    }

    private companion object {
        const val BluetoothAdapter_FINISHED = android.bluetooth.BluetoothAdapter.ACTION_DISCOVERY_FINISHED
    }
}
