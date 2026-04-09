package com.stukachoff.ui.threats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stukachoff.data.apps.AppPermissionRisk
import com.stukachoff.data.apps.AppThreatAnalyzer
import com.stukachoff.data.apps.PermissionAnalyzer
import com.stukachoff.data.content.ContentRepository
import com.stukachoff.domain.model.AppThreat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ThreatsState(
    val threats: List<AppThreat> = emptyList(),
    val permissionRisks: List<AppPermissionRisk> = emptyList(),
    val isLoading: Boolean = true,
    val noneInstalled: Boolean = false
)

@HiltViewModel
class ThreatsViewModel @Inject constructor(
    private val analyzer: AppThreatAnalyzer,
    private val permissionAnalyzer: PermissionAnalyzer,
    private val contentRepository: ContentRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ThreatsState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch { contentRepository.refreshInBackground() }
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = ThreatsState(isLoading = true)
            val threats = analyzer.analyze()
            val risks   = permissionAnalyzer.analyzeInstalledApps()
            _state.value = ThreatsState(
                threats          = threats,
                permissionRisks  = risks,
                isLoading        = false,
                noneInstalled    = threats.isEmpty()
            )
        }
    }
}
