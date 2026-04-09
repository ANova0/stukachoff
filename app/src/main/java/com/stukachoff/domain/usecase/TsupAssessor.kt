package com.stukachoff.domain.usecase

import com.stukachoff.data.apps.ActiveClient
import com.stukachoff.data.apps.VpnEngine
import com.stukachoff.domain.model.CheckResult
import com.stukachoff.domain.model.CheckStatus
import com.stukachoff.domain.model.HarmSeverity
import com.stukachoff.domain.model.OutboundConfig
import com.stukachoff.domain.model.OverallVerdict
import com.stukachoff.domain.model.ProtectionLevel
import com.stukachoff.domain.model.TsupLevel
import com.stukachoff.domain.model.VpnConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TsupAssessor @Inject constructor() {

    fun assessTsup(
        activeClient: ActiveClient?,
        vpnConfig: VpnConfig?,
        mtu: Int
    ): Pair<ProtectionLevel, String> {
        // Use config data if available (more accurate)
        if (vpnConfig != null && vpnConfig.outbounds.isNotEmpty()) {
            val ob = vpnConfig.outbounds.first()
            return when (ob.tsupResistance) {
                TsupLevel.HIGH    -> ProtectionLevel.HIGH to
                    "${ob.protocol.uppercase()}+${ob.security.uppercase()} — высокая устойчивость к ТСПУ"
                TsupLevel.MEDIUM  -> ProtectionLevel.MEDIUM to
                    "${ob.protocol.uppercase()} порт ${ob.serverPort} — средняя устойчивость"
                TsupLevel.LOW     -> ProtectionLevel.LOW to
                    "${ob.protocol.uppercase()} — низкая устойчивость, виден ТСПУ"
                TsupLevel.BLOCKED -> ProtectionLevel.CRITICAL to
                    "${ob.protocol.uppercase()} — заблокирован ТСПУ"
            }
        }

        // Fallback: by client + MTU
        if (activeClient == null) return ProtectionLevel.MEDIUM to "Клиент не определён"

        return when {
            activeClient.engine == VpnEngine.AMNEZIA ->
                ProtectionLevel.HIGH to "AmneziaWG — высокая устойчивость (junk packets)"
            activeClient.engine == VpnEngine.TOR ->
                ProtectionLevel.HIGH to "Tor — максимальная анонимность"
            activeClient.engine == VpnEngine.XRAY || activeClient.engine == VpnEngine.SINGBOX ->
                ProtectionLevel.MEDIUM to
                    "${activeClient.displayName} — зависит от протокола в конфиге провайдера"
            activeClient.engine == VpnEngine.WIREGUARD ->
                ProtectionLevel.LOW to "WireGuard блокируется ТСПУ с декабря 2025"
            activeClient.engine == VpnEngine.OPENVPN ->
                ProtectionLevel.CRITICAL to "OpenVPN заблокирован ТСПУ"
            activeClient.engine == VpnEngine.CLOUDFLARE ->
                ProtectionLevel.MEDIUM to "Cloudflare WARP — частичная защита"
            else -> ProtectionLevel.MEDIUM to
                "${activeClient.displayName} — устойчивость неизвестна"
        }
    }

    fun buildVerdict(
        fixable: List<CheckResult.Fixable>,
        activeClient: ActiveClient?,
        vpnConfig: VpnConfig?,
        mtu: Int
    ): OverallVerdict {
        val criticals = fixable.filter {
            it.status == CheckStatus.RED && it.harmSeverity == HarmSeverity.CRITICAL
        }
        val highs = fixable.filter {
            it.status == CheckStatus.RED && it.harmSeverity == HarmSeverity.HIGH
        }

        val appLevel = when {
            criticals.isNotEmpty() -> ProtectionLevel.CRITICAL
            highs.isNotEmpty()     -> ProtectionLevel.LOW
            fixable.any { it.status == CheckStatus.YELLOW } -> ProtectionLevel.MEDIUM
            else -> ProtectionLevel.HIGH
        }

        val appDetails = when (appLevel) {
            ProtectionLevel.CRITICAL -> "Критично: ${criticals.first().title}"
            ProtectionLevel.LOW      -> "${highs.size} проблем требуют внимания"
            ProtectionLevel.MEDIUM   -> "Есть незначительные проблемы"
            ProtectionLevel.HIGH     -> "Конфигурация защищена"
        }

        val (tsupLevel, tsupDetails) = assessTsup(activeClient, vpnConfig, mtu)

        return OverallVerdict(
            appProtection    = appLevel,
            tsupProtection   = tsupLevel,
            appDetails       = appDetails,
            tsupDetails      = tsupDetails,
            topRecommendation = buildTopRec(criticals, highs, activeClient, tsupLevel)
        )
    }

    private fun buildTopRec(
        criticals: List<CheckResult.Fixable>,
        highs: List<CheckResult.Fixable>,
        activeClient: ActiveClient?,
        tsupLevel: ProtectionLevel
    ): String? {
        val client = activeClient?.displayName ?: "VPN клиента"
        return when {
            criticals.any { it.id == "grpc_api" }  ->
                "Закрой gRPC API в настройках $client"
            criticals.any { it.id == "exit_ip" }   ->
                "VPN не маршрутизирует трафик — проверь настройки $client"
            highs.any { it.id == "split_tunnel" }  ->
                "Включи маршрутизацию всего трафика через VPN"
            highs.any { it.id == "dns_leak" }      ->
                "Настрой DNS через VPN-туннель"
            tsupLevel == ProtectionLevel.LOW &&
                activeClient?.engine == VpnEngine.WIREGUARD ->
                "Переключись с WireGuard на AmneziaWG"
            tsupLevel == ProtectionLevel.CRITICAL  ->
                "Смени VPN клиент — текущий заблокирован ТСПУ"
            else -> null
        }
    }
}
