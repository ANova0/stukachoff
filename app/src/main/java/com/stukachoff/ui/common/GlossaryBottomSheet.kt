package com.stukachoff.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Словарь всех технических терминов
val glossary = mapOf(
    "gRPC" to GlossaryEntry(
        term = "gRPC API",
        simple = "Служебная дверца управления xray",
        full = "gRPC — это протокол удалённого вызова процедур. " +
                "xray открывает его на порту 10085 для управления собой. " +
                "Проблема: по умолчанию без пароля — любое приложение на телефоне " +
                "может подключиться и забрать IP сервера, UUID и ключи шифрования."
    ),
    "TUN" to GlossaryEntry(
        term = "TUN-режим",
        simple = "VPN на уровне сети, а не приложения",
        full = "TUN — виртуальный сетевой интерфейс. В TUN-режиме VPN перехватывает " +
                "весь трафик на уровне ядра Android. Преимущество: нет открытых " +
                "прокси-портов на localhost — сканировать нечего. " +
                "Hiddify и NekoBox используют TUN по умолчанию."
    ),
    "SOCKS5" to GlossaryEntry(
        term = "SOCKS5",
        simple = "Протокол прокси с открытым портом",
        full = "SOCKS5 — протокол прокси. Когда VPN-клиент работает в режиме SOCKS5, " +
                "он открывает локальный порт (например 10808) на 127.0.0.1. " +
                "Любое приложение без разрешений может подключиться к этому порту " +
                "и определить что VPN активен."
    ),
    "DNS" to GlossaryEntry(
        term = "DNS-утечка",
        simple = "Провайдер видит какие сайты ты посещаешь",
        full = "DNS — система перевода доменных имён в IP-адреса. " +
                "При DNS-утечке запросы типа 'что такое instagram.com?' " +
                "уходят напрямую к провайдеру, а не через VPN-туннель. " +
                "Провайдер видит полный список сайтов которые ты посещаешь " +
                "даже при активном VPN."
    ),
    "MTU" to GlossaryEntry(
        term = "MTU fingerprint",
        simple = "Размер пакетов выдаёт тип VPN",
        full = "MTU (Maximum Transmission Unit) — максимальный размер пакета данных. " +
                "Стандартная сеть: 1500 байт. " +
                "WireGuard: 1420 байт. AmneziaWG: 1280 байт. " +
                "По этому числу ТСПУ и приложения могут определить " +
                "какой именно VPN-протокол используется."
    ),
    "TRANSPORT_VPN" to GlossaryEntry(
        term = "TRANSPORT_VPN",
        simple = "Системный флаг — VPN активен",
        full = "TRANSPORT_VPN — флаг в Android ConnectivityManager. " +
                "Ядро системы выставляет его когда любой VPN активен. " +
                "Любое приложение с разрешением ACCESS_NETWORK_STATE (есть у большинства) " +
                "может его прочитать. Скрыть без root невозможно — " +
                "это фундаментальная архитектура Android VpnService."
    ),
    "UUID" to GlossaryEntry(
        term = "UUID",
        simple = "Твой уникальный ключ на VPN-сервере",
        full = "UUID (Universally Unique Identifier) — уникальный идентификатор " +
                "твоего аккаунта на VPN-сервере в протоколах VLESS и VMess. " +
                "Это как логин. Если UUID утёк — сервер точно знает кто ты, " +
                "и при желании может тебя идентифицировать."
    ),
    "SNI" to GlossaryEntry(
        term = "SNI",
        simple = "Маскировочный домен в TLS-запросе",
        full = "SNI (Server Name Indication) — поле в TLS-запросе где указывается " +
                "к какому сайту обращаешься. Reality использует SNI легитимного сайта " +
                "(например icloud.com) чтобы VPN-трафик выглядел как обычный HTTPS. " +
                "Если SNI утёк — видно под какой сайт маскируется твой VPN."
    ),
    "Reality" to GlossaryEntry(
        term = "XTLS-Reality",
        simple = "Протокол маскировки под легитимный HTTPS",
        full = "Reality — технология маскировки в xray/VLESS. " +
                "Использует настоящий TLS-сертификат реального сайта (например Apple) " +
                "чтобы VPN-трафик был неотличим от обычного HTTPS на сетевом уровне. " +
                "Защищает от ТСПУ/DPI, но не от детекции внутри устройства."
    ),
    "split-tunnel" to GlossaryEntry(
        term = "Split-tunnel",
        simple = "Часть приложений идёт мимо VPN",
        full = "Split-tunnel (раздельное туннелирование) — режим когда одни приложения " +
                "идут через VPN, а другие — напрямую. " +
                "Важно: даже если конкретное приложение в bypass, " +
                "оно всё равно видит TRANSPORT_VPN через getAllNetworks() — " +
                "факт VPN не скрыть даже от приложений в bypass."
    ),
    "MCC" to GlossaryEntry(
        term = "MCC (код оператора)",
        simple = "Идентификатор страны твоего оператора",
        full = "MCC (Mobile Country Code) — трёхзначный код страны мобильного оператора. " +
                "Российские операторы: МТС=25001, МегаФон=25002, Билайн=25099, Tele2=25020. " +
                "Если у приложения есть разрешение READ_PHONE_STATE, " +
                "оно видит MCC и может скоррелировать: российская SIM + зарубежный IP = VPN."
    ),
    "ТСПУ" to GlossaryEntry(
        term = "ТСПУ",
        simple = "DPI-оборудование у провайдеров для блокировок",
        full = "ТСПУ (Технические Средства Противодействия Угрозам) — " +
                "оборудование глубокой инспекции пакетов (DPI) установленное " +
                "у всех российских провайдеров по требованию Роскомнадзора. " +
                "Анализирует трафик в реальном времени, блокирует VPN-протоколы " +
                "по сигнатурам, поведенческому анализу и TLS-fingerprinting."
    )
)

