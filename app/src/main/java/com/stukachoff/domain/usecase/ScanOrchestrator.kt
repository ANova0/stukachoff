package com.stukachoff.domain.usecase

import com.stukachoff.data.apps.ActiveClientCheckerImpl
import com.stukachoff.data.apps.AppThreatAnalyzer
import com.stukachoff.data.config.ActiveGrpcScanner
import com.stukachoff.data.config.ClashConfigReader
import com.stukachoff.data.config.XrayConfigReader
import com.stukachoff.data.network.DeviceInfoCollector
import com.stukachoff.data.network.ExitIpCheckResult
import com.stukachoff.data.network.ExitIpChecker
import com.stukachoff.data.network.SystemProxyAnalyzer
import com.stukachoff.data.prefs.AppPreferences
import com.stukachoff.domain.checker.AndroidVersionChecker
import com.stukachoff.domain.checker.DnsChecker
import com.stukachoff.domain.checker.InterfaceChecker
import com.stukachoff.domain.checker.PortCategory
import com.stukachoff.domain.checker.PortScanner
import com.stukachoff.domain.checker.RoutingChecker
import com.stukachoff.domain.checker.SplitTunnelCheckerImpl
import com.stukachoff.domain.checker.VpnStatusChecker
import com.stukachoff.domain.checker.WorkProfileChecker
import com.stukachoff.domain.model.CheckResult
import com.stukachoff.domain.model.CheckStatus
import com.stukachoff.domain.model.ConfigSource
import com.stukachoff.domain.model.DeviceInfo
import com.stukachoff.domain.model.HarmSeverity
import com.stukachoff.domain.model.OutboundConfig
import com.stukachoff.domain.model.ScanState
import com.stukachoff.domain.model.TsupLevel
import com.stukachoff.domain.model.VpnConfig
import com.stukachoff.domain.model.VpnStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

