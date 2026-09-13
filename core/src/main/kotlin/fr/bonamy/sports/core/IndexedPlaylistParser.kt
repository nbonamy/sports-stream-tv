package fr.bonamy.sports.core

import com.google.gson.JsonParser
import java.util.Base64

/** Reads stream-xhd's indexed character data; never evaluates provider JavaScript. */
internal object IndexedPlaylistParser {
    fun parse(html: String): String? = runCatching {
        // Match the inspected decoding operation before interpreting its data or constants.
        val decoder = Regex("""\b([A-Za-z_$][\w$]*)\.forEach\(e\s*=>\s*\{\s*let\s+v\s*=\s*e\[1\];\s*playbackURL\s*\+=\s*String\.fromCharCode\(parseInt\(atob\(v\)\.replace\(/\\D/g,\s*''\)\)\s*-\s*k\)\s*\}\);""")
        val arrayName = decoder.find(html)?.groupValues?.get(1)?.let(Regex::escape) ?: return null
        if (!Regex("""$arrayName\.sort\(\(a,b\)\s*=>\s*a\[0\]\s*-\s*b\[0\]\);""").containsMatchIn(html) ||
            !Regex("""\bsource\s*:\s*playbackURL\s*[,}]""").containsMatchIn(html)) return null
        val functions = Regex("""\bvar\s+k\s*=\s*(\w+)\(\)\s*\+\s*(\w+)\(\)\s*;""").find(html) ?: return null
        val offset = functions.groupValues.drop(1).sumOf { name ->
            Regex("""function\s+${Regex.escape(name)}\(\)\s*\{\s*return\s+(\d+)\s*;\s*\}""")
                .find(html)?.groupValues?.get(1)?.toLongOrNull() ?: return null
        }
        require(offset in 0..Int.MAX_VALUE.toLong())
        val data = Regex("""\b$arrayName\s*=\s*(\[\[.*?\]\]);""", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1) ?: return null
        val pieces = JsonParser.parseString(data).asJsonArray
        require(pieces.size() in 1..8192)
        val characters = arrayOfNulls<Char>(pieces.size())
        for (piece in pieces) {
            val pair = piece.asJsonArray
            require(pair.size() == 2 && pair[0].asJsonPrimitive.isNumber && pair[1].asJsonPrimitive.isString)
            val index = pair[0].asString.toInt()
            require(index in characters.indices && characters[index] == null)
            val encoded = pair[1].asString
            require(encoded.length <= 64)
            val number = String(Base64.getDecoder().decode(encoded), Charsets.US_ASCII)
                .filter { it in '0'..'9' }.toLong()
            val code = number - offset
            require(code in 32..126)
            characters[index] = code.toInt().toChar()
        }
        characters.joinToString("") { requireNotNull(it).toString() }
    }.getOrNull()
}
