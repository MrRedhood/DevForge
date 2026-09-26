package com.mrredhood.devforge.core.agent

import android.app.ActivityManager
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.StatFs
import android.provider.DocumentsContract
import android.util.Base64
import com.mrredhood.devforge.core.policy.Capability
import com.mrredhood.devforge.core.policy.RiskLevel
import com.mrredhood.devforge.core.storage.ApprovalRepository
import com.mrredhood.devforge.core.storage.DevForgeDatabase
import com.mrredhood.devforge.core.storage.DurableStateRepository
import com.mrredhood.devforge.core.terminal.TerminalCapability
import com.mrredhood.devforge.core.terminal.TerminalCommandParser
import com.mrredhood.devforge.core.terminal.TerminalCommandPolicy
import com.mrredhood.devforge.core.workspace.WorkspaceFileTree
import java.io.ByteArrayOutputStream
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.regex.Pattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import org.json.JSONArray
import org.json.JSONObject

/** Additional bounded diagnostics/media tools exposed through the same provider-neutral AI gateway. */
class AgentAdditionalToolProvider(context: Context) {
    private val app = context.applicationContext
    private val resolver = app.contentResolver
    private val db = DevForgeDatabase.get(app)
    private val tree = WorkspaceFileTree(resolver)
    private val githubStore = GitHubWorkspaceStore(app)
    private val githubGateway = GitHubRepositoryGateway(CredentialSecurityStore(app))
    private val terminal = TerminalCapability(
        app,
        ApprovalRepository(db.approvalDao()),
        DurableStateRepository(db),
    )

    private val ids = listOf(
        AgentToolId.SCRAP_VIDEO, AgentToolId.SCRAP_AUDIO, AgentToolId.MEDIA_INFO, AgentToolId.MEDIA_DURATION,
        AgentToolId.MEDIA_MIME, AgentToolId.MEDIA_COVER_ART, AgentToolId.EXTRACT_VIDEO_FRAME,
        AgentToolId.EXTRACT_AUDIO_METADATA, AgentToolId.MEDIA_CHECKSUM, AgentToolId.LIST_MEDIA_STREAMS,
        AgentToolId.READ_BYTES, AgentToolId.GET_FILE_METADATA, AgentToolId.LIST_DIRECTORY_DETAILED,
        AgentToolId.SORT_DIRECTORY, AgentToolId.FIND_LARGEST_FILES, AgentToolId.FIND_DUPLICATES,
        AgentToolId.GET_TEXT_STATS, AgentToolId.DETECT_ENCODING, AgentToolId.DETECT_BINARY_FILE,
        AgentToolId.PREVIEW_FILE, AgentToolId.SEARCH_REGEX, AgentToolId.FIND_TODO, AgentToolId.FIND_FIXME,
        AgentToolId.FIND_COMMENTS, AgentToolId.COUNT_SYMBOLS, AgentToolId.FIND_TEST_FILES,
        AgentToolId.FIND_SOURCE_FILES, AgentToolId.GET_LANGUAGE_BREAKDOWN, AgentToolId.DETECT_FORMATTER,
        AgentToolId.DETECT_LINTER, AgentToolId.WHICH_COMMAND, AgentToolId.COMMAND_VERSION,
        AgentToolId.SHELL_INFO, AgentToolId.ENVIRONMENT_VARS, AgentToolId.SYSTEM_PROPERTIES, AgentToolId.CPU_INFO,
        AgentToolId.MEMORY_INFO, AgentToolId.STORAGE_CAPACITY, AgentToolId.NETWORK_INFO, AgentToolId.PROCESS_SNAPSHOT,
        AgentToolId.GRADLE_TASKS, AgentToolId.GRADLE_DEPENDENCIES, AgentToolId.PROJECT_MODULES,
        AgentToolId.BUILD_FILES, AgentToolId.PACKAGE_NAME, AgentToolId.GIT_HEAD, AgentToolId.GIT_BRANCHES,
        AgentToolId.GIT_TAGS, AgentToolId.GIT_REMOTE_INFO,
    )

    fun registerAll(registry: AgentToolRegistry): AgentToolRegistry {
        ids.forEach { registry.register(Tool(it)) }
        return registry
    }

