package com.focuslockout.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.focuslockout.ui.apps.AppSelectionRoute
import com.focuslockout.ui.home.HomeRoute

object Routes {
    /** Session setup when idle, the live dashboard while a lock is armed. */
    const val HOME = "home"
    const val APP_SELECTION = "app_selection"
}

@Composable
fun FocusLockNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeRoute(
                onChooseApps = {
                    navController.navigate(Routes.APP_SELECTION) { launchSingleTop = true }
                },
            )
        }
        composable(Routes.APP_SELECTION) {
            AppSelectionRoute(onBack = { navController.popBackStack() })
        }
    }
}
