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

/**
 * Проверяет что VPN-сеть:
 * 1. Имеет дефолтный маршрут (0.0.0.0/0) — трафик идёт через туннель
 * 2. Прошла валидацию Android — реально имеет интернет (NET_CAPABILITY_VALIDATED)
 */
@Singleton
class RoutingChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun check(): CheckResult.Fixable = withContext(Dispatchers.IO) {
        val cm = context.getSystemService(ConnectivityManager::class.java)

        // Находим VPN-сеть
        val vpnNetwork = cm.allNetworks.firstOrNull { network ->
            cm.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }

        if (vpnNetwork == null) {
            return@withContext CheckResult.Fixable(
                id           = "routing",
                title        = "Маршрутизация трафика",
                status       = CheckStatus.YELLOW,
                harm         = "VPN-сеть не найдена в таблице маршрутизации",
                harmSeverity = HarmSeverity.HIGH
            )
        }

        val caps      = cm.getNetworkCapabilities(vpnNetwork)
        val linkProps = cm.getLinkProperties(vpnNetwork)

        // Проверяем NET_CAPABILITY_VALIDATED — Android убедился что сеть имеет интернет
        val isValidated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

        // Проверяем наличие дефолтного маршрута через VPN
        val hasDefaultRoute = linkProps?.routes?.any { route ->
            route.destination.address.hostAddress?.let {
                it == "0.0.0.0" || it == "::"
            } ?: false || route.isDefaultRoute
        } ?: false

        // Проверяем интерфейс VPN
        val vpnInterface = linkProps?.interfaceName ?: "unknown"

        when {
            !isValidated && !hasDefaultRoute -> CheckResult.Fixable(
                id           = "routing",
                title        = "Маршрутизация трафика",
                status       = CheckStatus.RED,
                harm         = "VPN ($vpnInterface) не валидирован Android и нет дефолтного маршрута — трафик идёт мимо туннеля",
                harmSeverity = HarmSeverity.CRITICAL
            )
            !isValidated -> CheckResult.Fixable(
                id           = "routing",
                title        = "Маршрутизация трафика",
                status       = CheckStatus.YELLOW,
                harm         = "VPN ($vpnInterface) не прошёл валидацию Android — возможно нет интернета через туннель",
                harmSeverity = HarmSeverity.HIGH
            )
            !hasDefaultRoute -> CheckResult.Fixable(
                id           = "routing",
                title        = "Маршрутизация трафика",
                status       = CheckStatus.YELLOW,
                harm         = "Нет дефолтного маршрута через VPN ($vpnInterface) — возможен split-tunnel без полной защиты",
                harmSeverity = HarmSeverity.MEDIUM
            )
            else -> CheckResult.Fixable(
                id           = "routing",
                title        = "Маршрутизация трафика",
                status       = CheckStatus.GREEN,
                harm         = "Весь трафик маршрутизируется через $vpnInterface",
                harmSeverity = HarmSeverity.INFO
            )
        }
    }
}
