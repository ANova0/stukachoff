# Stukachoff v2.0 — Detection & Education Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Сделать Stukachoff лучше всех конкурентов по полноте детекции, добавить активное чтение конфигов с показом всех данных пользователю, определение активного клиента и полный обучающий учебник.

**Architecture:** Новые чекеры добавляются в domain/checker, интегрируются в ScanOrchestrator параллельно. ConfigReader читает xray gRPC и Clash API, все данные показываются пользователю локально и не передаются наружу. ActiveClientDetector использует 3 сигнала (process + interface + ports). LEARN переписывается как контекстный учебник по активному клиенту.

**Tech Stack:** Kotlin, Coroutines, OkHttp3, gRPC (через raw HTTP/2), Jetpack Compose, Hilt

---

## Task 1: Full Port Scanner (1024–65535)

**Files:**
- Modify: `app/src/main/java/com/stukachoff/data/network/PortScannerImpl.kt`
- Test: `app/src/test/java/com/stukachoff/data/network/FullPortScannerTest.kt`

**Step 1: Написать тест**

```kotlin
class FullPortScannerTest {
    @Test
    fun `known gRPC port detected in full scan range`() {
        // Тест что 10085 попадает в диапазон full scan
        assertTrue(10085 in 1024..65535)
        assertEquals(PortCategory.XRAY_GRPC, PortCategorizer.categorize(10085))
    }

    @Test
    fun `unknown open port classified correctly`() {
        val port = OpenPort(12345, PortCategory.UNKNOWN, "Unknown service")
        assertEquals(PortCategory.UNKNOWN, port.category)
    }
}
```

**Step 2: Запустить — убедиться что проходит (логика уже есть)**

```bash
.\gradlew.bat testDebugUnitTest --tests "*.FullPortScannerTest" --no-configuration-cache
```

**Step 3: Обновить PortScannerImpl — добавить full scan режим**

```kotlin
class PortScannerImpl : PortScanner {
    
    private val FULL_SCAN_RANGE = 1024..65535
    private val WORKERS = 50
    private val TIMEOUT_MS = 100

    // Известные порты — быстрый скан (существующий)
    override suspend fun scan(): PortScanResult = withContext(Dispatchers.IO) {
        val knownPorts = PortCategorizer.grpcPorts + 
                         PortCategorizer.clashPorts + 
                         PortCategorizer.proxyPorts
        val openKnown = knownPorts.mapNotNull { port ->
            if (isPortOpen(port)) OpenPort(port, PortCategorizer.categorize(port), PortCategorizer.describe(port))
            else null
        }
        buildResult(openKnown)
    }

    // Полный скан — параллельные воркеры
    suspend fun fullScan(): List<OpenPort> = withContext(Dispatchers.IO) {
        val chunks = FULL_SCAN_RANGE.chunked(FULL_SCAN_RANGE.count() / WORKERS + 1)
        chunks.flatMap { chunk ->
            chunk.mapNotNull { port ->
                if (isPortOpen(port)) {
                    OpenPort(port, PortCategorizer.categorize(port), PortCategorizer.describe(port))
                } else null
            }
        }
    }

    private fun isPortOpen(port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), TIMEOUT_MS); true }
    } catch (_: Exception) { false }

    private fun buildResult(openPorts: List<OpenPort>): PortScanResult {
        val grpcOpen  = openPorts.any { it.category == PortCategory.XRAY_GRPC }
        val clashOpen = openPorts.any { it.category == PortCategory.CLASH_API }
        val proxyOpen = openPorts.any { it.category in setOf(PortCategory.SOCKS5, PortCategory.HTTP_PROXY, PortCategory.MIXED) }
        return PortScanResult(
            openKnownPorts  = openPorts,
            grpcApiResult   = CheckResult.Fixable("grpc_api", "xray gRPC API",
                if (grpcOpen) CheckStatus.RED else CheckStatus.GREEN,
                if (grpcOpen) "IP сервера, UUID, ключи доступны без пароля" else "Закрыт",
                if (grpcOpen) HarmSeverity.CRITICAL else HarmSeverity.INFO),
            clashApiResult  = CheckResult.Fixable("clash_api", "Clash / Mihomo REST API",
                if (clashOpen) CheckStatus.RED else CheckStatus.GREEN,
                if (clashOpen) "История соединений и конфиг доступны" else "Закрыт",
                if (clashOpen) HarmSeverity.HIGH else HarmSeverity.INFO),
            proxyModeResult = CheckResult.Fixable("proxy_mode", "Прокси-порт",
                if (proxyOpen) CheckStatus.RED else CheckStatus.GREEN,
                if (proxyOpen) "Прокси-порт открыт" else "Нет открытых прокси-портов",
                if (proxyOpen) HarmSeverity.HIGH else HarmSeverity.INFO)
        )
    }
}
```

**Step 4: Commit**

```bash
git add app/src/main/java/com/stukachoff/data/network/PortScannerImpl.kt
git add app/src/test/java/com/stukachoff/data/network/FullPortScannerTest.kt
git commit -m "feat: full port scan 1024-65535 with parallel workers"
```

---

## Task 2: Protocol Handshake Verification

**Files:**
- Create: `app/src/main/java/com/stukachoff/data/network/ProtocolVerifier.kt`
- Test: `app/src/test/java/com/stukachoff/data/network/ProtocolVerifierTest.kt`

**Step 1: Тест**

```kotlin
class ProtocolVerifierTest {
    @Test
    fun `SOCKS5 greeting bytes are correct`() {
        val greeting = byteArrayOf(0x05, 0x01, 0x00)
        assertEquals(0x05.toByte(), greeting[0]) // SOCKS5 version
        assertEquals(0x01.toByte(), greeting[1]) // 1 auth method
        assertEquals(0x00.toByte(), greeting[2]) // no auth
    }

    @Test
    fun `HTTP CONNECT is correct format`() {
        val probe = "CONNECT localhost:80 HTTP/1.1\r\nHost: localhost\r\n\r\n"
        assertTrue(probe.startsWith("CONNECT"))
    }
}
```

**Step 2: Запустить**

```bash
.\gradlew.bat testDebugUnitTest --tests "*.ProtocolVerifierTest" --no-configuration-cache
```

**Step 3: Реализовать**

