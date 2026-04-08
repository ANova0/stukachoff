package com.stukachoff.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stukachoff.BuildConfig

@Composable
fun SettingsScreen(onOpenOnboarding: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Меню",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item {
            SettingsSection(title = "Справка") {
                SettingsItem(
                    icon = Icons.Default.PlayArrow,
                    title = "Как работает приложение",
                    subtitle = "Повторный просмотр онбординга",
                    onClick = onOpenOnboarding
                )
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "О приложении",
                    subtitle = "Версия ${BuildConfig.VERSION_NAME}",
                    onClick = {}
                )
            }
        }

        item {
            SettingsSection(title = "Безопасность") {
                InfoCard(
                    title = "Core версия",
                    body = "Это core-версия приложения. Разрешение INTERNET отсутствует — " +
                            "технически невозможно отправить данные на внешние серверы."
                )
            }
        }

        item {
            SettingsSection(title = "Исходный код") {
                InfoCard(
                    title = "Open Source",
                    body = "Код полностью открыт на GitHub. Можешь проверить что именно " +
                            "делает приложение — нет ни одного скрытого SDK или трекера."
                )
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column { content() }
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun InfoCard(title: String, body: String) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
