package com.remotemagic.app.wifi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object WakeOnLan {
    fun parseMac(mac: String): ByteArray? {
        val hex = mac.filter { it != ':' && it != '-' && !it.isWhitespace() }
        if (hex.length != 12 || hex.any { Character.digit(it, 16) < 0 }) return null
        val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return bytes.takeIf { b -> b.any { it != 0.toByte() } }
    }

    fun magicPacket(mac: ByteArray): ByteArray {
        require(mac.size == 6)
        val packet = ByteArray(102) { 0xFF.toByte() }
        for (i in 0 until 16) mac.copyInto(packet, 6 + i * 6)
        return packet
    }

    /** Sends the magic packet. Success only means it was sent, not that the TV woke up. */
    suspend fun wake(mac: String, host: String): Boolean = withContext(Dispatchers.IO) {
        val bytes = parseMac(mac) ?: return@withContext false
        val packet = magicPacket(bytes)
        val subnet = host.substringBeforeLast('.', "").takeIf { it.isNotEmpty() }?.let { "$it.255" }
        runCatching {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                repeat(3) {
                    listOfNotNull("255.255.255.255", subnet).forEach { target ->
                        for (port in intArrayOf(9, 7)) {
                            socket.send(DatagramPacket(packet, packet.size, InetAddress.getByName(target), port))
                        }
                    }
                    delay(120)
                }
            }
        }.isSuccess
    }
}