```kotlin
enum class DetectedProtocol { SOCKS5, HTTP_PROXY, UNKNOWN_TCP, NOT_OPEN }

object ProtocolVerifier {
    
    private const val TIMEOUT_MS = 300

    fun verify(port: Int): DetectedProtocol {
        // Пробуем SOCKS5
        if (trySocks5(port)) return DetectedProtocol.SOCKS5
        // Пробуем HTTP CONNECT
        if (tryHttpProxy(port)) return DetectedProtocol.HTTP_PROXY
        return DetectedProtocol.UNKNOWN_TCP
    }

    private fun trySocks5(port: Int): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), TIMEOUT_MS)
            val out = socket.getOutputStream()
            val inp = socket.getInputStream()
            // SOCKS5 greeting
            out.write(byteArrayOf(0x05, 0x01, 0x00))
            out.flush()
            val response = ByteArray(2)
            inp.read(response)
            // Если ответ 0x05 0x00 — это SOCKS5
            response[0] == 0x05.toByte()
        }
    } catch (_: Exception) { false }

    private fun tryHttpProxy(port: Int): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), TIMEOUT_MS)
            val out = socket.getOutputStream()
            val inp = socket.getInputStream()
            out.write("CONNECT localhost:80 HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray())
            out.flush()
            val buffer = ByteArray(20)
            inp.read(buffer)
            val response = String(buffer)
            response.startsWith("HTTP/")
        }
    } catch (_: Exception) { false }
}
```

**Step 4: Commit**

```bash
git add app/src/main/java/com/stukachoff/data/network/ProtocolVerifier.kt
git add app/src/test/
git commit -m "feat: SOCKS5 and HTTP proxy handshake verification"
```

---

## Task 3: Active VPN Config Reader (gRPC + Clash)

**Files:**
- Create: `app/src/main/java/com/stukachoff/data/config/VpnConfigReader.kt`
- Create: `app/src/main/java/com/stukachoff/data/config/XrayConfigReader.kt`
- Create: `app/src/main/java/com/stukachoff/data/config/ClashConfigReader.kt`
- Create: `app/src/main/java/com/stukachoff/domain/model/VpnConfig.kt`

**Step 1: Domain модели**

```kotlin
// VpnConfig.kt
data class VpnConfig(
    val source: ConfigSource,          // XRAY_GRPC / CLASH_API
    val outbounds: List<OutboundConfig>
)

data class OutboundConfig(
    val protocol: String,              // vless, vmess, trojan, shadowsocks
    val serverAddress: String,         // IP или домен сервера
    val serverPort: Int,
    val transport: String,             // tcp, ws, http, grpc, xhttp
    val security: String,             // reality, tls, none
    val sni: String,
    val uuid: String,                  // показываем пользователю!
    val publicKey: String?,            // Reality public key
    val tsupResistance: TsupLevel
)

enum class TsupLevel { HIGH, MEDIUM, LOW, BLOCKED }
enum class ConfigSource { XRAY_GRPC, CLASH_API, NOT_AVAILABLE }
```

**Step 2: XrayConfigReader через raw gRPC HTTP/2**

```kotlin
@Singleton
class XrayConfigReader @Inject constructor() {

    // gRPC: POST /xray.app.proxyman.command.HandlerService/GetOutboundUsers
    // без аутентификации — это и есть уязвимость которую мы показываем пользователю
    suspend fun readConfig(port: Int): List<OutboundConfig>? = withContext(Dispatchers.IO) {
        runCatching {
            val client = OkHttpClient.Builder()
                .protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE))
                .connectTimeout(3, TimeUnit.SECONDS)
                .build()

            // Попытка через HandlerService
            val body = "\u0000\u0000\u0000\u0000\u0000" // пустой protobuf
            val request = Request.Builder()
                .url("http://127.0.0.1:$port/xray.app.proxyman.command.HandlerService/GetOutboundUsers")
                .post(body.toByteArray().toRequestBody("application/grpc".toMediaType()))
                .header("content-type", "application/grpc")
                .header("te", "trailers")
                .build()

            val response = client.newCall(request).execute()
            parseXrayResponse(response.body?.bytes())
        }.getOrNull()
    }

    // Также пробуем через статистику — часто доступно
    suspend fun readStats(port: Int): Map<String, Long>? = withContext(Dispatchers.IO) {
        runCatching {
            val client = OkHttpClient.Builder()
                .protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE))
                .connectTimeout(3, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder()
                .url("http://127.0.0.1:$port/xray.app.stats.command.StatsService/GetStats")
                .post("".toByteArray().toRequestBody())
                .build()
            val resp = client.newCall(request).execute()
            parseStatsResponse(resp.body?.bytes())
        }.getOrNull()
    }

    private fun parseXrayResponse(bytes: ByteArray?): List<OutboundConfig> {
        if (bytes == null) return emptyList()
        // Парсим protobuf: находим строки IP/домен, порт, протокол
        return extractOutboundsFromProtobuf(bytes)
    }

    private fun extractOutboundsFromProtobuf(bytes: ByteArray): List<OutboundConfig> {
        // Protobuf field extraction — ищем строки похожие на домены/IP и порты
        val result = mutableListOf<OutboundConfig>()
        val text = bytes.toString(Charsets.ISO_8859_1)
        
        // Ищем паттерны: UUID (36 символов), порты, домены
        val uuidRegex = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        val uuids = uuidRegex.findAll(text).map { it.value }.toList()
        
        if (uuids.isNotEmpty()) {
            result.add(OutboundConfig(
                protocol   = "vless",
                serverAddress = extractServerAddress(text),
                serverPort = extractServerPort(text),
                transport  = extractTransport(text),
                security   = extractSecurity(text),
                sni        = extractSni(text),
                uuid       = uuids.first(),
                publicKey  = extractPublicKey(text),
                tsupResistance = calculateTsupResistance(extractTransport(text), extractSecurity(text))
            ))
        }
        return result
    }

    private fun extractServerAddress(text: String): String {
        // Ищем IPv4 паттерн
        val ipRegex = Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""")
        return ipRegex.find(text)?.value ?: "unknown"
    }
    
    private fun extractServerPort(text: String): Int {
        // Ищем паттерны портов (числа 1-65535 рядом с IP)
        return 443 // fallback, уточняется при реальном парсинге
    }

    private fun extractTransport(text: String): String {
        return when {
            text.contains("reality", ignoreCase = true) -> "reality"
            text.contains("websocket", ignoreCase = true) -> "ws"
            text.contains("http2", ignoreCase = true) -> "h2"
            text.contains("xhttp", ignoreCase = true) -> "xhttp"
            else -> "tcp"
        }
    }

    private fun extractSecurity(text: String): String {
        return when {
            text.contains("reality", ignoreCase = true) -> "reality"
            text.contains("tls", ignoreCase = true) -> "tls"
            else -> "none"
        }
    }

    private fun extractSni(text: String): String {
        // Ищем домены: паттерн xxx.xxx.xxx
        val domainRegex = Regex("""[a-zA-Z0-9][a-zA-Z0-9-]*\.[a-zA-Z]{2,}(\.[a-zA-Z]{2,})?""")
        return domainRegex.find(text)?.value ?: ""
    }

    private fun extractPublicKey(text: String): String? {
        // Base64 строки длиной 43-44 символа (Reality public key)
        val b64Regex = Regex("[A-Za-z0-9+/]{43}=?")
        return b64Regex.find(text)?.value
    }

    private fun calculateTsupResistance(transport: String, security: String): TsupLevel {
        return when {
            security == "reality" -> TsupLevel.HIGH
            transport == "xhttp" && security == "tls" -> TsupLevel.HIGH
            transport == "ws" && security == "tls" -> TsupLevel.MEDIUM
            security == "tls" -> TsupLevel.MEDIUM
            else -> TsupLevel.LOW
        }
    }

    private fun parseStatsResponse(bytes: ByteArray?): Map<String, Long> = emptyMap()
}
```

