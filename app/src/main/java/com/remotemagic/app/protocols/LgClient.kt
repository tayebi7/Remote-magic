package com.remotemagic.app.protocols

import com.remotemagic.app.domain.PowerState
import com.remotemagic.app.domain.TextResult
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
    private var pointerSocket: JsonSocket? = null
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
        runCatching { socket.open(path) }.onSuccess { pointerSocket = socket }
    }

    override suspend fun send(key: RemoteKey): Boolean = withContext(Dispatchers.IO) {
        if (key == RemoteKey.POWER) return@withContext request("ssap://system/turnOff") != null
        val button = BUTTONS[key] ?: return@withContext false
        if (pointerSocket?.isOpen != true) openPointer()
        val name = if (key == RemoteKey.PLAY_PAUSE) {
            muted = !muted
            if (muted) "PAUSE" else "PLAY"
        } else button
        pointerSocket?.send("type:button\nname:$name\n\n") == true
    }

    override suspend fun launch(app: TvApp) = APPS[app]?.let { launchCustom(it) } ?: false

    override suspend fun launchCustom(target: String): Boolean = withContext(Dispatchers.IO) {
        request("ssap://system.launcher/launch", JSONObject().put("id", target)) != null
    }

    private suspend fun pointerSend(command: String): Boolean = withContext(Dispatchers.IO) {
        if (pointerSocket?.isOpen != true) openPointer()
        pointerSocket?.send(command) == true
    }

    override val pointer = object : PointerInput {
        override val canScroll = true
        override val canLongPress = false
        override suspend fun move(dx: Int, dy: Int) = pointerSend("type:move\ndx:$dx\ndy:$dy\ndown:0\n\n")
        override suspend fun click() = pointerSend("type:click\n\n")
        override suspend fun scroll(steps: Int) = pointerSend("type:scroll\ndx:0\ndy:$steps\n\n")
    }

    override val textInput = object : TextInput {
        private suspend fun ime(method: String, payload: JSONObject = JSONObject()) = withContext(Dispatchers.IO) {
            request("ssap://com.webos.service.ime/$method", payload) != null
        }
        override suspend fun type(text: String) =
            if (ime("insertText", JSONObject().put("text", text).put("replace", false))) TextResult.CONFIRMED
            else TextResult.NO_FIELD
        override suspend fun backspace() = ime("deleteCharacters", JSONObject().put("count", 1))
        override suspend fun enter() = ime("sendEnterKey")
    }

    override suspend fun powerState(): PowerState? = withContext(Dispatchers.IO) {
        val state = request("ssap://com.webos.service.tvpower/power/getPowerState")?.optString("state").orEmpty()
        when {
            state.isEmpty() -> null
            state == "Active" -> PowerState.ON
            else -> PowerState.STANDBY
        }
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
        pointerSocket?.close()
        main?.close()
        pointerSocket = null
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
            RemoteKey.REWIND to "REWIND", RemoteKey.FAST_FORWARD to "FASTFORWARD",
            RemoteKey.STOP to "STOP", RemoteKey.STAR to "ASTERISK", RemoteKey.ENTER to "ENTER"
        ) + (0..9).associate { RemoteKey.digit(it) to "$it" }

        private val APPS = mapOf(TvApp.YOUTUBE to "youtube.leanback.v4")

        fun registerMessage(clientKey: String): String {
            val permissions = JSONArray(listOf(
                "LAUNCH", "CONTROL_AUDIO", "CONTROL_POWER", "CONTROL_INPUT_TV", "CONTROL_INPUT_MEDIA_PLAYBACK",
                "CONTROL_MOUSE_AND_KEYBOARD", "CONTROL_INPUT_TEXT", "READ_INSTALLED_APPS",
                "CONTROL_TV_SCREEN", "CAPTURE_SCREEN", "READ_TV_CURRENT_CHANNEL", "READ_TV_POWER_STATE"
            ))
            val payload = JSONObject().put("pairingType", "PROMPT")
                .put("manifest", JSONObject().put("manifestVersion", 1).put("permissions", permissions))
            if (clientKey.isNotEmpty()) payload.put("client-key", clientKey)
            return JSONObject().put("type", "register").put("id", "register_0").put("payload", payload).toString()
        }
    }
}
