package com.stukachoff.domain.checker

import com.stukachoff.domain.model.CheckResult
import com.stukachoff.domain.model.CheckStatus
import com.stukachoff.domain.model.HarmSeverity

interface PortScanner {
    suspend fun scan(): PortScanResult
    suspend fun fullScan(): List<OpenPort>
}

data class PortScanResult(
    val openKnownPorts: List<OpenPort>,
    val grpcApiResult: CheckResult.Fixable,
    val clashApiResult: CheckResult.Fixable,
    val proxyModeResult: CheckResult.Fixable
)

data class OpenPort(
    val port: Int,
    val category: PortCategory,
    val description: String
)

enum class PortCategory { XRAY_GRPC, CLASH_API, SOCKS5, HTTP_PROXY, MIXED, UNKNOWN }

object PortCategorizer {
    private val knownPorts = mapOf(
        10085 to (PortCategory.XRAY_GRPC to "xray gRPC API"),
        19085 to (PortCategory.XRAY_GRPC to "xray gRPC API (Marzban)"),
        23456 to (PortCategory.XRAY_GRPC to "xray gRPC API (Hiddify)"),
        9090  to (PortCategory.CLASH_API to "Clash REST API"),
        9091  to (PortCategory.CLASH_API to "Clash REST API"),
        19090 to (PortCategory.CLASH_API to "Clash REST API"),
        10808 to (PortCategory.SOCKS5 to "v2rayNG SOCKS5"),
        10809 to (PortCategory.HTTP_PROXY to "v2rayNG HTTP"),
        7891  to (PortCategory.SOCKS5 to "Clash SOCKS5"),
        7890  to (PortCategory.HTTP_PROXY to "Clash HTTP"),
        2334  to (PortCategory.SOCKS5 to "Hiddify/NekoBox SOCKS5"),
        2333  to (PortCategory.HTTP_PROXY to "Hiddify HTTP"),
        1080  to (PortCategory.MIXED to "sing-box Mixed"),
    )

    fun categorize(port: Int): PortCategory = knownPorts[port]?.first ?: PortCategory.UNKNOWN
    fun describe(port: Int): String = knownPorts[port]?.second ?: "Unknown service"

    val grpcPorts = setOf(10085, 19085, 23456)
    val clashPorts = setOf(9090, 9091, 19090)
    val proxyPorts = setOf(10808, 10809, 7891, 7890, 2334, 2333, 1080)
}
