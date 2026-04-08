package com.stukachoff.domain.model

data class AppThreat(
    val packageName: String,
    val appName: String,
    val version: String?,
    val threatLevel: ThreatLevel,
    val isInstalled: Boolean,
    val confirmedMethods: List<DetectionMethod>,
    val possibleMethods: List<DetectionMethod>,
    val harm: String,
    val source: String? = null
)

enum class ThreatLevel { RED, YELLOW, GREY }

enum class DetectionMethod {
    TRANSPORT_VPN,      // ConnectivityManager.TRANSPORT_VPN — без разрешений
    INTERFACE_NAME,     // java.net.NetworkInterface — без разрешений
    LOCALHOST_SCAN,     // Socket к 127.0.0.1 — без разрешений
    HTTP_PROBING,       // Пробы к заблокированным сайтам — нужен INTERNET
    PLMN_MCC,           // Код оператора SIM — нужен READ_PHONE_STATE
    GEOLOCATION,        // GeoIP запрос — нужен INTERNET
    PACKAGE_SCAN        // Список VPN-приложений — нужен QUERY_ALL_PACKAGES
}
