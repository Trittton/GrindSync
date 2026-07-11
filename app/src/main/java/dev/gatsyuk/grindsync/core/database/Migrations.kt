package dev.gatsyuk.grindsync.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every schema change ships a migration (NFR-4). Destructive fallback is
 * never enabled — a missing migration must fail loudly in development,
 * not silently wipe user data in production.
 */
object Migrations {

    /** v1 -> v2: adds Exercise.is_archived (soft-delete instead of losing history). */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE exercise ADD COLUMN is_archived INTEGER NOT NULL DEFAULT 0")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
