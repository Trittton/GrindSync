package dev.gatsyuk.grindsync.feature.profile.rankmap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gatsyuk.grindsync.core.database.dao.ExerciseDao
import dev.gatsyuk.grindsync.core.database.dao.WorkoutDao
import dev.gatsyuk.grindsync.core.datastore.UserPreferencesRepository
import dev.gatsyuk.grindsync.core.gamification.RankEngine
import dev.gatsyuk.grindsync.core.model.Muscle
import dev.gatsyuk.grindsync.core.model.MuscleRole
import dev.gatsyuk.grindsync.core.model.Rank
import dev.gatsyuk.grindsync.core.model.Sex
import dev.gatsyuk.grindsync.core.ui.rankColor
import dev.gatsyuk.grindsync.core.ui.rankLabel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class RankMapUiState(
    val game: RankEngine.GamificationState,
    /** Trained exercises per muscle that have NO strength standard — shown as
     *  "not counted" so the ranking inputs are fully transparent. */
    val uncountedByMuscle: Map<Muscle, List<String>>,
)

@HiltViewModel
class MuscleRanksViewModel @Inject constructor(
    workoutDao: WorkoutDao,
    exerciseDao: ExerciseDao,
    prefs: UserPreferencesRepository,
) : ViewModel() {
    val state = combine(
        workoutDao.observeCompletedWorkouts(),
        exerciseDao.observeExercisesWithMuscles(),
        prefs.sex,
        prefs.bodyweightFallbackKg,
    ) { workouts, catalog, sex, bwFallback ->
        val game = RankEngine.compute(workouts, catalog, sex, bwFallback)
        val scoredIds = game.exercises.filter { it.score != null }.map { it.exerciseId }.toSet()
        val trainedIds = game.exercises.map { it.exerciseId }.toSet()
        val uncounted = mutableMapOf<Muscle, MutableList<String>>()
        catalog.forEach { entry ->
            if (entry.exercise.id in trainedIds && entry.exercise.id !in scoredIds) {
                entry.muscles.forEach { mapping ->
                    uncounted.getOrPut(mapping.muscle) { mutableListOf() }.add(entry.exercise.name)
                }
            }
        }
        RankMapUiState(game, uncounted)
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000),
        RankMapUiState(RankEngine.compute(emptyList(), emptyList(), Sex.UNSET, null), emptyMap()),
    )
}

/**
 * Rank Map data layer (SPEC §7.7): every muscle's strength rank with its
 * contributing exercises. The front/back body-figure rendering ships as a
 * later polish pass on top of exactly this data. Semantics: STRENGTH rank,
 * not recovery. Grey "not trained" is deliberately distinct from rank E.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuscleRanksScreen(
    onBack: () -> Unit,
    viewModel: MuscleRanksViewModel = hiltViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val game = uiState.game
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rank Map") },
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Strength rank per muscle: weighted average of the normalized " +
                        "best lifts training it (primary ×1.0, secondary ×0.4). " +
                        "Ladder: E- up to SSS+ (all-time world-record level). " +
                        "Dimmed E- means no ranking data yet. Tap a muscle for its lifts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            items(Muscle.entries.toList(), key = { it.name }) { muscle ->
                val rank = game.muscleRanks[muscle]
                val isExpanded = expanded == muscle.name
                Card(
                    onClick = {
                        expanded = if (isExpanded) null else muscle.name
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        rankColor(rank?.rank ?: Rank.E_MINUS)
                                            .copy(alpha = if (rank == null) 0.45f else 1f),
                                        RoundedCornerShape(8.dp),
                                    )
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    rankLabel(rank?.rank),
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    muscle.name.lowercase().replace('_', ' ')
                                        .replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    rank?.let { "Score %.0f".format(it.score) }
                                        ?: "No data for ranking yet",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                if (isExpanded) "▲" else "▼",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (isExpanded) {
                            Column(Modifier.padding(top = 10.dp)) {
                                if (rank == null) {
                                    Text(
                                        "No ranked lifts train this muscle yet. Log a barbell " +
                                            "lift with a strength standard (squat, bench, deadlift, " +
                                            "rows, presses…) that targets it.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    uiState.uncountedByMuscle[muscle]?.let { names ->
                                        Text(
                                            "Trained but not counted (no strength standard yet): " +
                                                names.joinToString(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 6.dp),
                                        )
                                    }
                                } else {
                                    rank.contributors.forEach { contributor ->
                                        Row(
                                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        rankColor(Rank.fromScore(contributor.score)),
                                                        RoundedCornerShape(6.dp),
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                            ) {
                                                Text(
                                                    Rank.fromScore(contributor.score).label,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                )
                                            }
                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                contributor.exerciseName,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text(
                                                "×%.1f %s".format(
                                                    contributor.contributionWeight,
                                                    if (contributor.role == MuscleRole.PRIMARY) "primary" else "secondary",
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    uiState.uncountedByMuscle[muscle]?.let { names ->
                                        Text(
                                            "Trained but not counted (no strength standard yet): " +
                                                names.joinToString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 8.dp),
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
}
