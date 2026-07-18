package dev.gatsyuk.soloranking.core.database.seed

import dev.gatsyuk.soloranking.core.database.AppDatabase
import dev.gatsyuk.soloranking.core.database.entity.ExerciseEntity
import dev.gatsyuk.soloranking.core.database.entity.ExerciseMuscleEntity
import dev.gatsyuk.soloranking.core.database.entity.MuscleGroupEntity
import dev.gatsyuk.soloranking.core.database.entity.RoutineEntity
import dev.gatsyuk.soloranking.core.database.entity.RoutineExerciseEntity
import dev.gatsyuk.soloranking.core.model.ContributionWeights
import dev.gatsyuk.soloranking.core.model.MuscleRole
import dev.gatsyuk.soloranking.core.model.TargetMode

/**
 * Idempotent first-run seeding. Runs through DAOs (not raw SQL) so the seed
 * path is exercised by the same code paths and validation the app uses.
 */
class DatabaseSeeder(private val db: AppDatabase) {

    suspend fun seedIfEmpty() {
        if (db.exerciseDao().countExercises() > 0) return
        seed()
    }

    suspend fun seed() {
        val exerciseDao = db.exerciseDao()
        val routineDao = db.routineDao()

        // 1. Categories
        val groupIds = exerciseDao.insertMuscleGroups(
            SeedData.muscleGroups.mapIndexed { index, name ->
                MuscleGroupEntity(name = name, displayOrder = index)
            },
        )
        val groupIdByName = SeedData.muscleGroups.zip(groupIds).toMap()

        // 2. Exercises
        val exerciseIds = exerciseDao.insertExercises(
            SeedData.exercises.map { seed ->
                ExerciseEntity(
                    name = seed.name,
                    muscleGroupId = requireNotNull(groupIdByName[seed.category]) {
                        "Unknown category '${seed.category}' for '${seed.name}'"
                    },
                    exerciseType = seed.type,
                    isUnilateral = seed.unilateral,
                    isCustom = false,
                )
            },
        )
        val exerciseIdByName = SeedData.exercises.map { it.name }.zip(exerciseIds).toMap()

        // 3. Muscle mappings (primary 1.0 / secondary 0.4)
        exerciseDao.insertExerciseMuscles(
            SeedData.exercises.flatMap { seed ->
                val id = exerciseIdByName.getValue(seed.name)
                seed.primary.map {
                    ExerciseMuscleEntity(id, it, MuscleRole.PRIMARY, ContributionWeights.PRIMARY)
                } + seed.secondary.map {
                    ExerciseMuscleEntity(id, it, MuscleRole.SECONDARY, ContributionWeights.SECONDARY)
                }
            },
        )

        // 4. Example routine template
        val routineId = routineDao.insertRoutine(
            RoutineEntity(
                name = SeedData.EXAMPLE_ROUTINE_NAME,
                targetMode = TargetMode.LATEST,
                notes = "Starter full-body template. Edit freely or build your own.",
            ),
        )
        routineDao.insertRoutineExercises(
            SeedData.exampleRoutine.mapIndexed { index, entry ->
                RoutineExerciseEntity(
                    routineId = routineId,
                    exerciseId = requireNotNull(exerciseIdByName[entry.exerciseName]) {
                        "Routine references unknown exercise '${entry.exerciseName}'"
                    },
                    position = index,
                    targetSets = entry.targetSets,
                    repMin = entry.repMin,
                    repMax = entry.repMax,
                )
            },
        )
    }
}
