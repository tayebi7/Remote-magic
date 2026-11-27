package com.remotemagic.app

import com.remotemagic.app.domain.*
import com.remotemagic.app.protocols.*
import com.remotemagic.app.remote.RemoteController
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

private object NoPairing : PairingUi {
    override suspend fun requestCode(): String? = null
    override fun showPromptOnTv() {}
}

class ConnectionTest {
    @Test fun rokuSendsRealHttpCommands() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("<device-info><wifi-mac>aa:bb:cc:dd:ee:ff</wifi-mac></device-info>"))
            server.enqueue(MockResponse())
            server.enqueue(MockResponse().setBody("<apps><app id=\"837\">YouTube</app></apps>"))
            server.enqueue(MockResponse())
            server.enqueue(MockResponse().setBody("<apps/>"))
            server.start()
            val client = RokuClient(server.hostName, server.port)
            assertEquals("aa:bb:cc:dd:ee:ff", client.connect(Credentials(), NoPairing).mac)
            assertTrue(client.send(RemoteKey.HOME))
            assertTrue(client.launch(TvApp.YOUTUBE))
            server.takeRequest()
            assertEquals("/keypress/Home", server.takeRequest().path)
            assertEquals("/query/apps", server.takeRequest().path)
            assertEquals("/launch/837", server.takeRequest().path)
            // An app that is not installed must be reported as a failure.
            assertFalse(client.launchCustom("999"))
        }
    }

    @Test(expected = IOException::class)
    fun nonRokuDeviceIsRejected() {
        runBlocking {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setBody("<html/>"))
                server.start()
                RokuClient(server.hostName, server.port).connect(Credentials(), NoPairing)
            }
        }
    }

    private class FakeClient(private val fail: Boolean) : TvClient {
        override val supportedKeys = setOf(RemoteKey.HOME)
        override var onClosed: (() -> Unit)? = null
        override suspend fun connect(saved: Credentials, pairing: PairingUi): Credentials {
            if (fail) throw IOException("unreachable")
            return saved
        }
        override suspend fun send(key: RemoteKey) = true
        override fun disconnect() {}
    }

    @Test fun controllerHandlesErrorsUnsupportedAndReconnect() = runBlocking {
        var fail = true
        var last: FakeClient? = null
        val controller = RemoteController { FakeClient(fail).also { last = it } }
        val device = DiscoveredDevice("TV", Protocol.ROKU, "10.0.0.2")

        assertEquals(SendResult.NOT_CONNECTED, controller.send(RemoteKey.HOME))
        assertNull(controller.connect(device, Credentials(), NoPairing))
        assertEquals(ConnectionStatus.ERROR, controller.state.value.status)

        fail = false
        assertNotNull(controller.connect(device, Credentials(), NoPairing))
        assertEquals(SendResult.OK, controller.send(RemoteKey.HOME))
        assertEquals(SendResult.UNSUPPORTED, controller.send(RemoteKey.SOURCE))

        last!!.onClosed!!.invoke() // TV dropped the connection
        assertEquals(ConnectionStatus.DISCONNECTED, controller.state.value.status)
        assertTrue(controller.state.value.lost)
        assertNotNull(controller.connect(device, Credentials(), NoPairing))
        assertEquals(ConnectionStatus.CONNECTED, controller.state.value.status)
    }
}
