package com.stukachoff.data.network

import java.net.InetSocketAddress
import java.net.Socket
import java.io.IOException

enum class DetectedProtocol { SOCKS5, HTTP_PROXY, UNKNOWN_TCP, NOT_OPEN }

object ProtocolVerifier {

    private const val TIMEOUT_MS = 300

    /**
     * Identifies what protocol is running on a localhost port.
     * Uses real handshake bytes — identifies but does NOT exploit.
     */
    fun verify(port: Int): DetectedProtocol {
        if (!isPortReachable(port)) return DetectedProtocol.NOT_OPEN
        if (trySocks5(port)) return DetectedProtocol.SOCKS5
        if (tryHttpProxy(port)) return DetectedProtocol.HTTP_PROXY
        return DetectedProtocol.UNKNOWN_TCP
    }

    private fun isPortReachable(port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), TIMEOUT_MS); true }
    } catch (_: Exception) { false }

    /**
     * SOCKS5 greeting: version=5, nmethods=1, method=0 (no auth)
     * Valid SOCKS5 responds with 0x05 0x00
     */
    private fun trySocks5(port: Int): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), TIMEOUT_MS)
            socket.soTimeout = TIMEOUT_MS
            val out = socket.getOutputStream()
            val inp = socket.getInputStream()
            out.write(byteArrayOf(0x05, 0x01, 0x00))
            out.flush()
            val response = ByteArray(2)
            val read = inp.read(response)
            read >= 2 && response[0] == 0x05.toByte()
        }
    } catch (_: Exception) { false }

    /**
     * HTTP proxy probe: CONNECT method
     * Valid HTTP proxy responds with HTTP/1.x
     */
    private fun tryHttpProxy(port: Int): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), TIMEOUT_MS)
            socket.soTimeout = TIMEOUT_MS
            val out = socket.getOutputStream()
            val inp = socket.getInputStream()
            out.write("CONNECT localhost:80 HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray())
            out.flush()
            val buffer = ByteArray(20)
            val read = inp.read(buffer)
            read > 0 && String(buffer, 0, read).startsWith("HTTP/")
        }
    } catch (_: Exception) { false }
}
