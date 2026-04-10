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
        // === 1. Статус VPN (узкая полоска) ===
        item { VpnStatusBanner(state.vpnStatus, state.isScanning) }

        if (state.vpnStatus == VpnStatus.NOT_ACTIVE) {
            item { NotActiveMessage(onScan = { viewModel.scan() }) }
            return@LazyColumn
        }

        if (state.vpnStatus == VpnStatus.CORPORATE_VPN) {
            item { CorporateVpnMessage() }
        }

        // === 2. Активный клиент + WARP индикатор ===
        state.activeClient?.let { client ->
            item { ActiveClientBanner(client) }
        }

        // WARP обёртка — важная информация, показываем отдельно
        val exitIpResult = state.fixable.find { it.id == "vpn_works" }
        if (exitIpResult?.harm?.contains("WARP") == true) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF4CAF50).copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🛡️", fontSize = 20.sp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("WARP обёртка активна",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50))
                            Text("Реальный IP сервера скрыт за Cloudflare",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // === 3. Вердикт (главное — сразу видно) ===
        state.overallVerdict?.let { verdict ->
            item { OverallVerdictCard(verdict = verdict) }
        }

        // === 4. Проблемы (RED/YELLOW) или "Всё ок" ===
        if (state.fixable.isNotEmpty()) {
            val issues = state.fixable.filter { it.status != CheckStatus.GREEN }

            if (issues.isNotEmpty()) {
                val hasRed = issues.any { it.status == CheckStatus.RED }
                item {
                    Text(
                        if (hasRed) "Найдены уязвимости" else "Рекомендации",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (hasRed) Color(0xFFF44336) else Color(0xFFFF9800),
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("✅", fontSize = 24.sp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Очевидных уязвимостей не найдено",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold)
                            Text("${state.fixable.size} проверок пройдены",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                // Зелёные проверки — компактно, с (?) для глоссария
                items(state.fixable) { check ->
                    GreenCheckRow(check = check, onExplain = onLearnMore)
                }
            }
        }

        // === 5. Конфиг VPN (если gRPC прочитан) ===
        state.vpnConfig?.takeIf { it.outbounds.isNotEmpty() }?.let { config ->
            item { ConfigRevealCard(config = config, accessMethod = state.configAccessMethod) }
        }

        // === 6. "Что видят приложения" — КОМПАКТНАЯ строка, по тапу разворачивается ===
        if (state.alwaysVisible.isNotEmpty()) {
            item { CompactAlwaysVisibleSection(state.alwaysVisible) }
        }

        // === 7. Технические детали (collapsed) ===
        if (state.fixable.isNotEmpty()) {
            item { TechnicalDetailsCard(state) }
        }

        // === 8. Кнопки ===
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
                    Text(if (state.isScanning) "Проверяю..." else "Проверить снова")
                }
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

        // === 9. Deep Scan ===
        if (!isDeepScanning && fullScanPorts.isEmpty()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(
                        "Глубокий скан ищет открытые сервисы на всех портах (1-65535). " +
                        "На каждом найденном порту пробуем gRPC handshake — если ответит, " +
                        "читаем конфиг VPN.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                OutlinedButton(
                    onClick = { viewModel.deepScan() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Text("🔍 Глубокое сканирование всех портов (1-65535)")
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
                        Text("Глубокий скан: ${fullScanPorts.size} открытых портов",
                            fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Каждый открытый порт — потенциальная точка входа для стукачей",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        val shownPorts = fullScanPorts.take(15)
                        shownPorts.forEach { p ->
                            val risk = when (p.category) {
                                com.stukachoff.domain.checker.PortCategory.XRAY_GRPC ->
                                    "⚠️ API управления — ключи и IP доступны"
                                com.stukachoff.domain.checker.PortCategory.CLASH_API ->
                                    "⚠️ API конфигурации — история соединений"
                                com.stukachoff.domain.checker.PortCategory.SOCKS5,
                                com.stukachoff.domain.checker.PortCategory.HTTP_PROXY,
                                com.stukachoff.domain.checker.PortCategory.MIXED ->
                                    "Прокси-порт — виден без разрешений"
                                else -> "Открытый TCP-сервис"
                            }
                            Text("• ${p.port}: ${p.description} — $risk",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (fullScanPorts.size > 15) {
                            Text("... и ещё ${fullScanPorts.size - 15} портов",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp))
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
            Text("Обновить статус", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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

/**
 * Компактный инфоблок "Что видят приложения" — одна строка,
 * разворачивается по тапу. Не доминирует визуально.
 */
@Composable
fun CompactAlwaysVisibleSection(checks: List<CheckResult.AlwaysVisible>) {
    var expanded by remember { mutableStateOf(false) }
    var selectedCheck by remember { mutableStateOf<CheckResult.AlwaysVisible?>(null) }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        // Компактная строка — тап разворачивает
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ℹ️", fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                "Что видят все приложения",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (expanded) "↑" else "↓",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                checks.forEach { check ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(check.title,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium)
                            Text(check.knowsWhat,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2)
                        }
                        TextButton(
                            onClick = { selectedCheck = check },
                            contentPadding = PaddingValues(4.dp),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Text("?", fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Text(
                    "Скрыть без root невозможно — это архитектура Android",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }

    // Диалог пояснения при тапе на (?)
    selectedCheck?.let { check ->
        AlertDialog(
            onDismissRequest = { selectedCheck = null },
            title = { Text(check.title) },
            text = {
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
                TextButton(onClick = { selectedCheck = null }) { Text("Понятно") }
            }
        )
    }

    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
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

// Краткие описания проверок для глоссария (?)
/**
 * Компактная строка для GREEN проверок — показывает (?) для глоссария
 */
@Composable
fun GreenCheckRow(check: CheckResult.Fixable, onExplain: (String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🟢", fontSize = 12.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            check.title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = { showDialog = true },
            contentPadding = PaddingValues(2.dp),
            modifier = Modifier.size(24.dp)
        ) {
            Text("?", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(check.title) },
            text = {
                Text(
                    checkExplanations[check.id] ?: check.harm,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text("Понятно") }
            }
        )
    }
}

private val checkExplanations = mapOf(
    "vpn_works"    to "Реальная проверка работы VPN — делаем HTTPS-запрос через туннель и смотрим какой IP видит внешний мир.",
    "grpc_api"     to "gRPC API — служебный порт xray (10085). Если открыт без пароля — любое приложение читает IP сервера, UUID и ключи.",
    "clash_api"    to "Clash REST API (порт 9090). Без пароля отдаёт полную конфигурацию и список всех посещённых сайтов.",
    "dns_leak"     to "DNS-утечка — когда запросы «какой IP у google.com?» идут мимо VPN к провайдеру. Провайдер видит все сайты.",
    "mtu"          to "MTU — максимальный размер пакета. WireGuard=1420, AmneziaWG=1280. По этому числу ТСПУ определяет тип VPN.",
    "split_tunnel" to "Split-tunnel — часть приложений идёт мимо VPN. VK и Сбер в bypass делают HTTP-пробы и определяют VPN.",
    "system_proxy" to "Системный прокси — если VPN-клиент работает в режиме прокси, его адрес виден всем приложениям.",
    "work_profile" to "Изоляция профилей — Knox, Shelter или Island создают отдельный профиль. VPN в изолированном профиле труднее обнаружить.",
    "android_version" to "Android 10+ закрывает /proc/net/ через SELinux. На старых версиях любое приложение видит все TCP-соединения.",
    "exit_ip"      to "Exit IP — реальный IP через который твой трафик выходит в интернет. Если VPN работает — это IP VPN-сервера.",
    "proxy_mode"   to "SOCKS5/HTTP прокси открыт на localhost. Любое приложение подключается к нему напрямую, обходя VPN-туннель, и узнаёт IP сервера. Рабочий профиль НЕ защищает."
)

@Composable
fun CheckCard(
    check: CheckResult.Fixable,
    isRechecking: Boolean = false,
    onLearnMore: (String) -> Unit,
    onRecheck: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(check.status == CheckStatus.RED) }
    var showExplanation by remember { mutableStateOf(false) }

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
        // Кнопка (?) — глоссарий с объяснением термина
        TextButton(
            onClick = { showExplanation = true },
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

    // Глоссарный диалог при нажатии (?)
    if (showExplanation) {
        AlertDialog(
            onDismissRequest = { showExplanation = false },
            title = { Text(check.title) },
            text = {
                Text(
                    checkExplanations[check.id]
                        ?: "Описание для этой проверки ещё не добавлено.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                if (check.requiresFix) {
                    TextButton(onClick = {
                        showExplanation = false
                        onLearnMore(check.id)
                    }) { Text("Как исправить") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showExplanation = false }) { Text("Понятно") }
            }
        )
    }
}
