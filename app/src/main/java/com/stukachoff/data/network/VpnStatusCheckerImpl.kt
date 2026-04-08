package com.stukachoff.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnTransportInfo
import android.net.VpnManager
import android.os.Build
import com.stukachoff.domain.checker.VpnStatusChecker
import com.stukachoff.domain.model.VpnStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VpnStatusCheckerImpl(private val context: Context) : VpnStatusChecker {

    override suspend fun check(): VpnStatus = withContext(Dispatchers.IO) {
        val cm = context.getSystemService(ConnectivityManager::class.java)

        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue

            // Android 12+: определяем тип VPN
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val info = caps.transportInfo
                if (info is VpnTransportInfo) {
                    return@withContext when (info.type) {
                        VpnManager.TYPE_VPN_PLATFORM -> VpnStatus.CORPORATE_VPN
                        else -> VpnStatus.USER_VPN
                    }
                }
            }
            return@withContext VpnStatus.USER_VPN
        }
        VpnStatus.NOT_ACTIVE
    }
}
