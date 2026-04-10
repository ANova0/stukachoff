package com.stukachoff.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
        body   = "С апреля 2026 года десятки крупных российских приложений " +
                "обязаны находить VPN и сообщать о нём.\n\n" +
                "Stukachoff показывает кто стучит, что видит, " +
                "и учит как от этого защититься.",
        accent = Color(0xFFF44336)
    ),
    OnboardingPage(
        emoji  = "🔍",
        title  = "Что проверяет",
        body   = "• Открытые порты и API которые сливают конфиг VPN\n" +
                "• DNS-утечки — видит ли провайдер твои сайты\n" +
                "• Раздельное туннелирование — правильно ли настроено\n" +
                "• Какой VPN-клиент активен и его устойчивость к ТСПУ\n" +
                "• WARP-обёртка — скрыт ли реальный IP сервера\n" +
                "• Критическая уязвимость SOCKS5 прокси\n\n" +
                "Данные никуда не отправляются.",
        accent = Color(0xFF9C27B0)
    ),
    OnboardingPage(
        emoji  = "🛡️",
        title  = "Два режима работы",
        body   = "Защищённый режим (по умолчанию):\n" +
                "Все проверки локально. Ни один запрос не уходит в сеть.\n\n" +
                "Сетевой режим (включается в Меню):\n" +
                "Дополнительно проверяет exit IP через VPN-туннель — " +
                "показывает реальный IP через который ты выходишь в интернет.\n\n" +
                "Для полной проверки рекомендуется Сетевой режим.",
        accent = Color(0xFF4CAF50)
    ),
    OnboardingPage(
        emoji  = "📚",
        title  = "Научим настраивать",
        body   = "Stukachoff не только находит проблемы — он учит их решать.\n\n" +
                "• Как настроить раздельное туннелирование\n" +
                "• Какие приложения пускать через VPN, какие в обход\n" +
                "• Как закрыть уязвимые API\n" +
                "• Что написать в поддержку провайдера\n\n" +
                "Инструкции адаптированы под твой VPN-клиент.",
        accent = Color(0xFFFF9800)
    ),
    OnboardingPage(
        emoji  = "👁️",
        title  = "Начнём",
        body   = "Запусти VPN и нажми «Начать».\n\n" +
                "🟢 Защищено\n" +
                "🟡 Рекомендация — можно улучшить\n" +
                "🔴 Уязвимость — нужно исправить\n\n" +
                "Учебник защиты доступен в Меню.",
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
    // Фиксированная верхняя часть — заголовок всегда на одной высоте
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Фиксированный отступ от верха — заголовки на одной высоте
        Spacer(Modifier.height(40.dp))
        Text(page.emoji, fontSize = 64.sp)
        Spacer(Modifier.height(20.dp))
        Text(
            page.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = page.accent
        )
        Spacer(Modifier.height(16.dp))
        Text(
            page.body,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Start,
            lineHeight = 22.sp
        )
        Spacer(Modifier.height(32.dp))
    }
}
