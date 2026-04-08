package com.stukachoff.data.network

import com.stukachoff.domain.checker.DnsChecker
import com.stukachoff.domain.model.CheckResult
import com.stukachoff.domain.model.CheckStatus
import com.stukachoff.domain.model.HarmSeverity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DnsCheckerImpl : DnsChecker {

    fun classifyDns(ip: String): CheckStatus = when {
        ip.startsWith("10.")   -> CheckStatus.GREEN  // VPN туннель (RFC 1918)
        ip.startsWith("172.")  -> CheckStatus.GREEN  // VPN туннель
        ip.startsWith("192.168.") -> CheckStatus.YELLOW  // Локальная сеть — не идеально
        ip.startsWith("127.")  -> CheckStatus.GREEN  // Localhost / FakeIP
        ip.startsWith("fd")    -> CheckStatus.GREEN  // IPv6 ULA — VPN туннель
        ip.startsWith("::1")   -> CheckStatus.GREEN  // IPv6 Localhost
        else -> CheckStatus.RED                       // Внешний DNS — утечка
    }

    override suspend fun check(): CheckResult.Fixable = withContext(Dispatchers.IO) {
        val dnsServers = getDnsServers()
        val hasLeak = dnsServers.any { classifyDns(it) == CheckStatus.RED }
        val hasWarning = dnsServers.any { classifyDns(it) == CheckStatus.YELLOW }

        CheckResult.Fixable(
            id = "dns_leak",
            title = "DNS-утечка",
            status = when {
                hasLeak -> CheckStatus.RED
                hasWarning -> CheckStatus.YELLOW
                else -> CheckStatus.GREEN
            },
            harm = "Провайдер видит все домены которые ты посещаешь — даже с активным VPN",
            harmSeverity = HarmSeverity.HIGH
        )
    }

    private fun getDnsServers(): List<String> = buildList {
        for (i in 1..4) {
            System.getProperty("net.dns$i")?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
    }
}

object SystemProxyAnalyzer {
    fun check(): CheckResult.Fixable {
        val httpProxy = System.getProperty("http.proxyHost")
        val socksProxy = System.getProperty("socksProxyHost")
        val proxySet = !httpProxy.isNullOrBlank() || !socksProxy.isNullOrBlank()

        return CheckResult.Fixable(
            id = "system_proxy",
            title = "Системный прокси",
            status = if (proxySet) CheckStatus.RED else CheckStatus.GREEN,
            harm = "IP прокси-сервера виден всем приложениям через System.getProperty() без разрешений",
            harmSeverity = HarmSeverity.MEDIUM
        )
    }
}
