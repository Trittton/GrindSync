package dev.gatsyuk.grindsync.core.model

/**
 * A measurement field a set can capture. Which fields are valid for a given
 * set is decided by the parent exercise's [ExerciseType] — never by the set itself.
 */
enum class SetField {
    WEIGHT,   // stored canonically in kg. Semantics vary by type:
              //   STRENGTH_*  -> external load
              //   BODYWEIGHT_WEIGHT_REPS -> ADDED weight on top of bodyweight
              //   ASSISTED_WEIGHT_REPS   -> ASSIST weight (machine counterweight)
    REPS,
    TIME,     // seconds
    DISTANCE, // meters
    KCAL,
}

/**
 * Tracking mode of an exercise (SPEC §2.5). Gates which [SetField]s a
 * SetEntry may store and which inputs the logging UI renders.
 */
enum class ExerciseType(val fields: Set<SetField>) {
    STRENGTH_WEIGHT_REPS(setOf(SetField.WEIGHT, SetField.REPS)),
    STRENGTH_WEIGHT_TIME(setOf(SetField.WEIGHT, SetField.TIME)),
    STRENGTH_WEIGHT_DISTANCE(setOf(SetField.WEIGHT, SetField.DISTANCE)),
    STRENGTH_WEIGHT_DISTANCE_TIME(setOf(SetField.WEIGHT, SetField.DISTANCE, SetField.TIME)),
    BODYWEIGHT_WEIGHT_REPS(setOf(SetField.WEIGHT, SetField.REPS)),
    BODYWEIGHT_REPS(setOf(SetField.REPS)),
    BODYWEIGHT_TIME(setOf(SetField.TIME)),
    ASSISTED_WEIGHT_REPS(setOf(SetField.WEIGHT, SetField.REPS)),
    CARDIO_TIME_DISTANCE_KCAL(setOf(SetField.TIME, SetField.DISTANCE, SetField.KCAL)),
    OTHER_NOTES(emptySet()),
}

/**
 * Per-type validation for a candidate set. Notes are always allowed.
 * Fields outside the exercise type's allowed set must be null; at least one
 * allowed field must be present (except OTHER_NOTES, which stores notes only).
 */
object SetValidation {

    data class Candidate(
        val weightKg: Double? = null,
        val reps: Int? = null,
        val timeSeconds: Int? = null,
        val distanceMeters: Double? = null,
        val kcal: Int? = null,
    ) {
        fun presentFields(): Set<SetField> = buildSet {
            if (weightKg != null) add(SetField.WEIGHT)
            if (reps != null) add(SetField.REPS)
            if (timeSeconds != null) add(SetField.TIME)
            if (distanceMeters != null) add(SetField.DISTANCE)
            if (kcal != null) add(SetField.KCAL)
        }
    }

    sealed interface Result {
        data object Valid : Result
        data class Invalid(val disallowed: Set<SetField>, val missingAnyOf: Set<SetField>) : Result
    }

    fun validate(type: ExerciseType, candidate: Candidate): Result {
        val present = candidate.presentFields()
        val disallowed = present - type.fields
        val missing = if (type == ExerciseType.OTHER_NOTES || present.intersect(type.fields).isNotEmpty()) {
            emptySet()
        } else {
            type.fields
        }
        return if (disallowed.isEmpty() && missing.isEmpty()) Result.Valid
        else Result.Invalid(disallowed, missing)
    }
}
