package com.mrredhood.devforge.core.extension

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class InstalledExtension(
    val manifest: ExtensionManifest,
    val enabled: Boolean = true,
    val installedAtEpochMs: Long = System.currentTimeMillis(),
)

class ExtensionPackageStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("devforge_extensions", Context.MODE_PRIVATE)

    fun list(): List<InstalledExtension> {
        val array = runCatching { JSONArray(preferences.getString(KEY_INSTALLED, "[]") ?: "[]") }.getOrElse { JSONArray() }
        return buildList {
            for (index in 0 until array.length()) {
                runCatching { array.getJSONObject(index).toInstalledExtension()?.let(::add) }
            }
        }.sortedWith(compareBy({ it.manifest.source.name }, { it.manifest.name.lowercase() }))
    }

    fun get(id: String): InstalledExtension? = list().firstOrNull { it.manifest.id == id }

    @Synchronized fun put(extension: InstalledExtension) {
        save(list().filterNot { it.manifest.id == extension.manifest.id } + extension)
    }

    @Synchronized fun remove(id: String) {
        save(list().filterNot { it.manifest.id == id })
        if (activeIconThemeId() == id) setActiveIconTheme(null)
    }

    @Synchronized fun setEnabled(id: String, enabled: Boolean) {
        save(list().map { if (it.manifest.id == id) it.copy(enabled = enabled) else it })
    }

    fun activeIconThemeId(): String? = preferences.getString(KEY_ACTIVE_ICON_THEME, null)

    fun setActiveIconTheme(id: String?) {
        preferences.edit().apply {
            if (id.isNullOrBlank()) remove(KEY_ACTIVE_ICON_THEME) else putString(KEY_ACTIVE_ICON_THEME, id)
        }.apply()
    }

    private fun save(items: List<InstalledExtension>) {
        val array = JSONArray()
        items.take(MAX_INSTALLED).forEach { array.put(it.toJson()) }
        preferences.edit().putString(KEY_INSTALLED, array.toString()).apply()
    }

    private fun InstalledExtension.toJson() = JSONObject()
        .put("manifest", manifest.toJson())
        .put("enabled", enabled)
        .put("installedAt", installedAtEpochMs)

    private fun ExtensionManifest.toJson(): JSONObject {
        val c = JSONObject()
        c.put("commands", JSONArray(contributions.commands))
        c.put("grammars", JSONArray(contributions.grammars))
        c.put("snippets", JSONArray(contributions.snippets))
        c.put("themes", JSONArray(contributions.themes))
        c.put("iconThemes", JSONArray(contributions.iconThemes.map {
            JSONObject().put("id", it.id).put("label", it.label).put("path", it.path)
        }))
        c.put("languages", JSONArray(contributions.languages.map {
            JSONObject().put("id", it.id).put("label", it.label)
                .put("extensions", JSONArray(it.extensions)).put("aliases", JSONArray(it.aliases))
        }))
        return JSONObject()
            .put("id", id).put("name", name).put("version", version)
            .put("capabilities", JSONArray(capabilities.map { it.name }))
            .put("entryPoint", entryPoint).put("source", source.name).put("kind", kind.name)
            .put("compatibility", compatibility.name).put("description", description)
            .put("rootPath", rootPath).put("nativeLanguages", JSONArray(nativeLanguages))
            .put("unsupportedReason", unsupportedReason).put("contributions", c)
    }

    private fun JSONObject.toManifest(): ExtensionManifest {
        val caps = buildSet {
            val array = optJSONArray("capabilities") ?: JSONArray()
            for (i in 0 until array.length()) runCatching { add(ExtensionCapability.valueOf(array.getString(i))) }
        }
        val c = optJSONObject("contributions") ?: JSONObject()
        val languages = buildList {
            val array = c.optJSONArray("languages") ?: JSONArray()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(ExtensionLanguageContribution(
                    item.optString("id"),
                    item.optString("label").ifBlank { item.optString("id") },
                    item.optJSONArray("extensions").strings(),
                    item.optJSONArray("aliases").strings(),
                ))
            }
        }
        val icons = buildList {
            val array = c.optJSONArray("iconThemes") ?: JSONArray()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(ExtensionIconThemeContribution(item.optString("id"), item.optString("label"), item.optString("path")))
            }
        }
        return ExtensionManifest(
            id = optString("id"), name = optString("name"), version = optString("version"),
            capabilities = caps, entryPoint = optString("entryPoint"),
            source = runCatching { ExtensionSource.valueOf(optString("source")) }.getOrDefault(ExtensionSource.DEVFORGE),
            kind = runCatching { ExtensionPackageKind.valueOf(optString("kind")) }.getOrDefault(ExtensionPackageKind.RUNTIME),
            compatibility = runCatching { ExtensionCompatibility.valueOf(optString("compatibility")) }.getOrDefault(ExtensionCompatibility.UNSUPPORTED),
            description = optString("description"), rootPath = optString("rootPath"),
            nativeLanguages = optJSONArray("nativeLanguages").strings(),
            unsupportedReason = optString("unsupportedReason").ifBlank { null },
            contributions = ExtensionContributions(
                languages = languages, iconThemes = icons,
                commands = c.optJSONArray("commands").strings(),
                grammars = c.optJSONArray("grammars").strings(),
                snippets = c.optJSONArray("snippets").strings(),
                themes = c.optJSONArray("themes").strings(),
            ),
        )
    }

    private fun JSONObject.toInstalledExtension(): InstalledExtension =
        InstalledExtension(getJSONObject("manifest").toManifest(), optBoolean("enabled", true), optLong("installedAt"))

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else buildList {
            for (i in 0 until length()) optString(i).takeIf { it.isNotBlank() }?.let(::add)
        }

    companion object {
        private const val KEY_INSTALLED = "installed"
        private const val KEY_ACTIVE_ICON_THEME = "activeIconTheme"
        private const val MAX_INSTALLED = 200
    }
}