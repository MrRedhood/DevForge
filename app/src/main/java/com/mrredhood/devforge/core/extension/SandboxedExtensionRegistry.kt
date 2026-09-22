package com.mrredhood.devforge.core.extension

import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

class SandboxedExtensionRegistry {
    private val manifests = ConcurrentHashMap<String, ExtensionManifest>()

    fun register(manifest: ExtensionManifest): ExtensionValidation {
        val validation = validate(manifest)
        if (validation is ExtensionValidation.Valid) manifests[manifest.id] = manifest
        return validation
    }

    fun get(id: String): ExtensionManifest? = manifests[id]

    fun list(): List<ExtensionManifest> = manifests.values.sortedBy { it.id }

    fun validate(manifest: ExtensionManifest): ExtensionValidation {
        if (!ID_PATTERN.matches(manifest.id)) return ExtensionValidation.Invalid("Extension ID is invalid.")
        if (manifest.name.isBlank() || manifest.name.length > 120) return ExtensionValidation.Invalid("Extension name is invalid.")
        if (manifest.version.isBlank() || manifest.version.length > 40) return ExtensionValidation.Invalid("Extension version is invalid.")
        if (manifest.entryPoint.isBlank() || manifest.entryPoint.length > 240) return ExtensionValidation.Invalid("Extension entry point is invalid.")
        if (manifest.capabilities.size > MAX_CAPABILITIES) return ExtensionValidation.Invalid("Extension requests too many capabilities.")
        return ExtensionValidation.Valid(manifest)
    }

    fun parseManifest(raw: String): ExtensionValidation = runCatching {
        val json = JSONObject(raw)
        val capabilities = buildSet {
            val array = json.optJSONArray("capabilities") ?: JSONArray()
            for (index in 0 until array.length()) {
                val name = array.optString(index).trim()
                add(ExtensionCapability.valueOf(name))
            }
        }
        ExtensionManifest(
            id = json.optString("id").trim(),
            name = json.optString("name").trim(),
            version = json.optString("version").trim(),
            capabilities = capabilities,
            entryPoint = json.optString("entryPoint").trim(),
        )
    }.fold(
        onSuccess = ::validate,
        onFailure = { ExtensionValidation.Invalid(it.message ?: "Invalid extension manifest.") },
    )

    companion object {
        private const val MAX_CAPABILITIES = 5
        private val ID_PATTERN = Regex("^[a-zA-Z0-9._-]{1,80}$")
    }
}
