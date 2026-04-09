package com.stukachoff.data.apps

import com.stukachoff.domain.checker.OpenPort
import com.stukachoff.domain.checker.PortCategory
import com.stukachoff.domain.model.TsupLevel
import org.junit.Assert.*
import org.junit.Test

class ActiveClientDetectorTest {

    @Test
    fun `wg0 with MTU 1420 is WireGuard with confidence 75+`() {
        val result = ActiveClientDetector.classify("wg0", 1420, emptySet(), emptyList())
        assertNotNull(result)
        assertEquals(VpnEngine.WIREGUARD, result!!.engine)
        assertTrue("Confidence should be >= 75", result.confidence >= 75)
    }

    @Test
    fun `awg0 interface is AmneziaWG`() {
        val result = ActiveClientDetector.classify("awg0", 1280, emptySet(), emptyList())
        assertNotNull(result)
        assertEquals(VpnEngine.AMNEZIA, result!!.engine)
    }

    @Test
    fun `tun0 with Hiddify running and no ports is TUN mode`() {
        val running = setOf("app.hiddify.com")
        val result = ActiveClientDetector.classify("tun0", 1500, running, emptyList())
        assertNotNull(result)
        assertEquals("app.hiddify.com", result!!.packageName)
        assertEquals("Hiddify", result.displayName)
        assertEquals(VpnMode.TUN, result.mode)
        assertTrue(result.confidence >= 90)
    }

    @Test
    fun `tun0 with v2rayNG running and port 10808 open is SOCKS5 mode`() {
        val running = setOf("com.v2ray.ang")
        val openPorts = listOf(OpenPort(10808, PortCategory.SOCKS5, "v2rayNG SOCKS5"))
        val result = ActiveClientDetector.classify("tun0", 1500, running, openPorts)
        assertNotNull(result)
        assertEquals("com.v2ray.ang", result!!.packageName)
        assertEquals(VpnMode.SOCKS5, result.mode)
    }

    @Test
    fun `WireGuard engine has LOW TSPU resistance`() {
        val result = ActiveClientDetector.classify("wg0", 1420, emptySet(), emptyList())
        assertNotNull(result)
        assertEquals(TsupLevel.LOW, result!!.tsupResistanceBase)
    }

    @Test
    fun `Amnezia engine has HIGH TSPU resistance`() {
        val result = ActiveClientDetector.classify("awg0", 1280, emptySet(), emptyList())
        assertNotNull(result)
        assertEquals(TsupLevel.HIGH, result!!.tsupResistanceBase)
    }

    @Test
    fun `PACKAGE_ENGINE contains all major VPN clients`() {
        val packages = ActiveClientDetector.PACKAGE_ENGINE.keys
        assertTrue(packages.contains("app.hiddify.com"))
        assertTrue(packages.contains("com.v2ray.ang"))
        assertTrue(packages.contains("org.amnezia.awg"))
        assertTrue(packages.contains("com.wireguard.android"))
        assertTrue(packages.contains("de.blinkt.openvpn"))
    }
}
