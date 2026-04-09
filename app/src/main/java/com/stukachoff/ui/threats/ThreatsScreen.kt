package com.stukachoff.ui.threats

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.stukachoff.data.apps.AppPermissionRisk
import com.stukachoff.data.apps.AppRunningStatus
import com.stukachoff.data.apps.DangerousPermission
import com.stukachoff.domain.model.AppThreat
import com.stukachoff.domain.model.ThreatLevel

@Composable
fun ThreatsScreen(viewModel: ThreatsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 88.dp) // место для sticky кнопки
        ) {
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Кто стучит", style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Приложения которые могут детектировать твой VPN",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(48.dp),
                        contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                // Красные — подтверждённые
                val red = state.threats.filter { it.threatLevel == ThreatLevel.RED }
                if (red.isNotEmpty()) {
                    item { SectionHeader("🔴 Активные стукачи", "Подтверждённая детекция VPN") }
                    items(red) { ThreatListItem(it, state.runningPackages) }
                }

                // Жёлтые
                val yellow = state.threats.filter { it.threatLevel == ThreatLevel.YELLOW }
                if (yellow.isNotEmpty()) {
                    item { SectionHeader("🟡 Потенциальные стукачи", "Обязаны внедрить детекцию") }
                    items(yellow) { ThreatListItem(it, state.runningPackages) }
                }

                if (state.noneInstalled) {
                    item {
                        Text("✅ Известных стукачей не установлено",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge)
                    }
                }

                // Опасные разрешения
                if (state.permissionRisks.isNotEmpty()) {
                    item { SectionHeader("📱 Опасные разрешения", "Доступ к SIM и геолокации") }
                    items(state.permissionRisks) { PermissionRiskRow(it) }
                }

                // Операторы
                item { OperatorsRow() }
            }
        }

        // Sticky кнопка внизу
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Button(
                onClick = {
                Toast.makeText(context, "В разработке — появится с обновлением", Toast.LENGTH_SHORT).show()
            },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF44336)
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    "🛡️ Получить план защиты",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ThreatListItem(threat: AppThreat, runningPackages: Set<String>) {
    val isRunning = threat.packageName in runningPackages
    val statusColor = if (isRunning) Color(0xFFF44336) else Color(0xFFFF9800)
    val statusText  = if (isRunning) "активен" else "опасен"
    val statusEmoji = if (isRunning) "🔴" else "🟡"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Флаг статуса
        Surface(
            color = statusColor.copy(alpha = 0.15f),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.width(80.dp)
        ) {
            Text(
                "$statusEmoji $statusText",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = statusColor,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(threat.appName, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium)
            Text(threat.harm, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1)
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
fun PermissionRiskRow(risk: AppPermissionRisk) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = Color(0xFFFF9800).copy(alpha = 0.15f),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.width(80.dp)
        ) {
            Text(
                "⚠️ разрешения",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFFF9800),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(risk.appName, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium)
            Text(
                risk.dangerousPermissions.joinToString(", ") { it.label },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun OperatorsRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text("📡", fontSize = 18.sp, modifier = Modifier.padding(end = 8.dp, top = 2.dp))
        Column {
            Text("Операторы связи", style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold)
            Text(
                "МТС, Билайн, МегаФон, Tele2 — детектируют через ТСПУ на сетевом уровне, не через приложение.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
