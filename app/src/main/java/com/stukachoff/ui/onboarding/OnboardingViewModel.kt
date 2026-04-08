package com.stukachoff.ui.onboarding

import androidx.lifecycle.ViewModel
import com.stukachoff.data.prefs.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefs: AppPreferences
) : ViewModel() {
    fun completeOnboarding() {
        prefs.onboardingCompleted = true
    }
}
