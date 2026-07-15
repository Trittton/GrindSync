package dev.gatsyuk.grindsync.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gatsyuk.grindsync.core.database.dao.ExerciseDao
import dev.gatsyuk.grindsync.core.database.dao.ExerciseHistorySummary
import dev.gatsyuk.grindsync.core.database.dao.StatsDao
import dev.gatsyuk.grindsync.core.database.dao.WorkoutDao
import dev.gatsyuk.grindsync.core.datastore.UserPreferencesRepository
import dev.gatsyuk.grindsync.core.gamification.RankEngine
import dev.gatsyuk.grindsync.core.model.WeightUnit
import dev.gatsyuk.grindsync.core.model.Weights
import dev.gatsyuk.grindsync.core.model.formatDurationMillis
import dev.gatsyuk.grindsync.core.stats.StatsCalculator
import dev.gatsyuk.grindsync.core.ui.rankColor
import dev.gatsyuk.grindsync.core.ui.rankLabel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    workoutDao: WorkoutDao,
    statsDao: StatsDao,
    exerciseDao: ExerciseDao,
    prefs: UserPreferencesRepository,
) : ViewModel() {

    private val history = workoutDao.observeCompletedWorkouts()

    /** The Solo-Leveling layer — recomputed from raw history on every change. */
    val gamification = combine(
        history,
        exerciseDao.observeExercisesWithMuscles(),
        prefs.sex,
        prefs.bodyweightFallbackKg,
    ) { workouts, catalog, sex, bwFallback ->
        RankEngine.compute(workouts, catalog, sex, bwFallback)
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000),
        RankEngine.compute(emptyList(), emptyList(), dev.gatsyuk.grindsync.core.model.Sex.UNSET, null),
    )

    val totals = history.map { StatsCalculator.totals(it) }
        .stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000),
            StatsCalculator.TotalStats(0, 0, 0.0, 0, 0),
        )

    val weeklyFrequency = history
        .map { StatsCalculator.weeklyFrequency(it, weeks = 8, today = LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), List(8) { 0 })

    val exercisesWithHistory = statsDao.observeExercisesWithHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val weightUnit = prefs.weightUnit
        .stateIn(viewModelScope, SharingStarted.Eagerly, WeightUnit.KG)
}

