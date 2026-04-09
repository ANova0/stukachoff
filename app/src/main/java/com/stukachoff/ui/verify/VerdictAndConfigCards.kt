package com.stukachoff.ui.verify

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stukachoff.domain.model.OutboundConfig
import com.stukachoff.domain.model.OverallVerdict
import com.stukachoff.domain.model.ProtectionLevel
import com.stukachoff.domain.model.TsupLevel
import com.stukachoff.domain.model.VpnConfig

// ─── OverallVerdictCard ────────────────────────────────────────────────────

@Composable
fun OverallVerdictCard(verdict: OverallVerdict) {
    val bgColor = when (verdict.appProtection) {
        ProtectionLevel.CRITICAL -> Color(0xFF7B0000).copy(alpha = 0.12f)
        ProtectionLevel.LOW      -> Color(0xFFF44336).copy(alpha = 0.08f)
        ProtectionLevel.MEDIUM   -> Color(0xFFFF9800).copy(alpha = 0.08f)
        ProtectionLevel.HIGH     -> Color(0xFF1B5E20).copy(alpha = 0.08f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "ИТОГ РАССЛЕДОВАНИЯ",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            VerdictRow(
                label   = "От приложений",
                level   = verdict.appProtection,
                details = verdict.appDetails
            )
            Spacer(Modifier.height(8.dp))
            VerdictRow(
                label   = "От ТСПУ",
                level   = verdict.tsupProtection,
                details = verdict.tsupDetails
            )

            verdict.topRecommendation?.let { rec ->
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    "→ $rec",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun VerdictRow(label: String, level: ProtectionLevel, details: String) {
    val (emoji, color) = when (level) {
        ProtectionLevel.HIGH     -> "🟢" to Color(0xFF4CAF50)
        ProtectionLevel.MEDIUM   -> "🟡" to Color(0xFFFF9800)
        ProtectionLevel.LOW      -> "🔴" to Color(0xFFF44336)
        ProtectionLevel.CRITICAL -> "🚨" to Color(0xFF7B0000)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 20.sp,
            modifier = Modifier.semantics {
                contentDescription = when (level) {
                    ProtectionLevel.HIGH     -> "Высокая защита"
                    ProtectionLevel.MEDIUM   -> "Средняя защита"
                    ProtectionLevel.LOW      -> "Низкая защита"
                    ProtectionLevel.CRITICAL -> "Критическая уязвимость"
                }
            }
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── ConfigRevealCard ─────────────────────────────────────────────────────

@Composable
fun ConfigRevealCard(config: VpnConfig) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF7B0000).copy(alpha = 0.10f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔴", fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Конфиг прочитан — это видят стукачи",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF44336),
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { expanded = !expanded },
                    contentPadding = PaddingValues(4.dp)
                ) {
                    Text(if (expanded) "Скрыть ↑" else "Показать что видят стукачи ↓")
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Stukachoff прочитал твой VPN-конфиг через открытый gRPC API. " +
                        "Любое приложение на телефоне может сделать то же самое.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))

                    config.outbounds.forEachIndexed { i, ob ->
                        if (i > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        OutboundDataBlock(ob)
                    }
                }
            }
        }
    }
}

@Composable
private fun OutboundDataBlock(ob: OutboundConfig) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ConfigDataRow("Протокол",     ob.protocol.uppercase())
        ConfigDataRow("Transport",    ob.transport.uppercase())
        ConfigDataRow("Безопасность", ob.security.uppercase())
        ConfigDataRow(
            label   = "Сервер",
            value   = "${ob.serverAddress}:${ob.serverPort}",
            warning = "Этот IP передадут в блоклист РКН"
        )
        if (ob.sni.isNotBlank())
            ConfigDataRow("SNI", ob.sni)
        if (ob.uuid.isNotBlank())
            ConfigDataRow(
                label   = "UUID",
                value   = ob.uuid,
                warning = "По UUID тебя идентифицируют на сервере"
            )
        ob.publicKey?.let {
            ConfigDataRow("Публичный ключ", it)
        }

        Spacer(Modifier.height(8.dp))
        TsupBadge(ob.tsupResistance)
    }
}

@Composable
private fun ConfigDataRow(label: String, value: String, warning: String? = null) {
    Column(modifier = Modifier.padding(vertical = 1.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                "$label:",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(110.dp)
            )
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
        }
        warning?.let {
            Text(
                "⚠️ $it",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFFF9800),
                modifier = Modifier.padding(start = 110.dp)
            )
        }
    }
}

@Composable
private fun TsupBadge(level: TsupLevel) {
    val (text, color) = when (level) {
        TsupLevel.HIGH    -> "✅ Высокая устойчивость к ТСПУ" to Color(0xFF4CAF50)
        TsupLevel.MEDIUM  -> "⚠️ Средняя устойчивость к ТСПУ" to Color(0xFFFF9800)
        TsupLevel.LOW     -> "🔴 Низкая устойчивость к ТСПУ" to Color(0xFFF44336)
        TsupLevel.BLOCKED -> "🚨 Протокол заблокирован ТСПУ" to Color(0xFF7B0000)
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
