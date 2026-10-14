package com.github.kr328.clash.service.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.github.kr328.clash.common.Global
import com.github.kr328.clash.service.data.migrations.MIGRATIONS
import androidx.room.Database as DB

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

        private fun open(context: Context): Database {
            return Room.databaseBuilder(
                context.applicationContext,
                Database::class.java,
                "profiles"
            ).addMigrations(*MIGRATIONS).build()
        }
    }
}
