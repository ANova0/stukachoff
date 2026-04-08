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

    private val _privacyMode = MutableStateFlow(prefs.privacyModeEnabled)
    val privacyMode = _privacyMode.asStateFlow()

    fun setPrivacyMode(enabled: Boolean) {
        prefs.privacyModeEnabled = enabled
        _privacyMode.value = enabled
    }
}
