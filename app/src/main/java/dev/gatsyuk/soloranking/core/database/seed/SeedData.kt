package dev.gatsyuk.soloranking.core.database.seed

import dev.gatsyuk.soloranking.core.model.ContributionWeights.PRIMARY
import dev.gatsyuk.soloranking.core.model.ContributionWeights.SECONDARY
import dev.gatsyuk.soloranking.core.model.ExerciseType
import dev.gatsyuk.soloranking.core.model.ExerciseType.ASSISTED_WEIGHT_REPS
import dev.gatsyuk.soloranking.core.model.ExerciseType.BODYWEIGHT_REPS
import dev.gatsyuk.soloranking.core.model.ExerciseType.BODYWEIGHT_TIME
import dev.gatsyuk.soloranking.core.model.ExerciseType.BODYWEIGHT_WEIGHT_REPS
import dev.gatsyuk.soloranking.core.model.ExerciseType.CARDIO_TIME_DISTANCE_KCAL
import dev.gatsyuk.soloranking.core.model.ExerciseType.OTHER_NOTES
import dev.gatsyuk.soloranking.core.model.ExerciseType.STRENGTH_WEIGHT_DISTANCE
import dev.gatsyuk.soloranking.core.model.ExerciseType.STRENGTH_WEIGHT_DISTANCE_TIME
import dev.gatsyuk.soloranking.core.model.ExerciseType.STRENGTH_WEIGHT_REPS
import dev.gatsyuk.soloranking.core.model.ExerciseType.STRENGTH_WEIGHT_TIME
import dev.gatsyuk.soloranking.core.model.Muscle
import dev.gatsyuk.soloranking.core.model.Muscle.ABS
import dev.gatsyuk.soloranking.core.model.Muscle.ADDUCTORS
import dev.gatsyuk.soloranking.core.model.Muscle.BICEPS
import dev.gatsyuk.soloranking.core.model.Muscle.CALVES
import dev.gatsyuk.soloranking.core.model.Muscle.CHEST
import dev.gatsyuk.soloranking.core.model.Muscle.FOREARMS
import dev.gatsyuk.soloranking.core.model.Muscle.FRONT_DELTS
import dev.gatsyuk.soloranking.core.model.Muscle.GLUTES
import dev.gatsyuk.soloranking.core.model.Muscle.HAMSTRINGS
import dev.gatsyuk.soloranking.core.model.Muscle.LATS
import dev.gatsyuk.soloranking.core.model.Muscle.LOWER_BACK
import dev.gatsyuk.soloranking.core.model.Muscle.OBLIQUES
import dev.gatsyuk.soloranking.core.model.Muscle.QUADS
import dev.gatsyuk.soloranking.core.model.Muscle.REAR_DELTS
import dev.gatsyuk.soloranking.core.model.Muscle.SIDE_DELTS
import dev.gatsyuk.soloranking.core.model.Muscle.TRAPS
import dev.gatsyuk.soloranking.core.model.Muscle.TRICEPS
import dev.gatsyuk.soloranking.core.model.Muscle.UPPER_BACK

/**
 * Starter catalog, authored clean-room. Muscle mappings follow standard
 * exercise-science convention (prime movers vs synergists); contribution
 * weights per SPEC §7.7 (primary 1.0, secondary 0.4).
 */
object SeedData {

    // Display categories (RepCount's "Category" concept).
    val muscleGroups: List<String> = listOf(
        "Chest", "Back", "Shoulders", "Biceps", "Triceps",
        "Core", "Quads", "Hamstrings", "Glutes", "Calves",
        "Cardio", "Full Body", "Other",
    )

    data class SeedExercise(
        val name: String,
        val category: String,
        val type: ExerciseType,
        val primary: List<Muscle>,
        val secondary: List<Muscle> = emptyList(),
        val unilateral: Boolean = false,
    )

