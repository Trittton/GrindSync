package dev.gatsyuk.grindsync.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
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
import dev.gatsyuk.grindsync.feature.profile.rankmap.MuscleRanksScreen
import dev.gatsyuk.grindsync.feature.profile.stats.ExerciseStatsScreen
import dev.gatsyuk.grindsync.feature.workout.TrainScreen
import dev.gatsyuk.grindsync.feature.workout.live.LiveWorkoutScreen
import dev.gatsyuk.grindsync.feature.workout.routines.RoutineEditorScreen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The 3 top-level worlds (SPEC §6.1): Train · Nutrition · Profile. */
enum class TopLevelDestination(val label: String, val icon: ImageVector) {
    TRAIN("Train", Icons.Default.FitnessCenter),
    NUTRITION("Nutrition", Icons.Default.Restaurant),
    PROFILE("Profile", Icons.Default.Person),
}

const val HOME_ROUTE = "home"
const val SETTINGS_ROUTE = "settings"
const val LIVE_WORKOUT_ROUTE = "live/{workoutId}"
const val ROUTINE_EDITOR_ROUTE = "routine_editor/{routineId}"
const val EXERCISE_STATS_ROUTE = "exercise_stats/{exerciseId}"
const val MUSCLE_RANKS_ROUTE = "muscle_ranks"

fun liveWorkoutRoute(workoutId: Long) = "live/$workoutId"
fun routineEditorRoute(routineId: Long?) = "routine_editor/${routineId ?: -1L}"
fun exerciseStatsRoute(exerciseId: Long) = "exercise_stats/$exerciseId"

@HiltViewModel
class ShellViewModel @Inject constructor(workoutDao: WorkoutDao) : ViewModel() {
    val inProgressWorkout = workoutDao.observeInProgressWorkout()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun GrindSyncApp(shellViewModel: ShellViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val inProgress by shellViewModel.inProgressWorkout.collectAsStateWithLifecycle()

    // Top-level tabs live in a pager (swipe between them, SPEC §6.1 one-thumb nav);
    // sub-screens push on top via the nav graph.
    val pagerState = rememberPagerState(pageCount = { TopLevelDestination.entries.size })
    val scope = rememberCoroutineScope()

    val onHome = currentRoute == HOME_ROUTE
    val onLiveScreen = currentRoute == LIVE_WORKOUT_ROUTE

    Scaffold(
        bottomBar = {
            if (onHome) {
                NavigationBar {
                    TopLevelDestination.entries.forEachIndexed { index, destination ->
                        NavigationBarItem(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
            val live = inProgress
            val showResumeBar = live != null && !onLiveScreen
            if (showResumeBar && live != null) {
                ResumeWorkoutBar(
                    workout = live,
                    onClick = { navController.navigate(liveWorkoutRoute(live.id)) },
                    modifier = Modifier.statusBarsPadding(),
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .let {
                        // The bar already consumed the status inset; stop the
                        // screens' own top bars from adding it a second time.
                        if (showResumeBar) it.consumeWindowInsets(WindowInsets.statusBars) else it
                    },
            ) {
                NavHost(
                    navController = navController,
                    startDestination = HOME_ROUTE,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable(HOME_ROUTE) {
                        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                            when (TopLevelDestination.entries[page]) {
                                TopLevelDestination.TRAIN -> TrainScreen(
                                    onOpenWorkout = { navController.navigate(liveWorkoutRoute(it)) },
                                    onEditRoutine = { navController.navigate(routineEditorRoute(it)) },
                                    onNewRoutine = { navController.navigate(routineEditorRoute(null)) },
                                )
                                TopLevelDestination.NUTRITION -> NutritionScreen()
                                TopLevelDestination.PROFILE -> ProfileScreen(
                                    onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
                                    onOpenExercise = { navController.navigate(exerciseStatsRoute(it)) },
                                    onOpenMuscleRanks = { navController.navigate(MUSCLE_RANKS_ROUTE) },
                                )
                            }
                        }
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
                    composable(
                        EXERCISE_STATS_ROUTE,
                        arguments = listOf(navArgument("exerciseId") { type = NavType.LongType }),
                    ) {
                        ExerciseStatsScreen(onBack = { navController.popBackStack() })
                    }
                    composable(MUSCLE_RANKS_ROUTE) {
                        MuscleRanksScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}

@Composable
private fun ResumeWorkoutBar(
    workout: WorkoutEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
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
