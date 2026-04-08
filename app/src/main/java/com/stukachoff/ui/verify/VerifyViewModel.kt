package com.stukachoff.ui.verify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stukachoff.domain.model.ScanState
import com.stukachoff.domain.usecase.ScanOrchestrator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Минимальное время показа анимации расследования (мс)
private const val MIN_SCAN_DURATION_MS = 7_000L

@HiltViewModel
class VerifyViewModel @Inject constructor(
    private val orchestrator: ScanOrchestrator
) : ViewModel() {

    private val _state = MutableStateFlow(ScanState())
    val state = _state.asStateFlow()

    // true пока показываем анимацию расследования
    private val _showScanning = MutableStateFlow(false)
    val showScanning = _showScanning.asStateFlow()

    init { scan() }

    fun scan() {
        viewModelScope.launch {
            _showScanning.value = true
            _state.value = ScanState(isScanning = true)

            // Запускаем сканирование и минимальный таймер параллельно
            val scanJob = async {
                var finalState = ScanState()
                orchestrator.scan().collect { finalState = it }
                finalState
            }
            val timerJob = async { delay(MIN_SCAN_DURATION_MS) }

            // Ждём оба — результат и таймер
            val result = scanJob.await()
            timerJob.await()

            _state.value = result
            _showScanning.value = false
        }
    }
}
