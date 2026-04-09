package com.stukachoff.data.apps

import android.content.Context
import com.stukachoff.domain.checker.OpenPort
import com.stukachoff.domain.checker.PortCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

enum class VpnEngine { XRAY, SINGBOX, WIREGUARD, AMNEZIA, OPENVPN, PSIPHON, TOR, CLOUDFLARE, OTHER }
enum class VpnMode   { TUN, SOCKS5, HTTP, MIXED, UNKNOWN }

data class ActiveClient(
    val packageName: String,
    val displayName: String,
    val engine: VpnEngine,
    val mode: VpnMode,
    val confidence: Int,          // 0-100
    val tsupResistanceBase: com.stukachoff.domain.model.TsupLevel
)

object ActiveClientDetector {

    val PACKAGE_ENGINE: Map<String, Pair<VpnEngine, String>> = mapOf(
        "app.hiddify.com"                      to (VpnEngine.XRAY       to "Hiddify"),
        "com.v2ray.ang"                        to (VpnEngine.XRAY       to "v2rayNG"),
        "dev.hexasoftware.v2box"               to (VpnEngine.XRAY       to "V2Box"),
        "com.v2raytun.android"                 to (VpnEngine.XRAY       to "V2RayTun"),
        "io.github.saeeddev94.xray"            to (VpnEngine.XRAY       to "Xray"),
        "com.github.dyhkwong.sagernet"         to (VpnEngine.XRAY       to "ExclaveVPN"),
        "com.happproxy"                        to (VpnEngine.XRAY       to "HappProxy"),
        "io.nekohasekai.sfa"                   to (VpnEngine.SINGBOX    to "sing-box"),
        "moe.nb4a"                             to (VpnEngine.SINGBOX    to "NekoBox"),
        "com.github.nekohasekai.sagernet"      to (VpnEngine.SINGBOX    to "NekoBox"),
        "io.nekohasekai.sagernet"              to (VpnEngine.SINGBOX    to "NekoBox"),
        "org.amnezia.vpn"                      to (VpnEngine.AMNEZIA    to "AmneziaVPN"),
        "org.amnezia.awg"                      to (VpnEngine.AMNEZIA    to "AmneziaWG"),
        "com.wireguard.android"                to (VpnEngine.WIREGUARD  to "WireGuard"),
        "com.zaneschepke.wireguardautotunnel"  to (VpnEngine.WIREGUARD  to "WG Tunnel"),
        "de.blinkt.openvpn"                    to (VpnEngine.OPENVPN    to "OpenVPN"),
        "net.openvpn.openvpn"                  to (VpnEngine.OPENVPN    to "OpenVPN Connect"),
        "com.psiphon3"                         to (VpnEngine.PSIPHON    to "Psiphon"),
        "org.torproject.android"               to (VpnEngine.TOR        to "Orbot"),
        "com.cloudflare.onedotonedotonedotone" to (VpnEngine.CLOUDFLARE to "Cloudflare WARP"),
        "io.github.dovecoteescapee.byedpi"     to (VpnEngine.OTHER      to "ByeDPI"),
        "com.romanvht.byebyedpi"               to (VpnEngine.OTHER      to "ByeByeDPI"),
        "com.nordvpn.android"                  to (VpnEngine.OTHER      to "NordVPN"),
        "com.expressvpn.vpn"                   to (VpnEngine.OTHER      to "ExpressVPN"),
        "com.protonvpn.android"                to (VpnEngine.OTHER      to "Proton VPN"),
    )

    fun classify(
        interfaceName: String,
        mtu: Int,
        runningPackages: Set<String>,
        openPorts: List<OpenPort>
    ): ActiveClient {
        // Signal 1: interface name → engine
        val engineByIface: VpnEngine? = when {
            interfaceName.startsWith("awg", ignoreCase = true) -> VpnEngine.AMNEZIA
            interfaceName.startsWith("wg",  ignoreCase = true) -> VpnEngine.WIREGUARD
            interfaceName.startsWith("ppp", ignoreCase = true) -> VpnEngine.OPENVPN
            interfaceName.startsWith("tap", ignoreCase = true) -> VpnEngine.OPENVPN
            else -> null // tun0 is ambiguous
        }

        // Signal 2: MTU → engine
        val engineByMtu: VpnEngine? = when (mtu) {
            1280 -> VpnEngine.AMNEZIA   // AmneziaWG characteristic MTU
            1420 -> VpnEngine.WIREGUARD // Standard WireGuard MTU
            else -> null
        }

        // Signal 3: running process → package/engine
        val runningPkg = PACKAGE_ENGINE.keys.firstOrNull { it in runningPackages }
        val (pkgEngine, pkgName) = runningPkg?.let { PACKAGE_ENGINE[it]!! } ?: (null to null)

        // Detect mode from open ports
        val mode: VpnMode = when {
            openPorts.any { it.category == PortCategory.SOCKS5 }    -> VpnMode.SOCKS5
            openPorts.any { it.category == PortCategory.HTTP_PROXY } -> VpnMode.HTTP
            openPorts.any { it.category == PortCategory.MIXED }      -> VpnMode.MIXED
            interfaceName.startsWith("tun", ignoreCase = true)       -> VpnMode.TUN
            else -> VpnMode.UNKNOWN
        }

        val finalEngine = pkgEngine ?: engineByIface ?: engineByMtu ?: VpnEngine.OTHER
        val confidence = when {
            runningPkg != null && (engineByIface == finalEngine || engineByMtu == finalEngine) -> 98
            runningPkg != null                                                                  -> 90
            engineByIface != null && engineByMtu == engineByIface                              -> 85
            engineByIface != null || engineByMtu != null                                       -> 75
            else                                                                               -> 50
        }

        val name = pkgName ?: when (finalEngine) {
            VpnEngine.AMNEZIA   -> "AmneziaWG"
            VpnEngine.WIREGUARD -> "WireGuard"
            VpnEngine.OPENVPN   -> "OpenVPN"
            else -> "VPN"
        }

        return ActiveClient(
            packageName        = runningPkg ?: "",
            displayName        = name,
            engine             = finalEngine,
            mode               = mode,
            confidence         = confidence,
            tsupResistanceBase = baseTsup(finalEngine, mtu)
        )
    }

    private fun baseTsup(engine: VpnEngine, mtu: Int): com.stukachoff.domain.model.TsupLevel {
        return when {
            engine == VpnEngine.AMNEZIA                              -> com.stukachoff.domain.model.TsupLevel.HIGH
            engine == VpnEngine.TOR                                  -> com.stukachoff.domain.model.TsupLevel.HIGH
            engine == VpnEngine.XRAY || engine == VpnEngine.SINGBOX  -> com.stukachoff.domain.model.TsupLevel.MEDIUM
            engine == VpnEngine.WIREGUARD && mtu == 1420             -> com.stukachoff.domain.model.TsupLevel.LOW
            engine == VpnEngine.OPENVPN                              -> com.stukachoff.domain.model.TsupLevel.BLOCKED
            engine == VpnEngine.CLOUDFLARE                           -> com.stukachoff.domain.model.TsupLevel.MEDIUM
            else                                                     -> com.stukachoff.domain.model.TsupLevel.MEDIUM
        }
    }
}

@Singleton
class ActiveClientCheckerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val processChecker: ProcessChecker
) {
    suspend fun detect(
        primaryInterface: String?,
        mtu: Int,
        openPorts: List<OpenPort>
    ): ActiveClient = withContext(Dispatchers.IO) {
        val running = processChecker.getRunningPackages()
        ActiveClientDetector.classify(primaryInterface ?: "tun0", mtu, running, openPorts)
    }
}
