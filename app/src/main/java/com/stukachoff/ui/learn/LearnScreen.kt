package com.stukachoff.ui.learn

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stukachoff.ui.common.GlossaryText

// TODO: загружать из ContentRepository (threats.json) в следующих задачах
@Composable
fun LearnScreen(checkId: String) {
    val content = learnContent[checkId]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        if (content == null) {
            Text("Инструкция не найдена для: $checkId")
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
    }
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
        title = "Прокси-порт виден на устройстве",
        what = "VPN-клиент работает в режиме SOCKS5 или HTTP-прокси. " +
                "Локальный порт (10808, 2334 и др.) открыт и виден любому приложению " +
                "без разрешений — просто подключившись к 127.0.0.1.",
        harm = "Протокол идентифицирован. При brute force — могут подобрать credentials. " +
                "Враждебные приложения знают что VPN активен и какого типа.",
        fixes = listOf(
            ClientFix("Hiddify", "Настройки → Режим работы → TUN (полный режим)"),
            ClientFix("NekoBox", "Настройки → Режим VPN → TUN Mode"),
            ClientFix("v2rayNG", "Настройки → включить VPN Mode")
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
        title = "MTU fingerprint",
        what = "Размер пакетов (MTU) на VPN-интерфейсе отличается от стандартного (1500). " +
                "WireGuard использует 1420, некоторые настройки — 1280 и ниже. " +
                "По этому числу ТСПУ и приложения определяют тип VPN.",
        harm = "ТСПУ точнее идентифицирует протокол. Помогает при блокировке.",
        fixes = listOf(
            ClientFix("AmneziaWG", "Установить MTU = 1280 в настройках тоннеля"),
            ClientFix("xray TUN", "В конфиге: tun.mtu = 1500"),
            ClientFix("sing-box", "В конфиге: tun.mtu = 1500")
        )
    )
)
