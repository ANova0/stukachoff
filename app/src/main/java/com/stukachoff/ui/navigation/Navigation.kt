package com.stukachoff.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.stukachoff.ui.learn.LearnScreen
import com.stukachoff.ui.onboarding.OnboardingScreen
import com.stukachoff.ui.settings.SettingsScreen
import com.stukachoff.ui.threats.ThreatsScreen
import com.stukachoff.ui.verify.VerifyScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector? = null) {
    object Verify      : Screen("verify", "Проверка", Icons.Default.Home)
    object Threats     : Screen("threats", "Стукачи", Icons.Default.List)
    object Settings    : Screen("settings", "Меню", Icons.Default.Menu)
    object Onboarding  : Screen("onboarding", "Онбординг")
    object Learn       : Screen("learn/{checkId}", "Как исправить") {
        fun route(checkId: String) = "learn/$checkId"
    }
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
            SettingsScreen(onOpenOnboarding = {
                navController.navigate(Screen.Onboarding.route)
            })
        }

        composable(Screen.Learn.route) { backStack ->
            val checkId = backStack.arguments?.getString("checkId") ?: ""
            LearnScreen(checkId = checkId)
        }
    }
}

@Composable
fun BottomNavBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    // Не показываем нижнюю панель на онбординге и экране LEARN
    if (currentRoute == Screen.Onboarding.route ||
        currentRoute?.startsWith("learn/") == true) return

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
