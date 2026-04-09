package com.stukachoff.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.stukachoff.domain.model.CheckStatus
import com.stukachoff.ui.about.AboutScreen
import com.stukachoff.ui.learn.LearnScreen
import com.stukachoff.ui.onboarding.OnboardingScreen
import com.stukachoff.ui.settings.SettingsScreen
import com.stukachoff.ui.threats.ThreatsScreen
import com.stukachoff.ui.tutorial.TutorialScreen
import com.stukachoff.ui.verify.VerifyScreen
import com.stukachoff.ui.verify.VerifyViewModel

sealed class Screen(val route: String, val label: String, val icon: ImageVector? = null) {
    object Verify      : Screen("verify", "Проверка", Icons.Default.Home)
    object Threats     : Screen("threats", "Стукачи", Icons.Default.List)
    object Settings    : Screen("settings", "Меню", Icons.Default.Menu)
    object Onboarding  : Screen("onboarding", "Онбординг")
    object About       : Screen("about", "О приложении")
    object Learn       : Screen("learn/{checkId}", "Как исправить") {
        fun route(checkId: String) = "learn/$checkId"
    }
    object Tutorial    : Screen("tutorial", "Учебник")
}

val bottomNavItems = listOf(Screen.Verify, Screen.Threats, Screen.Settings)

@Composable
fun StukachoffNavHost(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(navController = navController, startDestination = startDestination) {

        composable(Screen.Onboarding.route) {
            OnboardingScreen(onFinish = {
                navController.navigate(Screen.Verify.route) {
                    popUpTo(Screen.Onboarding.route) { inclusive = true }
                }
            })
        }

        composable(Screen.Verify.route) {
            VerifyScreen(onLearnMore = { checkId ->
                navController.navigate(Screen.Learn.route(checkId))
            })
        }

        composable(Screen.Threats.route) {
            ThreatsScreen()
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onOpenOnboarding = { navController.navigate(Screen.Onboarding.route) },
                onOpenAbout      = { navController.navigate(Screen.About.route) },
                onOpenTutorial   = { navController.navigate(Screen.Tutorial.route) }
            )
        }

        composable(Screen.About.route) {
            AboutScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Learn.route) { backStack ->
            val checkId = backStack.arguments?.getString("checkId") ?: ""
            LearnScreen(checkId = checkId)
        }

        composable(Screen.Tutorial.route) {
            val parentEntry = remember(it) {
                runCatching { navController.getBackStackEntry(Screen.Verify.route) }.getOrNull()
            }
            val verifyVm: VerifyViewModel = if (parentEntry != null) {
                hiltViewModel(parentEntry)
            } else {
                hiltViewModel() // fallback: own VM
            }
            val state by verifyVm.state.collectAsState()
            TutorialScreen(
                activeClient    = state.activeClient,
                vulnerabilities = state.fixable
                    .filter { check -> check.status == CheckStatus.RED || check.status == CheckStatus.YELLOW }
                    .map { check -> check.id },
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
fun BottomNavBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    // Не показываем нижнюю панель на онбординге и экранах LEARN, TUTORIAL, ABOUT
    if (currentRoute == Screen.Onboarding.route ||
        currentRoute?.startsWith("learn/") == true ||
        currentRoute == Screen.About.route ||
        currentRoute == Screen.Tutorial.route) return

    NavigationBar {
        bottomNavItems.forEach { screen ->
            NavigationBarItem(
                selected = currentRoute == screen.route,
                onClick = { onNavigate(screen.route) },
                icon = { Icon(screen.icon!!, contentDescription = screen.label) },
                label = { Text(screen.label) }
            )
        }
    }
}
