package com.remotemagic.app.bluetooth

import com.remotemagic.app.domain.RemoteKey

/** USB HID usages sent over Bluetooth: report 1 = keyboard, report 2 = consumer control. */
object HidReports {
    const val KEYBOARD_ID = 1
    const val CONSUMER_ID = 2

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    val DESCRIPTOR: ByteArray = bytes(
        0x05, 0x01, 0x09, 0x06, 0xA1, 0x01, 0x85, KEYBOARD_ID,
        0x05, 0x07, 0x19, 0xE0, 0x29, 0xE7, 0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95, 0x08, 0x81, 0x02,
        0x95, 0x01, 0x75, 0x08, 0x81, 0x01,
        0x95, 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x65, 0x19, 0x00, 0x29, 0x65, 0x81, 0x00,
        0xC0,
        0x05, 0x0C, 0x09, 0x01, 0xA1, 0x01, 0x85, CONSUMER_ID,
        0x15, 0x00, 0x26, 0xFF, 0x03, 0x19, 0x00, 0x2A, 0xFF, 0x03, 0x75, 0x10, 0x95, 0x01, 0x81, 0x00,
        0xC0
    )

    private val keyboard: Map<RemoteKey, Int> = mapOf(
        RemoteKey.UP to 0x52, RemoteKey.DOWN to 0x51, RemoteKey.LEFT to 0x50, RemoteKey.RIGHT to 0x4F,
        RemoteKey.OK to 0x28, RemoteKey.NUM_0 to 0x27
    ) + (1..9).associate { RemoteKey.digit(it) to 0x1D + it }

    private val consumer: Map<RemoteKey, Int> = mapOf(
        RemoteKey.POWER to 0x30, RemoteKey.HOME to 0x223, RemoteKey.BACK to 0x224, RemoteKey.MENU to 0x40,
        RemoteKey.VOLUME_UP to 0xE9, RemoteKey.VOLUME_DOWN to 0xEA, RemoteKey.MUTE to 0xE2,
        RemoteKey.CHANNEL_UP to 0x9C, RemoteKey.CHANNEL_DOWN to 0x9D, RemoteKey.PLAY_PAUSE to 0xCD,
        RemoteKey.REWIND to 0xB4, RemoteKey.FAST_FORWARD to 0xB3
    )

    val keys: Set<RemoteKey> = keyboard.keys + consumer.keys

    data class Report(val id: Int, val press: ByteArray, val release: ByteArray)

    fun report(key: RemoteKey): Report? {
        keyboard[key]?.let {
            val press = ByteArray(8).also { data -> data[2] = it.toByte() }
            return Report(KEYBOARD_ID, press, ByteArray(8))
        }
        return consumer[key]?.let {
            Report(CONSUMER_ID, bytes(it and 0xFF, it shr 8), ByteArray(2))
        }
    }
}
