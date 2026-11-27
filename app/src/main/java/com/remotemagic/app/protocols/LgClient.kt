package com.remotemagic.app.protocols

import com.remotemagic.app.domain.RemoteKey
import com.remotemagic.app.domain.TvApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** LG webOS SSAP protocol (wss://TV:3001, falling back to ws://TV:3000). */
class LgClient(private val host: String) : TvClient {
    private var main: JsonSocket? = null
    private var pointer: JsonSocket? = null
    private var trust = PinningTrustManager("")
    private val lock = Mutex()
    private var nextId = 0
    private var muted = false
    override var onClosed: (() -> Unit)? = null

    override val supportedKeys get() = BUTTONS.keys + RemoteKey.POWER
    override val supportedApps get() = APPS.keys
    override val supportsCustomLaunch = true
    override val supportsScreenshot = true

    override suspend fun connect(saved: Credentials, pairing: PairingUi): Credentials = withContext(Dispatchers.IO) {
        trust = PinningTrustManager(saved.certPin)
        var ws = JsonSocket(tvHttpClient(trust))
        try {
            ws.open("wss://$host:3001")
        } catch (e: Exception) {
            // Never downgrade to plain text once this TV has been seen over TLS.
            if (saved.certPin.isNotEmpty()) throw e
            ws = JsonSocket(tvHttpClient())
            ws.open("ws://$host:3000")
        }
        try {
            ws.send(registerMessage(saved.token))
            var clientKey = saved.token
            while (true) {
                val message = JSONObject(ws.receive(60_000))
                val payload = message.optJSONObject("payload")
                if (message.optString("type") == "registered") {
                    clientKey = payload?.optString("client-key").orEmpty().ifEmpty { clientKey }
                    break
                }
                if (message.optString("type") == "error") throw IOException(message.optString("error", "Pairing refused"))
                if (payload?.optString("pairingType") == "PROMPT") pairing.showPromptOnTv()
            }
            main = ws
            ws.onClosed = { onClosed?.invoke() }
            openPointer()
            Credentials(clientKey, trust.seenPin, saved.mac)
        } catch (e: Exception) {
            ws.close()
            throw e
        }
    }

    private suspend fun request(uri: String, payload: JSONObject = JSONObject()): JSONObject? = lock.withLock {
        val ws = main ?: return@withLock null
        val id = "rm_${nextId++}"
        val message = JSONObject().put("type", "request").put("id", id).put("uri", uri).put("payload", payload)
        if (!ws.send(message.toString())) return@withLock null
        runCatching {
            var reply: JSONObject
            do { reply = JSONObject(ws.receive(6000)) } while (reply.optString("id") != id)
            reply.optJSONObject("payload")?.takeIf { reply.optString("type") != "error" && it.optBoolean("returnValue", true) }
        }.getOrNull()
    }

    private suspend fun openPointer() {
        val path = request("ssap://com.webos.service.networkinput/getPointerInputSocket")
            ?.optString("socketPath").orEmpty()
        if (path.isEmpty()) return
        val socket = JsonSocket(tvHttpClient(trust))
        runCatching { socket.open(path) }.onSuccess { pointer = socket }
    }

    override suspend fun send(key: RemoteKey): Boolean = withContext(Dispatchers.IO) {
        if (key == RemoteKey.POWER) return@withContext request("ssap://system/turnOff") != null
        val button = BUTTONS[key] ?: return@withContext false
        if (pointer?.isOpen != true) openPointer()
        val name = if (key == RemoteKey.PLAY_PAUSE) {
            muted = !muted
            if (muted) "PAUSE" else "PLAY"
        } else button
        pointer?.send("type:button\nname:$name\n\n") == true
    }

    override suspend fun launch(app: TvApp) = APPS[app]?.let { launchCustom(it) } ?: false

    override suspend fun launchCustom(target: String): Boolean = withContext(Dispatchers.IO) {
        request("ssap://system.launcher/launch", JSONObject().put("id", target)) != null
    }

    /** Real capture of the TV picture. Returns null when the TV or the content forbids it. */
    override suspend fun screenshot(): ByteArray? = withContext(Dispatchers.IO) {
        val url = request("ssap://tv/executeOneShot")?.optString("imageUri").orEmpty()
        if (url.isEmpty()) return@withContext null
        runCatching {
            tvHttpClient(trust).newCall(Request.Builder().url(url).build()).execute().use {
                if (it.isSuccessful) it.body?.bytes() else null
            }
        }.getOrNull()
    }

    override fun disconnect() {
        pointer?.close()
        main?.close()
        pointer = null
        main = null
    }

    companion object {
        val BUTTONS: Map<RemoteKey, String> = mapOf(
            RemoteKey.HOME to "HOME", RemoteKey.BACK to "BACK", RemoteKey.MENU to "MENU",
            RemoteKey.SETTINGS to "QMENU", RemoteKey.EXIT to "EXIT", RemoteKey.UP to "UP",
            RemoteKey.DOWN to "DOWN", RemoteKey.LEFT to "LEFT", RemoteKey.RIGHT to "RIGHT",
            RemoteKey.OK to "ENTER", RemoteKey.VOLUME_UP to "VOLUMEUP", RemoteKey.VOLUME_DOWN to "VOLUMEDOWN",
            RemoteKey.MUTE to "MUTE", RemoteKey.CHANNEL_UP to "CHANNELUP",
            RemoteKey.CHANNEL_DOWN to "CHANNELDOWN", RemoteKey.PLAY_PAUSE to "PLAY",
            RemoteKey.REWIND to "REWIND", RemoteKey.FAST_FORWARD to "FASTFORWARD"
        ) + (0..9).associate { RemoteKey.digit(it) to "$it" }

        private val APPS = mapOf(
            TvApp.YOUTUBE to "youtube.leanback.v4", TvApp.NETFLIX to "netflix",
            TvApp.PRIME to "amazon", TvApp.DISNEY to "com.disney.disneyplus-prod",
            TvApp.BROWSER to "com.webos.app.browser"
        )

        fun registerMessage(clientKey: String): String {
            val permissions = JSONArray(listOf(
                "LAUNCH", "CONTROL_AUDIO", "CONTROL_POWER", "CONTROL_INPUT_TV", "CONTROL_INPUT_MEDIA_PLAYBACK",
                "CONTROL_MOUSE_AND_KEYBOARD", "CONTROL_INPUT_TEXT", "READ_INSTALLED_APPS",
                "CONTROL_TV_SCREEN", "CAPTURE_SCREEN", "READ_TV_CURRENT_CHANNEL"
            ))
            val payload = JSONObject().put("pairingType", "PROMPT")
                .put("manifest", JSONObject().put("manifestVersion", 1).put("permissions", permissions))
            if (clientKey.isNotEmpty()) payload.put("client-key", clientKey)
            return JSONObject().put("type", "register").put("id", "register_0").put("payload", payload).toString()
        }
    }
}
