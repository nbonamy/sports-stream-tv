package fr.bonamy.sports.core

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import java.net.URI
import java.util.Base64

/** Interpret the published embed/session data; provider JavaScript is never executed. */
internal object SessionPlayer {
    private const val ID = "([A-Za-z_$][\\w$]*)"
    private data class Configuration(val media: String, val auth: String, val csrf: String)
    private fun value(v: String?): String = requireNotNull(v).also { require(it.isNotBlank() && it.length <= 8192) }
    private fun jsonValue(v: JsonElement?): String {
        require(v != null && v.isJsonPrimitive && (v.asJsonPrimitive.isString || v.asJsonPrimitive.isNumber))
        return value(v.asString)
    }
    private fun base64(v: String): String {
        require(v.length <= 32768 && Regex("^[A-Za-z0-9+/]+={0,2}$").matches(v))
        return String(Base64.getDecoder().decode(v), Charsets.UTF_8)
    }
    private fun destination(url: String): String = url.also { require(PlayerDestination.accepts(it)) }

    fun embed(html: String, pageUrl: String): String? = runCatching {
        val doc = Jsoup.parse(html, pageUrl)
        val script = doc.select("script[data-pldm][data-pid][data-play]").firstOrNull {
            PlayerDestination.accepts(it.absUrl("src")) && URI(it.absUrl("src")).path == "/scripts/v1/embed.min.js"
        } ?: return null
        if (doc.select(".video-player,.player-embed,.ratio").isEmpty()) return null
        val raw = Regex("""\b(?:const|let|var)\s+siteConfig\s*=\s*(\{[^\n]{1,65536}?\})\s*;""").find(html)?.groupValues?.get(1) ?: return null
        val data = JsonParser.parseString(raw).asJsonObject
        val host = value(script.attr("data-pldm")).trim()
        require(!Regex("""[\s/\\?#@]""").containsMatchIn(host))
        val category = script.attr("data-gc").trim().ifEmpty { "sports" }
        require(Regex("[A-Za-z0-9_-]+").matches(category))
        destination("https://$host/sd0embed/$category").toHttpUrl().newBuilder()
            .addQueryParameter("pid", value(script.attr("data-pid")).trim())
            .addQueryParameter("v", value(script.attr("data-play")).trim())
            .addQueryParameter("player", script.attr("data-player").trim().ifEmpty { "jw" })
            .addQueryParameter("csrf", jsonValue(data["csrf"]))
            .addQueryParameter("csrf_ip", jsonValue(data["csrf_ip"]))
            .addQueryParameter("expires", jsonValue(data["sec_expires"]))
            .addQueryParameter("sec_hash", jsonValue(data["sec_hash"]))
            .build().toString()
    }.getOrNull()

    private fun configuration(html: String): Configuration? = runCatching {
        fun match(pattern: String) = Regex(pattern).find(html)
        val selected = match("""\bfile\s*:\s*$ID\.toString\(\)""")?.groupValues?.get(1) ?: return null
        val source = match("""\b(?:let|const|var)\s+${Regex.escape(selected)}\s*=\s*new URL\($ID\)""")?.groupValues?.get(1) ?: return null
        val encoded = match("""\b${Regex.escape(source)}\s*=\s*$ID\(String\.fromCharCode\(\.\.\.(\[[\d,\s]{1,65536}\])\)\)""") ?: return null
        val decode = Regex.escape(encoded.groupValues[1])
        if (match("""function\s+$decode\($ID\)\s*\{\s*return\s+(?:window\.)?atob\(\1\)\s*;?\s*\}""") == null) return null
        val chars = JsonParser.parseString(encoded.groupValues[2]).asJsonArray
        require(chars.size() <= 16384 && chars.all { it.isJsonPrimitive && it.asJsonPrimitive.isNumber && it.asDouble in 0.0..127.0 && it.asDouble % 1 == 0.0 })
        val media = destination(base64(chars.joinToString("") { it.asInt.toChar().toString() }))
        val template = match("""\$\{$ID\}\?scode=\$\{encodeURIComponent\([^)]*\.scode\)\}&stream=\$\{encodeURIComponent\($ID\)\}""") ?: return null
        val initial = match("""\b[A-Za-z_$][\w$]*\(\{scode:"([^"\n]{1,8192})",ts:([A-Za-z_$][\w$]*),session_id:"([^"\n]{1,8192})",device_id:([A-Za-z_$][\w$]*)\}\)""") ?: return null
        fun literal(name: String): String = JsonParser.parseString(match("""\b${Regex.escape(name)}\s*=\s*("[^"\n]{1,8192}")""")!!.groupValues[1]).asString
        val endpoint = match("""\b${Regex.escape(template.groupValues[1])}\s*=\s*$decode\("([A-Za-z0-9+/=]+)"\)""")?.groupValues?.get(1) ?: return null
        val csrf = match(""""X-CSRF-Auth"\s*:\s*$decode\("([A-Za-z0-9+/=]+)"\)""")?.groupValues?.get(1) ?: return null
        val expires = match("""\b${Regex.escape(initial.groupValues[2])}\s*=\s*parseInt\((\d{1,12}),10\)""")?.groupValues?.get(1) ?: return null
        val host = match("""&host_id=`\+encodeURIComponent\("([^"\n]{1,256})"\)""")?.groupValues?.get(1) ?: return null
        val auth = destination(base64(endpoint)).toHttpUrl().newBuilder().query(null)
            .addQueryParameter("scode", initial.groupValues[1]).addQueryParameter("stream", literal(template.groupValues[2]))
            .addQueryParameter("expires", expires).addQueryParameter("u_id", literal(initial.groupValues[4]))
            .addQueryParameter("session_id", initial.groupValues[3]).addQueryParameter("host_id", host).build().toString()
        Configuration(media, auth, base64(csrf))
    }.getOrNull()

    suspend fun resolve(html: String, pageUrl: String, http: PageClient): ResolvedStream? {
        val config = configuration(html) ?: return null
        val origin = URI(pageUrl).let { "${it.scheme}://${it.rawAuthority}" }
        val headers = mapOf("Referer" to "$origin/", "Origin" to origin, "User-Agent" to PageClient.USER_AGENT)
        val response = http.getPlayerPage(config.auth, headers + mapOf("Accept" to "application/json", "X-CSRF-Auth" to config.csrf))
        val session = JsonParser.parseString(response.body).asJsonObject
        val success = session["success"]
        val device = session["device_id"]
        val expiry = session["valid_until"]
        require(success?.isJsonPrimitive == true && success.asJsonPrimitive.isBoolean && success.asBoolean &&
            device?.isJsonPrimitive == true && device.asJsonPrimitive.isString && device.asString.isNotEmpty() && device.asString.length <= 8192 &&
            expiry?.isJsonPrimitive == true && expiry.asJsonPrimitive.isNumber && expiry.asDouble.isFinite() &&
            expiry.asDouble > 0 && expiry.asDouble <= 9007199254740991.0 / 1000 && expiry.asDouble % 1 == 0.0)
        val media = config.media.toHttpUrl().newBuilder().setQueryParameter("u_id", device.asString).build().toString()
        val manifest = http.getPlayerPage(media, headers)
        require(manifest.body.trimStart().startsWith("#EXTM3U"))
        return ResolvedStream(media, headers, expiry.asLong * 1000)
    }
}
