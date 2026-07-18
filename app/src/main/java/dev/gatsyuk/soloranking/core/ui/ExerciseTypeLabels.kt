package dev.gatsyuk.soloranking.core.ui

import dev.gatsyuk.soloranking.core.model.ExerciseType

/** Human-readable names for the 10 tracking modes (SPEC §2.5). */
fun exerciseTypeLabel(type: ExerciseType): String = when (type) {
    ExerciseType.STRENGTH_WEIGHT_REPS -> "Strength: weight × reps"
    ExerciseType.STRENGTH_WEIGHT_TIME -> "Strength: weight × time"
    ExerciseType.STRENGTH_WEIGHT_DISTANCE -> "Strength: weight × distance"
    ExerciseType.STRENGTH_WEIGHT_DISTANCE_TIME -> "Strength: weight × distance × time"
    ExerciseType.BODYWEIGHT_WEIGHT_REPS -> "Bodyweight: +weight × reps"
    ExerciseType.BODYWEIGHT_REPS -> "Bodyweight: reps"
    ExerciseType.BODYWEIGHT_TIME -> "Bodyweight: time"
    ExerciseType.ASSISTED_WEIGHT_REPS -> "Assisted: -weight × reps"
    ExerciseType.CARDIO_TIME_DISTANCE_KCAL -> "Cardio: time / distance / kcal"
    ExerciseType.OTHER_NOTES -> "Other: notes only"
}