    val exercises: List<SeedExercise> = listOf(
        // ---- Chest ----
        SeedExercise("Barbell Bench Press", "Chest", STRENGTH_WEIGHT_REPS,
            primary = listOf(CHEST), secondary = listOf(TRICEPS, FRONT_DELTS)),
        SeedExercise("Incline Dumbbell Press", "Chest", STRENGTH_WEIGHT_REPS,
            primary = listOf(CHEST), secondary = listOf(FRONT_DELTS, TRICEPS)),
        SeedExercise("Dumbbell Fly", "Chest", STRENGTH_WEIGHT_REPS,
            primary = listOf(CHEST)),
        SeedExercise("Push-Up", "Chest", BODYWEIGHT_REPS,
            primary = listOf(CHEST), secondary = listOf(TRICEPS, FRONT_DELTS)),
        SeedExercise("Dip", "Chest", BODYWEIGHT_WEIGHT_REPS,
            primary = listOf(CHEST), secondary = listOf(TRICEPS, FRONT_DELTS)),

        // ---- Back ----
        SeedExercise("Deadlift", "Back", STRENGTH_WEIGHT_REPS,
            primary = listOf(GLUTES, HAMSTRINGS, LOWER_BACK),
            secondary = listOf(QUADS, TRAPS, LATS, FOREARMS)),
        SeedExercise("Barbell Row", "Back", STRENGTH_WEIGHT_REPS,
            primary = listOf(LATS, UPPER_BACK), secondary = listOf(REAR_DELTS, BICEPS, FOREARMS)),
        SeedExercise("Pull-Up", "Back", BODYWEIGHT_WEIGHT_REPS,
            primary = listOf(LATS), secondary = listOf(UPPER_BACK, BICEPS, FOREARMS)),
        SeedExercise("Chin-Up", "Back", BODYWEIGHT_WEIGHT_REPS,
            primary = listOf(LATS), secondary = listOf(BICEPS, UPPER_BACK)),
        SeedExercise("Assisted Pull-Up", "Back", ASSISTED_WEIGHT_REPS,
            primary = listOf(LATS), secondary = listOf(UPPER_BACK, BICEPS)),
        SeedExercise("Lat Pulldown", "Back", STRENGTH_WEIGHT_REPS,
            primary = listOf(LATS), secondary = listOf(BICEPS, UPPER_BACK)),
        SeedExercise("Seated Cable Row", "Back", STRENGTH_WEIGHT_REPS,
            primary = listOf(UPPER_BACK), secondary = listOf(LATS, BICEPS, REAR_DELTS)),

        // ---- Shoulders ----
        SeedExercise("Overhead Press", "Shoulders", STRENGTH_WEIGHT_REPS,
            primary = listOf(FRONT_DELTS), secondary = listOf(SIDE_DELTS, TRICEPS)),
        SeedExercise("Lateral Raise", "Shoulders", STRENGTH_WEIGHT_REPS,
            primary = listOf(SIDE_DELTS)),
        SeedExercise("Rear Delt Fly", "Shoulders", STRENGTH_WEIGHT_REPS,
            primary = listOf(REAR_DELTS), secondary = listOf(UPPER_BACK)),

        // ---- Arms ----
        SeedExercise("Barbell Curl", "Biceps", STRENGTH_WEIGHT_REPS,
            primary = listOf(BICEPS), secondary = listOf(FOREARMS)),
        SeedExercise("Hammer Curl", "Biceps", STRENGTH_WEIGHT_REPS,
            primary = listOf(BICEPS), secondary = listOf(FOREARMS)),
        SeedExercise("Triceps Pushdown", "Triceps", STRENGTH_WEIGHT_REPS,
            primary = listOf(TRICEPS)),
        SeedExercise("Skull Crusher", "Triceps", STRENGTH_WEIGHT_REPS,
            primary = listOf(TRICEPS)),

        // ---- Legs ----
        SeedExercise("Back Squat", "Quads", STRENGTH_WEIGHT_REPS,
            primary = listOf(QUADS, GLUTES), secondary = listOf(HAMSTRINGS, LOWER_BACK, ADDUCTORS)),
        SeedExercise("Front Squat", "Quads", STRENGTH_WEIGHT_REPS,
            primary = listOf(QUADS), secondary = listOf(GLUTES, ABS)),
        SeedExercise("Romanian Deadlift", "Hamstrings", STRENGTH_WEIGHT_REPS,
            primary = listOf(HAMSTRINGS, GLUTES), secondary = listOf(LOWER_BACK)),
        SeedExercise("Leg Press", "Quads", STRENGTH_WEIGHT_REPS,
            primary = listOf(QUADS), secondary = listOf(GLUTES, ADDUCTORS)),
        SeedExercise("Leg Curl", "Hamstrings", STRENGTH_WEIGHT_REPS,
            primary = listOf(HAMSTRINGS)),
        SeedExercise("Leg Extension", "Quads", STRENGTH_WEIGHT_REPS,
            primary = listOf(QUADS)),
        SeedExercise("Bulgarian Split Squat", "Quads", STRENGTH_WEIGHT_REPS,
            primary = listOf(QUADS, GLUTES), secondary = listOf(HAMSTRINGS, ADDUCTORS),
            unilateral = true),
        SeedExercise("Hip Thrust", "Glutes", STRENGTH_WEIGHT_REPS,
            primary = listOf(GLUTES), secondary = listOf(HAMSTRINGS)),
        SeedExercise("Standing Calf Raise", "Calves", STRENGTH_WEIGHT_REPS,
            primary = listOf(CALVES)),

        // ---- Core ----
        SeedExercise("Plank", "Core", BODYWEIGHT_TIME,
            primary = listOf(ABS), secondary = listOf(OBLIQUES)),
        SeedExercise("Weighted Plank", "Core", STRENGTH_WEIGHT_TIME,
            primary = listOf(ABS), secondary = listOf(OBLIQUES)),
        SeedExercise("Hanging Leg Raise", "Core", BODYWEIGHT_REPS,
            primary = listOf(ABS), secondary = listOf(OBLIQUES, FOREARMS)),
        SeedExercise("Crunch", "Core", BODYWEIGHT_REPS,
            primary = listOf(ABS)),

        // ---- Loaded carries / conditioning ----
        SeedExercise("Farmer's Walk", "Full Body", STRENGTH_WEIGHT_DISTANCE,
            primary = listOf(FOREARMS, TRAPS), secondary = listOf(ABS, GLUTES)),
        SeedExercise("Sled Push", "Full Body", STRENGTH_WEIGHT_DISTANCE_TIME,
            primary = listOf(QUADS, GLUTES), secondary = listOf(CALVES, HAMSTRINGS)),

        // ---- Cardio ----
        SeedExercise("Running", "Cardio", CARDIO_TIME_DISTANCE_KCAL,
            primary = emptyList(), secondary = listOf(QUADS, HAMSTRINGS, GLUTES, CALVES)),
        SeedExercise("Cycling", "Cardio", CARDIO_TIME_DISTANCE_KCAL,
            primary = emptyList(), secondary = listOf(QUADS, HAMSTRINGS, GLUTES, CALVES)),
        SeedExercise("Rowing Machine", "Cardio", CARDIO_TIME_DISTANCE_KCAL,
            primary = emptyList(), secondary = listOf(UPPER_BACK, LATS, QUADS, HAMSTRINGS)),

        // ---- Other ----
        SeedExercise("Stretching / Mobility", "Other", OTHER_NOTES,
            primary = emptyList()),
    )

