package com.mrredhood.devforge.core.workspace

import android.content.Context
import com.mrredhood.devforge.core.storage.DevForgeDatabase
import com.mrredhood.devforge.core.storage.toDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RelevantWorkspaceMatch(
    val path: String,
    val kind: String,
    val line: Int?,
    val score: Int,
)

class WorkspaceSemanticRetrievalService(context: Context) {
    private val appContext = context.applicationContext
    private val database = DevForgeDatabase.get(appContext)
    private val workspaceSearch = WorkspaceSearch(appContext.contentResolver)
    private val symbolIndex = WorkspaceSymbolIndexStore(appContext)

    suspend fun retrieve(
        workspaceId: String,
        query: String,
        pathPrefix: String = "",
        limit: Int = 12,
    ): List<RelevantWorkspaceMatch> = withContext(Dispatchers.IO) {
        val workspace = database.workspaceDao().findById(workspaceId)?.toDomain()
            ?: return@withContext emptyList()
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return@withContext emptyList()
        val tokens = normalizedQuery
            .lowercase()
            .split(Regex("[^a-z0-9_.$-]+"))
            .filter { it.length >= 2 }
            .distinct()
            .take(10)
        val prefix = pathPrefix.trim().trim('/')

        val ranked = linkedMapOf<String, RelevantWorkspaceMatch>()
        symbolIndex.search(workspaceId, normalizedQuery)
            .filter { prefix.isBlank() || it.path.startsWith(prefix, true) }
            .take(80)
            .forEach { symbol ->
                val score = score(tokens, symbol.path, symbol.name) + 12
                ranked[symbol.path + "#" + symbol.name + ":" + symbol.line] =
                    RelevantWorkspaceMatch(symbol.path, symbol.kind, symbol.line, score)
            }

        runCatching {
            workspaceSearch.search(workspace.treeUri, normalizedQuery, 80)
                .filter { !it.isDirectory }
                .filter { prefix.isBlank() || it.name.startsWith(prefix, true) || it.uri.toString().contains(prefix, true) }
                .forEach { entry ->
                    val relative = entry.name
                    val score = score(tokens, relative, relative)
                    ranked[relative] = RelevantWorkspaceMatch(relative, "file", null, score)
                }
        }

        ranked.values
            .sortedWith(compareByDescending<RelevantWorkspaceMatch> { it.score }.thenBy { it.path.lowercase() })
            .take(limit.coerceIn(1, 40))
    }

    private fun score(tokens: List<String>, path: String, symbol: String): Int {
        val p = path.lowercase()
        val s = symbol.lowercase()
        return tokens.sumOf { token ->
            when {
                s == token -> 20
                s.contains(token) -> 10
                p.contains("/$token") || p.contains(token + ".") -> 8
                p.contains(token) -> 4
                else -> 0
            }
        }
    }
}
