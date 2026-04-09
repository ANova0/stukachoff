package com.stukachoff.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.stukachoff.BuildConfig
import com.stukachoff.ui.update.UpdateDialog
import com.stukachoff.ui.update.UpdateUiState
import com.stukachoff.ui.update.UpdateViewModel

@Composable
fun SettingsScreen(
    onOpenOnboarding: () -> Unit,
    onOpenAbout: () -> Unit = {},
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel()
) {
    val privacyMode by settingsViewModel.privacyMode.collectAsState()
    val updateState by updateViewModel.state.collectAsState()
    var showUpdateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(updateState) {
        if (updateState !is UpdateUiState.Idle) showUpdateDialog = true
    }

    if (showUpdateDialog && updateState !is UpdateUiState.Idle) {
        UpdateDialog(
            state             = updateState,
            onCheckUpdates    = { updateViewModel.checkForUpdates() },
            onDownloadInstall = {
                (updateState as? UpdateUiState.UpdateAvailable)?.release
                    ?.let { updateViewModel.downloadAndInstall(it) }
            },
            onInstall    = {
                (updateState as? UpdateUiState.ReadyToInstall)?.apkFile
                    ?.let { updateViewModel.installApk(it) }
            },
            onOpenGitHub = { updateViewModel.openGitHubReleases() },
            onDismiss    = { showUpdateDialog = false }
        )
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Меню", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold)
            }
        }

        // Режим приватности — главный переключатель
        item {
            SettingsSection(title = "Режим работы") {
                PrivacyModeToggle(
                    enabled   = privacyMode,
                    onToggle  = { settingsViewModel.setPrivacyMode(it) }
                )
            }
        }

        item {
            SettingsSection(title = "Обновления") {
                SettingsItem(
                    icon     = Icons.Default.Refresh,
                    title    = "Проверить обновления",
                    subtitle = if (privacyMode) "Включи сетевой режим для автопроверки"
                               else "Текущая версия ${BuildConfig.VERSION_NAME}",
                    onClick  = {
                        showUpdateDialog = true
                        updateViewModel.checkForUpdates()
                    }
                )
            }
        }

        item {
            SettingsSection(title = "Справка") {
                SettingsItem(
                    icon    = Icons.Default.PlayArrow,
                    title   = "Как работает приложение",
                    subtitle = "Повторный просмотр онбординга",
                    onClick = onOpenOnboarding
                )
                SettingsItem(
                    icon     = Icons.Default.Info,
                    title    = "О приложении",
                    subtitle = "Версия ${BuildConfig.VERSION_NAME} · MIT · Open Source",
                    onClick  = onOpenAbout
                )
            }
        }

        item {
            SettingsSection(title = "Открытый код") {
                InfoCard(
                    title = "github.com/ANova0/stukachoff",
                    body  = "Код полностью открыт. Нет аналитических SDK, нет трекеров, " +
                            "нет фоновых процессов. Можешь собрать самостоятельно и сравнить SHA-256."
                )
            }
        }
    }
}

@Composable
fun PrivacyModeToggle(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (enabled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        if (enabled) "Режим приватности" else "Сетевой режим",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (enabled) "Никаких сетевых запросов" else "Exit IP + автообновление",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF4CAF50),
                    checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.3f)
                )
            )
        }

        Spacer(Modifier.height(8.dp))

        Surface(
            color = if (enabled) Color(0xFF4CAF50).copy(alpha = 0.1f)
                    else Color(0xFF2196F3).copy(alpha = 0.1f),
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                if (enabled)
                    "🔒 Приложение не делает сетевых запросов. Все проверки — только локально на устройстве."
                else
                    "🌐 Разрешены HTTPS-запросы: проверка exit IP через VPN-туннель и автообновление с GitHub.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp))
        Card(modifier = Modifier.fillMaxWidth()) { Column { content() } }
    }
}

@Composable
fun SettingsItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun InfoCard(title: String, body: String) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
