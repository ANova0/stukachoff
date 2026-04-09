package com.stukachoff.data.config

import com.stukachoff.domain.model.OutboundConfig
import com.stukachoff.domain.model.TsupLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XrayConfigReader @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE))
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    suspend fun readOutbounds(port: Int): List<OutboundConfig>? = withContext(Dispatchers.IO) {
        runCatching {
            // Empty protobuf body for HandlerService/GetOutboundUsers
            val body = ByteArray(5).apply {
                // gRPC frame: compressed=0, length=0 (4 bytes big-endian)
                this[0] = 0x00 // not compressed
                // bytes 1-4: message length = 0
            }.toRequestBody("application/grpc".toMediaType())

            val request = Request.Builder()
                .url("http://127.0.0.1:$port/xray.app.proxyman.command.HandlerService/GetOutboundUsers")
                .post(body)
                .header("content-type", "application/grpc")
                .header("te", "trailers")
                .header("user-agent", "grpc-kotlin/1.0.0")
                .build()

            val response = client.newCall(request).execute()
            val bytes = response.body?.bytes()
            parseOutbounds(bytes)
        }.getOrNull()
    }

    fun parseOutbounds(bytes: ByteArray?): List<OutboundConfig> {
        if (bytes == null || bytes.isEmpty()) return emptyList()
        val text = bytes.toString(Charsets.ISO_8859_1)
        return extractOutbounds(text)
    }

    private fun extractOutbounds(text: String): List<OutboundConfig> {
        val uuidRegex    = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", RegexOption.IGNORE_CASE)
        val ipv4Regex    = Regex("""\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b""")
        val domainRegex  = Regex("""[a-zA-Z0-9][a-zA-Z0-9\-]{0,61}[a-zA-Z0-9]\.[a-zA-Z]{2,}(\.[a-zA-Z]{2,})?""")
        val base64Regex  = Regex("[A-Za-z0-9+/\\-_]{40,}={0,2}")

        val uuids   = uuidRegex.findAll(text).map { it.value }.toList()
        val ips     = ipv4Regex.findAll(text).map { it.value }
            .filter { !it.startsWith("127.") && !it.startsWith("0.") }.toList()
        val domains = domainRegex.findAll(text).map { it.value }
            .filter { !it.contains("grpc") && !it.contains("xray") }.toList()
        val keys    = base64Regex.findAll(text).map { it.value }.toList()

        if (uuids.isEmpty() && ips.isEmpty()) return emptyList()

        val transport = detectTransport(text)
        val security  = detectSecurity(text)
        val server    = ips.firstOrNull() ?: domains.firstOrNull() ?: "unknown"
        val sni       = domains.firstOrNull { it.contains(".") } ?: ""

        return listOf(OutboundConfig(
            protocol      = detectProtocol(text),
            serverAddress = server,
            serverPort    = detectPort(text, server),
            transport     = transport,
            security      = security,
            sni           = sni,
            uuid          = uuids.firstOrNull() ?: "",
            publicKey     = keys.firstOrNull(),
            tsupResistance = calculateTsup(transport, security)
        ))
    }

    private fun detectProtocol(text: String): String = when {
        text.contains("vless",        ignoreCase = true) -> "vless"
        text.contains("vmess",        ignoreCase = true) -> "vmess"
        text.contains("trojan",       ignoreCase = true) -> "trojan"
        text.contains("shadowsocks",  ignoreCase = true) -> "shadowsocks"
        else -> "unknown"
    }

    private fun detectTransport(text: String): String = when {
        text.contains("xhttp",      ignoreCase = true) -> "xhttp"
        text.contains("reality",    ignoreCase = true) -> "reality"
        text.contains("websocket",  ignoreCase = true) -> "ws"
        text.contains("grpc",       ignoreCase = true) -> "grpc"
        text.contains("http2",      ignoreCase = true) -> "h2"
        else -> "tcp"
    }

    private fun detectSecurity(text: String): String = when {
        text.contains("reality", ignoreCase = true) -> "reality"
        text.contains("tls",     ignoreCase = true) -> "tls"
        else -> "none"
    }

    private fun detectPort(text: String, server: String): Int {
        val portRegex = Regex(""":(\d{2,5})\b""")
        return portRegex.findAll(text).mapNotNull { it.groupValues[1].toIntOrNull() }
            .filter { it in 1..65535 && it != 80 }.firstOrNull() ?: 443
    }

    fun calculateTsup(transport: String, security: String): TsupLevel = when {
        security == "reality"                          -> TsupLevel.HIGH
        transport == "xhttp" && security == "tls"     -> TsupLevel.HIGH
        transport == "ws"    && security == "tls"     -> TsupLevel.MEDIUM
        security == "tls"                             -> TsupLevel.MEDIUM
        transport == "grpc"                           -> TsupLevel.LOW
        else                                          -> TsupLevel.LOW
    }
}
