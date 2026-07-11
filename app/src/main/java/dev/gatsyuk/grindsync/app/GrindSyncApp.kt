package dev.gatsyuk.grindsync.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gatsyuk.grindsync.core.database.dao.WorkoutDao
import dev.gatsyuk.grindsync.core.database.entity.WorkoutEntity
import dev.gatsyuk.grindsync.feature.nutrition.NutritionScreen
import dev.gatsyuk.grindsync.feature.profile.ProfileScreen
import dev.gatsyuk.grindsync.feature.profile.SettingsScreen
import dev.gatsyuk.grindsync.feature.workout.TrainScreen
import dev.gatsyuk.grindsync.feature.workout.live.LiveWorkoutScreen
import dev.gatsyuk.grindsync.feature.workout.routines.RoutineEditorScreen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

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
const val LIVE_WORKOUT_ROUTE = "live/{workoutId}"
const val ROUTINE_EDITOR_ROUTE = "routine_editor/{routineId}"

fun liveWorkoutRoute(workoutId: Long) = "live/$workoutId"
fun routineEditorRoute(routineId: Long?) = "routine_editor/${routineId ?: -1L}"

@HiltViewModel
class ShellViewModel @Inject constructor(workoutDao: WorkoutDao) : ViewModel() {
    val inProgressWorkout = workoutDao.observeInProgressWorkout()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun GrindSyncApp(shellViewModel: ShellViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val inProgress by shellViewModel.inProgressWorkout.collectAsStateWithLifecycle()

    val isTopLevel = TopLevelDestination.entries.any { dest ->
        currentDestination?.hierarchy?.any { it.route == dest.route } == true
    }
    val onLiveScreen = currentDestination?.route == LIVE_WORKOUT_ROUTE

    Scaffold(
        bottomBar = {
            Column {
                val live = inProgress
                if (live != null && !onLiveScreen) {
                    ResumeWorkoutBar(
                        workout = live,
                        onClick = { navController.navigate(liveWorkoutRoute(live.id)) },
                    )
                }
                if (isTopLevel) {
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
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.TRAIN.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopLevelDestination.TRAIN.route) {
                TrainScreen(
                    onOpenWorkout = { navController.navigate(liveWorkoutRoute(it)) },
                    onEditRoutine = { navController.navigate(routineEditorRoute(it)) },
                    onNewRoutine = { navController.navigate(routineEditorRoute(null)) },
                )
            }
            composable(TopLevelDestination.NUTRITION.route) { NutritionScreen() }
            composable(TopLevelDestination.PROFILE.route) {
                ProfileScreen(onOpenSettings = { navController.navigate(SETTINGS_ROUTE) })
            }
            composable(SETTINGS_ROUTE) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(
                LIVE_WORKOUT_ROUTE,
                arguments = listOf(navArgument("workoutId") { type = NavType.LongType }),
            ) {
                LiveWorkoutScreen(onBack = { navController.popBackStack() })
            }
            composable(
                ROUTINE_EDITOR_ROUTE,
                arguments = listOf(navArgument("routineId") { type = NavType.LongType }),
            ) {
                RoutineEditorScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun ResumeWorkoutBar(workout: WorkoutEntity, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "Workout in progress — ${workout.name}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
