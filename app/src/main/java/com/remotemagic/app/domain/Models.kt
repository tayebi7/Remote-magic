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
    PLAY_PAUSE, REWIND, FAST_FORWARD;

    companion object {
        fun digit(n: Int): RemoteKey = valueOf("NUM_$n")
    }
}

enum class TvApp(val title: String) {
    YOUTUBE("YouTube"), NETFLIX("Netflix"), PRIME("Prime Video"), DISNEY("Disney+"),
    IPTV("IPTV"), MOVIES("Movies"), MUSIC("Music"), BROWSER("Browser")
}

data class DiscoveredDevice(
    val name: String,
    val protocol: Protocol,
    /** IP address for Wi-Fi devices, MAC address for Bluetooth devices. */
    val address: String,
    val manufacturer: String = "",
    val mac: String = ""
) {
    val id: String get() = "${protocol.name}:$address"
}

enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

data class ConnectionState(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val device: DiscoveredDevice? = null,
    val error: String? = null
)

enum class SendResult { OK, NOT_CONNECTED, UNSUPPORTED, FAILED }
