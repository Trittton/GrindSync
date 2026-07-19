package dev.gatsyuk.soloranking.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gatsyuk.soloranking.core.database.dao.ExerciseDao
import dev.gatsyuk.soloranking.core.database.dao.ExerciseHistorySummary
import dev.gatsyuk.soloranking.core.database.dao.StatsDao
import dev.gatsyuk.soloranking.core.database.dao.WorkoutDao
import dev.gatsyuk.soloranking.core.datastore.UserPreferencesRepository
import dev.gatsyuk.soloranking.core.gamification.RankEngine
import dev.gatsyuk.soloranking.core.model.WeightUnit
import dev.gatsyuk.soloranking.core.model.Weights
import dev.gatsyuk.soloranking.core.model.formatDurationMillis
import dev.gatsyuk.soloranking.core.stats.StatsCalculator
import dev.gatsyuk.soloranking.core.ui.GlowProgressBar
import dev.gatsyuk.soloranking.core.ui.PanelLabel
import dev.gatsyuk.soloranking.core.ui.RankBadge
import dev.gatsyuk.soloranking.core.ui.SectionHeader
import dev.gatsyuk.soloranking.core.ui.StatTile
import dev.gatsyuk.soloranking.core.ui.SystemPanel
import dev.gatsyuk.soloranking.core.ui.theme.LocalSystemColors
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
        RankEngine.compute(emptyList(), emptyList(), dev.gatsyuk.soloranking.core.model.Sex.UNSET, null),
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
            item { RankHeroPanel(game, onOpenMuscleRanks) }
            item { StatSheetPanel(game) }
            item { StreakPanel(game) }
            item { AchievementsPanel(game) }

            item { SectionHeader("Statistics") }
            item { TotalsPanel(totals, unit) }
            item { FrequencyPanel(frequency) }

            item { SectionHeader("Exercises") }
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
private fun RankHeroPanel(game: RankEngine.GamificationState, onOpenMuscleRanks: () -> Unit) {
    val sys = LocalSystemColors.current
    SystemPanel(Modifier.fillMaxWidth(), glow = true, contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RankBadge(game.overallRank, 72.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                PanelLabel("Overall rank")
                Spacer(Modifier.height(2.dp))
                if (game.glPoints != null) {
                    Text(
                        "%.1f".format(game.glPoints),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Black,
                            fontFeatureSettings = "tnum",
                        ),
                    )
                    Text(
                        "IPF GL points · squat + bench + deadlift",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "No data for ranking yet. Log squat, bench & deadlift " +
                            "and set sex + bodyweight in Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "LV. ${game.overallLevel.level}",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    fontFeatureSettings = "tnum",
                ),
            )
            Spacer(Modifier.width(12.dp))
            GlowProgressBar(
                progress = if (game.overallLevel.xpForNext == 0) 0f
                else game.overallLevel.xpInto.toFloat() / game.overallLevel.xpForNext,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "${game.overallLevel.xpInto}/${game.overallLevel.xpForNext} XP",
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .clickable(onClick = onOpenMuscleRanks)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Rank Map — per-muscle ranks",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = sys.accent,
            )
        }
    }
}

@Composable
private fun StatSheetPanel(game: RankEngine.GamificationState) {
    SystemPanel(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            StatTile(
                "STR",
                game.statSheet.strengthGl?.let { "%.1f".format(it) } ?: "—",
                Modifier.weight(1f),
            )
            StatTile("END", game.statSheet.enduranceReps28d.toString(), Modifier.weight(1f))
            StatTile("CON", "${game.statSheet.consistencyPct}%", Modifier.weight(1f))
        }
        Text(
            "STR = IPF GL points from your best squat/bench/deadlift · " +
                "END = working reps in the last 28 days · " +
                "CON = share of the last 8 weeks with 2+ sessions",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun StreakPanel(game: RankEngine.GamificationState) {
    SystemPanel(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            StatTile("Week streak", "${game.currentStreakWeeks}", Modifier.weight(1f))
            StatTile("Longest", "${game.longestStreakWeeks}", Modifier.weight(1f))
            StatTile("Total PRs", "${game.totalPrCount}", Modifier.weight(1f))
        }
    }
}

@Composable
private fun AchievementsPanel(game: RankEngine.GamificationState) {
    val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
    SystemPanel(Modifier.fillMaxWidth()) {
        PanelLabel("Achievements")
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

@Composable
private fun TotalsPanel(totals: StatsCalculator.TotalStats, unit: WeightUnit) {
    SystemPanel(Modifier.fillMaxWidth()) {
        PanelLabel("All time")
        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            StatTile("Workouts", totals.workouts.toString(), Modifier.weight(1f))
            StatTile("Duration", formatDurationMillis(totals.totalDurationMillis), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            StatTile(
                "Volume",
                "${Weights.formatKgAs(totals.totalVolumeKg, unit)} ${Weights.unitLabel(unit)}",
                Modifier.weight(1f),
            )
            StatTile("Sets / Reps", "${totals.totalSets} / ${totals.totalReps}", Modifier.weight(1f))
        }
    }
}

@Composable
private fun FrequencyPanel(weekCounts: List<Int>) {
    val sys = LocalSystemColors.current
    SystemPanel(Modifier.fillMaxWidth()) {
        PanelLabel("Workouts per week — last ${weekCounts.size} weeks")
        val max = (weekCounts.maxOrNull() ?: 0).coerceAtLeast(1)
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
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
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(
                                    if (count > 0) {
                                        Brush.verticalGradient(
                                            listOf(sys.accent, sys.accent.copy(alpha = 0.45f)),
                                        )
                                    } else {
                                        Brush.verticalGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.surfaceVariant,
                                                MaterialTheme.colorScheme.surfaceVariant,
                                            ),
                                        )
                                    },
                                ),
                        )
                    }
                    Text(
                        count.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
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
    val sys = LocalSystemColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(sys.panelFill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
            RankBadge(standing.rank, 34.dp)
            Spacer(Modifier.width(10.dp))
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
