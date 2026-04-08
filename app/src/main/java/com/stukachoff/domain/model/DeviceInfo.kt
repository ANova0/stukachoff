package com.stukachoff.domain.model

data class DeviceInfo(
    val androidVersion: String,
    val sdkInt: Int,
    val manufacturer: String,
    val model: String,
    val vpnInterfaces: List<InterfaceInfo>,
    val installedVpnClients: List<String>
)

data class InterfaceInfo(
    val name: String,
    val addresses: List<String>,
    val mtu: Int
)
