package com.stukachoff.data.export

import android.content.Context
import android.content.Intent
import com.stukachoff.BuildConfig
import com.stukachoff.domain.model.CheckStatus
import com.stukachoff.domain.model.ScanState
import com.stukachoff.domain.model.VpnStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun buildShareIntent(state: ScanState): Intent {
        val report = buildReport(state)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Stukachoff — Отчёт о защите VPN")
            putExtra(Intent.EXTRA_TEXT, report)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    private fun buildReport(state: ScanState): String {
        val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
        val sb = StringBuilder()

        sb.appendLine("╔══════════════════════════════════╗")
        sb.appendLine("║   STUKACHOFF — ОТЧЁТ             ║")
        sb.appendLine("╚══════════════════════════════════╝")
        sb.appendLine()
        sb.appendLine("Дата:    $date")
        sb.appendLine("Версия:  ${BuildConfig.VERSION_NAME}")

        // Устройство — без чувствительных данных
        state.deviceInfo?.let { info ->
            sb.appendLine("Android: ${info.androidVersion} (API ${info.sdkInt})")
            sb.appendLine("VPN:     ${info.installedVpnClients.joinToString(", ").ifBlank { "—" }}")
        }
        sb.appendLine()

        // Статус VPN
        sb.appendLine("═══════════════════════════")
        sb.appendLine("СТАТУС VPN")
        sb.appendLine("═══════════════════════════")
        sb.appendLine(when (state.vpnStatus) {
            VpnStatus.USER_VPN      -> "✅ VPN активен"
            VpnStatus.NOT_ACTIVE    -> "❌ VPN не обнаружен"
            VpnStatus.CORPORATE_VPN -> "🏢 Корпоративный VPN"
            VpnStatus.UNKNOWN       -> "❓ Неизвестно"
        })
        sb.appendLine()

        // Проверки
        sb.appendLine("═══════════════════════════")
        sb.appendLine("РЕЗУЛЬТАТЫ ПРОВЕРОК")
        sb.appendLine("═══════════════════════════")
        state.fixable.forEach { check ->
            val statusIcon = when (check.status) {
                CheckStatus.GREEN  -> "🟢"
                CheckStatus.YELLOW -> "🟡"
                CheckStatus.RED    -> "🔴"
            }
            sb.appendLine("$statusIcon ${check.title}")
            if (check.status != CheckStatus.GREEN) {
                // Не включаем реальные IP в отчёт — только описание проблемы
                val safeHarm = check.harm
                    .replace(Regex("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"), "[IP скрыт]")
                sb.appendLine("   ↳ $safeHarm")
            }
        }

        sb.appendLine()
        sb.appendLine("═══════════════════════════")
        sb.appendLine("Проверено приложением Stukachoff")
        sb.appendLine("github.com/ANova0/stukachoff")
        sb.appendLine()
        sb.appendLine("⚠️ IP-адреса в отчёте скрыты для безопасности")

        return sb.toString()
    }
}
