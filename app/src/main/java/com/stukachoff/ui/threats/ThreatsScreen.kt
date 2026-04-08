package com.stukachoff.ui.threats

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stukachoff.domain.model.AppThreat
import com.stukachoff.domain.model.ThreatLevel

// TODO: подключить ViewModel + AppThreatAnalyzer в Task 9
@Composable
fun ThreatsScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Кто стучит", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Приложения на твоём устройстве которые могут детектировать VPN",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item { OperatorsCard() }
        // TODO: items(threats) { AppThreatCard(it) }
    }
}

@Composable
fun OperatorsCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Операторы связи (МТС, Билайн, МегаФон, Tele2)",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Детектируют VPN через ТСПУ на сетевом уровне — не через приложение. " +
                        "Это другой уровень угрозы, на уровне всей сети.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
