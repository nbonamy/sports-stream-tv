package fr.bonamy.sports.core

import com.google.gson.JsonParser
import java.util.Base64

/** Decodes the data envelope used by barecrop's player, ignoring ads and P2P settings. */
internal object BarecropConfigParser {
    fun playlist(html: String): String? = runCatching {
        val encoded = Regex("""window\._econfig\s*=\s*['"]([A-Za-z0-9+/=]+)['"]""")
            .find(html)?.groupValues?.get(1) ?: return null
        require(encoded.length <= 512 * 1024)
        fun decode(value: String) = String(Base64.getDecoder().decode(value), Charsets.UTF_8)
        val envelope = decode(encoded)
        require(envelope.length % 4 == 0)
        val size = envelope.length / 4
        require(size > 4)
        val order = listOf(2, 0, 3, 1)
        val pieces = arrayOfNulls<String>(4)
        order.forEachIndexed { index, destination ->
            val piece = envelope.substring(index * size, (index + 1) * size)
            pieces[destination] = decode(piece.removeRange(3, 4))
        }
        val config = JsonParser.parseString(decode(pieces.joinToString("") { requireNotNull(it) })).asJsonObject
        fun value(name: String) = config[name]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString?.takeIf { it.isNotBlank() }
        value("stream_url_nop2p") ?: value("stream_url")
    }.getOrNull()
}
