package com.mrredhood.devforge.core.terminal

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.mrredhood.devforge.core.policy.ActionRequest
import com.mrredhood.devforge.core.policy.Capability
import com.mrredhood.devforge.core.policy.DefaultPolicy
import com.mrredhood.devforge.core.policy.PermissionMode
import com.mrredhood.devforge.core.policy.RiskLevel
import com.mrredhood.devforge.core.storage.ApprovalEntity
import com.mrredhood.devforge.core.storage.ApprovalRepository
import com.mrredhood.devforge.core.storage.AuditEventEntity
import com.mrredhood.devforge.core.storage.DurableStateRepository
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class TerminalExecutable(val binaryPath: String, val risk: RiskLevel) {
    PWD("/system/bin/pwd", RiskLevel.R1),
    ECHO("/system/bin/echo", RiskLevel.R1),
    PRINTF("/system/bin/printf", RiskLevel.R1),
    LS("/system/bin/ls", RiskLevel.R1),
    CAT("/system/bin/cat", RiskLevel.R1),
    HEAD("/system/bin/head", RiskLevel.R1),
    TAIL("/system/bin/tail", RiskLevel.R1),
    WC("/system/bin/wc", RiskLevel.R1),
    GREP("/system/bin/grep", RiskLevel.R1),
    FIND("/system/bin/find", RiskLevel.R1),
    SORT("/system/bin/sort", RiskLevel.R1),
    UNIQ("/system/bin/uniq", RiskLevel.R1),
    CUT("/system/bin/cut", RiskLevel.R1),
    TR("/system/bin/tr", RiskLevel.R1),
    SED("/system/bin/sed", RiskLevel.R2),
    MKDIR("/system/bin/mkdir", RiskLevel.R2),
    TOUCH("/system/bin/touch", RiskLevel.R2),
    RM("/system/bin/rm", RiskLevel.R2),
    CP("/system/bin/cp", RiskLevel.R2),
    MV("/system/bin/mv", RiskLevel.R2),
    CHMOD("/system/bin/chmod", RiskLevel.R2),
}

data class TerminalCommand(
    val executable: TerminalExecutable,
    val args: List<String> = emptyList(),
    val workingDirectory: String = "",
    val timeoutMs: Long = TerminalCommandPolicy.DEFAULT_TIMEOUT_MS,
    val sessionId: String? = null,
) {
    fun canonicalForm(): String = buildString {
        append(executable.name).append('\n')
        append(workingDirectory).append('\n')
        append(sessionId.orEmpty()).append('\n')
        args.forEach { append(it.length).append(':').append(it).append('\n') }
        append(timeoutMs)
    }
}

object TerminalCommandPolicy {
    const val MAX_ARGS = 24
    const val MAX_ARG_LENGTH = 256
    const val MAX_COMMAND_BYTES = 8 * 1024
    const val MAX_OUTPUT_BYTES = 64 * 1024
    const val DEFAULT_TIMEOUT_MS = 10_000L
    const val MAX_TIMEOUT_MS = 15_000L

    fun validate(command: TerminalCommand) {
        require(command.args.size <= MAX_ARGS) { "Too many arguments." }
        require(command.timeoutMs in 250L..MAX_TIMEOUT_MS) { "Terminal timeout is out of range." }
        require(command.workingDirectory.isEmpty() || isRelative(command.workingDirectory)) {
            "Terminal working directory must remain inside the workspace sandbox."
        }
        command.args.forEach { arg ->
            require(arg.isNotEmpty() && arg.length <= MAX_ARG_LENGTH) { "Terminal argument is invalid." }
            require('\u0000' !in arg && '\n' !in arg && '\r' !in arg) { "Control characters are not allowed." }
            require(!arg.startsWith("/") && !arg.startsWith("~/")) { "Absolute paths are not allowed." }
            require("=/" !in arg && "=~/" !in arg) { "Absolute option values are not allowed." }
            require(!arg.split('/').contains("..")) { "Path traversal is not allowed." }
        }
        require(command.canonicalForm().toByteArray(Charsets.UTF_8).size <= MAX_COMMAND_BYTES) {
            "Command exceeds the 8 KiB limit."
        }
    }

    private fun isRelative(path: String): Boolean {
        require(!path.startsWith("/") && !path.startsWith("~/")) { "Absolute paths are not allowed." }
        val segments = path.replace('\\', '/').split('/')
        require(segments.none { it.isBlank() || it == "." || it == ".." || it == ".git" }) {
            "Unsafe workspace path segment."
        }
        require('\u0000' !in path && '\n' !in path && '\r' !in path) {
            "Control characters are not allowed."
        }
        return true
    }
}

