package dev.gatsyuk.grindsync.core.export

import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * Pure (JVM-only, no Android) conversion between a [BackupSnapshot] and its
 * on-disk forms. Kept side-effect-free so the round-trip is unit-testable.
 */
object BackupSerializer {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true // tolerate fields added by a newer version
        encodeDefaults = true
    }

    fun toJson(snapshot: BackupSnapshot): String =
        json.encodeToString(BackupSnapshot.serializer(), snapshot)

    /** Throws on malformed JSON or a snapshot newer than we understand. */
    fun fromJson(text: String): BackupSnapshot {
        val snapshot = json.decodeFromString(BackupSnapshot.serializer(), text)
        require(snapshot.version <= BackupSnapshot.CURRENT_VERSION) {
            "Backup version ${snapshot.version} is newer than this app supports " +
                "(${BackupSnapshot.CURRENT_VERSION}). Update GrindSync and try again."
        }
        return snapshot
    }

    // ---- CSV (read-oriented, for spreadsheets; SPEC §10 NFR-2) ----

    /** One denormalized row per logged set — the sheet a user actually wants. */
    fun workoutsCsv(snapshot: BackupSnapshot): String {
        val exercisesById = snapshot.exercises.associateBy { it.id }
        val workoutsById = snapshot.workouts.associateBy { it.id }
        val workoutExercisesById = snapshot.workoutExercises.associateBy { it.id }

        val header = listOf(
            "date", "workout", "bodyweight_kg", "exercise", "exercise_type",
            "set_position", "set_kind", "weight_kg", "reps", "time_seconds",
            "distance_meters", "kcal", "notes",
        )
        val rows = snapshot.setEntries
            .sortedWith(compareBy({ it.workoutExerciseId }, { it.position }))
            .mapNotNull { set ->
                val we = workoutExercisesById[set.workoutExerciseId] ?: return@mapNotNull null
                val workout = workoutsById[we.workoutId] ?: return@mapNotNull null
                val exercise = exercisesById[we.exerciseId]
                listOf(
                    LocalDate.ofEpochDay(workout.dateEpochDay).toString(),
                    workout.name,
                    workout.bodyweightKg?.toString().orEmpty(),
                    exercise?.name.orEmpty(),
                    exercise?.exerciseType.orEmpty(),
                    set.position.toString(),
                    set.setKind,
                    set.weightKg?.toString().orEmpty(),
                    set.reps?.toString().orEmpty(),
                    set.timeSeconds?.toString().orEmpty(),
                    set.distanceMeters?.toString().orEmpty(),
                    set.kcal?.toString().orEmpty(),
                    set.notes.orEmpty(),
                )
            }
        return buildCsv(header, rows)
    }

    /** One row per diary entry with resolved food + computed macros. */
    fun nutritionCsv(snapshot: BackupSnapshot): String {
        val foodsById = snapshot.foodItems.associateBy { it.id }
        val header = listOf(
            "date", "meal", "food", "brand", "quantity_servings",
            "kcal", "protein_g", "carbs_g", "fat_g",
        )
        val rows = snapshot.diaryEntries
            .sortedWith(compareBy({ it.dateEpochDay }, { it.id }))
            .mapNotNull { entry ->
                val food = foodsById[entry.foodItemId] ?: return@mapNotNull null
                val q = entry.quantityServings
                listOf(
                    LocalDate.ofEpochDay(entry.dateEpochDay).toString(),
                    entry.meal,
                    food.name,
                    food.brand.orEmpty(),
                    trimNumber(q),
                    trimNumber(food.kcalPerServing * q),
                    trimNumber(food.proteinG * q),
                    trimNumber(food.carbsG * q),
                    trimNumber(food.fatG * q),
                )
            }
        return buildCsv(header, rows)
    }

    private fun trimNumber(v: Double): String {
        val s = "%.2f".format(v)
        return s.trimEnd('0').trimEnd('.')
    }

    private fun buildCsv(header: List<String>, rows: List<List<String>>): String {
        val sb = StringBuilder()
        sb.appendLine(header.joinToString(",") { escapeCsv(it) })
        rows.forEach { row -> sb.appendLine(row.joinToString(",") { escapeCsv(it) }) }
        return sb.toString()
    }

    /** RFC-4180 quoting: wrap in quotes when the value has a comma, quote, or newline. */
    private fun escapeCsv(value: String): String {
        val needsQuote = value.contains(',') || value.contains('"') ||
            value.contains('\n') || value.contains('\r')
        if (!needsQuote) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }
}
