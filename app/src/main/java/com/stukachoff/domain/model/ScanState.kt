package com.stukachoff.domain.model

data class ScanState(
    val vpnStatus: VpnStatus = VpnStatus.UNKNOWN,
    val alwaysVisible: List<CheckResult.AlwaysVisible> = emptyList(),
    val fixable: List<CheckResult.Fixable> = emptyList(),
    val isScanning: Boolean = false,
    val error: String? = null
)

enum class VpnStatus {
    UNKNOWN,
    NOT_ACTIVE,
    USER_VPN,      // VpnService-based: Hiddify, v2rayNG, NekoBox и т.д.
    CORPORATE_VPN  // MDM/Enterprise VPN
}
