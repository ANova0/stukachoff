package com.stukachoff.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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

    override suspend fun check(): CheckResult.Fixable = withContext(Dispatchers.IO) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
            ?: return@withContext greenResult("Нет доступа к сетевой информации")

        val activeNetwork = cm.activeNetwork
            ?: return@withContext greenResult("Сеть не определена")

        val caps = cm.getNetworkCapabilities(activeNetwork)
        val isVpnNetwork = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        val linkProps = cm.getLinkProperties(activeNetwork)
            ?: return@withContext greenResult("DNS-серверы не определены")

        val dnsServers = linkProps.dnsServers
            .mapNotNull { it.hostAddress }
            .filter { it.isNotBlank() }

        if (dnsServers.isEmpty()) {
            return@withContext greenResult("DNS через FakeIP/TUN (серверы не раскрыты)")
        }

        // КЛЮЧЕВАЯ ЛОГИКА:
        // Если activeNetwork = VPN-сеть → DNS серверы настроены ВНУТРИ туннеля
        // 1.1.1.1 через VPN-туннель = НОРМАЛЬНО, не утечка
        // Утечка = DNS идёт к провайдерскому DNS МИМО туннеля
        if (isVpnNetwork) {
            // DNS получены от VPN-сети → они идут через туннель → GREEN
            return@withContext CheckResult.Fixable(
                id           = "dns_leak",
                title        = "DNS-утечка",
                status       = CheckStatus.GREEN,
                harm         = "DNS через VPN-туннель: ${dnsServers.joinToString(", ")}",
                harmSeverity = HarmSeverity.INFO
            )
        }

        // activeNetwork НЕ VPN — проверяем есть ли VPN-сеть вообще
        val hasVpnNetwork = cm.allNetworks.any { network ->
            cm.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }

        if (hasVpnNetwork) {
            // VPN есть, но наше приложение идёт НЕ через VPN
            // Это нормально при раздельном туннелировании (Stukachoff в bypass)
            val isLocalDns = dnsServers.any { it.startsWith("192.168.") || it.startsWith("10.") }
            if (isLocalDns) {
                // Локальный DNS (роутер) = наше приложение в bypass = раздельное туннелирование
                return@withContext CheckResult.Fixable(
                    id           = "dns_leak",
                    title        = "DNS через основную сеть",
                    status       = CheckStatus.GREEN,
                    harm         = "Stukachoff идёт мимо VPN (раздельное туннелирование). " +
                                   "DNS: ${dnsServers.joinToString(", ")} — это нормально. " +
                                   "Другие приложения через VPN используют свой DNS.",
                    harmSeverity = HarmSeverity.INFO
                )
            }
            return@withContext CheckResult.Fixable(
                id           = "dns_leak",
                title        = "DNS-утечка",
                status       = CheckStatus.RED,
                harm         = "DNS-запросы идут мимо VPN: ${dnsServers.joinToString(", ")} — провайдер видит посещаемые сайты",
                harmSeverity = HarmSeverity.HIGH
            )
        }

        // Нет VPN — не можем оценить
        greenResult("VPN не активен — проверка невозможна")
    }

    // Оставляем для обратной совместимости с тестами
    fun classifyDns(ip: String): CheckStatus = when {
        ip.startsWith("10.")      -> CheckStatus.GREEN
        ip.startsWith("172.")     -> CheckStatus.GREEN
        ip.startsWith("192.168.") -> CheckStatus.YELLOW
        ip.startsWith("127.")     -> CheckStatus.GREEN
        ip.startsWith("::1")      -> CheckStatus.GREEN
        ip.startsWith("fd")       -> CheckStatus.GREEN
        ip.startsWith("fc")       -> CheckStatus.GREEN
        ip.isEmpty()              -> CheckStatus.GREEN
        else                      -> CheckStatus.RED
    }

    private fun greenResult(msg: String) = CheckResult.Fixable(
        id = "dns_leak", title = "DNS-утечка",
        status = CheckStatus.GREEN, harm = msg, harmSeverity = HarmSeverity.INFO
    )
}
