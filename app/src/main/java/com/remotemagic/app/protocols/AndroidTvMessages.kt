package com.remotemagic.app.protocols

import java.math.BigInteger
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey

/** Messages of the Android TV Remote protocol v2 (the one used by the Google TV app). */
object AndroidTvMessages {
    private const val STATUS_OK = 200L
    private const val HEXADECIMAL = 3L
    private const val ROLE_INPUT = 1L
    const val FEATURES = 622L

    private fun outer(field: Int, body: ProtoWriter) =
        ProtoWriter().varint(1, 2).varint(2, STATUS_OK).message(field, body).toByteArray()

    private fun encoding() = ProtoWriter().varint(1, HEXADECIMAL).varint(2, 6)

    fun pairingRequest(client: String) = outer(10, ProtoWriter().string(1, "atvremote").string(2, client))
    fun options() = outer(20, ProtoWriter().message(1, encoding()).varint(3, ROLE_INPUT))
    fun configuration() = outer(30, ProtoWriter().message(1, encoding()).varint(2, ROLE_INPUT))
    fun secret(hash: ByteArray) = outer(40, ProtoWriter().bytes(1, hash))

    private fun unsigned(value: BigInteger): ByteArray =
        value.toByteArray().let { if (it.size > 1 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }

    /** Returns the pairing secret, or null when the typed code does not match the certificates. */
    fun pairingSecret(client: RSAPublicKey, server: RSAPublicKey, code: String): ByteArray? {
        val clean = code.trim()
        if (clean.length != 6 || clean.any { Character.digit(it, 16) < 0 }) return null
        val bytes = clean.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(unsigned(client.modulus))
        digest.update(unsigned(client.publicExponent))
        digest.update(unsigned(server.modulus))
        digest.update(unsigned(server.publicExponent))
        digest.update(bytes, 1, 2)
        return digest.digest().takeIf { it[0] == bytes[0] }
    }

    fun configure(): ByteArray {
        val info = ProtoWriter().varint(3, 1).string(4, "1").string(5, "com.remotemagic.app").string(6, "1.0.0")
        return ProtoWriter().message(1, ProtoWriter().varint(1, FEATURES).message(2, info)).toByteArray()
    }

    fun setActive() = ProtoWriter().message(2, ProtoWriter().varint(1, FEATURES)).toByteArray()
    fun pingResponse(value: Long) = ProtoWriter().message(9, ProtoWriter().varint(1, value)).toByteArray()
    fun keyPress(keyCode: Int) =
        ProtoWriter().message(10, ProtoWriter().varint(1, keyCode.toLong()).varint(2, 3)).toByteArray()
    /** Inserts text into the field the TV announced through its IME counters. */
    fun imeText(imeCounter: Long, fieldCounter: Long, text: String): ByteArray {
        val cursor = (text.length - 1).toLong()
        val status = ProtoWriter().varint(1, cursor).varint(2, cursor).string(3, text)
        val edit = ProtoWriter().varint(1, 1).message(2, status)
        val batch = ProtoWriter().varint(1, imeCounter).varint(2, fieldCounter).message(3, edit)
        return ProtoWriter().message(21, batch).toByteArray()
    }

    fun appLink(link: String) = ProtoWriter().message(90, ProtoWriter().string(1, link)).toByteArray()
}
