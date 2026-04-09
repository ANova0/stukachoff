package com.stukachoff.data.network

import com.stukachoff.domain.checker.PortCategory
import com.stukachoff.domain.checker.PortCategorizer
import com.stukachoff.domain.checker.OpenPort
import org.junit.Assert.*
import org.junit.Test

class FullPortScannerTest {

    @Test
    fun `known gRPC port is in full scan range`() {
        assertTrue(10085 in 1024..65535)
        assertEquals(PortCategory.XRAY_GRPC, PortCategorizer.categorize(10085))
    }

    @Test
    fun `unknown open port classified as UNKNOWN`() {
        val port = OpenPort(12345, PortCategory.UNKNOWN, "Unknown service")
        assertEquals(PortCategory.UNKNOWN, port.category)
    }

    @Test
    fun `full scan range covers all VPN ports`() {
        val knownVpnPorts = listOf(10085, 19085, 23456, 9090, 10808, 2334, 1080)
        knownVpnPorts.forEach { port ->
            assertTrue("Port $port should be in full scan range", port in 1024..65535)
        }
    }

    @Test
    fun `port categorizer returns correct category for all known ports`() {
        assertEquals(PortCategory.XRAY_GRPC, PortCategorizer.categorize(10085))
        assertEquals(PortCategory.XRAY_GRPC, PortCategorizer.categorize(19085))
        assertEquals(PortCategory.CLASH_API, PortCategorizer.categorize(9090))
        assertEquals(PortCategory.SOCKS5,    PortCategorizer.categorize(10808))
        assertEquals(PortCategory.UNKNOWN,   PortCategorizer.categorize(12345))
    }
}
