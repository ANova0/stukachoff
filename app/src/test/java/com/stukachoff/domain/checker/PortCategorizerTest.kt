package com.stukachoff.domain.checker

import org.junit.Assert.*
import org.junit.Test

class PortCategorizerTest {

    @Test
    fun `port 10085 is XRAY_GRPC`() {
        assertEquals(PortCategory.XRAY_GRPC, PortCategorizer.categorize(10085))
    }

    @Test
    fun `port 19085 is XRAY_GRPC`() {
        assertEquals(PortCategory.XRAY_GRPC, PortCategorizer.categorize(19085))
    }

    @Test
    fun `port 23456 is XRAY_GRPC`() {
        assertEquals(PortCategory.XRAY_GRPC, PortCategorizer.categorize(23456))
    }

    @Test
    fun `port 9090 is CLASH_API`() {
        assertEquals(PortCategory.CLASH_API, PortCategorizer.categorize(9090))
    }

    @Test
    fun `port 10808 is SOCKS5`() {
        assertEquals(PortCategory.SOCKS5, PortCategorizer.categorize(10808))
    }

    @Test
    fun `port 10809 is HTTP_PROXY`() {
        assertEquals(PortCategory.HTTP_PROXY, PortCategorizer.categorize(10809))
    }

    @Test
    fun `port 2334 is SOCKS5 for Hiddify`() {
        assertEquals(PortCategory.SOCKS5, PortCategorizer.categorize(2334))
    }

    @Test
    fun `port 7891 is SOCKS5 for Clash`() {
        assertEquals(PortCategory.SOCKS5, PortCategorizer.categorize(7891))
    }

    @Test
    fun `port 1080 is MIXED for sing-box`() {
        assertEquals(PortCategory.MIXED, PortCategorizer.categorize(1080))
    }

    @Test
    fun `unknown port returns UNKNOWN`() {
        assertEquals(PortCategory.UNKNOWN, PortCategorizer.categorize(12345))
    }

    @Test
    fun `grpcPorts set contains all xray ports`() {
        assertTrue(10085 in PortCategorizer.grpcPorts)
        assertTrue(19085 in PortCategorizer.grpcPorts)
        assertTrue(23456 in PortCategorizer.grpcPorts)
    }
}
