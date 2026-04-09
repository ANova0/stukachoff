package com.stukachoff.ui.verify

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.platform.LocalContext
import com.stukachoff.data.export.ReportExporter
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val isDeepScanning by viewModel.isDeepScanning.collectAsState()
    val fullScanPorts by viewModel.fullScanPorts.collectAsState()

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
            item { NotActiveMessage(onScan = { viewModel.scan() }) }
            return@LazyColumn
        }

        if (state.vpnStatus == VpnStatus.CORPORATE_VPN) {
            item { CorporateVpnMessage() }
        }

        // Активный клиент — показываем ЯВНО
        state.activeClient?.let { client ->
            item { ActiveClientBanner(client) }
        }

        state.overallVerdict?.let { verdict ->
            item { OverallVerdictCard(verdict = verdict) }
        }
        state.vpnConfig?.takeIf { it.outbounds.isNotEmpty() }?.let { config ->
            item { ConfigRevealCard(config = config) }
        }

        if (state.alwaysVisible.isNotEmpty()) {
            item { AlwaysVisibleSection(state.alwaysVisible, state.deviceInfo) }
        }

        if (state.fixable.isNotEmpty()) {
            // FIX-5: Показываем ТОЛЬКО проблемы (RED/YELLOW)
            val issues = state.fixable.filter { it.status != CheckStatus.GREEN }
            val allGreen = issues.isEmpty()

            if (!allGreen) {
                item {
                    Text(
                        "Найдены уязвимости",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF44336),
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
                    )
                }
                items(issues) { check ->
                    CheckCard(
                        check        = check,
                        isRechecking = recheckingId == check.id,
                        onLearnMore  = onLearnMore,
                        onRecheck    = { viewModel.recheckSingle(check.id) }
                    )
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1B5E20).copy(alpha = 0.1f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("✅", fontSize = 40.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Проблем не обнаружено",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold)
                            Text("Все ${state.fixable.size} проверок пройдены",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Технические детали — GREEN проверки + полный дамп
            item { TechnicalDetailsCard(state) }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick  = { viewModel.scan() },
                    modifier = Modifier.weight(1f),
                    enabled  = !state.isScanning
                ) {
                    if (state.isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (state.isScanning) "Проверяю..." else "Проверить снова")
                }
                // Кнопка поделиться отчётом
                if (state.fixable.isNotEmpty()) {
                    OutlinedButton(
                        onClick  = { viewModel.shareReport() },
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Поделиться",
                            modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // Deep Scan button
        if (!isDeepScanning && fullScanPorts.isEmpty()) {
            item {
                OutlinedButton(
                    onClick = { viewModel.deepScan() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Text("🔍 Глубокое сканирование (1024-65535)")
                }
            }
        }
        if (isDeepScanning) {
            item {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                Text("Сканирую все порты...", modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        if (fullScanPorts.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Найдено в глубоком скане: ${fullScanPorts.size} открытых портов")
                        fullScanPorts.take(10).forEach { p ->
                            Text("• Порт ${p.port}: ${p.description}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
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
fun NotActiveMessage(onScan: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Text("🔍", fontSize = 64.sp)
        Spacer(Modifier.height(24.dp))
        Text(
            "Включи VPN и начни расследование",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Stukachoff покажет кто следит за твоим VPN только при активном соединении",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick  = onScan,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
            shape    = RoundedCornerShape(14.dp)
        ) {
            Text("Сканировать устройство", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun ActiveClientBanner(client: com.stukachoff.data.apps.ActiveClient) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("📱", fontSize = 24.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Активный клиент: ${client.displayName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    buildString {
                        append("${client.engine.name} · ${client.mode.name}")
                        if (client.confidence < 80) append(" · точность: ${client.confidence}%")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CorporateVpnMessage() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1565C0).copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("🏢 Корпоративный VPN", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Обнаружен MDM или корпоративный VPN. Проверки настроены для пользовательских " +
                "VPN-клиентов — некоторые результаты могут отличаться.",
                style = MaterialTheme.typography.bodySmall,
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
    var glossaryCheck by remember { mutableStateOf<CheckResult.AlwaysVisible?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Это видят все приложения",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))

            // Три строки — минималистично
            AlwaysVisibleRow(
                label       = "Конечный IP-адрес",
                status      = if (deviceInfo != null) "Не защищён от компрометации" else "—",
                isProtected = false,
                explanation = checks.find { it.id == "transport_vpn" }?.explanation ?: "",
                onHelp      = { glossaryCheck = checks.find { it.id == "transport_vpn" } }
            )
            AlwaysVisibleRow(
                label       = "Установленные VPN-клиенты",
                status      = if ((deviceInfo?.installedVpnClients?.size ?: 0) > 0)
                                  "Обнаружены (${deviceInfo!!.installedVpnClients.joinToString(", ")})"
                              else "Не обнаружены",
                isProtected = (deviceInfo?.installedVpnClients?.size ?: 0) == 0,
                explanation = checks.find { it.id == "vpn_clients" }?.explanation ?: "",
                onHelp      = { glossaryCheck = checks.find { it.id == "vpn_clients" } }
            )
            AlwaysVisibleRow(
                label       = "Заблокированные сайты",
                status      = "Доступны",
                isProtected = false,
                explanation = checks.find { it.id == "http_probing" }?.explanation ?: "",
                onHelp      = { glossaryCheck = checks.find { it.id == "http_probing" } }
            )
        }
    }

    // Bottom sheet с пояснением при нажатии (?)
    glossaryCheck?.let { check ->
        AlertDialog(
            onDismissRequest = { glossaryCheck = null },
            title   = { Text(check.title) },
            text    = {
                Column {
                    Text(check.explanation, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("✅ Знают: ${check.knowsWhat}",
                        style = MaterialTheme.typography.bodySmall)
                    Text("❌ Не знают: ${check.doesntKnow}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = { glossaryCheck = null }) { Text("Понятно") }
            }
        )
    }
}

@Composable
fun AlwaysVisibleRow(
    label: String,
    status: String,
    isProtected: Boolean,
    explanation: String,
    onHelp: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium)
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (isProtected) Color(0xFF4CAF50)
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Кнопка (?)
        TextButton(
            onClick = onHelp,
            contentPadding = PaddingValues(4.dp),
            modifier = Modifier.size(32.dp)
        ) {
            Text("?", fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
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
    var expanded by remember { mutableStateOf(check.status == CheckStatus.RED) }

    val statusIcon = when (check.status) {
        CheckStatus.GREEN  -> "🟢"
        CheckStatus.YELLOW -> "🟡"
        CheckStatus.RED    -> "🔴"
    }

    // Компактная строка — разворачивается по тапу
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (check.requiresFix) expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isRechecking) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Text(statusIcon, fontSize = 18.sp)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            check.title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        // FIX-7: Кнопка (?) — расшифровка термина через Learn
        TextButton(
            onClick = { onLearnMore(check.id) },
            contentPadding = PaddingValues(2.dp),
            modifier = Modifier.size(28.dp)
        ) {
            Text("?", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary)
        }
        if (check.harmSeverity == HarmSeverity.CRITICAL && check.status == CheckStatus.RED) {
            Surface(
                color = Color(0xFFF44336),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("!", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Spacer(Modifier.width(4.dp))
        }
        IconButton(onClick = onRecheck, enabled = !isRechecking,
            modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Refresh, contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    // Разворачиваемая детальная информация
    AnimatedVisibility(visible = expanded && check.requiresFix) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 44.dp, end = 16.dp, bottom = 10.dp)
        ) {
            GlossaryText(
                text  = check.harm,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = { onLearnMore(check.id) },
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Как исправить →", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}
