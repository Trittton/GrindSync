package dev.gatsyuk.grindsync.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.gatsyuk.grindsync.feature.nutrition.NutritionScreen
import dev.gatsyuk.grindsync.feature.profile.ProfileScreen
import dev.gatsyuk.grindsync.feature.profile.SettingsScreen
import dev.gatsyuk.grindsync.feature.workout.TrainScreen

/** The 3 top-level worlds (SPEC §6.1): Train · Nutrition · Profile. */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    TRAIN("train", "Train", Icons.Default.FitnessCenter),
    NUTRITION("nutrition", "Nutrition", Icons.Default.Restaurant),
    PROFILE("profile", "Profile", Icons.Default.Person),
}

const val SETTINGS_ROUTE = "settings"

@Composable
fun GrindSyncApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy
                        ?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                // Per-tab back stack with state preservation (SPEC §6.1).
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.TRAIN.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopLevelDestination.TRAIN.route) { TrainScreen() }
            composable(TopLevelDestination.NUTRITION.route) { NutritionScreen() }
            composable(TopLevelDestination.PROFILE.route) {
                ProfileScreen(onOpenSettings = { navController.navigate(SETTINGS_ROUTE) })
            }
            composable(SETTINGS_ROUTE) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