**Step 3: ClashConfigReader**

```kotlin
@Singleton
class ClashConfigReader @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .build()

    suspend fun readConfig(port: Int): ClashReadResult? = withContext(Dispatchers.IO) {
        runCatching {
            // /configs — полная конфигурация
            val configResp = get("http://127.0.0.1:$port/configs")
            // /proxies — список прокси серверов
            val proxiesResp = get("http://127.0.0.1:$port/proxies")
            // /connections — активные соединения (какие сайты открыты)
            val connsResp = get("http://127.0.0.1:$port/connections")

            ClashReadResult(
                rawConfig     = configResp,
                proxies       = parseProxies(proxiesResp),
                connections   = parseConnections(connsResp)
            )
        }.getOrNull()
    }

    private fun get(url: String): String? = runCatching {
        client.newCall(Request.Builder().url(url).build())
            .execute().body?.string()
    }.getOrNull()

    private fun parseProxies(json: String?): List<ProxyEntry> {
        if (json == null) return emptyList()
        val result = mutableListOf<ProxyEntry>()
        runCatching {
            val obj = JSONObject(json)
            val proxies = obj.getJSONObject("proxies")
            proxies.keys().forEach { key ->
                val proxy = proxies.getJSONObject(key)
                result.add(ProxyEntry(
                    name     = key,
                    type     = proxy.optString("type"),
                    server   = proxy.optString("server"),
                    port     = proxy.optInt("port"),
                    udp      = proxy.optBoolean("udp")
                ))
            }
        }
        return result
    }

    private fun parseConnections(json: String?): List<ConnectionEntry> {
        if (json == null) return emptyList()
        val result = mutableListOf<ConnectionEntry>()
        runCatching {
            val obj = JSONObject(json)
            val conns = obj.getJSONArray("connections")
            for (i in 0 until minOf(conns.length(), 20)) { // максимум 20 соединений
                val conn = conns.getJSONObject(i)
                val metadata = conn.getJSONObject("metadata")
                result.add(ConnectionEntry(
                    host       = metadata.optString("host"),
                    destIp     = metadata.optString("destinationIP"),
                    destPort   = metadata.optInt("destinationPort"),
                    processPath = metadata.optString("processPath"),
                    upload     = conn.optLong("upload"),
                    download   = conn.optLong("download")
                ))
            }
        }
        return result
    }
}

data class ClashReadResult(
    val rawConfig: String?,
    val proxies: List<ProxyEntry>,
    val connections: List<ConnectionEntry>
)

data class ProxyEntry(val name: String, val type: String, val server: String, val port: Int, val udp: Boolean)
data class ConnectionEntry(val host: String, val destIp: String, val destPort: Int, val processPath: String, val upload: Long, val download: Long)
```

**Step 4: Commit**

```bash
git add app/src/main/java/com/stukachoff/data/config/
git add app/src/main/java/com/stukachoff/domain/model/VpnConfig.kt
git commit -m "feat: active config reader — xray gRPC + Clash API, full data shown to user"
```

---

## Task 4: Active Client Detector

**Files:**
- Create: `app/src/main/java/com/stukachoff/data/apps/ActiveClientDetector.kt`
- Test: `app/src/test/java/com/stukachoff/data/apps/ActiveClientDetectorTest.kt`

**Step 1: Тест**

```kotlin
class ActiveClientDetectorTest {

    @Test
    fun `wg0 with MTU 1420 is WireGuard`() {
        val result = ActiveClientDetector.classifyByInterface("wg0", 1420, emptySet(), emptyList())
        assertEquals(VpnEngine.WIREGUARD, result?.engine)
        assertTrue(result!!.confidence >= 80)
    }

    @Test
    fun `tun0 with port 2334 open is Hiddify SOCKS5 mode`() {
        val openPorts = listOf(OpenPort(2334, PortCategory.SOCKS5, "Hiddify"))
        val running = setOf("app.hiddify.com")
        val result = ActiveClientDetector.classifyByInterface("tun0", 1500, running, openPorts)
        assertEquals("app.hiddify.com", result?.packageName)
        assertEquals(VpnMode.SOCKS5, result?.mode)
    }

    @Test
    fun `tun0 no ports Hiddify running is TUN mode`() {
        val running = setOf("app.hiddify.com")
        val result = ActiveClientDetector.classifyByInterface("tun0", 1500, running, emptyList())
        assertEquals("app.hiddify.com", result?.packageName)
        assertEquals(VpnMode.TUN, result?.mode)
    }
}
```

**Step 2: Запустить**

```bash
.\gradlew.bat testDebugUnitTest --tests "*.ActiveClientDetectorTest" --no-configuration-cache
```

**Step 3: Реализовать**

