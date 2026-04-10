package com.stukachoff.ui.learn

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stukachoff.ui.common.GlossaryText

// TODO: загружать из ContentRepository (threats.json) в следующих задачах
// Имя Telegram-бота для deep link — замени на реальный username
private const val BOT_USERNAME = "YOUR_BOT_NAME"
private const val BOT_DEEP_LINK_BASE = "https://t.me/$BOT_USERNAME?start=stukachoff_fix_"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnScreen(checkId: String, onBack: (() -> Unit)? = null) {
    val content = learnContent[checkId]
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Как исправить") },
                navigationIcon = {
                    IconButton(onClick = {
                        onBack?.invoke() ?: (context as? Activity)?.onBackPressed()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        if (content == null) {
            Text("Инструкция для \"$checkId\" ещё не создана",
                style = MaterialTheme.typography.bodyLarge)
            return@Column
        }

        Text(content.title, style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        GlossaryText(content.what, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Чем грозит", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(content.harm)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Как исправить", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        content.fixes.forEach { fix ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(fix.client, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(fix.instruction, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Deep link к боту — только если бот настроен
        if (BOT_USERNAME != "YOUR_BOT_NAME" &&
            checkId in listOf("grpc_api", "proxy_mode", "dns_leak", "clash_api")) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text(
                "Используете подписку?",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = {
                    val url = "$BOT_DEEP_LINK_BASE$checkId"
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Получить исправленный конфиг через бот →")
            }
        }
    }
    } // Scaffold
}

data class LearnContent(
    val title: String,
    val what: String,
    val harm: String,
    val fixes: List<ClientFix>
)

data class ClientFix(val client: String, val instruction: String)

val learnContent = mapOf(
    "grpc_api" to LearnContent(
        title = "xray gRPC API открыт",
        what = "xray открывает служебный порт (10085) для управления. Любое приложение " +
                "на устройстве может подключиться к нему и забрать адрес сервера, " +
                "UUID и ключи шифрования без какого-либо пароля.",
        harm = "Сбер, ВК или Ozon получат IP твоего VPN-сервера и передадут в РКН. " +
                "Сервер попадёт в блоклист — перестанет работать у всех пользователей.",
        fixes = listOf(
            ClientFix("Hiddify",
                "Настройки → Ядро → Дополнительные настройки → Отключить API управления"),
            ClientFix("NekoBox",
                "Настройки → Настройки ядра → API → снять галочку Enable API"),
            ClientFix("v2rayNG",
                "Настройки → Настройки приложения → убедиться что Local SOCKs Port не 10085"),
            ClientFix("Получили конфиг по подписке?",
                "Обратитесь к провайдеру VPN — попросите конфиг без gRPC API. " +
                        "Правильно настроенный конфиг не содержит секцию \"api\".")
        )
    ),
    "proxy_mode" to LearnContent(
        title = "Критическая уязвимость: открытый прокси",
        what = "Все VLESS-клиенты (Hiddify, v2rayNG, V2Box, NekoBox и др.) " +
                "запускают SOCKS5-прокси на localhost без аутентификации.\n\n" +
                "Проблема: любое приложение на устройстве может подключиться " +
                "к этому прокси НАПРЯМУЮ, обойдя VPN-туннель (VpnService). " +
                "Через прокси стукач определяет реальный IP VPN-сервера.\n\n" +
                "Рабочий профиль (Knox, Shelter, Island) НЕ защищает — " +
                "loopback-интерфейс (127.0.0.1) общий для всех профилей.",
        harm = "Стукач подключается к прокси → отправляет запрос через VPN-туннель → " +
                "получает IP VPN-сервера → отправляет в РКН → сервер в блоклисте.\n\n" +
                "Это работает даже если приложение-стукач исключено из VPN routing.",
        fixes = listOf(
            ClientFix("WARP обёртка", "Лучшая защита: используй Cloudflare WARP поверх VPN. " +
                    "Даже если стукач найдёт прокси — увидит IP Cloudflare, а не реального сервера."),
            ClientFix("Hiddify", "Настройки → Режим работы → TUN (убирает SOCKS5 порт). " +
                    "Но TUN не защищает от прямого подключения к xray API."),
            ClientFix("v2rayNG", "Настройки → VPN Mode → включить. Порт 10808 закроется."),
            ClientFix("NekoBox", "Настройки → TUN Mode → включить."),
            ClientFix("Поддержка", "Попроси провайдера добавить WARP-обёртку в конфиг — " +
                    "это единственная надёжная защита от данной уязвимости.")
        )
    ),
    "dns_leak" to LearnContent(
        title = "DNS-утечка",
        what = "DNS-запросы (\"что такое google.com?\") уходят через провайдера, " +
                "а не через VPN-туннель. Провайдер видит полный список сайтов которые ты посещаешь.",
        harm = "Провайдер знает твою историю посещений даже при активном VPN. " +
                "Это видит ТСПУ и любое приложение с доступом к сети.",
        fixes = listOf(
            ClientFix("Hiddify", "Настройки → DNS → выбрать Remote DNS (8.8.8.8 через туннель)"),
            ClientFix("NekoBox", "Настройки → FakeIP DNS → включить"),
            ClientFix("v2rayNG", "Настройки → DNS → Remote DNS server")
        )
    ),
    "android_version" to LearnContent(
        title = "Устаревшая версия Android",
        what = "На Android 9 и ниже любое приложение может читать /proc/net/tcp — " +
                "список всех активных TCP-соединений на устройстве. " +
                "На Android 10+ это заблокировано через SELinux.",
        harm = "Любое приложение видит IP всех серверов к которым ты подключён: " +
                "VPN-серверы, SSH-сессии на VPS, корпоративные серверы.",
        fixes = listOf(
            ClientFix("Решение", "Обновить Android до версии 10 или выше. " +
                    "Если устройство не поддерживает обновление — рассмотреть замену.")
        )
    ),
    "work_profile" to LearnContent(
        title = "Work Profile и изоляция",
        what  = "Work Profile (рабочий профиль) позволяет держать приложения " +
                "в изолированной песочнице. VPN-клиент в отдельном профиле " +
                "сложнее обнаружить враждебным приложениям в основном профиле.",
        harm  = "Без изоляции — VK, Сбер и другие приложения находятся " +
                "в одном профиле с VPN-клиентом и видят его напрямую.",
        fixes = listOf(
            ClientFix("Shelter (F-Droid)", "Установи Shelter, перенеси VPN-клиент в рабочий профиль"),
            ClientFix("Island (Google Play)", "Аналог Shelter — клонирует приложения в изолированный профиль"),
            ClientFix("Samsung Knox", "На Samsung устройствах — используй Secure Folder для VPN-клиента")
        )
    ),
    "exit_ip" to LearnContent(
        title = "Exit IP через туннель",
        what  = "Проверяет реальный IP-адрес который видят внешние серверы " +
                "когда трафик идёт через VPN. Запрос уходит через туннель — " +
                "ТСПУ видит только зашифрованный трафик.",
        harm  = "Если запрос не прошёл через туннель — VPN не перехватывает " +
                "трафик приложения. Включи сетевой режим в настройках для проверки.",
        fixes = listOf(
            ClientFix("Проверка", "Включи Сетевой режим в Меню → Настройки"),
            ClientFix("Если красный", "VPN-клиент не перехватывает трафик — проверь режим TUN")
        )
    ),
    "mtu" to LearnContent(
        title = "Размер пакетов (MTU)",
        what = "Размер пакетов (MTU) на VPN-интерфейсе отличается от стандартного (1500). " +
                "WireGuard использует 1420, AmneziaWG — 1280. " +
                "По этому числу ТСПУ определяет тип VPN-протокола.",
        harm = "ТСПУ точнее идентифицирует протокол и может его заблокировать.",
        fixes = listOf(
            ClientFix("AmneziaWG", "MTU = 1280 — это нормально для AmneziaWG"),
            ClientFix("WireGuard", "Настройки туннеля → MTU = 1280 (менее заметно)"),
            ClientFix("Hiddify", "Настройки → tun.mtu = 1500 (стандартный)")
        )
    ),
    "split_tunnel" to LearnContent(
        title = "Раздельное туннелирование",
        what = "Если весь трафик идёт через VPN (full tunnel), то приложения-стукачи " +
                "(VK, Сбер, Ozon) тоже проходят через туннель. Они делают HTTP-пробы " +
                "к заблокированным сайтам — сайты доступны → стукач подтверждает VPN.\n\n" +
                "Рекомендуемая настройка: российские приложения идут мимо VPN (split-tunnel). " +
                "Тогда HTTP-пробы стукачей идут напрямую → сайты заблокированы → стукач не может подтвердить VPN.",
        harm = "При full tunnel стукачи подтверждают VPN через HTTP-пробы к заблокированным сайтам.",
        fixes = listOf(
            ClientFix("Hiddify", "Настройки → Маршрутизация → Раздельная маршрутизация (RU direct, остальное proxy)"),
            ClientFix("v2rayNG", "Настройки → Per-app proxy → исключить российские приложения"),
            ClientFix("NekoBox", "Routing → Российские IP/домены direct, остальное proxy"),
            ClientFix("AmneziaVPN", "Настройки → Раздельное туннелирование → включить"),
            ClientFix("Поддержка", "Попроси провайдера настроить маршрутизацию: RU → direct, остальное → proxy")
        )
    ),
    "vpn_works" to LearnContent(
        title = "VPN не работает",
        what = "VPN-клиент подключён, но трафик не маршрутизируется через туннель. " +
                "Это может быть ошибка TLS, проблема с DNS или сбой сервера.",
        harm = "Весь трафик идёт напрямую — VPN не защищает. IP виден провайдеру.",
        fixes = listOf(
            ClientFix("Проверь", "Переподключи VPN — возможно сервер временно недоступен"),
            ClientFix("Hiddify", "Попробуй другой сервер или протокол"),
            ClientFix("WireGuard", "Проверь что endpoint доступен"),
            ClientFix("Поддержка", "Напиши провайдеру — возможно сервер заблокирован ТСПУ")
        )
    ),
    "clash_api" to LearnContent(
        title = "API конфигурации открыт",
        what = "Clash/Mihomo REST API открыт на порту 9090 без пароля. " +
                "Через него доступна полная конфигурация и список всех активных соединений — " +
                "какие сайты ты открывал через VPN.",
        harm = "Враждебные приложения видят историю соединений и IP всех серверов.",
        fixes = listOf(
            ClientFix("Clash", "Настройки → External Controller → установить secret"),
            ClientFix("FlClash", "Настройки → API → установить пароль"),
            ClientFix("sing-box", "config.json → experimental.clash_api.secret"),
            ClientFix("Поддержка", "Попросите добавить secret в Clash API конфига")
        )
    ),
    "system_proxy" to LearnContent(
        title = "Системный прокси обнаружен",
        what = "VPN-клиент установил системный прокси (http.proxyHost). " +
                "Этот адрес виден любому приложению через System.getProperty() без разрешений.",
        harm = "Любое приложение узнаёт IP и порт прокси без разрешений.",
        fixes = listOf(
            ClientFix("Решение", "Переключи VPN-клиент в TUN-режим — системный прокси не используется"),
            ClientFix("Hiddify", "Настройки → Режим работы → VPN (TUN)"),
            ClientFix("v2rayNG", "Настройки → VPN mode → включить")
        )
    )
)
