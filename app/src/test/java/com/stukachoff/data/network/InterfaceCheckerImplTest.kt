package com.stukachoff.data.network

import com.stukachoff.domain.checker.InterfaceType
import org.junit.Assert.*
import org.junit.Test

class InterfaceCheckerImplTest {

    private val checker = InterfaceCheckerImpl()

    @Test
    fun `tun0 is TUN`() {
        assertEquals(InterfaceType.TUN, checker.classifyInterfaceName("tun0"))
    }

    @Test
    fun `tun1 is TUN`() {
        assertEquals(InterfaceType.TUN, checker.classifyInterfaceName("tun1"))
    }

    @Test
    fun `wg0 is WIREGUARD`() {
        assertEquals(InterfaceType.WIREGUARD, checker.classifyInterfaceName("wg0"))
    }

    @Test
    fun `ppp0 is PPP`() {
        assertEquals(InterfaceType.PPP, checker.classifyInterfaceName("ppp0"))
    }

    @Test
    fun `tap0 is TAP`() {
        assertEquals(InterfaceType.TAP, checker.classifyInterfaceName("tap0"))
    }

    @Test
    fun `ipsec0 is IPSEC`() {
        assertEquals(InterfaceType.IPSEC, checker.classifyInterfaceName("ipsec0"))
    }

    @Test
    fun `xfrm0 is IPSEC`() {
        assertEquals(InterfaceType.IPSEC, checker.classifyInterfaceName("xfrm0"))
    }

    @Test
    fun `eth0 is NORMAL`() {
        assertEquals(InterfaceType.NORMAL, checker.classifyInterfaceName("eth0"))
    }

    @Test
    fun `wlan0 is NORMAL`() {
        assertEquals(InterfaceType.NORMAL, checker.classifyInterfaceName("wlan0"))
    }

    @Test
    fun `lo is NORMAL`() {
        assertEquals(InterfaceType.NORMAL, checker.classifyInterfaceName("lo"))
    }

    @Test
    fun `classification is case insensitive`() {
        assertEquals(InterfaceType.TUN, checker.classifyInterfaceName("TUN0"))
        assertEquals(InterfaceType.WIREGUARD, checker.classifyInterfaceName("WG0"))
    }
}
