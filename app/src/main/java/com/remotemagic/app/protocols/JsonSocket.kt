package com.remotemagic.app.protocols

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.concurrent.TimeUnit

fun tvHttpClient(trust: PinningTrustManager? = null): OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .apply {
            if (trust != null) {
                sslSocketFactory(trust.context().socketFactory, trust)
                hostnameVerifier { _, _ -> true } // identity is checked by the certificate pin
            }
        }
        .build()

/** Small coroutine wrapper around an OkHttp WebSocket carrying text messages. */
class JsonSocket(private val client: OkHttpClient) {
    private val incoming = Channel<String>(Channel.UNLIMITED)
    private var socket: WebSocket? = null
    @Volatile var isOpen = false
        private set
    var onClosed: (() -> Unit)? = null

    suspend fun open(url: String, timeoutMs: Long = 7000) {
        val opened = Channel<Throwable?>(1)
        socket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isOpen = true
                opened.trySend(null)
            }
            override fun onMessage(webSocket: WebSocket, text: String) { incoming.trySend(text) }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                closed(IOException("Closed by TV: $reason"))
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = closed(t)
            private fun closed(cause: Throwable) {
                val wasOpen = isOpen
                isOpen = false
                opened.trySend(cause)
                incoming.close(cause)
                if (wasOpen) onClosed?.invoke()
            }
        })
        val error = withTimeout(timeoutMs) { opened.receive() }
        if (error != null) throw IOException(error.message ?: "Connection failed", error)
    }

    suspend fun receive(timeoutMs: Long): String = withTimeout(timeoutMs) { incoming.receive() }

    fun send(text: String): Boolean = isOpen && socket?.send(text) == true

    fun close() {
        onClosed = null
        isOpen = false
        socket?.close(1000, null)
        socket = null
    }
}
