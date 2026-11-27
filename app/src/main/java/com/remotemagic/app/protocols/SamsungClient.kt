package com.remotemagic.app.protocols

import com.remotemagic.app.domain.RemoteKey
import com.remotemagic.app.domain.TvApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.Base64

/** Samsung Tizen remote channel (2016+ models): wss://TV:8002 with a per-client token. */
class SamsungClient(private val host: String, private val appName: String = "REMOTE MAGIC") : TvClient {
    private var socket: JsonSocket? = null
    override var onClosed: (() -> Unit)? = null

    override val supportedKeys get() = KEYS.keys
    override val supportedApps get() = APPS.keys
    override val supportsCustomLaunch = true

    override suspend fun connect(saved: Credentials, pairing: PairingUi): Credentials = withContext(Dispatchers.IO) {
        val trust = PinningTrustManager(saved.certPin)
        val ws = JsonSocket(tvHttpClient(trust))
        val name = Base64.getEncoder().encodeToString(appName.toByteArray())
        var url = "wss://$host:8002/api/v2/channels/samsung.remote.control?name=$name"
        if (saved.token.isNotEmpty()) url += "&token=${saved.token}"
        try {
            ws.open(url)
            if (saved.token.isEmpty()) pairing.showPromptOnTv()
            var token = saved.token
            while (true) {
                val message = JSONObject(ws.receive(45_000))
                when (message.optString("event")) {
                    "ms.channel.connect" -> {
                        token = message.optJSONObject("data")?.optString("token").orEmpty().ifEmpty { token }
                        break
                    }
                    "ms.channel.unauthorized", "ms.channel.timeOut" ->
                        throw IOException("The TV refused the connection. Allow this phone on the TV.")
                }
            }
            ws.onClosed = { onClosed?.invoke() }
            socket = ws
            Credentials(token, trust.seenPin, readMac().ifEmpty { saved.mac })
        } catch (e: Exception) {
            ws.close()
            throw e
        }
    }

    private fun readMac(): String = runCatching {
        tvHttpClient().newCall(Request.Builder().url("http://$host:8001/api/v2/").build()).execute().use {
            JSONObject(it.body!!.string()).getJSONObject("device").optString("wifiMac")
        }
    }.getOrDefault("")

    override suspend fun send(key: RemoteKey): Boolean {
        val code = KEYS[key] ?: return false
        return socket?.send(keyMessage(code)) == true
    }

    override suspend fun launch(app: TvApp) = APPS[app]?.let { launchCustom(it) } ?: false

    /** Uses the TV's REST API, which reports whether the app really exists. */
    override suspend fun launchCustom(target: String): Boolean = withContext(Dispatchers.IO) {
        if (socket?.isOpen != true || !target.all { it.isLetterOrDigit() || it == '.' || it == '_' }) return@withContext false
        val request = Request.Builder().url("http://$host:8001/api/v2/applications/$target")
            .post(ByteArray(0).toRequestBody()).build()
        runCatching { tvHttpClient().newCall(request).execute().use { it.isSuccessful } }.getOrDefault(false)
    }

    override fun disconnect() {
        socket?.close()
        socket = null
    }

    companion object {
        val KEYS: Map<RemoteKey, String> = mapOf(
            RemoteKey.POWER to "KEY_POWER", RemoteKey.HOME to "KEY_HOME", RemoteKey.BACK to "KEY_RETURN",
            RemoteKey.MENU to "KEY_MENU", RemoteKey.SETTINGS to "KEY_TOOLS", RemoteKey.SOURCE to "KEY_SOURCE",
            RemoteKey.EXIT to "KEY_EXIT", RemoteKey.UP to "KEY_UP", RemoteKey.DOWN to "KEY_DOWN",
            RemoteKey.LEFT to "KEY_LEFT", RemoteKey.RIGHT to "KEY_RIGHT", RemoteKey.OK to "KEY_ENTER",
            RemoteKey.VOLUME_UP to "KEY_VOLUP", RemoteKey.VOLUME_DOWN to "KEY_VOLDOWN",
            RemoteKey.MUTE to "KEY_MUTE", RemoteKey.CHANNEL_UP to "KEY_CHUP",
            RemoteKey.CHANNEL_DOWN to "KEY_CHDOWN", RemoteKey.PLAY_PAUSE to "KEY_PLAY_BACK",
            RemoteKey.REWIND to "KEY_REWIND", RemoteKey.FAST_FORWARD to "KEY_FF"
        ) + (0..9).associate { RemoteKey.digit(it) to "KEY_$it" }

        private val APPS = mapOf(
            TvApp.YOUTUBE to "111299001912", TvApp.NETFLIX to "3201907018807",
            TvApp.PRIME to "3201910019365", TvApp.DISNEY to "3201901017640",
            TvApp.BROWSER to "org.tizen.browser"
        )

        fun keyMessage(code: String): String = JSONObject()
            .put("method", "ms.remote.control")
            .put("params", JSONObject()
                .put("Cmd", "Click").put("DataOfCmd", code)
                .put("Option", "false").put("TypeOfRemote", "SendRemoteKey"))
            .toString()
    }
}
