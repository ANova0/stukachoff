package com.stukachoff.ui.verify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.content.Intent
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
    private val reportExporter: ReportExporter
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

    fun deepScan() {
        if (_isDeepScanning.value) return
        viewModelScope.launch {
            _isDeepScanning.value = true
            val results = portScanner.fullScan()
            _fullScanPorts.value = results
            _isDeepScanning.value = false
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
