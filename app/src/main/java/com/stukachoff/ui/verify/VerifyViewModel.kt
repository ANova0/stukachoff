package com.stukachoff.ui.verify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stukachoff.domain.model.ScanState
import com.stukachoff.domain.usecase.ScanOrchestrator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VerifyViewModel @Inject constructor(
    private val orchestrator: ScanOrchestrator
) : ViewModel() {

    private val _state = MutableStateFlow(ScanState())
    val state = _state.asStateFlow()

    init { scan() }

    fun scan() {
        viewModelScope.launch {
            orchestrator.scan().collect { _state.value = it }
        }
    }
}
