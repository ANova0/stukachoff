package com.stukachoff.data.network

import com.stukachoff.domain.checker.PortCategory
import com.stukachoff.domain.checker.PortCategorizer
import com.stukachoff.domain.checker.OpenPort
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class FullPortScannerTest {

    private val scanner = PortScannerImpl()

    @Test
    fun `fullScan returns list (may be empty on test machine)`() = runTest {
        // fullScan() must run and return a list (empty is ok in unit test environment)
        val result = scanner.fullScan()
        assertNotNull(result)
        assertTrue("fullScan() must return a List", result is List<*>)
    }

    @Test
    fun `fullScan does not include ports outside 1024-65535`() = runTest {
        val result = scanner.fullScan()
        result.forEach { port ->
            assertTrue("Port ${port.port} must be in 1024-65535", port.port in 1024..65535)
        }
    }

    @Test
    fun `fullScan categorizes found ports correctly`() = runTest {
        val result = scanner.fullScan()
        result.forEach { openPort ->
            val expected = PortCategorizer.categorize(openPort.port)
            assertEquals(
                "Port ${openPort.port} category mismatch",
                expected, openPort.category
            )
        }
    }

    @Test
    fun `port categorizer covers all known VPN ports`() {
        assertEquals(PortCategory.XRAY_GRPC, PortCategorizer.categorize(10085))
        assertEquals(PortCategory.XRAY_GRPC, PortCategorizer.categorize(19085))
        assertEquals(PortCategory.CLASH_API, PortCategorizer.categorize(9090))
        assertEquals(PortCategory.SOCKS5,    PortCategorizer.categorize(10808))
        assertEquals(PortCategory.UNKNOWN,   PortCategorizer.categorize(12345))
    }
}
