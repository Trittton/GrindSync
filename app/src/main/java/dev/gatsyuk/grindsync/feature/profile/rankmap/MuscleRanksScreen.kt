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
import dev.gatsyuk.grindsync.core.model.Sex
import dev.gatsyuk.grindsync.core.ui.rankColor
import dev.gatsyuk.grindsync.core.ui.rankLabel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MuscleRanksViewModel @Inject constructor(
    workoutDao: WorkoutDao,
    exerciseDao: ExerciseDao,
    prefs: UserPreferencesRepository,
) : ViewModel() {
    val gamification = combine(
        workoutDao.observeCompletedWorkouts(),
        exerciseDao.observeExercisesWithMuscles(),
        prefs.sex,
        prefs.bodyweightFallbackKg,
    ) { workouts, catalog, sex, bwFallback ->
        RankEngine.compute(workouts, catalog, sex, bwFallback)
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000),
        RankEngine.compute(emptyList(), emptyList(), Sex.UNSET, null),
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
    val game by viewModel.gamification.collectAsStateWithLifecycle()
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
                    "Strength rank per muscle — weighted average of the normalized " +
                        "best lifts training it (primary ×1.0, secondary ×0.4). " +
                        "Grey = not trained yet (different from rank E).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            items(Muscle.entries.toList(), key = { it.name }) { muscle ->
                val rank = game.muscleRanks[muscle]
                Card(
                    onClick = {
                        expanded = if (expanded == muscle.name) null else muscle.name
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(rankColor(rank?.rank), RoundedCornerShape(8.dp)),
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
                                    rank?.let { "Score %.0f / 100".format(it.score) } ?: "Not trained",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (expanded == muscle.name && rank != null) {
                            Column(Modifier.padding(top = 8.dp)) {
                                rank.contributors.forEach { contributor ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                        Text(
                                            contributor.exerciseName,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            "score %.0f · ×%.1f %s".format(
                                                contributor.score,
                                                contributor.contributionWeight,
                                                if (contributor.role == MuscleRole.PRIMARY) "primary" else "secondary",
                                            ),
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
}
