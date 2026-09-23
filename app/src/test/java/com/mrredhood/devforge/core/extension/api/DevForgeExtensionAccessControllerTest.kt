package com.mrredhood.devforge.core.extension.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevForgeExtensionAccessControllerTest {
    @Test
    fun undeclaredPermissionIsDenied() {
        val controller = DevForgeExtensionAccessController(
            DevForgePermissionSet(setOf(DevForgePermission.FILES_READ)),
        )
        val decision = controller.check(DevForgePermission.FILES_WRITE)
        assertFalse(decision.granted)
        assertFalse(decision.requiresApproval)
    }

    @Test
    fun highRiskPermissionRemainsApprovalSensitive() {
        val controller = DevForgeExtensionAccessController(
            DevForgePermissionSet(setOf(DevForgePermission.TERMINAL_EXECUTE)),
        )
        val decision = controller.check(DevForgePermission.TERMINAL_EXECUTE)
        assertTrue(decision.granted)
        assertTrue(decision.requiresApproval)
    }
}
