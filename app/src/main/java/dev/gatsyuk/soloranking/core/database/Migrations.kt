package dev.gatsyuk.soloranking.core.database

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

    /** v2 -> v3: nutrition domain (food_item, diary_entry, nutrition_target). */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `food_item` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                    "`brand` TEXT, `source` TEXT NOT NULL, `barcode` TEXT, " +
                    "`serving_size` REAL NOT NULL, `serving_unit` TEXT NOT NULL, " +
                    "`kcal_per_serving` REAL NOT NULL, `protein_g` REAL NOT NULL, " +
                    "`carbs_g` REAL NOT NULL, `fat_g` REAL NOT NULL, `is_favorite` INTEGER NOT NULL)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_item_name` ON `food_item` (`name`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `diary_entry` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` INTEGER NOT NULL, " +
                    "`meal` TEXT NOT NULL, `food_item_id` INTEGER NOT NULL, " +
                    "`quantity_servings` REAL NOT NULL, " +
                    "FOREIGN KEY(`food_item_id`) REFERENCES `food_item`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE RESTRICT )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_diary_entry_date` ON `diary_entry` (`date`)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_diary_entry_food_item_id` ON `diary_entry` (`food_item_id`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `nutrition_target` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`effective_date` INTEGER NOT NULL, `kcal_target` INTEGER NOT NULL, " +
                    "`protein_target_g` INTEGER NOT NULL, `carbs_target_g` INTEGER NOT NULL, " +
                    "`fat_target_g` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_nutrition_target_effective_date` " +
                    "ON `nutrition_target` (`effective_date`)",
            )
        }
    }

    /** v3 -> v4: Exercise.default_warmup_sets (auto-marked W rows on start). */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE exercise ADD COLUMN default_warmup_sets INTEGER NOT NULL DEFAULT 0",
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}
