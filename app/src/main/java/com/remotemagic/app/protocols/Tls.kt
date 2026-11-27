package com.remotemagic.app.protocols

import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * TVs use self-signed certificates, so normal CA validation is impossible.
 * Trust on first use: remember the certificate fingerprint and refuse a different one later.
 */
class PinningTrustManager(private val expectedPin: String) : X509TrustManager {
    @Volatile var seenPin: String = expectedPin
        private set

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) =
        throw CertificateException("Client certificates are not accepted")

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
        val pin = fingerprint(chain.first())
        if (expectedPin.isNotEmpty() && pin != expectedPin) {
            throw CertificateException("TV certificate changed. Delete the saved device and pair again.")
        }
        seenPin = pin
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    fun context(keys: Array<javax.net.ssl.KeyManager>? = null): SSLContext =
        SSLContext.getInstance("TLS").apply { init(keys, arrayOf(this@PinningTrustManager), null) }

    companion object {
        fun fingerprint(cert: X509Certificate): String =
            MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02x".format(it) }
    }
}
