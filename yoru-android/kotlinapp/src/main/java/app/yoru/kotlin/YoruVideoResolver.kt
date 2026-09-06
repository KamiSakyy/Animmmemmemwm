package app.yoru.kotlin

import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.Base64
import java.util.Locale

class YoruVideoResolver(private val client: OkHttpClient) {
    fun resolve(input: String): Map<Int, String> {
        val url = embed(input)
        if (url.isBlank()) return emptyMap()
        direct(url).takeIf { it.isNotEmpty() }?.let { return it }
        val html = runCatching { text(url, referer(url)) }.getOrDefault("")
        scan(html, url).takeIf { it.isNotEmpty() }?.let { return it }
        decoded(html).forEach { chunk -> scan(chunk, url).takeIf { it.isNotEmpty() }?.let { return it } }
        return emptyMap()
    }

    private fun direct(url: String): Map<Int, String> {
        val lower = url.lowercase(Locale.ROOT)
        return when {
            lower.contains(".m3u8") || lower.contains("hls") || lower.contains("playlist") -> parseHls(runCatching { text(url, referer(url)) }.getOrDefault(""), url).ifEmpty { mapOf(qualityOf(url) to url) }
            lower.contains(".mpd") -> mapOf(qualityOf(url) to url)
            lower.contains(".mp4") || lower.contains(".webm") || lower.contains(".mkv") -> mapOf(qualityOf(url) to url)
            else -> emptyMap()
        }
    }

    private fun parseHls(manifest: String, base: String): Map<Int, String> {
        if (!manifest.trimStart().startsWith("#EXTM3U")) return emptyMap()
        val out = linkedMapOf<Int, String>()
        var h = 0
        manifest.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.startsWith("#EXT-X-STREAM-INF", true)) h = Regex("RESOLUTION=\\d+x(\\d+)").find(line)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            else if (line.isNotBlank() && !line.startsWith("#") && h > 0) {
                out[h] = resolve(base, line)
                h = 0
            }
        }
        if (out.isEmpty() && manifest.contains("#EXTINF")) out[qualityOf(base)] = base
        return out.toSortedMap()
    }

    private fun scan(html: String, base: String): Map<Int, String> {
        if (html.isBlank()) return emptyMap()
        val links = linkedSetOf<String>()
        val patterns = listOf(
            "https?:\\\\?/\\\\?/[^\"'<>\\s]+?(?:\\.m3u8|\\.mp4|\\.mpd)[^\"'<>\\s]*",
            "(?:file|src|url)\\s*[:=]\\s*[\"']([^\"']+(?:\\.m3u8|\\.mp4|\\.mpd)[^\"']*)[\"']",
            "<source[^>]+src=[\"']([^\"']+)[\"']",
            "iframe[^>]+src=[\"']([^\"']+)[\"']"
        )
        patterns.forEach { pattern ->
            Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(html).forEach { m ->
                val raw = (m.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() } ?: m.value).replace("\\/", "/").replace("&amp;", "&")
                val safe = if (raw.contains(".m3u8") || raw.contains(".mp4") || raw.contains(".mpd")) absolute(base, raw) else embed(absolute(base, raw))
                if (safe.isNotBlank()) links.add(safe)
            }
        }
        links.toList().take(6).forEach { direct(it).takeIf { rows -> rows.isNotEmpty() }?.let { return it } }
        return links.filter { it.contains(".m3u8") || it.contains(".mp4") || it.contains(".mpd") }.associateBy { qualityOf(it) }.toSortedMap()
    }

    private fun decoded(html: String): List<String> {
        val out = ArrayList<String>()
        Regex("[A-Za-z0-9+/=]{40,}").findAll(html).take(24).forEach { m ->
            runCatching { String(Base64.getDecoder().decode(m.value)) }.getOrNull()?.takeIf { it.contains(".m3u8") || it.contains(".mp4") }?.let(out::add)
        }
        return out
    }

    private fun text(url: String, referer: String): String {
        val request = Request.Builder().url(url).header("User-Agent", chrome).header("Accept", "text/html,application/json,*/*").header("Referer", referer).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("network")
            return response.body?.string().orEmpty()
        }
    }

    private fun embed(input: String): String {
        var s = input.trim().replace("&amp;", "&")
        Regex("src=[\"']([^\"']+)", RegexOption.IGNORE_CASE).find(s)?.let { s = it.groupValues[1] }
        if (s.startsWith("//")) s = "https:$s"
        val uri = runCatching { Uri.parse(s) }.getOrNull()
        val host = uri?.host.orEmpty()
        if (host in listOf("aniqit.com", "kodik.info", "kodik.cc", "kodik.biz")) s = uri!!.buildUpon().scheme("https").authority("kodikplayer.com").build().toString()
        return safe(s)
    }

    private fun absolute(base: String, raw: String): String = runCatching {
        val fixed = raw.trim().replace(" ", "%20").replace("\\/", "/").replace("&amp;", "&")
        safe(if (fixed.startsWith("//")) "https:$fixed" else URI(base).resolve(fixed).toString())
    }.getOrDefault("")

    private fun resolve(base: String, child: String): String = runCatching { safe(URI(base).resolve(child).toString()) }.getOrDefault("")
    private fun referer(url: String): String = runCatching { val u = Uri.parse(url); "${u.scheme}://${u.host}/" }.getOrDefault("https://yani.tv/")
    private fun safe(raw: String): String {
        val fixed = raw.trim().replace(" ", "%20").replace("\\/", "/")
        if (!fixed.startsWith("http://") && !fixed.startsWith("https://") && !fixed.startsWith("file:")) return ""
        if (fixed.startsWith("file:")) return fixed
        val host = runCatching { Uri.parse(fixed).host?.lowercase(Locale.ROOT).orEmpty() }.getOrDefault("")
        if (!host.contains('.') || host == "localhost" || host.endsWith(".local")) return ""
        return fixed
    }
    private fun qualityOf(url: String): Int = Regex("(?:^|[^0-9])([1-9][0-9]{2,3})p?(?:[^0-9]|$)").find(url)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0

    companion object { private const val chrome = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Mobile Safari/537.36" }
}
