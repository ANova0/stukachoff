package com.stukachoff.data.network

import com.stukachoff.domain.checker.InterfaceCheckResult
import com.stukachoff.domain.checker.InterfaceChecker
import com.stukachoff.domain.checker.InterfaceType
import com.stukachoff.domain.checker.MtuAnalyzer
import com.stukachoff.domain.checker.MtuSignature
import com.stukachoff.domain.checker.VpnInterface
import com.stukachoff.domain.model.CheckResult
import com.stukachoff.domain.model.CheckStatus
import com.stukachoff.domain.model.HarmSeverity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.NetworkInterface

class InterfaceCheckerImpl : InterfaceChecker {

    fun classifyInterfaceName(name: String): InterfaceType {
        val n = name.lowercase()
        return when {
            n.startsWith("tun")   -> InterfaceType.TUN
            n.startsWith("wg")    -> InterfaceType.WIREGUARD
            n.startsWith("tap")   -> InterfaceType.TAP
            n.startsWith("ppp")   -> InterfaceType.PPP
            n.startsWith("ipsec") -> InterfaceType.IPSEC
            n.startsWith("xfrm")  -> InterfaceType.IPSEC
            else -> InterfaceType.NORMAL
        }
    }

    override suspend fun check(): InterfaceCheckResult = withContext(Dispatchers.IO) {
        val interfaces = runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
        }.getOrDefault(emptyList())

        val vpnIfaces = interfaces
            .filter { classifyInterfaceName(it.name) != InterfaceType.NORMAL }
            .map { VpnInterface(it.name, classifyInterfaceName(it.name), it.mtu) }

        val mtu = vpnIfaces.minOfOrNull { it.mtu } ?: 1500
        val mtuSignature = MtuAnalyzer.analyze(mtu)

        val mtuStatus = when (mtuSignature) {
            MtuSignature.STANDARD -> CheckStatus.GREEN
            MtuSignature.WIREGUARD -> CheckStatus.YELLOW
            MtuSignature.AMNEZIA, MtuSignature.LOW_ANOMALY -> CheckStatus.RED
        }

        InterfaceCheckResult(
            vpnInterfaces = vpnIfaces,
            mtuResult = CheckResult.Fixable(
                id = "mtu",
                title = "Размер пакетов",
                status = mtuStatus,
                harm = "ТСПУ и приложения определяют тип VPN-протокола по размеру пакетов",
                harmSeverity = HarmSeverity.MEDIUM
            )
        )
    }
}