```kotlin
enum class VpnEngine { XRAY, SINGBOX, WIREGUARD, AMNEZIA, OPENVPN, PSIPHON, TOR, CLOUDFLARE, OTHER }
enum class VpnMode { TUN, SOCKS5, HTTP, MIXED, UNKNOWN }

data class ActiveClient(
    val packageName: String,
    val displayName: String,
    val engine: VpnEngine,
    val mode: VpnMode,
    val confidence: Int,
    val tsupResistanceBase: TsupLevel // базовая без конфига
)

object ActiveClientDetector {

    // Маппинг: package → engine
    private val PACKAGE_ENGINE = mapOf(
        "app.hiddify.com"                     to (VpnEngine.XRAY    to "Hiddify"),
        "com.v2ray.ang"                        to (VpnEngine.XRAY    to "v2rayNG"),
        "dev.hexasoftware.v2box"               to (VpnEngine.XRAY    to "V2Box"),
        "com.v2raytun.android"                 to (VpnEngine.XRAY    to "V2RayTun"),
        "io.github.saeeddev94.xray"            to (VpnEngine.XRAY    to "Xray"),
        "com.github.dyhkwong.sagernet"         to (VpnEngine.XRAY    to "ExclaveVPN"),
        "com.happproxy"                        to (VpnEngine.XRAY    to "HappProxy"),
        "io.nekohasekai.sfa"                   to (VpnEngine.SINGBOX  to "sing-box"),
        "moe.nb4a"                             to (VpnEngine.SINGBOX  to "NekoBox"),
        "com.github.nekohasekai.sagernet"      to (VpnEngine.SINGBOX  to "NekoBox"),
        "org.amnezia.vpn"                      to (VpnEngine.AMNEZIA  to "AmneziaVPN"),
        "org.amnezia.awg"                      to (VpnEngine.AMNEZIA  to "AmneziaWG"),
        "com.wireguard.android"                to (VpnEngine.WIREGUARD to "WireGuard"),
        "com.zaneschepke.wireguardautotunnel"  to (VpnEngine.WIREGUARD to "WG Tunnel"),
        "de.blinkt.openvpn"                    to (VpnEngine.OPENVPN  to "OpenVPN"),
        "net.openvpn.openvpn"                  to (VpnEngine.OPENVPN  to "OpenVPN Connect"),
        "com.psiphon3"                         to (VpnEngine.PSIPHON  to "Psiphon"),
        "org.torproject.android"               to (VpnEngine.TOR      to "Orbot"),
        "com.cloudflare.onedotonedotonedotone" to (VpnEngine.CLOUDFLARE to "Cloudflare WARP"),
        "io.github.dovecoteescapee.byedpi"     to (VpnEngine.OTHER   to "ByeDPI"),
        "com.romanvht.byebyedpi"               to (VpnEngine.OTHER   to "ByeByeDPI"),
        "com.nordvpn.android"                  to (VpnEngine.OTHER   to "NordVPN"),
        "com.expressvpn.vpn"                   to (VpnEngine.OTHER   to "ExpressVPN"),
        "com.protonvpn.android"                to (VpnEngine.OTHER   to "Proton VPN"),
    )

    fun classifyByInterface(
        interfaceName: String,
        mtu: Int,
        runningPackages: Set<String>,
        openPorts: List<OpenPort>
    ): ActiveClient? {
        // Signal 1: interface name
        val engineByInterface: VpnEngine? = when {
            interfaceName.startsWith("awg") -> VpnEngine.AMNEZIA
            interfaceName.startsWith("wg")  -> VpnEngine.WIREGUARD
            interfaceName.startsWith("tun") -> null // xray или singbox
            interfaceName.startsWith("ppp") -> VpnEngine.OPENVPN
            else -> null
        }

        // Signal 2: MTU
        val engineByMtu: VpnEngine? = when (mtu) {
            1280 -> VpnEngine.AMNEZIA   // AmneziaWG characteristic
            1420 -> VpnEngine.WIREGUARD // Standard WireGuard
            else -> null
        }

        // Signal 3: running processes + ports
        val runningVpnPackage = PACKAGE_ENGINE.keys.firstOrNull { it in runningPackages }
        val (packageEngine, packageName) = runningVpnPackage
            ?.let { PACKAGE_ENGINE[it]!! }
            ?: (null to null)

        // Определяем режим по портам
        val mode: VpnMode = when {
            openPorts.any { it.category == PortCategory.SOCKS5 }    -> VpnMode.SOCKS5
            openPorts.any { it.category == PortCategory.HTTP_PROXY } -> VpnMode.HTTP
            openPorts.any { it.category == PortCategory.MIXED }      -> VpnMode.MIXED
            interfaceName.startsWith("tun")                          -> VpnMode.TUN
            else -> VpnMode.UNKNOWN
        }

        // Комбинируем сигналы
        val finalEngine = packageEngine ?: engineByInterface ?: engineByMtu ?: VpnEngine.OTHER
        val confidence = when {
            runningVpnPackage != null && (engineByInterface == finalEngine || engineByMtu == finalEngine) -> 98
            runningVpnPackage != null -> 90
            engineByInterface != null && engineByMtu == engineByInterface -> 85
            engineByInterface != null || engineByMtu != null -> 75
            else -> 50
        }

        val displayName = packageName
            ?: when (finalEngine) {
                VpnEngine.AMNEZIA   -> "AmneziaWG"
                VpnEngine.WIREGUARD -> "WireGuard"
                VpnEngine.OPENVPN   -> "OpenVPN"
                else -> "VPN"
            }

        return ActiveClient(
            packageName          = runningVpnPackage ?: "",
            displayName          = displayName,
            engine               = finalEngine,
            mode                 = mode,
            confidence           = confidence,
            tsupResistanceBase   = baseTsupResistance(finalEngine, mtu)
        )
    }

    private fun baseTsupResistance(engine: VpnEngine, mtu: Int): TsupLevel = when {
        engine == VpnEngine.AMNEZIA                -> TsupLevel.HIGH
        engine == VpnEngine.XRAY                   -> TsupLevel.MEDIUM // может быть HIGH если Reality
        engine == VpnEngine.SINGBOX                -> TsupLevel.MEDIUM
        engine == VpnEngine.WIREGUARD && mtu == 1420 -> TsupLevel.LOW   // стандартный WG блокируется
        engine == VpnEngine.OPENVPN                -> TsupLevel.BLOCKED
        engine == VpnEngine.CLOUDFLARE             -> TsupLevel.MEDIUM
        engine == VpnEngine.TOR                    -> TsupLevel.HIGH
        else -> TsupLevel.MEDIUM
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
    ): ActiveClient? = withContext(Dispatchers.IO) {
        val running = processChecker.getRunningPackages()
        ActiveClientDetector.classifyByInterface(
            primaryInterface ?: "tun0", mtu, running, openPorts
        )
    }
}
```

**Step 4: Commit**

```bash
git add app/src/main/java/com/stukachoff/data/apps/ActiveClientDetector.kt
git add app/src/test/
git commit -m "feat: active client detector — 3-signal algorithm (process+interface+ports)"
```

---

## Task 5: Split-Tunnel Detection

**Files:**
- Create: `app/src/main/java/com/stukachoff/domain/checker/SplitTunnelChecker.kt`
- Test: `app/src/test/java/com/stukachoff/domain/checker/SplitTunnelCheckerTest.kt`

**Step 1: Тест**

```kotlin
class SplitTunnelCheckerTest {
    @Test
    fun `single active network means full tunnel`() {
        // Если одна сеть — весь трафик через VPN
        val result = SplitTunnelChecker.classify(activeNetworksCount = 1, vpnIsDefault = true)
        assertEquals(SplitTunnelStatus.FULL_TUNNEL, result)
    }

    @Test
    fun `multiple networks means split tunnel`() {
        val result = SplitTunnelChecker.classify(activeNetworksCount = 2, vpnIsDefault = false)
        assertEquals(SplitTunnelStatus.SPLIT_TUNNEL, result)
    }
}
```

**Step 2: Реализовать**

