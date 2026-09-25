package com.mrredhood.devforge.core.agent

import android.content.Context
import com.mrredhood.devforge.core.policy.PermissionMode
import com.mrredhood.devforge.core.storage.ApprovalRepository
import com.mrredhood.devforge.core.storage.DevForgeDatabase
import com.mrredhood.devforge.core.storage.DurableStateRepository
import com.mrredhood.devforge.core.workspace.WorkspaceContextLedgerRepository

data class AgentToolRuntime(
    val registry: AgentToolRegistry,
    val gateway: AgentToolGateway,
    val workspaceTools: WorkspaceAgentToolProvider,
)

/** Composition root for the provider-neutral agent runtime. */
object AgentRuntime {
    fun createToolRuntime(
        context: Context,
        permissionMode: PermissionMode = PermissionMode.AUTONOMOUS,
    ): AgentToolRuntime {
        val appContext = context.applicationContext
        val database = DevForgeDatabase.get(appContext)
        val durableState = DurableStateRepository(database)
        val coordination = AgentCoordinationService(database)
        val registry = AgentToolRegistry()
        val workspaceTools = WorkspaceAgentToolProvider(
            appContext,
            appContext.contentResolver,
            database.workspaceDao(),
            com.mrredhood.devforge.core.git.GitRemoteTransportService(appContext),
        )
        workspaceTools.registerAll(registry)
        AgentWebToolProvider().registerAll(registry)
        AgentUtilityToolProvider().registerAll(registry)
        WorkspaceContextToolProvider(appContext).registerAll(registry)
        WorkspaceSemanticRetrievalToolProvider(appContext).registerAll(registry)
        AgentGitToolProvider(appContext).registerAll(registry)
        AgentTerminalToolProvider(appContext).registerAll(registry)
        AgentExtendedToolProvider(appContext).registerAll(registry)
        AgentAdditionalToolProvider(appContext).registerAll(registry)
        AgentCoordinationToolProvider(coordination).registerAll(registry)
        val approvals = ApprovalRepository(database.approvalDao())
        val gateway = AgentToolGateway(
            registry = registry,
            approvals = approvals,
            durableState = durableState,
            coordination = coordination,
            toolSettings = ToolSettingsStore(appContext),
            permissionMode = permissionMode,
            contextLedger = WorkspaceContextLedgerRepository(database),
        )
        return AgentToolRuntime(registry, gateway, workspaceTools)
    }

    fun create(
        context: Context,
        permissionMode: PermissionMode = PermissionMode.AUTONOMOUS,
    ): AgentTaskEngine {
        val appContext = context.applicationContext
        val database = DevForgeDatabase.get(appContext)
        val durableState = DurableStateRepository(database)
        val coordination = AgentCoordinationService(database)
        val runtime = createToolRuntime(appContext, permissionMode)
        val approvals = ApprovalRepository(database.approvalDao())
        return AgentTaskEngine(
            durableState = durableState,
            gateway = runtime.gateway,
            coordination = coordination,
            approvals = approvals,
        )
    }
}
