# Stukachoff — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Android-приложение для верификации защиты VPN-пользователей в России — показывает что видят враждебные приложения и как это исправить.

**Architecture:** MVVM + Clean Architecture. Domain-слой — чистый Kotlin (unit-тестируемый). Data-слой — Android-зависимые реализации. UI-слой — Jetpack Compose. Два build flavor: `core` (без INTERNET) и `full` (с INTERNET для exit IP + обновления контента).

**Tech Stack:** Kotlin 2.0, Jetpack Compose, Material 3, Coroutines + Flow, Hilt (DI), OkHttp (только full), JUnit4 + MockK, AGP 8.4

---

## Структура проекта

```
app/
├── src/
│   ├── main/
│   │   ├── java/com/stukachoff/
│   │   │   ├── domain/
│   │   │   │   ├── model/          ← чистые data классы
│   │   │   │   ├── checker/        ← интерфейсы проверок
│   │   │   │   └── usecase/        ← ScanOrchestrator
│   │   │   ├── data/
│   │   │   │   ├── network/        ← NetworkInfoRepository
│   │   │   │   ├── apps/           ← AppRepository
│   │   │   │   └── content/        ← ContentRepository (threats.json)
│   │   │   ├── ui/
│   │   │   │   ├── verify/
│   │   │   │   ├── threats/
│   │   │   │   ├── learn/
│   │   │   │   └── common/         ← компоненты, глоссарий
│   │   │   └── di/                 ← Hilt модули
│   │   └── AndroidManifest.xml
│   ├── core/                       ← flavor: только ACCESS_NETWORK_STATE
│   ├── full/                       ← flavor: + INTERNET
│   └── test/                       ← unit тесты
├── build.gradle.kts
└── threats.json                    ← встроенный контент
```

---

## Task 1: Project Scaffold

