package fr.bonamy.sports.core

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.InetAddress
import java.net.URI

/** Player pages may change domains, but remain public HTTPS destinations. */
internal object PlayerDestination {
    fun accepts(value: String): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        if (uri.scheme != "https" || uri.host == null) return false
        val url = value.toHttpUrlOrNull() ?: return false
        if (!url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty()) return false
        val host = url.host.trimEnd('.')
        if ((!host.contains('.') && !host.contains(':')) || host.endsWith(".localhost") ||
            host.endsWith(".local") || host.endsWith(".internal")) return false
        return if (host.contains(':') || host.all { it.isDigit() || it == '.' }) {
            runCatching { isPublic(InetAddress.getByName(host)) }.getOrDefault(false)
        } else true
    }

    fun isPublic(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress) return false
        val bytes = address.address.map { it.toInt() and 255 }
        return if (bytes.size == 4) {
            bytes[0] != 0 && bytes[0] < 224 &&
                !(bytes[0] == 100 && bytes[1] in 64..127) &&
                !(bytes[0] == 198 && bytes[1] in 18..19)
        } else bytes[0] and 0xfe != 0xfc
    }
}
