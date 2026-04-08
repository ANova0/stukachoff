package com.stukachoff.domain.checker

import com.stukachoff.domain.model.CheckResult

interface InterfaceChecker {
    suspend fun check(): InterfaceCheckResult
}

data class InterfaceCheckResult(
    val vpnInterfaces: List<VpnInterface>,
    val mtuResult: CheckResult.Fixable
)

data class VpnInterface(
    val name: String,
    val type: InterfaceType,
    val mtu: Int
)

enum class InterfaceType { TUN, WIREGUARD, TAP, PPP, IPSEC, NORMAL }

enum class MtuSignature { STANDARD, WIREGUARD, AMNEZIA, LOW_ANOMALY }

object MtuAnalyzer {
    fun analyze(mtu: Int): MtuSignature = when (mtu) {
        1500 -> MtuSignature.STANDARD
        1420 -> MtuSignature.WIREGUARD
        1280 -> MtuSignature.AMNEZIA
        else -> if (mtu < 1400) MtuSignature.LOW_ANOMALY else MtuSignature.STANDARD
    }
}