**Files:**
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/core/AndroidManifest.xml`
- Create: `app/src/full/AndroidManifest.xml`

**Step 1: Создать Android проект**

В Android Studio: New Project → Empty Activity → Kotlin + Compose.
Package: `com.stukachoff`
MinSdk: 24, TargetSdk: 35

**Step 2: Настроить build.gradle.kts**

```kotlin
android {
    namespace = "com.stukachoff"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.stukachoff"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    flavorDimensions += "network"
    productFlavors {
        create("core") {
            dimension = "network"
            applicationIdSuffix = ".core"
        }
        create("full") {
            dimension = "network"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("com.google.dagger:hilt-android:2.51")
    kapt("com.google.dagger:hilt-compiler:2.51")

    // Только для full flavor
    "fullImplementation"("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
}
```

**Step 3: Манифест core flavor**

```xml
<!-- app/src/core/AndroidManifest.xml -->
<manifest>
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>

    <queries>
        <package android:name="app.hiddify.com"/>
        <package android:name="com.v2ray.ang"/>
        <package android:name="com.github.nekohasekai.sagernet"/>
        <package android:name="io.nekohasekai.sagernet"/>
        <package android:name="com.clash.mini"/>
        <package android:name="com.github.shadowsocks"/>
        <package android:name="org.amnezia.vpn"/>
        <package android:name="org.amnezia.awg"/>
        <package android:name="de.blinkt.openvpn"/>
        <package android:name="com.zaneschepke.wireguardautotunnel"/>
        <!-- known threat apps -->
        <package android:name="com.vk.vkclient"/>
        <package android:name="ru.sberbankmobile"/>
        <package android:name="ru.tinkoff.banking.new"/>
        <package android:name="ru.ozon.app.android"/>
        <package android:name="com.avito.android"/>
        <package android:name="com.hh.android"/>
    </queries>
</manifest>
```

**Step 4: Манифест full flavor**

```xml
<!-- app/src/full/AndroidManifest.xml -->
<manifest>
    <uses-permission android:name="android.permission.INTERNET"/>
</manifest>
```

**Step 5: Commit**

```bash
git add .
git commit -m "feat: project scaffold with core/full flavors"
```

---

## Task 2: Domain Models

**Files:**
- Create: `app/src/main/java/com/stukachoff/domain/model/CheckResult.kt`
- Create: `app/src/main/java/com/stukachoff/domain/model/ScanState.kt`
- Create: `app/src/main/java/com/stukachoff/domain/model/AppThreat.kt`
- Create: `app/src/main/java/com/stukachoff/domain/model/ThreatLevel.kt`
- Test: `app/src/test/java/com/stukachoff/domain/model/CheckResultTest.kt`

**Step 1: Написать тест**

```kotlin
class CheckResultTest {
    @Test
    fun `fixable check with red status requires fix`() {
        val check = CheckResult.Fixable(
            id = "grpc_api",
            title = "gRPC API открыт",
            status = CheckStatus.RED,
            harm = "IP сервера → РКН → блоклист",
            harmSeverity = HarmSeverity.CRITICAL
        )
        assertTrue(check.requiresFix)
    }

    @Test
    fun `always visible check has no status`() {
        val check = CheckResult.AlwaysVisible(
            id = "transport_vpn",
            title = "Факт VPN",
            knowsWhat = "VPN есть",
            doesntKnow = "Какой и куда"
        )
        assertNull(check.status)
    }
}
```

**Step 2: Запустить тест — убедиться что падает**

```bash
./gradlew :app:testCoreDebugUnitTest --tests "*.CheckResultTest" -q
```
Ожидание: FAIL — классы не существуют

**Step 3: Реализовать модели**

```kotlin
// CheckResult.kt
sealed class CheckResult {
    abstract val id: String
    abstract val title: String

    data class AlwaysVisible(
        override val id: String,
        override val title: String,
        val explanation: String,
        val knowsWhat: String,
        val doesntKnow: String
    ) : CheckResult() {
        val status: CheckStatus? = null
    }

    data class Fixable(
        override val id: String,
        override val title: String,
        val status: CheckStatus,
        val harm: String,
        val harmSeverity: HarmSeverity,
        val affectedClients: List<String> = emptyList()
    ) : CheckResult() {
        val requiresFix: Boolean get() = status == CheckStatus.RED || status == CheckStatus.YELLOW
    }
}

enum class CheckStatus { GREEN, YELLOW, RED }

enum class HarmSeverity { INFO, MEDIUM, HIGH, CRITICAL }

// ScanState.kt
data class ScanState(
    val vpnStatus: VpnStatus = VpnStatus.UNKNOWN,
    val alwaysVisible: List<CheckResult.AlwaysVisible> = emptyList(),
    val fixable: List<CheckResult.Fixable> = emptyList(),
    val isScanning: Boolean = false,
    val error: String? = null
)

enum class VpnStatus { UNKNOWN, NOT_ACTIVE, USER_VPN, CORPORATE_VPN }

// AppThreat.kt
data class AppThreat(
    val packageName: String,
    val appName: String,
    val version: String?,
    val threatLevel: ThreatLevel,
    val isInstalled: Boolean,
    val confirmedMethods: List<DetectionMethod>,
    val possibleMethods: List<DetectionMethod>,
    val harm: String,
    val source: String? = null
)

enum class ThreatLevel { RED, YELLOW, GREY }

enum class DetectionMethod {
    TRANSPORT_VPN,
    INTERFACE_NAME,
    HTTP_PROBING,
    PLMN_MCC,
    PACKAGE_SCAN,
    LOCALHOST_SCAN,
    GEOLOCATION
}
```

**Step 4: Запустить тест — убедиться что проходит**

```bash
./gradlew :app:testCoreDebugUnitTest --tests "*.CheckResultTest" -q
```
Ожидание: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/stukachoff/domain/model/
git add app/src/test/java/com/stukachoff/domain/model/
git commit -m "feat: domain models — CheckResult, ScanState, AppThreat"
```

---

## Task 3: VPN Status Checker

**Files:**
- Create: `app/src/main/java/com/stukachoff/domain/checker/VpnStatusChecker.kt`
- Create: `app/src/main/java/com/stukachoff/data/network/VpnStatusCheckerImpl.kt`
- Test: `app/src/test/java/com/stukachoff/domain/checker/VpnStatusCheckerTest.kt`

**Step 1: Написать тест**

```kotlin
class VpnStatusCheckerTest {
    private val checker = mockk<VpnStatusChecker>()

    @Test
    fun `returns NOT_ACTIVE when no VPN transport`() = runTest {
        coEvery { checker.check() } returns VpnStatus.NOT_ACTIVE
        assertEquals(VpnStatus.NOT_ACTIVE, checker.check())
    }

    @Test
    fun `returns USER_VPN for VpnService-based VPN`() = runTest {
        coEvery { checker.check() } returns VpnStatus.USER_VPN
        assertEquals(VpnStatus.USER_VPN, checker.check())
    }
}
```

**Step 2: Запустить — убедиться что падает**

```bash
./gradlew :app:testCoreDebugUnitTest --tests "*.VpnStatusCheckerTest" -q
```

**Step 3: Реализовать интерфейс и имплементацию**

```kotlin
// VpnStatusChecker.kt
interface VpnStatusChecker {
    suspend fun check(): VpnStatus
}

// VpnStatusCheckerImpl.kt
class VpnStatusCheckerImpl(
    private val context: Context
) : VpnStatusChecker {

    override suspend fun check(): VpnStatus = withContext(Dispatchers.IO) {
        val cm = context.getSystemService(ConnectivityManager::class.java)

        // Проверяем все сети
        val allNetworks = cm.allNetworks
        for (network in allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                // Android 12+: определяем тип VPN
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val info = caps.transportInfo
                    if (info is VpnTransportInfo) {
                        return@withContext when (info.type) {
                            VpnManager.TYPE_VPN_PLATFORM -> VpnStatus.CORPORATE_VPN
                            else -> VpnStatus.USER_VPN
                        }
                    }
                }
                return@withContext VpnStatus.USER_VPN
            }
        }
        VpnStatus.NOT_ACTIVE
    }
}
```

**Step 4: Запустить — убедиться что проходит**

```bash
./gradlew :app:testCoreDebugUnitTest --tests "*.VpnStatusCheckerTest" -q
```

**Step 5: Commit**

```bash
git add app/src/main/java/com/stukachoff/domain/checker/VpnStatusChecker.kt
git add app/src/main/java/com/stukachoff/data/network/VpnStatusCheckerImpl.kt
git add app/src/test/
git commit -m "feat: VPN status checker — USER_VPN vs CORPORATE_VPN vs NOT_ACTIVE"
```

---

## Task 4: Interface & MTU Checker

**Files:**
- Create: `app/src/main/java/com/stukachoff/domain/checker/InterfaceChecker.kt`
- Create: `app/src/main/java/com/stukachoff/data/network/InterfaceCheckerImpl.kt`
- Test: `app/src/test/java/com/stukachoff/domain/checker/InterfaceCheckerTest.kt`

**Step 1: Написать тест**

```kotlin
class InterfaceCheckerTest {

    @Test
    fun `detects VPN interface by name tun0`() {
        val checker = InterfaceCheckerImpl()
        // Мокаем NetworkInterface через reflection или wrapper
        val result = checker.classifyInterfaceName("tun0")
        assertEquals(InterfaceType.TUN, result)
    }

    @Test
    fun `detects WireGuard interface`() {
        val checker = InterfaceCheckerImpl()
        assertEquals(InterfaceType.WIREGUARD, checker.classifyInterfaceName("wg0"))
    }

    @Test
    fun `standard ethernet not flagged`() {
        val checker = InterfaceCheckerImpl()
        assertEquals(InterfaceType.NORMAL, checker.classifyInterfaceName("eth0"))
    }

    @Test
    fun `MTU 1420 detected as WireGuard signature`() {
        assertEquals(MtuSignature.WIREGUARD, MtuAnalyzer.analyze(1420))
    }

    @Test
    fun `MTU 1500 is clean`() {
        assertEquals(MtuSignature.STANDARD, MtuAnalyzer.analyze(1500))
    }
}
```

**Step 2: Запустить — убедиться что падает**

```bash
./gradlew :app:testCoreDebugUnitTest --tests "*.InterfaceCheckerTest" -q
```

**Step 3: Реализовать**

```kotlin
// InterfaceChecker.kt
interface InterfaceChecker {
    suspend fun check(): InterfaceCheckResult
}

data class InterfaceCheckResult(
    val vpnInterfaces: List<VpnInterface>,
    val mtuResult: CheckResult.Fixable
)

data class VpnInterface(val name: String, val type: InterfaceType, val mtu: Int)

enum class InterfaceType { TUN, WIREGUARD, TAP, PPP, IPSEC, NORMAL }

enum class MtuSignature { STANDARD, WIREGUARD, AMNEZIA, LOW_ANOMALY }

// InterfaceCheckerImpl.kt
class InterfaceCheckerImpl : InterfaceChecker {

    fun classifyInterfaceName(name: String): InterfaceType {
        val n = name.lowercase()
        return when {
            n.startsWith("tun") -> InterfaceType.TUN
            n.startsWith("wg") -> InterfaceType.WIREGUARD
            n.startsWith("tap") -> InterfaceType.TAP
            n.startsWith("ppp") -> InterfaceType.PPP
            n.startsWith("ipsec") || n.startsWith("xfrm") -> InterfaceType.IPSEC
            else -> InterfaceType.NORMAL
        }
    }

    override suspend fun check(): InterfaceCheckResult = withContext(Dispatchers.IO) {
        val interfaces = runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
        }.getOrDefault(emptyList())

        val vpnIfaces = interfaces
            .filter { classifyInterfaceName(it.name) != InterfaceType.NORMAL }
            .map { VpnInterface(it.name, classifyInterfaceName(it.name), it.mtu) }

        val mtu = vpnIfaces.minOfOrNull { it.mtu } ?: 1500
        val mtuStatus = when (MtuAnalyzer.analyze(mtu)) {
            MtuSignature.STANDARD -> CheckStatus.GREEN
            MtuSignature.WIREGUARD -> CheckStatus.YELLOW
            else -> CheckStatus.RED
        }

        InterfaceCheckResult(
            vpnInterfaces = vpnIfaces,
            mtuResult = CheckResult.Fixable(
                id = "mtu",
                title = "MTU fingerprint",
                status = mtuStatus,
                harm = "ТСПУ идентифицирует тип VPN",
                harmSeverity = HarmSeverity.MEDIUM
            )
        )
    }
}

object MtuAnalyzer {
    fun analyze(mtu: Int): MtuSignature = when (mtu) {
        1500 -> MtuSignature.STANDARD
        1420 -> MtuSignature.WIREGUARD
        1280 -> MtuSignature.AMNEZIA
        else -> if (mtu < 1400) MtuSignature.LOW_ANOMALY else MtuSignature.STANDARD
    }
}
```

**Step 4: Запустить — убедиться что проходит**

```bash
./gradlew :app:testCoreDebugUnitTest --tests "*.InterfaceCheckerTest" -q
```

**Step 5: Commit**

```bash
git add app/src/main/java/com/stukachoff/domain/checker/InterfaceChecker.kt
git add app/src/main/java/com/stukachoff/data/network/InterfaceCheckerImpl.kt
git add app/src/test/
git commit -m "feat: interface + MTU checker"
```

---

## Task 5: Localhost Port Scanner

**Files:**
- Create: `app/src/main/java/com/stukachoff/domain/checker/PortScanner.kt`
- Create: `app/src/main/java/com/stukachoff/data/network/PortScannerImpl.kt`
- Test: `app/src/test/java/com/stukachoff/domain/checker/PortScannerTest.kt`

**Step 1: Написать тест**

```kotlin
class PortScannerTest {

    @Test
    fun `known xray gRPC port flagged as critical`() {
        val port = KnownPort(10085, PortCategory.XRAY_GRPC, "xray gRPC API")
        assertEquals(HarmSeverity.CRITICAL, port.severity)
    }

    @Test
    fun `known SOCKS5 port flagged as high`() {
        val port = KnownPort(10808, PortCategory.SOCKS5, "v2rayNG SOCKS5")
        assertEquals(HarmSeverity.HIGH, port.severity)
    }

    @Test
    fun `port categorizer returns XRAY_GRPC for 10085`() {
        assertEquals(PortCategory.XRAY_GRPC, PortCategorizer.categorize(10085))
    }

    @Test
    fun `port categorizer returns CLASH_API for 9090`() {
        assertEquals(PortCategory.CLASH_API, PortCategorizer.categorize(9090))
    }
}
```

**Step 2: Запустить — убедиться что падает**

```bash
./gradlew :app:testCoreDebugUnitTest --tests "*.PortScannerTest" -q
```

**Step 3: Реализовать**

```kotlin
// PortScanner.kt
interface PortScanner {
    suspend fun scan(): PortScanResult
}

data class PortScanResult(
    val openKnownPorts: List<OpenPort>,
    val grpcApiResult: CheckResult.Fixable,
    val clashApiResult: CheckResult.Fixable,
    val proxyModeResult: CheckResult.Fixable
)

data class OpenPort(val port: Int, val category: PortCategory, val description: String)

enum class PortCategory { XRAY_GRPC, CLASH_API, SOCKS5, HTTP_PROXY, MIXED, UNKNOWN }

// PortCategorizer.kt
object PortCategorizer {
    private val knownPorts = mapOf(
        10085 to (PortCategory.XRAY_GRPC to "xray gRPC API"),
        19085 to (PortCategory.XRAY_GRPC to "xray gRPC API (Marzban)"),
        23456 to (PortCategory.XRAY_GRPC to "xray gRPC API (Hiddify)"),
        9090  to (PortCategory.CLASH_API to "Clash REST API"),
        9091  to (PortCategory.CLASH_API to "Clash REST API"),
        19090 to (PortCategory.CLASH_API to "Clash REST API"),
        10808 to (PortCategory.SOCKS5 to "v2rayNG SOCKS5"),
        10809 to (PortCategory.HTTP_PROXY to "v2rayNG HTTP"),
        7891  to (PortCategory.SOCKS5 to "Clash SOCKS5"),
        7890  to (PortCategory.HTTP_PROXY to "Clash HTTP"),
        2334  to (PortCategory.SOCKS5 to "Hiddify/NekoBox SOCKS5"),
        2333  to (PortCategory.HTTP_PROXY to "Hiddify HTTP"),
        1080  to (PortCategory.MIXED to "sing-box Mixed"),
    )

    fun categorize(port: Int): PortCategory =
        knownPorts[port]?.first ?: PortCategory.UNKNOWN

    fun describe(port: Int): String =
        knownPorts[port]?.second ?: "Unknown service"

    val grpcPorts = setOf(10085, 19085, 23456)
    val clashPorts = setOf(9090, 9091, 19090)
    val proxyPorts = setOf(10808, 10809, 7891, 7890, 2334, 2333, 1080)
}

// PortScannerImpl.kt
class PortScannerImpl : PortScanner {

    private val timeout = 150 // ms

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
                harm = "IP сервера, UUID, ключи Reality → деанон + блоклист РКН",
                harmSeverity = HarmSeverity.CRITICAL
            ),
            clashApiResult = CheckResult.Fixable(
                id = "clash_api",
                title = "Clash / Mihomo REST API",
                status = if (clashOpen) CheckStatus.RED else CheckStatus.GREEN,
                harm = "История соединений, IP серверов",
                harmSeverity = HarmSeverity.HIGH
            ),
            proxyModeResult = CheckResult.Fixable(
                id = "proxy_mode",
                title = "Режим VPN-клиента",
                status = when {
                    proxyOpen -> CheckStatus.RED
                    else -> CheckStatus.GREEN
                },
                harm = "Прокси-порт виден без разрешений, протокол идентифицирован",
                harmSeverity = HarmSeverity.HIGH
            )
        )
    }

    private fun isPortOpen(port: Int): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), timeout)
            true
        }
    } catch (e: Exception) { false }
}
```

**Step 4: Запустить — убедиться что проходит**

```bash
./gradlew :app:testCoreDebugUnitTest --tests "*.PortScannerTest" -q
```

**Step 5: Commit**

```bash
git add app/src/main/java/com/stukachoff/
git add app/src/test/
git commit -m "feat: localhost port scanner — gRPC, Clash API, proxy ports"
```

---

## Task 6: DNS & System Proxy Checkers

**Files:**
- Create: `app/src/main/java/com/stukachoff/domain/checker/DnsChecker.kt`
- Create: `app/src/main/java/com/stukachoff/data/network/DnsCheckerImpl.kt`
- Test: `app/src/test/java/com/stukachoff/domain/checker/DnsCheckerTest.kt`

**Step 1: Написать тест**

```kotlin
class DnsCheckerTest {

    @Test
    fun `DNS 10_x is VPN tunnel — green`() {
        val checker = DnsCheckerImpl()
        assertEquals(CheckStatus.GREEN, checker.classifyDns("10.0.0.1"))
    }

    @Test
    fun `DNS 127_x is local — green`() {
        val checker = DnsCheckerImpl()
        assertEquals(CheckStatus.GREEN, checker.classifyDns("127.0.0.1"))
    }

    @Test
    fun `DNS 8_8_8_8 without VPN tunnel — red`() {
        val checker = DnsCheckerImpl()
        assertEquals(CheckStatus.RED, checker.classifyDns("8.8.8.8"))
    }

    @Test
    fun `system proxy set indicates SOCKS mode`() {
        val result = SystemProxyAnalyzer.analyze(
            httpProxy = "127.0.0.1",
            socksProxy = null
        )
        assertEquals(CheckStatus.RED, result.status)
    }

    @Test
    fun `no system proxy — green`() {
        val result = SystemProxyAnalyzer.analyze(
            httpProxy = null,
            socksProxy = null
        )
        assertEquals(CheckStatus.GREEN, result.status)
    }
}
```

**Step 2: Запустить — убедиться что падает**

```bash
./gradlew :app:testCoreDebugUnitTest --tests "*.DnsCheckerTest" -q
```

**Step 3: Реализовать**

```kotlin
// DnsCheckerImpl.kt
class DnsCheckerImpl : DnsChecker {

    fun classifyDns(ip: String): CheckStatus = when {
        ip.startsWith("10.") -> CheckStatus.GREEN       // VPN туннель
        ip.startsWith("172.") -> CheckStatus.GREEN      // VPN туннель
        ip.startsWith("127.") -> CheckStatus.GREEN      // Localhost DNS (FakeIP)
        ip.startsWith("fd") -> CheckStatus.GREEN        // IPv6 VPN
        else -> CheckStatus.RED                         // Внешний DNS — утечка
    }

    override suspend fun check(): CheckResult.Fixable = withContext(Dispatchers.IO) {
        val dns = getDnsServers()
        val hasLeak = dns.any { classifyDns(it) == CheckStatus.RED }

        CheckResult.Fixable(
            id = "dns_leak",
            title = "DNS-утечка",
            status = if (hasLeak) CheckStatus.RED else CheckStatus.GREEN,
            harm = "Провайдер видит все запрашиваемые домены даже с VPN",
            harmSeverity = HarmSeverity.HIGH
        )
    }

    private fun getDnsServers(): List<String> {
        return try {
            System.getProperty("net.dns1")?.let { listOf(it) } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }
}

// SystemProxyAnalyzer.kt
object SystemProxyAnalyzer {
    fun analyze(httpProxy: String?, socksProxy: String?): CheckResult.Fixable {
        val proxySet = httpProxy != null || socksProxy != null
        return CheckResult.Fixable(
            id = "system_proxy",
            title = "Системный прокси",
            status = if (proxySet) CheckStatus.RED else CheckStatus.GREEN,
            harm = "IP прокси виден всем приложениям без разрешений",
            harmSeverity = HarmSeverity.MEDIUM
        )
    }

    fun getFromSystem() = analyze(
        httpProxy = System.getProperty("http.proxyHost"),
        socksProxy = System.getProperty("socksProxyHost")
    )
}
```

**Step 4: Запустить — убедиться что проходит**

```bash
./gradlew :app:testCoreDebugUnitTest --tests "*.DnsCheckerTest" -q
```

**Step 5: Commit**

```bash
git add app/src/main/java/com/stukachoff/
git add app/src/test/
git commit -m "feat: DNS leak checker + system proxy analyzer"
```

---

## Task 7: Android Version Checker

**Files:**
- Create: `app/src/main/java/com/stukachoff/domain/checker/AndroidVersionChecker.kt`
- Test: `app/src/test/java/com/stukachoff/domain/checker/AndroidVersionCheckerTest.kt`

**Step 1: Написать тест**

```kotlin
class AndroidVersionCheckerTest {

    @Test
    fun `Android 10+ returns GREEN`() {
        val checker = AndroidVersionChecker()
        assertEquals(CheckStatus.GREEN, checker.classify(29))
    }

    @Test
    fun `Android 9 returns RED`() {
        val checker = AndroidVersionChecker()
        assertEquals(CheckStatus.RED, checker.classify(28))
    }
}
```

**Step 2: Запустить — убедиться что падает**

```bash
./gradlew :app:testCoreDebugUnitTest --tests "*.AndroidVersionCheckerTest" -q
```

**Step 3: Реализовать**

```kotlin
class AndroidVersionChecker {

    fun classify(sdkVersion: Int): CheckStatus =
        if (sdkVersion >= Build.VERSION_CODES.Q) CheckStatus.GREEN else CheckStatus.RED

    fun check(): CheckResult.Fixable = CheckResult.Fixable(
        id = "android_version",
        title = "Версия Android",
        status = classify(Build.VERSION.SDK_INT),
        harm = "Android 9 и ниже: любое приложение читает все активные TCP-соединения включая SSH-сессии",
        harmSeverity = HarmSeverity.HIGH
    )
}
```

**Step 4: Запустить — убедиться что проходит**

```bash
./gradlew :app:testCoreDebugUnitTest --tests "*.AndroidVersionCheckerTest" -q
```

**Step 5: Commit**

```bash
git add app/src/main/java/com/stukachoff/domain/checker/AndroidVersionChecker.kt
git add app/src/test/
git commit -m "feat: Android version checker"
```

---

## Task 8: Scan Orchestrator

**Files:**
- Create: `app/src/main/java/com/stukachoff/domain/usecase/ScanOrchestrator.kt`
- Test: `app/src/test/java/com/stukachoff/domain/usecase/ScanOrchestratorTest.kt`

**Step 1: Написать тест**

```kotlin
class ScanOrchestratorTest {
    private val vpnChecker = mockk<VpnStatusChecker>()
    private val portScanner = mockk<PortScanner>()
    private val interfaceChecker = mockk<InterfaceChecker>()
    private val dnsChecker = mockk<DnsChecker>()

    private val orchestrator = ScanOrchestrator(
        vpnChecker, portScanner, interfaceChecker, dnsChecker
    )

    @Test
    fun `returns NOT_ACTIVE state when VPN not running`() = runTest {
        coEvery { vpnChecker.check() } returns VpnStatus.NOT_ACTIVE

        val state = orchestrator.scan().first { it.vpnStatus != VpnStatus.UNKNOWN }

        assertEquals(VpnStatus.NOT_ACTIVE, state.vpnStatus)
        assertTrue(state.fixable.isEmpty())
    }

    @Test
    fun `runs all checkers when VPN active`() = runTest {
        coEvery { vpnChecker.check() } returns VpnStatus.USER_VPN
        coEvery { portScanner.scan() } returns mockPortScanResult()
        coEvery { interfaceChecker.check() } returns mockInterfaceResult()
        coEvery { dnsChecker.check() } returns mockDnsResult()

        val state = orchestrator.scan().last()

        assertFalse(state.isScanning)
        assertTrue(state.fixable.isNotEmpty())
    }
}
```

**Step 2: Запустить — убедиться что падает**

```bash
./gradlew :app:testCoreDebugUnitTest --tests "*.ScanOrchestratorTest" -q
```

**Step 3: Реализовать**

```kotlin
class ScanOrchestrator @Inject constructor(
    private val vpnStatusChecker: VpnStatusChecker,
    private val portScanner: PortScanner,
    private val interfaceChecker: InterfaceChecker,
    private val dnsChecker: DnsChecker,
    private val androidVersionChecker: AndroidVersionChecker = AndroidVersionChecker()
) {
    fun scan(): Flow<ScanState> = flow {
        emit(ScanState(isScanning = true))

        val vpnStatus = vpnStatusChecker.check()
        emit(ScanState(vpnStatus = vpnStatus, isScanning = vpnStatus == VpnStatus.USER_VPN))

        if (vpnStatus != VpnStatus.USER_VPN) return@flow

        // Параллельный запуск всех проверок
        val portResult = async { portScanner.scan() }
        val interfaceResult = async { interfaceChecker.check() }
        val dnsResult = async { dnsChecker.check() }

        val fixable = buildList {
            add(androidVersionChecker.check())
            val ports = portResult.await()
            add(ports.proxyModeResult)
            add(ports.grpcApiResult)
            add(ports.clashApiResult)
            add(dnsResult.await())
            add(interfaceResult.await().mtuResult)
            add(SystemProxyAnalyzer.getFromSystem())
        }.sortedByDescending { it.harmSeverity.ordinal }

        emit(ScanState(
            vpnStatus = vpnStatus,
            alwaysVisible = buildAlwaysVisible(interfaceResult.await()),
            fixable = fixable,
            isScanning = false
        ))
    }.flowOn(Dispatchers.IO)

    private fun buildAlwaysVisible(interfaceResult: InterfaceCheckResult) = listOf(
        CheckResult.AlwaysVisible(
            id = "transport_vpn",
            title = "Факт VPN",
            explanation = "Android ядро выставляет этот флаг — любое приложение с одним разрешением его видит",
            knowsWhat = "VPN активен",
            doesntKnow = "Какой сервер и куда"
        ),
        CheckResult.AlwaysVisible(
            id = "interface_name",
            title = "Имя сетевого интерфейса",
            explanation = "java.net.NetworkInterface доступен без разрешений",
            knowsWhat = "Тип клиента по имени (${interfaceResult.vpnInterfaces.firstOrNull()?.name ?: "tun0"})",
            doesntKnow = "Конфигурацию"
        ),
        CheckResult.AlwaysVisible(
            id = "http_probing",
            title = "Доступность заблокированных сайтов",
            explanation = "Если VPN работает — заблокированные сайты открываются. VK Max проверяет это",
            knowsWhat = "Косвенный факт VPN через доступность сайтов",
            doesntKnow = "Какой именно VPN"
        )
    )
}
```

**Step 4: Запустить — убедиться что проходит**

```bash
./gradlew :app:testCoreDebugUnitTest --tests "*.ScanOrchestratorTest" -q
```

**Step 5: Commit**

```bash
git add app/src/main/java/com/stukachoff/domain/usecase/
git add app/src/test/
git commit -m "feat: scan orchestrator — parallel checks, Flow-based state"
```

---

## Task 9: App Threats Analyzer

**Files:**
- Create: `app/src/main/java/com/stukachoff/data/apps/AppThreatAnalyzer.kt`
- Create: `app/src/main/assets/threats.json`
- Test: `app/src/test/java/com/stukachoff/data/apps/AppThreatAnalyzerTest.kt`

**Step 1: Создать threats.json**

```json
{
  "version": "2026-04-08",
  "known_apps": [
    {
      "package": "com.vk.vkclient",
      "name": "VK Max",
      "threat_level": "red",
      "confirmed": true,
      "methods": ["transport_vpn", "http_probing", "plmn"],
      "harm": "IP сервера передаётся в РКН через бинарный TCP-протокол",
      "source": "ntc.party/22584, март 2026"
    },
    {
      "package": "ru.sberbankmobile",
      "name": "СберБанк",
      "threat_level": "yellow",
      "confirmed": false,
      "methods": ["transport_vpn"],
      "harm": "Обязан внедрить детекцию к 15.04.2026"
    },
    {
      "package": "ru.tinkoff.banking.new",
      "name": "Т-Банк",
      "threat_level": "yellow",
      "confirmed": false,
      "methods": ["transport_vpn"],
      "harm": "Официально рекомендует отключить VPN при входе"
    },
    {
      "package": "ru.ozon.app.android",
      "name": "Ozon",
      "threat_level": "yellow",
      "confirmed": false,
      "methods": ["transport_vpn", "localhost_scan"],
      "harm": "Участник совещания Минцифры 28.03.2026"
    },
    {
      "package": "com.avito.android",
      "name": "Авито",
      "threat_level": "yellow",
      "confirmed": false,
      "methods": ["transport_vpn"],
      "harm": "Участник совещания Минцифры 28.03.2026"
    },
    {
      "package": "com.hh.android",
      "name": "HH.ru",
      "threat_level": "yellow",
      "confirmed": false,
      "methods": ["transport_vpn"],
      "harm": "Обязан внедрить детекцию к 15.04.2026"
    }
  ],
  "permission_risks": {
    "android.permission.ACCESS_NETWORK_STATE": {
      "can_detect": ["transport_vpn", "interface_name", "mtu"],
      "risk": "medium"
    },
    "android.permission.INTERNET": {
      "can_detect": ["http_probing", "geoip"],
      "risk": "high"
    },
    "android.permission.READ_PHONE_STATE": {
      "can_detect": ["mcc_correlation"],
      "risk": "high"
    }
  }
}
```

**Step 2: Написать тест**

```kotlin
class AppThreatAnalyzerTest {

    @Test
    fun `VK Max is flagged as RED`() {
        val db = ThreatDatabase.fromJson(loadTestJson())
        val app = db.findByPackage("com.vk.vkclient")
        assertNotNull(app)
        assertEquals(ThreatLevel.RED, app!!.threatLevel)
        assertTrue(app.confirmed)
    }

    @Test
    fun `app with only ACCESS_NETWORK_STATE gets YELLOW`() {
        val analyzer = PermissionRiskAnalyzer()
        val result = analyzer.analyze(
            listOf("android.permission.ACCESS_NETWORK_STATE")
        )
        assertEquals(ThreatLevel.YELLOW, result.level)
    }
}
```

**Step 3: Запустить — убедиться что падает**

```bash
./gradlew :app:testCoreDebugUnitTest --tests "*.AppThreatAnalyzerTest" -q
```

**Step 4: Реализовать**

```kotlin
class AppThreatAnalyzer @Inject constructor(
    private val context: Context,
    private val contentRepository: ContentRepository
) {
    suspend fun analyze(): List<AppThreat> = withContext(Dispatchers.IO) {
        val db = contentRepository.getThreatDatabase()
        val pm = context.packageManager

        db.knownApps.mapNotNull { knownApp ->
            val isInstalled = try {
                pm.getPackageInfo(knownApp.packageName, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) { false }

            AppThreat(
                packageName = knownApp.packageName,
                appName = knownApp.name,
                version = runCatching {
                    pm.getPackageInfo(knownApp.packageName, 0).versionName
                }.getOrNull(),
                threatLevel = knownApp.threatLevel,
                isInstalled = isInstalled,
                confirmedMethods = knownApp.methods,
                possibleMethods = emptyList(),
                harm = knownApp.harm,
                source = knownApp.source
            )
        }.filter { it.isInstalled }
            .sortedByDescending { it.threatLevel.ordinal }
    }
}
```

**Step 5: Запустить — убедиться что проходит**

```bash
./gradlew :app:testCoreDebugUnitTest --tests "*.AppThreatAnalyzerTest" -q
```

**Step 6: Commit**

```bash
git add app/src/main/assets/threats.json
git add app/src/main/java/com/stukachoff/data/apps/
git add app/src/test/
git commit -m "feat: app threat analyzer + threats.json database"
```

---

## Task 10: Hilt DI Setup

**Files:**
- Create: `app/src/main/java/com/stukachoff/StukachoffApp.kt`
- Create: `app/src/main/java/com/stukachoff/di/CheckersModule.kt`
- Create: `app/src/main/java/com/stukachoff/di/RepositoryModule.kt`

**Step 1: Реализовать**

```kotlin
// StukachoffApp.kt
@HiltAndroidApp
class StukachoffApp : Application()

// CheckersModule.kt
@Module
@InstallIn(SingletonComponent::class)
object CheckersModule {

    @Provides @Singleton
    fun provideVpnStatusChecker(@ApplicationContext ctx: Context): VpnStatusChecker =
        VpnStatusCheckerImpl(ctx)

    @Provides @Singleton
    fun providePortScanner(): PortScanner = PortScannerImpl()

    @Provides @Singleton
    fun provideInterfaceChecker(): InterfaceChecker = InterfaceCheckerImpl()

    @Provides @Singleton
    fun provideDnsChecker(): DnsChecker = DnsCheckerImpl()

    @Provides @Singleton
    fun provideScanOrchestrator(
        vpn: VpnStatusChecker,
        ports: PortScanner,
        iface: InterfaceChecker,
        dns: DnsChecker
    ): ScanOrchestrator = ScanOrchestrator(vpn, ports, iface, dns)
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/stukachoff/
git commit -m "feat: Hilt DI setup"
```

---

## Task 11: VERIFY ViewModel + Screen

**Files:**
- Create: `app/src/main/java/com/stukachoff/ui/verify/VerifyViewModel.kt`
- Create: `app/src/main/java/com/stukachoff/ui/verify/VerifyScreen.kt`
- Create: `app/src/main/java/com/stukachoff/ui/common/CheckCard.kt`
- Create: `app/src/main/java/com/stukachoff/ui/common/AlwaysVisibleCard.kt`

**Step 1: ViewModel**

```kotlin
@HiltViewModel
class VerifyViewModel @Inject constructor(
    private val orchestrator: ScanOrchestrator
) : ViewModel() {

    private val _state = MutableStateFlow(ScanState())
    val state = _state.asStateFlow()

    fun scan() {
        viewModelScope.launch {
            orchestrator.scan().collect { _state.value = it }
        }
    }
}
```

**Step 2: CheckCard composable**

```kotlin
@Composable
fun CheckCard(check: CheckResult.Fixable, onLearnMore: (String) -> Unit) {
    val color = when (check.status) {
        CheckStatus.GREEN -> Color(0xFF4CAF50)
        CheckStatus.YELLOW -> Color(0xFFFF9800)
        CheckStatus.RED -> Color(0xFFF44336)
    }
    val icon = when (check.status) {
        CheckStatus.GREEN -> "🟢"
        CheckStatus.YELLOW -> "🟡"
        CheckStatus.RED -> "🔴"
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(check.title, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f))
                if (check.harmSeverity == HarmSeverity.CRITICAL) {
                    Badge { Text("Критично") }
                }
            }

            if (check.status != CheckStatus.GREEN) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    check.harm,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { onLearnMore(check.id) }) {
                    Text("Как исправить →")
                }
            }
        }
    }
}
```

**Step 3: VerifyScreen**

```kotlin
@Composable
fun VerifyScreen(
    viewModel: VerifyViewModel = hiltViewModel(),
    onLearnMore: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.scan() }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // Статус VPN
        item { VpnStatusBanner(state.vpnStatus) }

        // Нескрываемые
        if (state.alwaysVisible.isNotEmpty()) {
            item {
                AlwaysVisibleSection(checks = state.alwaysVisible)
            }
        }

        // Устранимые
        if (state.fixable.isNotEmpty()) {
            item {
                Text(
                    "Можно защитить",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }
            items(state.fixable) { check ->
                CheckCard(check = check, onLearnMore = onLearnMore)
            }
        }

        // Кнопка повтора
        item {
            Button(
                onClick = { viewModel.scan() },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                enabled = !state.isScanning
            ) {
                if (state.isScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (state.isScanning) "Проверяю..." else "Проверить снова")
            }
        }
    }
}
```

**Step 4: Commit**

```bash
git add app/src/main/java/com/stukachoff/ui/
git commit -m "feat: VERIFY screen — CheckCard, AlwaysVisible section, VerifyViewModel"
```

---

## Task 12: THREATS Screen

**Files:**
- Create: `app/src/main/java/com/stukachoff/ui/threats/ThreatsViewModel.kt`
- Create: `app/src/main/java/com/stukachoff/ui/threats/ThreatsScreen.kt`

**Step 1: ViewModel + Screen (аналогично Task 11, паттерн тот же)**

```kotlin
@HiltViewModel
class ThreatsViewModel @Inject constructor(
    private val analyzer: AppThreatAnalyzer
) : ViewModel() {
    val threats = flow { emit(analyzer.analyze()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
}

@Composable
fun ThreatsScreen(viewModel: ThreatsViewModel = hiltViewModel()) {
    val threats by viewModel.threats.collectAsState()

    LazyColumn {
        item { ThreatsHeader() }

        items(threats.filter { it.threatLevel == ThreatLevel.RED }) { app ->
            AppThreatCard(app, highlightColor = Color(0xFFF44336))
        }
        items(threats.filter { it.threatLevel == ThreatLevel.YELLOW }) { app ->
            AppThreatCard(app, highlightColor = Color(0xFFFF9800))
        }

        item { OperatorsInfoCard() }
    }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/stukachoff/ui/threats/
git commit -m "feat: THREATS screen"
```

---

## Task 13: LEARN Module + Glossary

**Files:**
- Create: `app/src/main/java/com/stukachoff/ui/learn/LearnContent.kt`
- Create: `app/src/main/java/com/stukachoff/ui/common/GlossaryBottomSheet.kt`

**Step 1: Реализовать**

```kotlin
// GlossaryBottomSheet.kt
val glossary = mapOf(
    "gRPC" to "Служебный интерфейс управления xray. Как дверца техобслуживания — нужна разработчикам, но если открыта — войти может кто угодно.",
    "TUN-режим" to "VPN работает на уровне сети, а не приложения. Нет открытых прокси-портов — сканировать нечего.",
    "SOCKS5" to "Протокол прокси. Когда VPN-клиент работает в этом режиме — открывает локальный порт который виден другим приложениям.",
    "DNS-утечка" to "Запросы к сайтам (\"что такое google.com?\") уходят через провайдера а не через VPN. Провайдер видит весь список сайтов.",
    "MTU" to "Максимальный размер пакета. WireGuard использует 1420, стандартная сеть — 1500. По этому числу можно определить тип VPN.",
    "TRANSPORT_VPN" to "Флаг в Android системе. Горит когда VPN активен. Виден любому приложению с одним разрешением. Не скрыть без root.",
    "UUID" to "Уникальный идентификатор твоего аккаунта на VPN-сервере. Как логин — если утёк, тебя можно идентифицировать.",
    "SNI" to "Имя сайта в TLS-запросе. Reality использует SNI легитимного сайта (например icloud.com) чтобы маскировать VPN-трафик.",
    "Reality" to "Протокол маскировки трафика. Делает VPN неотличимым от обычного HTTPS на уровне сети. Не защищает от детекции внутри устройства.",
    "MCC" to "Код страны мобильного оператора. Российские операторы — 250. Если SIM российская, а IP зарубежный — явный сигнал VPN.",
    "ТСПУ" to "Технические средства противодействия угрозам. Оборудование DPI установленное у провайдеров. Блокирует VPN на сетевом уровне.",
    "split-tunnel" to "Режим VPN когда часть приложений идёт через туннель, часть — напрямую. Не скрывает факт VPN от обходимых приложений."
)

@Composable
fun GlossaryText(text: String, modifier: Modifier = Modifier) {
    // Кликабельные термины в тексте — открывают BottomSheet
    // Реализация через AnnotatedString + clickable spans
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/stukachoff/ui/
git commit -m "feat: LEARN module + glossary bottom sheet"
```

---

## Task 14: Navigation + MainActivity

**Files:**
- Create: `app/src/main/java/com/stukachoff/ui/MainActivity.kt`
- Create: `app/src/main/java/com/stukachoff/ui/Navigation.kt`

**Step 1: Реализовать**

```kotlin
// Navigation.kt
sealed class Screen(val route: String) {
    object Verify : Screen("verify")
    object Threats : Screen("threats")
    object Learn : Screen("learn/{checkId}") {
        fun route(checkId: String) = "learn/$checkId"
    }
}

@Composable
fun StukachoffNavHost(navController: NavHostController) {
    NavHost(navController, startDestination = Screen.Verify.route) {
        composable(Screen.Verify.route) {
            VerifyScreen(onLearnMore = { navController.navigate(Screen.Learn.route(it)) })
        }
        composable(Screen.Threats.route) { ThreatsScreen() }
        composable(Screen.Learn.route) { backStack ->
            val checkId = backStack.arguments?.getString("checkId")
            LearnScreen(checkId = checkId)
        }
    }
}

// MainActivity.kt
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StukachoffTheme {
                val navController = rememberNavController()
                Scaffold(
                    bottomBar = { BottomNavBar(navController) }
                ) { padding ->
                    Box(modifier = Modifier.padding(padding)) {
                        StukachoffNavHost(navController)
                    }
                }
            }
        }
    }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/stukachoff/ui/
git commit -m "feat: navigation + MainActivity"
```

---

## Task 15: GitHub Actions CI/CD

**Files:**
- Create: `.github/workflows/build.yml`
- Create: `.github/workflows/release.yml`

**Step 1: Build workflow**

```yaml
# .github/workflows/build.yml
name: Build
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '17', distribution: 'temurin' }
      - name: Unit tests
        run: ./gradlew testCoreDebugUnitTest testFullDebugUnitTest

  build:
    runs-on: ubuntu-latest
    needs: test
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '17', distribution: 'temurin' }
      - name: Build core APK
        run: ./gradlew assembleCoreRelease
      - name: Build full APK
        run: ./gradlew assembleFullRelease
```

**Step 2: Release workflow (по тегу)**

```yaml
# .github/workflows/release.yml
name: Release
on:
  push:
    tags: ['v*']
jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '17', distribution: 'temurin' }
      - name: Build signed APKs
        run: |
          ./gradlew assembleCoreRelease assembleFullRelease
      - name: Compute SHA-256
        run: |
          sha256sum app/build/outputs/apk/core/release/*.apk > checksums.txt
          sha256sum app/build/outputs/apk/full/release/*.apk >> checksums.txt
      - name: Create Release
        uses: softprops/action-gh-release@v1
        with:
          files: |
            app/build/outputs/apk/core/release/*.apk
            app/build/outputs/apk/full/release/*.apk
            checksums.txt
          body: |
            ## Stukachoff ${{ github.ref_name }}

            ### Версии
            - `stukachoff-core.apk` — без INTERNET permission (основная)
            - `stukachoff-full.apk` — с проверкой exit IP и обновлением контента

            ### Верификация
            Проверь SHA-256 из `checksums.txt` перед установкой.
```

**Step 3: Commit**

```bash
git add .github/
git commit -m "ci: GitHub Actions build + release with SHA-256 checksums"
```

---

## Task 16: Финальная проверка и reproducible build

**Step 1: Запустить все тесты**

```bash
./gradlew testCoreDebugUnitTest testFullDebugUnitTest
```
Ожидание: все PASS

**Step 2: Собрать оба APK**

```bash
./gradlew assembleCoreDebug assembleFullDebug
```

**Step 3: Проверить разрешения core APK**

```bash
aapt dump permissions app/build/outputs/apk/core/debug/app-core-debug.apk
```
Ожидание: только `android.permission.ACCESS_NETWORK_STATE` — без INTERNET

**Step 4: Убедиться что нет лишних SDK**

```bash
./gradlew :app:dependencies --configuration coreDebugRuntimeClasspath | grep -E "firebase|amplitude|sentry|crashlytics"
```
Ожидание: пустой вывод

**Step 5: Финальный commit**

```bash
git add .
git commit -m "chore: final verification — permissions audit, no tracking SDKs"
```

---

## Открытые вопросы для следующей итерации

1. **XHTTP**: добавить в LEARN как рекомендуемый транспорт (требует отдельного исследования API)
2. **Exit IP checker** (full flavor): реализовать HTTPS-запрос через активный туннель к `api.ipify.org`
3. **Обновление threats.json**: ContentRepository для full flavor с верификацией SHA-256
4. **Deep link бот**: интеграция с Telegram deep links для замкнутого цикла
5. **Тестирование на Xiaomi/MIUI**: специфика сетевого стека
6. **F-Droid**: настройка reproducible builds (deterministic APK)
