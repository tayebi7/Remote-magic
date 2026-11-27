package com.remotemagic.app

import com.remotemagic.app.bluetooth.HidReports
import com.remotemagic.app.discovery.DiscoveryService
import com.remotemagic.app.discovery.Ssdp
import com.remotemagic.app.domain.Protocol
import com.remotemagic.app.domain.RemoteKey
import com.remotemagic.app.protocols.*
import com.remotemagic.app.settings.RemoteSection
import com.remotemagic.app.settings.SettingsStore
import com.remotemagic.app.wifi.WakeOnLan
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey

class ProtocolTest {
    @Test fun wakeOnLanPacket() {
        val mac = WakeOnLan.parseMac("AA:bb:CC:dd:EE:01")!!
        val packet = WakeOnLan.magicPacket(mac)
        assertEquals(102, packet.size)
        assertArrayEquals(ByteArray(6) { 0xFF.toByte() }, packet.copyOfRange(0, 6))
        assertArrayEquals(mac, packet.copyOfRange(96, 102))
        assertNull(WakeOnLan.parseMac("00:00:00:00:00:00"))
        assertNull(WakeOnLan.parseMac("xyz"))
    }

    @Test fun ssdpDiscoveryParsing() {
        assertEquals(Protocol.ROKU, Ssdp.protocolOf("HTTP/1.1 200 OK\r\nST: roku:ecp\r\n\r\n"))
        assertEquals(
            Protocol.LG,
            Ssdp.protocolOf("HTTP/1.1 200 OK\r\nst: urn:lge-com:service:webos-second-screen:1\r\n\r\n")
        )
        assertNull(Ssdp.protocolOf("HTTP/1.1 200 OK\r\nST: upnp:rootdevice\r\n\r\n"))
        assertTrue(DiscoveryService.isValidIpv4("192.168.1.20"))
        assertFalse(DiscoveryService.isValidIpv4("192.168.1.300"))
        assertFalse(DiscoveryService.isValidIpv4("tv.local"))
    }

    @Test fun protobufRoundTripAndFraming() {
        val data = ProtoWriter().varint(1, 622).string(2, "abc").toByteArray()
        val out = ByteArrayOutputStream().apply { writeFrame(data) }
        val message = ProtoMessage.parse(ByteArrayInputStream(out.toByteArray()).readFrame())
        assertEquals(622L, message.number(1))
        assertEquals("abc", String(message.blobs[2]!!))
    }

    @Test fun androidTvMessages() {
        // remote_key_inject { key_code: 3, direction: SHORT }
        assertArrayEquals(byteArrayOf(0x52, 4, 8, 3, 16, 3), AndroidTvMessages.keyPress(3))
        val outer = ProtoMessage.parse(AndroidTvMessages.pairingRequest("x"))
        assertEquals(2L, outer.number(1))
        assertEquals(200L, outer.number(2))
        assertTrue(outer.has(10))
    }

    @Test fun androidTvPairingSecretValidatesCode() {
        val generator = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        val client = generator.generateKeyPair().public as RSAPublicKey
        val server = generator.generateKeyPair().public as RSAPublicKey
        fun raw(v: BigInteger) = v.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
        val hash = MessageDigest.getInstance("SHA-256").apply {
            listOf(client.modulus, client.publicExponent, server.modulus, server.publicExponent).forEach { update(raw(it)) }
            update(byteArrayOf(0x12, 0x34))
        }.digest()
        val code = "%02X1234".format(hash[0])
        assertArrayEquals(hash, AndroidTvMessages.pairingSecret(client, server, code))
        val wrong = "%02X1234".format((hash[0] + 1).toByte())
        assertNull(AndroidTvMessages.pairingSecret(client, server, wrong))
        assertNull(AndroidTvMessages.pairingSecret(client, server, "zz"))
    }

    @Test fun commandPayloads() {
        val samsung = JSONObject(SamsungClient.keyMessage("KEY_HOME"))
        assertEquals("ms.remote.control", samsung.getString("method"))
        assertEquals("KEY_HOME", samsung.getJSONObject("params").getString("DataOfCmd"))
        val lg = JSONObject(LgClient.registerMessage("abc"))
        assertEquals("abc", lg.getJSONObject("payload").getString("client-key"))
        assertEquals(7, AndroidTvClient.KEYS[RemoteKey.NUM_0])
        assertEquals(16, AndroidTvClient.KEYS[RemoteKey.NUM_9])
    }

    @Test fun bluetoothReports() {
        val one = HidReports.report(RemoteKey.NUM_1)!!
        assertEquals(HidReports.KEYBOARD_ID, one.id)
        assertEquals(0x1E.toByte(), one.press[2])
        val home = HidReports.report(RemoteKey.HOME)!!
        assertArrayEquals(byteArrayOf(0x23, 0x02), home.press)
        assertNull(HidReports.report(RemoteKey.SOURCE))
    }

    @Test fun sectionOrderSurvivesBadData() {
        val order = SettingsStore.parseOrder("MEDIA,bogus,APPS")
        assertEquals(listOf(RemoteSection.MEDIA, RemoteSection.APPS), order.take(2))
        assertEquals(RemoteSection.entries.size, order.size)
    }
}
