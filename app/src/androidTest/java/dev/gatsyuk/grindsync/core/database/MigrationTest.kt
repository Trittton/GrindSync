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
