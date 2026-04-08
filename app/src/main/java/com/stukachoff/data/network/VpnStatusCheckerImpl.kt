package com.stukachoff.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.stukachoff.domain.checker.VpnStatusChecker
import com.stukachoff.domain.model.VpnStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VpnStatusCheckerImpl(private val context: Context) : VpnStatusChecker {

    override suspend fun check(): VpnStatus = withContext(Dispatchers.IO) {
        val cm = context.getSystemService(ConnectivityManager::class.java)

        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                // Проверяем тип VPN через capabilities:
                // Корпоративный VPN не имеет флага NET_CAPABILITY_NOT_VPN
                // но имеет NET_CAPABILITY_ENTERPRISE (API 31+)
                val hasEnterprise = caps.hasCapability(32) // NET_CAPABILITY_ENTERPRISE = 32
                return@withContext if (hasEnterprise) {
                    VpnStatus.CORPORATE_VPN
                } else {
                    VpnStatus.USER_VPN
                }
            }
        }
        VpnStatus.NOT_ACTIVE
    }
}
