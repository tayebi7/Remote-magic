package com.remotemagic.app.discovery

import com.remotemagic.app.domain.Protocol

object Ssdp {
    val TARGETS = listOf(
        "roku:ecp",
        "urn:samsung.com:device:RemoteControlReceiver:1",
        "urn:lge-com:service:webos-second-screen:1"
    )

    fun searchRequest(target: String): String =
        "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: $target\r\n\r\n"

    fun headers(response: String): Map<String, String> =
        response.lineSequence().drop(1).mapNotNull {
            val i = it.indexOf(':')
            if (i <= 0) null else it.substring(0, i).trim().uppercase() to it.substring(i + 1).trim()
        }.toMap()

    /** Which supported protocol answered, or null for unrelated UPnP devices. */
    fun protocolOf(response: String): Protocol? {
        if (!response.startsWith("HTTP/1.1 200")) return null
        val st = headers(response)["ST"].orEmpty()
        return when {
            st == "roku:ecp" -> Protocol.ROKU
            st.startsWith("urn:samsung.com:device:RemoteControlReceiver") -> Protocol.SAMSUNG
            st.startsWith("urn:lge-com:service:webos-second-screen") -> Protocol.LG
            else -> null
        }
    }
}
