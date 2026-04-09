package com.stukachoff.domain.usecase

import com.stukachoff.data.apps.ActiveClient
import com.stukachoff.data.apps.VpnEngine
import com.stukachoff.data.apps.VpnMode
import com.stukachoff.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class TsupAssessorTest {

    private val assessor = TsupAssessor()

    private fun makeClient(engine: VpnEngine, name: String = "Test") = ActiveClient(
        packageName = "", displayName = name, engine = engine,
        mode = VpnMode.TUN, confidence = 90, tsupResistanceBase = TsupLevel.MEDIUM
    )

    @Test
    fun `AmneziaWG gives HIGH TSPU protection`() {
        val (level, _) = assessor.assessTsup(makeClient(VpnEngine.AMNEZIA, "AmneziaWG"), null, 1280)
        assertEquals(ProtectionLevel.HIGH, level)
    }

    @Test
    fun `WireGuard gives LOW TSPU protection`() {
        val (level, _) = assessor.assessTsup(makeClient(VpnEngine.WIREGUARD, "WireGuard"), null, 1420)
        assertEquals(ProtectionLevel.LOW, level)
    }

    @Test
    fun `OpenVPN gives CRITICAL TSPU protection`() {
        val (level, _) = assessor.assessTsup(makeClient(VpnEngine.OPENVPN, "OpenVPN"), null, 1500)
        assertEquals(ProtectionLevel.CRITICAL, level)
    }

    @Test
    fun `config with reality security overrides client-based assessment`() {
        val config = VpnConfig(
            source = ConfigSource.XRAY_GRPC,
            outbounds = listOf(
                OutboundConfig("vless", "1.2.3.4", 443, "tcp", "reality",
                    "icloud.com", "uuid", null, TsupLevel.HIGH)
            )
        )
        val (level, details) = assessor.assessTsup(makeClient(VpnEngine.XRAY), config, 1500)
        assertEquals(ProtectionLevel.HIGH, level)
        assertTrue(details.contains("REALITY"))
    }

    @Test
    fun `verdict with all green checks returns HIGH app protection`() {
        val verdict = assessor.buildVerdict(emptyList(), makeClient(VpnEngine.AMNEZIA), null, 1280)
        assertEquals(ProtectionLevel.HIGH, verdict.appProtection)
        assertNull(verdict.topRecommendation)
    }

    @Test
    fun `verdict with critical grpc check returns CRITICAL app protection`() {
        val criticalCheck = CheckResult.Fixable(
            "grpc_api", "xray gRPC API", CheckStatus.RED,
            "IP сервера доступен", HarmSeverity.CRITICAL
        )
        val verdict = assessor.buildVerdict(listOf(criticalCheck), makeClient(VpnEngine.XRAY), null, 1500)
        assertEquals(ProtectionLevel.CRITICAL, verdict.appProtection)
        assertNotNull(verdict.topRecommendation)
        assertTrue(verdict.topRecommendation!!.contains("gRPC"))
    }

    @Test
    fun `WireGuard generates switch recommendation`() {
        val verdict = assessor.buildVerdict(emptyList(), makeClient(VpnEngine.WIREGUARD, "WireGuard"), null, 1420)
        assertNotNull(verdict.topRecommendation)
        assertTrue(verdict.topRecommendation!!.contains("AmneziaWG"))
    }
}
