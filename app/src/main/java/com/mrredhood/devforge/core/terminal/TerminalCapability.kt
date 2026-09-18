package com.mrredhood.devforge.core.terminal

import android.content.Context
import com.mrredhood.devforge.core.policy.ActionRequest
import com.mrredhood.devforge.core.policy.Capability
import com.mrredhood.devforge.core.policy.DefaultPolicy
import com.mrredhood.devforge.core.policy.PermissionMode
import com.mrredhood.devforge.core.policy.RiskLevel
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class TerminalExecutable(
    val binaryPath: String,
    val risk: RiskLevel,
) {
    PWD("/system/bin/pwd", RiskLevel.R1),
    ECHO("/system/bin/echo", RiskLevel.R1),
    LS("/system/bin/ls", RiskLevel.R1),
    CAT("/system/bin/cat", RiskLevel.R1),
    HEAD("/system/bin/head", RiskLevel.R1),
    TAIL("/system/bin/tail", RiskLevel.R1),
    WC("/system/bin/wc", RiskLevel.R1),
    GREP("/system/bin/grep", RiskLevel.R1),
    MKDIR("/system/bin/mkdir", RiskLevel.R2),
    TOUCH("/system/bin/touch", RiskLevel.R2),
}

data class TerminalCommand(
    val executable: TerminalExecutable,
    val args: List<String> = emptyList(),
    val workingDirectory: String = "",
    val timeoutMs: Long = TerminalCommandPolicy.DEFAULT_TIMEOUT_MS,
) {
    fun canonicalForm(): String = buildString {
        append(executable.name).append('\n')
        append(workingDirectory).append('\n')
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
        require(command.args.size <= MAX_ARGS) {
            "Terminal commands may contain at most " + MAX_ARGS + " arguments."
        }
        require(command.timeoutMs in 250L..MAX_TIMEOUT_MS) {
            "Terminal timeout must be between 250 ms and " + MAX_TIMEOUT_MS + " ms."
        }
        require(command.workingDirectory.isEmpty() || isRelative(command.workingDirectory)) {
            "Terminal working directory must remain inside the sandbox."
        }
        command.args.forEach { arg ->
            require(arg.isNotEmpty() && arg.length <= MAX_ARG_LENGTH) {
                "Terminal arguments must be non-empty and bounded."
            }
            require('\u0000' !in arg && '\n' !in arg && '\r' !in arg) {
                "Terminal arguments cannot contain control separators."
            }
            require(!arg.startsWith("/") && !arg.startsWith("~/")) {
                "Absolute terminal paths are not allowed."
            }
            require("=/" !in arg && "=~/" !in arg) {
                "Option values cannot target an absolute path."
            }
            require(!arg.split('/').contains("..")) {
                "Terminal path traversal is not allowed."
            }
        }
        require(command.canonicalForm().toByteArray(Charsets.UTF_8).size <= MAX_COMMAND_BYTES) {
            "Terminal command exceeds the 8 KiB safety limit."
        }
    }

    private fun isRelative(path: String): Boolean {
        require(!path.startsWith("/") && !path.startsWith("~/")) {
            "Absolute terminal paths are not allowed."
        }
        return path.replace('\\', '/').split('/').none {
            it.isBlank() || it == "." || it == ".." || it.equals(".git", true)
        }
    }
}

enum class TerminalRunStatus {
    EXITED,
    TIMED_OUT,
    OUTPUT_LIMIT_EXCEEDED,
    START_FAILED,
}

data class TerminalExecution(
    val command: TerminalCommand,
    val status: TerminalRunStatus,
    val exitCode: Int?,
    val output: String,
    val durationMs: Long,
)

sealed interface TerminalCapabilityResult {
    data class Completed(
        val execution: TerminalExecution,
        val approvalId: String? = null,
    ) : TerminalCapabilityResult

    data class ApprovalRequired(
        val approvalId: String,
        val summary: String,
    ) : TerminalCapabilityResult

    data class Failure(val message: String) : TerminalCapabilityResult
}

/**
 * Native terminal authorization/execution boundary.
 *
 * It is intentionally not registered as an AgentTool: model output cannot obtain shell access.
 */
