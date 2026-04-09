package com.stukachoff.data.config

import com.stukachoff.domain.checker.OpenPort
import com.stukachoff.domain.checker.PortCategory
import com.stukachoff.domain.model.ConfigSource
import com.stukachoff.domain.model.OutboundConfig
import com.stukachoff.domain.model.VpnConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Активно сканирует ВСЕ найденные открытые порты на наличие xray gRPC API.
 * Пробует gRPC handshake (HTTP/2) на каждом порту.
 *
 * Два режима маркировки:
 * - Стандартный порт (10085, 19085, 23456) → 🔴 "Без усилий — любое приложение видит"
 * - Нестандартный порт (найден зондажем)   → 🟡 "Найден через активное сканирование"
 */
@Singleton
class ActiveGrpcScanner @Inject constructor(
    private val xrayConfigReader: XrayConfigReader
) {
    private val KNOWN_GRPC_PORTS = setOf(10085, 19085, 23456)

    private val probeClient = OkHttpClient.Builder()
        .protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE))
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()

    data class GrpcScanResult(
        val port: Int,
        val isKnownPort: Boolean,
        val config: VpnConfig?,
        val respondedToGrpc: Boolean
    )

    /**
     * Пробуем gRPC handshake на всех открытых портах.
     * Возвращает первый порт который ответил + прочитанный конфиг.
     */
    suspend fun scanAllPorts(openPorts: List<OpenPort>): GrpcScanResult? = withContext(Dispatchers.IO) {
        if (openPorts.isEmpty()) return@withContext null

        // Сначала пробуем известные gRPC порты (быстро)
        val knownGrpcPorts = openPorts.filter { it.port in KNOWN_GRPC_PORTS }
        for (port in knownGrpcPorts) {
            val result = tryGrpcOnPort(port.port)
            if (result != null) return@withContext GrpcScanResult(
                port = port.port,
                isKnownPort = true,
                config = result,
                respondedToGrpc = true
            )
        }

        // Затем пробуем ВСЕ остальные открытые порты (активный зондаж)
        val otherPorts = openPorts
            .filter { it.port !in KNOWN_GRPC_PORTS }
            .filter { it.category != PortCategory.CLASH_API } // Clash — отдельный протокол

        val semaphore = Semaphore(10) // не слишком агрессивно
        val results = coroutineScope {
            otherPorts.map { port ->
                async {
                    semaphore.withPermit {
                        val config = tryGrpcOnPort(port.port)
                        if (config != null) GrpcScanResult(
                            port = port.port,
                            isKnownPort = false,
                            config = config,
                            respondedToGrpc = true
                        ) else null
                    }
                }
            }.awaitAll().filterNotNull()
        }

        results.firstOrNull()
    }

    /**
     * Пробует gRPC HTTP/2 handshake на порту.
     * Если ответ содержит данные — читает конфиг через XrayConfigReader.
     */
    private suspend fun tryGrpcOnPort(port: Int): VpnConfig? {
        return runCatching {
            // Быстрая проверка — gRPC ли это вообще
            val body = ByteArray(5).toRequestBody("application/grpc".toMediaType())
            val request = Request.Builder()
                .url("http://127.0.0.1:$port/xray.app.proxyman.command.HandlerService/GetOutboundUsers")
                .post(body)
                .header("content-type", "application/grpc")
                .header("te", "trailers")
                .build()

            val response = probeClient.newCall(request).execute()
            val contentType = response.header("content-type") ?: ""

            if (!contentType.contains("grpc")) {
                response.close()
                return@runCatching null
            }

            // gRPC ответил — читаем полный конфиг
            response.close()
            val outbounds = xrayConfigReader.readOutbounds(port) ?: emptyList()
            if (outbounds.isNotEmpty()) VpnConfig(ConfigSource.XRAY_GRPC, outbounds) else null
        }.getOrNull()
    }
}
