package com.stukachoff.domain.usecase

import com.stukachoff.data.network.SystemProxyAnalyzer
import com.stukachoff.domain.checker.AndroidVersionChecker
import com.stukachoff.domain.checker.DnsChecker
import com.stukachoff.domain.checker.InterfaceChecker
import com.stukachoff.domain.checker.PortScanner
import com.stukachoff.domain.checker.VpnStatusChecker
import com.stukachoff.domain.model.CheckResult
import com.stukachoff.domain.model.HarmSeverity
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
    private val androidVersionChecker: AndroidVersionChecker
) {
    fun scan(): Flow<ScanState> = flow {
        emit(ScanState(isScanning = true))

        val vpnStatus = vpnStatusChecker.check()
        if (vpnStatus != VpnStatus.USER_VPN) {
            emit(ScanState(vpnStatus = vpnStatus, isScanning = false))
            return@flow
        }

        emit(ScanState(vpnStatus = vpnStatus, isScanning = true))

        // Параллельный запуск всех проверок
        coroutineScope {
            val portResult    = async { portScanner.scan() }
            val ifaceResult   = async { interfaceChecker.check() }
            val dnsResult     = async { dnsChecker.check() }

            val ports   = portResult.await()
            val iface   = ifaceResult.await()
            val dns     = dnsResult.await()

            val fixable = buildList {
                add(androidVersionChecker.check())
                add(ports.proxyModeResult)
                add(ports.grpcApiResult)
                add(ports.clashApiResult)
                add(dns)
                add(iface.mtuResult)
                add(SystemProxyAnalyzer.check())
            }.sortedByDescending { it.harmSeverity.ordinal }

            emit(ScanState(
                vpnStatus = vpnStatus,
                alwaysVisible = buildAlwaysVisible(iface.vpnInterfaces.firstOrNull()?.name),
                fixable = fixable,
                isScanning = false
            ))
        }
    }.flowOn(Dispatchers.IO)

    private fun buildAlwaysVisible(interfaceName: String?) = listOf(
        CheckResult.AlwaysVisible(
            id = "transport_vpn",
            title = "Факт VPN",
            explanation = "Android ядро выставляет этот флаг когда VPN активен. " +
                    "Любое приложение с одним разрешением ACCESS_NETWORK_STATE видит его. " +
                    "Скрыть без root невозможно — это архитектура системы.",
            knowsWhat = "VPN активен",
            doesntKnow = "Какой сервер, куда, чьи ключи"
        ),
        CheckResult.AlwaysVisible(
            id = "interface_name",
            title = "Имя сетевого интерфейса",
            explanation = "java.net.NetworkInterface работает без каких-либо разрешений. " +
                    "Имя интерфейса ${interfaceName ?: "tun0"} идентифицирует тип клиента.",
            knowsWhat = "Тип VPN-клиента (${interfaceName ?: "tun0"})",
            doesntKnow = "Конфигурацию и адрес сервера"
        ),
        CheckResult.AlwaysVisible(
            id = "http_probing",
            title = "Доступность заблокированных сайтов",
            explanation = "Если VPN работает — заблокированные сайты становятся доступны. " +
                    "VK Max отправляет HTTP-пробы к таким сайтам и по ответам делает вывод о наличии VPN. " +
                    "Защититься от этого метода без отключения VPN невозможно.",
            knowsWhat = "Косвенный факт VPN через доступность сайтов",
            doesntKnow = "Какой именно VPN-сервер используется"
        )
    )
}
