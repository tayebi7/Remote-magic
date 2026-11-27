package com.remotemagic.app.remote

import android.content.Context
import android.os.Build
import com.remotemagic.app.bluetooth.BluetoothHidClient
import com.remotemagic.app.data.ClientCertificate
import com.remotemagic.app.domain.DiscoveredDevice
import com.remotemagic.app.domain.Protocol
import com.remotemagic.app.protocols.AndroidTvClient
import com.remotemagic.app.protocols.LgClient
import com.remotemagic.app.protocols.RokuClient
import com.remotemagic.app.protocols.SamsungClient
import com.remotemagic.app.protocols.TvClient

class ClientFactory(private val context: Context) {
    fun create(device: DiscoveredDevice): TvClient = when (device.protocol) {
        Protocol.ANDROID_TV -> AndroidTvClient(device.address, ClientCertificate::keyManagers, ClientCertificate::publicKey)
        Protocol.SAMSUNG -> SamsungClient(device.address)
        Protocol.LG -> LgClient(device.address)
        Protocol.ROKU -> RokuClient(device.address)
        Protocol.BLUETOOTH_HID ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) BluetoothHidClient(context, device.address)
            else throw UnsupportedOperationException("Bluetooth remote needs Android 9 or newer")
    }
}
