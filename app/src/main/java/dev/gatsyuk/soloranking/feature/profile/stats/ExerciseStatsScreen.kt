package dev.gatsyuk.soloranking.feature.profile.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gatsyuk.soloranking.core.database.dao.ExerciseDao
import dev.gatsyuk.soloranking.core.database.dao.StatsDao
import dev.gatsyuk.soloranking.core.database.entity.ExerciseEntity
import dev.gatsyuk.soloranking.core.datastore.UserPreferencesRepository
import dev.gatsyuk.soloranking.core.model.WeightUnit
import dev.gatsyuk.soloranking.core.model.Weights
import dev.gatsyuk.soloranking.core.stats.StatsCalculator
import dev.gatsyuk.soloranking.core.ui.charts.ChartPoint
import dev.gatsyuk.soloranking.core.ui.charts.LineChart
import dev.gatsyuk.soloranking.core.ui.exerciseTypeLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class ExerciseStatsState(
    val exercise: ExerciseEntity? = null,
    val supportsStrength: Boolean = false,
    val bestE1rm: StatsCalculator.BestE1rm? = null,
    val series: List<StatsCalculator.SessionPoint> = emptyList(),
    val repPrs: List<StatsCalculator.RepPr> = emptyList(),
    val totalSets: Int = 0,
)

@HiltViewModel
class ExerciseStatsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    statsDao: StatsDao,
    exerciseDao: ExerciseDao,
    prefs: UserPreferencesRepository,
) : ViewModel() {

    private val exerciseId: Long = checkNotNull(savedStateHandle["exerciseId"])
    private val exercise = MutableStateFlow<ExerciseEntity?>(null)

    init {
        viewModelScope.launch { exercise.value = exerciseDao.getById(exerciseId) }
    }

    val weightUnit = prefs.weightUnit
        .stateIn(viewModelScope, SharingStarted.Eagerly, WeightUnit.KG)

    val state = combine(exercise, statsDao.observeSetsForExercise(exerciseId)) { ex, sets ->
        if (ex == null) return@combine ExerciseStatsState()
        val type = ex.exerciseType
        ExerciseStatsState(
            exercise = ex,
            supportsStrength = StatsCalculator.typeSupportsStrengthStats(type),
            bestE1rm = StatsCalculator.bestE1rm(sets, type),
            series = StatsCalculator.sessionSeries(sets, type, ex.isUnilateral),
            repPrs = StatsCalculator.repPrs(sets, type),
            totalSets = sets.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseStatsState())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseStatsScreen(
    onBack: () -> Unit,
    viewModel: ExerciseStatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unit by viewModel.weightUnit.collectAsStateWithLifecycle()
    val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.exercise?.name ?: "…") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                    state.exercise?.let { exerciseTypeLabel(it.exerciseType) }.orEmpty() +
                        " · ${state.totalSets} sets logged",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!state.supportsStrength) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Text(
                            "Charts for this tracking mode (time / distance / cardio) " +
                                "arrive in a later phase. Raw history is safe in the log.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
                return@LazyColumn
            }

            state.bestE1rm?.let { best ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "Best estimated 1RM (Epley)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "${Weights.formatKgAs(best.valueKg, unit)} ${Weights.unitLabel(unit)}",
                                style = MaterialTheme.typography.headlineMedium,
                            )
                            Text(
                                best.date.format(dateFormat),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            val e1rmPoints = state.series.mapNotNull { p ->
                p.bestE1rmKg?.let { ChartPoint(p.date, Weights.kgToDisplay(it, unit)) }
            }
            if (e1rmPoints.isNotEmpty()) {
                item {
                    ChartCard("Estimated 1RM (${Weights.unitLabel(unit)})") {
                        LineChart(e1rmPoints, valueLabel = { Weights.format(it) })
                    }
                }
            }

            val volumePoints = state.series
                .filter { it.volumeKg > 0 }
                .map { ChartPoint(it.date, Weights.kgToDisplay(it.volumeKg, unit)) }
            if (volumePoints.isNotEmpty()) {
                item {
                    ChartCard("Volume per session (${Weights.unitLabel(unit)})") {
                        LineChart(volumePoints, valueLabel = { Weights.format(it) })
                    }
                }
            }

            if (state.repPrs.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "Rep PRs",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            state.repPrs.forEachIndexed { index, pr ->
                                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp))
                                Row(Modifier.fillMaxWidth().padding(top = if (index == 0) 8.dp else 0.dp)) {
                                    Text(
                                        "${pr.reps} rep${if (pr.reps > 1) "s" else ""}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        "${Weights.formatKgAs(pr.weightKg, unit)} ${Weights.unitLabel(unit)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        "  ·  ${pr.date.format(dateFormat)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            content()
        }
    }
}
