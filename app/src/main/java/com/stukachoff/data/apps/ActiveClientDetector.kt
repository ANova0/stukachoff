package com.stukachoff.data.apps

import android.content.Context
import android.content.pm.PackageManager
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
    val confidence: Int,
    val tsupResistanceBase: com.stukachoff.domain.model.TsupLevel,
    val allInstalled: List<String> = emptyList()  // все установленные VPN-клиенты
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

    // Порты которые однозначно привязаны к конкретному клиенту
    private val PORT_TO_CLIENT = mapOf(
        2334  to "Hiddify",         // Hiddify SOCKS5
        2333  to "Hiddify",         // Hiddify HTTP
        10808 to "v2rayNG",         // v2rayNG SOCKS5
        10809 to "v2rayNG",         // v2rayNG HTTP
        7891  to "Clash",           // Clash SOCKS5
        7890  to "Clash",           // Clash HTTP
    )

    /**
     * Определяет активный VPN-клиент по 3 сигналам БЕЗ process signal
     * (getRunningAppProcesses мёртв на Android 13+).
     *
     * Сигналы:
     * 1. Если ОДИН VPN-клиент установлен → 100% это он
     * 2. Interface name + MTU (wg0 → WireGuard, MTU 1280 → AmneziaWG)
     * 3. Open ports (2334 → Hiddify, 10808 → v2rayNG)
     */
    fun classify(
        interfaceName: String,
        mtu: Int,
        installedVpnPackages: List<String>,
        openPorts: List<OpenPort>
    ): ActiveClient {
        val installedClients = installedVpnPackages.mapNotNull { PACKAGE_ENGINE[it] }

        // Signal 1: Interface → engine (wg0 → WG, awg → Amnezia)
        val engineByIface: VpnEngine? = when {
            interfaceName.startsWith("awg", ignoreCase = true) -> VpnEngine.AMNEZIA
            interfaceName.startsWith("wg",  ignoreCase = true) -> VpnEngine.WIREGUARD
            interfaceName.startsWith("ppp", ignoreCase = true) -> VpnEngine.OPENVPN
            interfaceName.startsWith("tap", ignoreCase = true) -> VpnEngine.OPENVPN
            else -> null
        }

        // Signal 2: MTU → engine
        // AmneziaVPN использует разные MTU: 1280 (AmneziaWG), 1376 (OpenVPN), 1320 и др.
        // WireGuard стандартный: 1420
        val engineByMtu: VpnEngine? = when (mtu) {
            1280 -> VpnEngine.AMNEZIA   // AmneziaWG
            1376 -> VpnEngine.AMNEZIA   // AmneziaVPN OpenVPN mode
            1320 -> VpnEngine.AMNEZIA   // AmneziaVPN WG mode
            1420 -> VpnEngine.WIREGUARD // Standard WireGuard
            else -> {
                // Нестандартный MTU (не 1500 и не 1420) + установлена Amnezia → вероятно Amnezia
                if (mtu != 1500 && mtu != 1420 && mtu < 1500 &&
                    installedVpnPackages.any { it.startsWith("org.amnezia") }) {
                    VpnEngine.AMNEZIA
                } else null
            }
        }

        // Signal 3: Open ports → specific client name
        // Но только если этот клиент реально установлен
        val clientByPort = openPorts
            .mapNotNull { PORT_TO_CLIENT[it.port] }
            .firstOrNull { portClient ->
                // Проверяем что клиент реально установлен
                installedClients.any { it.second == portClient }
            }

        // Signal 4: If only ONE VPN client installed → must be it
        val singleInstalled = if (installedClients.size == 1) installedClients.first() else null

        // Determine mode
        val mode: VpnMode = when {
            openPorts.any { it.category == PortCategory.SOCKS5 }    -> VpnMode.SOCKS5
            openPorts.any { it.category == PortCategory.HTTP_PROXY } -> VpnMode.HTTP
            openPorts.any { it.category == PortCategory.MIXED }      -> VpnMode.MIXED
            interfaceName.startsWith("tun", ignoreCase = true)       -> VpnMode.TUN
            else -> VpnMode.UNKNOWN
        }

        // Priority: singleInstalled > clientByPort > engineByIface > engineByMtu
        val (finalName, finalEngine, confidence) = when {
            // Один установлен → точно он
            singleInstalled != null -> Triple(
                singleInstalled.second, singleInstalled.first, 95
            )
            // Порт уникально идентифицирует клиент
            clientByPort != null -> {
                val entry = PACKAGE_ENGINE.values.find { it.second == clientByPort }
                Triple(clientByPort, entry?.first ?: VpnEngine.XRAY, 90)
            }
            // Interface определяет engine, ищем клиента этого engine
            engineByIface != null -> {
                val clientsOfEngine = installedClients.filter { it.first == engineByIface }
                val name = if (clientsOfEngine.size == 1) clientsOfEngine.first().second
                           else clientsOfEngine.joinToString(" / ") { it.second }.ifBlank {
                               engineByIface.name
                           }
                val conf = when {
                    clientsOfEngine.size == 1          -> 85
                    engineByMtu == engineByIface       -> 85  // interface + MTU agree
                    else                               -> 60
                }
                Triple(name, engineByIface, conf)
            }
            // MTU определяет engine
            engineByMtu != null -> {
                val clientsOfEngine = installedClients.filter { it.first == engineByMtu }
                val name = if (clientsOfEngine.size == 1) clientsOfEngine.first().second
                           else engineByMtu.name
                Triple(name, engineByMtu, 75)
            }
            // Несколько клиентов, tun0, нет портов → "один из:"
            else -> {
                val xrayClients = installedClients.filter {
                    it.first == VpnEngine.XRAY || it.first == VpnEngine.SINGBOX
                }
                if (xrayClients.isNotEmpty()) {
                    val names = xrayClients.joinToString(", ") { it.second }
                    Triple("Один из: $names", VpnEngine.XRAY, 40)
                } else {
                    val names = installedClients.joinToString(", ") { it.second }
                    Triple(names.ifBlank { "VPN" }, VpnEngine.OTHER, 30)
                }
            }
        }

        return ActiveClient(
            packageName        = "",
            displayName        = finalName,
            engine             = finalEngine,
            mode               = mode,
            confidence         = confidence,
            tsupResistanceBase = baseTsup(finalEngine, mtu),
            allInstalled       = installedClients.map { it.second }
        )
    }

    private fun baseTsup(engine: VpnEngine, mtu: Int): com.stukachoff.domain.model.TsupLevel = when {
        engine == VpnEngine.AMNEZIA                              -> com.stukachoff.domain.model.TsupLevel.HIGH
        engine == VpnEngine.TOR                                  -> com.stukachoff.domain.model.TsupLevel.HIGH
        engine == VpnEngine.XRAY || engine == VpnEngine.SINGBOX  -> com.stukachoff.domain.model.TsupLevel.MEDIUM
        engine == VpnEngine.WIREGUARD && mtu == 1420             -> com.stukachoff.domain.model.TsupLevel.LOW
        engine == VpnEngine.OPENVPN                              -> com.stukachoff.domain.model.TsupLevel.BLOCKED
        engine == VpnEngine.CLOUDFLARE                           -> com.stukachoff.domain.model.TsupLevel.MEDIUM
        else                                                     -> com.stukachoff.domain.model.TsupLevel.MEDIUM
    }
}

@Singleton
class ActiveClientCheckerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Определяет активный VPN-клиент.
     * НЕ использует getRunningAppProcesses (мёртв на Android 13+).
     * Вместо этого: installed packages + interface + MTU + ports.
     */
    suspend fun detect(
        primaryInterface: String?,
        mtu: Int,
        openPorts: List<OpenPort>,
        installedVpnPackages: List<String>
    ): ActiveClient = withContext(Dispatchers.IO) {
        ActiveClientDetector.classify(
            primaryInterface ?: "tun0", mtu, installedVpnPackages, openPorts
        )
    }
}
