package com.stukachoff.ui.threats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stukachoff.data.apps.AppThreatAnalyzer
import com.stukachoff.data.content.ContentRepository
import com.stukachoff.domain.model.AppThreat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ThreatsState(
    val threats: List<AppThreat> = emptyList(),
    val isLoading: Boolean = true,
    val noneInstalled: Boolean = false,
    val dbVersion: String = ""
)

@HiltViewModel
class ThreatsViewModel @Inject constructor(
    private val analyzer: AppThreatAnalyzer,
    private val contentRepository: ContentRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ThreatsState())
    val state = _state.asStateFlow()

    init {
        // Обновляем базу в фоне (только при сетевом режиме)
        viewModelScope.launch { contentRepository.refreshInBackground() }
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = ThreatsState(isLoading = true)
            val threats = analyzer.analyze()
            _state.value = ThreatsState(
                threats      = threats,
                isLoading    = false,
                noneInstalled = threats.isEmpty()
            )
        }
    }
}
