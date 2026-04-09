package com.stukachoff.domain.model

data class VpnConfig(
    val source: ConfigSource,
    val outbounds: List<OutboundConfig>
)

data class OutboundConfig(
    val protocol: String,        // vless, vmess, trojan, shadowsocks
    val serverAddress: String,   // shown to user with warning "goes to RKN blocklist"
    val serverPort: Int,
    val transport: String,       // tcp, ws, h2, grpc, xhttp
    val security: String,        // reality, tls, none
    val sni: String,
    val uuid: String,            // shown to user with warning "identifies you on server"
    val publicKey: String?,
    val tsupResistance: TsupLevel
)

enum class TsupLevel { HIGH, MEDIUM, LOW, BLOCKED }
enum class ConfigSource { XRAY_GRPC, CLASH_API, NOT_AVAILABLE }
