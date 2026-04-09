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
        val pm = context.packageManager
        ALL_VPN_PACKAGES.mapNotNull { (pkg, name) ->
            try { pm.getPackageInfo(pkg, 0); name }
            catch (_: PackageManager.NameNotFoundException) { null }
        }
    }

    /** Возвращает package names установленных VPN-клиентов (для ActiveClientDetector) */
    suspend fun installedVpnPackages(): List<String> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        ALL_VPN_PACKAGES.mapNotNull { (pkg, _) ->
            try { pm.getPackageInfo(pkg, 0); pkg }
            catch (_: PackageManager.NameNotFoundException) { null }
        }
    }

    companion object {
        // Полный список VPN-клиентов — объединение всех трёх референсов
        // + верифицировано через adb на реальном устройстве
        val ALL_VPN_PACKAGES = listOf(
            // xray / V2Ray семейство
            "com.v2ray.ang"                      to "v2rayNG",
            "dev.hexasoftware.v2box"              to "V2Box",
            "com.v2raytun.android"                to "V2RayTun",
            "io.github.saeeddev94.xray"           to "Xray",
            "com.github.dyhkwong.sagernet"        to "ExclaveVPN",
            // sing-box / NekoBox семейство
            "io.nekohasekai.sfa"                  to "sing-box",
            "moe.nb4a"                            to "NekoBox",
            "com.github.nekohasekai.sagernet"     to "NekoBox",
            "io.nekohasekai.sagernet"             to "NekoBox",
            // Hiddify
            "app.hiddify.com"                     to "Hiddify",
            // HappProxy (HAPP)
            "com.happproxy"                       to "HappProxy",
            // Clash семейство
            "com.clash.mini"                      to "Clash",
            "com.github.metacubex.clash.meta"     to "Clash Meta",
            // Shadowsocks
            "com.github.shadowsocks"              to "Shadowsocks",
            "com.github.shadowsocks.tv"           to "Shadowsocks TV",
            // ByeDPI
            "io.github.dovecoteescapee.byedpi"    to "ByeDPI",
            "com.romanvht.byebyedpi"              to "ByeByeDPI",
            // Amnezia
            "org.amnezia.vpn"                     to "AmneziaVPN",
            "org.amnezia.awg"                     to "AmneziaWG",
            // WireGuard
            "com.wireguard.android"               to "WireGuard",
            "com.zaneschepke.wireguardautotunnel"  to "WG Tunnel",
            // OpenVPN
            "de.blinkt.openvpn"                   to "OpenVPN",
            "net.openvpn.openvpn"                 to "OpenVPN Connect",
            // StrongSwan / IKEv2
            "com.strongswan.android"              to "StrongSwan",
            // Tor
            "org.torproject.android"              to "Orbot (Tor)",
            "org.torproject.torbrowser"           to "Tor Browser",
            "info.guardianproject.orfox"          to "Orfox",
            // Другие
            "org.outline.android.client"          to "Outline VPN",
            "com.psiphon3"                        to "Psiphon",
            "org.getlantern.lantern"              to "Lantern",
            "com.cloudflare.onedotonedotonedotone" to "Cloudflare WARP",
            "com.nordvpn.android"                 to "NordVPN",
            "com.expressvpn.vpn"                  to "ExpressVPN",
            "com.protonvpn.android"               to "Proton VPN",
            "free.vpn.unblock.proxy.turbovpn"     to "Turbo VPN",
            // Telegram прокси
            "org.aspect.tgwsproxy"                to "TG WS Proxy",
            "org.aspect.tgwsproxy.android"        to "TG WS Proxy",
            // Termux (часто используется для проксирования)
            "com.termux"                          to "Termux"
        )
    }
}
