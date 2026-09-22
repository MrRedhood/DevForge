package com.mrredhood.devforge.core.extension

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class ResolvedIconAsset(
    val path: String,
    val label: String,
)

data class StoredIconTheme(
    val extensionId: String,
    val themeId: String,
    val label: String,
    val rootPath: String,
    val fileNames: Map<String, String>,
    val fileExtensions: Map<String, String>,
    val folderNames: Map<String, String>,
    val folderNamesExpanded: Map<String, String>,
    val file: String?,
    val folder: String?,
    val folderExpanded: String?,
)

class ExtensionIconThemeStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("devforge_icon_themes", Context.MODE_PRIVATE)

    fun list(): List<StoredIconTheme> {
        val array = runCatching { JSONArray(preferences.getString(KEY_THEMES, "[]") ?: "[]") }.getOrElse { JSONArray() }
        return buildList {
            for (i in 0 until array.length()) runCatching { add(array.getJSONObject(i).toTheme()) }
        }
    }

    fun index(extension: InstalledExtension) {
        val root = File(extension.manifest.rootPath)
        val candidates = extension.manifest.contributions.iconThemes
        val indexed = candidates.mapNotNull { contribution ->
            val file = File(root, contribution.path)
            if (!file.isFile) return@mapNotNull null
            parseVsCodeIconTheme(extension.manifest.id, contribution, root, file)
        }.toMutableList()

        val acodeFiles = listOf(File(root, "file_icons.json"), File(root, "folder_icons.json"))
        if (acodeFiles.any { it.isFile }) {
            indexed += parseAcodeIconPack(extension.manifest.id, extension.manifest.name, root)
        }
        if (indexed.isEmpty()) return
        val merged = list().filterNot { it.extensionId == extension.manifest.id } + indexed
        save(merged)
    }

    fun remove(extensionId: String) {
        save(list().filterNot { it.extensionId == extensionId })
    }

    fun activeTheme(store: ExtensionPackageStore): StoredIconTheme? {
        val id = store.activeIconThemeId() ?: return null
        return list().firstOrNull { it.extensionId == id }
    }

    fun resolve(name: String, isFolder: Boolean, expanded: Boolean, store: ExtensionPackageStore): ResolvedIconAsset? {
        val theme = activeTheme(store) ?: return null
        val base = name.substringAfterLast('/').trim()
        val lower = base.lowercase()
        val resultKey = when {
            isFolder && expanded -> theme.folderNamesExpanded[base] ?: theme.folderExpanded
            isFolder -> theme.folderNames[base] ?: theme.folder
            theme.fileNames[base] != null -> theme.fileNames[base]
            else -> theme.fileExtensions[lower.substringAfterLast('.', "")] ?: theme.file
        } ?: return null
        return ResolvedIconAsset(resultKey, theme.label)
    }

    private fun parseVsCodeIconTheme(
        extensionId: String,
        contribution: ExtensionIconThemeContribution,
        root: File,
        file: File,
    ): StoredIconTheme {
        val json = JSONObject(file.readText())
        val definitions = json.optJSONObject("iconDefinitions") ?: JSONObject()
        val resolve = { value: String? ->
            value?.takeIf { it.isNotBlank() }?.let { definitions.optJSONObject(it)?.optString("iconPath")?.ifBlank { null } }
                ?.let { File(file.parentFile ?: root, it).canonicalPath }
        }
        val files = json.optJSONObject("fileNames").toMap().mapValues { resolve(it.value) ?: "" }.filterValues { it.isNotBlank() }
        val extensions = json.optJSONObject("fileExtensions").toMap().mapValues { resolve(it.value) ?: "" }.filterValues { it.isNotBlank() }
        val folders = json.optJSONObject("folderNames").toMap().mapValues { resolve(it.value) ?: "" }.filterValues { it.isNotBlank() }
        val foldersExpanded = json.optJSONObject("folderNamesExpanded").toMap().mapValues { resolve(it.value) ?: "" }.filterValues { it.isNotBlank() }
        return StoredIconTheme(
            extensionId = extensionId,
            themeId = contribution.id,
            label = contribution.label,
            rootPath = root.absolutePath,
            fileNames = files,
            fileExtensions = extensions,
            folderNames = folders,
            folderNamesExpanded = foldersExpanded,
            file = resolve(json.optString("file")),
            folder = resolve(json.optString("folder")),
            folderExpanded = resolve(json.optString("folderExpanded")),
        )
    }

    private fun parseAcodeIconPack(extensionId: String, label: String, root: File): StoredIconTheme {
        val fileJson = File(root, "file_icons.json").takeIf { it.isFile }?.let { JSONObject(it.readText()) }
        val folderJson = File(root, "folder_icons.json").takeIf { it.isFile }?.let { JSONObject(it.readText()) }
        val iconsDir = File(root, "icons")
        fun resolveAsset(value: String): String {
            val direct = File(root, value)
            if (direct.isFile) return direct.canonicalPath
            val normalized = value.removePrefix("./")
            val candidates = listOf(
                File(iconsDir, normalized),
                File(iconsDir, "$normalized.svg"),
                File(iconsDir, "$normalized.png"),
                File(iconsDir, "$normalized.webp"),
            )
            return candidates.firstOrNull { it.isFile }?.canonicalPath ?: ""
        }
        fun map(obj: JSONObject?, key: String, lowerCaseKeys: Boolean = false): Map<String, String> =
            obj?.optJSONObject(key)?.toMap()?.mapNotNull { (k, v) ->
                val asset = resolveAsset(v)
                if (asset.isBlank()) null else (if (lowerCaseKeys) k.lowercase() else k) to asset
            }?.toMap().orEmpty()
        val fileNames = map(fileJson, "fileNames")
        val extensions = map(fileJson, "fileExtensions", true)
        val folderNames = map(folderJson, "folderNames")
        val folderNamesExpanded = map(folderJson, "folderNamesExpanded")
        val defaultFile = listOf("file", "default").firstNotNullOfOrNull { key ->
            fileJson?.optString(key)?.takeIf { it.isNotBlank() }?.let(::resolveAsset)?.takeIf { it.isNotBlank() }
        }
        val defaultFolder = folderJson?.optString("folder")?.takeIf { it.isNotBlank() }?.let(::resolveAsset)?.takeIf { it.isNotBlank() }
        val defaultFolderExpanded = folderJson?.optString("folderExpanded")?.takeIf { it.isNotBlank() }?.let(::resolveAsset)?.takeIf { it.isNotBlank() }
        return StoredIconTheme(
            extensionId = extensionId,
            themeId = extensionId,
            label = label,
            rootPath = root.absolutePath,
            fileNames = fileNames,
            fileExtensions = extensions,
            folderNames = folderNames,
            folderNamesExpanded = folderNamesExpanded,
            file = defaultFile,
            folder = defaultFolder,
            folderExpanded = defaultFolderExpanded,
        )
    }

    private fun JSONObject.toMap(): Map<String, String> = keys().asSequence().mapNotNull { key ->
        optString(key).takeIf { it.isNotBlank() }?.let { key to it }
    }.toMap()

    private fun StoredIconTheme.toJson() = JSONObject()
        .put("extensionId", extensionId).put("themeId", themeId).put("label", label).put("rootPath", rootPath)
        .put("fileNames", JSONObject(fileNames)).put("fileExtensions", JSONObject(fileExtensions))
        .put("folderNames", JSONObject(folderNames)).put("folderNamesExpanded", JSONObject(folderNamesExpanded))
        .put("file", file).put("folder", folder).put("folderExpanded", folderExpanded)

    private fun JSONObject.toTheme() = StoredIconTheme(
        extensionId = optString("extensionId"), themeId = optString("themeId"),
        label = optString("label"), rootPath = optString("rootPath"),
        fileNames = optJSONObject("fileNames").toMap(),
        fileExtensions = optJSONObject("fileExtensions").toMap().mapKeys { it.key.lowercase() },
        folderNames = optJSONObject("folderNames").toMap(),
        folderNamesExpanded = optJSONObject("folderNamesExpanded").toMap(),
        file = optString("file").ifBlank { null },
        folder = optString("folder").ifBlank { null },
        folderExpanded = optString("folderExpanded").ifBlank { null },
    )

    private fun JSONArray?.asList(): List<JSONObject> = if (this == null) emptyList() else buildList {
        for (i in 0 until length()) optJSONObject(i)?.let(::add)
    }

    private fun save(items: List<StoredIconTheme>) {
        val array = JSONArray()
        items.forEach { array.put(it.toJson()) }
        preferences.edit().putString(KEY_THEMES, array.toString()).apply()
    }

    companion object {
        private const val KEY_THEMES = "themes"
    }
}

private fun JSONObject?.toMap(): Map<String, String> =
    if (this == null) emptyMap() else keys().asSequence().mapNotNull { key ->
        optString(key).takeIf { it.isNotBlank() }?.let { key to it }
    }.toMap()
