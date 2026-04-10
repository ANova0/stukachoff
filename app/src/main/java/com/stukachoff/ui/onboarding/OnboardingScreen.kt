package com.stukachoff.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
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
        emoji  = "🕵️",
        title  = "Стукачев следит за стукачами",
        body   = "С апреля 2026 года десятки крупных российских приложений обязаны " +
                "находить VPN на твоём устройстве и сообщать о нём в государственные реестры.\n\n" +
                "Stukachoff показывает кто это делает — и что именно видит.",
        accent = Color(0xFFF44336)
    ),
    OnboardingPage(
        emoji  = "🔍",
        title  = "Как идёт расследование",
        body   = "Stukachoff сканирует устройство так же, как это делают враждебные приложения:\n\n" +
                "• Сканирование всех портов и API VPN-клиентов\n" +
                "• Проверка DNS-утечек и маршрутизации\n" +
                "• Определение активного клиента и протокола\n" +
                "• Оценка устойчивости к ТСПУ\n" +
                "• Обнаружение WARP-обёртки\n\n" +
                "Данные остаются на устройстве. Никуда не передаются.",
        accent = Color(0xFF9C27B0)
    ),
    OnboardingPage(
        emoji  = "🔒",
        title  = "Безопасность прежде всего",
        body   = "По умолчанию Stukachoff работает только локально — " +
                "никаких сетевых запросов, никакой телеметрии.\n\n" +
                "Если хочешь проверить реальный exit IP через туннель и получать " +
                "автообновления — включи это в Меню → Автообновление.\n\n" +
                "Ты контролируешь что делает приложение.",
        accent = Color(0xFF4CAF50)
    ),
    OnboardingPage(
        emoji  = "🚫",
        title  = "Чего мы не делаем никогда",
        body   = "✗  Не отправляем IP твоего сервера никуда\n" +
                "✗  Не собираем идентификаторы устройства\n" +
                "✗  Нет аналитики, трекеров, SDK слежки\n" +
                "✗  Нет фоновой работы — только когда открыто\n" +
                "✗  Не рекламируем VPN-сервисы\n\n" +
                "Код полностью открыт — можешь проверить сам на GitHub.",
        accent = Color(0xFFFF9800)
    ),
    OnboardingPage(
        emoji  = "👁️",
        title  = "Готов к расследованию",
        body   = "Запусти VPN и нажми «Начать».\n\n" +
                "Stukachoff проведёт расследование и покажет:\n\n" +
                "🟢 Защищено — всё в порядке\n" +
                "🟡 Частично — есть что улучшить\n" +
                "🔴 Уязвимо — нужно исправить\n\n" +
                "Настройки и режим работы — в третьей вкладке.",
        accent = Color(0xFF2196F3)
    )
)

@OptIn(ExperimentalFoundationApi::class)
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
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == onboardingPages.size - 1
    val currentPage = onboardingPages[pagerState.currentPage]

    Column(modifier = Modifier.fillMaxSize()) {
        // Пропустить — на всех слайдах включая последний
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = finish) {
                Text("Пропустить", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            OnboardingPageContent(page = onboardingPages[page])
        }

        // Dots
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(onboardingPages.size) { index ->
                val selected = index == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (selected) 10.dp else 7.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) currentPage.accent
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                )
            }
        }

        // Button
        Button(
            onClick = {
                if (isLastPage) finish()
                else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = currentPage.accent),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                if (isLastPage) "Начать расследование" else "Далее",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(page.emoji, fontSize = 72.sp)
        Spacer(Modifier.height(28.dp))
        Text(
            page.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = page.accent
        )
        Spacer(Modifier.height(20.dp))
        Text(
            page.body,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Start,
            lineHeight = 24.sp
        )
    }
}
