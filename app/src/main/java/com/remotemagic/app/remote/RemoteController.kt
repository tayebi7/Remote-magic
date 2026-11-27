package com.remotemagic.app.remote

import com.remotemagic.app.domain.ConnectionState
import com.remotemagic.app.domain.ConnectionStatus
import com.remotemagic.app.domain.DiscoveredDevice
import com.remotemagic.app.domain.RemoteKey
import com.remotemagic.app.domain.SendResult
import com.remotemagic.app.domain.TvApp
import com.remotemagic.app.protocols.Credentials
import com.remotemagic.app.protocols.PairingUi
import com.remotemagic.app.protocols.TvClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Owns the single active TV connection and routes commands to it. */
class RemoteController(private val factory: (DiscoveredDevice) -> TvClient) {
    private val lock = Mutex()
    private val mutable = MutableStateFlow(ConnectionState())
    val state = mutable.asStateFlow()

    @Volatile var client: TvClient? = null
        private set

    /** Returns the credentials on success, null on failure (see [state] for the reason). */
    suspend fun connect(device: DiscoveredDevice, saved: Credentials, pairing: PairingUi): Credentials? = lock.withLock {
        release()
        mutable.value = ConnectionState(ConnectionStatus.CONNECTING, device)
        var next: TvClient? = null
        try {
            val created = factory(device).also { next = it }
            val credentials = created.connect(saved, pairing)
            client = created
            created.onClosed = {
                if (client === created) {
                    client = null
                    mutable.value = ConnectionState(ConnectionStatus.DISCONNECTED, device)
                }
            }
            mutable.value = ConnectionState(ConnectionStatus.CONNECTED, device)
            credentials
        } catch (e: CancellationException) {
            next?.disconnect()
            mutable.value = ConnectionState(ConnectionStatus.DISCONNECTED, device)
            // A timeout is a failed connection; a real cancellation must propagate.
            if (e is kotlinx.coroutines.TimeoutCancellationException) {
                mutable.value = ConnectionState(ConnectionStatus.ERROR, device, "Timed out")
                null
            } else throw e
        } catch (e: Exception) {
            next?.disconnect()
            mutable.value = ConnectionState(ConnectionStatus.ERROR, device, e.message ?: e.javaClass.simpleName)
            null
        }
    }

    suspend fun send(key: RemoteKey): SendResult = dispatch(key in (client?.supportedKeys ?: emptySet())) { it.send(key) }
    suspend fun launch(app: TvApp): SendResult = dispatch(app in (client?.supportedApps ?: emptySet())) { it.launch(app) }
    suspend fun launchCustom(target: String): SendResult =
        dispatch(client?.supportsCustomLaunch == true) { it.launchCustom(target) }

    private suspend fun dispatch(supported: Boolean, action: suspend (TvClient) -> Boolean): SendResult {
        val active = client ?: return SendResult.NOT_CONNECTED
        if (!supported) return SendResult.UNSUPPORTED
        return if (action(active)) SendResult.OK else SendResult.FAILED
    }

    fun disconnect() {
        release()
        mutable.value = ConnectionState(ConnectionStatus.DISCONNECTED, mutable.value.device)
    }

    private fun release() {
        val old = client
        client = null
        old?.onClosed = null
        old?.disconnect()
    }
}