/** Profile leads with the overall rank hero (SPEC §6.7), stats below. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onOpenSettings: () -> Unit,
    onOpenExercise: (Long) -> Unit,
    onOpenMuscleRanks: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val game by viewModel.gamification.collectAsStateWithLifecycle()
    val totals by viewModel.totals.collectAsStateWithLifecycle()
    val frequency by viewModel.weeklyFrequency.collectAsStateWithLifecycle()
    val exercises by viewModel.exercisesWithHistory.collectAsStateWithLifecycle()
    val unit by viewModel.weightUnit.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { RankHeroCard(game, onOpenMuscleRanks) }
            item { StatSheetRow(game) }
            item { StreakCard(game) }
            item { AchievementsCard(game) }

            item {
                Text(
                    "Statistics",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            item { TotalsCard(totals, unit) }
            item { FrequencyCard(frequency) }

            item {
                Text(
                    "Exercise stats",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (exercises.isEmpty()) {
                item {
                    Text(
                        "Log a workout and per-exercise charts appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(exercises, key = { it.exerciseId }) { entry ->
                val standing = game.exercises.firstOrNull { it.exerciseId == entry.exerciseId }
                ExerciseStatRow(entry, standing, onClick = { onOpenExercise(entry.exerciseId) })
            }
        }
    }
}

@Composable
private fun RankHeroCard(game: RankEngine.GamificationState, onOpenMuscleRanks: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val unranked = game.overallRank == null
                val label = rankLabel(game.overallRank) // unranked renders as the E− floor
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            rankColor(game.overallRank ?: dev.gatsyuk.grindsync.core.model.Rank.E_MINUS)
                                .copy(alpha = if (unranked) 0.45f else 1f),
                            RoundedCornerShape(14.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        fontSize = if (label.length > 2) 18.sp else 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Overall rank", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                    Text(
                        game.glPoints?.let { "IPF GL %.1f".format(it) }
                            ?: "No data for ranking yet. Log squat, bench & deadlift " +
                            "and set sex + bodyweight in Settings",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Level ${game.overallLevel.level}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.width(10.dp))
                LinearProgressIndicator(
                    progress = {
                        if (game.overallLevel.xpForNext == 0) 0f
                        else game.overallLevel.xpInto.toFloat() / game.overallLevel.xpForNext
                    },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "${game.overallLevel.xpInto}/${game.overallLevel.xpForNext} XP",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            Card(onClick = onOpenMuscleRanks, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Rank Map: per-muscle ranks", style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f))
                    Text("→", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun StatSheetRow(game: RankEngine.GamificationState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                StatCell(
                    "STR",
                    game.statSheet.strengthGl?.let { "%.1f".format(it) } ?: "-",
                    Modifier.weight(1f),
                )
                StatCell("END", game.statSheet.enduranceReps28d.toString(), Modifier.weight(1f))
                StatCell("CON", "${game.statSheet.consistencyPct}%", Modifier.weight(1f))
            }
            Text(
                "STR = IPF GL points from your best squat/bench/deadlift · " +
                    "END = working reps in the last 28 days · " +
                    "CON = share of the last 8 weeks with 2+ sessions",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun StreakCard(game: RankEngine.GamificationState) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp)) {
            StatCell("Week streak", "${game.currentStreakWeeks}", Modifier.weight(1f))
            StatCell("Longest", "${game.longestStreakWeeks}", Modifier.weight(1f))
            StatCell("Total PRs", "${game.totalPrCount}", Modifier.weight(1f))
        }
    }
}

@Composable
private fun AchievementsCard(game: RankEngine.GamificationState) {
    val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "Achievements",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (game.achievements.isEmpty()) {
                Text(
                    "None defined yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            game.achievements.forEach { achievement ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (achievement.unlockedOn != null) "★" else "☆",
                        color = if (achievement.unlockedOn != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(achievement.title, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            achievement.unlockedOn?.let { "Unlocked ${it.format(dateFormat)}" }
                                ?: achievement.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalsCard(totals: StatsCalculator.TotalStats, unit: WeightUnit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "All time",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                StatCell("Workouts", totals.workouts.toString(), Modifier.weight(1f))
                StatCell("Duration", formatDurationMillis(totals.totalDurationMillis), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                StatCell(
                    "Volume",
                    "${Weights.formatKgAs(totals.totalVolumeKg, unit)} ${Weights.unitLabel(unit)}",
                    Modifier.weight(1f),
                )
                StatCell("Sets / Reps", "${totals.totalSets} / ${totals.totalReps}", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FrequencyCard(weekCounts: List<Int>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "Workouts per week — last ${weekCounts.size} weeks",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            val max = (weekCounts.maxOrNull() ?: 0).coerceAtLeast(1)
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                weekCounts.forEach { count ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        // Fixed-height bar area + separate label row: no overlap.
                        Box(
                            Modifier.height(44.dp).width(18.dp),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(((40 * count / max).coerceAtLeast(2)).dp)
                                    .background(
                                        if (count > 0) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                                    ),
                            )
                        }
                        Text(
                            count.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseStatRow(
    entry: ExerciseHistorySummary,
    standing: RankEngine.ExerciseStanding?,
    onClick: () -> Unit,
) {
    val dateFormat = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.exerciseName, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${entry.setCount} sets · last ${entry.lastDate.format(dateFormat)}" +
                        (standing?.let { " · Lv ${it.level.level}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (standing?.rank != null) {
                Box(
                    modifier = Modifier
                        .background(rankColor(standing.rank), RoundedCornerShape(7.dp))
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        rankLabel(standing.rank),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Text("→", color = MaterialTheme.colorScheme.primary)
        }
    }
}
