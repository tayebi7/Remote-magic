package com.remotemagic.app.protocols

import com.remotemagic.app.domain.DiscoveredDevice

import com.remotemagic.app.domain.PowerState
import com.remotemagic.app.domain.RemoteKey
import com.remotemagic.app.domain.TextResult
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

/** Real pointer on the TV screen. */
interface PointerInput {
    val canScroll: Boolean
    val canLongPress: Boolean
    suspend fun move(dx: Int, dy: Int): Boolean
    suspend fun click(): Boolean
    suspend fun longClick(): Boolean = false
    /** Positive scrolls up. */
    suspend fun scroll(steps: Int): Boolean = false
}

/** Text entry into the field focused on the TV. */
interface TextInput {
    suspend fun type(text: String): TextResult
    suspend fun backspace(): Boolean
    suspend fun enter(): Boolean
}

interface TvClient {
    val pointer: PointerInput? get() = null
    val textInput: TextInput? get() = null
    /** Null when the protocol cannot tell. */
    suspend fun powerState(): PowerState? = null
    val supportedKeys: Set<RemoteKey>
    val supportedApps: Set<TvApp> get() = emptySet()
    /** Whether a free-form app id / deep link can be launched (custom shortcuts). */
    val supportsCustomLaunch: Boolean get() = false
    val supportsScreenshot: Boolean get() = false
    var onClosed: (() -> Unit)?
    /** The device actually connected, when it was unknown before connecting (a TV that found the phone). */
    val resolved: DiscoveredDevice? get() = null

    /** Throws on failure. Returns the credentials to remember. */
    suspend fun connect(saved: Credentials, pairing: PairingUi): Credentials
    suspend fun send(key: RemoteKey): Boolean
    suspend fun launch(app: TvApp): Boolean = false
    suspend fun launchCustom(target: String): Boolean = false
    suspend fun screenshot(): ByteArray? = null
    /** Protocol-specific power on (in addition to Wake-on-LAN). */
    fun disconnect()
}
