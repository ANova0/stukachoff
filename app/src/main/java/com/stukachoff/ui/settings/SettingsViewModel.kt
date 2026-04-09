package com.stukachoff.ui.settings

import androidx.lifecycle.ViewModel
import com.stukachoff.data.prefs.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: AppPreferences
) : ViewModel() {

    // autoUpdate = true → privacyMode = false (разрешаем сеть)
    private val _autoUpdate = MutableStateFlow(!prefs.privacyModeEnabled)
    val autoUpdate = _autoUpdate.asStateFlow()

    fun setAutoUpdate(enabled: Boolean) {
        prefs.privacyModeEnabled = !enabled
        _autoUpdate.value = enabled
    }
}
