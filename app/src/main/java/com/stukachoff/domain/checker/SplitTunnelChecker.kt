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
                id = "split_tunnel", title = "Раздельное туннелирование",
                status = CheckStatus.GREEN, harm = "Статус не определён",
                harmSeverity = HarmSeverity.INFO
            )

        val vpnExists = cm.allNetworks.any { network ->
            cm.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }

        val activeNetwork = cm.activeNetwork
        val activeIsVpn = activeNetwork?.let { network ->
            cm.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        } ?: false

        val status = SplitTunnelClassifier.classify(activeIsVpn, vpnExists)

        // Для пользователей в РФ:
        // Split-tunnel (русские приложения мимо VPN) — РЕКОМЕНДУЕМАЯ настройка
        // Full tunnel — стукачи через HTTP-пробы подтверждают VPN
        CheckResult.Fixable(
            id           = "split_tunnel",
            title        = "Раздельное туннелирование",
            status       = when (status) {
                SplitTunnelStatus.SPLIT_TUNNEL -> CheckStatus.GREEN
                SplitTunnelStatus.FULL_TUNNEL  -> CheckStatus.YELLOW
                SplitTunnelStatus.UNKNOWN      -> CheckStatus.GREEN
            },
            harm         = when (status) {
                SplitTunnelStatus.SPLIT_TUNNEL ->
                    "Stukachoff идёт мимо VPN — раздельное туннелирование активно. " +
                    "Если стукачи (VK, Сбер) тоже в обходе — они не смогут подтвердить VPN. " +
                    "Проверь настройки клиента: стукачи должны быть в обходе, заблокированные сервисы — через VPN."
                SplitTunnelStatus.FULL_TUNNEL ->
                    "Весь трафик через VPN. Стукачи тоже идут через туннель — " +
                    "они делают HTTP-пробы к заблокированным сайтам и могут подтвердить VPN. " +
                    "Рекомендуем: включи раздельное туннелирование (стукачи — в обход, остальное — через VPN)."
                SplitTunnelStatus.UNKNOWN ->
                    "Статус маршрутизации не определён"
            },
            harmSeverity = when (status) {
                SplitTunnelStatus.FULL_TUNNEL -> HarmSeverity.MEDIUM
                else -> HarmSeverity.INFO
            }
        )
    }
}
