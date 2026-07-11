package dev.gatsyuk.grindsync.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gatsyuk.grindsync.core.database.dao.ExerciseHistorySummary
import dev.gatsyuk.grindsync.core.database.dao.StatsDao
import dev.gatsyuk.grindsync.core.database.dao.WorkoutDao
import dev.gatsyuk.grindsync.core.datastore.UserPreferencesRepository
import dev.gatsyuk.grindsync.core.model.WeightUnit
import dev.gatsyuk.grindsync.core.model.Weights
import dev.gatsyuk.grindsync.core.model.formatDurationMillis
import dev.gatsyuk.grindsync.core.stats.StatsCalculator
import kotlinx.coroutines.flow.SharingStarted
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
    prefs: UserPreferencesRepository,
) : ViewModel() {

    private val history = workoutDao.observeCompletedWorkouts()

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

/** Free stats dashboard (SPEC §6.5/§6.7) — the paywalled RepCount tab, unpaywalled. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onOpenSettings: () -> Unit,
    onOpenExercise: (Long) -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
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
            item {
                Text(
                    "Rank & XP arrive in Phase 3 — the numbers below already feed them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                ExerciseStatRow(entry, onClick = { onOpenExercise(entry.exerciseId) })
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
                Modifier.fillMaxWidth().height(64.dp).padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                weekCounts.forEach { count ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .width(18.dp)
                                .height(((44 * count / max).coerceAtLeast(2)).dp)
                                .background(
                                    if (count > 0) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                                ),
                        )
                        Text(
                            count.toString(),
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
private fun ExerciseStatRow(entry: ExerciseHistorySummary, onClick: () -> Unit) {
    val dateFormat = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.exerciseName, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${entry.setCount} sets · last ${entry.lastDate.format(dateFormat)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("→", color = MaterialTheme.colorScheme.primary)
        }
    }
}
