package fr.bonamy.sports.core

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Rank iframe evidence without requiring knowledge of the hosting domain. */
internal object PlayerEmbeds {
    private val excludedRole = Regex("(?i)(^|[\\s_-])(ad|ads|advert|advertisement|advertising|banner|tracking|analytics|chat|sponsor)([\\s_-]|$)")
    private val playerRole = Regex("(?i)(player|video|stream|embed)")
    private val hiddenStyle = Regex("(?i)(display\\s*:\\s*none|visibility\\s*:\\s*hidden)")

    fun candidates(doc: Document): List<String> = doc.select("iframe[src], iframe[data-src]")
        .filterNot { frame ->
            (listOf(frame) + frame.parents()).any { node ->
                node.hasAttr("hidden") || node.attr("aria-hidden") == "true" ||
                    hiddenStyle.containsMatchIn(node.attr("style")) || excludedRole.containsMatchIn(role(node))
            } || listOf("width", "height").any { dimension ->
                frame.attr(dimension).removeSuffix("px").toIntOrNull()?.let { it <= 2 } == true
            }
        }.mapNotNull { frame ->
            val url = listOf("src", "data-src").map { frame.absUrl(it) }.firstOrNull(PlayerDestination::accepts)
                ?: return@mapNotNull null
            val score = (if (frame.hasAttr("allowfullscreen") || frame.attr("allow").contains("fullscreen")) 4 else 0) +
                (if ((listOf(frame) + frame.parents()).any { playerRole.containsMatchIn(role(it)) }) 2 else 0) +
                (if (playerRole.containsMatchIn(java.net.URI(url).path.orEmpty())) 1 else 0)
            url to score
        }.sortedByDescending { it.second }.map { it.first }.distinct().take(8)

    private fun role(element: Element) = listOf("id", "class", "title", "name", "aria-label")
        .joinToString(" ") { element.attr(it) }
}