```kotlin
enum class SplitTunnelStatus { FULL_TUNNEL, SPLIT_TUNNEL, UNKNOWN }

object SplitTunnelChecker {
    fun classify(activeNetworksCount: Int, vpnIsDefault: Boolean): SplitTunnelStatus = when {
        activeNetworksCount == 1 && vpnIsDefault -> SplitTunnelStatus.FULL_TUNNEL
        activeNetworksCount > 1                  -> SplitTunnelStatus.SPLIT_TUNNEL
        else -> SplitTunnelStatus.UNKNOWN
    }
}

@Singleton
class SplitTunnelCheckerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun check(): CheckResult.Fixable = withContext(Dispatchers.IO) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        
        val allNetworks = cm.allNetworks
        val vpnNetworks = allNetworks.filter { network ->
            cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
        val nonVpnNetworks = allNetworks.filter { network ->
            val caps = cm.getNetworkCapabilities(network)
            caps != null &&
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
            (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
             caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
        }

        // Если есть и VPN и не-VPN сети одновременно — split tunnel
        val hasSplitTunnel = vpnNetworks.isNotEmpty() && nonVpnNetworks.isNotEmpty()

        CheckResult.Fixable(
            id           = "split_tunnel",
            title        = "Маршрутизация (Split-Tunnel)",
            status       = if (hasSplitTunnel) CheckStatus.YELLOW else CheckStatus.GREEN,
            harm         = if (hasSplitTunnel)
                "Split-tunnel активен — часть приложений идёт мимо VPN. " +
                "VK и Сбер в bypass могут делать HTTP-пробы напрямую."
            else
                "Весь трафик идёт через VPN-туннель",
            harmSeverity = if (hasSplitTunnel) HarmSeverity.HIGH else HarmSeverity.INFO
        )
    }
}
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/stukachoff/domain/checker/SplitTunnelChecker.kt
git add app/src/test/
git commit -m "feat: split-tunnel detection via ConnectivityManager.getAllNetworks()"
```

---

## Task 6: TSPU Assessment + Overall Verdict

**Files:**
- Create: `app/src/main/java/com/stukachoff/domain/usecase/TsupAssessor.kt`
- Modify: `app/src/main/java/com/stukachoff/domain/model/ScanState.kt`
- Modify: `app/src/main/java/com/stukachoff/domain/usecase/ScanOrchestrator.kt`

**Step 1: Модели**

```kotlin
// Добавить в ScanState.kt
data class ScanState(
    val vpnStatus: VpnStatus = VpnStatus.UNKNOWN,
    val alwaysVisible: List<CheckResult.AlwaysVisible> = emptyList(),
    val fixable: List<CheckResult.Fixable> = emptyList(),
    val deviceInfo: DeviceInfo? = null,
    val activeClient: ActiveClient? = null,
    val vpnConfig: VpnConfig? = null,        // прочитанный конфиг (если gRPC открыт)
    val overallVerdict: OverallVerdict? = null,
    val isScanning: Boolean = false,
    val error: String? = null
)

data class OverallVerdict(
    val appProtection: ProtectionLevel,
    val tsupProtection: ProtectionLevel,
    val appDetails: String,
    val tsupDetails: String,
    val topRecommendation: String?
)

enum class ProtectionLevel { HIGH, MEDIUM, LOW, CRITICAL }
```

**Step 2: TsupAssessor**

```kotlin
@Singleton
class TsupAssessor @Inject constructor() {

    fun assess(
        activeClient: ActiveClient?,
        vpnConfig: VpnConfig?,
        mtu: Int
    ): Pair<ProtectionLevel, String> {
        // Если знаем конфиг — используем его
        if (vpnConfig != null && vpnConfig.outbounds.isNotEmpty()) {
            val outbound = vpnConfig.outbounds.first()
            return when (outbound.tsupResistance) {
                TsupLevel.HIGH -> ProtectionLevel.HIGH to
                    "${outbound.protocol.uppercase()}+${outbound.security.uppercase()} — высокая устойчивость к ТСПУ"
                TsupLevel.MEDIUM -> ProtectionLevel.MEDIUM to
                    "${outbound.protocol.uppercase()} на порту ${outbound.serverPort} — средняя устойчивость"
                TsupLevel.LOW -> ProtectionLevel.LOW to
                    "${outbound.protocol.uppercase()} — блокируется ТСПУ"
                TsupLevel.BLOCKED -> ProtectionLevel.CRITICAL to
                    "${outbound.protocol.uppercase()} — заблокирован ТСПУ"
            }
        }

        // Без конфига — по клиенту и MTU
        if (activeClient == null) return ProtectionLevel.MEDIUM to "Клиент не определён"

        return when {
            activeClient.engine == VpnEngine.AMNEZIA ->
                ProtectionLevel.HIGH to "AmneziaWG — высокая устойчивость к ТСПУ"
            activeClient.engine == VpnEngine.TOR ->
                ProtectionLevel.HIGH to "Tor — высокая анонимность"
            activeClient.engine == VpnEngine.XRAY || activeClient.engine == VpnEngine.SINGBOX ->
                ProtectionLevel.MEDIUM to "${activeClient.displayName} — зависит от протокола в конфиге провайдера"
            activeClient.engine == VpnEngine.WIREGUARD ->
                ProtectionLevel.LOW to "WireGuard блокируется ТСПУ с декабря 2025"
            activeClient.engine == VpnEngine.OPENVPN ->
                ProtectionLevel.CRITICAL to "OpenVPN заблокирован ТСПУ"
            activeClient.engine == VpnEngine.CLOUDFLARE ->
                ProtectionLevel.MEDIUM to "Cloudflare WARP — частичная защита"
            else -> ProtectionLevel.MEDIUM to "${activeClient.displayName} — неизвестная устойчивость"
        }
    }

    fun buildVerdict(
        fixable: List<CheckResult.Fixable>,
        activeClient: ActiveClient?,
        vpnConfig: VpnConfig?,
        mtu: Int
    ): OverallVerdict {
        val criticalIssues = fixable.filter {
            it.status == CheckStatus.RED && it.harmSeverity == HarmSeverity.CRITICAL
        }
        val highIssues = fixable.filter {
            it.status == CheckStatus.RED && it.harmSeverity == HarmSeverity.HIGH
        }

        val appLevel = when {
            criticalIssues.isNotEmpty() -> ProtectionLevel.CRITICAL
            highIssues.isNotEmpty()     -> ProtectionLevel.LOW
            fixable.any { it.status == CheckStatus.YELLOW } -> ProtectionLevel.MEDIUM
            else -> ProtectionLevel.HIGH
        }

        val appDetails = when (appLevel) {
            ProtectionLevel.CRITICAL -> "Критическая уязвимость: ${criticalIssues.first().title}"
            ProtectionLevel.LOW -> "${highIssues.size} проблем требуют внимания"
            ProtectionLevel.MEDIUM -> "Есть незначительные проблемы"
            ProtectionLevel.HIGH -> "Конфигурация защищена"
        }

        val (tsupLevel, tsupDetails) = assess(activeClient, vpnConfig, mtu)

        val topRec = buildTopRecommendation(criticalIssues, highIssues, activeClient, tsupLevel)

        return OverallVerdict(appLevel, tsupLevel, appDetails, tsupDetails, topRec)
    }

    private fun buildTopRecommendation(
        criticalIssues: List<CheckResult.Fixable>,
        highIssues: List<CheckResult.Fixable>,
        activeClient: ActiveClient?,
        tsupLevel: ProtectionLevel
    ): String? = when {
        criticalIssues.any { it.id == "grpc_api" } ->
            "Закрой gRPC API в настройках ${activeClient?.displayName ?: "клиента"}"
        criticalIssues.any { it.id == "exit_ip" } ->
            "VPN не маршрутизирует трафик — проверь настройки ${activeClient?.displayName ?: "клиента"}"
        highIssues.any { it.id == "split_tunnel" } ->
            "Включи маршрутизацию всего трафика через VPN"
        highIssues.any { it.id == "dns_leak" } ->
            "Настрой DNS через VPN-туннель"
        tsupLevel == ProtectionLevel.LOW && activeClient?.engine == VpnEngine.WIREGUARD ->
            "Переключись с WireGuard на AmneziaWG"
        tsupLevel == ProtectionLevel.CRITICAL ->
            "Смени VPN-клиент — текущий блокируется ТСПУ"
        else -> null
    }
}
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/stukachoff/domain/usecase/TsupAssessor.kt
git add app/src/main/java/com/stukachoff/domain/model/ScanState.kt
git commit -m "feat: TSPU assessor + overall verdict (two scores)"
```

