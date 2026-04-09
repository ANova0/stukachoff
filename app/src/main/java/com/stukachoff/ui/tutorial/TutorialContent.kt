package com.stukachoff.ui.tutorial

import com.stukachoff.data.apps.VpnEngine

data class TutorialStep(
    val title: String,
    val description: String,
    val instructions: Map<String, String> // clientName -> instruction
)

data class VulnerabilityFix(
    val title: String,
    val description: String,
    val clientInstructions: Map<String, String>, // client name -> instruction
    val supportMessage: String  // copy-paste text to send to VPN support
)

data class TsupAdvice(
    val problem: String,
    val solution: String,
    val steps: List<String>
)

val BASE_STEPS = listOf(
    TutorialStep(
        title = "Шаг 1: Включи TUN-режим",
        description = "В TUN-режиме VPN работает на уровне сети — нет открытых прокси-портов " +
                "которые обнаруживают стукачи. Это самый важный шаг.",
        instructions = mapOf(
            "Hiddify"     to "Настройки → Режим работы → VPN (TUN)",
            "v2rayNG"     to "Настройки → VPN mode → включить",
            "NekoBox"     to "Настройки → TUN Mode → включить",
            "V2Box"       to "Настройки → TUN → включить",
            "V2RayTun"    to "TUN-режим активен по умолчанию",
            "AmneziaVPN"  to "TUN-режим активен по умолчанию",
            "AmneziaWG"   to "TUN-режим активен по умолчанию",
            "WireGuard"   to "TUN-режим активен по умолчанию",
            "OpenVPN"     to "TUN-режим активен по умолчанию",
            "Psiphon"     to "TUN-режим активен по умолчанию",
            "Orbot"       to "TUN-режим активен по умолчанию"
        )
    ),
    TutorialStep(
        title = "Шаг 2: Все приложения через VPN",
        description = "Если часть приложений идёт мимо VPN (split-tunnel), они делают " +
                "HTTP-запросы напрямую и могут определить что у тебя VPN по доступности заблокированных сайтов.",
        instructions = mapOf(
            "Hiddify"    to "Настройки → Маршрутизация → Route all traffic",
            "v2rayNG"    to "Настройки → Per-app proxy → отключить или выбрать Proxy All",
            "NekoBox"    to "Route all apps through proxy",
            "V2Box"      to "Настройки → Routing → All traffic",
            "WireGuard"  to "Конфигурация туннеля → AllowedIPs = 0.0.0.0/0, ::/0",
            "AmneziaWG"  to "Конфигурация → AllowedIPs = 0.0.0.0/0, ::/0",
            "AmneziaVPN" to "Настройки → Маршрутизировать весь трафик"
        )
    ),
    TutorialStep(
        title = "Шаг 3: DNS через туннель",
        description = "Без этого настройки все DNS-запросы (\"какой IP у google.com?\") " +
                "идут через провайдера — он видит список всех сайтов которые ты посещаешь.",
        instructions = mapOf(
            "Hiddify"    to "Настройки → DNS → Remote DNS Server (вкл)",
            "v2rayNG"    to "Настройки → DNS → Remote DNS server = 1.1.1.1",
            "NekoBox"    to "Настройки → FakeIP DNS Mode → включить",
            "V2Box"      to "Настройки → DNS → Remote",
            "WireGuard"  to "Конфигурация туннеля → DNS = 1.1.1.1",
            "AmneziaWG"  to "Конфигурация → DNS = 1.1.1.1",
            "AmneziaVPN" to "Настройки → DNS через VPN"
        )
    )
)

