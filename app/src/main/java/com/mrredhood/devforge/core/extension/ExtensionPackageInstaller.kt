package com.mrredhood.devforge.core.extension

import android.content.Context
import android.net.Uri
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ExtensionInstallResult(
    val extension: InstalledExtension,
    val message: String,
)

class ExtensionPackageInstaller(
    private val context: Context,
    private val store: ExtensionPackageStore = ExtensionPackageStore(context),
) {
    suspend fun install(uri: Uri): Result<ExtensionInstallResult> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            installStream(
                open = { resolver.openInputStream(uri) },
                remote = null,
            )
        }
    }

    suspend fun installRemote(extension: MarketplaceExtension): Result<ExtensionInstallResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(extension.installable) { extension.priceText?.let { "This extension is paid ($it) and cannot be installed without a marketplace purchase." } ?: "This extension cannot be installed from the live catalog." }
            require(extension.source == ExtensionSource.ACODE || extension.source == ExtensionSource.VSCODE) {
                "Unsupported marketplace source."
            }
            val downloadUrl = extension.downloadUrl.trim()
            require(downloadUrl.startsWith("https://")) { "Extension download URL must use HTTPS." }
            val host = URL(downloadUrl).host.lowercase()
            require(
                host == "acode.app" ||
                    host == "marketplace.visualstudio.com",
            ) { "Extension download host is not an approved marketplace host." }

            val tempRoot = context.noBackupFilesDir.resolve("extensions").apply { mkdirs() }
            val tempZip = tempRoot.resolve(".download-" + UUID.randomUUID() + ".zip")
            try {
                download(downloadUrl, tempZip)
                installStream(
                    open = { tempZip.inputStream().buffered() },
                    remote = extension,
                )
            } finally {
                tempZip.delete()
            }
        }
    }

    private fun installStream(
        open: () -> java.io.InputStream?,
        remote: MarketplaceExtension?,
    ): ExtensionInstallResult {
        val root = context.noBackupFilesDir.resolve("extensions")
        root.mkdirs()
        val staging = root.resolve(".staging-" + UUID.randomUUID()).apply { mkdirs() }
        return try {
            open()?.use { input ->
                extract(input, staging)
            } ?: error("Unable to read the selected extension package.")

            val analyzed = ExtensionPackageAnalyzer.analyze(staging).getOrThrow()
            val baseManifest = analyzed.manifest
            require(baseManifest.compatibility != ExtensionCompatibility.UNSUPPORTED) {
                baseManifest.unsupportedReason ?: "DevForge cannot safely execute this package."
            }
            if (remote != null) {
                require(baseManifest.source == remote.source) {
                    "Marketplace package source mismatch."
                }
            }

            val manifest = baseManifest.copy(
                downloadUrl = remote?.downloadUrl,
                sourcePageUrl = remote?.sourcePageUrl,
            )
            val finalRoot = root.resolve(safeId(manifest.id))
            finalRoot.deleteRecursively()
            val packageRoot = analyzed.packageRoot.canonicalFile
            val stagingCanonical = staging.canonicalFile
            require(
                packageRoot.path == stagingCanonical.path ||
                    packageRoot.path.startsWith(stagingCanonical.path + File.separator),
            ) { "Extension package root escapes staging directory." }

            if (packageRoot.path == stagingCanonical.path) {
                require(staging.renameTo(finalRoot)) { "Unable to install extension files." }
            } else {
                require(packageRoot.renameTo(finalRoot)) { "Unable to install extension payload." }
                staging.deleteRecursively()
            }

            val installed = InstalledExtension(
                manifest = manifest.copy(rootPath = finalRoot.absolutePath),
                enabled = true,
            )
            store.put(installed)
            ExtensionIconThemeStore(context).index(installed)
            if (manifest.kind == ExtensionPackageKind.ICON_THEME) store.setActiveIconTheme(manifest.id)

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

    private fun download(url: String, destination: File) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 45_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "DevForge/0.1")
            setRequestProperty("Accept", "application/octet-stream,*/*")
        }
        try {
            val code = connection.responseCode
            require(code in 200..299) { "Extension download failed (HTTP $code)." }
            val length = connection.contentLengthLong
            require(length <= MAX_PACKAGE_BYTES || length < 0) { "Extension package is too large." }
            connection.inputStream.buffered().use { input ->
                destination.outputStream().buffered().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= MAX_PACKAGE_BYTES) { "Extension package is too large." }
                        output.write(buffer, 0, read)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun extract(input: java.io.InputStream, destination: File) {
        var files = 0
        var totalBytes = 0L
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
    }

    private fun safeId(id: String): String =
        id.replace(Regex("[^A-Za-z0-9._-]"), "_").take(100).ifBlank { "extension" }

    companion object {
        private const val MAX_FILES = 2_000
        private const val MAX_FILE_BYTES = 8L * 1024L * 1024L
        private const val MAX_PACKAGE_BYTES = 64L * 1024L * 1024L
    }
}
