package com.remotemagic.app.protocols

import com.remotemagic.app.domain.RemoteKey
import com.remotemagic.app.domain.TvApp

/** Interaction needed from the user while pairing. */
interface PairingUi {
    /** Ask for the code shown on the TV. Null means the user cancelled. */
    suspend fun requestCode(): String?
    /** Tell the user to accept the request on the TV screen. */
    fun showPromptOnTv()
}

/** Secrets learned while pairing; persisted (encrypted) for reconnection. */
data class Credentials(val token: String = "", val certPin: String = "", val mac: String = "")

interface TvClient {
    val supportedKeys: Set<RemoteKey>
    val supportedApps: Set<TvApp> get() = emptySet()
    /** Whether a free-form app id / deep link can be launched (custom shortcuts). */
    val supportsCustomLaunch: Boolean get() = false
    val supportsScreenshot: Boolean get() = false
    var onClosed: (() -> Unit)?

    /** Throws on failure. Returns the credentials to remember. */
    suspend fun connect(saved: Credentials, pairing: PairingUi): Credentials
    suspend fun send(key: RemoteKey): Boolean
    suspend fun launch(app: TvApp): Boolean = false
    suspend fun launchCustom(target: String): Boolean = false
    suspend fun screenshot(): ByteArray? = null
    /** Protocol-specific power on (in addition to Wake-on-LAN). */
    fun disconnect()
}
