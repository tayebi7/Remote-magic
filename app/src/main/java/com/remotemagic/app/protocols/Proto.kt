package com.remotemagic.app.protocols

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/** Minimal protocol-buffers wire codec (varint and length-delimited fields only are needed). */
class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun varint(field: Int, value: Long): ProtoWriter {
        raw((field shl 3).toLong())
        raw(value)
        return this
    }

    fun bytes(field: Int, value: ByteArray): ProtoWriter {
        raw(((field shl 3) or 2).toLong())
        raw(value.size.toLong())
        out.write(value)
        return this
    }

    fun string(field: Int, value: String) = bytes(field, value.toByteArray())
    fun message(field: Int, value: ProtoWriter) = bytes(field, value.toByteArray())
    fun toByteArray(): ByteArray = out.toByteArray()

    private fun raw(value: Long) = writeVarint(out, value)
}

fun writeVarint(out: OutputStream, value: Long) {
    var v = value
    while (v and 0x7fL.inv() != 0L) {
        out.write(((v and 0x7f) or 0x80).toInt())
        v = v ushr 7
    }
    out.write(v.toInt())
}

fun readVarint(input: InputStream): Long {
    var result = 0L
    var shift = 0
    while (shift < 64) {
        val b = input.read()
        if (b < 0) throw EOFException("Connection closed")
        result = result or ((b and 0x7f).toLong() shl shift)
        if (b and 0x80 == 0) return result
        shift += 7
    }
    throw IllegalStateException("Malformed varint")
}

class ProtoMessage(val numbers: Map<Int, Long>, val blobs: Map<Int, ByteArray>) {
    fun has(field: Int) = field in numbers || field in blobs
    fun number(field: Int): Long = numbers[field] ?: 0
    fun message(field: Int): ProtoMessage? = blobs[field]?.let { parse(it) }

    companion object {
        fun parse(data: ByteArray): ProtoMessage {
            val input = data.inputStream()
            val numbers = HashMap<Int, Long>()
            val blobs = HashMap<Int, ByteArray>()
            while (input.available() > 0) {
                val tag = readVarint(input)
                val field = (tag ushr 3).toInt()
                when ((tag and 7).toInt()) {
                    0 -> numbers[field] = readVarint(input)
                    2 -> {
                        val size = readVarint(input).toInt()
                        require(size in 0..input.available()) { "Truncated message" }
                        blobs[field] = ByteArray(size).also { if (size > 0) input.read(it) }
                    }
                    1 -> input.skip(8)
                    5 -> input.skip(4)
                    else -> throw IllegalStateException("Unsupported wire type")
                }
            }
            return ProtoMessage(numbers, blobs)
        }
    }
}

fun OutputStream.writeFrame(payload: ByteArray) {
    val frame = ByteArrayOutputStream()
    writeVarint(frame, payload.size.toLong())
    frame.write(payload)
    write(frame.toByteArray())
    flush()
}

fun InputStream.readFrame(): ByteArray {
    val size = readVarint(this).toInt()
    require(size in 0..65_536) { "Frame too large" }
    val data = ByteArray(size)
    var offset = 0
    while (offset < size) {
        val read = read(data, offset, size - offset)
        if (read < 0) throw EOFException("Connection closed")
        offset += read
    }
    return data
}
