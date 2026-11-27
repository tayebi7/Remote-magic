package com.remotemagic.app.protocols

import com.remotemagic.app.domain.PowerState
import com.remotemagic.app.domain.TextResult
import java.net.URLEncoder
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
        RemoteKey.REWIND to "Rev", RemoteKey.FAST_FORWARD to "Fwd", RemoteKey.ENTER to "Enter"
    ) + (0..9).associate { RemoteKey.digit(it) to "Lit_$it" }

    private val apps = mapOf(TvApp.YOUTUBE to "837")

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
    override suspend fun launch(app: TvApp) = apps[app]?.let { launchCustom(it) } ?: false
    override suspend fun launchCustom(target: String): Boolean {
        if (target.isEmpty() || !target.all { it.isLetterOrDigit() || it == '_' || it == '.' }) return false
        val installed = get("query/apps") ?: return false
        return installed.contains("id=\"$target\"") && post("launch/$target")
    }

    private suspend fun get(path: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url("http://$host:$port/$path").build()).execute()
                .use { if (it.isSuccessful) it.body?.string() else null }
        }.getOrNull()
    }

    override suspend fun powerState(): PowerState? {
        val mode = get("query/device-info")?.let { Regex("<power-mode>([^<]+)<").find(it)?.groupValues?.get(1) }
        return when (mode) { null -> null; "PowerOn" -> PowerState.ON; else -> PowerState.STANDBY }
    }

    override val textInput = object : TextInput {
        override suspend fun type(text: String): TextResult {
            var i = 0
            while (i < text.length) {
                val point = text.codePointAt(i)
                val encoded = URLEncoder.encode(String(Character.toChars(point)), "UTF-8").replace("+", "%20")
                if (!post("keypress/Lit_$encoded")) return TextResult.FAILED
                i += Character.charCount(point)
            }
            return TextResult.SENT
        }
        override suspend fun backspace() = post("keypress/Backspace")
        override suspend fun enter() = post("keypress/Enter")
    }

    override fun disconnect() { connected = false }
}
