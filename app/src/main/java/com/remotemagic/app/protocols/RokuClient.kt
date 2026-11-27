package com.remotemagic.app.protocols

import com.remotemagic.app.domain.RemoteKey
import com.remotemagic.app.domain.TvApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** Roku External Control Protocol (HTTP, port 8060). */
class RokuClient(private val host: String, private val port: Int = 8060) : TvClient {
    private val http = tvHttpClient()
    private var connected = false
    override var onClosed: (() -> Unit)? = null

    private val keys = mapOf(
        RemoteKey.POWER to "Power", RemoteKey.HOME to "Home", RemoteKey.BACK to "Back",
        RemoteKey.MENU to "Info", RemoteKey.UP to "Up", RemoteKey.DOWN to "Down",
        RemoteKey.LEFT to "Left", RemoteKey.RIGHT to "Right", RemoteKey.OK to "Select",
        RemoteKey.VOLUME_UP to "VolumeUp", RemoteKey.VOLUME_DOWN to "VolumeDown",
        RemoteKey.MUTE to "VolumeMute", RemoteKey.CHANNEL_UP to "ChannelUp",
        RemoteKey.CHANNEL_DOWN to "ChannelDown", RemoteKey.PLAY_PAUSE to "Play",
        RemoteKey.REWIND to "Rev", RemoteKey.FAST_FORWARD to "Fwd"
    ) + (0..9).associate { RemoteKey.digit(it) to "Lit_$it" }

    private val apps = mapOf(
        TvApp.YOUTUBE to "837", TvApp.NETFLIX to "12", TvApp.PRIME to "13", TvApp.DISNEY to "291097"
    )

    override val supportedKeys get() = keys.keys
    override val supportedApps get() = apps.keys
    override val supportsCustomLaunch = true

    override suspend fun connect(saved: Credentials, pairing: PairingUi): Credentials = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("http://$host:$port/query/device-info").build()
        val body = http.newCall(request).execute().use {
            if (!it.isSuccessful) throw IOException("Roku answered HTTP ${it.code}")
            it.body?.string().orEmpty()
        }
        if (!body.contains("<device-info")) throw IOException("Not a Roku device")
        connected = true
        val mac = Regex("<wifi-mac>([^<]+)</wifi-mac>").find(body)?.groupValues?.get(1).orEmpty()
        saved.copy(mac = mac.ifEmpty { saved.mac })
    }

    private suspend fun post(path: String): Boolean = withContext(Dispatchers.IO) {
        if (!connected) return@withContext false
        val request = Request.Builder().url("http://$host:$port/$path").post(ByteArray(0).toRequestBody()).build()
        runCatching { http.newCall(request).execute().use { it.isSuccessful } }.getOrDefault(false)
    }

    override suspend fun send(key: RemoteKey) = keys[key]?.let { post("keypress/$it") } ?: false
    override suspend fun launch(app: TvApp) = apps[app]?.let { post("launch/$it") } ?: false
    override suspend fun launchCustom(target: String) =
        target.all { it.isLetterOrDigit() || it == '_' || it == '.' } && post("launch/$target")

    override fun disconnect() { connected = false }
}
