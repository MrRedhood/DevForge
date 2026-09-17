package com.mrredhood.devforge.core.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [WorkspaceEntity::class, BuildReceiptEntity::class, ApprovalEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class DevForgeDatabase : RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun buildReceiptDao(): BuildReceiptDao
    abstract fun approvalDao(): ApprovalDao

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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `approval_actions` (
                        `approvalId` TEXT NOT NULL,
                        `actionId` TEXT NOT NULL,
                        `capability` TEXT NOT NULL,
                        `risk` TEXT NOT NULL,
                        `workspaceId` TEXT NOT NULL,
                        `summary` TEXT NOT NULL,
                        `parametersHash` TEXT NOT NULL,
                        `preconditionHash` TEXT,
                        `payload` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `expiresAtEpochMs` INTEGER NOT NULL,
                        `resolvedAtEpochMs` INTEGER,
                        PRIMARY KEY(`approvalId`)
                    )
                    """.trimIndent(),
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_approval_actions_status_createdAtEpochMs` ON `approval_actions` (`status`, `createdAtEpochMs`)")
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
