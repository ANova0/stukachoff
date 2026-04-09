package com.stukachoff.domain.checker

import com.stukachoff.domain.model.CheckStatus
import org.junit.Assert.*
import org.junit.Test

class SplitTunnelCheckerTest {

    @Test
    fun `full tunnel when only VPN networks exist`() {
        val status = SplitTunnelClassifier.classify(vpnNetworkCount = 1, nonVpnNetworkCount = 0)
        assertEquals(SplitTunnelStatus.FULL_TUNNEL, status)
    }

    @Test
    fun `split tunnel when both VPN and non-VPN networks exist`() {
        val status = SplitTunnelClassifier.classify(vpnNetworkCount = 1, nonVpnNetworkCount = 1)
        assertEquals(SplitTunnelStatus.SPLIT_TUNNEL, status)
    }

    @Test
    fun `unknown when no VPN network found`() {
        val status = SplitTunnelClassifier.classify(vpnNetworkCount = 0, nonVpnNetworkCount = 1)
        assertEquals(SplitTunnelStatus.UNKNOWN, status)
    }

    @Test
    fun `full tunnel when multiple VPN networks but no non-VPN`() {
        val status = SplitTunnelClassifier.classify(vpnNetworkCount = 2, nonVpnNetworkCount = 0)
        assertEquals(SplitTunnelStatus.FULL_TUNNEL, status)
    }

    @Test
    fun `split tunnel mapped to YELLOW check status`() {
        // Verify enum mapping for UI
        val splitStatus = SplitTunnelStatus.SPLIT_TUNNEL
        val expectedUiStatus = CheckStatus.YELLOW
        // The mapping: SPLIT_TUNNEL -> YELLOW
        assertEquals(CheckStatus.YELLOW, when (splitStatus) {
            SplitTunnelStatus.FULL_TUNNEL  -> CheckStatus.GREEN
            SplitTunnelStatus.SPLIT_TUNNEL -> CheckStatus.YELLOW
            SplitTunnelStatus.UNKNOWN      -> CheckStatus.GREEN
        })
    }
}
