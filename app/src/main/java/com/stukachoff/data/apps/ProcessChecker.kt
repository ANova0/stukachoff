package com.stukachoff.data.apps

import android.app.ActivityManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

enum class AppRunningStatus {
    ACTIVE,   // Прямо сейчас работает — следит
    DANGER,   // Установлен, не запущен — потенциально опасен
    UNKNOWN
}

@Singleton
class ProcessChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun getRunningPackages(): Set<String> = withContext(Dispatchers.IO) {
        val am = context.getSystemService(ActivityManager::class.java)
        runCatching {
            am.runningAppProcesses
                ?.flatMap { it.pkgList?.toList() ?: emptyList() }
                ?.toSet()
                ?: emptySet()
        }.getOrDefault(emptySet())
    }

    fun getStatus(packageName: String, runningPackages: Set<String>): AppRunningStatus {
        return if (packageName in runningPackages) AppRunningStatus.ACTIVE
        else AppRunningStatus.DANGER
    }
}
