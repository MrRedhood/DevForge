package com.mrredhood.devforge.core.extension

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

data class ExtensionInstallResult(
    val extension: InstalledExtension,
    val message: String,
)

class ExtensionPackageInstaller(
    private val context: Context,
    private val store: ExtensionPackageStore = ExtensionPackageStore(context),
) {
    suspend fun install(uri: Uri): Result<ExtensionInstallResult> = runCatching {
        val root = context.noBackupFilesDir.resolve("extensions")
        root.mkdirs()
        val staging = root.resolve(".staging-" + UUID.randomUUID()).apply { mkdirs() }
        try {
            extract(uri, staging)
            val analyzed = ExtensionPackageAnalyzer.analyze(staging).getOrThrow()
            val manifest = analyzed.manifest
            require(manifest.compatibility != ExtensionCompatibility.UNSUPPORTED) {
                manifest.unsupportedReason ?: "DevForge cannot safely execute this package."
            }
            val finalRoot = root.resolve(safeId(manifest.id))
            finalRoot.deleteRecursively()
            require(staging.renameTo(finalRoot)) { "Unable to install extension files." }
            val installed = InstalledExtension(
                manifest = manifest.copy(rootPath = finalRoot.absolutePath),
                enabled = true,
            )
            store.put(installed)
            ExtensionIconThemeStore(context).index(installed)
            ExtensionInstallResult(
                installed,
                buildString {
                    append(manifest.name).append(" ").append(manifest.version).append(" installed. ")
                    when (manifest.compatibility) {
                        ExtensionCompatibility.NATIVE_LANGUAGE -> append("DevForge already supports ")
                            .append(manifest.nativeLanguages.joinToString())
                            .append(" natively; the package did not replace native language support.")
                        ExtensionCompatibility.DECLARATIVE_SUPPORTED -> append("Its supported declarative features are active now.")
                        ExtensionCompatibility.ACODE_RUNTIME_SUPPORTED -> append("The Acode runtime contract passed static safety checks and can be activated.")
                        ExtensionCompatibility.VSCODE_WEB_RUNTIME_SUPPORTED -> append("The VS Code Web runtime contract passed static safety checks and can be activated.")
                        ExtensionCompatibility.UNSUPPORTED -> append("Unsupported.")
                    }
                },
            )
        } finally {
            if (staging.exists()) staging.deleteRecursively()
        }
    }

    fun uninstall(id: String) {
        store.get(id)?.manifest?.rootPath?.let { File(it).deleteRecursively() }
        store.remove(id)
        ExtensionIconThemeStore(context).remove(id)
    }

    private fun extract(uri: Uri, destination: File) {
        val resolver = context.contentResolver
        var files = 0
        var totalBytes = 0L
        resolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    files++
                    require(files <= MAX_FILES) { "Extension package contains too many files." }
                    val name = entry.name.replace('\\', '/')
                    require(name.isNotBlank() && !name.startsWith("/") && !name.split('/').contains("..")) {
                        "Unsafe package path."
                    }
                    val target = File(destination, name).canonicalFile
                    require(target.path.startsWith(destination.canonicalPath + File.separator)) {
                        "Package escapes its installation directory."
                    }
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { output ->
                            val buffer = ByteArray(32 * 1024)
                            var size = 0L
                            while (true) {
                                val read = zip.read(buffer)
                                if (read < 0) break
                                size += read
                                totalBytes += read
                                require(size <= MAX_FILE_BYTES) { "Extension file is too large." }
                                require(totalBytes <= MAX_PACKAGE_BYTES) { "Extension package is too large." }
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
        } ?: error("Unable to read the selected extension package.")
    }

    private fun safeId(id: String): String =
        id.replace(Regex("[^A-Za-z0-9._-]"), "_").take(100).ifBlank { "extension" }

    companion object {
        private const val MAX_FILES = 2_000
        private const val MAX_FILE_BYTES = 8L * 1024L * 1024L
        private const val MAX_PACKAGE_BYTES = 64L * 1024L * 1024L
    }
}
