package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.details.EmailDetailsScreen
import com.example.ui.home.HomeScreen
import com.example.ui.home.HomeViewModel
import com.example.ui.settings.SettingsScreen

object Destinations {
    const val HOME = "home"
    const val DETAILS = "details"
    const val SETTINGS = "settings"
}

@Composable
fun KmtMailNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    homeViewModel: HomeViewModel = viewModel()
) {
    NavHost(
        navController = navController,
        startDestination = Destinations.HOME,
        modifier = modifier
    ) {
        composable(Destinations.HOME) {
            HomeScreen(
                viewModel = homeViewModel,
                onMessageClick = { messageId ->
                    navController.navigate("${Destinations.DETAILS}/$messageId")
                },
                onSettingsClick = {
                    navController.navigate(Destinations.SETTINGS)
                }
            )
        }

        composable(
            route = "${Destinations.DETAILS}/{messageId}",
            arguments = listOf(navArgument("messageId") { type = NavType.StringType })
        ) { backStackEntry ->
            val messageId = backStackEntry.arguments?.getString("messageId") ?: ""
            EmailDetailsScreen(
                messageId = messageId,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Destinations.SETTINGS) {
            SettingsScreen(
                viewModel = homeViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
