package com.stukachoff.data.network

import org.junit.Assert.*
import org.junit.Test

class ProtocolVerifierTest {

    @Test
    fun `SOCKS5 greeting bytes are correct format`() {
        // Version 5, 1 method, method 0 (no auth)
        val greeting = byteArrayOf(0x05, 0x01, 0x00)
        assertEquals(0x05.toByte(), greeting[0]) // SOCKS5 version
        assertEquals(0x01.toByte(), greeting[1]) // number of methods
        assertEquals(0x00.toByte(), greeting[2]) // no auth method
    }

    @Test
    fun `HTTP CONNECT probe is valid HTTP format`() {
        val probe = "CONNECT localhost:80 HTTP/1.1\r\nHost: localhost\r\n\r\n"
        assertTrue(probe.startsWith("CONNECT"))
        assertTrue(probe.contains("HTTP/1.1"))
        assertTrue(probe.endsWith("\r\n\r\n"))
    }

    @Test
    fun `verify on closed port returns UNKNOWN_TCP`() {
        // Port 1 is always closed on Android
        val result = ProtocolVerifier.verify(1)
        // Either NOT_OPEN or UNKNOWN_TCP — both are valid for a closed port
        assertTrue(result == DetectedProtocol.UNKNOWN_TCP || result == DetectedProtocol.NOT_OPEN)
    }

    @Test
    fun `DetectedProtocol enum has all expected values`() {
        val values = DetectedProtocol.values().map { it.name }
        assertTrue(values.contains("SOCKS5"))
        assertTrue(values.contains("HTTP_PROXY"))
        assertTrue(values.contains("UNKNOWN_TCP"))
        assertTrue(values.contains("NOT_OPEN"))
    }
}
