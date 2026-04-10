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
        private const val FULL_SCAN_CONCURRENCY = 100  // Conservative for mobile fd limits
    }

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
                title = "API управления VPN",
                status = if (grpcOpen) CheckStatus.RED else CheckStatus.GREEN,
                harm = "IP сервера, UUID, ключи Reality → деанон пользователя + сервер в блоклист РКН",
                harmSeverity = HarmSeverity.CRITICAL,
                affectedClients = listOf("v2rayNG", "Hiddify", "NekoBox", "sing-box")
            ),
            clashApiResult = CheckResult.Fixable(
                id = "clash_api",
                title = "API конфигурации",
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

    /**
     * Целевое сканирование VPN-связанных портов (~3000 портов вместо 65535).
     * Сканирует: известные VPN-порты + системные + рандомную выборку.
     * Завершается за 30-60 секунд вместо 3+ минут.
     */
    override suspend fun fullScan(): List<OpenPort> = withContext(Dispatchers.IO) {
        val targetPorts = buildSet {
            // Известные VPN-порты (расширенный набор)
            addAll(PortCategorizer.grpcPorts)
            addAll(PortCategorizer.clashPorts)
            addAll(PortCategorizer.proxyPorts)
            // Системные порты 1-1024
            addAll(1..1024)
            // Расширенные xray/sing-box диапазоны
            addAll(2330..2340)    // Hiddify/NekoBox
            addAll(7888..7895)    // Clash
            addAll(8000..8100)    // HTTP servers
            addAll(9080..9100)    // Clash API
            addAll(10080..10090)  // xray gRPC
            addAll(10800..10815)  // v2rayNG
            addAll(19080..19090)  // Marzban
            addAll(23450..23460)  // Hiddify gRPC
            addAll(62780..62800)  // xray альтернатив
            // Популярные высокие порты
            addAll(listOf(15000, 20000, 25000, 30000, 35000, 40000, 45000, 50000, 55000, 60000))
            // Рандомная выборка из оставшихся (1000 портов)
            val random = java.util.Random(42)
            repeat(1000) {
                add(1025 + random.nextInt(64510))
            }
        }.sorted()

        val result = mutableListOf<OpenPort>()
        val semaphore = Semaphore(FULL_SCAN_CONCURRENCY)

        val chunks = targetPorts.chunked(500)
        for (chunk in chunks) {
            val chunkResults = coroutineScope {
                chunk.map { port ->
                    async {
                        semaphore.withPermit {
                            if (isPortOpen(port, FULL_SCAN_TIMEOUT_MS))
                                OpenPort(port, PortCategorizer.categorize(port), PortCategorizer.describe(port))
                            else null
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            result.addAll(chunkResults)
        }
        result
    }

    private fun isPortOpen(port: Int): Boolean = isPortOpen(port, KNOWN_PORT_TIMEOUT_MS)

    private fun isPortOpen(port: Int, timeoutMs: Int): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), timeoutMs)
            true
        }
    } catch (_: Exception) { false }
}
