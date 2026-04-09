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

/**
 * Единственный надёжный тест реальной работы VPN-туннеля.
 * Запускается ВСЕГДА (не зависит от "режима автообновления") —
 * это диагностический тест, не телеметрия.
 *
 * Если VPN маршрутизирует трафик → запрос идёт через туннель → показываем exit IP (IP сервера)
 * Если VPN сломан (TLS error, нет туннелинга) → запрос идёт напрямую или падает
 */
@Singleton
class ExitIpChecker @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun check(): ExitIpCheckResult = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://api.ipify.org?format=text")
                .header("User-Agent", "Mozilla/5.0")
                .build()
            val response = client.newCall(request).execute()
            val ip = response.body?.string()?.trim() ?: ""

            if (ip.isBlank()) {
                ExitIpCheckResult.Failed("Пустой ответ от сервера")
            } else {
                ExitIpCheckResult.Success(ip)
            }
        }.getOrElse { e ->
            val msg = when {
                e.message?.contains("Unable to resolve host") == true ->
                    "DNS не работает — VPN не маршрутизирует трафик"
                e.message?.contains("timeout") == true ->
                    "Таймаут — VPN не отвечает"
                e.message?.contains("HANDSHAKE") == true ->
                    "TLS ошибка — VPN некорректно настроен"
                else -> e.message ?: "Неизвестная ошибка"
            }
            ExitIpCheckResult.Failed(msg)
        }
    }

    fun toCheckResult(result: ExitIpCheckResult): CheckResult.Fixable = CheckResult.Fixable(
        id           = "exit_ip",
        title        = "VPN маршрутизирует трафик",
        status       = when (result) {
            is ExitIpCheckResult.Success -> CheckStatus.GREEN
            is ExitIpCheckResult.Failed  -> CheckStatus.RED
        },
        harm         = when (result) {
            is ExitIpCheckResult.Success -> "Работает · Exit IP: ${result.ip}"
            is ExitIpCheckResult.Failed  -> "⚠️ ${result.reason}"
        },
        harmSeverity = when (result) {
            is ExitIpCheckResult.Success -> HarmSeverity.INFO
            is ExitIpCheckResult.Failed  -> HarmSeverity.CRITICAL
        }
    )
}

sealed class ExitIpCheckResult {
    data class Success(val ip: String) : ExitIpCheckResult()
    data class Failed(val reason: String) : ExitIpCheckResult()
}
