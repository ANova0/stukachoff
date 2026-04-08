package com.stukachoff.ui.verify

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val scanMessages = listOf(
    "Инициирую расследование...",
    "Сканирую локальные порты...",
    "Проверяю gRPC API...",
    "Ищу открытые прокси...",
    "Анализирую DNS-серверы...",
    "Проверяю MTU fingerprint...",
    "Обнаруживаю системный прокси...",
    "Определяю режим VPN-клиента...",
    "Анализирую сетевые интерфейсы...",
    "Ищу стукачей на устройстве...",
    "Проверяю версию Android...",
    "Собираю доказательную базу...",
    "Составляю досье...",
    "Расследование завершается..."
)

@Composable
fun ScanningScreen() {
    var currentMessageIndex by remember { mutableIntStateOf(0) }
    var displayedText by remember { mutableStateOf(scanMessages[0]) }

    // Переключаем сообщения каждые ~600ms
    LaunchedEffect(Unit) {
        while (true) {
            delay(600)
            currentMessageIndex = (currentMessageIndex + 1) % scanMessages.size
            displayedText = scanMessages[currentMessageIndex]
        }
    }

    // Радар анимация
    val radarRotation = rememberInfiniteTransition(label = "radar")
    val angle by radarRotation.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing)
        ),
        label = "angle"
    )

    // Пульс кольца
    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // Радар
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(200.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centerX = size.width / 2
                    val centerY = size.height / 2
                    val maxRadius = size.minDimension / 2

                    // Пульсирующее кольцо
                    drawCircle(
                        color = Color(0xFFF44336).copy(alpha = pulseAlpha),
                        radius = maxRadius * pulseScale,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 3f)
                    )

                    // Концентрические круги радара
                    for (i in 1..3) {
                        drawCircle(
                            color = Color(0xFFF44336).copy(alpha = 0.2f),
                            radius = maxRadius * (i / 3f),
                            center = Offset(centerX, centerY),
                            style = Stroke(width = 1f)
                        )
                    }

                    // Крестовина
                    drawLine(
                        color = Color(0xFFF44336).copy(alpha = 0.2f),
                        start = Offset(centerX - maxRadius, centerY),
                        end = Offset(centerX + maxRadius, centerY),
                        strokeWidth = 1f
                    )
                    drawLine(
                        color = Color(0xFFF44336).copy(alpha = 0.2f),
                        start = Offset(centerX, centerY - maxRadius),
                        end = Offset(centerX, centerY + maxRadius),
                        strokeWidth = 1f
                    )

                    // Луч радара
                    val sweepAngleRad = Math.toRadians(angle.toDouble())
                    val endX = centerX + maxRadius * Math.cos(sweepAngleRad).toFloat()
                    val endY = centerY + maxRadius * Math.sin(sweepAngleRad).toFloat()
                    drawLine(
                        color = Color(0xFFF44336).copy(alpha = 0.8f),
                        start = Offset(centerX, centerY),
                        end = Offset(endX, endY),
                        strokeWidth = 2f
                    )

                    // Точка в центре
                    drawCircle(
                        color = Color(0xFFF44336),
                        radius = 6f,
                        center = Offset(centerX, centerY)
                    )
                }

                // Иконка глаза в центре
                Text("👁️", fontSize = 36.sp)
            }

            Spacer(Modifier.height(40.dp))

            Text(
                "РАССЛЕДОВАНИЕ",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
                color = Color(0xFFF44336)
            )

            Spacer(Modifier.height(16.dp))

            // Сменяющиеся сообщения
            Text(
                displayedText,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.height(24.dp)
            )

            Spacer(Modifier.height(32.dp))

            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(2.dp),
                color = Color(0xFFF44336),
                trackColor = Color(0xFFF44336).copy(alpha = 0.2f)
            )
        }
    }
}
