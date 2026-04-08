package com.stukachoff.data.network

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
class ExitIpChecker @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Запрос идёт через VPN-туннель (если VPN активен и не исключает наше приложение)
    // Результат: IP VPN-сервера — показываем пользователю, никуда не отправляем
    suspend fun check(): ExitIpResult = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://api.ipify.org?format=text")
                .header("User-Agent", "Mozilla/5.0")
                .build()
            val response = client.newCall(request).execute()
            val ip = response.body?.string()?.trim() ?: ""
            ExitIpResult(
                exitIp = ip,
                check  = CheckResult.Fixable(
                    id           = "exit_ip",
                    title        = "Exit IP (через туннель)",
                    status       = if (ip.isNotBlank()) CheckStatus.GREEN else CheckStatus.YELLOW,
                    harm         = if (ip.isNotBlank()) "VPN туннель работает: $ip"
                                   else "Не удалось определить exit IP",
                    harmSeverity = HarmSeverity.INFO
                )
            )
        }.getOrElse {
            ExitIpResult(
                exitIp = null,
                check  = CheckResult.Fixable(
                    id           = "exit_ip",
                    title        = "Exit IP (через туннель)",
                    status       = CheckStatus.YELLOW,
                    harm         = "Ошибка запроса: ${it.message}",
                    harmSeverity = HarmSeverity.INFO
                )
            )
        }
    }
}

data class ExitIpResult(
    val exitIp: String?,
    val check: CheckResult.Fixable
)
