package com.remotemagic.app.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.remotemagic.app.domain.DiscoveredDevice
import com.remotemagic.app.domain.Protocol
import com.remotemagic.app.protocols.tvHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentLinkedQueue

/** Finds TVs with SSDP (Roku, Samsung, LG) and mDNS (Android TV). */
class DiscoveryService(private val context: Context) {

    fun discover(durationMs: Long = 8000): Flow<DiscoveredDevice> = channelFlow {
        val lock = context.applicationContext.getSystemService(WifiManager::class.java)
            ?.createMulticastLock("remotemagic")
        runCatching { lock?.acquire() }
        try {
            launch(Dispatchers.IO) { ssdp(durationMs) }
            launch { mdns(durationMs) }.join()
        } finally {
            runCatching { lock?.release() }
        }
    }

    private suspend fun ProducerScope<DiscoveredDevice>.ssdp(durationMs: Long) {
        runCatching {
            DatagramSocket().use { socket ->
                socket.soTimeout = 500
                val group = InetAddress.getByName("239.255.255.250")
                Ssdp.TARGETS.forEach {
                    val data = Ssdp.searchRequest(it).toByteArray()
                    socket.send(DatagramPacket(data, data.size, group, 1900))
                }
                val seen = HashSet<String>()
                val end = System.currentTimeMillis() + durationMs
                val buffer = ByteArray(2048)
                while (isActive && System.currentTimeMillis() < end) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    val protocol = Ssdp.protocolOf(String(packet.data, 0, packet.length)) ?: continue
                    val host = packet.address.hostAddress ?: continue
                    if (seen.add("$protocol$host")) {
                        launch { send(describe(host, protocol)) }
                    }
                }
            }
        }
    }

    private suspend fun ProducerScope<DiscoveredDevice>.mdns(durationMs: Long) {
        val nsd = context.getSystemService(NsdManager::class.java) ?: return
        val found = ConcurrentLinkedQueue<NsdServiceInfo>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) { found.add(serviceInfo) }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
        }
        runCatching { nsd.discoverServices("_androidtvremote2._tcp", NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { return }
        try {
            val end = System.currentTimeMillis() + durationMs
            var resolving = false
            while (System.currentTimeMillis() < end) {
                // NsdManager can only resolve one service at a time.
                val next = if (resolving) null else found.poll()
                if (next != null) {
                    resolving = true
                    @Suppress("DEPRECATION")
                    nsd.resolveService(next, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) { resolving = false }
                        override fun onServiceResolved(info: NsdServiceInfo) {
                            info.host?.hostAddress?.let {
                                trySend(DiscoveredDevice(info.serviceName, Protocol.ANDROID_TV, it))
                            }
                            resolving = false
                        }
                    })
                }
                delay(200)
            }
        } finally {
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
    }

    /** Reads the real name and maker from the TV when it publishes them. */
    private suspend fun describe(host: String, protocol: Protocol): DiscoveredDevice = withContext(Dispatchers.IO) {
        fun get(url: String) = runCatching {
            tvHttpClient().newCall(Request.Builder().url(url).build()).execute().use { it.body!!.string() }
        }.getOrDefault("")
        fun tag(xml: String, name: String) = Regex("<$name>([^<]*)</$name>").find(xml)?.groupValues?.get(1).orEmpty()

        when (protocol) {
            Protocol.ROKU -> {
                val xml = get("http://$host:8060/query/device-info")
                DiscoveredDevice(
                    tag(xml, "friendly-device-name").ifEmpty { "Roku" }, protocol, host,
                    tag(xml, "vendor-name").ifEmpty { "Roku" }, tag(xml, "wifi-mac")
                )
            }
            Protocol.SAMSUNG -> {
                val device = runCatching { JSONObject(get("http://$host:8001/api/v2/")).getJSONObject("device") }.getOrNull()
                DiscoveredDevice(
                    device?.optString("name").orEmpty().ifEmpty { "Samsung TV" }, protocol, host,
                    "Samsung", device?.optString("wifiMac").orEmpty()
                )
            }
            Protocol.LG -> DiscoveredDevice("LG webOS TV", protocol, host, "LG")
            else -> DiscoveredDevice(protocol.label, protocol, host)
        }
    }

    /** Manual IP entry: find which supported services actually answer at that address. */
    suspend fun probe(host: String): List<DiscoveredDevice> = coroutineScope {
        val ports = listOf(
            8060 to Protocol.ROKU, 8002 to Protocol.SAMSUNG, 3001 to Protocol.LG,
            3000 to Protocol.LG, 6466 to Protocol.ANDROID_TV
        )
        val open = ports.map { (port, protocol) ->
            async(Dispatchers.IO) {
                protocol.takeIf { runCatching { Socket().use { it.connect(InetSocketAddress(host, port), 1500) } }.isSuccess }
            }
        }.awaitAll().filterNotNull().distinct()
        open.map { describe(host, it) }
    }

    companion object {
        fun isValidIpv4(value: String): Boolean {
            val parts = value.split('.')
            return parts.size == 4 && parts.all { p ->
                p.length in 1..3 && p.all { it.isDigit() } && p.toInt() <= 255
            }
        }
    }
}
