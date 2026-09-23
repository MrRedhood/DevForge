package com.mrredhood.devforge.core.extension.api

data class DevForgePermissionDecision(
    val granted: Boolean,
    val requiresApproval: Boolean,
    val reason: String? = null,
)

class DevForgeExtensionAccessController(
    private val permissions: DevForgePermissionSet,
) {
    fun check(permission: DevForgePermission): DevForgePermissionDecision {
        if (!permissions.contains(permission)) {
            return DevForgePermissionDecision(
                granted = false,
                requiresApproval = false,
                reason = "Package did not declare " + permission.wireName + ".",
            )
        }
        return DevForgePermissionDecision(
            granted = true,
            requiresApproval = permission.risk == DevForgePermissionRisk.HIGH,
            reason = if (permission.risk == DevForgePermissionRisk.HIGH) {
                "Operation is declared by the package but remains approval-sensitive."
            } else null,
        )
    }
}
