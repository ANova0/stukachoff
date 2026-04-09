package com.stukachoff.ui.verify

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stukachoff.domain.model.DeviceInfo
import com.stukachoff.domain.model.ScanState
import com.stukachoff.domain.model.VpnStatus

/**
 * Блок технических деталей — полный дамп как у конкурентов,
 * но данные остаются на устройстве и никуда не отправляются.
 */
@Composable
fun TechnicalDetailsCard(state: ScanState) {
    if (state.vpnStatus != VpnStatus.USER_VPN || state.deviceInfo == null) return

    var expanded by remember { mutableStateOf(false) }
    val info = state.deviceInfo

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    if (expanded) "🔍 Технические детали ↑" else "🔍 Технические детали ↓",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    // Устройство
                    TechSection(title = "Устройство") {
                        TechRow("Модель", "${info.manufacturer} ${info.model}")
                        TechRow("Android", "${info.androidVersion} (API ${info.sdkInt})")
                    }

                    // Сетевые интерфейсы — полный список
                    TechSection(title = "Сетевые интерфейсы") {
                        if (info.vpnInterfaces.isEmpty()) {
                            TechRow("VPN интерфейсы", "не обнаружены")
                        } else {
                            info.vpnInterfaces.forEach { iface ->
                                TechRow(
                                    iface.name,
                                    buildString {
                                        appendLine("MTU: ${iface.mtu}")
                                        iface.addresses.forEachIndexed { i, addr ->
                                            if (i > 0) appendLine()
                                            append(addr)
                                        }
                                    }.trim()
                                )
                            }
                        }
                    }

                    // VPN клиенты
                    TechSection(title = "VPN-клиенты") {
                        if (info.installedVpnClients.isEmpty()) {
                            TechRow("Статус", "не обнаружены")
                        } else {
                            info.installedVpnClients.forEach { client ->
                                TechRow("Установлен", client)
                            }
                        }
                    }

                    // Статусы проверок
                    TechSection(title = "Результаты проверок") {
                        state.fixable.forEach { check ->
                            val statusStr = when (check.status) {
                                com.stukachoff.domain.model.CheckStatus.GREEN  -> "✅ OK"
                                com.stukachoff.domain.model.CheckStatus.YELLOW -> "⚠️ Внимание"
                                com.stukachoff.domain.model.CheckStatus.RED    -> "🔴 Уязвимо"
                            }
                            TechRow(check.title, statusStr)
                        }
                    }

                    Surface(
                        color = Color(0xFF4CAF50).copy(alpha = 0.1f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            "🔒 Эти данные видны только тебе и не покидают устройство",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TechSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Column(content = content)
    }
}

@Composable
fun TechRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            "$label:",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}
