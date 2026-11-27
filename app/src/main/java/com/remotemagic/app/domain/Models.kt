package com.remotemagic.app.domain

enum class Protocol(val label: String, val wifi: Boolean = true) {
    ANDROID_TV("Android TV / Google TV"),
    SAMSUNG("Samsung Tizen"),
    LG("LG webOS"),
    ROKU("Roku"),
    BLUETOOTH_HID("Bluetooth HID", wifi = false)
}

enum class RemoteKey {
    POWER, HOME, BACK, MENU, SETTINGS, SOURCE, EXIT, UP, DOWN, LEFT, RIGHT, OK,
    VOLUME_UP, VOLUME_DOWN, MUTE, CHANNEL_UP, CHANNEL_DOWN,
    NUM_0, NUM_1, NUM_2, NUM_3, NUM_4, NUM_5, NUM_6, NUM_7, NUM_8, NUM_9,
    PLAY_PAUSE, REWIND, FAST_FORWARD, STOP, STAR, HASH, ENTER;

    companion object {
        fun digit(n: Int): RemoteKey = valueOf("NUM_$n")
    }
}

/** The only three shortcuts of the app. */
enum class TvApp(val title: String) { YOUTUBE("YouTube"), IPTV("IPTV"), RADIO("Radio") }

enum class PowerState { ON, STANDBY }

enum class TextResult { CONFIRMED, SENT, NO_FIELD, BAD_CHARACTERS, FAILED }

data class DiscoveredDevice(
    val name: String,
    val protocol: Protocol,
    /** IP address for Wi-Fi devices, MAC address for Bluetooth devices. */
    val address: String,
    val manufacturer: String = "",
    val mac: String = "",
    /** False for devices seen during a scan that cannot be controlled (BLE only). */
    val supported: Boolean = true,
    val paired: Boolean = false
) {
    val id: String get() = "${protocol.name}:$address"
}

enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

data class ConnectionState(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val device: DiscoveredDevice? = null,
    val error: String? = null,
    /** True when the TV or link dropped without the user asking. */
    val lost: Boolean = false,
    val power: PowerState? = null
)

enum class SendResult { OK, NOT_CONNECTED, UNSUPPORTED, FAILED }