enum class TerminalRunStatus { EXITED, TIMED_OUT, OUTPUT_LIMIT_EXCEEDED, START_FAILED }

data class TerminalExecution(
    val command: TerminalCommand,
    val status: TerminalRunStatus,
    val exitCode: Int?,
    val output: String,
    val durationMs: Long,
)

sealed interface TerminalCapabilityResult {
    data class Completed(val execution: TerminalExecution, val approvalId: String? = null) : TerminalCapabilityResult
    data class ApprovalRequired(val approvalId: String, val summary: String) : TerminalCapabilityResult
    data class Failure(val message: String) : TerminalCapabilityResult
}

class TerminalCapability(
    context: Context,
    private val approvals: ApprovalRepository,
    private val durableState: DurableStateRepository,
    private val permissionMode: PermissionMode = PermissionMode.SOME,
) {
    private val terminal = SandboxedTerminal(context)

    suspend fun prepareWorkspace(workspaceId: String, workspaceRoot: Uri): Result<Unit> =
        runCatching { terminal.prepareWorkspace(workspaceId, workspaceRoot) }

    suspend fun execute(workspaceId: String, command: TerminalCommand): TerminalCapabilityResult =
        executeStreaming(workspaceId, command) {}

    /**
     * Execute a command typed directly by the user through a real Android shell.
     * This path is intentionally only used by TerminalViewModel; agent/tool execution
     * continues through the policy-gated TerminalCommand API above.
     */
    suspend fun executeInteractiveShell(
        workspaceId: String,
        workingDirectory: String,
        commandLine: String,
        timeoutMs: Long,
        onOutput: suspend (String) -> Unit,
    ): TerminalExecution {
        val normalized = commandLine.trim()
        require(normalized.isNotBlank()) { "Enter a command." }
        require(normalized.length <= TerminalCommandPolicy.MAX_COMMAND_BYTES) { "Command is too long." }
        require('\u0000' !in normalized && '\r' !in normalized && '\n' !in normalized) {
            "Control characters are not allowed."
        }
        TerminalCommandPolicy.validate(
            TerminalCommand(
                executable = TerminalExecutable.PWD,
                workingDirectory = workingDirectory,
                timeoutMs = timeoutMs,
            ),
        )
        return terminal.executeShell(workspaceId, workingDirectory, normalized, timeoutMs, onOutput)
    }

    suspend fun executeStreaming(
        workspaceId: String,
        command: TerminalCommand,
        onOutput: suspend (String) -> Unit,
    ): TerminalCapabilityResult {
        val validation = runCatching { TerminalCommandPolicy.validate(command) }.exceptionOrNull()
        if (validation != null) return TerminalCapabilityResult.Failure(validation.message ?: "Invalid terminal command.")
        return authorizeAndExecute(workspaceId, command, null, onOutput)
    }

    suspend fun executeApprovedStreaming(
        approvalId: String,
        workspaceId: String,
        command: TerminalCommand,
        onOutput: suspend (String) -> Unit,
    ): TerminalCapabilityResult {
        val validation = runCatching { TerminalCommandPolicy.validate(command) }.exceptionOrNull()
        if (validation != null) return TerminalCapabilityResult.Failure(validation.message ?: "Invalid terminal command.")
        val action = actionRequest(workspaceId, command)
        val approval = approvals.observeById(approvalId).first()
            ?: return TerminalCapabilityResult.Failure("Approval not found.")
        if (approval.status != ApprovalRepository.STATUS_APPROVED) return TerminalCapabilityResult.Failure("Approval is not approved.")
        if (approval.expiresAtEpochMs <= System.currentTimeMillis()) {
            approvals.expireDue()
            return TerminalCapabilityResult.Failure("Approval has expired.")
        }
        if (
            approval.workspaceId != workspaceId ||
            approval.actionId != action.actionId ||
            approval.parametersHash != action.parametersHash ||
            approval.capability != Capability.RUN_TERMINAL.name ||
            approval.risk != command.executable.risk.name
        ) return TerminalCapabilityResult.Failure("Approval does not match this command.")
        if (!approvals.claimApproved(approvalId)) return TerminalCapabilityResult.Failure("Approval is no longer executable.")
        return authorizeAndExecute(workspaceId, command, approvalId, onOutput)
    }

    private suspend fun authorizeAndExecute(
        workspaceId: String,
        command: TerminalCommand,
        approvalId: String?,
        onOutput: suspend (String) -> Unit,
    ): TerminalCapabilityResult {
        val action = actionRequest(workspaceId, command)
        if (approvalId == null && DefaultPolicy.requiresApproval(action, permissionMode)) {
            val id = UUID.randomUUID().toString()
            approvals.createPending(
                approvalId = id,
                actionId = action.actionId,
                capability = action.capability,
                risk = action.risk,
                workspaceId = workspaceId,
                summary = action.summary,
                parametersHash = action.parametersHash,
                preconditionHash = null,
                payload = JSONObject()
                    .put("capability", Capability.RUN_TERMINAL.name)
                    .put("workspaceId", workspaceId)
                    .put("executable", command.executable.name)
                    .put("args", JSONArray(command.args))
                    .put("workingDirectory", command.workingDirectory)
                    .put("timeoutMs", command.timeoutMs)
                    .put("sessionId", command.sessionId)
                    .toString()
                    .take(TerminalCommandPolicy.MAX_COMMAND_BYTES),
                expiresAtEpochMs = System.currentTimeMillis() + APPROVAL_TTL_MS,
            )
            audit(workspaceId, action, "TERMINAL_APPROVAL_REQUIRED", "Terminal approval requested.", id)
            return TerminalCapabilityResult.ApprovalRequired(id, action.summary)
        }

        val execution = try {
            terminal.execute(workspaceId, command, onOutput)
        } catch (cancelled: CancellationException) {
            if (approvalId != null) runCatching { approvals.finishFailure(approvalId) }
            throw cancelled
        } catch (error: Throwable) {
            if (approvalId != null) approvals.finishFailure(approvalId)
            val message = error.message ?: "Terminal execution failed."
            audit(workspaceId, action, "TERMINAL_FAILED", message, approvalId)
            return TerminalCapabilityResult.Failure(message)
        }

        val success = execution.status == TerminalRunStatus.EXITED && execution.exitCode == 0
        if (approvalId != null) {
            if (success) approvals.finishSuccess(approvalId) else approvals.finishFailure(approvalId)
        }
        audit(
            workspaceId,
            action,
            if (success) "TERMINAL_COMPLETED" else "TERMINAL_FAILED",
            command.executable.name.lowercase() + " finished with exit " + (execution.exitCode ?: -1),
            approvalId,
        )
        return TerminalCapabilityResult.Completed(execution, approvalId)
    }

    private fun actionRequest(workspaceId: String, command: TerminalCommand) = ActionRequest(
        actionId = "terminal:" + workspaceId + ":" + UUID.nameUUIDFromBytes(
            command.canonicalForm().toByteArray(Charsets.UTF_8),
        ),
        capability = Capability.RUN_TERMINAL,
        risk = command.executable.risk,
        workspaceId = workspaceId,
        summary = (command.executable.name.lowercase() + " " + command.args.joinToString(" ")).take(500),
        parametersHash = MessageDigest.getInstance("SHA-256")
            .digest((workspaceId + "\n" + command.canonicalForm()).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) },
    )

    private suspend fun audit(
        workspaceId: String,
        action: ActionRequest,
        eventType: String,
        summary: String,
        approvalId: String?,
    ) {
        durableState.recordAudit(
            AuditEventEntity(
                eventId = UUID.randomUUID().toString(),
                workspaceId = workspaceId,
                actionId = action.actionId,
                capability = Capability.RUN_TERMINAL.name,
                risk = action.risk.name,
                eventType = eventType,
                summary = summary.take(500),
                metadataJson = JSONObject().put("approvalId", approvalId).toString(),
                createdAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    private companion object {
        const val APPROVAL_TTL_MS = 10L * 60L * 1000L
    }
}

private class SandboxedTerminal(context: Context) {
    private val resolver: ContentResolver = context.contentResolver
    private val root = File(context.noBackupFilesDir, "terminal-sandboxes").apply { mkdirs() }
    private val workspaceRoots = java.util.concurrent.ConcurrentHashMap<String, Uri>()

    suspend fun prepareWorkspace(workspaceId: String, workspaceRoot: Uri) = withContext(Dispatchers.IO) {
        workspaceRoots[workspaceId] = workspaceRoot
        mirrorIntoSandbox(workspaceRoot, sandboxRoot(workspaceId))
    }

    suspend fun executeShell(
        workspaceId: String,
        workingDirectory: String,
        commandLine: String,
        timeoutMs: Long,
        onOutput: suspend (String) -> Unit,
    ): TerminalExecution = withContext(Dispatchers.IO) {
        require(commandLine.isNotBlank())
        val workspaceRoot = workspaceRoots[workspaceId]
            ?: throw IOException("Terminal workspace is not prepared.")
        val sandboxRoot = sandboxRoot(workspaceId)
        mirrorIntoSandbox(workspaceRoot, sandboxRoot)
        val workingDirectoryFile = resolveDirectory(sandboxRoot, workingDirectory)
        val startedAt = System.nanoTime()

        val process = try {
            ProcessBuilder(
                "/system/bin/sh",
                "-c",
                commandLine,
            )
                .directory(workingDirectoryFile)
                .redirectErrorStream(true)
                .apply {
                    environment().clear()
                    environment()["PATH"] = "/system/bin:/system/xbin"
                    environment()["HOME"] = sandboxRoot.absolutePath
                    environment()["PWD"] = workingDirectoryFile.absolutePath
                    environment()["TMPDIR"] = File(sandboxRoot, "tmp").apply { mkdirs() }.absolutePath
                    environment()["SHELL"] = "/system/bin/sh"
                    environment()["TERM"] = "xterm-256color"
                    environment()["LANG"] = "C.UTF-8"
                }
                .start()
        } catch (error: IOException) {
            return@withContext TerminalExecution(
                TerminalCommand(
                    executable = TerminalExecutable.PWD,
                    workingDirectory = workingDirectory,
                    timeoutMs = timeoutMs,
                ),
                TerminalRunStatus.START_FAILED,
                null,
                (error.message ?: "Unable to start shell.").take(2_000),
                elapsed(startedAt),
            )
        }

        val cancellationHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause != null && process.isAlive) process.destroyForcibly()
        }

        val execution = try {
            val outputDeferred = async(Dispatchers.IO) { readBounded(process, onOutput) }
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                process.waitFor(500, TimeUnit.MILLISECONDS)
                TerminalExecution(
                    TerminalCommand(
                        executable = TerminalExecutable.PWD,
                        workingDirectory = workingDirectory,
                        timeoutMs = timeoutMs,
                    ),
                    TerminalRunStatus.TIMED_OUT,
                    null,
                    runCatching { outputDeferred.await() }.getOrDefault("Command timed out."),
                    elapsed(startedAt),
                )
            } else {
                try {
                    TerminalExecution(
                        TerminalCommand(
                            executable = TerminalExecutable.PWD,
                            workingDirectory = workingDirectory,
                            timeoutMs = timeoutMs,
                        ),
                        TerminalRunStatus.EXITED,
                        process.exitValue(),
                        outputDeferred.await(),
                        elapsed(startedAt),
                    )
                } catch (_: OutputLimitExceeded) {
                    TerminalExecution(
                        TerminalCommand(
                            executable = TerminalExecutable.PWD,
                            workingDirectory = workingDirectory,
                            timeoutMs = timeoutMs,
                        ),
                        TerminalRunStatus.OUTPUT_LIMIT_EXCEEDED,
                        null,
                        "Output exceeded the terminal limit.",
                        elapsed(startedAt),
                    )
                }
            }
        } finally {
            cancellationHandle?.dispose()
            if (process.isAlive) process.destroyForcibly()
        }

        if (execution.status == TerminalRunStatus.EXITED) {
            syncSandboxToWorkspace(sandboxRoot, workspaceRoot)
        }
        execution
    }

    suspend fun execute(
        workspaceId: String,
        command: TerminalCommand,
        onOutput: suspend (String) -> Unit,
    ): TerminalExecution = withContext(Dispatchers.IO) {
        TerminalCommandPolicy.validate(command)
        val workspaceRoot = workspaceRoots[workspaceId]
            ?: throw IOException("Terminal workspace is not prepared.")
        val sandboxRoot = sandboxRoot(workspaceId)
        mirrorIntoSandbox(workspaceRoot, sandboxRoot)
        val workingDirectory = resolveDirectory(sandboxRoot, command.workingDirectory)
        val startedAt = System.nanoTime()
        val process = try {
            ProcessBuilder(buildList {
                add(command.executable.binaryPath)
                addAll(command.args.map(::normalizeArgument))
            })
                .directory(workingDirectory)
                .redirectErrorStream(true)
                .apply {
                    environment().clear()
                    environment()["PATH"] = "/system/bin:/system/xbin"
                    environment()["HOME"] = sandboxRoot.absolutePath
                    environment()["PWD"] = workingDirectory.absolutePath
                    environment()["LC_ALL"] = "C"
                }
                .start()
        } catch (error: IOException) {
            return@withContext TerminalExecution(
                command,
                TerminalRunStatus.START_FAILED,
                null,
                (error.message ?: "Unable to start command.").take(2_000),
                elapsed(startedAt),
            )
        }

        val cancellationHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause != null && process.isAlive) process.destroyForcibly()
        }

        val execution = try {
            val outputDeferred = async(Dispatchers.IO) { readBounded(process, onOutput) }
            if (!process.waitFor(command.timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                process.waitFor(500, TimeUnit.MILLISECONDS)
                TerminalExecution(
                    command,
                    TerminalRunStatus.TIMED_OUT,
                    null,
                    runCatching { outputDeferred.await() }.getOrDefault("Command timed out."),
                    elapsed(startedAt),
                )
            } else {
                try {
                    TerminalExecution(
                        command,
                        TerminalRunStatus.EXITED,
                        process.exitValue(),
                        outputDeferred.await(),
                        elapsed(startedAt),
                    )
                } catch (_: OutputLimitExceeded) {
                    TerminalExecution(
                        command,
                        TerminalRunStatus.OUTPUT_LIMIT_EXCEEDED,
                        null,
                        "Output exceeded the terminal limit.",
                        elapsed(startedAt),
                    )
                }
            }
        } finally {
            cancellationHandle?.dispose()
            if (process.isAlive) process.destroyForcibly()
        }

        if (
            command.executable.risk >= RiskLevel.R2 &&
            execution.status == TerminalRunStatus.EXITED &&
            execution.exitCode == 0
        ) {
            syncSandboxToWorkspace(sandboxRoot, workspaceRoot)
        }
        execution
    }

    private fun mirrorIntoSandbox(sourceRoot: Uri, targetRoot: File) {
        targetRoot.deleteRecursively()
        targetRoot.mkdirs()
        val budget = MirrorBudget()
        copySafNode(sourceRoot, targetRoot, budget)
    }

    private fun copySafNode(source: Uri, target: File, budget: MirrorBudget) {
        val metadata = queryDocument(source) ?: throw IOException("Unable to read workspace document.")
        if (metadata.isDirectory) {
            target.mkdirs()
            listChildren(source).forEach { child ->
                val safeName = requireSafeName(child.name)
                if (safeName == ".git") {
                    copySafNode(child.uri, File(target, safeName), budget)
                } else {
                    copySafNode(child.uri, File(target, safeName), budget)
                }
            }
        } else {
            require(metadata.size <= MAX_FILE_BYTES) { "Workspace file is too large for Terminal: " + metadata.name }
            budget.consume(metadata.size)
            target.parentFile?.mkdirs()
            resolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output -> copyBounded(input, output, metadata.size) }
            } ?: throw IOException("Unable to read workspace file: " + metadata.name)
        }
    }

    private fun syncSandboxToWorkspace(sourceRoot: File, targetRoot: Uri) {
        syncDirectory(sourceRoot, targetRoot, MirrorBudget(), "")
    }

    private fun syncDirectory(source: File, target: Uri, budget: MirrorBudget, relativePath: String) {
        val existing = listChildren(target).associateBy { it.name }.toMutableMap()
        source.listFiles()?.sortedBy { it.name }?.forEach { item ->
            if (item.name == ".git") return@forEach
            val itemPath = if (relativePath.isBlank()) item.name else relativePath + "/" + item.name
            requireSafeName(item.name)
            if (item.isDirectory) {
                val directory = existing.remove(item.name)?.uri
                    ?: DocumentsContract.createDocument(
                        resolver,
                        target,
                        DocumentsContract.Document.MIME_TYPE_DIR,
                        item.name,
                    )
                    ?: throw IOException("Unable to create folder: " + itemPath)
                syncDirectory(item, directory, budget, itemPath)
            } else {
                val size = item.length()
                require(size <= MAX_FILE_BYTES) { "Terminal produced an oversized file: " + itemPath }
                budget.consume(size)
                val document = existing.remove(item.name)?.uri
                    ?: DocumentsContract.createDocument(resolver, target, "application/octet-stream", item.name)
                    ?: throw IOException("Unable to create file: " + itemPath)
                resolver.openOutputStream(document, "wt")?.use { output ->
                    item.inputStream().use { input -> copyBounded(input, output, size) }
                } ?: throw IOException("Unable to write workspace file: " + itemPath)
            }
        }
        // Do not delete entries that are absent from the mirrored subset.
        // Deletion through the terminal is therefore intentionally non-destructive on SAF workspaces.
    }

    private fun listChildren(parent: Uri): List<DocumentRef> = runCatching {
        val documentId = runCatching { DocumentsContract.getDocumentId(parent) }
            .getOrElse { DocumentsContract.getTreeDocumentId(parent) }
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parent, documentId)
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext() && size < MAX_CHILDREN) {
                    val id = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    val mime = cursor.getString(2).orEmpty()
                    add(
                        DocumentRef(
                            DocumentsContract.buildDocumentUriUsingTree(parent, id),
                            name,
                            mime == DocumentsContract.Document.MIME_TYPE_DIR,
                            cursor.getLong(3).takeIf { it >= 0L } ?: 0L,
                        ),
                    )
                }
            }
        }.orEmpty()
    }.getOrDefault(emptyList())

    private fun queryDocument(uri: Uri): DocumentMetadata? = runCatching {
        resolver.query(
            uri,
            arrayOf(
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                DocumentMetadata(
                    cursor.getString(0).orEmpty() == DocumentsContract.Document.MIME_TYPE_DIR,
                    cursor.getLong(1).takeIf { it >= 0L } ?: 0L,
                    cursor.getString(2).orEmpty(),
                )
            }
        }
    }.getOrNull()

    private fun copyBounded(input: java.io.InputStream, output: java.io.OutputStream, expectedBytes: Long) {
        val buffer = ByteArray(64 * 1024)
        var copied = 0L
        while (copied < expectedBytes) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), expectedBytes - copied).toInt())
            if (read <= 0) throw IOException("Unexpected end of workspace file.")
            output.write(buffer, 0, read)
            copied += read
        }
        if (input.read() != -1) throw IOException("Workspace file changed while mirroring.")
    }

    private suspend fun readBounded(process: Process, onOutput: suspend (String) -> Unit): String {
        BufferedInputStream(process.inputStream).use { input ->
            val output = ByteArrayOutputStream(TerminalCommandPolicy.MAX_OUTPUT_BYTES)
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > TerminalCommandPolicy.MAX_OUTPUT_BYTES) {
                    process.destroyForcibly()
                    throw OutputLimitExceeded()
                }
                output.write(buffer, 0, read)
                onOutput(buffer.copyOf(read).toString(Charsets.UTF_8))
            }
            return output.toString(Charsets.UTF_8.name())
        }
    }

    private fun normalizeArgument(argument: String): String {
        require(!argument.contains("..")) { "Path traversal is not allowed." }
        return argument
    }

    private fun resolveDirectory(root: File, relativePath: String): File {
        val normalized = relativePath.replace('\\', '/').trim('/')
        if (normalized.isEmpty()) return root
        val candidate = File(root, normalized).canonicalFile
        val canonicalRoot = root.canonicalFile
        require(candidate.path == canonicalRoot.path || candidate.path.startsWith(canonicalRoot.path + File.separator)) {
            "Working directory escapes the sandbox."
        }
        require(candidate.isDirectory) { "Directory does not exist: " + relativePath }
        return candidate
    }

    private fun sandboxRoot(workspaceId: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(workspaceId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(root, digest.take(32)).apply { mkdirs() }
    }

    private fun requireSafeName(value: String): String {
        val name = value.trim()
        require(name.isNotBlank() && name != "." && name != ".." && "/" !in name && "\\" !in name && '\u0000' !in name) {
            "Unsafe workspace document name."
        }
        return name.take(255)
    }

    private fun elapsed(startedAt: Long): Long =
        ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)

    private data class DocumentMetadata(val isDirectory: Boolean, val size: Long, val name: String)
    private data class DocumentRef(val uri: Uri, val name: String, val isDirectory: Boolean, val size: Long)

    private class MirrorBudget {
        private var files = 0
        private var bytes = 0L
        fun consume(size: Long) {
            require(size >= 0L)
            files += 1
            bytes += size
            require(files <= MAX_MIRROR_FILES) { "Terminal workspace has too many files." }
            require(bytes <= MAX_MIRROR_BYTES) { "Terminal workspace is too large." }
        }
    }

    private class OutputLimitExceeded : RuntimeException()

    private companion object {
        const val MAX_MIRROR_FILES = 8_000
        const val MAX_MIRROR_BYTES = 256L * 1024L * 1024L
        const val MAX_FILE_BYTES = 64L * 1024L * 1024L
        const val MAX_CHILDREN = 1_000
    }
}
