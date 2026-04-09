package com.stukachoff.ui.tutorial

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stukachoff.data.apps.ActiveClient
import com.stukachoff.data.apps.VpnEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialScreen(
    activeClient: ActiveClient?,
    vulnerabilities: List<String>,  // list of check IDs with issues
    onBack: () -> Unit
) {
    val clientName = activeClient?.displayName

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Учебник защиты") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Active client banner
            clientName?.let {
                item { ActiveClientBanner(clientName = it, engine = activeClient.engine) }
            }

            // Vulnerability fixes — only when there are real issues
            val relevantFixes = vulnerabilities.mapNotNull { id -> VULNERABILITY_FIXES[id] }
            if (relevantFixes.isNotEmpty()) {
                item {
                    SectionHeader("🚨 Найдены уязвимости — исправь в первую очередь")
                }
                items(relevantFixes) { fix ->
                    VulnerabilityFixCard(fix = fix, clientName = clientName)
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            // Base steps — always shown
            item { SectionHeader("🛡️ Базовая защита (3 шага)") }
            items(BASE_STEPS) { step ->
                BaseStepCard(step = step, clientName = clientName)
            }

            // TSPU advice if needed
            activeClient?.engine?.let { engine ->
                TSPU_ADVICE[engine]?.let { advice ->
                    item { SectionHeader("📡 Защита от ТСПУ") }
                    item { TsupAdviceCard(advice = advice) }
                }
            }

            // All clients reference
            item { SectionHeader("📖 Справочник по всем клиентам") }
            items(VULNERABILITY_FIXES.values.toList()) { fix ->
                AllClientsReferenceCard(fix = fix)
            }
        }
    }
}

@Composable
fun ActiveClientBanner(clientName: String, engine: VpnEngine) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Активный клиент:", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(clientName, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text("Инструкции адаптированы для $clientName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
fun BaseStepCard(step: TutorialStep, clientName: String?) {
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(16.dp)
        ) {
            Text(step.title, style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold)
            Text(step.description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    // Show specific instruction for active client, or all
                    val instruction = clientName?.let { step.instructions[it] }
                    if (instruction != null) {
                        InstructionRow(client = clientName, instruction = instruction, highlight = true)
                    } else {
                        step.instructions.entries.forEach { (client, instr) ->
                            InstructionRow(client = client, instruction = instr)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VulnerabilityFixCard(fix: VulnerabilityFix, clientName: String?) {
    val clipboard = LocalClipboardManager.current
    var expanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF44336).copy(alpha = 0.06f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("🔴", modifier = Modifier.padding(end = 8.dp))
                Text(fix.title, style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { expanded = !expanded },
                    contentPadding = PaddingValues(4.dp)) {
                    Text(if (expanded) "↑" else "↓")
                }
            }
            Text(fix.description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))

                    val instruction = clientName?.let { fix.clientInstructions[it] }
                    if (instruction != null) {
                        InstructionRow(client = clientName, instruction = instruction, highlight = true)
                    } else {
                        fix.clientInstructions.entries.forEach { (client, instr) ->
                            InstructionRow(client = client, instruction = instr)
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text("Для поддержки провайдера:", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(fix.supportMessage,
                                style = MaterialTheme.typography.bodySmall)
                            TextButton(
                                onClick = { clipboard.setText(AnnotatedString(fix.supportMessage)) },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Скопировать", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TsupAdviceCard(advice: TsupAdvice) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Проблема: ${advice.problem}", style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFF44336))
            Spacer(Modifier.height(4.dp))
            Text("Решение: ${advice.solution}", style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            advice.steps.forEachIndexed { i, step ->
                Text("${i + 1}. $step", style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 1.dp))
            }
        }
    }
}

@Composable
fun AllClientsReferenceCard(fix: VulnerabilityFix) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
        Column(modifier = Modifier
            .clickable { expanded = !expanded }
            .padding(12.dp)) {
            Text(fix.title, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium)
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    fix.clientInstructions.entries.forEach { (client, instr) ->
                        InstructionRow(client = client, instruction = instr)
                    }
                }
            }
        }
    }
}

@Composable
fun InstructionRow(client: String, instruction: String, highlight: Boolean = false) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 2.dp)) {
        Text(
            "$client:",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            color = if (highlight) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp)
        )
        Text(instruction, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f))
    }
}
