package com.mrredhood.devforge.core.agent

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Provider-neutral public web tools with bounded responses and SSRF-aware URL validation. */
class AgentWebToolProvider {
    fun registerAll(registry: AgentToolRegistry): AgentToolRegistry = registry
        .register(WebSearchTool())
        .register(ScrapeUrlTool())
        .register(FetchUrlTool())
        .register(ExtractLinksTool())

    private inner class WebSearchTool : AgentTool {
        override val definition = AgentToolDefinition(
            AgentToolId.WEB_SEARCH,
            "Search the public web and return bounded titles, URLs and snippets.",
            com.mrredhood.devforge.core.policy.Capability.READ_WORKSPACE,
            com.mrredhood.devforge.core.policy.RiskLevel.R0,
            false,
        )

        override suspend fun execute(context: AgentToolContext, request: AgentToolRequest): AgentToolResult = try {
            val args = JSONObject(request.argumentsJson)
            val query = args.optString("query").trim()
            require(query.isNotBlank()) { "Web search query cannot be empty." }
            val limit = args.optInt("limit", 8).coerceIn(1, MAX_SEARCH_RESULTS)
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            val html = HttpFetcher.fetch(
                "https://html.duckduckgo.com/html/?q=" + encoded,
                MAX_RESPONSE_BYTES,
            ).body
            val results = parseSearchResults(html, limit)
            AgentToolResult.Success(
                summary = "Found " + results.length() + " web results for '" + query + "'.",
                output = JSONObject().put("query", query).put("results", results).toString(),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            AgentToolResult.Failure(error.message ?: "Web search failed.")
        }
    }

    private inner class ScrapeUrlTool : AgentTool {
        override val definition = AgentToolDefinition(
            AgentToolId.SCRAPE_URL,
            "Fetch a public web page and extract bounded readable text and title.",
            com.mrredhood.devforge.core.policy.Capability.READ_WORKSPACE,
            com.mrredhood.devforge.core.policy.RiskLevel.R0,
            false,
        )

        override suspend fun execute(context: AgentToolContext, request: AgentToolRequest): AgentToolResult = try {
            val url = JSONObject(request.argumentsJson).optString("url").trim()
            val response = HttpFetcher.fetch(url, MAX_RESPONSE_BYTES)
            AgentToolResult.Success(
                summary = "Scraped " + url + ".",
                output = JSONObject()
                    .put("url", response.finalUrl)
                    .put("status", response.status)
                    .put("contentType", response.contentType ?: JSONObject.NULL)
                    .put("title", extractTitle(response.body))
                    .put("text", htmlToText(response.body).take(MAX_TEXT_CHARS))
                    .toString(),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            AgentToolResult.Failure(error.message ?: "Unable to scrape URL.")
        }
    }

    private inner class FetchUrlTool : AgentTool {
        override val definition = AgentToolDefinition(
            AgentToolId.FETCH_URL,
            "Fetch bounded raw HTTP(S) content from a public URL.",
            com.mrredhood.devforge.core.policy.Capability.READ_WORKSPACE,
            com.mrredhood.devforge.core.policy.RiskLevel.R0,
            false,
        )

        override suspend fun execute(context: AgentToolContext, request: AgentToolRequest): AgentToolResult = try {
            val url = JSONObject(request.argumentsJson).optString("url").trim()
            val response = HttpFetcher.fetch(url, MAX_RESPONSE_BYTES)
            AgentToolResult.Success(
                summary = "Fetched " + url + ".",
                output = JSONObject()
                    .put("url", response.finalUrl)
                    .put("status", response.status)
                    .put("contentType", response.contentType ?: JSONObject.NULL)
                    .put("body", response.body.take(MAX_RAW_CHARS))
                    .toString(),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            AgentToolResult.Failure(error.message ?: "Unable to fetch URL.")
        }
    }

    private inner class ExtractLinksTool : AgentTool {
        override val definition = AgentToolDefinition(
            AgentToolId.EXTRACT_LINKS,
            "Extract bounded normalized links from a web page.",
            com.mrredhood.devforge.core.policy.Capability.READ_WORKSPACE,
            com.mrredhood.devforge.core.policy.RiskLevel.R0,
            false,
        )

        override suspend fun execute(context: AgentToolContext, request: AgentToolRequest): AgentToolResult = try {
            val url = JSONObject(request.argumentsJson).optString("url").trim()
            val response = HttpFetcher.fetch(url, MAX_RESPONSE_BYTES)
            val links = extractLinks(response.finalUrl, response.body)
            AgentToolResult.Success(
                summary = "Extracted " + links.length() + " links from " + url + ".",
                output = JSONObject().put("url", response.finalUrl).put("links", links).toString(),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            AgentToolResult.Failure(error.message ?: "Unable to extract links.")
        }
    }

    companion object {
        private const val MAX_RESPONSE_BYTES = 512 * 1024
        private const val MAX_TEXT_CHARS = 24_000
        private const val MAX_RAW_CHARS = 24_000
        private const val MAX_SEARCH_RESULTS = 10
        private const val MAX_REDIRECTS = 3

        private fun parseSearchResults(html: String, limit: Int): JSONArray {
            val output = JSONArray()
            val pattern = Regex(
                """<a[^>]*class=["'][^"']*result__a[^"']*["'][^>]*href=["']([^"']+)["'][^>]*>(.*?)</a>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            )
            val snippetPattern = Regex(
                """<a[^>]*class=["'][^"']*result__snippet[^"']*["'][^>]*>(.*?)</a>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            )
            val snippets = snippetPattern.findAll(html)
                .map { cleanHtml(it.groupValues[1]) }
                .toList()
            pattern.findAll(html).take(limit).forEachIndexed { index, match ->
                val url = decodeSearchUrl(match.groupValues[1])
                if (url.isBlank() || !url.startsWith("http")) return@forEachIndexed
                output.put(
                    JSONObject()
                        .put("title", cleanHtml(match.groupValues[2]))
                        .put("url", url)
                        .put("snippet", snippets.getOrNull(index).orEmpty()),
                )
            }
            return output
        }

        private fun decodeSearchUrl(raw: String): String = runCatching {
            val uri = URI(raw)
            val query = uri.rawQuery.orEmpty()
            val encoded = query.split("&")
                .firstOrNull { it.startsWith("uddg=", true) }
                ?.substringAfter('=')
            if (encoded.isNullOrBlank()) raw
            else URLDecoder.decode(encoded, StandardCharsets.UTF_8.name())
        }.getOrDefault(raw)

        private fun extractTitle(html: String): String =
            Regex(
                """<title[^>]*>(.*?)</title>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ).find(html)?.groupValues?.getOrNull(1)?.let(::cleanHtml).orEmpty()

        private fun extractLinks(baseUrl: String, html: String): JSONArray {
            val output = JSONArray()
            val seen = linkedSetOf<String>()
            val regex = Regex(
                """<a[^>]*href=["']([^"'#]+)["']""",
                RegexOption.IGNORE_CASE,
            )
            for (match in regex.findAll(html)) {
                if (seen.size >= 100) break
                val href = match.groupValues[1].trim()
                val resolved = runCatching { URL(URL(baseUrl), href).toString() }.getOrNull() ?: continue
                if (!resolved.startsWith("http://") && !resolved.startsWith("https://")) continue
                if (seen.add(resolved)) output.put(resolved)
            }
            return output
        }

        private fun htmlToText(html: String): String = cleanHtml(
            html.replace(Regex("""(?is)<script[^>]*>.*?</script>"""), " ")
                .replace(Regex("""(?is)<style[^>]*>.*?</style>"""), " ")
                .replace(Regex("""(?is)<noscript[^>]*>.*?</noscript>"""), " ")
                .replace(Regex("""(?is)<svg[^>]*>.*?</svg>"""), " ")
                .replace(Regex("""(?is)<[^>]+>"""), " "),
        ).replace(Regex("""\s+"""), " ").trim()

        private fun cleanHtml(value: String): String = value
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("""\s+"""), " ")
            .trim()

        private object HttpFetcher {
            data class Response(
                val finalUrl: String,
                val status: Int,
                val contentType: String?,
                val body: String,
            )

            suspend fun fetch(url: String, maxBytes: Int): Response = withContext(Dispatchers.IO) {
                var current = validateUrl(url)
                for (attempt in 0..MAX_REDIRECTS) {
                    val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        instanceFollowRedirects = false
                        connectTimeout = 10_000
                        readTimeout = 15_000
                        setRequestProperty("User-Agent", "DevForge/1.0 (Android)")
                        setRequestProperty("Accept", "text/html,application/json,text/plain,*/*")
                    }
                    try {
                        val status = connection.responseCode
                        if (status in 300..399) {
                            val next = connection.getHeaderField("Location")
                            if (next.isNullOrBlank() || attempt == MAX_REDIRECTS) {
                                error("Too many redirects or missing redirect target.")
                            }
                            current = validateUrl(URL(URL(current), next).toString())
                            continue
                        }
                        if (status !in 200..299) {
                            error("HTTP " + status + " returned by " + current + ".")
                        }
                        val bytes = connection.inputStream.use { readBounded(it, maxBytes) }
                        return@withContext Response(
                            current,
                            status,
                            connection.contentType,
                            bytes.toString(Charsets.UTF_8),
                        )
                    } finally {
                        connection.disconnect()
                    }
                }
                error("Unable to fetch URL.")
            }

            private fun readBounded(input: java.io.InputStream, maxBytes: Int): ByteArray {
                val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (total < maxBytes) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= maxBytes) {
                        "Web response exceeds the " + (maxBytes / 1024) + " KiB safety limit."
                    }
                    output.write(buffer, 0, read)
                }
                return output.toByteArray()
            }

            private fun validateUrl(value: String): String {
                val parsed = URL(value)
                require(parsed.protocol == "http" || parsed.protocol == "https") {
                    "Only HTTP(S) URLs are supported."
                }
                require(parsed.userInfo == null) { "URL credentials are not allowed." }
                val host = parsed.host.trim().lowercase(Locale.US)
                require(host.isNotBlank()) { "URL host is required." }
                require(!isBlockedHost(host)) {
                    "Private, loopback, and link-local hosts are blocked."
                }
                return parsed.toString()
            }

            private fun isBlockedHost(host: String): Boolean {
                if (host == "localhost" || host.endsWith(".localhost")) return true
                val addresses = runCatching { InetAddress.getAllByName(host) }
                    .getOrDefault(emptyArray())
                return addresses.any { address ->
                    when (address) {
                        is Inet4Address -> {
                            val bytes = address.address.map { it.toInt() and 0xff }
                            bytes[0] == 10 ||
                                bytes[0] == 127 ||
                                (bytes[0] == 169 && bytes[1] == 254) ||
                                (bytes[0] == 192 && bytes[1] == 168) ||
                                (bytes[0] == 172 && bytes[1] in 16..31) ||
                                bytes[0] == 0
                        }
                        else -> address.isLoopbackAddress ||
                            address.isLinkLocalAddress ||
                            address.isSiteLocalAddress
                    }
                }
            }
        }
    }
}
