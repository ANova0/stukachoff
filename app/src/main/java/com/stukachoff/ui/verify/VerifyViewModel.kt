package com.stukachoff.ui.verify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.content.Intent
import com.stukachoff.data.config.ActiveGrpcScanner
import com.stukachoff.data.export.ReportExporter
import com.stukachoff.data.network.DnsCheckerImpl
import com.stukachoff.data.network.ExitIpChecker
import com.stukachoff.data.network.SystemProxyAnalyzer
import com.stukachoff.domain.checker.AndroidVersionChecker
import com.stukachoff.domain.checker.InterfaceChecker
import com.stukachoff.domain.checker.OpenPort
import com.stukachoff.domain.checker.PortScanner
import com.stukachoff.domain.model.CheckResult
import com.stukachoff.domain.model.ScanState
import com.stukachoff.domain.usecase.ScanOrchestrator
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MIN_SCAN_DURATION_MS = 7_000L

@HiltViewModel
class VerifyViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val orchestrator: ScanOrchestrator,
    private val portScanner: PortScanner,
    private val interfaceChecker: InterfaceChecker,
    private val exitIpChecker: ExitIpChecker,
    private val androidVersionChecker: AndroidVersionChecker,
    private val dnsChecker: DnsCheckerImpl,
    private val reportExporter: ReportExporter,
    private val activeGrpcScanner: ActiveGrpcScanner
) : ViewModel() {

    private val _state = MutableStateFlow(ScanState())
    val state = _state.asStateFlow()

    private val _showScanning = MutableStateFlow(false)
    val showScanning = _showScanning.asStateFlow()

    // ID проверки которая сейчас перепроверяется (для spinner на карточке)
    private val _recheckingId = MutableStateFlow<String?>(null)
    val recheckingId = _recheckingId.asStateFlow()

    private val _fullScanPorts = MutableStateFlow<List<OpenPort>>(emptyList())
    val fullScanPorts = _fullScanPorts.asStateFlow()

    private val _isDeepScanning = MutableStateFlow(false)
    val isDeepScanning = _isDeepScanning.asStateFlow()

    init { scan() }

    fun scan() {
        viewModelScope.launch {
            _showScanning.value = true
            _state.value = ScanState(isScanning = true)

            val scanJob = async {
                var finalState = ScanState()
                orchestrator.scan().collect { finalState = it }
                finalState
            }
            val timerJob = async { delay(MIN_SCAN_DURATION_MS) }

            val result = scanJob.await()
            timerJob.await()

            _state.value = result
            _showScanning.value = false
        }
    }

    fun shareReport() {
        val intent = reportExporter.buildShareIntent(_state.value)
        context.startActivity(Intent.createChooser(intent, "Поделиться отчётом")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // Результат deep scan: null = не запускался, true = gRPC найден, false = не найден
    private val _deepScanResult = MutableStateFlow<String?>(null)
    val deepScanResult = _deepScanResult.asStateFlow()

    fun deepScan() {
        if (_isDeepScanning.value) return
        viewModelScope.launch {
            _isDeepScanning.value = true
            _deepScanResult.value = null

            val results = portScanner.fullScan()
            _fullScanPorts.value = results

            // Главное: пробуем gRPC на ВСЕХ найденных портах
            var grpcFound = false
            if (results.isNotEmpty()) {
                val grpcResult = activeGrpcScanner.scanAllPorts(results)
                if (grpcResult?.config != null) {
                    grpcFound = true
                    val method = if (grpcResult.isKnownPort)
                        com.stukachoff.domain.model.ConfigAccessMethod.KNOWN_PORT
                    else
                        com.stukachoff.domain.model.ConfigAccessMethod.ACTIVE_PROBE
                    _state.value = _state.value.copy(
                        vpnConfig = grpcResult.config,
                        configAccessMethod = method
                    )
                    _deepScanResult.value = "🔴 gRPC API найден на порту ${grpcResult.port}! Конфиг прочитан."
                }
            }

            if (!grpcFound) {
                // Разделяем на уязвимые (SOCKS5/HTTP) и нейтральные
                val vulnerablePorts = results.filter {
                    it.category == com.stukachoff.domain.checker.PortCategory.SOCKS5 ||
                    it.category == com.stukachoff.domain.checker.PortCategory.HTTP_PROXY ||
                    it.category == com.stukachoff.domain.checker.PortCategory.MIXED
                }
                _deepScanResult.value = if (vulnerablePorts.isNotEmpty()) {
                    "🟡 gRPC API не найден. Но обнаружены прокси-порты: " +
                    vulnerablePorts.joinToString(", ") { "${it.port} (${it.description})" }
                } else {
                    "🟢 gRPC API не обнаружен — конфиг защищён от стукачей. " +
                    "Просканировано ${results.size + 3000} портов."
                }
            }

            _isDeepScanning.value = false
        }
    }

    /** Пользователь вручную выбрал VPN-клиент */
    fun setManualClient(clientName: String) {
        val current = _state.value.activeClient ?: return
        val pkg = com.stukachoff.data.apps.ActiveClientDetector.PACKAGE_ENGINE.entries
            .find { it.value.second == clientName }
        if (pkg != null) {
            _state.value = _state.value.copy(
                activeClient = current.copy(
                    packageName = pkg.key,
                    displayName = clientName,
                    engine = pkg.value.first,
                    confidence = 100
                )
            )
        }
    }

    /**
     * Перепроверяет одну конкретную карточку без полного пересканирования.
     */
    fun recheckSingle(checkId: String) {
        viewModelScope.launch {
            _recheckingId.value = checkId
            val newCheck: CheckResult.Fixable? = when (checkId) {
                "exit_ip"        -> exitIpChecker.toCheckResult(exitIpChecker.check())
                "android_version" -> androidVersionChecker.check()
                "dns_leak"       -> dnsChecker.check()
                "system_proxy"   -> SystemProxyAnalyzer.check()
                "proxy_mode",
                "grpc_api",
                "clash_api"      -> portScanner.scan().let { ports ->
                    when (checkId) {
                        "proxy_mode" -> ports.proxyModeResult
                        "grpc_api"   -> ports.grpcApiResult
                        else         -> ports.clashApiResult
                    }
                }
                "mtu"            -> interfaceChecker.check().mtuResult
                // Перезапуск всего скана для сложных проверок
                "split_tunnel", "vpn_works", "work_profile" -> {
                    scan() // полный ресканирование
                    null   // scan() обновит весь state
                }
                else             -> null
            }

            newCheck?.let { updated ->
                _state.value = _state.value.copy(
                    fixable = _state.value.fixable.map {
                        if (it.id == checkId) updated else it
                    }
                )
            }
            _recheckingId.value = null
        }
    }
}