    data class SeedRoutineExercise(
        val exerciseName: String,
        val targetSets: Int,
        val repMin: Int? = null,
        val repMax: Int? = null,
    )

    /** One flat first-run template (SPEC §12.3) — no periodization. */
    const val EXAMPLE_ROUTINE_NAME = "Full Body A (example)"
    val exampleRoutine: List<SeedRoutineExercise> = listOf(
        SeedRoutineExercise("Back Squat", targetSets = 3, repMin = 5, repMax = 8),
        SeedRoutineExercise("Barbell Bench Press", targetSets = 3, repMin = 5, repMax = 8),
        SeedRoutineExercise("Barbell Row", targetSets = 3, repMin = 6, repMax = 10),
        SeedRoutineExercise("Overhead Press", targetSets = 2, repMin = 8, repMax = 12),
        SeedRoutineExercise("Romanian Deadlift", targetSets = 2, repMin = 8, repMax = 12),
        SeedRoutineExercise("Plank", targetSets = 3), // time-based: no rep range
    )

    fun contributionWeight(role: dev.gatsyuk.soloranking.core.model.MuscleRole): Double =
        when (role) {
            dev.gatsyuk.soloranking.core.model.MuscleRole.PRIMARY -> PRIMARY
            dev.gatsyuk.soloranking.core.model.MuscleRole.SECONDARY -> SECONDARY
        }
}
