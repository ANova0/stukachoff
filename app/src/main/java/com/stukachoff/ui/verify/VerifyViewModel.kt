package com.stukachoff.ui.verify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stukachoff.data.network.DnsCheckerImpl
import com.stukachoff.data.network.ExitIpChecker
import com.stukachoff.data.network.PortScannerImpl
import com.stukachoff.data.network.SystemProxyAnalyzer
import com.stukachoff.domain.checker.AndroidVersionChecker
import com.stukachoff.domain.checker.InterfaceCheckerImpl
import com.stukachoff.domain.model.CheckResult
import com.stukachoff.domain.model.ScanState
import com.stukachoff.domain.usecase.ScanOrchestrator
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
    private val orchestrator: ScanOrchestrator,
    private val exitIpChecker: ExitIpChecker,
    private val androidVersionChecker: AndroidVersionChecker,
    private val dnsChecker: DnsCheckerImpl
) : ViewModel() {

    private val _state = MutableStateFlow(ScanState())
    val state = _state.asStateFlow()

    private val _showScanning = MutableStateFlow(false)
    val showScanning = _showScanning.asStateFlow()

    // ID проверки которая сейчас перепроверяется (для spinner на карточке)
    private val _recheckingId = MutableStateFlow<String?>(null)
    val recheckingId = _recheckingId.asStateFlow()

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

    /**
     * Перепроверяет одну конкретную карточку без полного пересканирования.
     */
    fun recheckSingle(checkId: String) {
        viewModelScope.launch {
            _recheckingId.value = checkId
            val newCheck: CheckResult.Fixable? = when (checkId) {
                "exit_ip"        -> exitIpChecker.check()
                "android_version" -> androidVersionChecker.check()
                "dns_leak"       -> dnsChecker.check()
                "system_proxy"   -> SystemProxyAnalyzer.check()
                "proxy_mode",
                "grpc_api",
                "clash_api"      -> PortScannerImpl().scan().let { ports ->
                    when (checkId) {
                        "proxy_mode" -> ports.proxyModeResult
                        "grpc_api"   -> ports.grpcApiResult
                        else         -> ports.clashApiResult
                    }
                }
                "mtu"            -> InterfaceCheckerImpl().check().mtuResult
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
