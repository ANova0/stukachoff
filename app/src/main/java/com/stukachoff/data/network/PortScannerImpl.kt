package com.stukachoff.data.network

import com.stukachoff.domain.checker.OpenPort
import com.stukachoff.domain.checker.PortCategory
import com.stukachoff.domain.checker.PortCategorizer
import com.stukachoff.domain.checker.PortScanResult
import com.stukachoff.domain.checker.PortScanner
import com.stukachoff.domain.model.CheckResult
import com.stukachoff.domain.model.CheckStatus
import com.stukachoff.domain.model.HarmSeverity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class PortScannerImpl : PortScanner {

    private val connectTimeoutMs = 150

    override suspend fun scan(): PortScanResult = withContext(Dispatchers.IO) {
        val allKnownPorts = PortCategorizer.grpcPorts +
                PortCategorizer.clashPorts +
                PortCategorizer.proxyPorts

        val openPorts = allKnownPorts.mapNotNull { port ->
            if (isPortOpen(port)) {
                OpenPort(port, PortCategorizer.categorize(port), PortCategorizer.describe(port))
            } else null
        }

        val grpcOpen = openPorts.any { it.category == PortCategory.XRAY_GRPC }
        val clashOpen = openPorts.any { it.category == PortCategory.CLASH_API }
        val proxyOpen = openPorts.any {
            it.category in setOf(PortCategory.SOCKS5, PortCategory.HTTP_PROXY, PortCategory.MIXED)
        }

        PortScanResult(
            openKnownPorts = openPorts,
            grpcApiResult = CheckResult.Fixable(
                id = "grpc_api",
                title = "xray gRPC API",
                status = if (grpcOpen) CheckStatus.RED else CheckStatus.GREEN,
                harm = "IP сервера, UUID, ключи Reality → деанон пользователя + сервер в блоклист РКН",
                harmSeverity = HarmSeverity.CRITICAL,
                affectedClients = listOf("v2rayNG", "Hiddify", "NekoBox", "sing-box")
            ),
            clashApiResult = CheckResult.Fixable(
                id = "clash_api",
                title = "Clash / Mihomo REST API",
                status = if (clashOpen) CheckStatus.RED else CheckStatus.GREEN,
                harm = "История всех соединений и IP серверов доступны без пароля",
                harmSeverity = HarmSeverity.HIGH,
                affectedClients = listOf("Clash for Android", "FlClash", "sing-box")
            ),
            proxyModeResult = CheckResult.Fixable(
                id = "proxy_mode",
                title = "Режим VPN-клиента",
                status = when {
                    proxyOpen -> CheckStatus.RED
                    else -> CheckStatus.GREEN
                },
                harm = "Прокси-порт виден любому приложению без разрешений. Протокол идентифицирован.",
                harmSeverity = HarmSeverity.HIGH
            )
        )
    }

    suspend fun fullScan(): List<OpenPort> = withContext(Dispatchers.IO) {
        val FULL_SCAN_RANGE = 1024..65535
        val WORKERS = 50
        val TIMEOUT_MS = 100

        val portList = FULL_SCAN_RANGE.toList()
        val chunkSize = (portList.size / WORKERS) + 1
        val chunks = portList.chunked(chunkSize)

        chunks.flatMap { chunk ->
            chunk.mapNotNull { port ->
                if (isPortOpen(port, TIMEOUT_MS)) {
                    OpenPort(port, PortCategorizer.categorize(port), PortCategorizer.describe(port))
                } else null
            }
        }
    }

    private fun isPortOpen(port: Int): Boolean = isPortOpen(port, connectTimeoutMs)

    private fun isPortOpen(port: Int, timeoutMs: Int): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), timeoutMs)
            true
        }
    } catch (_: Exception) { false }
}
