package com.mrredhood.devforge.core.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.RoomDatabase.Callback
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mrredhood.devforge.core.policy.Capability
import com.mrredhood.devforge.core.policy.CapabilityGrantRegistry
import com.mrredhood.devforge.core.policy.RiskLevel

@Database(
    entities = [
        WorkspaceEntity::class,
        BuildReceiptEntity::class,
        ApprovalEntity::class,
        EditorTabEntity::class,
        EditorSnapshotEntity::class,
        AgentTaskEntity::class,
        AutomationEntity::class,
        AutomationRunEntity::class,
        AutomationTriggerStateEntity::class,
        AuditEventEntity::class,
        CapabilityGrantEntity::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class DevForgeDatabase : RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun buildReceiptDao(): BuildReceiptDao
    abstract fun approvalDao(): ApprovalDao
    abstract fun editorTabDao(): EditorTabDao
    abstract fun editorSnapshotDao(): EditorSnapshotDao
    abstract fun agentTaskDao(): AgentTaskDao
    abstract fun automationDao(): AutomationDao
    abstract fun automationTriggerStateDao(): AutomationTriggerStateDao
    abstract fun auditEventDao(): AuditEventDao
    abstract fun capabilityGrantDao(): CapabilityGrantDao
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
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
                """.trimIndent())
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
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
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_approval_actions_status_createdAtEpochMs` ON approval_actions(status, createdAtEpochMs)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `editor_tabs` (
                        `uri` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `savedContent` TEXT NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`uri`)
                    )
                """.trimIndent())
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `editor_snapshots` (
                        `snapshotId` TEXT NOT NULL,
                        `uri` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `contentHash` TEXT NOT NULL,
                        `reason` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`snapshotId`)
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_editor_snapshots_uri_createdAtEpochMs` ON editor_snapshots(uri, createdAtEpochMs)")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `agent_tasks` (
                        `taskId` TEXT NOT NULL,
                        `workspaceId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `instruction` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        `payload` TEXT,
                        `errorMessage` TEXT,
                        PRIMARY KEY(`taskId`)
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_tasks_workspaceId_updatedAtEpochMs` ON agent_tasks(workspaceId, updatedAtEpochMs)")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `automation_definitions` (
                        `automationId` TEXT NOT NULL,
                        `workspaceId` TEXT,
                        `name` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `triggerType` TEXT NOT NULL,
                        `schedule` TEXT,
                        `actionGraph` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`automationId`)
                    )
                """.trimIndent())
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `automation_runs` (
                        `runId` TEXT NOT NULL,
                        `automationId` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `startedAtEpochMs` INTEGER NOT NULL,
                        `completedAtEpochMs` INTEGER,
                        `errorMessage` TEXT,
                        `receiptJson` TEXT,
                        PRIMARY KEY(`runId`)
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_automation_runs_automationId_startedAtEpochMs` ON automation_runs(automationId, startedAtEpochMs)")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `audit_events` (
                        `eventId` TEXT NOT NULL,
                        `workspaceId` TEXT,
                        `actionId` TEXT,
                        `capability` TEXT,
                        `risk` TEXT,
                        `eventType` TEXT NOT NULL,
                        `summary` TEXT NOT NULL,
                        `metadataJson` TEXT,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`eventId`)
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_events_createdAtEpochMs` ON audit_events(createdAtEpochMs)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_events_workspaceId_createdAtEpochMs` ON audit_events(workspaceId, createdAtEpochMs)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `capability_grants` (
                        `grantId` TEXT NOT NULL,
                        `workspaceId` TEXT NOT NULL,
                        `capability` TEXT NOT NULL,
                        `maxRisk` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `expiresAtEpochMs` INTEGER,
                        `enabled` INTEGER NOT NULL,
                        PRIMARY KEY(`grantId`)
                    )
                """.trimIndent())
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_capability_grants_workspaceId_capability` ON capability_grants(workspaceId, capability)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_capability_grants_expiresAtEpochMs` ON capability_grants(expiresAtEpochMs)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `chat_sessions` (
                        `sessionId` TEXT NOT NULL,
                        `scopeId` TEXT NOT NULL,
                        `providerId` TEXT NOT NULL,
                        `modelId` TEXT NOT NULL,
                        `modelName` TEXT NOT NULL,
                        `contextLimit` INTEGER,
                        `title` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`sessionId`)
                    )
                """.trimIndent())
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_chat_sessions_scopeId_providerId_modelId` ON chat_sessions(scopeId, providerId, modelId)")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `chat_messages` (
                        `messageId` TEXT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `commandName` TEXT,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`messageId`)
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_sessionId_createdAtEpochMs` ON chat_messages(sessionId, createdAtEpochMs)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE agent_tasks ADD COLUMN currentStep INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE agent_tasks ADD COLUMN stepCount INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE agent_tasks ADD COLUMN result TEXT")
                database.execSQL("ALTER TABLE agent_tasks ADD COLUMN approvalId TEXT")
                database.execSQL("ALTER TABLE agent_tasks ADD COLUMN lastToolId TEXT")
                database.execSQL("ALTER TABLE agent_tasks ADD COLUMN startedAtEpochMs INTEGER")
                database.execSQL("ALTER TABLE agent_tasks ADD COLUMN completedAtEpochMs INTEGER")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `automation_trigger_state` (
                        `automationId` TEXT NOT NULL,
                        `lastRepositoryFingerprint` TEXT,
                        `lastBuildRunId` INTEGER,
                        `lastEvaluatedAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`automationId`)
                    )
                """.trimIndent())
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .addCallback(object : Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            installAuditTriggers(db)
                            hydrateGrantRegistry(db)
                        }
                    })
                    .build()
                    .also { INSTANCE = it }
            }

        private fun installAuditTriggers(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS devforge_approval_insert_audit
                AFTER INSERT ON approval_actions
                BEGIN
                    INSERT INTO audit_events(eventId, workspaceId, actionId, capability, risk, eventType, summary, metadataJson, createdAtEpochMs)
                    VALUES (lower(hex(randomblob(16))), NEW.workspaceId, NEW.actionId, NEW.capability, NEW.risk, 'APPROVAL_CREATED', NEW.summary, '{"status":"'||NEW.status||'"}', strftime('%s','now') * 1000);
                END
            """.trimIndent())
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS devforge_approval_status_audit
                AFTER UPDATE OF status ON approval_actions
                WHEN OLD.status != NEW.status
                BEGIN
                    INSERT INTO audit_events(eventId, workspaceId, actionId, capability, risk, eventType, summary, metadataJson, createdAtEpochMs)
                    VALUES (lower(hex(randomblob(16))), NEW.workspaceId, NEW.actionId, NEW.capability, NEW.risk, 'APPROVAL_STATUS', NEW.summary, '{"from":"'||OLD.status||'","to":"'||NEW.status||'"}', strftime('%s','now') * 1000);
                END
            """.trimIndent())
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS devforge_grant_insert_audit
                AFTER INSERT ON capability_grants
                WHEN NEW.enabled = 1
                BEGIN
                    INSERT INTO audit_events(eventId, workspaceId, capability, risk, eventType, summary, metadataJson, createdAtEpochMs)
                    VALUES (lower(hex(randomblob(16))), NEW.workspaceId, NEW.capability, NEW.maxRisk, 'GRANT_CREATED', 'Persistent grant enabled for '||NEW.capability, '{"expiresAtEpochMs":'||COALESCE(CAST(NEW.expiresAtEpochMs AS TEXT),'null')||'}', strftime('%s','now') * 1000);
                END
            """.trimIndent())
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS devforge_grant_status_audit
                AFTER UPDATE OF enabled ON capability_grants
                WHEN OLD.enabled != NEW.enabled
                BEGIN
                    INSERT INTO audit_events(eventId, workspaceId, capability, risk, eventType, summary, metadataJson, createdAtEpochMs)
                    VALUES (lower(hex(randomblob(16))), NEW.workspaceId, NEW.capability, NEW.maxRisk, CASE WHEN NEW.enabled = 1 THEN 'GRANT_CREATED' ELSE 'GRANT_REVOKED' END, CASE WHEN NEW.enabled = 1 THEN 'Persistent grant enabled for '||NEW.capability ELSE 'Persistent grant revoked for '||NEW.capability END, NULL, strftime('%s','now') * 1000);
                END
            """.trimIndent())
        }

        private fun hydrateGrantRegistry(db: SupportSQLiteDatabase) {
            CapabilityGrantRegistry.clear()
            val now = System.currentTimeMillis()
            db.query("SELECT workspaceId, capability, maxRisk, expiresAtEpochMs FROM capability_grants WHERE enabled = 1 AND (expiresAtEpochMs IS NULL OR expiresAtEpochMs > $now)").use { cursor ->
                val workspaceIndex = cursor.getColumnIndexOrThrow("workspaceId")
                val capabilityIndex = cursor.getColumnIndexOrThrow("capability")
                val maxRiskIndex = cursor.getColumnIndexOrThrow("maxRisk")
                val expiresIndex = cursor.getColumnIndexOrThrow("expiresAtEpochMs")
                while (cursor.moveToNext()) {
                    val workspaceId = cursor.getString(workspaceIndex)
                    val capability = runCatching { Capability.valueOf(cursor.getString(capabilityIndex)) }.getOrNull() ?: continue
                    val maxRisk = runCatching { RiskLevel.valueOf(cursor.getString(maxRiskIndex)) }.getOrNull() ?: continue
                    val expiresAt = if (cursor.isNull(expiresIndex)) null else cursor.getLong(expiresIndex)
                    CapabilityGrantRegistry.put(workspaceId, capability, maxRisk, expiresAt)
                }
            }
        }
    }
}
