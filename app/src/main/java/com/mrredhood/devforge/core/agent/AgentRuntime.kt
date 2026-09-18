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
        permissionMode: PermissionMode = PermissionMode.SOME,
    ): AgentTaskEngine {
        val appContext = context.applicationContext
        val database = DevForgeDatabase.get(appContext)
        val durableState = DurableStateRepository(database)
        val coordination = AgentCoordinationService(database)
        val registry = AgentToolRegistry()
        WorkspaceAgentToolProvider(appContext.contentResolver, database.workspaceDao()).registerAll(registry)
        AgentCoordinationToolProvider(coordination).registerAll(registry)
        val gateway = AgentToolGateway(
            registry = registry,
            approvals = ApprovalRepository(database.approvalDao()),
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
