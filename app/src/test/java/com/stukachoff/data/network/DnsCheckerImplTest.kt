package com.stukachoff.data.network

import com.stukachoff.domain.model.CheckStatus
import org.junit.Assert.*
import org.junit.Test

class DnsCheckerImplTest {

    private val checker = DnsCheckerImpl()

    @Test
    fun `10_x is VPN tunnel — GREEN`() {
        assertEquals(CheckStatus.GREEN, checker.classifyDns("10.0.0.1"))
        assertEquals(CheckStatus.GREEN, checker.classifyDns("10.10.14.1"))
    }

    @Test
    fun `172_x is VPN tunnel — GREEN`() {
        assertEquals(CheckStatus.GREEN, checker.classifyDns("172.16.0.1"))
    }

    @Test
    fun `127_x is localhost FakeIP — GREEN`() {
        assertEquals(CheckStatus.GREEN, checker.classifyDns("127.0.0.1"))
        assertEquals(CheckStatus.GREEN, checker.classifyDns("127.0.0.53"))
    }

    @Test
    fun `IPv6 fd prefix is VPN — GREEN`() {
        assertEquals(CheckStatus.GREEN, checker.classifyDns("fd00::1"))
    }

    @Test
    fun `8_8_8_8 Google DNS is leak — RED`() {
        assertEquals(CheckStatus.RED, checker.classifyDns("8.8.8.8"))
    }

    @Test
    fun `1_1_1_1 Cloudflare DNS is leak — RED`() {
        assertEquals(CheckStatus.RED, checker.classifyDns("1.1.1.1"))
    }

    @Test
    fun `provider DNS is leak — RED`() {
        assertEquals(CheckStatus.RED, checker.classifyDns("77.88.8.8"))
    }

    @Test
    fun `192_168 local network is YELLOW`() {
        assertEquals(CheckStatus.YELLOW, checker.classifyDns("192.168.1.1"))
    }
}
