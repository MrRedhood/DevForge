package com.mrredhood.devforge.core.agent

import android.content.ContentResolver
import android.net.Uri
import com.mrredhood.devforge.core.editor.ContentHasher
import com.mrredhood.devforge.core.editor.DiffEngine
import com.mrredhood.devforge.core.editor.DiffLine
import com.mrredhood.devforge.core.policy.Capability
import com.mrredhood.devforge.core.security.WorkspacePathScope
import com.mrredhood.devforge.core.storage.ApprovalEntity
import com.mrredhood.devforge.core.storage.WorkspaceDao
import com.mrredhood.devforge.core.storage.capabilityOrNull
import com.mrredhood.devforge.core.workspace.WorkspaceFileTree
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class AgentPatchPreview(
    val path: String,
    val before: String,
    val after: String,
    val beforeHash: String,
    val approvedPreconditionHash: String?,
    val preconditionMatches: Boolean,
    val diff: List<DiffLine>,
    val isNewFile: Boolean,
)

class AgentPatchPreviewService(
    private val resolver: ContentResolver,
    private val workspaceDao: WorkspaceDao,
) {
    private val diffEngine = DiffEngine()
    private val tree = WorkspaceFileTree(resolver)

    suspend fun preview(approval: ApprovalEntity): AgentPatchPreview = withContext(Dispatchers.IO) {
        require(approval.capabilityOrNull() == Capability.EDIT_FILES) {
            "Approval is not a file-edit action."
        }
        val payload = JSONObject(approval.payload)
        require(payload.optString("tool") == AgentToolId.PATCH_FILE.wireName) {
            "Approval does not contain a structured file patch."
        }
        val arguments = payload.optJSONObject("arguments")
            ?: throw IllegalArgumentException("Patch arguments are missing.")
        val patch = AgentFilePatchCodec.decode(arguments.toString())
        val allowed = WorkspacePathScope(
            buildList {
                val array = payload.optJSONArray("allowedPrefixes") ?: JSONArray().put("")
                require(array.length() <= WorkspacePathScope.MAX_PREFIXES) {
                    "Approval path scope exceeds the limit."
                }
                for (index in 0 until array.length()) add(array.optString(index, ""))
            },
        )
        val workspace = workspaceDao.findById(approval.workspaceId)
            ?: throw IllegalArgumentException("Workspace '" + approval.workspaceId + "' was not found.")
        val root = documentUri(Uri.parse(workspace.treeUri))
        val rawPath = WorkspacePathScope.normalize(patch.path)
        val directPathExists = exists(root, rawPath)
        val path = AgentWorkspacePath.canonicalize(
            rawPath = rawPath,
            workspaceName = workspace.name,
            scope = allowed,
            directPathExists = directPathExists,
        )
        val existed = exists(root, path)
        val before = readExisting(root, path)
        val beforeHash = ContentHasher.sha256(before)
        val approvedPrecondition = approval.preconditionHash
        val payloadPrecondition = payload.optString("preconditionHash", "").takeIf(String::isNotBlank)
        val expected = approvedPrecondition ?: payloadPrecondition
        require(expected != null) { "Patch approval has no pre-image binding." }
        AgentPatchPreview(
            path = path,
            before = before,
            after = patch.content,
            beforeHash = beforeHash,
            approvedPreconditionHash = expected,
            preconditionMatches = expected?.equals(beforeHash, ignoreCase = true) ?: true,
            diff = diffEngine.compare(before, patch.content).take(MAX_DIFF_LINES),
            isNewFile = !existed,
        )
    }

    private fun documentUri(uri: Uri): Uri {
        val treeDocumentId = runCatching { android.provider.DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        return treeDocumentId?.let { android.provider.DocumentsContract.buildDocumentUriUsingTree(uri, it) } ?: uri
    }

    private fun readExisting(root: Uri, path: String): String {
        val uri = runCatching { resolve(root, path) }.getOrNull() ?: return ""
        return readText(uri)
    }

    private fun exists(root: Uri, path: String): Boolean =
        runCatching { resolve(root, path); true }.getOrDefault(false)

    private fun resolve(root: Uri, path: String): Uri {
        var current = root
        WorkspacePathScope.normalize(path).split('/').forEach { segment ->
            current = tree.list(current, MAX_DIRECTORY_ENTRIES).firstOrNull { it.name == segment }?.uri
                ?: throw IllegalArgumentException("Workspace path does not exist: " + path)
        }
        return current
    }

    private fun readText(uri: Uri): String {
        val output = ByteArrayOutputStream()
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(8 * 1024)
            while (output.size() <= MAX_PREVIEW_BYTES) {
                val read = input.read(buffer)
                if (read < 0) break
                if (output.size() + read > MAX_PREVIEW_BYTES) {
                    throw IllegalArgumentException("Patch preview target exceeds the 128 KiB text limit.")
                }
                output.write(buffer, 0, read)
            }
        } ?: throw IOException("Unable to open patch preview target.")
        val bytes = output.toByteArray()
        require(!bytes.contains(0.toByte())) { "Patch preview target is binary." }
        return bytes.toString(Charsets.UTF_8)
    }

    companion object {
        private const val MAX_PREVIEW_BYTES = 128 * 1024
        private const val MAX_DIFF_LINES = 2_000
        private const val MAX_DIRECTORY_ENTRIES = 256
    }
}
