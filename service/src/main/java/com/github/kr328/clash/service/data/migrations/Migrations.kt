package com.github.kr328.clash.service.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE imported ADD COLUMN ageSecretKey TEXT")
        database.execSQL("ALTER TABLE pending ADD COLUMN ageSecretKey TEXT")
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        for (table in listOf("imported", "pending")) {
            database.execSQL("ALTER TABLE $table ADD COLUMN followProvider INTEGER NOT NULL DEFAULT 0")
            // Eight hours on a link subscription were set by the app when it was added, not chosen
            // by the person: such a subscription now follows its provider (D-311).
            database.execSQL("UPDATE $table SET followProvider = 1 WHERE type = 'Url' AND interval = 28800000")
        }
    }
}

val MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
)
