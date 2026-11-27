package com.remotemagic.app.protocols

import com.remotemagic.app.domain.RemoteKey
import com.remotemagic.app.domain.TvApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLSocket

/**
 * Android TV / Google TV: pairing on port 6467, control on port 6466, both mutual TLS.
 * The client private key lives in the Android Keystore.
 */
class AndroidTvClient(
    private val host: String,
    private val keyManagers: () -> Array<KeyManager>,
    private val clientKey: () -> RSAPublicKey
) : TvClient {
    private var socket: SSLSocket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var reader: Job? = null
    override var onClosed: (() -> Unit)? = null

    override val supportedKeys get() = KEYS.keys
    override val supportedApps get() = APPS.keys
    override val supportsCustomLaunch = true

    private fun open(port: Int, trust: PinningTrustManager): SSLSocket {
        val raw = Socket()
        raw.connect(InetSocketAddress(host, port), 5000)
        val tls = trust.context(keyManagers()).socketFactory.createSocket(raw, host, port, true) as SSLSocket
        tls.soTimeout = 15_000
        tls.startHandshake()
        return tls
    }

    override suspend fun connect(saved: Credentials, pairing: PairingUi): Credentials = withContext(Dispatchers.IO) {
        var pin = saved.certPin
        if (saved.token != PAIRED) pin = pair(pairing)
        val trust = PinningTrustManager(pin)
        val tls = open(6466, trust)
        try {
            handshake(tls)
        } catch (e: Exception) {
            tls.close()
            throw e
        }
        socket = tls
        reader = scope.launch { readLoop(tls) }
        Credentials(PAIRED, trust.seenPin, saved.mac)
    }

    private suspend fun pair(ui: PairingUi): String {
        val trust = PinningTrustManager("")
        open(6467, trust).use { tls ->
            fun exchange(message: ByteArray): ProtoMessage {
                tls.outputStream.writeFrame(message)
                val reply = ProtoMessage.parse(tls.inputStream.readFrame())
                if (reply.number(2) != 200L) throw IOException("Pairing rejected (status ${reply.number(2)})")
                return reply
            }
            exchange(AndroidTvMessages.pairingRequest("REMOTE MAGIC"))
            exchange(AndroidTvMessages.options())
            exchange(AndroidTvMessages.configuration())
            val code = ui.requestCode() ?: throw IOException("Pairing cancelled")
            val serverKey = tls.session.peerCertificates.first().publicKey as RSAPublicKey
            val secret = AndroidTvMessages.pairingSecret(clientKey(), serverKey, code)
                ?: throw IOException("Wrong pairing code")
            exchange(AndroidTvMessages.secret(secret))
        }
        return trust.seenPin
    }

    /** Waits for the TV to start the session, answering configuration messages. */
    private fun handshake(tls: SSLSocket) {
        while (true) {
            val message = ProtoMessage.parse(tls.inputStream.readFrame())
            reply(tls, message)
            if (message.has(40)) return // remote_start
        }
    }

    private fun reply(tls: SSLSocket, message: ProtoMessage) {
        val answer = when {
            message.has(1) -> AndroidTvMessages.configure()
            message.has(2) -> AndroidTvMessages.setActive()
            message.has(8) -> AndroidTvMessages.pingResponse(message.message(8)?.number(1) ?: 0)
            else -> return
        }
        synchronized(tls) { tls.outputStream.writeFrame(answer) }
    }

    private fun readLoop(tls: SSLSocket) {
        try {
            tls.soTimeout = 20_000 // the TV pings every few seconds
            while (!tls.isClosed) reply(tls, ProtoMessage.parse(tls.inputStream.readFrame()))
        } catch (_: Exception) {
            if (socket === tls) {
                socket = null
                runCatching { tls.close() }
                onClosed?.invoke()
            }
        }
    }

    private suspend fun write(data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val tls = socket ?: return@withContext false
        runCatching { synchronized(tls) { tls.outputStream.writeFrame(data) } }.isSuccess
    }

    override suspend fun send(key: RemoteKey) = KEYS[key]?.let { write(AndroidTvMessages.keyPress(it)) } ?: false
    override suspend fun launch(app: TvApp) = APPS[app]?.let { launchCustom(it) } ?: false
    override suspend fun launchCustom(target: String) = write(AndroidTvMessages.appLink(target))

    override fun disconnect() {
        val tls = socket
        socket = null
        runCatching { tls?.close() }
        reader?.cancel()
        scope.cancel()
    }

    companion object {
        private const val PAIRED = "paired"

        /** Android KeyEvent key codes. */
        val KEYS: Map<RemoteKey, Int> = mapOf(
            RemoteKey.POWER to 26, RemoteKey.HOME to 3, RemoteKey.BACK to 4, RemoteKey.MENU to 82,
            RemoteKey.SETTINGS to 176, RemoteKey.SOURCE to 178, RemoteKey.UP to 19, RemoteKey.DOWN to 20,
            RemoteKey.LEFT to 21, RemoteKey.RIGHT to 22, RemoteKey.OK to 23, RemoteKey.VOLUME_UP to 24,
            RemoteKey.VOLUME_DOWN to 25, RemoteKey.MUTE to 164, RemoteKey.CHANNEL_UP to 166,
            RemoteKey.CHANNEL_DOWN to 167, RemoteKey.PLAY_PAUSE to 85, RemoteKey.REWIND to 89,
            RemoteKey.FAST_FORWARD to 90
        ) + (0..9).associate { RemoteKey.digit(it) to 7 + it }

        private val APPS = mapOf(
            TvApp.YOUTUBE to "https://www.youtube.com", TvApp.NETFLIX to "https://www.netflix.com/title",
            TvApp.PRIME to "https://app.primevideo.com", TvApp.DISNEY to "https://www.disneyplus.com"
        )
    }
}
