package com.stukachoff.data.network

import android.os.Build
import com.stukachoff.domain.model.DeviceInfo
import com.stukachoff.domain.model.InterfaceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceInfoCollector @Inject constructor() {

    suspend fun collect(installedVpnClients: List<String>): DeviceInfo =
        withContext(Dispatchers.IO) {
            val vpnInterfaces = collectVpnInterfaces()
            DeviceInfo(
                androidVersion      = Build.VERSION.RELEASE,
                sdkInt              = Build.VERSION.SDK_INT,
                // Без Build.FINGERPRINT — только модель
                manufacturer        = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
                model               = Build.MODEL,
                vpnInterfaces       = vpnInterfaces,
                installedVpnClients = installedVpnClients
            )
        }

    private fun collectVpnInterfaces(): List<InterfaceInfo> {
        val vpnPrefixes = setOf("tun", "wg", "tap", "ppp", "ipsec", "xfrm")
        return runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList()
                ?.filter { iface -> vpnPrefixes.any { iface.name.lowercase().startsWith(it) } }
                ?.map { iface ->
                    InterfaceInfo(
                        name      = iface.name,
                        addresses = iface.inetAddresses.toList()
                            .filter { !it.isLoopbackAddress }
                            .map { it.hostAddress ?: "" }
                            .filter { it.isNotBlank() },
                        mtu       = iface.mtu
                    )
                } ?: emptyList()
        }.getOrDefault(emptyList())
    }
}
