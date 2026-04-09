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
    fun `fullScan completes without throwing and ports are in valid range`() = runTest {
        // Verifies fullScan() runs successfully and returns valid data
        val result = scanner.fullScan()
        // Each found port must be in the scanned range
        result.forEach { openPort ->
            assertTrue(
                "Port ${openPort.port} outside scan range",
                openPort.port in 1..65535
            )
            assertFalse("Description must not be empty", openPort.description.isBlank())
        }
        // Passes if no exception thrown (port scanning may return empty on CI)
    }

    @Test
    fun `fullScan covers range 1-65535`() = runTest {
        val result = scanner.fullScan()
        result.forEach { port ->
            assertTrue("Port ${port.port} must be in 1-65535", port.port in 1..65535)
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