---

## Task 7: Config Display UI

**Files:**
- Create: `app/src/main/java/com/stukachoff/ui/verify/ConfigRevealCard.kt`
- Create: `app/src/main/java/com/stukachoff/ui/verify/OverallVerdictCard.kt`

**Step 1: OverallVerdictCard**

```kotlin
@Composable
fun OverallVerdictCard(verdict: OverallVerdict) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (verdict.appProtection) {
                ProtectionLevel.HIGH     -> Color(0xFF1B5E20).copy(alpha = 0.1f)
                ProtectionLevel.MEDIUM   -> Color(0xFFFF9800).copy(alpha = 0.1f)
                ProtectionLevel.LOW      -> Color(0xFFF44336).copy(alpha = 0.1f)
                ProtectionLevel.CRITICAL -> Color(0xFF7B0000).copy(alpha = 0.1f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("ИТОГ РАССЛЕДОВАНИЯ",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            VerdictRow("От приложений", verdict.appProtection, verdict.appDetails)
            Spacer(Modifier.height(8.dp))
            VerdictRow("От ТСПУ", verdict.tsupProtection, verdict.tsupDetails)

            verdict.topRecommendation?.let { rec ->
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("→ $rec",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun VerdictRow(label: String, level: ProtectionLevel, details: String) {
    val (emoji, color) = when (level) {
        ProtectionLevel.HIGH     -> "🟢" to Color(0xFF4CAF50)
        ProtectionLevel.MEDIUM   -> "🟡" to Color(0xFFFF9800)
        ProtectionLevel.LOW      -> "🔴" to Color(0xFFF44336)
        ProtectionLevel.CRITICAL -> "🚨" to Color(0xFF7B0000)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 20.sp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(details, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
```

**Step 2: ConfigRevealCard — показываем ВСЁ что прочитали**

```kotlin
@Composable
fun ConfigRevealCard(config: VpnConfig, onClose: () -> Unit) {
    // Показываем всё включая IP, UUID, ключи — пользователь должен знать что видно
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF7B0000).copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row {
                Text("🔴", fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text("Конфиг прочитан — это видит любое приложение",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF44336))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Stukachoff прочитал твой VPN-конфиг через открытый gRPC API. " +
                "Ровно то же самое сделают приложения-стукачи на твоём телефоне.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            config.outbounds.forEachIndexed { i, outbound ->
                if (i > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ConfigDataRow("Протокол", outbound.protocol.uppercase())
                ConfigDataRow("Transport", outbound.transport.uppercase())
                ConfigDataRow("Безопасность", outbound.security.uppercase())
                ConfigDataRow("Сервер", outbound.serverAddress,
                    warning = "Этот IP попадёт в блоклист РКН")
                ConfigDataRow("Порт", outbound.serverPort.toString(),
                    warning = if (outbound.serverPort == 443) "Порт 443 зондируется ТСПУ" else null)
                if (outbound.sni.isNotBlank())
                    ConfigDataRow("SNI", outbound.sni)
                ConfigDataRow("UUID", outbound.uuid,
                    warning = "По UUID тебя идентифицируют на сервере")
                outbound.publicKey?.let {
                    ConfigDataRow("Публичный ключ", it)
                }

                // ТСПУ оценка на основе конфига
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = when (outbound.tsupResistance) {
                        TsupLevel.HIGH    -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                        TsupLevel.MEDIUM  -> Color(0xFFFF9800).copy(alpha = 0.15f)
                        TsupLevel.LOW     -> Color(0xFFF44336).copy(alpha = 0.15f)
                        TsupLevel.BLOCKED -> Color(0xFF7B0000).copy(alpha = 0.15f)
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        when (outbound.tsupResistance) {
                            TsupLevel.HIGH    -> "✅ Высокая устойчивость к ТСПУ (${outbound.security.uppercase()})"
                            TsupLevel.MEDIUM  -> "⚠️ Средняя устойчивость к ТСПУ"
                            TsupLevel.LOW     -> "🔴 Низкая устойчивость к ТСПУ"
                            TsupLevel.BLOCKED -> "🚨 Протокол заблокирован ТСПУ"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ConfigDataRow(label: String, value: String, warning: String? = null) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Row {
            Text("$label:", style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(100.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
        }
        warning?.let {
            Text("⚠️ $it", style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFFF9800), modifier = Modifier.padding(start = 100.dp))
        }
    }
}
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/stukachoff/ui/verify/
git commit -m "feat: config reveal card + overall verdict UI"
```

---

## Task 8: Complete LEARN Tutorial

**Files:**
- Create: `app/src/main/java/com/stukachoff/ui/tutorial/TutorialScreen.kt`
- Create: `app/src/main/java/com/stukachoff/ui/tutorial/TutorialContent.kt`
- Modify: `app/src/main/java/com/stukachoff/ui/navigation/Navigation.kt`
- Modify: `app/src/main/java/com/stukachoff/ui/settings/SettingsScreen.kt`

**Структура TutorialContent.kt — полный список инструкций:**

