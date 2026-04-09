package com.stukachoff.ui.threats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.stukachoff.data.apps.AppPermissionRisk
import com.stukachoff.data.apps.DangerousPermission
import com.stukachoff.domain.model.AppThreat
import com.stukachoff.domain.model.DetectionMethod
import com.stukachoff.domain.model.ThreatLevel

@Composable
fun ThreatsScreen(viewModel: ThreatsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Кто стучит",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Установленные приложения которые могут детектировать твой VPN",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (state.isLoading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
        } else if (state.noneInstalled) {
            item { NoneInstalledCard() }
        } else {
            // Красные — подтверждённые
            val red = state.threats.filter { it.threatLevel == ThreatLevel.RED }
            if (red.isNotEmpty()) {
                item {
                    ThreatSectionHeader(
                        title = "🔴 Активные стукачи",
                        subtitle = "Подтверждённая детекция VPN"
                    )
                }
                items(red) { AppThreatCard(it) }
            }

            // Жёлтые — потенциальные
            val yellow = state.threats.filter { it.threatLevel == ThreatLevel.YELLOW }
            if (yellow.isNotEmpty()) {
                item {
                    ThreatSectionHeader(
                        title = "🟡 Потенциальные стукачи",
                        subtitle = "Обязаны внедрить детекцию к 15.04.2026"
                    )
                }
                items(yellow) { AppThreatCard(it) }
            }
        }

        // MCC/SIM риски — приложения с опасными разрешениями
        if (state.permissionRisks.isNotEmpty()) {
            item {
                ThreatSectionHeader(
                    title    = "📱 Опасные разрешения",
                    subtitle = "Установленные приложения с доступом к SIM и геолокации"
                )
            }
            items(state.permissionRisks) { PermissionRiskCard(it) }
        }

        // Операторы всегда внизу
        item { OperatorsCard() }

        // Объяснение методов
        item { MethodsExplanationCard() }
    }
}

@Composable
fun ThreatSectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AppThreatCard(threat: AppThreat) {
    var expanded by remember { mutableStateOf(false) }

    val borderColor = when (threat.threatLevel) {
        ThreatLevel.RED    -> Color(0xFFF44336)
        ThreatLevel.YELLOW -> Color(0xFFFF9800)
        ThreatLevel.GREY   -> Color(0xFF9E9E9E)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            width = 1.dp
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Индикатор угрозы
                Surface(
                    modifier = Modifier.size(10.dp),
                    shape = RoundedCornerShape(50),
                    color = borderColor
                ) {}
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(threat.appName, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    threat.version?.let {
                        Text("v$it", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (threat.confirmedMethods.isNotEmpty()) {
                    Surface(
                        color = Color(0xFFF44336).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "Подтверждено",
                            color = Color(0xFFF44336),
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                threat.harm,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Методы детекции
            val methods = (threat.confirmedMethods + threat.possibleMethods).distinct()
            if (methods.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    methods.forEach { method ->
                        MethodChip(method)
                    }
                }
            }

            // Источник (раскрывается)
            threat.source?.let { source ->
                TextButton(
                    onClick = { expanded = !expanded },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        if (expanded) "Скрыть источник ↑" else "Источник ↓",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                AnimatedVisibility(expanded) {
                    Text(
                        source,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun MethodChip(method: DetectionMethod) {
    val (label, color) = when (method) {
        DetectionMethod.TRANSPORT_VPN  -> "VPN флаг" to Color(0xFF9C27B0)
        DetectionMethod.INTERFACE_NAME -> "Интерфейс" to Color(0xFF2196F3)
        DetectionMethod.LOCALHOST_SCAN -> "Порты" to Color(0xFFF44336)
        DetectionMethod.HTTP_PROBING   -> "HTTP пробы" to Color(0xFFFF9800)
        DetectionMethod.PLMN_MCC       -> "SIM оператор" to Color(0xFF009688)
        DetectionMethod.GEOLOCATION    -> "GeoIP" to Color(0xFF4CAF50)
        DetectionMethod.PACKAGE_SCAN   -> "Приложения" to Color(0xFF795548)
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            label,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun NoneInstalledCard() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20).copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("✅", fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "Известных стукачей не обнаружено",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Ни одно приложение из нашей базы не установлено. " +
                        "База обновляется — проверяй регулярно.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun OperatorsCard() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "📡 Операторы связи",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "МТС, Билайн, МегаФон, Tele2 детектируют VPN на сетевом уровне через ТСПУ — " +
                        "не через приложение на устройстве. Это другой уровень угрозы. " +
                        "Защита — выбор устойчивых протоколов (VLESS+Reality, AmneziaWG).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MethodsExplanationCard() {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(if (expanded) "Что означают метки ↑" else "Что означают метки? ↓")
            }
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MethodDescription("VPN флаг", "TRANSPORT_VPN — любое приложение с одним разрешением ACCESS_NETWORK_STATE")
                    MethodDescription("Интерфейс", "Имя сетевого интерфейса (tun0) — вообще без разрешений")
                    MethodDescription("Порты", "Сканирование 127.0.0.1 — вообще без разрешений")
                    MethodDescription("HTTP пробы", "Запросы к заблокированным сайтам — нужен INTERNET")
                    MethodDescription("SIM оператор", "Код российского оператора в связке с зарубежным IP — нужен READ_PHONE_STATE")
                }
            }
        }
    }
}

@Composable
fun PermissionRiskCard(risk: AppPermissionRisk) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                risk.appName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            risk.dangerousPermissions.forEach { perm ->
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Surface(
                        color = Color(0xFFFF9800).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            perm.label,
                            color = Color(0xFFFF9800),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        perm.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun MethodDescription(label: String, description: String) {
    Row {
        Text("• $label: ", style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold)
        Text(description, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
