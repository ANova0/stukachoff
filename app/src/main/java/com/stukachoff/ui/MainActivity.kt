package com.stukachoff.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.stukachoff.data.prefs.AppPreferences
import com.stukachoff.ui.navigation.BottomNavBar
import com.stukachoff.ui.navigation.Screen
import com.stukachoff.ui.navigation.StukachoffNavHost
import com.stukachoff.ui.navigation.bottomNavItems
import com.stukachoff.ui.theme.StukachoffTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var prefs: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val startDestination = if (prefs.onboardingCompleted) {
            Screen.Verify.route
        } else {
            Screen.Onboarding.route
        }

        setContent {
            StukachoffTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                Scaffold(
                    bottomBar = {
                        BottomNavBar(
                            currentRoute = currentRoute,
                            onNavigate = { route ->
                                navController.navigate(route) {
                                    popUpTo(Screen.Verify.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                ) { padding ->
                    Box(modifier = Modifier.padding(padding)) {
                        StukachoffNavHost(
                            navController = navController,
                            startDestination = startDestination
                        )
                    }
                }
            }
        }
    }
}
