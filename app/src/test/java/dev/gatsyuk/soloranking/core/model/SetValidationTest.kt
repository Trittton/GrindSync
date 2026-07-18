package dev.gatsyuk.soloranking.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Per-type field gating for the polymorphic SetEntry (SPEC §2.5/§5). */
class SetValidationTest {

    @Test
    fun `strength weight-reps accepts weight and reps`() {
        val result = SetValidation.validate(
            ExerciseType.STRENGTH_WEIGHT_REPS,
            SetValidation.Candidate(weightKg = 100.0, reps = 5),
        )
        assertEquals(SetValidation.Result.Valid, result)
    }

    @Test
    fun `strength weight-reps rejects cardio fields`() {
        val result = SetValidation.validate(
            ExerciseType.STRENGTH_WEIGHT_REPS,
            SetValidation.Candidate(weightKg = 100.0, reps = 5, kcal = 200),
        )
        assertTrue(result is SetValidation.Result.Invalid)
        assertEquals(setOf(SetField.KCAL), (result as SetValidation.Result.Invalid).disallowed)
    }

    @Test
    fun `bodyweight time accepts time only`() {
        val result = SetValidation.validate(
            ExerciseType.BODYWEIGHT_TIME,
            SetValidation.Candidate(timeSeconds = 60),
        )
        assertEquals(SetValidation.Result.Valid, result)
    }

    @Test
    fun `bodyweight time rejects weight`() {
        val result = SetValidation.validate(
            ExerciseType.BODYWEIGHT_TIME,
            SetValidation.Candidate(weightKg = 20.0, timeSeconds = 60),
        )
        assertTrue(result is SetValidation.Result.Invalid)
    }

    @Test
    fun `empty set is invalid for measurable types`() {
        val result = SetValidation.validate(
            ExerciseType.CARDIO_TIME_DISTANCE_KCAL,
            SetValidation.Candidate(),
        )
        assertTrue(result is SetValidation.Result.Invalid)
    }

    @Test
    fun `other-notes accepts empty measurements`() {
        val result = SetValidation.validate(ExerciseType.OTHER_NOTES, SetValidation.Candidate())
        assertEquals(SetValidation.Result.Valid, result)
    }

    @Test
    fun `every type gates exactly its declared fields`() {
        // Each type must reject any field outside its declared set.
        val allFieldsCandidate = SetValidation.Candidate(
            weightKg = 1.0, reps = 1, timeSeconds = 1, distanceMeters = 1.0, kcal = 1,
        )
        ExerciseType.entries.forEach { type ->
            val result = SetValidation.validate(type, allFieldsCandidate)
            if (type.fields.size == SetField.entries.size) {
                assertEquals(SetValidation.Result.Valid, result)
            } else {
                assertTrue("$type should reject extra fields", result is SetValidation.Result.Invalid)
                assertEquals(
                    SetField.entries.toSet() - type.fields,
                    (result as SetValidation.Result.Invalid).disallowed,
                )
            }
        }
    }
}
