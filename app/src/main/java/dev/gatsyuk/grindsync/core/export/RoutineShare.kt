package dev.gatsyuk.grindsync.core.export

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Shareable routine format (user feature: send a routine through any app as
 * text, import by pasting). Exercises travel by NAME with their full
 * definition — type, category, muscle mapping — so the receiver's app can
 * recreate anything they don't already have.
 */
@Serializable
data class SharedRoutine(
    val grindsyncRoutine: Int = FORMAT_VERSION,
    val name: String,
    val notes: String? = null,
    val exercises: List<SharedRoutineExercise> = emptyList(),
) {
    companion object {
        const val FORMAT_VERSION = 1
    }
}

@Serializable
data class SharedRoutineExercise(
    val name: String,
    val category: String,
    val exerciseType: String,
    val isUnilateral: Boolean = false,
    val defaultWarmupSets: Int = 0,
    val muscles: List<SharedMuscle> = emptyList(),
    val targetSets: Int,
    val repMin: Int? = null,
    val repMax: Int? = null,
)

@Serializable
data class SharedMuscle(val muscle: String, val role: String, val contributionWeight: Double)

object RoutineShare {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    fun toJson(routine: SharedRoutine): String =
        json.encodeToString(SharedRoutine.serializer(), routine)

    /** Throws with a readable message on garbage or a newer format. */
    fun fromJson(text: String): SharedRoutine {
        val parsed = runCatching { json.decodeFromString(SharedRoutine.serializer(), text.trim()) }
            .getOrElse { throw IllegalArgumentException("That doesn't look like a shared GrindSync routine.") }
        require(parsed.grindsyncRoutine in 1..SharedRoutine.FORMAT_VERSION) {
            "This routine was shared from a newer GrindSync version. Update the app."
        }
        require(parsed.exercises.isNotEmpty()) { "The shared routine has no exercises." }
        return parsed
    }
}