class TerminalCapability(
    context: Context,
    private val approvals: ApprovalRepository,
    private val durableState: DurableStateRepository,
    private val permissionMode: PermissionMode = PermissionMode.SOME,
) {
    private val terminal = SandboxedTerminal(context)

    suspend fun execute(workspaceId: String, command: TerminalCommand): TerminalCapabilityResult =
        executeStreaming(workspaceId, command) {}

    suspend fun executeStreaming(
        workspaceId: String,
        command: TerminalCommand,
        onOutput: suspend (String) -> Unit,
    ): TerminalCapabilityResult {
        val validation = runCatching { TerminalCommandPolicy.validate(command) }.exceptionOrNull()
        if (validation != null) {
            return TerminalCapabilityResult.Failure(validation.message ?: "Invalid terminal command.")
        }
        return authorizeAndExecute(workspaceId, command, null, onOutput)
    }

    suspend fun executeApproved(
        approvalId: String,
        workspaceId: String,
        command: TerminalCommand,
    ): TerminalCapabilityResult = executeApprovedStreaming(approvalId, workspaceId, command) {}
        val validation = runCatching { TerminalCommandPolicy.validate(command) }.exceptionOrNull()
        if (validation != null) {
            return TerminalCapabilityResult.Failure(validation.message ?: "Invalid terminal command.")
        }
        val action = actionRequest(workspaceId, command)
        val approval = approvals.observeById(approvalId).first()
            ?: return TerminalCapabilityResult.Failure("Approval '" + approvalId + "' was not found.")
        if (approval.status != ApprovalRepository.STATUS_APPROVED) {
            return TerminalCapabilityResult.Failure("Approval '" + approvalId + "' is not approved.")
        }
        if (approval.expiresAtEpochMs <= System.currentTimeMillis()) {
            approvals.expireDue()
            return TerminalCapabilityResult.Failure("Approval '" + approvalId + "' has expired.")
        }
        if (
            approval.workspaceId != workspaceId ||
            approval.actionId != action.actionId ||
            approval.parametersHash != action.parametersHash ||
            approval.capability != Capability.RUN_TERMINAL.name ||
            approval.risk != command.executable.risk.name
        ) {
            return TerminalCapabilityResult.Failure("Approval '" + approvalId + "' does not match this terminal command.")
        }
        if (!approvals.claimApproved(approvalId)) {
            return TerminalCapabilityResult.Failure("Approval '" + approvalId + "' is no longer executable.")
        }
        return authorizeAndExecute(workspaceId, command, approvalId) {}
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
            ?: return TerminalCapabilityResult.Failure("Approval '" + approvalId + "' was not found.")
        if (approval.status != ApprovalRepository.STATUS_APPROVED) return TerminalCapabilityResult.Failure("Approval '" + approvalId + "' is not approved.")
        if (approval.expiresAtEpochMs <= System.currentTimeMillis()) {
            approvals.expireDue()
            return TerminalCapabilityResult.Failure("Approval '" + approvalId + "' has expired.")
        }
        if (approval.workspaceId != workspaceId || approval.actionId != action.actionId ||
            approval.parametersHash != action.parametersHash || approval.capability != Capability.RUN_TERMINAL.name ||
            approval.risk != command.executable.risk.name
        ) return TerminalCapabilityResult.Failure("Approval '" + approvalId + "' does not match this terminal command.")
        if (!approvals.claimApproved(approvalId)) return TerminalCapabilityResult.Failure("Approval '" + approvalId + "' is no longer executable.")
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
                    .toString()
                    .take(TerminalCommandPolicy.MAX_COMMAND_BYTES),
                expiresAtEpochMs = System.currentTimeMillis() + APPROVAL_TTL_MS,
            )
            audit(workspaceId, action, "TERMINAL_APPROVAL_REQUIRED", "Terminal approval requested.", id)
            return TerminalCapabilityResult.ApprovalRequired(id, action.summary)
        }

        val execution = runCatching { terminal.execute(workspaceId, command, onOutput) }.getOrElse { error ->
            val message = error.message ?: "Terminal execution failed."
            if (approvalId != null) approvals.finishFailure(approvalId)
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
            "Terminal " + command.executable.name.lowercase() +
                " completed with status " + execution.status + ".",
            approvalId,
        )
        return TerminalCapabilityResult.Completed(execution, approvalId)
    }

    private fun actionRequest(workspaceId: String, command: TerminalCommand) = ActionRequest(
        actionId = "terminal:" + workspaceId + ":" +
            UUID.nameUUIDFromBytes(command.canonicalForm().toByteArray(Charsets.UTF_8)),
        capability = Capability.RUN_TERMINAL,
        risk = command.executable.risk,
        workspaceId = workspaceId,
        summary = (
            "Run " + command.executable.name.lowercase() +
                " in the DevForge sandbox (" + command.args.size + " args)."
            ).take(500),
        parametersHash = hash(workspaceId, command),
    )

    private fun hash(workspaceId: String, command: TerminalCommand): String =
        MessageDigest.getInstance("SHA-256")
            .digest((workspaceId + "\n" + command.canonicalForm()).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

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
    private val root = File(context.noBackupFilesDir, "terminal-sandboxes").apply { mkdirs() }

    suspend fun execute(
        workspaceId: String,
        command: TerminalCommand,
        onOutput: suspend (String) -> Unit = {},
    ): TerminalExecution = withContext(Dispatchers.IO) {
        TerminalCommandPolicy.validate(command)
        val sandboxRoot = sandboxRoot(workspaceId)
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
                    environment()["LC_ALL"] = "C"
                }
                .start()
        } catch (error: IOException) {
            return@withContext TerminalExecution(
                command,
                TerminalRunStatus.START_FAILED,
                null,
                (error.message ?: "Unable to start terminal command.").take(2_000),
                elapsed(startedAt),
            )
        }

        val cancellationHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause != null && process.isAlive) process.destroyForcibly()
        }

        try {
            val outputDeferred = async(Dispatchers.IO) { readBounded(process, onOutput) }
            if (!process.waitFor(command.timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                process.waitFor(500, TimeUnit.MILLISECONDS)
                val output = runCatching { outputDeferred.await() }
                    .getOrDefault("Terminal command exceeded its time limit.")
                return@withContext TerminalExecution(
                    command,
                    TerminalRunStatus.TIMED_OUT,
                    null,
                    output.take(TerminalCommandPolicy.MAX_OUTPUT_BYTES),
                    elapsed(startedAt),
                )
            }

            val output = try {
                outputDeferred.await()
            } catch (_: OutputLimitExceeded) {
                return@withContext TerminalExecution(
                    command,
                    TerminalRunStatus.OUTPUT_LIMIT_EXCEEDED,
                    null,
                    "Terminal output exceeded the 65536 byte limit; the process was terminated.",
                    elapsed(startedAt),
                )
            }

            TerminalExecution(
                command,
                TerminalRunStatus.EXITED,
                process.exitValue(),
                output.take(TerminalCommandPolicy.MAX_OUTPUT_BYTES),
                elapsed(startedAt),
            )
        } finally {
            cancellationHandle?.dispose()
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private fun sandboxRoot(workspaceId: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(workspaceId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(root, digest.take(32)).apply { mkdirs() }
    }

    private fun resolveDirectory(root: File, relativePath: String): File {
        val normalized = relativePath.replace('\\', '/').trim('/')
        if (normalized.isEmpty()) return root
        val candidate = File(root, normalized).canonicalFile
        val canonicalRoot = root.canonicalFile
        require(candidate.path == canonicalRoot.path || candidate.path.startsWith(canonicalRoot.path + File.separator)) {
            "Terminal working directory escapes the sandbox."
        }
        require(candidate.isDirectory) { "Terminal working directory does not exist: " + relativePath }
        return candidate
    }

    private fun normalizeArgument(argument: String): String {
        require(!argument.contains("..")) { "Terminal path traversal is not allowed." }
        return argument
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

    private fun elapsed(startedAt: Long): Long =
        ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)

    private class OutputLimitExceeded : RuntimeException()
}
