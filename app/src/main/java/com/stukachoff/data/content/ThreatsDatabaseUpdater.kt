package com.stukachoff.data.content

import android.content.Context
import com.stukachoff.data.prefs.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThreatsDatabaseUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val cacheFile get() = context.cacheDir.resolve("threats_cache.json")
    private val lastUpdatePref = "threats_last_update"

    /**
     * Обновляет threats.json если:
     * - Сетевой режим включён
     * - Кэш старше 7 дней
     */
    suspend fun updateIfNeeded() = withContext(Dispatchers.IO) {
        if (prefs.privacyModeEnabled) return@withContext
        if (!isCacheStale()) return@withContext

        runCatching {
            val request = Request.Builder()
                .url("https://raw.githubusercontent.com/ANova0/stukachoff/main/app/src/main/assets/threats.json")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@runCatching
                // Базовая валидация — должен быть JSON с known_apps
                if (body.contains("known_apps")) {
                    cacheFile.writeText(body)
                    prefs.saveLastThreatUpdate(System.currentTimeMillis())
                }
            }
        }
    }

    fun getCachedOrNull(): String? {
        if (!cacheFile.exists()) return null
        return runCatching { cacheFile.readText() }.getOrNull()
    }

    private fun isCacheStale(): Boolean {
        val lastUpdate = prefs.lastThreatUpdate
        if (lastUpdate == 0L) return true
        val sevenDays = 7 * 24 * 60 * 60 * 1000L
        return System.currentTimeMillis() - lastUpdate > sevenDays
    }
}
