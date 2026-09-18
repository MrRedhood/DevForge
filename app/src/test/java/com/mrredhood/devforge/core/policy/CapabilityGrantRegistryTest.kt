package com.mrredhood.devforge.core.policy

import com.mrredhood.devforge.core.security.WorkspacePathScope
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityGrantRegistryTest {
    @After
    fun clearRegistry() {
        CapabilityGrantRegistry.clear()
    }

    @Test
    fun pathScopedGrantAllowsOnlyContainedActions() {
        CapabilityGrantRegistry.put(
            workspaceId = "workspace",
            capability = Capability.EDIT_FILES,
            maxRisk = RiskLevel.R2,
            expiresAtEpochMs = null,
            pathScope = WorkspacePathScope(listOf("src")),
        )

        assertTrue(
            CapabilityGrantRegistry.allows(
                "workspace",
                Capability.EDIT_FILES,
                RiskLevel.R2,
                WorkspacePathScope(listOf("src/main")),
            ),
        )
        assertFalse(
            CapabilityGrantRegistry.allows(
                "workspace",
                Capability.EDIT_FILES,
                RiskLevel.R2,
                WorkspacePathScope(listOf("docs")),
            ),
        )
        assertFalse(
            CapabilityGrantRegistry.allows(
                "workspace",
                Capability.EDIT_FILES,
                RiskLevel.R2,
            ),
        )
    }

    @Test
    fun wholeWorkspaceGrantKeepsExistingBehavior() {
        CapabilityGrantRegistry.put(
            workspaceId = "workspace",
            capability = Capability.EDIT_FILES,
            maxRisk = RiskLevel.R2,
            expiresAtEpochMs = null,
        )

        assertTrue(
            CapabilityGrantRegistry.allows(
                "workspace",
                Capability.EDIT_FILES,
                RiskLevel.R2,
                WorkspacePathScope(listOf("src/main")),
            ),
        )
        assertTrue(
            CapabilityGrantRegistry.allows(
                "workspace",
                Capability.EDIT_FILES,
                RiskLevel.R2,
                WorkspacePathScope(listOf("docs")),
            ),
        )
    }

    @Test
    fun pathScopeAndRiskCeilingAreBothEnforcedByDefaultPolicy() {
        CapabilityGrantRegistry.put(
            workspaceId = "workspace",
            capability = Capability.EDIT_FILES,
            maxRisk = RiskLevel.R1,
            expiresAtEpochMs = null,
            pathScope = WorkspacePathScope(listOf("src")),
        )

        val inScope = ActionRequest(
            actionId = "action-in-scope",
            capability = Capability.EDIT_FILES,
            risk = RiskLevel.R2,
            workspaceId = "workspace",
            summary = "Edit source file",
            parametersHash = "hash",
            pathScope = WorkspacePathScope(listOf("src/main")),
        )
        val outOfScope = inScope.copy(
            actionId = "action-out-of-scope",
            pathScope = WorkspacePathScope(listOf("docs")),
        )

        assertTrue(DefaultPolicy.requiresApproval(inScope, PermissionMode.SOME))
        assertTrue(DefaultPolicy.requiresApproval(outOfScope, PermissionMode.SOME))
    }

    @Test
    fun neverPermissionModeCannotBeBypassedByPersistentGrant() {
        CapabilityGrantRegistry.put(
            workspaceId = "workspace",
            capability = Capability.EDIT_FILES,
            maxRisk = RiskLevel.R5,
            expiresAtEpochMs = null,
        )

        val action = ActionRequest(
            actionId = "action",
            capability = Capability.EDIT_FILES,
            risk = RiskLevel.R2,
            workspaceId = "workspace",
            summary = "Edit source file",
            parametersHash = "hash",
        )

        assertTrue(DefaultPolicy.requiresApproval(action, PermissionMode.NEVER))
    }

    @Test
    fun matchingRiskCeilingRemovesApprovalWithinScope() {
        CapabilityGrantRegistry.put(
            workspaceId = "workspace",
            capability = Capability.EDIT_FILES,
            maxRisk = RiskLevel.R2,
            expiresAtEpochMs = null,
            pathScope = WorkspacePathScope(listOf("src")),
        )

        val action = ActionRequest(
            actionId = "action",
            capability = Capability.EDIT_FILES,
            risk = RiskLevel.R2,
            workspaceId = "workspace",
            summary = "Edit source file",
            parametersHash = "hash",
            pathScope = WorkspacePathScope(listOf("src/main")),
        )

        assertFalse(DefaultPolicy.requiresApproval(action, PermissionMode.SOME))
        assertTrue(
            DefaultPolicy.requiresApproval(
                action.copy(pathScope = WorkspacePathScope(listOf("docs"))),
                PermissionMode.SOME,
            ),
        )
    }
}
