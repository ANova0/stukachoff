package com.stukachoff.domain.checker

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.stukachoff.domain.model.CheckResult
import com.stukachoff.domain.model.CheckStatus
import com.stukachoff.domain.model.HarmSeverity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

enum class SplitTunnelStatus { FULL_TUNNEL, SPLIT_TUNNEL, UNKNOWN }

// Pure logic — testable without Android framework
object SplitTunnelClassifier {
    fun classify(vpnNetworkCount: Int, nonVpnNetworkCount: Int): SplitTunnelStatus = when {
        vpnNetworkCount > 0 && nonVpnNetworkCount == 0 -> SplitTunnelStatus.FULL_TUNNEL
        vpnNetworkCount > 0 && nonVpnNetworkCount > 0  -> SplitTunnelStatus.SPLIT_TUNNEL
        else -> SplitTunnelStatus.UNKNOWN
    }
}

@Singleton
class SplitTunnelCheckerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun check(): CheckResult.Fixable = withContext(Dispatchers.IO) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
            ?: return@withContext CheckResult.Fixable(
                id = "split_tunnel", title = "Маршрутизация (Split-Tunnel)",
                status = CheckStatus.GREEN, harm = "Статус не определён",
                harmSeverity = HarmSeverity.INFO
            )

        val allNetworks = cm.allNetworks
        val vpnCount = allNetworks.count { network ->
            cm.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
        val nonVpnCount = allNetworks.count { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@count false
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
            (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
             caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
        }

        val status = SplitTunnelClassifier.classify(vpnCount, nonVpnCount)

        CheckResult.Fixable(
            id           = "split_tunnel",
            title        = "Все приложения через VPN",
            status       = when (status) {
                SplitTunnelStatus.FULL_TUNNEL -> CheckStatus.GREEN
                SplitTunnelStatus.SPLIT_TUNNEL -> CheckStatus.YELLOW
                SplitTunnelStatus.UNKNOWN      -> CheckStatus.GREEN // assume full if unknown
            },
            harm         = when (status) {
                SplitTunnelStatus.FULL_TUNNEL ->
                    "Весь трафик идёт через VPN-туннель"
                SplitTunnelStatus.SPLIT_TUNNEL ->
                    "Split-tunnel активен — часть приложений идёт мимо VPN. " +
                    "Приложения в bypass могут делать HTTP-пробы напрямую и детектировать VPN."
                SplitTunnelStatus.UNKNOWN ->
                    "Статус маршрутизации не определён"
            },
            harmSeverity = when (status) {
                SplitTunnelStatus.SPLIT_TUNNEL -> HarmSeverity.HIGH
                else -> HarmSeverity.INFO
            }
        )
    }
}
