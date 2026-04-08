package com.stukachoff.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.stukachoff.ui.learn.LearnScreen
import com.stukachoff.ui.threats.ThreatsScreen
import com.stukachoff.ui.verify.VerifyScreen

sealed class Screen(val route: String, val label: String) {
    object Verify  : Screen("verify", "Проверка")
    object Threats : Screen("threats", "Стукачи")
    object Learn   : Screen("learn/{checkId}", "Как исправить") {
        fun route(checkId: String) = "learn/$checkId"
    }
}

@Composable
fun StukachoffNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Verify.route) {
        composable(Screen.Verify.route) {
            VerifyScreen(onLearnMore = { checkId ->
                navController.navigate(Screen.Learn.route(checkId))
            })
        }
        composable(Screen.Threats.route) {
            ThreatsScreen()
        }
        composable(Screen.Learn.route) { backStack ->
            val checkId = backStack.arguments?.getString("checkId") ?: ""
            LearnScreen(checkId = checkId)
        }
    }
}

@Composable
fun BottomNavBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    val items = listOf(Screen.Verify, Screen.Threats)
    NavigationBar {
        items.forEach { screen ->
            NavigationBarItem(
                selected = currentRoute == screen.route,
                onClick = { onNavigate(screen.route) },
                icon = {
                    Icon(
                        imageVector = when (screen) {
                            is Screen.Verify -> Icons.Default.Home
                            is Screen.Threats -> Icons.Default.List
                            else -> Icons.Default.Info
                        },
                        contentDescription = screen.label
                    )
                },
                label = { Text(screen.label) }
            )
        }
    }
}
