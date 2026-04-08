package com.stukachoff.data.apps

import android.content.Context
import android.content.pm.PackageManager
import com.stukachoff.data.content.ContentRepository
import com.stukachoff.domain.model.AppThreat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppThreatAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contentRepository: ContentRepository
) {
    suspend fun analyze(): List<AppThreat> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        contentRepository.loadKnownApps().mapNotNull { entry ->
            val packageInfo = try {
                pm.getPackageInfo(entry.packageName, 0)
            } catch (_: PackageManager.NameNotFoundException) { null }

            packageInfo ?: return@mapNotNull null

            AppThreat(
                packageName      = entry.packageName,
                appName          = entry.name,
                version          = packageInfo.versionName,
                threatLevel      = entry.threatLevel,
                isInstalled      = true,
                confirmedMethods = if (entry.confirmed) entry.methods else emptyList(),
                possibleMethods  = if (!entry.confirmed) entry.methods else emptyList(),
                harm             = entry.harm,
                source           = entry.source
            )
        }.sortedWith(compareByDescending<AppThreat> { it.threatLevel.ordinal }
            .thenBy { it.appName })
    }

    suspend fun installedVpnClients(): List<String> = withContext(Dispatchers.IO) {
        val vpnPackages = listOf(
            // Верифицированные package names (проверено через adb)
            "app.hiddify.com"                     to "Hiddify",
            "dev.hexasoftware.v2box"               to "V2Box",
            "com.v2raytun.android"                 to "V2RayTun",
            "com.v2ray.ang"                        to "v2rayNG",
            "com.github.nekohasekai.sagernet"      to "NekoBox",
            "io.nekohasekai.sagernet"              to "NekoBox",
            "moe.nb4a"                             to "NekoBox (nb4a)",
            "com.clash.mini"                       to "Clash",
            "com.github.shadowsocks"               to "Shadowsocks",
            "org.amnezia.vpn"                      to "AmneziaVPN",
            "org.amnezia.awg"                      to "AmneziaWG",
            "de.blinkt.openvpn"                    to "OpenVPN",
            "net.openvpn.openvpn"                  to "OpenVPN Connect",
            "com.wireguard.android"                to "WireGuard",
            "com.zaneschepke.wireguardautotunnel"  to "WG Tunnel"
        )
        val pm = context.packageManager
        vpnPackages.mapNotNull { (pkg, name) ->
            try { pm.getPackageInfo(pkg, 0); name }
            catch (_: PackageManager.NameNotFoundException) { null }
        }
    }
}
