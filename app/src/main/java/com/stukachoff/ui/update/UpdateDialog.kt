package com.stukachoff.ui.update

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun UpdateDialog(
    state: UpdateUiState,
    onCheckUpdates: () -> Unit,
    onDownloadInstall: () -> Unit,
    onInstall: () -> Unit,
    onOpenGitHub: () -> Unit,
    onDismiss: () -> Unit
) {
    when (state) {
        is UpdateUiState.Checking -> {
            Dialog(onDismissRequest = {}) {
                Card {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Проверяю обновления...")
                    }
                }
            }
        }

        is UpdateUiState.UpdateAvailable -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Доступно обновление ${state.release.tagName}") },
                text = {
                    Column {
                        Text(
                            "APK будет скачан и проверен по SHA-256 перед установкой.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (state.release.releaseNotes.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                state.release.releaseNotes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = onDownloadInstall) { Text("Скачать и установить") }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text("Позже") }
                }
            )
        }

        is UpdateUiState.Downloading -> {
            Dialog(onDismissRequest = {}) {
                Card {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("Скачиваю обновление...", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { state.progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("${state.progress}%", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        is UpdateUiState.Verifying -> {
            Dialog(onDismissRequest = {}) {
                Card {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(state.message)
                        Text(
                            "Проверяем SHA-256 подпись файла",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        is UpdateUiState.ReadyToInstall -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("✅ Файл проверен") },
                text = {
                    Column {
                        Text("SHA-256 совпадает. Файл подлинный.")
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "⚠️ Если при установке появится «конфликт пакетов» — " +
                            "удали текущую версию вручную и установи новую.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = onInstall) { Text("Установить") }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                }
            )
        }

        is UpdateUiState.NoNetwork -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Режим приватности включён") },
                text = {
                    Text(
                        "Сетевые запросы отключены в настройках.\n\n" +
                        "Включи «Сетевой режим» в Меню → Настройки чтобы проверять " +
                        "обновления автоматически, или открой GitHub вручную."
                    )
                },
                confirmButton = {
                    Button(onClick = { onOpenGitHub(); onDismiss() }) {
                        Text("Открыть GitHub")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text("Закрыть") }
                }
            )
        }

        is UpdateUiState.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Ошибка") },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = onDismiss) { Text("OK") }
                }
            )
        }

        is UpdateUiState.UpToDate -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("✅ Актуальная версия") },
                text = { Text("Обновлений нет. Установлена последняя версия.") },
                confirmButton = {
                    TextButton(onClick = onDismiss) { Text("OK") }
                }
            )
        }

        else -> { /* Idle — ничего не показываем */ }
    }
}
