package dev.gatsyuk.grindsync.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** NFR-4: every schema change ships a migration proven against real old data. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate3To4_addsDefaultWarmupSets() {
        helper.createDatabase(dbName, 3).apply {
            execSQL("INSERT INTO muscle_group (id, name, display_order) VALUES (1, 'Chest', 0)")
            execSQL(
                "INSERT INTO exercise (id, name, muscle_group_id, exercise_type, is_unilateral, is_custom, is_archived) " +
                    "VALUES (10, 'Barbell Bench Press', 1, 'STRENGTH_WEIGHT_REPS', 0, 0, 0)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 4, true, Migrations.MIGRATION_3_4)

        db.query("SELECT name, default_warmup_sets FROM exercise WHERE id = 10").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Barbell Bench Press", cursor.getString(0))
            assertEquals(0, cursor.getInt(1)) // default: no auto warmups
        }
    }

    @Test
    fun migrate2To3_addsNutritionTablesAndKeepsWorkoutData() {
        helper.createDatabase(dbName, 2).apply {
            execSQL("INSERT INTO muscle_group (id, name, display_order) VALUES (1, 'Chest', 0)")
            execSQL(
                "INSERT INTO exercise (id, name, muscle_group_id, exercise_type, is_unilateral, is_custom, is_archived) " +
                    "VALUES (10, 'Barbell Bench Press', 1, 'STRENGTH_WEIGHT_REPS', 0, 0, 0)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 3, true, Migrations.MIGRATION_2_3)

        // Old data intact.
        db.query("SELECT name FROM exercise WHERE id = 10").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Barbell Bench Press", cursor.getString(0))
        }
        // New tables usable.
        db.execSQL(
            "INSERT INTO food_item (name, source, serving_size, serving_unit, " +
                "kcal_per_serving, protein_g, carbs_g, fat_g, is_favorite) " +
                "VALUES ('Oats', 'CUSTOM', 100.0, 'g', 370.0, 13.0, 60.0, 7.0, 0)",
        )
        db.execSQL(
            "INSERT INTO diary_entry (date, meal, food_item_id, quantity_servings) " +
                "VALUES (20645, 'BREAKFAST', 1, 0.8)",
        )
        db.query(
            "SELECT f.name, d.quantity_servings FROM diary_entry d " +
                "JOIN food_item f ON f.id = d.food_item_id",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Oats", cursor.getString(0))
            assertEquals(0.8, cursor.getDouble(1), 0.001)
        }
    }

    @Test
    fun migrate1To2_preservesDataAndAddsIsArchived() {
        // Create v1 schema and insert a row with real data.
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO muscle_group (id, name, display_order) VALUES (1, 'Chest', 0)")
            execSQL(
                "INSERT INTO exercise (id, name, muscle_group_id, exercise_type, is_unilateral, is_custom) " +
                    "VALUES (10, 'Barbell Bench Press', 1, 'STRENGTH_WEIGHT_REPS', 0, 0)",
            )
            close()
        }

        // Migrate and validate against the exported v2 schema.
        val db = helper.runMigrationsAndValidate(dbName, 2, true, Migrations.MIGRATION_1_2)

        db.query("SELECT name, exercise_type, is_archived FROM exercise WHERE id = 10").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Barbell Bench Press", cursor.getString(0))
            assertEquals("STRENGTH_WEIGHT_REPS", cursor.getString(1))
            assertEquals(0, cursor.getInt(2)) // default: not archived
        }
    }
}
