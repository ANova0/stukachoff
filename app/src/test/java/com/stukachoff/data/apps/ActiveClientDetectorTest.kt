package com.stukachoff.data.apps

import com.stukachoff.domain.checker.OpenPort
import com.stukachoff.domain.checker.PortCategory
import com.stukachoff.domain.model.TsupLevel
import org.junit.Assert.*
import org.junit.Test

class ActiveClientDetectorTest {

    @Test
    fun `wg0 with MTU 1420 is WireGuard`() {
        val result = ActiveClientDetector.classify("wg0", 1420, emptyList(), emptyList())
        assertEquals(VpnEngine.WIREGUARD, result.engine)
        assertTrue(result.confidence >= 75)
    }

    @Test
    fun `awg0 interface is AmneziaWG`() {
        val result = ActiveClientDetector.classify("awg0", 1280, emptyList(), emptyList())
        assertEquals(VpnEngine.AMNEZIA, result.engine)
    }

    @Test
    fun `single installed Hiddify with tun0 is identified with high confidence`() {
        val installed = listOf("app.hiddify.com")
        val result = ActiveClientDetector.classify("tun0", 1500, installed, emptyList())
        assertEquals("Hiddify", result.displayName)
        assertTrue("Confidence should be >= 90", result.confidence >= 90)
    }

    @Test
    fun `port 10808 identifies v2rayNG even with multiple clients`() {
        val openPorts = listOf(OpenPort(10808, PortCategory.SOCKS5, "v2rayNG SOCKS5"))
        val installed = listOf("com.v2ray.ang", "app.hiddify.com")
        val result = ActiveClientDetector.classify("tun0", 1500, installed, openPorts)
        assertEquals("v2rayNG", result.displayName)
        assertEquals(VpnMode.SOCKS5, result.mode)
    }

    @Test
    fun `multiple xray clients with no ports shows combined names`() {
        val installed = listOf("app.hiddify.com", "dev.hexasoftware.v2box")
        val result = ActiveClientDetector.classify("tun0", 1500, installed, emptyList())
        assertTrue(result.displayName.contains("Hiddify"))
        assertTrue(result.displayName.contains("V2Box"))
        assertEquals(50, result.confidence)
    }

    @Test
    fun `WireGuard has LOW TSPU resistance`() {
        val result = ActiveClientDetector.classify("wg0", 1420, emptyList(), emptyList())
        assertEquals(TsupLevel.LOW, result.tsupResistanceBase)
    }

    @Test
    fun `Amnezia has HIGH TSPU resistance`() {
        val result = ActiveClientDetector.classify("awg0", 1280, emptyList(), emptyList())
        assertEquals(TsupLevel.HIGH, result.tsupResistanceBase)
    }
}