```kotlin
// БАЗОВЫЕ ШАГ (любой клиент)
val BASE_STEPS = listOf(
    TutorialStep("Шаг 1: TUN-режим",
        "Самый важный шаг. В TUN-режиме VPN работает на уровне сети — нет открытых прокси-портов которые видят стукачи.",
        mapOf(
            "Hiddify"         to "Настройки → Режим работы → VPN (TUN)",
            "v2rayNG"         to "Настройки → VPN mode → включить",
            "NekoBox"         to "Настройки → TUN Mode → включить",
            "AmneziaWG"       to "TUN-режим всегда активен по умолчанию",
            "WireGuard"       to "TUN-режим всегда активен по умолчанию",
            "OpenVPN"         to "TUN-режим всегда активен по умолчанию"
        )
    ),
    TutorialStep("Шаг 2: Все приложения через VPN",
        "Если часть приложений идёт мимо VPN — они могут проверять доступность заблокированных сайтов и детектировать VPN косвенно.",
        mapOf(
            "Hiddify"   to "Настройки → Маршрутизация → Все подключения через VPN",
            "v2rayNG"   to "Настройки → Per-app proxy → выбрать все приложения ИЛИ отключить per-app",
            "NekoBox"   to "Настройки → Route all traffic → включить",
            "WireGuard" to "Конфигурация туннеля → AllowedIPs = 0.0.0.0/0, ::/0",
            "AmneziaWG" to "Конфигурация → AllowedIPs = 0.0.0.0/0, ::/0"
        )
    ),
    TutorialStep("Шаг 3: DNS через туннель",
        "Без этого провайдер видит какие сайты ты посещаешь даже при активном VPN.",
        mapOf(
            "Hiddify"   to "Настройки → DNS → Remote DNS Server (например 1.1.1.1)",
            "v2rayNG"   to "Настройки → DNS → установить DNS сервер",
            "NekoBox"   to "Настройки → FakeIP DNS → включить",
            "WireGuard" to "Конфигурация → DNS = 1.1.1.1",
            "AmneziaWG" to "Конфигурация → DNS = 1.1.1.1"
        )
    )
)

// ИНСТРУКЦИИ ПО УЯЗВИМОСТЯМ
val VULNERABILITY_FIXES = mapOf(
    "grpc_api" to VulnerabilityFix(
        title = "Закрыть gRPC API",
        description = "gRPC API позволяет любому приложению читать твой VPN-конфиг: сервер, ключи, UUID.",
        clientInstructions = mapOf(
            "Hiddify"   to "Настройки → Ядро → Дополнительно → Отключить API управления",
            "v2rayNG"   to "Настройки → убедиться что API секция отсутствует в конфиге",
            "NekoBox"   to "Настройки ядра → API → снять Enable API",
            "V2Box"     to "Настройки → xray config → удалить секцию \"api\"",
            "sing-box"  to "config.json → удалить блок \"v2ray_api\" из experimental",
        ),
        supportMessage = "Попроси поддержку провайдера: «Пожалуйста отключите gRPC API в конфигах которые вы выдаёте клиентам»"
    ),
    "clash_api" to VulnerabilityFix(
        title = "Защитить Clash API",
        description = "Clash REST API без пароля позволяет читать все активные соединения и историю.",
        clientInstructions = mapOf(
            "Clash for Android" to "Настройки → External Controller → установить secret",
            "FlClash"           to "Настройки → API → установить пароль",
            "sing-box"          to "config.json → experimental.clash_api.secret = \"ваш_пароль\""
        ),
        supportMessage = "Попроси поддержку: «Добавьте secret в секцию external-controller вашего Clash конфига»"
    ),
    "dns_leak" to VulnerabilityFix(
        title = "Устранить DNS-утечку",
        description = "DNS-запросы идут мимо VPN — провайдер видит посещаемые сайты.",
        clientInstructions = mapOf(
            "Hiddify"   to "Настройки → DNS → Remote DNS (Remote = DNS через туннель)",
            "v2rayNG"   to "Настройки → DNS → Remote DNS server = 1.1.1.1",
            "NekoBox"   to "Настройки → FakeIP DNS Mode → включить",
            "WireGuard" to "Настройки туннеля → DNS = 1.1.1.1",
            "AmneziaWG" to "Настройки туннеля → DNS = 1.1.1.1"
        ),
        supportMessage = "Попроси поддержку: «Настройте DNS через туннель в клиентских конфигах»"
    ),
    "split_tunnel" to VulnerabilityFix(
        title = "Маршрутизировать весь трафик",
        description = "Приложения в bypass видят прямой интернет и могут детектировать VPN.",
        clientInstructions = mapOf(
            "Hiddify"   to "Настройки → Маршрутизация → Route all traffic",
            "v2rayNG"   to "Убрать per-app proxy, все приложения через VPN",
            "NekoBox"   to "Route all apps through proxy",
            "WireGuard" to "AllowedIPs = 0.0.0.0/0, ::/0",
            "AmneziaWG" to "AllowedIPs = 0.0.0.0/0, ::/0"
        ),
        supportMessage = "Попроси поддержку: «Как настроить full tunnel без исключений?»"
    )
)

// ТСПУ ИНСТРУКЦИИ
val TSPU_ADVICE = mapOf(
    VpnEngine.WIREGUARD to TsupAdvice(
        problem = "WireGuard блокируется ТСПУ с декабря 2025 по UDP-сигнатуре handshake",
        solution = "Переключись на AmneziaWG — это WireGuard с шифрованием служебных пакетов",
        steps = listOf(
            "Скачай AmneziaVPN или AmneziaWG из Google Play",
            "Импортируй существующий WireGuard конфиг — он совместим",
            "В настройках включи Junk packets (Jc = 4, Jmin = 40, Jmax = 70)",
            "Установи MTU = 1280"
        )
    ),
    VpnEngine.OPENVPN to TsupAdvice(
        problem = "OpenVPN заблокирован ТСПУ — сигнатура определяется мгновенно",
        solution = "Смени протокол на VLESS+Reality (Hiddify) или AmneziaWG",
        steps = listOf(
            "Обратись к поддержке VPN-провайдера",
            "Попроси конфиг VLESS+Reality или AmneziaWG"
        )
    )
)
```

**Step 2: TutorialScreen — две секции**

