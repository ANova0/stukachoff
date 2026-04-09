package com.stukachoff.domain.model

import com.stukachoff.data.apps.ActiveClient

data class ScanState(
    val vpnStatus: VpnStatus = VpnStatus.UNKNOWN,
    val alwaysVisible: List<CheckResult.AlwaysVisible> = emptyList(),
    val fixable: List<CheckResult.Fixable> = emptyList(),
    val deviceInfo: DeviceInfo? = null,
    val activeClient: ActiveClient? = null,
    val vpnConfig: VpnConfig? = null,
    val configAccessMethod: ConfigAccessMethod = ConfigAccessMethod.NOT_READ,
    val overallVerdict: OverallVerdict? = null,
    val isScanning: Boolean = false,
    val error: String? = null
)

enum class VpnStatus {
    UNKNOWN,
    NOT_ACTIVE,
    USER_VPN,      // VpnService-based: Hiddify, v2rayNG, NekoBox и т.д.
    CORPORATE_VPN  // MDM/Enterprise VPN
}

data class OverallVerdict(
    val appProtection: ProtectionLevel,
    val tsupProtection: ProtectionLevel,
    val appDetails: String,
    val tsupDetails: String,
    val topRecommendation: String?
)

enum class ProtectionLevel { HIGH, MEDIUM, LOW, CRITICAL }

enum class ConfigAccessMethod {
    NOT_READ,           // gRPC не ответил нигде
    KNOWN_PORT,         // 🔴 Стандартный порт (10085) — любое приложение видит
    ACTIVE_PROBE,       // 🟡 Нестандартный порт — найден через сканирование
    CLASH_API           // Через Clash REST API
}