    private inner class Tool(private val id: AgentToolId) : AgentTool {
        override val definition = AgentToolDefinition(
            id = id,
            description = description(id),
            capability = capability(id),
            risk = risk(id),
            sideEffecting = false,
        )

        override suspend fun execute(context: AgentToolContext, request: AgentToolRequest): AgentToolResult {
            currentCoroutineContext().ensureActive()
            val args = runCatching { JSONObject(request.argumentsJson) }.getOrDefault(JSONObject())
            return try {
                when (id) {
                    AgentToolId.SCRAP_VIDEO -> mediaInfo(context, args, true)
                    AgentToolId.SCRAP_AUDIO,
                    AgentToolId.EXTRACT_AUDIO_METADATA -> mediaInfo(context, args, false)
                    AgentToolId.MEDIA_INFO,
                    AgentToolId.LIST_MEDIA_STREAMS -> mediaInfo(context, args, null)
                    AgentToolId.MEDIA_DURATION -> mediaField(context, args, MediaMetadataRetriever.METADATA_KEY_DURATION, "durationMs")
                    AgentToolId.MEDIA_MIME -> {
                        val path = requiredPath(args)
                        AgentToolResult.Success(
                            "Read media MIME type.",
                            JSONObject().put("mimeType", resolver.getType(resolve(context.workspaceId, path)) ?: JSONObject.NULL).toString(),
                        )
                    }
                    AgentToolId.MEDIA_COVER_ART -> coverArt(context, args)
                    AgentToolId.EXTRACT_VIDEO_FRAME -> videoFrame(context, args)
                    AgentToolId.MEDIA_CHECKSUM -> checksum(context, args)
                    AgentToolId.READ_BYTES -> readBytes(context, args)
                    AgentToolId.GET_FILE_METADATA -> fileMetadata(context, args)
                    AgentToolId.LIST_DIRECTORY_DETAILED -> directory(context, args, "")
                    AgentToolId.SORT_DIRECTORY -> directory(context, args, args.optString("sortBy", "name"))
                    AgentToolId.FIND_LARGEST_FILES -> largestFiles(context, args)
                    AgentToolId.FIND_DUPLICATES -> duplicates(context, args)
                    AgentToolId.GET_TEXT_STATS -> textStats(context, args)
                    AgentToolId.DETECT_ENCODING -> encoding(context, args)
                    AgentToolId.DETECT_BINARY_FILE -> binary(context, args)
                    AgentToolId.PREVIEW_FILE -> preview(context, args)
                    AgentToolId.SEARCH_REGEX -> search(context, Pattern.compile(args.optString("pattern"), Pattern.MULTILINE))
                    AgentToolId.FIND_TODO -> search(context, Pattern.compile("\\bTODO\\b", Pattern.CASE_INSENSITIVE))
                    AgentToolId.FIND_FIXME -> search(context, Pattern.compile("\\bFIXME\\b", Pattern.CASE_INSENSITIVE))
                    AgentToolId.FIND_COMMENTS -> search(context, Pattern.compile("//|#|/\\*|\\*/"))
                    AgentToolId.COUNT_SYMBOLS -> countSymbols(context)
                    AgentToolId.FIND_TEST_FILES -> sourceFiles(context, true)
                    AgentToolId.FIND_SOURCE_FILES -> sourceFiles(context, false)
                    AgentToolId.GET_LANGUAGE_BREAKDOWN -> languageBreakdown(context)
                    AgentToolId.DETECT_FORMATTER -> detectConfigs(context, formatterMarkers)
                    AgentToolId.DETECT_LINTER -> detectConfigs(context, linterMarkers)
                    AgentToolId.WHICH_COMMAND -> whichCommand(args)
                    AgentToolId.COMMAND_VERSION -> commandVersion(context, args)
                    AgentToolId.SHELL_INFO -> AgentToolResult.Success(
                        "Read shell information.",
                        JSONObject().put("shell", "/system/bin/sh").put("kernel", System.getProperty("os.version") ?: "unknown").put("arch", System.getProperty("os.arch") ?: "unknown").toString(),
                    )
                    AgentToolId.ENVIRONMENT_VARS -> environmentVars()
                    AgentToolId.SYSTEM_PROPERTIES -> systemProperties()
                    AgentToolId.CPU_INFO -> cpuInfo()
                    AgentToolId.MEMORY_INFO -> memoryInfo()
                    AgentToolId.STORAGE_CAPACITY -> storageCapacity()
                    AgentToolId.NETWORK_INFO -> networkInfo()
                    AgentToolId.PROCESS_SNAPSHOT -> processSnapshot()
                    AgentToolId.GRADLE_TASKS -> gradle(context, "tasks --all")
                    AgentToolId.GRADLE_DEPENDENCIES -> gradle(context, "dependencies")
                    AgentToolId.PROJECT_MODULES -> projectModules(context)
                    AgentToolId.BUILD_FILES -> buildFiles(context)
                    AgentToolId.PACKAGE_NAME -> packageName(context)
                    AgentToolId.GIT_HEAD -> git(context, "git rev-parse --abbrev-ref HEAD && git rev-parse HEAD")
                    AgentToolId.GIT_BRANCHES -> git(context, "git branch --list")
                    AgentToolId.GIT_TAGS -> git(context, "git tag --list")
                    AgentToolId.GIT_REMOTE_INFO -> git(context, "git remote -v")
                    else -> AgentToolResult.Failure("Unsupported additional tool: " + id.wireName)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                AgentToolResult.Failure(error.message ?: id.wireName + " failed.")
            }
        }
    }

    private fun description(id: AgentToolId): String = id.wireName.replace('_', ' ')

    private fun capability(id: AgentToolId): Capability = when (id) {
        AgentToolId.COMMAND_VERSION, AgentToolId.GRADLE_TASKS, AgentToolId.GRADLE_DEPENDENCIES -> Capability.RUN_TERMINAL
        else -> Capability.READ_WORKSPACE
    }

    private fun risk(id: AgentToolId): RiskLevel = when (id) {
        AgentToolId.COMMAND_VERSION, AgentToolId.GRADLE_TASKS, AgentToolId.GRADLE_DEPENDENCIES -> RiskLevel.R2
        else -> RiskLevel.R0
    }

    private suspend fun mediaInfo(context: AgentToolContext, args: JSONObject, video: Boolean?): AgentToolResult {
        val path = requiredPath(args)
        val uri = resolve(context.workspaceId, path)
        val r = MediaMetadataRetriever()
        val afd = resolver.openAssetFileDescriptor(uri, "r") ?: return AgentToolResult.Failure("Unable to open media.")
        return try {
            r.setDataSource(afd.fileDescriptor)
            val out = JSONObject()
                .put("path", path)
                .put("mimeType", resolver.getType(uri) ?: JSONObject.NULL)
                .put("durationMs", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: JSONObject.NULL)
                .put("width", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: JSONObject.NULL)
                .put("height", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: JSONObject.NULL)
                .put("rotation", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: JSONObject.NULL)
                .put("bitrate", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: JSONObject.NULL)
                .put("title", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: JSONObject.NULL)
                .put("artist", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: JSONObject.NULL)
                .put("album", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: JSONObject.NULL)
                .put("genre", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE) ?: JSONObject.NULL)
            if (video != false && video != null) {
                r.embeddedPicture?.let { out.put("hasEmbeddedArtwork", true) }
            }
            if (video == true) {
                val frame = r.getFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    val buffer = ByteArrayOutputStream()
                    frame.compress(android.graphics.Bitmap.CompressFormat.JPEG, 50, buffer)
                    frame.recycle()
                    val bytes = buffer.toByteArray()
                    val clipped = bytes.copyOf(minOf(bytes.size, 96 * 1024))
                    out.put("keyFrameMime", "image/jpeg")
                    out.put("keyFrameBase64", Base64.encodeToString(clipped, Base64.NO_WRAP))
                    out.put("keyFrameTruncated", bytes.size > clipped.size)
                }
            }
            AgentToolResult.Success("Scraped bounded media metadata.", out.toString())
        } finally {
            afd.close()
            r.release()
        }
    }

    private suspend fun mediaField(context: AgentToolContext, args: JSONObject, key: Int, name: String): AgentToolResult {
        val uri = resolve(context.workspaceId, requiredPath(args))
        val r = MediaMetadataRetriever()
        val afd = resolver.openAssetFileDescriptor(uri, "r") ?: return AgentToolResult.Failure("Unable to open media.")
        return try {
            r.setDataSource(afd.fileDescriptor)
            AgentToolResult.Success("Read media field.", JSONObject().put(name, r.extractMetadata(key)?.toLongOrNull() ?: JSONObject.NULL).toString())
        } finally {
            afd.close()
            r.release()
        }
    }

    private suspend fun coverArt(context: AgentToolContext, args: JSONObject): AgentToolResult {
        val uri = resolve(context.workspaceId, requiredPath(args))
        val r = MediaMetadataRetriever()
        val afd = resolver.openAssetFileDescriptor(uri, "r") ?: return AgentToolResult.Failure("Unable to open media.")
        return try {
            r.setDataSource(afd.fileDescriptor)
            val bytes = r.embeddedPicture ?: return AgentToolResult.Success("No embedded artwork found.", "{}")
            val clipped = bytes.copyOf(minOf(bytes.size, 96 * 1024))
            AgentToolResult.Success("Extracted embedded artwork.", JSONObject().put("bytes", bytes.size).put("truncated", bytes.size > clipped.size).put("base64", Base64.encodeToString(clipped, Base64.NO_WRAP)).toString())
        } finally {
            afd.close()
            r.release()
        }
    }

    private suspend fun videoFrame(context: AgentToolContext, args: JSONObject): AgentToolResult {
        val uri = resolve(context.workspaceId, requiredPath(args))
        val r = MediaMetadataRetriever()
        val afd = resolver.openAssetFileDescriptor(uri, "r") ?: return AgentToolResult.Failure("Unable to open media.")
        return try {
            r.setDataSource(afd.fileDescriptor)
            val frame = r.getFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return AgentToolResult.Failure("No decodable video frame.")
            val buffer = ByteArrayOutputStream()
            frame.compress(android.graphics.Bitmap.CompressFormat.JPEG, 50, buffer)
            frame.recycle()
            val bytes = buffer.toByteArray()
            val clipped = bytes.copyOf(minOf(bytes.size, 96 * 1024))
            AgentToolResult.Success("Extracted key video frame.", JSONObject().put("mimeType", "image/jpeg").put("bytes", bytes.size).put("truncated", bytes.size > clipped.size).put("base64", Base64.encodeToString(clipped, Base64.NO_WRAP)).toString())
        } finally {
            afd.close()
            r.release()
        }
    }

    private suspend fun checksum(context: AgentToolContext, args: JSONObject): AgentToolResult {
        val uri = resolve(context.workspaceId, requiredPath(args))
        val md = MessageDigest.getInstance("SHA-256")
        runInterruptible(Dispatchers.IO) {
            resolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (total < 8 * 1024 * 1024) {
                    val read = input.read(buffer, 0, minOf(buffer.size, 8 * 1024 * 1024 - total))
                    if (read < 0) break
                    md.update(buffer, 0, read)
                    total += read
                }
            } ?: error("Unable to read media.")
        }
        return AgentToolResult.Success("Calculated SHA-256.", JSONObject().put("sha256", md.digest().joinToString("") { "%02x".format(it) }).toString())
    }

    private suspend fun readBytes(context: AgentToolContext, args: JSONObject): AgentToolResult {
        val path = requiredPath(args)
        val bytes = readRaw(context.workspaceId, path, args.optInt("maxBytes", 512).coerceIn(1, 4096))
        return AgentToolResult.Success("Read bounded bytes.", JSONObject().put("byteCount", bytes.size).put("hex", bytes.joinToString("") { "%02x".format(it) }).toString())
    }

    private suspend fun fileMetadata(context: AgentToolContext, args: JSONObject): AgentToolResult {
        val path = requiredPath(args)
        val m = metadata(resolve(context.workspaceId, path))
        return AgentToolResult.Success("Read metadata.", JSONObject().put("path", path).put("name", m.name ?: JSONObject.NULL).put("mimeType", m.mime ?: JSONObject.NULL).put("sizeBytes", m.size ?: JSONObject.NULL).put("modifiedAtEpochMs", m.modified ?: JSONObject.NULL).toString())
    }

    private suspend fun directory(context: AgentToolContext, args: JSONObject, sort: String): AgentToolResult {
        val path = args.optString("path").trim().trim('/')
        val uri = resolve(context.workspaceId, path)
        val entries = tree.list(uri, 500).map {
            val m = metadata(it.uri)
            JSONObject().put("name", it.name).put("directory", it.isDirectory).put("sizeBytes", m.size ?: JSONObject.NULL).put("modifiedAtEpochMs", m.modified ?: JSONObject.NULL)
        }
        val sorted = when (sort.lowercase(Locale.US)) {
            "size" -> entries.sortedByDescending { it.optLong("sizeBytes", -1) }
            "modified", "time" -> entries.sortedByDescending { it.optLong("modifiedAtEpochMs", -1) }
            else -> entries.sortedBy { it.optString("name").lowercase(Locale.US) }
        }
        return AgentToolResult.Success("Listed directory.", JSONArray(sorted).toString())
    }

    private suspend fun largestFiles(context: AgentToolContext, args: JSONObject): AgentToolResult {
        val nodes = walk(context.workspaceId, args.optInt("maxFiles", 120).coerceIn(1, 200))
        val out = nodes.mapNotNull {
            val size = if (it.uri != null) {
                metadata(it.uri).size
            } else {
                remoteMetadata(context.workspaceId, it.path).size
            } ?: return@mapNotNull null
            JSONObject().put("path", it.path).put("sizeBytes", size)
        }.sortedByDescending { it.optLong("sizeBytes") }.take(args.optInt("limit", 15).coerceIn(1, 50))
        return AgentToolResult.Success("Found largest files.", JSONArray(out).toString())
    }

    private suspend fun duplicates(context: AgentToolContext, args: JSONObject): AgentToolResult {
        val nodes = walk(context.workspaceId, args.optInt("maxFiles", 80).coerceIn(1, 100))
        val sized = nodes.groupBy {
            if (it.uri != null) metadata(it.uri).size ?: -1L
            else remoteMetadata(context.workspaceId, it.path).size ?: -1L
        }.filterKeys { it >= 0 }
        val groups = mutableListOf<JSONObject>()
        for ((size, sameSize) in sized) {
            if (sameSize.size < 2 || size > 1024 * 1024) continue
            val byHash = sameSize.groupBy {
                if (it.uri != null) hashUri(it.uri, 1024 * 1024)
                else hashRemote(context.workspaceId, it.path, 1024 * 1024)
            }
            byHash.filterValues { it.size > 1 }.forEach { (hash, matches) ->
                groups += JSONObject().put("sizeBytes", size).put("sha256", hash).put("paths", JSONArray(matches.map { it.path }))
            }
        }
        return AgentToolResult.Success("Found duplicate groups.", JSONArray(groups).toString())
    }

    private suspend fun textStats(context: AgentToolContext, args: JSONObject): AgentToolResult {
        val text = readText(context.workspaceId, requiredPath(args), 512 * 1024)
        val words = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        return AgentToolResult.Success("Calculated text statistics.", JSONObject().put("lines", if (text.isEmpty()) 0 else text.count { it == '\n' } + 1).put("words", words).put("characters", text.length).put("bytes", text.toByteArray(Charsets.UTF_8).size).toString())
    }

    private suspend fun encoding(context: AgentToolContext, args: JSONObject): AgentToolResult {
        val path = requiredPath(args)
        val bytes = readRaw(context.workspaceId, path, 64 * 1024)
        val value = when {
            bytes.size >= 3 && bytes[0].toInt() and 255 == 239 && bytes[1].toInt() and 255 == 187 && bytes[2].toInt() and 255 == 191 -> "UTF-8 BOM"
            bytes.size >= 2 && bytes[0].toInt() and 255 == 255 && bytes[1].toInt() and 255 == 254 -> "UTF-16LE BOM"
            bytes.size >= 2 && bytes[0].toInt() and 255 == 254 && bytes[1].toInt() and 255 == 255 -> "UTF-16BE BOM"
            else -> runCatching {
                StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes))
                "UTF-8"
            }.getOrElse { "Unknown/invalid UTF-8" }
        }
        return AgentToolResult.Success("Detected file encoding.", JSONObject().put("encoding", value).toString())
    }

    private suspend fun binary(context: AgentToolContext, args: JSONObject): AgentToolResult {
        val bytes = readRaw(context.workspaceId, requiredPath(args), 8192)
        val isBinary = bytes.any { it.toInt() == 0 }
        return AgentToolResult.Success(if (isBinary) "File appears binary." else "File appears text-like.", JSONObject().put("binary", isBinary).put("sampleBytes", bytes.size).toString())
    }

    private suspend fun preview(context: AgentToolContext, args: JSONObject): AgentToolResult =
        AgentToolResult.Success("Read preview.", readText(context.workspaceId, requiredPath(args), args.optInt("maxBytes", 12000).coerceIn(1, 64 * 1024)).take(12000))

    private suspend fun search(context: AgentToolContext, pattern: Pattern): AgentToolResult {
        val hits = mutableListOf<String>()
        for (node in walk(context.workspaceId, 120)) {
            currentCoroutineContext().ensureActive()
            if (!isText(node.path)) continue
            val text = runCatching { readText(context.workspaceId, node.path, 256 * 1024) }.getOrNull() ?: continue
            text.lineSequence().forEachIndexed { index, line ->
                if (pattern.matcher(line).find() && hits.size < 200) hits += node.path + ":" + (index + 1) + ": " + line.take(400)
            }
        }
        return AgentToolResult.Success("Search completed.", hits.joinToString("\n"))
    }

    private suspend fun countSymbols(context: AgentToolContext): AgentToolResult {
        val regex = Regex("(?m)^\\s*(class|interface|object|enum class|fun|def|fn|func|struct|trait|type)\\b")
        val out = JSONObject()
        for (node in walk(context.workspaceId, 100)) {
            val text = runCatching { readText(context.workspaceId, node.path, 128 * 1024) }.getOrNull() ?: continue
            val count = regex.findAll(text).count()
            if (count > 0) out.put(node.path, count)
        }
        return AgentToolResult.Success("Counted declarations.", out.toString())
    }

    private suspend fun sourceFiles(context: AgentToolContext, tests: Boolean): AgentToolResult {
        val list = walk(context.workspaceId, 220).map { it.path }.filter {
            val p = it.lowercase(Locale.US)
            if (tests) p.contains("/test") || p.contains("test/") || p.endsWith("test.kt") || p.endsWith("test.java")
            else p.endsWith(".kt") || p.endsWith(".java") || p.endsWith(".kts") || p.endsWith(".py") || p.endsWith(".js") || p.endsWith(".ts") || p.endsWith(".rs") || p.endsWith(".go")
        }
        return AgentToolResult.Success("Found source files.", list.take(250).joinToString("\n"))
    }

    private suspend fun languageBreakdown(context: AgentToolContext): AgentToolResult {
        val counts = linkedMapOf<String, Int>()
        for (node in walk(context.workspaceId, 300)) {
            val ext = node.path.substringAfterLast('.', "(none)").lowercase(Locale.US)
            counts[ext] = (counts[ext] ?: 0) + 1
        }
        val out = JSONObject()
        counts.toSortedMap().forEach { (key, value) -> out.put(key, value) }
        return AgentToolResult.Success("Summarized file extensions.", out.toString())
    }

    private suspend fun detectConfigs(context: AgentToolContext, expected: Set<String>): AgentToolResult {
        val names = walk(context.workspaceId, 320).map { it.path.substringAfterLast('/').lowercase(Locale.US) }
        return AgentToolResult.Success("Detected configuration files.", names.filter { it in expected }.distinct().sorted().joinToString("\n"))
    }

    private fun whichCommand(args: JSONObject): AgentToolResult {
        val command = args.optString("command").trim()
        require(command.matches(Regex("[A-Za-z0-9._+-]{1,80}")))
        val process = ProcessBuilder("/system/bin/which", command).redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().use { it.readText() }.trim()
        process.waitFor()
        return AgentToolResult.Success(if (text.isBlank()) "Command not found." else "Command found.", text)
    }

    private suspend fun commandVersion(context: AgentToolContext, args: JSONObject): AgentToolResult {
        val command = args.optString("command").trim()
        require(command.matches(Regex("[A-Za-z0-9._+-]{1,80}")))
        return runShell(context, command + " --version", 5000)
    }

    private fun environmentVars(): AgentToolResult {
        val names = listOf("PATH", "HOME", "TMPDIR", "SHELL", "USER", "LANG", "ANDROID_ROOT", "ANDROID_DATA")
        val out = JSONObject()
        names.forEach { name -> System.getenv(name)?.let { out.put(name, it) } }
        return AgentToolResult.Success("Read environment variables.", out.toString())
    }

    private fun systemProperties(): AgentToolResult =
        AgentToolResult.Success("Read Android system properties.", JSONObject().put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL).put("device", Build.DEVICE).put("sdkInt", Build.VERSION.SDK_INT).put("release", Build.VERSION.RELEASE).put("securityPatch", Build.VERSION.SECURITY_PATCH).toString())

    private fun cpuInfo(): AgentToolResult =
        AgentToolResult.Success("Read CPU information.", JSONObject().put("processors", Runtime.getRuntime().availableProcessors()).put("arch", System.getProperty("os.arch") ?: "unknown").put("abis", JSONArray(Build.SUPPORTED_ABIS.toList())).toString())

    private fun memoryInfo(): AgentToolResult {
        val info = ActivityManager.MemoryInfo()
        (app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        return AgentToolResult.Success("Read memory information.", JSONObject().put("totalBytes", info.totalMem).put("availableBytes", info.availMem).put("lowMemory", info.lowMemory).put("thresholdBytes", info.threshold).put("jvmMaxBytes", Runtime.getRuntime().maxMemory()).toString())
    }

    private fun storageCapacity(): AgentToolResult {
        val stat = StatFs(app.filesDir.path)
        val block = stat.blockSizeLong
        return AgentToolResult.Success("Read storage capacity.", JSONObject().put("totalBytes", stat.blockCountLong * block).put("availableBytes", stat.availableBlocksLong * block).put("freeBytes", stat.freeBlocksLong * block).toString())
    }

    private fun networkInfo(): AgentToolResult {
        val manager = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = manager.activeNetwork?.let(manager::getNetworkCapabilities)
        val transports = mutableListOf<String>()
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) transports += "wifi"
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) transports += "cellular"
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true) transports += "ethernet"
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) transports += "vpn"
        return AgentToolResult.Success("Read network information.", JSONObject().put("connected", capabilities != null).put("validated", capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true).put("transports", JSONArray(transports)).toString())
    }

    private fun processSnapshot(): AgentToolResult {
        val manager = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val processes = manager.runningAppProcesses.orEmpty().take(80).map {
            JSONObject().put("pid", it.pid).put("uid", it.uid).put("importance", it.importance).put("processName", it.processName)
        }
        return AgentToolResult.Success("Read process snapshot.", JSONArray(processes).toString())
    }

    private suspend fun gradle(context: AgentToolContext, task: String): AgentToolResult {
        val hasWrapper = walk(context.workspaceId, 80).any { it.path == "gradlew" }
        return runShell(context, if (hasWrapper) "./gradlew $task" else "gradle $task", 60_000)
    }

    private suspend fun git(context: AgentToolContext, command: String): AgentToolResult = runShell(context, command, 20_000)

    private suspend fun projectModules(context: AgentToolContext): AgentToolResult {
        val hits = mutableListOf<String>()
        for (node in walk(context.workspaceId, 120)) {
            if (!node.path.endsWith("settings.gradle", true) && !node.path.endsWith("settings.gradle.kts", true)) continue
            runCatching { readText(context.workspaceId, node.path, 128 * 1024) }.getOrNull()?.lineSequence()?.filter {
                it.contains("include(") || it.trimStart().startsWith("include ")
            }?.forEach { hits += it.trim() }
        }
        return AgentToolResult.Success("Discovered project module declarations.", hits.joinToString("\n"))
    }

    private suspend fun buildFiles(context: AgentToolContext): AgentToolResult {
        val names = setOf("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "gradle.properties", "pom.xml", "package.json", "pyproject.toml", "Cargo.toml", "go.mod", "Makefile", "CMakeLists.txt")
        return AgentToolResult.Success("Found build/configuration files.", walk(context.workspaceId, 300).map { it.path }.filter { it.substringAfterLast('/').lowercase(Locale.US) in names }.take(250).joinToString("\n"))
    }

    private suspend fun packageName(context: AgentToolContext): AgentToolResult {
        val nodes = walk(context.workspaceId, 200)
        val manifest = nodes.firstOrNull { it.path.endsWith("AndroidManifest.xml", true) }
        val manifestText = manifest?.let { runCatching { readText(context.workspaceId, it.path, 128 * 1024) }.getOrNull() }.orEmpty()
        Regex("""package\s*=\s*"([^"]+)"""").find(manifestText)?.groupValues?.getOrNull(1)?.let {
            return AgentToolResult.Success("Detected Android package.", it)
        }
        for (node in nodes.filter { it.path.endsWith("build.gradle", true) || it.path.endsWith("build.gradle.kts", true) }) {
            val text = runCatching { readText(context.workspaceId, node.path, 128 * 1024) }.getOrNull().orEmpty()
            Regex("""applicationId\s*[= ]\s*"([^"]+)"""").find(text)?.groupValues?.getOrNull(1)?.let {
                return AgentToolResult.Success("Detected Android application ID.", it)
            }
        }
        return AgentToolResult.Failure("No Android package/applicationId found in the bounded scan.")
    }

    private suspend fun runShell(context: AgentToolContext, command: String, timeout: Long): AgentToolResult {
        val parsed = TerminalCommandParser.parseToolCommand(command, "", timeout, context.taskId)
        val output = StringBuilder()
        return when (val result = terminal.executeAuthorizedStreaming(context.workspaceId, parsed) { chunk ->
            if (output.length < TerminalCommandPolicy.MAX_OUTPUT_BYTES) {
                output.append(chunk.take(TerminalCommandPolicy.MAX_OUTPUT_BYTES - output.length))
            }
        }) {
            is com.mrredhood.devforge.core.terminal.TerminalCapabilityResult.Completed -> AgentToolResult.Success("Command completed.", output.toString())
            is com.mrredhood.devforge.core.terminal.TerminalCapabilityResult.ApprovalRequired -> AgentToolResult.Failure("Nested terminal approval is required.")
            is com.mrredhood.devforge.core.terminal.TerminalCapabilityResult.Failure -> AgentToolResult.Failure(result.message)
        }
    }

    private suspend fun readText(workspaceId: String, path: String, maxBytes: Int): String {
        val remote = githubStore.get(workspaceId)
        if (remote != null) {
            return when (val result = githubGateway.readFile(remote.owner, remote.repository, path.trim('/'), remote.branch)) {
                is GitHubFileResult.Success -> {
                    require(result.content.toByteArray(Charsets.UTF_8).size <= maxBytes) { "File exceeds bounded read limit." }
                    result.content
                }
                is GitHubFileResult.Failure -> error(result.message)
            }
        }
        val uri = resolve(workspaceId, path)
        return runInterruptible(Dispatchers.IO) {
            resolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= maxBytes) { "File exceeds bounded read limit." }
                    out.write(buffer, 0, read)
                }
                out.toString(Charsets.UTF_8.name())
            } ?: error("Unable to read file.")
        }
    }

    private suspend fun readRaw(workspaceId: String, path: String, maxBytes: Int): ByteArray {
        val remote = githubStore.get(workspaceId)
        if (remote != null) {
            val bytes = githubGateway.readFileBytes(remote.owner, remote.repository, path.trim('/'), remote.branch)
                .getOrElse { error(it.message ?: "Unable to read file.") }
            require(bytes.size <= maxBytes) { "File exceeds bounded read limit." }
            return bytes
        }
        val uri = resolve(workspaceId, path)
        return runInterruptible(Dispatchers.IO) {
            resolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(minOf(16 * 1024, maxBytes))
                var total = 0
                while (total < maxBytes) {
                    val read = input.read(buffer, 0, minOf(buffer.size, maxBytes - total))
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    total += read
                }
                out.toByteArray()
            } ?: error("Unable to read file.")
        }
    }

    private fun hashUri(uri: android.net.Uri, maxBytes: Int): String {
        val digest = MessageDigest.getInstance("SHA-256")
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (total < maxBytes) {
                val read = input.read(buffer, 0, minOf(buffer.size, maxBytes - total))
                if (read < 0) break
                digest.update(buffer, 0, read)
                total += read
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun walk(workspaceId: String, maxFiles: Int): List<Node> {
        val remote = githubStore.get(workspaceId)
        val output = mutableListOf<Node>()
        if (remote != null) {
            suspend fun visit(path: String, depth: Int) {
                if (depth > 12 || output.size >= maxFiles) return
                val entries = when (val result = githubGateway.listContents(remote.owner, remote.repository, path, remote.branch)) {
                    is GitHubContentsResult.Success -> result.entries
                    is GitHubContentsResult.Failure -> error(result.message)
                }
                for (entry in entries) {
                    currentCoroutineContext().ensureActive()
                    val childPath = if (path.isBlank()) entry.name else path + "/" + entry.name
                    if (entry.type == "dir") visit(childPath, depth + 1) else output += Node(childPath, null)
                    if (output.size >= maxFiles) return
                }
            }
            visit("", 0)
        } else {
            val root = resolve(workspaceId, "")
            suspend fun visit(uri: android.net.Uri, prefix: String, depth: Int) {
                if (depth > 12 || output.size >= maxFiles) return
                for (entry in tree.list(uri, 500)) {
                    currentCoroutineContext().ensureActive()
                    val path = if (prefix.isBlank()) entry.name else prefix + "/" + entry.name
                    if (entry.isDirectory) visit(entry.uri, path, depth + 1) else output += Node(path, entry.uri)
                    if (output.size >= maxFiles) return
                }
            }
            visit(root, "", 0)
        }
        return output
    }

    private data class Node(val path: String, val uri: android.net.Uri?)
    private data class Meta(val name: String?, val mime: String?, val size: Long?, val modified: Long?)

    private suspend fun remoteMetadata(workspaceId: String, path: String): Meta {
        val remote = githubStore.get(workspaceId) ?: return Meta(path.substringAfterLast('/'), null, null, null)
        val parent = path.substringBeforeLast('/', "")
        val name = path.substringAfterLast('/')
        val entry = when (val result = githubGateway.listContents(remote.owner, remote.repository, parent, remote.branch)) {
            is GitHubContentsResult.Success -> result.entries.firstOrNull { it.name.equals(name, true) }
            is GitHubContentsResult.Failure -> null
        } ?: return Meta(name, null, null, null)
        val mime = when (name.substringAfterLast('.', "").lowercase(Locale.US)) {
            "kt", "java", "js", "ts", "py", "rs", "go", "c", "cpp", "h", "hpp", "html", "css", "md", "json", "xml", "yaml", "yml", "toml", "txt" -> "text/plain"
            else -> null
        }
        return Meta(entry.name, mime, entry.sizeBytes, null)
    }

    private suspend fun hashRemote(workspaceId: String, path: String, maxBytes: Int): String {
        val remote = githubStore.get(workspaceId) ?: return ""
        val bytes = githubGateway.readFileBytes(remote.owner, remote.repository, path.trim('/'), remote.branch)
            .getOrElse { return "" }
        return MessageDigest.getInstance("SHA-256").digest(bytes.take(maxBytes).toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun metadata(uri: android.net.Uri): Meta {
        var name: String? = null
        var mime: String? = null
        var size: Long? = null
        var modified: Long? = null
        resolver.query(
            uri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val ni = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mi = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val si = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val li = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                if (ni >= 0) name = cursor.getString(ni)
                if (mi >= 0) mime = cursor.getString(mi)
                if (si >= 0 && !cursor.isNull(si)) size = cursor.getLong(si)
                if (li >= 0 && !cursor.isNull(li)) modified = cursor.getLong(li)
            }
        }
        return Meta(name, mime, size, modified)
    }

    private suspend fun resolve(workspaceId: String, path: String): android.net.Uri {
        var uri = android.net.Uri.parse(db.workspaceDao().findById(workspaceId)?.treeUri ?: error("Workspace not found."))
        path.trim('/').split('/').filter { it.isNotBlank() }.forEach { part ->
            uri = tree.list(uri, 500).firstOrNull { it.name == part }?.uri ?: error("Path does not exist.")
        }
        return uri
    }

    private fun requiredPath(args: JSONObject): String {
        val path = args.optString("path").trim()
        require(path.isNotBlank()) { "path is required." }
        return path
    }

    private fun isText(path: String): Boolean = path.substringAfterLast('.', "").lowercase(Locale.US) in textExtensions

    companion object {
        private val textExtensions = setOf("kt", "java", "kts", "gradle", "xml", "json", "yaml", "yml", "md", "txt", "js", "jsx", "ts", "tsx", "py", "rs", "go", "c", "h", "cpp", "hpp", "swift", "dart", "html", "css", "scss", "sass", "less", "toml", "properties", "sh")
        private val formatterMarkers = setOf(".editorconfig", ".prettierrc", ".prettierrc.json", ".prettierrc.js", "ktlint.yml", "ktlint.yaml", "spotless.gradle", "spotless.gradle.kts")
        private val linterMarkers = setOf("detekt.yml", "detekt.yaml", ".eslintrc", ".eslintrc.json", "eslint.config.js", "eslint.config.mjs", "pylintrc", ".flake8", "checkstyle.xml")
    }
}
