package com.mrredhood.devforge.core.workspace

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.nio.charset.StandardCharsets

data class WorkspaceSymbol(val path: String, val name: String, val kind: String, val line: Int)

object WorkspaceSymbolExtractor {
    private val patterns = listOf(
        Regex("""\b(class|interface|object|data\s+class|struct|record|union|module|namespace)\s+([A-Za-z_][A-Za-z0-9_]*)"""),
        Regex("""\b(fun|function|def|func|fn|proc|procedure|subroutine|sub)\s+([A-Za-z_][A-Za-z0-9_]*)"""),
        Regex("""\b(enum\s+class|enum|type|typedef|trait|protocol|interface)\s+([A-Za-z_][A-Za-z0-9_]*)"""),
        Regex("""\b(contract|library)\s+([A-Za-z_][A-Za-z0-9_]*)"""),
        Regex("""\b(component|entity|architecture)\s+([A-Za-z_][A-Za-z0-9_]*)"""),
        Regex("""^\s*([A-Za-z_][A-Za-z0-9_]*)\s*[:=]\s*\(?.*"""),
    )

    fun extract(path: String, content: String, maxSymbols: Int = 200): List<WorkspaceSymbol> {
        require(maxSymbols in 1..200)
        val result = mutableListOf<WorkspaceSymbol>()
        content.lineSequence().forEachIndexed { index, line ->
            if (result.size >= maxSymbols) return@forEachIndexed
            patterns.forEach { pattern ->
                if (result.size >= maxSymbols) return@forEach
                val match = pattern.find(line) ?: return@forEach
                result += WorkspaceSymbol(path.take(500), match.groupValues[2].take(160), match.groupValues[1].replace(" ", "_").lowercase().take(40), index + 1)
            }
        }
        return result.distinctBy { Triple(it.path, it.name, it.line) }
    }
}

class WorkspaceSymbolIndexStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("devforge_workspace_index", Context.MODE_PRIVATE)
    fun replace(workspaceId: String, symbols: List<WorkspaceSymbol>): Boolean {
        val array = org.json.JSONArray()
        symbols.take(MAX_SYMBOLS).forEach { s ->
            array.put(org.json.JSONObject().put("path", s.path).put("name", s.name).put("kind", s.kind).put("line", s.line))
        }
        val encoded = array.toString()
        if (encoded.toByteArray(Charsets.UTF_8).size > MAX_INDEX_BYTES) return false
        return prefs.edit().putString("index::$workspaceId", encoded).commit()
    }
    fun list(workspaceId: String, limit: Int = MAX_SYMBOLS): List<WorkspaceSymbol> = runCatching {
        val array = org.json.JSONArray(prefs.getString("index::$workspaceId", "[]"))
        buildList {
            for (i in 0 until minOf(array.length(), MAX_SYMBOLS, limit.coerceIn(1, MAX_SYMBOLS))) {
                val v = array.optJSONObject(i) ?: continue
                add(WorkspaceSymbol(v.optString("path").take(500), v.optString("name").take(160), v.optString("kind").take(40), v.optInt("line", 1).coerceAtLeast(1)))
            }
        }
    }.getOrDefault(emptyList())
    fun search(workspaceId: String, query: String, limit: Int = 60): List<WorkspaceSymbol> =
        list(workspaceId).filter { it.name.contains(query.trim(), true) || it.path.contains(query.trim(), true) }.take(limit.coerceIn(1, 100))
    fun clear(workspaceId: String) = prefs.edit().remove("index::$workspaceId").apply()
    companion object {
        const val MAX_SYMBOLS = 4_000
        private const val MAX_INDEX_BYTES = 512 * 1024
    }
}

class WorkspaceIndexer(private val resolver: ContentResolver) {
    suspend fun build(root: Uri, maxFiles: Int = 2_000, maxBytesPerFile: Int = 256 * 1024): List<WorkspaceSymbol> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            require(maxFiles in 1..2_000)
            require(maxBytesPerFile in 4 * 1024..512 * 1024)
            val result = mutableListOf<WorkspaceSymbol>()
            val budget = ScanBudget(maxFiles)
            walk(root, "", result, budget, maxBytesPerFile)
            result.take(WorkspaceSymbolIndexStore.MAX_SYMBOLS)
        }

    private fun walk(parent: Uri, prefix: String, result: MutableList<WorkspaceSymbol>, budget: ScanBudget, maxBytesPerFile: Int) {
        if (result.size >= WorkspaceSymbolIndexStore.MAX_SYMBOLS || budget.remainingFiles <= 0) return
        val documentId = runCatching { DocumentsContract.getDocumentId(parent) }.getOrElse { DocumentsContract.getTreeDocumentId(parent) }
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parent, documentId)
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
            ),
            null, null, "${DocumentsContract.Document.COLUMN_DISPLAY_NAME} COLLATE NOCASE ASC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            var visited = 0
            while (cursor.moveToNext() && budget.remainingFiles > 0 && result.size < WorkspaceSymbolIndexStore.MAX_SYMBOLS) {
                visited++
                budget.remainingFiles--
                val id = cursor.getString(idIndex)
                val name = cursor.getString(nameIndex) ?: "Unnamed"
                val mime = cursor.getString(mimeIndex)
                val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
                val child = DocumentsContract.buildDocumentUriUsingTree(parent, id)
                val childPath = if (prefix.isBlank()) name else "$prefix/$name"
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    walk(child, childPath, result, budget, maxBytesPerFile)
                } else if (isSourceName(name) && (size == null || size <= maxBytesPerFile)) {
                    val bytes = runCatching { resolver.openInputStream(child)?.use { it.readBytesLimited(maxBytesPerFile) } }.getOrNull()
                    if (bytes != null && bytes.indexOf(0) < 0) result += WorkspaceSymbolExtractor.extract(childPath, bytes.toString(StandardCharsets.UTF_8))
                }
            }
        }
    }

    private fun isSourceName(name: String): Boolean = SOURCE_EXTENSIONS.any { name.lowercase().endsWith(it) }
    private companion object {
        val SOURCE_EXTENSIONS = setOf(".kt",".kts",".java",".js",".jsx",".mjs",".cjs",".ts",".tsx",".mts",".cts",".py",".go",".rs",".cs",".swift",".php",".rb",".rake",".lua",".scala",".sc",".groovy",".gradle",".r",".pl",".pm",".hs",".lhs",".ex",".exs",".m",".mm",".d",".fs",".fsx",".fsi",".vb",".vbs",".jl",".zig",".nim",".nims",".clj",".cljs",".cljc",".gd",".sol",".pas",".pp",".f",".for",".f77",".f90",".f95",".f03",".f08",".cob",".cbl",".prolog",".pro",".ml",".mli",".vhd",".vhdl",".v",".sv",".svh",".c",".h",".cc",".cxx",".cpp",".hpp",".hh",".hxx")
    }
}

private data class ScanBudget(var remainingFiles: Int)

private fun java.io.InputStream.readBytesLimited(maxBytes: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > maxBytes) error("Source file exceeds indexing limit.")
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
