package com.mrredhood.devforge.core.extension

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ExtensionMarketplaceService {

    suspend fun searchAll(query: String, limit: Int = 20): Result<List<MarketplaceExtension>> =
        withContext(Dispatchers.IO) {
            try {
                val (acode, vscode) = coroutineScope {
                    val a = async { runCatching { searchAcode(query, limit) } }
                    val v = async { runCatching { searchVsCode(query, limit) } }
                    a.await() to v.await()
                }
                val available = acode.getOrDefault(emptyList()) + vscode.getOrDefault(emptyList())
                if (available.isEmpty()) {
                    throw acode.exceptionOrNull() ?: vscode.exceptionOrNull()
                        ?: IllegalStateException("Both extension catalogs returned no extensions.")
                }
                Result.success(
                    available
                        .sortedByDescending { it.downloads ?: 0L }
                        .take(limit * 2),
                )
            } catch (throwable: Throwable) {
                Result.failure(throwable)
            }
        }

    private fun searchAcode(query: String, limit: Int): List<MarketplaceExtension> {
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.toString())
        val url = buildString {
            append("https://acode.app/api/plugin?limit=")
            append(limit.coerceIn(1, 50))
            append("&page=1&orderBy=downloads&supported_editor=all")
            if (query.isNotBlank()) append("&name=").append(encoded)
        }
        val json = request(url, "application/json") ?: error("Acode plugin registry returned no data.")
        val rows = JSONArray(json)
        return buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val id = row.optString("id").trim()
                if (id.isBlank()) continue
                val price = row.optString("price").ifBlank {
                    row.optDouble("price", 0.0).takeIf { !it.isNaN() }?.toString() ?: ""
                }
                val priceNumber = price.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0
                val version = row.optString("version").trim().ifBlank { "unknown" }
                add(
                    MarketplaceExtension(
                        id = id,
                        name = row.optString("name").ifBlank { id },
                        version = version,
                        description = row.optString("description").replace(Regex("<[^>]+>"), "").trim(),
                        publisher = row.optString("author").ifBlank { "Acode community" },
                        source = ExtensionSource.ACODE,
                        downloadUrl = "https://acode.app/api/plugin/download/" + encodePath(id),
                        sourcePageUrl = "https://acode.app/plugin/" + encodePath(id),
                        downloads = row.optLong("downloads").takeIf { it > 0L },
                        rating = rating(row),
                        priceText = if (priceNumber > 0.0) price else null,
                        installable = priceNumber <= 0.0,
                    ),
                )
            }
        }
    }

    private fun searchVsCode(query: String, limit: Int): List<MarketplaceExtension> {
        val criteria = JSONArray()
            .put(JSONObject().put("filterType", 8).put("value", "Microsoft.VisualStudio.Code"))
        if (query.isBlank()) {
            criteria.put(JSONObject().put("filterType", 12).put("value", "4096"))
        } else {
            criteria.put(JSONObject().put("filterType", 10).put("value", query.trim()))
        }

        val body = JSONObject()
            .put(
                "filters",
                JSONArray().put(
                    JSONObject()
                        .put("criteria", criteria)
                        .put("pageNumber", 1)
                        .put("pageSize", limit.coerceIn(1, 50))
                        .put("sortBy", 4)
                        .put("sortOrder", 0),
                ),
            )
            .put("assetTypes", JSONArray().put("Microsoft.VisualStudio.Services.VSIXPackage"))
            .put("flags", 514)

        val json = request(
            "https://marketplace.visualstudio.com/_apis/public/gallery/extensionquery",
            "application/json;api-version=7.2-preview.1",
            "POST",
            body.toString(),
        ) ?: error("VS Code Marketplace returned no data.")

        val results = JSONObject(json).optJSONArray("results") ?: JSONArray()
        val extensions = results.optJSONObject(0)?.optJSONArray("extensions") ?: JSONArray()
        return buildList {
            for (index in 0 until extensions.length()) {
                val row = extensions.optJSONObject(index) ?: continue
                val publisher = row.optJSONObject("publisher")?.optString("publisherName").orEmpty()
                val extensionName = row.optString("extensionName").trim()
                val id = listOf(publisher, extensionName).filter { it.isNotBlank() }.joinToString(".")
                if (id.isBlank()) continue
                val versionRow = row.optJSONArray("versions")?.optJSONObject(0) ?: continue
                val version = versionRow.optString("version").ifBlank { "unknown" }
                val files = versionRow.optJSONArray("files") ?: JSONArray()
                val packageUrl = findVsixUrl(files)
                    ?: "https://marketplace.visualstudio.com/_apis/public/gallery/publishers/" +
                    encodePath(publisher) + "/vsextensions/" + encodePath(extensionName) +
                    "/" + encodePath(version) + "/vspackage"
                val flags = row.optJSONObject("statistics")
                val downloads = flags?.optLong("install").takeIf { it != null && it > 0L }
                    ?: flags?.optLong("installs").takeIf { it != null && it > 0L }
                val rating = flags?.optDouble("rating").takeIf { it != null && !it.isNaN() }
                add(
                    MarketplaceExtension(
                        id = id,
                        name = row.optString("displayName").ifBlank { extensionName },
                        version = version,
                        description = row.optString("shortDescription").ifBlank { row.optString("description") },
                        publisher = publisher.ifBlank { "VS Code Marketplace" },
                        source = ExtensionSource.VSCODE,
                        downloadUrl = packageUrl,
                        sourcePageUrl = "https://marketplace.visualstudio.com/items?itemName=" + encodePath(id),
                        downloads = downloads,
                        rating = rating,
                        installable = packageUrl.isNotBlank(),
                    ),
                )
            }
        }
    }

    private fun findVsixUrl(files: JSONArray): String? {
        for (index in 0 until files.length()) {
            val file = files.optJSONObject(index) ?: continue
            if (file.optString("assetType") == "Microsoft.VisualStudio.Services.VSIXPackage") {
                file.optString("source").takeIf { it.startsWith("https://") }?.let { return it }
            }
        }
        return null
    }

    private fun request(
        url: String,
        accept: String,
        method: String = "GET",
        body: String? = null,
    ): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            requestMethod = method
            setRequestProperty("User-Agent", "DevForge/0.1 Android")
            setRequestProperty("Accept", accept)
            if (method == "POST") {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            if (body != null) {
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            }
            val code = connection.responseCode
            require(code in 200..299) { "Marketplace request failed (HTTP $code)." }
            return BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun rating(row: JSONObject): Double? =
        row.optDouble("rating").takeIf { !it.isNaN() && it > 0.0 }
            ?: runCatching {
                val up = row.optDouble("votes_up")
                val down = row.optDouble("votes_down")
                if (up + down <= 0) null else up / (up + down) * 5.0
            }.getOrNull()

    private fun encodePath(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replace("+", "%20")
}
