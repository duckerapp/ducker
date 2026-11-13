package com.github.kr328.clash.service.data

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.github.kr328.clash.common.Global
import com.github.kr328.clash.service.data.migrations.MIGRATIONS
import androidx.room3.Database as DB

@DB(
    version = 3,
    entities = [Imported::class, Pending::class, Selection::class],
    exportSchema = true,
)
abstract class Database : RoomDatabase() {
    abstract fun openImportedDao(): ImportedDao
    abstract fun openPendingDao(): PendingDao
    abstract fun openSelectionProxyDao(): SelectionDao

    companion object {
        val database: Database
            @Synchronized get() {
                return instance ?: open(Global.application).apply {
                    instance = this
                }
            }

        private var instance: Database? = null

        // The `Context` builder finds `profiles` in the app's databases folder, where Room 2 kept it; the builder
        // without one takes the name as a path and would open an empty database. The system SQLite, as before (F-495).
        private fun open(context: Context): Database {
            return Room.databaseBuilder(
                context.applicationContext,
                Database::class.java,
                "profiles"
            ).setDriver(AndroidSQLiteDriver()).addMigrations(*MIGRATIONS).build()
        }
    }
}
