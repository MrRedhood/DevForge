package com.mrredhood.devforge.core.extension.api

import org.json.JSONArray
import org.json.JSONObject

object DevForgeManifestValidator {
    private val ID = Regex("^[a-z][a-z0-9._-]{1,79}$")
    private val API = Regex("^(\\d+)\\.(\\d+)$")

    fun parse(raw: String): DevForgeManifestValidation = runCatching {
        val json = JSONObject(raw)
        val publisher = json.optJSONObject("publisher") ?: JSONObject()
        val devforge = json.optJSONObject("devforge") ?: JSONObject()
        val entry = json.optJSONObject("entry") ?: JSONObject()
        val contributes = json.optJSONObject("contributes") ?: JSONObject()

        val permissions = buildSet {
            val array = json.optJSONArray("permissions") ?: JSONArray()
            for (index in 0 until array.length()) {
                val wire = array.optString(index).trim()
                add(DevForgePermission.fromWireName(wire) ?: error("Unknown permission '$wire'."))
            }
        }

        DevForgeManifest(
            manifestVersion = json.optInt("manifestVersion", -1),
            id = json.optString("id").trim(),
            name = json.optString("name").trim(),
            version = json.optString("version").trim(),
            publisherId = publisher.optString("id").trim(),
            publisherName = publisher.optString("name").trim(),
            description = json.optString("description").trim(),
            type = DevForgePackageType.fromWireName(json.optString("type").trim())
                ?: error("Unknown package type."),
            apiVersion = devforge.optString("api").trim(),
            minimumDevForgeVersion = devforge.optString("minimumVersion").trim(),
            entryRuntime = runCatching { DevForgeRuntimeType.valueOf(entry.optString("runtime").trim().uppercase()) }
                .getOrElse { error("Unknown or missing entry runtime.") },
            entryPoint = entry.optString("main").trim(),
            permissions = permissions,
            activationEvents = json.optJSONObject("activation")?.optJSONArray("events").toStrings(),
            contributions = DevForgeContributions(
                commands = parseCommands(contributes.optJSONArray("commands")),
                panels = parsePanels(contributes.optJSONArray("panels")),
                menus = parseMenus(contributes.optJSONArray("menus")),
                languages = parseLanguages(contributes.optJSONArray("languages")),
            ),
        )
    }.fold(
        onSuccess = ::validate,
        onFailure = { error -> DevForgeManifestValidation.Invalid(listOf(error.message ?: "Invalid DevForge manifest.")) },
    )

    fun validate(manifest: DevForgeManifest): DevForgeManifestValidation {
        val errors = mutableListOf<String>()

        if (manifest.manifestVersion != 1) errors += "manifestVersion must be 1."
        if (!ID.matches(manifest.id)) errors += "Package id is invalid."
        if (manifest.name.isBlank() || manifest.name.length > 120) errors += "Package name is invalid."
        if (manifest.description.length > 4_000) errors += "Description is too long."
        if (DevForgeSemVer.parse(manifest.version) == null) errors += "Package version must use SemVer MAJOR.MINOR.PATCH."
        if (manifest.publisherId.isBlank() || !ID.matches(manifest.publisherId)) errors += "Publisher id is invalid."
        if (manifest.publisherName.isBlank() || manifest.publisherName.length > 120) errors += "Publisher name is invalid."
        if (!API.matches(manifest.apiVersion)) errors += "DevForge API version must be MAJOR.MINOR."
        val apiMajor = manifest.apiVersion.substringBefore('.').toIntOrNull()
        val supportedMajor = DEVFORGE_API_VERSION.substringBefore('.').toIntOrNull()
        if (apiMajor != supportedMajor) errors += "Unsupported DevForge API major version " + manifest.apiVersion + "."
        if (DevForgeSemVer.parse(manifest.minimumDevForgeVersion) == null) {
            errors += "minimumVersion must use SemVer MAJOR.MINOR.PATCH."
        }
        if (manifest.entryPoint.isBlank() || manifest.entryPoint.length > 240) errors += "Entry point is invalid."
        if (manifest.entryPoint.startsWith("/") || manifest.entryPoint.split('/').contains("..")) {
            errors += "Entry point must stay inside the package."
        }
        if (manifest.activationEvents.size > 64) errors += "Too many activation events."
        if (manifest.permissions.size > DevForgePermissionSet.MAX_PERMISSIONS) errors += "Too many requested permissions."
        errors += DevForgePermissionSet(manifest.permissions).validate()

        if (manifest.contributions.commands.size > 100) errors += "Too many command contributions."
        if (manifest.contributions.panels.size > 32) errors += "Too many panel contributions."
        if (manifest.contributions.menus.size > 100) errors += "Too many menu contributions."
        if (manifest.contributions.languages.size > 32) errors += "Too many language contributions."

        if (manifest.contributions.commands.isNotEmpty() && DevForgePermission.COMMANDS_REGISTER !in manifest.permissions) {
            errors += "commands contributions require commands.register."
        }
        if (manifest.contributions.panels.isNotEmpty() && DevForgePermission.UI_CONTRIBUTE !in manifest.permissions) {
            errors += "panel contributions require ui.contribute."
        }
        if (manifest.contributions.languages.isNotEmpty() && DevForgePermission.LANGUAGE_REGISTER !in manifest.permissions) {
            errors += "language contributions require language.register."
        }

        return if (errors.isEmpty()) DevForgeManifestValidation.Valid(manifest)
        else DevForgeManifestValidation.Invalid(errors.distinct())
    }

    private fun parseCommands(array: JSONArray?): List<DevForgeCommandContribution> =
        if (array == null) emptyList() else buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                val title = item.optString("title").trim()
                if (id.isNotBlank() && title.isNotBlank()) add(DevForgeCommandContribution(id, title))
            }
        }

    private fun parsePanels(array: JSONArray?): List<DevForgePanelContribution> =
        if (array == null) emptyList() else buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                val title = item.optString("title").trim()
                val location = item.optString("location").trim()
                if (id.isNotBlank() && title.isNotBlank() && location.isNotBlank()) {
                    add(DevForgePanelContribution(id, title, location))
                }
            }
        }

    private fun parseMenus(array: JSONArray?): List<DevForgeMenuContribution> =
        if (array == null) emptyList() else buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                val title = item.optString("title").trim()
                val commandId = item.optString("commandId").trim()
                if (id.isNotBlank() && title.isNotBlank() && commandId.isNotBlank()) {
                    add(DevForgeMenuContribution(id, title, commandId, item.optString("group").ifBlank { null }))
                }
            }
        }

    private fun parseLanguages(array: JSONArray?): List<DevForgeLanguageContribution> =
        if (array == null) emptyList() else buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                val label = item.optString("label").trim()
                val extensions = item.optJSONArray("extensions").toStrings()
                if (id.isNotBlank() && label.isNotBlank()) add(DevForgeLanguageContribution(id, label, extensions))
            }
        }

    private fun JSONArray?.toStrings(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }
}
