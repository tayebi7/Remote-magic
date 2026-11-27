package com.remotemagic.app

import android.app.Application
import com.remotemagic.app.bluetooth.BluetoothScanner
import com.remotemagic.app.data.AppDatabase
import com.remotemagic.app.discovery.DiscoveryService
import com.remotemagic.app.remote.ClientFactory
import com.remotemagic.app.remote.RemoteController
import com.remotemagic.app.settings.SettingsStore
import com.remotemagic.app.voice.VoiceRecognizer

/** Simple manual dependency container. */
class RemoteMagicApp : Application() {
    val database by lazy { AppDatabase.create(this) }
    val settings by lazy { SettingsStore(this) }
    val discovery by lazy { DiscoveryService(this) }
    val bluetooth by lazy { BluetoothScanner(this) }
    val voice by lazy { VoiceRecognizer(this) }
    val controller by lazy { RemoteController(ClientFactory(this)::create) }
}
