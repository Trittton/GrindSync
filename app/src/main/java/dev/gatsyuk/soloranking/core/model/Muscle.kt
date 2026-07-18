package dev.gatsyuk.soloranking.core.model

/**
 * Anatomical regions for the ExerciseMuscle mapping and the future Rank Map
 * body figure (SPEC §7.7). Distinct from MuscleGroup, which is the coarser
 * display "category" an exercise is filed under.
 */
enum class Muscle {
    CHEST,
    LATS,
    UPPER_BACK,   // rhomboids + mid-traps
    LOWER_BACK,
    TRAPS,
    FRONT_DELTS,
    SIDE_DELTS,
    REAR_DELTS,
    BICEPS,
    TRICEPS,
    FOREARMS,
    ABS,
    OBLIQUES,
    GLUTES,
    QUADS,
    HAMSTRINGS,
    ADDUCTORS,
    CALVES,
}

enum class MuscleRole { PRIMARY, SECONDARY }

object ContributionWeights {
    const val PRIMARY = 1.0
    const val SECONDARY = 0.4
}