data class GlossaryEntry(
    val term: String,
    val simple: String,
    val full: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlossaryBottomSheet(
    entry: GlossaryEntry,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                entry.term,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    entry.simple,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                entry.full,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp
            )
        }
    }
}

// Composable для кликабельного термина в тексте
@Composable
fun GlossaryText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium
) {
    var selectedEntry by remember { mutableStateOf<GlossaryEntry?>(null) }

    // Находим термины в тексте и делаем их кликабельными
    val annotatedText = buildAnnotatedString {
        var lastIndex = 0
        val sortedKeys = glossary.keys.sortedByDescending { it.length }

        // Простой поиск — находим первое вхождение любого термина
        val matches = mutableListOf<Pair<IntRange, String>>()
        sortedKeys.forEach { key ->
            var idx = text.indexOf(key, ignoreCase = true)
            while (idx != -1) {
                val range = idx until idx + key.length
                if (matches.none { it.first.overlaps(range) }) {
                    matches.add(range to key)
                }
                idx = text.indexOf(key, idx + 1, ignoreCase = true)
            }
        }
        matches.sortBy { it.first.first }

        matches.forEach { (range, key) ->
            append(text.substring(lastIndex, range.first))
            pushStringAnnotation("glossary", key)
            withStyle(SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                fontWeight = FontWeight.Medium
            )) {
                append(text.substring(range))
            }
            pop()
            lastIndex = range.last + 1
        }
        if (lastIndex < text.length) append(text.substring(lastIndex))
    }

    ClickableText(
        text = annotatedText,
        style = style.copy(color = MaterialTheme.colorScheme.onSurface),
        modifier = modifier,
        onClick = { offset ->
            annotatedText.getStringAnnotations("glossary", offset, offset)
                .firstOrNull()?.let { annotation ->
                    selectedEntry = glossary[annotation.item]
                }
        }
    )

    selectedEntry?.let { entry ->
        GlossaryBottomSheet(entry = entry, onDismiss = { selectedEntry = null })
    }
}

private fun IntRange.overlaps(other: IntRange) =
    first <= other.last && last >= other.first
