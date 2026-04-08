package com.stukachoff.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

data class OnboardingPage(
    val emoji: String,
    val title: String,
    val body: String,
    val accent: Color = Color(0xFFF44336)
)

val onboardingPages = listOf(
    OnboardingPage(
        emoji = "🔍",
        title = "Кто смотрит на твой VPN?",
        body = "С апреля 2026 года десятки крупных российских приложений — " +
                "маркетплейсы, банки, стриминг, сервисы — обязаны находить VPN на устройстве " +
                "и передавать данные о нём в государственные реестры.\n\n" +
                "Большинство пользователей не знают об этом.",
        accent = Color(0xFFF44336)
    ),
    OnboardingPage(
        emoji = "🛡️",
        title = "Что делает Stukachoff",
        body = "Сканирует твоё устройство так же, как это делают враждебные приложения — " +
                "и показывает что они видят.\n\n" +
                "Открытые порты, DNS-утечки, незащищённые API — " +
                "всё это становится видно до того, как это найдут другие.",
        accent = Color(0xFF4CAF50)
    ),
    OnboardingPage(
        emoji = "🔒",
        title = "Почему это безопасно",
        body = "Большинство аналогичных приложений отправляют IP твоего VPN-сервера " +
                "на внешние сайты в открытом виде — ТСПУ читает это мгновенно.\n\n" +
                "Stukachoff (core-версия) физически не может отправить данные наружу — " +
                "разрешения на интернет нет в манифесте.",
        accent = Color(0xFF2196F3)
    ),
    OnboardingPage(
        emoji = "🚫",
        title = "Чего мы не делаем",
        body = "✗  Не отправляем IP твоего сервера никуда\n" +
                "✗  Не собираем идентификаторы устройства\n" +
                "✗  Нет аналитики, трекеров, SDK слежки\n" +
                "✗  Нет фоновой работы — только когда открыто\n" +
                "✗  Не рекламируем VPN-сервисы\n\n" +
                "Код открыт — можешь проверить сам.",
        accent = Color(0xFFFF9800)
    ),
    OnboardingPage(
        emoji = "✅",
        title = "Готово к работе",
        body = "Запусти VPN и нажми «Проверить».\n\n" +
                "Зелёный — защищено.\n" +
                "Красный — есть проблема и инструкция как исправить.\n\n" +
                "Онбординг можно открыть повторно через меню.",
        accent = Color(0xFF4CAF50)
    )
)

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val finish = {
        viewModel.completeOnboarding()
        onFinish()
    }
    val pagerState = rememberPagerState { onboardingPages.size }
    val scope = rememberCoroutines()
    val isLastPage = pagerState.currentPage == onboardingPages.size - 1

    Column(modifier = Modifier.fillMaxSize()) {
        // Skip кнопка
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            if (!isLastPage) {
                TextButton(onClick = finish) {
                    Text("Пропустить", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Страницы
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            OnboardingPage(page = onboardingPages[page])
        }

        // Индикатор страниц
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(onboardingPages.size) { index ->
                val color = if (index == pagerState.currentPage)
                    onboardingPages[pagerState.currentPage].accent
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (index == pagerState.currentPage) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }

        // Кнопка
        Button(
            onClick = {
                if (isLastPage) {
                    finish()
                } else {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = onboardingPages[pagerState.currentPage].accent
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = if (isLastPage) "Начать проверку" else "Далее",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun OnboardingPage(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = page.emoji,
            fontSize = 80.sp
        )
        Spacer(Modifier.height(32.dp))
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = page.accent
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = page.body,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Start,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 24.sp
        )
    }
}

@Composable
private fun rememberCoroutines() = rememberCoroutineScope()
