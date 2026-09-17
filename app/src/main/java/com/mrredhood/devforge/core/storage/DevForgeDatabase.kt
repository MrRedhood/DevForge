package com.mrredhood.devforge.core.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [WorkspaceEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class DevForgeDatabase : RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao

    companion object {
        @Volatile private var INSTANCE: DevForgeDatabase? = null

        fun get(context: Context): DevForgeDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DevForgeDatabase::class.java,
                    "devforge.db",
                ).build().also { INSTANCE = it }
            }
    }
}
