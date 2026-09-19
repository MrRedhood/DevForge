package com.mrredhood.devforge.core.agent

import android.content.Context
import com.mrredhood.devforge.core.policy.PermissionMode
import com.mrredhood.devforge.core.storage.ApprovalRepository
import com.mrredhood.devforge.core.storage.DevForgeDatabase
import com.mrredhood.devforge.core.storage.DurableStateRepository

/** Composition root for the provider-neutral agent runtime. */
object AgentRuntime {
    fun create(
        context: Context,
        // Agent file operations are already bounded by workspace scope, tool access,
        // mutation leases and precondition checks. Run them autonomously so an agent can
        // actually complete coding tasks; PermissionMode.NEVER still blocks execution.
        permissionMode: PermissionMode = PermissionMode.AUTONOMOUS,
    ): AgentTaskEngine {
        val appContext = context.applicationContext
        val database = DevForgeDatabase.get(appContext)
        val durableState = DurableStateRepository(database)
        val coordination = AgentCoordinationService(database)
        val registry = AgentToolRegistry()
        WorkspaceAgentToolProvider(
            appContext.contentResolver,
            database.workspaceDao(),
            com.mrredhood.devforge.core.git.GitRemoteTransportService(appContext),
        ).registerAll(registry)
        AgentCoordinationToolProvider(coordination).registerAll(registry)
        val approvals = ApprovalRepository(database.approvalDao())
        val gateway = AgentToolGateway(
            registry = registry,
            approvals = approvals,
            durableState = durableState,
            coordination = coordination,
            permissionMode = permissionMode,
        )
        return AgentTaskEngine(
            durableState = durableState,
            gateway = gateway,
            coordination = coordination,
            approvals = approvals,
        )
    }
}
