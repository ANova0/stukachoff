package com.stukachoff.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stukachoff.BuildConfig

data class ChangelogEntry(
    val version: String,
    val date: String,
    val changes: List<String>
)

val changelog = listOf(
    ChangelogEntry(
        version = "2.0.1",
        date    = "Апрель 2026",
        changes = listOf(
            "Исправлен DNS-классификатор (1.1.1.1 через туннель ≠ утечка)",
            "Определение активного VPN-клиента без process signal",
            "Полное сканирование портов 1-65535",
            "Активное чтение VPN-конфига (gRPC + Clash API)",
            "Оценка устойчивости к ТСПУ",
            "Итоговый вердикт: защита от приложений + от ТСПУ",
            "Учебник защиты с инструкциями под активный клиент",
            "Показываются только проблемы — зелёные в деталях",
            "Русские названия проверок",
            "Split-tunnel детекция"
        )
    ),
    ChangelogEntry(
        version = "1.0.0",
        date    = "Апрель 2026",
        changes = listOf(
            "Первый публичный релиз",
            "37+ VPN-клиентов в базе",
            "Анимация расследования (радар)",
            "Автообновление с SHA-256 верификацией"
        )
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("О приложении") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Шапка
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("👁️", fontSize = 64.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Stukachoff",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Стукачев следит за стукачами",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "Версия ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Принципы
            item {
                AboutSection(title = "Принципы") {
                    PrincipleRow("🔒", "Нет INTERNET в базовых проверках",
                        "Все проверки VERIFY работают без сети — технически невозможно отправить данные")
                    PrincipleRow("👁️", "Открытый исходный код",
                        "Любой может проверить что делает приложение и собрать самостоятельно")
                    PrincipleRow("🚫", "Нет трекеров и аналитики",
                        "Нет Firebase, Amplitude, Crashlytics и других SDK слежки")
                    PrincipleRow("🛡️", "SHA-256 верификация обновлений",
                        "Каждое обновление проверяется по контрольной сумме перед установкой")
                }
            }

            // Ссылки
            item {
                AboutSection(title = "Ресурсы") {
                    LinkRow(
                        icon  = "💻",
                        title = "Исходный код",
                        url   = "https://github.com/ANova0/stukachoff",
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/ANova0/stukachoff")))
                        }
                    )
                    LinkRow(
                        icon  = "🐛",
                        title = "Сообщить о проблеме",
                        url   = "github.com/ANova0/stukachoff/issues",
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/ANova0/stukachoff/issues")))
                        }
                    )
                    LinkRow(
                        icon  = "📦",
                        title = "Скачать последнюю версию",
                        url   = "github.com/ANova0/stukachoff/releases",
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/ANova0/stukachoff/releases")))
                        }
                    )
                }
            }

            // Лицензия
            item {
                AboutSection(title = "Лицензия") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("MIT License", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Copyright © 2026 ANova0\n\n" +
                                "Разрешается свободное использование, копирование, изменение " +
                                "и распространение при сохранении уведомления об авторских правах.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Changelog
            item {
                AboutSection(title = "История версий") {
                    changelog.forEach { entry ->
                        ChangelogCard(entry)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun AboutSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

@Composable
fun PrincipleRow(icon: String, title: String, description: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Text(icon, fontSize = 20.sp, modifier = Modifier.padding(top = 2.dp, end = 12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun LinkRow(icon: String, title: String, url: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 20.sp, modifier = Modifier.padding(end = 12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(url, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("→", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun ChangelogCard(entry: ChangelogEntry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("v${entry.version}", fontWeight = FontWeight.Bold)
                Text(entry.date, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            entry.changes.forEach { change ->
                Row(modifier = Modifier.padding(vertical = 1.dp)) {
                    Text("•", modifier = Modifier.padding(end = 6.dp),
                        color = MaterialTheme.colorScheme.primary)
                    Text(change, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