class ScanOrchestrator @Inject constructor(
    private val vpnStatusChecker: VpnStatusChecker,
    private val portScanner: PortScanner,
    private val interfaceChecker: InterfaceChecker,
    private val dnsChecker: DnsChecker,
    private val androidVersionChecker: AndroidVersionChecker,
    private val deviceInfoCollector: DeviceInfoCollector,
    private val appThreatAnalyzer: AppThreatAnalyzer,
    private val exitIpChecker: ExitIpChecker,
    private val routingChecker: RoutingChecker,
    private val workProfileChecker: WorkProfileChecker,
    private val splitTunnelChecker: SplitTunnelCheckerImpl,
    private val activeClientChecker: ActiveClientCheckerImpl,
    private val tsupAssessor: TsupAssessor,
    private val xrayConfigReader: XrayConfigReader,
    private val clashConfigReader: ClashConfigReader,
    private val activeGrpcScanner: ActiveGrpcScanner,
    private val prefs: AppPreferences
) {
    fun scan(): Flow<ScanState> = flow {
        emit(ScanState(isScanning = true))

        val vpnStatus = vpnStatusChecker.check()
        if (vpnStatus != VpnStatus.USER_VPN) {
            emit(ScanState(vpnStatus = vpnStatus, isScanning = false))
            return@flow
        }

        emit(ScanState(vpnStatus = vpnStatus, isScanning = true))

        coroutineScope {
            // Параллельный запуск всех проверок
            val portResult          = async { portScanner.scan() }
            val ifaceResult         = async { interfaceChecker.check() }
            val dnsResult           = async { dnsChecker.check() }
            val vpnClients          = async { appThreatAnalyzer.installedVpnClients() }
            val installedPackages   = async { appThreatAnalyzer.installedVpnPackages() }
            val exitIpResult        = async { exitIpChecker.check() }
            val splitTunnelDeferred = async { splitTunnelChecker.check() }
            val routingResult       = async { routingChecker.check() }
            val workProfile         = async { workProfileChecker.check() }

            val ports       = portResult.await()
            val iface       = ifaceResult.await()
            val dns         = dnsResult.await()
            val clients     = vpnClients.await()
            val instPkgs    = installedPackages.await()
            val exitIp      = exitIpResult.await()
            val splitTunnel = splitTunnelDeferred.await()
            val routing     = routingResult.await()
            val workResult  = workProfile.await()

            val deviceInfo = deviceInfoCollector.collect(clients)

            // FIX-2: Active client без process signal — по installed + interface + MTU + ports
            val activeClient = activeClientChecker.detect(
                primaryInterface     = iface.vpnInterfaces.firstOrNull()?.name,
                mtu                  = iface.vpnInterfaces.firstOrNull()?.mtu ?: 1500,
                openPorts            = ports.openKnownPorts,
                installedVpnPackages = instPkgs
            )

            // FIX-15: Активное чтение конфига — пробуем gRPC на ВСЕХ найденных портах
            val grpcScanResult = activeGrpcScanner.scanAllPorts(ports.openKnownPorts)
            val vpnConfig = grpcScanResult?.config

            // Clash config как fallback если gRPC не нашёлся
            val clashConfig = if (vpnConfig == null && ports.clashApiResult.status == CheckStatus.RED) {
                val clashPort = ports.openKnownPorts
                    .firstOrNull { it.category == PortCategory.CLASH_API }?.port ?: 9090
                clashConfigReader.read(clashPort)?.let { result ->
                    val outbounds = result.proxies.take(3).map { proxy ->
                        OutboundConfig(proxy.type.lowercase(), proxy.server, proxy.port,
                            "tcp", "tls", "", "", null, TsupLevel.MEDIUM)
                    }
                    if (outbounds.isNotEmpty()) VpnConfig(ConfigSource.CLASH_API, outbounds) else null
                }
            } else null

            val finalVpnConfig = vpnConfig ?: clashConfig

            // Маркировка КАК получили конфиг
            // grpcScanResult?.isKnownPort == true → "Стандартный порт — любое приложение видит"
            // grpcScanResult?.isKnownPort == false → "Найден через активное сканирование"

            // FIX-3: Объединяем ExitIp + Routing в одну проверку "VPN работает"
            val vpnWorksCheck = buildVpnWorksCheck(exitIp, routing)

            val fixable = buildList {
                add(vpnWorksCheck)                      // ExitIp + Routing = одна карточка
                add(androidVersionChecker.check())
                add(ports.grpcApiResult)
                add(ports.clashApiResult)
                add(dns)
                add(iface.mtuResult)
                add(SystemProxyAnalyzer.check())
                add(workResult.check)
                add(splitTunnel)
            }.sortedByDescending { it.harmSeverity.ordinal }

            val mtu = iface.vpnInterfaces.firstOrNull()?.mtu ?: 1500
            // IP-анализ для WARP/Relay бонуса
            val ipAnalysis = (exitIp as? ExitIpCheckResult.Success)?.ipAnalysis
            val verdict = tsupAssessor.buildVerdict(fixable, activeClient, finalVpnConfig, mtu, ipAnalysis)

            // Определяем маркировку КАК получили конфиг
            val accessMethod = when {
                grpcScanResult?.isKnownPort == true  -> com.stukachoff.domain.model.ConfigAccessMethod.KNOWN_PORT
                grpcScanResult?.isKnownPort == false -> com.stukachoff.domain.model.ConfigAccessMethod.ACTIVE_PROBE
                clashConfig != null                  -> com.stukachoff.domain.model.ConfigAccessMethod.CLASH_API
                else                                 -> com.stukachoff.domain.model.ConfigAccessMethod.NOT_READ
            }

            emit(ScanState(
                vpnStatus         = vpnStatus,
                alwaysVisible     = buildAlwaysVisible(deviceInfo, activeClient,
                    iface.vpnInterfaces.firstOrNull()?.name, exitIp),
                fixable           = fixable,
                deviceInfo        = deviceInfo,
                activeClient      = activeClient,
                vpnConfig         = finalVpnConfig,
                configAccessMethod = accessMethod,
                overallVerdict    = verdict,
                isScanning        = false
            ))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * FIX-3: Объединяем ExitIpChecker + RoutingChecker в одну карточку "VPN работает"
     */
    private fun buildVpnWorksCheck(
        exitIp: ExitIpCheckResult,
        routing: CheckResult.Fixable
    ): CheckResult.Fixable {
        val exitIpOk = exitIp is ExitIpCheckResult.Success
        val routingOk = routing.status == CheckStatus.GREEN

        return when {
            exitIpOk && routingOk -> {
                val success = exitIp as ExitIpCheckResult.Success
                val analysis = success.ipAnalysis
                val warpInfo = if (analysis.isCloudflare) " · 🛡️ WARP обёртка" else ""
                CheckResult.Fixable(
                    id = "vpn_works", title = "VPN работает",
                    status = CheckStatus.GREEN,
                    harm = "Exit IP: ${success.ip}$warpInfo · ${analysis.description}",
                    harmSeverity = HarmSeverity.INFO
                )
            }
            exitIpOk && !routingOk -> CheckResult.Fixable(
                id = "vpn_works", title = "VPN работает",
                status = CheckStatus.YELLOW,
                harm = "Exit IP: ${(exitIp as ExitIpCheckResult.Success).ip} · ${routing.harm}",
                harmSeverity = HarmSeverity.MEDIUM
            )
            !exitIpOk && routingOk -> CheckResult.Fixable(
                id = "vpn_works", title = "VPN работает",
                status = CheckStatus.YELLOW,
                harm = (exitIp as ExitIpCheckResult.Failed).reason + " · Маршрутизация в норме",
                harmSeverity = HarmSeverity.MEDIUM
            )
            else -> CheckResult.Fixable(
                id = "vpn_works", title = "VPN не работает",
                status = CheckStatus.RED,
                harm = (exitIp as ExitIpCheckResult.Failed).reason,
                harmSeverity = HarmSeverity.CRITICAL
            )
        }
    }

    private fun buildAlwaysVisible(
        deviceInfo: DeviceInfo,
        activeClient: com.stukachoff.data.apps.ActiveClient,
        primaryInterface: String?,
        exitIp: ExitIpCheckResult
    ) = listOf(
        CheckResult.AlwaysVisible(
            id          = "transport_vpn",
            title       = "Факт VPN",
            explanation = "Android ядро выставляет флаг TRANSPORT_VPN когда VPN активен. " +
                    "Любое приложение с разрешением ACCESS_NETWORK_STATE видит его. " +
                    "Скрыть без root невозможно.",
            knowsWhat   = "VPN активен · ${primaryInterface ?: "tun0"} · ${activeClient.displayName}",
            doesntKnow  = "Адрес сервера, ключи, конфигурацию"
        ),
        CheckResult.AlwaysVisible(
            id          = "vpn_clients",
            title       = "Установленные VPN-клиенты",
            explanation = "Через блок <queries> любое приложение может проверить наличие конкретных пакетов.",
            knowsWhat   = if (deviceInfo.installedVpnClients.isEmpty()) "Не обнаружены"
                          else deviceInfo.installedVpnClients.joinToString(", "),
            doesntKnow  = "Конфигурацию и ключи"
        ),
        CheckResult.AlwaysVisible(
            id          = "http_probing",
            title       = "Заблокированные сайты",
            explanation = "Если VPN маршрутизирует трафик — заблокированные сайты открываются. " +
                    "Приложения проверяют это косвенно.",
            knowsWhat   = when (exitIp) {
                is ExitIpCheckResult.Success -> {
                    val a = exitIp.ipAnalysis
                    when {
                        a.isCloudflare -> "Доступны · ${exitIp.ip} (Cloudflare WARP — реальный IP скрыт)"
                        else           -> "Доступны · через ${exitIp.ip}"
                    }
                }
                is ExitIpCheckResult.Failed  -> "Не проверено — ${exitIp.reason}"
            },
            doesntKnow  = when (exitIp) {
                is ExitIpCheckResult.Success ->
                    if (exitIp.ipAnalysis.isCloudflare) "Реальный IP сервера (скрыт за WARP)"
                    else "Какой именно сервер"
                else -> "Какой именно сервер"
            }
        )
    )
}
