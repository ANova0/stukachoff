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

/**
 * Определяет реальный split-tunnel.
 *
 * ВАЖНО: На Android WiFi-сеть ВСЕГДА видна рядом с VPN —
 * это НЕ split-tunnel. Split-tunnel = когда VPN НЕ является
 * дефолтной сетью (activeNetwork ≠ VPN).
 *
 * Правильная проверка: activeNetwork = VPN → full tunnel.
 */
object SplitTunnelClassifier {
    fun classify(activeNetworkIsVpn: Boolean, vpnExists: Boolean): SplitTunnelStatus = when {
        vpnExists && activeNetworkIsVpn  -> SplitTunnelStatus.FULL_TUNNEL
        vpnExists && !activeNetworkIsVpn -> SplitTunnelStatus.SPLIT_TUNNEL
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
                id = "split_tunnel", title = "Все приложения через VPN",
                status = CheckStatus.GREEN, harm = "Статус не определён",
                harmSeverity = HarmSeverity.INFO
            )

        // Есть ли VPN-сеть вообще
        val vpnExists = cm.allNetworks.any { network ->
            cm.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }

        // Является ли VPN дефолтной сетью (activeNetwork)
        val activeNetwork = cm.activeNetwork
        val activeIsVpn = activeNetwork?.let { network ->
            cm.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        } ?: false

        val status = SplitTunnelClassifier.classify(activeIsVpn, vpnExists)

        CheckResult.Fixable(
            id           = "split_tunnel",
            title        = "Все приложения через VPN",
            status       = when (status) {
                SplitTunnelStatus.FULL_TUNNEL  -> CheckStatus.GREEN
                SplitTunnelStatus.SPLIT_TUNNEL -> CheckStatus.YELLOW
                SplitTunnelStatus.UNKNOWN      -> CheckStatus.GREEN
            },
            harm         = when (status) {
                SplitTunnelStatus.FULL_TUNNEL ->
                    "VPN — дефолтная сеть. Весь трафик через туннель."
                SplitTunnelStatus.SPLIT_TUNNEL ->
                    "VPN не является дефолтной сетью — часть приложений может идти мимо туннеля."
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
