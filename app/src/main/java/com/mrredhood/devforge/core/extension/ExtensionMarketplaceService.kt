package com.mrredhood.devforge.core.extension

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExtensionMarketplaceService(
    private val config: DevForgeMarketplaceConfig,
) {
    suspend fun searchAll(query: String, limit: Int = 20, type: String? = null): Result<List<MarketplaceExtension>> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(config.isConfigured()) {
                    "DevForge Marketplace server is not configured yet. Open Marketplace settings and add the server URL."
                }
                val params = buildList {
                    if (query.isNotBlank()) add("query=" + encode(query.trim()))
                    if (!type.isNullOrBlank() && type != "all") add("type=" + encode(type))
                    add("limit=" + limit.coerceIn(1, 50))
                }.joinToString("&")
                val payload = request(config.resolve("/v1/packages?" + params))
                val packages = JSONObject(payload).optJSONArray("packages") ?: JSONArray()
                buildList {
                    for (index in 0 until packages.length()) {
                        packages.optJSONObject(index)?.let { parsePackage(it)?.let(::add) }
                    }
                }
            }
        }

    suspend fun getPackage(id: String): Result<MarketplaceExtension> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(config.isConfigured()) { "DevForge Marketplace server is not configured." }
                parsePackage(JSONObject(request(config.resolve("/v1/packages/" + encodePath(id)))))
                    ?: error("Marketplace package is invalid.")
            }
        }

    private fun parsePackage(row: JSONObject): MarketplaceExtension? {
        val id = row.optString("id").trim()
        val name = row.optString("name").trim()
        val version = row.optString("version").trim()
        val downloadUrl = row.optString("downloadUrl").trim()
        if (id.isBlank() || name.isBlank() || version.isBlank() || downloadUrl.isBlank()) return null

        return MarketplaceExtension(
            id = id,
            name = name,
            version = version,
            description = row.optString("description").trim(),
            publisher = row.optJSONObject("publisher")?.optString("name").orEmpty().ifBlank { "DevForge community" },
            source = ExtensionSource.DEVFORGE,
            downloadUrl = config.resolve(downloadUrl),
            sourcePageUrl = row.optString("sourcePageUrl").ifBlank { config.resolve("/packages/" + encodePath(id)) },
            downloads = row.optLong("downloads").takeIf { it > 0L },
            rating = row.optDouble("rating").takeIf { !it.isNaN() && it > 0.0 },
            priceText = null,
            installable = row.optBoolean("installable", true),
            packageType = row.optString("type").ifBlank { "extension" },
            apiVersion = row.optString("apiVersion").ifBlank { "1.0" },
            minimumDevForgeVersion = row.optString("minimumDevForgeVersion").ifBlank { "1.0.0" },
            requestedPermissions = row.optJSONArray("permissions").toStrings(),
        )
    }

    private fun request(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "DevForge/0.1 Android")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            require(code in 200..299) { "Marketplace request failed (HTTP $code)." }
            return BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replace("+", "%20")

    private fun encodePath(value: String): String = encode(value)

    private fun JSONArray?.toStrings(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }
}
