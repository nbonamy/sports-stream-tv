package fr.bonamy.sports.core

import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.jsoup.Jsoup
import java.net.URI

/** A fresh playlist and the request context required for playlists, keys and media segments. */
data class ResolvedStream(val url: String, val headers: Map<String, String>, val expiresAtMillis: Long?)

object PlayerPageParser {
    fun playlist(html: String): String? {
        // Parse data only. Never evaluate JavaScript or load the provider's advertising scripts.
        val expression = Regex("""return\s*\(\s*(\[\s*".*?])\.join\(\s*""\s*\)(.*?)\)\s*;""", RegexOption.DOT_MATCHES_ALL)
            .find(html)
        if (expression != null) {
            val base = decodeArray(expression.groupValues[1]) ?: return null
            var suffix = expression.groupValues[2]
            var value = base
            val variable = Regex("""^\s*\+\s*(\w+)\.join\(\s*""\s*\)""")
            val element = Regex("""^\s*\+\s*document\.getElementById\(['"]([^'"]+)['"]\)\.innerHTML""")
            while (suffix.isNotBlank()) {
                val v = variable.find(suffix)
                val e = element.find(suffix)
                when {
                    v != null -> {
                        val declaration = Regex("""\b(?:var|let|const)\s+${Regex.escape(v.groupValues[1])}\s*=\s*(\[.*?]);""", RegexOption.DOT_MATCHES_ALL).find(html)
                            ?: return null
                        value += decodeArray(declaration.groupValues[1]) ?: return null
                        suffix = suffix.substring(v.value.length)
                    }
                    e != null -> {
                        value += Jsoup.parse(html).getElementById(e.groupValues[1])?.html() ?: return null
                        suffix = suffix.substring(e.value.length)
                    }
                    else -> return null // A changed expression needs an explicit parser update.
                }
            }
            if (isHls(value)) return value
        }
        return Regex("""(?:source|file|src)\s*[:=]\s*['"](https?[^'"\s]+)['"]""")
            .findAll(html).map { it.groupValues[1].replace("\\/", "/").replace("&amp;", "&") }
            .firstOrNull { isHls(it) }
    }

    private fun decodeArray(json: String): String? = runCatching {
        JsonParser.parseString(json).asJsonArray.joinToString("") {
            require(it.isJsonPrimitive && it.asJsonPrimitive.isString)
            it.asString
        }
    }.getOrNull()

    private fun isHls(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && uri.host != null && uri.path.endsWith(".m3u8")
    }.getOrDefault(false)

    fun nextPages(html: String, pageUrl: String, includeAlternatives: Boolean = true): List<String> {
        val doc = Jsoup.parse(html, pageUrl)
        val next = mutableListOf<String>()
        // Prefer the working alternate player, then fall back to the page's current embed.
        doc.select("a[href]").filter { includeAlternatives && it.text().trim().equals("Stream 2", true) }
            .forEach { next += it.absUrl("href") }
        doc.select("iframe[src]").forEach { next += it.absUrl("src") }
        // This provider constructs its iframe from a channel ID and a known embed script.
        val wikiScript = doc.select("script[src]").firstOrNull {
            runCatching { URI(it.absUrl("src")).let { uri -> uri.host == "igniteandship.com" && uri.path == "/wiki.js" } }.getOrDefault(false)
        }
        val channel = Regex("""\bfid\s*=\s*['"]([a-zA-Z0-9_-]+)['"]""").find(html)?.groupValues?.get(1)
        if (wikiScript != null && channel != null) next += "https://igniteandship.com/wiki.php?player=desktop&live=$channel"
        return next.filter { candidate ->
            runCatching {
                val uri = URI(candidate)
                uri.scheme == "https" && (uri.host == URI(pageUrl).host || uri.host in setOf("wikisport.info", "igniteandship.com", "in-stream.click"))
            }.getOrDefault(false)
        }.distinct()
    }
}

class StreamResolver(private val http: PageClient = PageClient(), private val trace: (String) -> Unit = {}) {
    /** Discover selectable streams without resolving signed media URLs or running scripts. */
    suspend fun streams(channel: Channel): List<StreamLink> {
        val result = mutableListOf<StreamLink>()
        for (link in channel.links) {
            val found = withTimeoutOrNull(20_000) { discover(link.url, SportsRepository.BASE, mutableSetOf(), 0) }.orEmpty()
            result += found.ifEmpty { listOf(link) }
        }
        return result.distinctBy { it.url }.mapIndexed { index, link -> link.copy(label = "Stream ${index + 1}") }
    }

    private suspend fun discover(url: String, parent: String, visited: MutableSet<String>, depth: Int): List<StreamLink> {
        if (depth > 3 || !visited.add(url)) return emptyList()
        try {
            val page = http.get(url, mapOf("Referer" to parent))
            val options = streamOptions(page.body, page.url)
            if (options.size > 1) return options
            for (next in PlayerPageParser.nextPages(page.body, page.url, false)) {
                val found = discover(next, page.url, visited, depth + 1)
                if (found.isNotEmpty()) return found
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* Playback still gets a chance with the original channel URL. */ }
        return emptyList()
    }

    companion object {
        fun streamOptions(html: String, pageUrl: String): List<StreamLink> = Jsoup.parse(html, pageUrl)
            .select("a[href]").mapNotNull { anchor ->
                val number = Regex("(?i)^stream\\s*#?\\s*(\\d+)$").matchEntire(anchor.text().trim())?.groupValues?.get(1)?.toIntOrNull()
                    ?: return@mapNotNull null
                val url = anchor.absUrl("href")
                val uri = runCatching { URI(url) }.getOrNull() ?: return@mapNotNull null
                if (uri.scheme != "https" || uri.host != URI(pageUrl).host) return@mapNotNull null
                number to StreamLink("Stream $number", url)
            }.sortedBy { it.first }.map { it.second }.distinctBy { it.url }
    }

    suspend fun resolve(link: StreamLink): ResolvedStream = withTimeoutOrNull(60_000) {
        val visited = mutableSetOf<String>()
        walk(link.url, SportsRepository.BASE, visited, 0)
            ?: throw SourceUnavailable("This source is offline or its player is not supported yet. Try another source.")
    } ?: throw SourceUnavailable("The source took too long to respond. Try another source.")

    private suspend fun walk(url: String, parent: String, visited: MutableSet<String>, depth: Int): ResolvedStream? {
        if (depth > 6 || visited.size >= 12 || !visited.add(url)) return null
        try {
            trace("Reading ${URI(url).host}")
            val page = http.get(url, mapOf("Referer" to parent))
            val playlist = PlayerPageParser.playlist(page.body)
            if (playlist != null) {
                val origin = URI(page.url).let { "${it.scheme}://${it.rawAuthority}" }
                val headers = mapOf("Referer" to "$origin/", "Origin" to origin, "User-Agent" to PageClient.USER_AGENT)
                trace("Checking HLS at ${URI(playlist).host}")
                val manifest = http.get(playlist, headers)
                if (manifest.body.trimStart().startsWith("#EXTM3U")) {
                    val expiry = Regex("""[?&]expires=(\d+)""").find(playlist)?.groupValues?.get(1)?.toLongOrNull()?.times(1000)
                    return ResolvedStream(playlist, headers, expiry)
                }
            }
            for (next in PlayerPageParser.nextPages(page.body, page.url, false)) {
                walk(next, page.url, visited, depth + 1)?.let { return it }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { trace("${URI(url).host}: ${if (e is SourceUnavailable) e.message else e.javaClass.simpleName}") }
        return null
    }
}
