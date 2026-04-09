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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class PortScannerImpl : PortScanner {

    companion object {
        private const val KNOWN_PORT_TIMEOUT_MS = 150
        private const val FULL_SCAN_TIMEOUT_MS  = 100
        private const val FULL_SCAN_CONCURRENCY = 200
    }

    private val connectTimeoutMs = KNOWN_PORT_TIMEOUT_MS

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

        // After identifying proxy ports, verify what protocol they actually are
        val proxyPortDetails = openPorts
            .filter { it.category in setOf(PortCategory.SOCKS5, PortCategory.HTTP_PROXY, PortCategory.MIXED) }
            .joinToString(", ") { op ->
                val proto = ProtocolVerifier.verify(op.port)
                "${op.port}=${proto.name}"
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
                title = "Прокси-порт",
                status = if (proxyOpen) CheckStatus.RED else CheckStatus.GREEN,
                harm = if (proxyOpen)
                    "Открыт прокси-порт: $proxyPortDetails — виден без разрешений"
                else
                    "Нет открытых прокси-портов",
                harmSeverity = if (proxyOpen) HarmSeverity.HIGH else HarmSeverity.INFO
            )
        )
    }

    override suspend fun fullScan(): List<OpenPort> = withContext(Dispatchers.IO) {
        val semaphore = Semaphore(FULL_SCAN_CONCURRENCY)
        coroutineScope {
            (1024..65535).map { port ->
                async {
                    semaphore.withPermit {
                        if (isPortOpen(port, FULL_SCAN_TIMEOUT_MS))
                            OpenPort(port, PortCategorizer.categorize(port), PortCategorizer.describe(port))
                        else null
                    }
                }
            }.awaitAll().filterNotNull()
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
