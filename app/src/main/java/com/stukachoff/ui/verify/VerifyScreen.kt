package com.stukachoff.ui.verify

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.stukachoff.domain.model.*
import com.stukachoff.ui.common.GlossaryText
import android.os.Build

@Composable
fun VerifyScreen(
    viewModel: VerifyViewModel = hiltViewModel(),
    onLearnMore: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val showScanning by viewModel.showScanning.collectAsState()
    val recheckingId by viewModel.recheckingId.collectAsState()

    if (showScanning) {
        ScanningScreen()
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item { VpnStatusBanner(state.vpnStatus, state.isScanning) }

        if (state.vpnStatus == VpnStatus.NOT_ACTIVE) {
            item { NotActiveMessage() }
            return@LazyColumn
        }

        if (state.alwaysVisible.isNotEmpty()) {
            item { AlwaysVisibleSection(state.alwaysVisible, state.deviceInfo) }
        }

        if (state.fixable.isNotEmpty()) {
            item {
                Text(
                    "Можно защитить",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                )
            }
            items(state.fixable) { check ->
                CheckCard(
                    check        = check,
                    isRechecking = recheckingId == check.id,
                    onLearnMore  = onLearnMore,
                    onRecheck    = { viewModel.recheckSingle(check.id) }
                )
            }

            // Технические детали — полный дамп
            item { TechnicalDetailsCard(state) }
        }

        item {
            Button(
                onClick = { viewModel.scan() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                enabled = !state.isScanning
            ) {
                if (state.isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (state.isScanning) "Проверяю..." else "Проверить снова")
            }
        }
    }
}

@Composable
fun VpnStatusBanner(status: VpnStatus, isScanning: Boolean) {
    val (color, text) = when (status) {
        VpnStatus.USER_VPN      -> Color(0xFF1B5E20) to "VPN активен"
        VpnStatus.CORPORATE_VPN -> Color(0xFF1565C0) to "Корпоративный VPN"
        VpnStatus.NOT_ACTIVE    -> Color(0xFFB71C1C) to "VPN не обнаружен"
        VpnStatus.UNKNOWN       -> Color(0xFF424242) to "Определяю..."
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color)
            .padding(16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun NotActiveMessage() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Запусти VPN и нажми «Проверить снова»",
                style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Stukachoff показывает реальную картину только при активном VPN-соединении.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AlwaysVisibleSection(
    checks: List<CheckResult.AlwaysVisible>,
    deviceInfo: DeviceInfo? = null
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Это видят все приложения",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Скрыть без root невозможно — это архитектура Android",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Краткая сводка всегда видна
            deviceInfo?.let { info ->
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))
                DeviceInfoSummary(info)
            }

            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Скрыть детали ↑" else "Что именно видят? ↓")
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    checks.forEach { check ->
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        Text(check.title, fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium)
                        Text("✅ Знают: ${check.knowsWhat}",
                            style = MaterialTheme.typography.bodySmall)
                        Text("❌ Не знают: ${check.doesntKnow}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(check.explanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceInfoSummary(info: DeviceInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        InfoRow("Устройство", "${info.manufacturer} ${info.model}")
        InfoRow("Android", "${info.androidVersion} (API ${info.sdkInt})")
        info.vpnInterfaces.forEach { iface ->
            InfoRow(iface.name, iface.addresses.joinToString(", ").ifBlank { "—" })
        }
        if (info.installedVpnClients.isNotEmpty()) {
            InfoRow("VPN-клиент", info.installedVpnClients.joinToString(", "))
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            "$label:",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(100.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun CheckCard(
    check: CheckResult.Fixable,
    isRechecking: Boolean = false,
    onLearnMore: (String) -> Unit,
    onRecheck: () -> Unit = {}
) {
    val statusColor = when (check.status) {
        CheckStatus.GREEN  -> Color(0xFF4CAF50)
        CheckStatus.YELLOW -> Color(0xFFFF9800)
        CheckStatus.RED    -> Color(0xFFF44336)
    }
    val statusIcon = when (check.status) {
        CheckStatus.GREEN  -> "🟢"
        CheckStatus.YELLOW -> "🟡"
        CheckStatus.RED    -> "🔴"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isRechecking) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(statusIcon, fontSize = 20.sp)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    check.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (check.harmSeverity == HarmSeverity.CRITICAL && check.status == CheckStatus.RED) {
                    Surface(
                        color = Color(0xFFF44336),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "Критично",
                            color = Color.White,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                // Кнопка повторной проверки
                IconButton(
                    onClick = onRecheck,
                    enabled = !isRechecking,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Проверить снова",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (check.requiresFix) {
                Spacer(Modifier.height(8.dp))
                // Термины в тексте кликабельны — открывают глоссарий
                GlossaryText(
                    text  = check.harm,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { onLearnMore(check.id) }) {
                    Text("Как исправить →")
                }
            }
        }
    }
}
