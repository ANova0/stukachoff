package com.stukachoff.data.network

import android.content.Context
import android.net.ConnectivityManager
import com.stukachoff.domain.checker.DnsChecker
import com.stukachoff.domain.model.CheckResult
import com.stukachoff.domain.model.CheckStatus
import com.stukachoff.domain.model.HarmSeverity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DnsCheckerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : DnsChecker {

    fun classifyDns(ip: String): CheckStatus = when {
        ip.startsWith("10.")      -> CheckStatus.GREEN  // VPN туннель (RFC 1918)
        ip.startsWith("172.1")    -> CheckStatus.GREEN  // VPN туннель 172.16-31.x
        ip.startsWith("172.2")    -> CheckStatus.GREEN
        ip.startsWith("172.3")    -> CheckStatus.GREEN
        ip.startsWith("192.168.") -> CheckStatus.YELLOW // Локальная сеть — возможна утечка
        ip.startsWith("127.")     -> CheckStatus.GREEN  // Localhost / FakeIP DNS
        ip.startsWith("::1")      -> CheckStatus.GREEN  // IPv6 Localhost
        ip.startsWith("fd")       -> CheckStatus.GREEN  // IPv6 ULA — VPN туннель
        ip.startsWith("fc")       -> CheckStatus.GREEN  // IPv6 ULA
        ip.isEmpty()              -> CheckStatus.GREEN  // Не определён — не утечка
        else                      -> CheckStatus.RED    // Внешний DNS — утечка
    }

    override suspend fun check(): CheckResult.Fixable = withContext(Dispatchers.IO) {
        val dnsServers = getDnsServersViaLinkProperties()

        // Если DNS-серверы не определены — не можем утверждать что есть утечка
        if (dnsServers.isEmpty()) {
            return@withContext CheckResult.Fixable(
                id           = "dns_leak",
                title        = "DNS-утечка",
                status       = CheckStatus.GREEN,
                harm         = "DNS-серверы не определены (TUN/FakeIP режим — вероятно защищён)",
                harmSeverity = HarmSeverity.INFO
            )
        }

        val leakingServers = dnsServers.filter { classifyDns(it) == CheckStatus.RED }
        val warningServers = dnsServers.filter { classifyDns(it) == CheckStatus.YELLOW }

        CheckResult.Fixable(
            id           = "dns_leak",
            title        = "DNS-утечка",
            status       = when {
                leakingServers.isNotEmpty() -> CheckStatus.RED
                warningServers.isNotEmpty() -> CheckStatus.YELLOW
                else                        -> CheckStatus.GREEN
            },
            harm         = when {
                leakingServers.isNotEmpty() ->
                    "Утечка: DNS-запросы идут через ${leakingServers.joinToString(", ")} — провайдер видит посещаемые сайты"
                warningServers.isNotEmpty() ->
                    "Локальный DNS ${warningServers.joinToString(", ")} — возможна частичная утечка"
                else ->
                    "DNS через туннель: ${dnsServers.joinToString(", ")}"
            },
            harmSeverity = if (leakingServers.isNotEmpty()) HarmSeverity.HIGH else HarmSeverity.MEDIUM
        )
    }

    /**
     * Правильный способ получения DNS на Android — через LinkProperties.
     * System.getProperty("net.dns1") ненадёжен на современных Android.
     */
    private fun getDnsServersViaLinkProperties(): List<String> {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val activeNetwork = cm.activeNetwork ?: return emptyList()
        val linkProps = cm.getLinkProperties(activeNetwork) ?: return emptyList()

        return linkProps.dnsServers
            .mapNotNull { it.hostAddress }
            .filter { it.isNotBlank() }
    }
}

object SystemProxyAnalyzer {
    fun check(): CheckResult.Fixable {
        val httpProxy = System.getProperty("http.proxyHost")
        val socksProxy = System.getProperty("socksProxyHost")
        val proxySet = !httpProxy.isNullOrBlank() || !socksProxy.isNullOrBlank()

        return CheckResult.Fixable(
            id           = "system_proxy",
            title        = "Системный прокси",
            status       = if (proxySet) CheckStatus.RED else CheckStatus.GREEN,
            harm         = if (proxySet)
                "IP прокси ${httpProxy ?: socksProxy} виден всем приложениям без разрешений"
            else
                "Системный прокси не установлен (TUN-режим)",
            harmSeverity = HarmSeverity.MEDIUM
        )
    }
}
