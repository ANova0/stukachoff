package com.stukachoff.domain.checker

import com.stukachoff.domain.model.CheckStatus
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
    fun `WiFi plus VPN with VPN as default is FULL tunnel not split`() {
        // This is the critical fix: WiFi always visible alongside VPN on Android
        // But if VPN is the activeNetwork → full tunnel, NOT split
        val status = SplitTunnelClassifier.classify(activeNetworkIsVpn = true, vpnExists = true)
        assertEquals(SplitTunnelStatus.FULL_TUNNEL, status)
    }
}
