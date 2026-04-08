package com.stukachoff.data.network

import com.stukachoff.data.prefs.AppPreferences
import com.stukachoff.domain.model.CheckResult
import com.stukachoff.domain.model.CheckStatus
import com.stukachoff.domain.model.HarmSeverity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExitIpChecker @Inject constructor(
    private val prefs: AppPreferences
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun check(): CheckResult.Fixable {
        if (prefs.privacyModeEnabled) {
            return CheckResult.Fixable(
                id           = "exit_ip",
                title        = "Exit IP (отключено)",
                status       = CheckStatus.YELLOW,
                harm         = "Включи сетевой режим в настройках чтобы проверить exit IP",
                harmSeverity = HarmSeverity.INFO
            )
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("https://api.ipify.org?format=text")
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                val ip = client.newCall(request).execute().body?.string()?.trim() ?: ""
                CheckResult.Fixable(
                    id           = "exit_ip",
                    title        = "Exit IP через туннель",
                    status       = if (ip.isNotBlank()) CheckStatus.GREEN else CheckStatus.YELLOW,
                    harm         = if (ip.isNotBlank()) "VPN работает · exit IP: $ip"
                                   else "Не удалось определить exit IP",
                    harmSeverity = HarmSeverity.INFO
                )
            }.getOrElse {
                CheckResult.Fixable(
                    id           = "exit_ip",
                    title        = "Exit IP через туннель",
                    status       = CheckStatus.YELLOW,
                    harm         = "Ошибка: ${it.message}",
                    harmSeverity = HarmSeverity.INFO
                )
            }
        }
    }
}
