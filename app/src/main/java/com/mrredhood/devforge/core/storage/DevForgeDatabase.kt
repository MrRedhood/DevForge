package com.mrredhood.devforge.core.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [WorkspaceEntity::class, BuildReceiptEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class DevForgeDatabase : RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun buildReceiptDao(): BuildReceiptDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `build_receipts` (
                        `runId` INTEGER NOT NULL,
                        `runNumber` INTEGER NOT NULL,
                        `githubOwner` TEXT NOT NULL,
                        `githubRepository` TEXT NOT NULL,
                        `workflowFile` TEXT NOT NULL,
                        `branch` TEXT NOT NULL,
                        `buildTask` TEXT NOT NULL,
                        `artifactName` TEXT NOT NULL,
                        `target` TEXT NOT NULL,
                        `state` TEXT NOT NULL,
                        `conclusion` TEXT,
                        `htmlUrl` TEXT,
                        `updatedAt` TEXT,
                        `recordedAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`runId`)
                    )
                    """.trimIndent(),
                )
            }
        }

        @Volatile private var INSTANCE: DevForgeDatabase? = null

        fun get(context: Context): DevForgeDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DevForgeDatabase::class.java,
                    "devforge.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
