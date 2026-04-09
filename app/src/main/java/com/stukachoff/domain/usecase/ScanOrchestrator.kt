package com.stukachoff.domain.usecase

import com.stukachoff.data.apps.AppThreatAnalyzer
import com.stukachoff.data.network.DeviceInfoCollector
import com.stukachoff.data.network.ExitIpChecker
import com.stukachoff.data.network.SystemProxyAnalyzer
import com.stukachoff.data.prefs.AppPreferences
import com.stukachoff.domain.checker.AndroidVersionChecker
import com.stukachoff.domain.checker.DnsChecker
import com.stukachoff.domain.checker.InterfaceChecker
import com.stukachoff.domain.checker.PortScanner
import com.stukachoff.domain.checker.VpnStatusChecker
import com.stukachoff.domain.checker.WorkProfileChecker
import com.stukachoff.domain.model.CheckResult
import com.stukachoff.domain.model.DeviceInfo
import com.stukachoff.domain.model.ScanState
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
    private val workProfileChecker: WorkProfileChecker,
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
            val portResult   = async { portScanner.scan() }
            val ifaceResult  = async { interfaceChecker.check() }
            val dnsResult    = async { dnsChecker.check() }
            val vpnClients   = async { appThreatAnalyzer.installedVpnClients() }
            // Exit IP — только если сетевой режим включён
            val exitIpResult     = async { exitIpChecker.check() }
            val workProfile      = async { workProfileChecker.check() }

            val ports   = portResult.await()
            val iface   = ifaceResult.await()
            val dns     = dnsResult.await()
            val clients = vpnClients.await()
            val exitIp      = exitIpResult.await()
            val workResult  = workProfile.await()

            val deviceInfo = deviceInfoCollector.collect(clients)

            val fixable = buildList {
                add(androidVersionChecker.check())
                add(ports.proxyModeResult)
                add(ports.grpcApiResult)
                add(ports.clashApiResult)
                add(dns)
                add(iface.mtuResult)
                add(SystemProxyAnalyzer.check())
                add(exitIp)
                add(workResult.check)
            }.sortedByDescending { it.harmSeverity.ordinal }

            emit(ScanState(
                vpnStatus     = vpnStatus,
                alwaysVisible = buildAlwaysVisible(deviceInfo, iface.vpnInterfaces.firstOrNull()?.name),
                fixable       = fixable,
                deviceInfo    = deviceInfo,
                isScanning    = false
            ))
        }
    }.flowOn(Dispatchers.IO)

    private fun buildAlwaysVisible(deviceInfo: DeviceInfo, primaryInterface: String?) = listOf(
        CheckResult.AlwaysVisible(
            id          = "transport_vpn",
            title       = "Факт VPN",
            explanation = "Android ядро выставляет этот флаг когда VPN активен. " +
                    "Любое приложение с одним разрешением ACCESS_NETWORK_STATE видит его. " +
                    "Скрыть без root невозможно — это архитектура системы.",
            knowsWhat   = "VPN активен · ${primaryInterface ?: "tun0"} · Android ${deviceInfo.androidVersion}",
            doesntKnow  = "Какой сервер, куда, чьи ключи"
        ),
        CheckResult.AlwaysVisible(
            id          = "interface_detail",
            title       = "Сетевые интерфейсы",
            explanation = "java.net.NetworkInterface доступен без разрешений. " +
                    "Показывает все интерфейсы с именами и IP-адресами.",
            knowsWhat   = deviceInfo.vpnInterfaces.joinToString(" · ") { iface ->
                "${iface.name}: ${iface.addresses.joinToString(", ")}"
            }.ifBlank { "Интерфейсы не определены" },
            doesntKnow  = "Адрес VPN-сервера и ключи шифрования"
        ),
        CheckResult.AlwaysVisible(
            id          = "vpn_clients",
            title       = "Установленные VPN-клиенты",
            explanation = "Через блок <queries> в манифесте любое приложение может проверить " +
                    "наличие конкретных пакетов без QUERY_ALL_PACKAGES.",
            knowsWhat   = if (deviceInfo.installedVpnClients.isEmpty()) "Не обнаружены"
                          else deviceInfo.installedVpnClients.joinToString(", "),
            doesntKnow  = "Конфигурацию клиента"
        ),
        CheckResult.AlwaysVisible(
            id          = "http_probing",
            title       = "Доступность заблокированных сайтов",
            explanation = "Если VPN работает — заблокированные сайты открываются. " +
                    "Некоторые приложения проверяют это и делают вывод о наличии VPN. " +
                    "Защититься от этого метода без отключения VPN невозможно.",
            knowsWhat   = "Косвенный факт VPN через доступность сайтов",
            doesntKnow  = "Какой именно VPN-сервер используется"
        )
    )
}
