package com.stukachoff.data.prefs

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("stukachoff_prefs", Context.MODE_PRIVATE)

    /** Онбординг пройден */
    var onboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit { putBoolean(KEY_ONBOARDING, value) }

    /**
     * Режим приватности: когда true — никаких сетевых запросов.
     * По умолчанию ВКЛЮЧЁН — пользователь сам решает когда разрешить сеть.
     */
    var privacyModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_PRIVACY_MODE, true)
        set(value) = prefs.edit { putBoolean(KEY_PRIVACY_MODE, value) }

    var lastThreatUpdate: Long
        get() = prefs.getLong(KEY_THREAT_UPDATE, 0L)
        set(value) = prefs.edit { putLong(KEY_THREAT_UPDATE, value) }

    fun saveLastThreatUpdate(timestamp: Long) {
        lastThreatUpdate = timestamp
    }

    companion object {
        private const val KEY_ONBOARDING    = "onboarding_completed"
        private const val KEY_PRIVACY_MODE  = "privacy_mode_enabled"
        private const val KEY_THREAT_UPDATE = "threats_last_update"
    }
}