val VULNERABILITY_FIXES: Map<String, VulnerabilityFix> = mapOf(
    "grpc_api" to VulnerabilityFix(
        title = "Закрыть gRPC API",
        description = "gRPC API открыт без пароля — любое приложение читает твой конфиг: " +
                "IP сервера, UUID, ключи шифрования.",
        clientInstructions = mapOf(
            "Hiddify"    to "Настройки → Ядро → Дополнительно → Отключить API управления",
            "v2rayNG"    to "Настройки → убедись что в конфиге нет секции \"api\"",
            "NekoBox"    to "Настройки ядра → API → снять Enable API",
            "V2Box"      to "Настройки → xray config → удалить секцию \"api\"",
            "sing-box"   to "config.json → удалить блок \"v2ray_api\" из experimental"
        ),
        supportMessage = "Пожалуйста отключите gRPC API в конфигах которые вы выдаёте клиентам. " +
                "Открытый порт 10085 позволяет любому приложению на устройстве читать конфиг."
    ),
    "clash_api" to VulnerabilityFix(
        title = "Защитить Clash API",
        description = "Clash REST API без пароля раскрывает полную конфигурацию и все активные соединения.",
        clientInstructions = mapOf(
            "Clash for Android" to "Настройки → External Controller → установить secret",
            "FlClash"           to "Настройки → API → установить пароль",
            "sing-box"          to "config.json → experimental.clash_api.secret = \"ваш_пароль\""
        ),
        supportMessage = "Пожалуйста добавьте secret в секцию external-controller вашего Clash конфига. " +
                "Открытый порт 9090 без пароля раскрывает историю соединений."
    ),
    "dns_leak" to VulnerabilityFix(
        title = "Устранить DNS-утечку",
        description = "DNS-запросы идут мимо VPN-туннеля — провайдер видит все посещаемые домены.",
        clientInstructions = mapOf(
            "Hiddify"    to "Настройки → DNS → Remote DNS Server → включить",
            "v2rayNG"    to "Настройки → DNS → Remote DNS server = 1.1.1.1",
            "NekoBox"    to "Настройки → FakeIP DNS Mode → включить",
            "WireGuard"  to "Настройки туннеля → DNS = 1.1.1.1",
            "AmneziaWG"  to "Настройки туннеля → DNS = 1.1.1.1"
        ),
        supportMessage = "Пожалуйста настройте DNS через туннель в клиентских конфигах."
    ),
    "split_tunnel" to VulnerabilityFix(
        title = "Маршрутизировать весь трафик",
        description = "Приложения в bypass идут напрямую и могут обнаружить VPN по HTTP-пробам.",
        clientInstructions = mapOf(
            "Hiddify"    to "Настройки → Маршрутизация → Route all traffic",
            "v2rayNG"    to "Настройки → Per-app proxy → Proxy All",
            "NekoBox"    to "Route all apps through proxy",
            "WireGuard"  to "AllowedIPs = 0.0.0.0/0, ::/0",
            "AmneziaWG"  to "AllowedIPs = 0.0.0.0/0, ::/0"
        ),
        supportMessage = "Как настроить маршрутизацию всего трафика через VPN без исключений?"
    )
)

val TSPU_ADVICE: Map<VpnEngine, TsupAdvice> = mapOf(
    VpnEngine.WIREGUARD to TsupAdvice(
        problem  = "WireGuard блокируется ТСПУ с декабря 2025 по UDP handshake сигнатуре",
        solution = "Переключись на AmneziaWG — это WireGuard с шифрованием служебных пакетов",
        steps    = listOf(
            "Скачай AmneziaVPN или AmneziaWG из Google Play",
            "Попроси провайдера предоставить AmneziaWG конфиг",
            "Или импортируй WireGuard конфиг — AmneziaVPN совместим",
            "Включи Junk packets в настройках (Jc=4)"
        )
    ),
    VpnEngine.OPENVPN to TsupAdvice(
        problem  = "OpenVPN заблокирован ТСПУ — сигнатура определяется мгновенно",
        solution = "Смени протокол на VLESS+Reality (Hiddify) или AmneziaWG",
        steps    = listOf(
            "Обратись в поддержку VPN-провайдера",
            "Попроси конфиg VLESS+Reality или AmneziaWG конфиг",
            "Установи Hiddify из Google Play и импортируй новый конфиг"
        )
    )
)