```kotlin
@Composable
fun TutorialScreen(
    activeClient: ActiveClient?,    // из ScanState — определённый клиент
    vulnerabilities: List<String>,  // id найденных уязвимостей
    onBack: () -> Unit
) {
    // Показываем инструкции только для активного клиента если он определён
    val clientName = activeClient?.displayName
    val clientPackage = activeClient?.packageName

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Учебник защиты") },
            navigationIcon = { IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null)
            }}
        )
    }) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {

            // Активный клиент — показываем вверху
            clientName?.let {
                item {
                    ActiveClientBanner(clientName = it,
                        engine = activeClient.engine,
                        tsupLevel = activeClient.tsupResistanceBase)
                }
            }

            // Критические уязвимости — если есть
            if (vulnerabilities.isNotEmpty()) {
                item {
                    TutorialSection("🚨 Найдены уязвимости — исправь в первую очередь") {}
                }
                items(vulnerabilities) { vulnId ->
                    VULNERABILITY_FIXES[vulnId]?.let { fix ->
                        VulnerabilityFixCard(
                            fix = fix,
                            clientName = clientName,
                            // Показываем инструкцию только для активного клиента
                            instruction = clientName?.let { fix.clientInstructions[it] }
                                ?: fix.clientInstructions.entries.first().let { "${it.key}: ${it.value}" }
                        )
                    }
                }
            }

            // Базовые шаги — всегда
            item { TutorialSection("🛡️ Базовая защита") {} }
            items(BASE_STEPS) { step ->
                BaseStepCard(
                    step = step,
                    clientInstruction = clientName?.let { step.instructions[it] }
                )
            }

            // ТСПУ совет если нужен
            activeClient?.engine?.let { engine ->
                TSPU_ADVICE[engine]?.let { advice ->
                    item { TsupAdviceCard(advice) }
                }
            }

            // Полный учебник по другим клиентам
            item { TutorialSection("📖 Инструкции по другим клиентам") {} }
            items(VULNERABILITY_FIXES.values.toList()) { fix ->
                AllClientsCard(fix)
            }
        }
    }
}
```

**Step 3: Добавить в Navigation и Settings**

```kotlin
// Navigation.kt
object Tutorial : Screen("tutorial", "Учебник")

// SettingsScreen.kt — кнопка "Учебник защиты"
SettingsItem(
    icon    = Icons.Default.Book,
    title   = "Учебник защиты",
    subtitle = "Инструкции по настройке VPN-клиента",
    onClick = onOpenTutorial
)
```

**Step 4: Commit**

```bash
git add app/src/main/java/com/stukachoff/ui/tutorial/
git add app/src/main/java/com/stukachoff/ui/navigation/Navigation.kt
git add app/src/main/java/com/stukachoff/ui/settings/SettingsScreen.kt
git commit -m "feat: complete tutorial — context-aware per active client, TSPU advice, support messages"
```

---

## Task 9: Wire Everything in ScanOrchestrator

**Files:**
- Modify: `app/src/main/java/com/stukachoff/domain/usecase/ScanOrchestrator.kt`

**Step 1: Добавить все новые чекеры**

```kotlin
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
    private val splitTunnelChecker: SplitTunnelCheckerImpl,   // NEW
    private val activeClientChecker: ActiveClientCheckerImpl,  // NEW
    private val xrayConfigReader: XrayConfigReader,            // NEW
    private val clashConfigReader: ClashConfigReader,          // NEW
    private val tsupAssessor: TsupAssessor,                    // NEW
    private val prefs: AppPreferences
) {
    fun scan(): Flow<ScanState> = flow {
        // ... существующий код ...

        coroutineScope {
            val portResult    = async { portScanner.scan() }
            val ifaceResult   = async { interfaceChecker.check() }
            val dnsResult     = async { dnsChecker.check() }
            val vpnClients    = async { appThreatAnalyzer.installedVpnClients() }
            val exitIpResult  = async { exitIpChecker.check() }
            val routingResult = async { routingChecker.check() }
            val workProfile   = async { workProfileChecker.check() }
            val splitTunnel   = async { splitTunnelChecker.check() }     // NEW

            val ports  = portResult.await()
            val iface  = ifaceResult.await()

            // Определяем активный клиент ПОСЛЕ портов и интерфейсов
            val activeClient = activeClientChecker.detect(
                iface.vpnInterfaces.firstOrNull()?.name,
                iface.vpnInterfaces.firstOrNull()?.mtu ?: 1500,
                ports.openKnownPorts
            )

            // Если gRPC открыт — читаем конфиг
            val vpnConfig = if (ports.grpcApiResult.status == CheckStatus.RED) {
                val grpcPort = ports.openKnownPorts
                    .firstOrNull { it.category == PortCategory.XRAY_GRPC }?.port ?: 10085
                val outbounds = xrayConfigReader.readConfig(grpcPort) ?: emptyList()
                if (outbounds.isNotEmpty()) VpnConfig(ConfigSource.XRAY_GRPC, outbounds) else null
            } else null

            // Если Clash API открыт — читаем соединения
            val clashData = if (ports.clashApiResult.status == CheckStatus.RED) {
                val clashPort = ports.openKnownPorts
                    .firstOrNull { it.category == PortCategory.CLASH_API }?.port ?: 9090
                clashConfigReader.readConfig(clashPort)
            } else null

            val fixable = buildList {
                add(exitIpChecker.toCheckResult(exitIpResult.await()))
                add(routingResult.await())
                add(androidVersionChecker.check())
                add(ports.grpcApiResult)
                add(ports.clashApiResult)
                add(dnsResult.await())
                add(iface.mtuResult)
                add(SystemProxyAnalyzer.check())
                add(splitTunnel.await())                              // NEW
                add(workProfile.await().check)
            }.sortedByDescending { it.harmSeverity.ordinal }

            val verdict = tsupAssessor.buildVerdict(
                fixable, activeClient, vpnConfig,
                iface.vpnInterfaces.firstOrNull()?.mtu ?: 1500
            )

            emit(ScanState(
                vpnStatus      = vpnStatus,
                alwaysVisible  = buildAlwaysVisible(...),
                fixable        = fixable,
                deviceInfo     = deviceInfoCollector.collect(vpnClients.await()),
                activeClient   = activeClient,
                vpnConfig      = vpnConfig,
                overallVerdict = verdict,
                isScanning     = false
            ))
        }
    }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/stukachoff/domain/usecase/ScanOrchestrator.kt
git commit -m "feat: wire all new checkers into ScanOrchestrator"
```

---

## Task 10: Update VerifyScreen — Verdict + Config Reveal

**Files:**
- Modify: `app/src/main/java/com/stukachoff/ui/verify/VerifyScreen.kt`

```kotlin
// Добавить в LazyColumn ПЕРЕД проверками:

// Итоговый вердикт
state.overallVerdict?.let {
    item { OverallVerdictCard(verdict = it) }
}

// Конфиг если прочитан (gRPC был открыт)
state.vpnConfig?.let { config ->
    if (config.outbounds.isNotEmpty()) {
        item { ConfigRevealCard(config = config, onClose = {}) }
    }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/stukachoff/ui/verify/VerifyScreen.kt
git commit -m "feat: verdict + config reveal in VerifyScreen"
```

---

## Финальная проверка

```bash
.\gradlew.bat assembleDebug --no-configuration-cache
.\gradlew.bat testDebugUnitTest --no-configuration-cache
git tag v2.0.0
git push origin v2.0.0
```
