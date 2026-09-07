package com.jnetai.assistant.internet

import com.jnetai.assistant.util.Err
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Free, API-less internet search for every chat mode, agent mode and the voice
 * assistant. Uses DuckDuckGo's public HTML endpoint (no API key, no account)
 * and falls back to DuckDuckGo's Instant Answer JSON API when no HTML results
 * are returned. Gate this behind Settings → "Enable Internet Search".
 *
 * Returns a compact, plain-text block of results (title / url / snippet) that
 * is injected into the model's context as a system message.
 */
object InternetSearch {

    const val KEY_ENABLED = "internet.search_enabled"
    private const val TIMEOUT_MS = 6_000L
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    /**
     * Searches the web for [query] and returns formatted plain text results.
     * Returns an empty string when nothing usable is found (never throws).
     */
    suspend fun search(query: String, maxResults: Int = 5, maxSnippet: Int = 600): String =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.isEmpty()) return@withContext ""
            val client = OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .writeTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            try {
                val html = get(client, "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(q, "UTF-8"))
                var results = parseHtml(html)
                if (results.isEmpty() && html.isBlank()) {
                    // fall back to the Instant Answer JSON API
                    results = parseInstantAnswer(
                        get(client, "https://api.duckduckgo.com/?q=" + URLEncoder.encode(q, "UTF-8") +
                            "&format=json&no_html=1&skip_disambig=1")
                    )
                }
                buildString {
                    results.take(maxResults).forEachIndexed { i, r ->
                        if (r.title.isNotBlank() || r.snippet.isNotBlank()) {
                            append(i + 1).append(". ")
                            if (r.title.isNotBlank()) append(htmlDecode(r.title)).append('\n')
                            if (r.url.isNotBlank()) append(r.url).append('\n')
                            if (r.snippet.isNotBlank()) append(htmlDecode(r.snippet).take(maxSnippet)).append("\n\n")
                        }
                    }
                }.trim().let {
                    if (it.isBlank()) "" else it
                }
            } catch (t: Throwable) {
                Err.w("Internet search failed for '$q': ${t.message ?: t.javaClass.simpleName}")
                ""
            }
        }

    private fun get(client: OkHttpClient, url: String): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.8")
            .build()
        client.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) return resp.body?.string().orEmpty()
            return ""
        }
    }

    private data class SearchResult(val title: String = "", val url: String = "", val snippet: String = "")

    /** Parses DuckDuckGo HTML "result__a" (title+link) and "result__snippet" rows. */
    private fun parseHtml(html: String): List<SearchResult> {
        val titles = Regex("<a[^>]*class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
            .findAll(html)
            .map { m ->
                SearchResult(
                    title = stripTags(m.groupValues[2]),
                    url = realUrl(m.groupValues[1])
                )
            }
            .toList()
        val snippets = Regex("<a[^>]*class=\"result__snippet\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
            .findAll(html)
            .map { stripTags(it.groupValues[1]).trim() }
            .toList()
        if (titles.isEmpty()) return emptyList()
        return titles.mapIndexed { i, t ->
            t.copy(snippet = snippets.getOrNull(i) ?: "")
        }
    }

    /** DuckDuckGo HTML links are redirects like /l/?uddg=<encoded>&rut=… — recover the real http(s) URL. */
    private fun realUrl(href: String): String {
        var url = href.trim()
        if (url.startsWith("//")) url = "https:$url"
        if (url.startsWith("/l/?uddg=")) {
            val uddg = url.substringAfter("uddg=").substringBefore('&')
            return runCatching { URLDecoder.decode(uddg, "UTF-8") }.getOrElse { uddg }
        }
        return url
    }

    /** Parses DuckDuckGo Instant Answer JSON (Abstract + RelatedTopics). */
    private fun parseInstantAnswer(json: String): List<SearchResult> {
        val out = mutableListOf<SearchResult>()
        try {
            val obj = com.google.gson.Gson().fromJson(json, com.google.gson.JsonObject::class.java) ?: return out
            val headline = obj.get("Heading")?.asString
            val url = obj.get("AbstractURL")?.asString
            val text = obj.get("AbstractText")?.asString
            if (!text.isNullOrBlank()) {
                out += SearchResult(title = headline ?: "", url = url ?: "", snippet = text.replace("\n", " ").trim())
            }
            obj.getAsJsonArray("RelatedTopics")?.forEach { el ->
                if (el.isJsonObject) {
                    val t = el.asJsonObject.get("Text")?.asString
                    val u = el.asJsonObject.get("FirstURL")?.asString
                    if (!t.isNullOrBlank()) out += SearchResult(title = "", url = u ?: "", snippet = t)
                }
            }
        } catch (t: Throwable) {
            Err.w("Instant Answer parse failed: ${t.message}")
        }
        return out
    }

    private fun stripTags(html: String): String =
        html.replace(Regex("<[^>]*>"), "").trim()

    private fun htmlDecode(s: String): String = s
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#x27;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .trim()
}