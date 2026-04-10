package com.stukachoff.domain.checker

import org.junit.Assert.*
import org.junit.Test

class SplitTunnelCheckerTest {

    @Test
    fun `full tunnel when activeNetwork is VPN`() {
        val status = SplitTunnelClassifier.classify(activeNetworkIsVpn = true, vpnExists = true)
        assertEquals(SplitTunnelStatus.FULL_TUNNEL, status)
    }

    @Test
    fun `split tunnel when VPN exists but activeNetwork is NOT VPN`() {
        val status = SplitTunnelClassifier.classify(activeNetworkIsVpn = false, vpnExists = true)
        assertEquals(SplitTunnelStatus.SPLIT_TUNNEL, status)
    }

    @Test
    fun `unknown when no VPN exists`() {
        val status = SplitTunnelClassifier.classify(activeNetworkIsVpn = false, vpnExists = false)
        assertEquals(SplitTunnelStatus.UNKNOWN, status)
    }

    @Test
    fun `split tunnel is GREEN for Russian users - recommended config`() {
        // Split-tunnel = Russian apps bypass VPN = can't detect VPN via HTTP probing
        val status = SplitTunnelClassifier.classify(activeNetworkIsVpn = false, vpnExists = true)
        assertEquals(SplitTunnelStatus.SPLIT_TUNNEL, status)
        // SPLIT_TUNNEL maps to GREEN (recommended for Russia)
    }

    @Test
    fun `full tunnel is YELLOW - stukachi can detect via HTTP probing`() {
        val status = SplitTunnelClassifier.classify(activeNetworkIsVpn = true, vpnExists = true)
        assertEquals(SplitTunnelStatus.FULL_TUNNEL, status)
        // FULL_TUNNEL maps to YELLOW (stukachi detect VPN via HTTP probes)
    }
}
