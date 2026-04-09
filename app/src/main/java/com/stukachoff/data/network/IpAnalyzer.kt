package com.stukachoff.data.network

/**
 * Анализирует IP-адреса: определяет Cloudflare WARP.
 * Russian IP detection УБРАНА — prefix-based heuristic слишком ненадёжна
 * (178.17.x.x — европейский ДЦ, не Россия).
 * Для точного определения страны нужен GeoIP API.
 */
object IpAnalyzer {

    data class IpAnalysis(
        val isCloudflare: Boolean,
        val description: String
    )

    /**
     * Cloudflare IP ranges — надёжно документированы.
     * Exit IP в этих диапазонах = WARP обёртка активна.
     */
    private val CLOUDFLARE_PREFIXES = listOf(
        "104.16.", "104.17.", "104.18.", "104.19.", "104.20.", "104.21.",
        "104.22.", "104.23.", "104.24.", "104.25.", "104.26.", "104.27.",
        "104.28.", "104.29.", "104.30.", "104.31.",
        "172.64.", "172.65.", "172.66.", "172.67.", "172.68.", "172.69.",
        "172.70.", "172.71.",
        "103.21.244.", "103.21.245.", "103.21.246.", "103.21.247.",
        "103.22.200.", "103.22.201.", "103.22.202.", "103.22.203.",
        "141.101.",
        "108.162.",
        "190.93.",
        "188.114.",
        "198.41.",
        "162.158.", "162.159.",
    )

    fun analyze(ip: String): IpAnalysis {
        val isCloudflare = CLOUDFLARE_PREFIXES.any { ip.startsWith(it) }

        val description = when {
            isCloudflare -> "Cloudflare WARP — реальный IP сервера скрыт"
            else         -> "Exit IP"
        }

        return IpAnalysis(isCloudflare, description)
    }
}
