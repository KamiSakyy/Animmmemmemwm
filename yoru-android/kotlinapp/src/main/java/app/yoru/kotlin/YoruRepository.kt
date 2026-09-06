package app.yoru.kotlin

import android.app.Application
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

class YoruRepository(private val app: Application) {
    private val client = OkHttpClient.Builder()
        .cache(Cache(File(app.cacheDir, "http-v2"), 96L * 1024L * 1024L))
        .connectTimeout(2500, TimeUnit.MILLISECONDS)
        .readTimeout(6500, TimeUnit.MILLISECONDS)
        .callTimeout(9000, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val detailMemory = linkedMapOf<String, AnimeDetail>()

    suspend fun catalog(query: String, filter: CatalogFilter = CatalogFilter(), limit: Int = 36): List<AnimeItem> = withContext(Dispatchers.IO) {
        val url = Uri.parse("https://anilibria.top/api/v1/anime/catalog/releases").buildUpon()
            .appendQueryParameter("limit", limit.coerceIn(12, 48).toString())
            .appendQueryParameter("page", "1")
            .appendQueryParameter("f[sorting]", filter.sort)
            .apply {
                if (query.isNotBlank()) appendQueryParameter("f[search]", query.trim())
                if (filter.type.isNotBlank()) appendQueryParameter("f[types]", filter.type)
                if (filter.status.isNotBlank()) appendQueryParameter("f[publish_statuses]", filter.status)
                if (filter.year > 0) {
                    appendQueryParameter("f[years][from_year]", filter.year.toString())
                    appendQueryParameter("f[years][to_year]", filter.year.toString())
                }
            }
            .build()
            .toString()
        val rows = json(url).optJSONArray("data")
        buildList {
            for (i in 0 until (rows?.length() ?: 0)) rows?.optJSONObject(i)?.let { add(parseAnime(it)) }
        }.distinctBy { it.id }
    }

    suspend fun details(id: String): AnimeDetail = withContext(Dispatchers.IO) {
        detailMemory[id]?.let { return@withContext it }
        val root = json("https://anilibria.top/api/v1/anime/releases/${enc(id)}")
        val anime = parseAnime(root)
        val episodes = parseEpisodes(root, anime)
        val related = parseRelated(root).filter { it.id != anime.id }.sortedWith(compareBy<AnimeItem> { if (it.year > 0) it.year else 9999 }.thenBy { it.title }).take(36)
        val similar = similarFast(anime).filter { it.id != anime.id }.take(12)
        val detail = AnimeDetail(anime = anime, episodes = episodes, related = related, similar = similar)
        detailMemory[id] = detail
        while (detailMemory.size > 32) detailMemory.remove(detailMemory.keys.first())
        detail
    }

    suspend fun prewarm(url: String) = withContext(Dispatchers.IO) {
        runCatching {
            if (url.isBlank()) return@runCatching
            val request = Request.Builder().url(url).header("Range", "bytes=0-2048").header("User-Agent", ua).build()
            client.newCall(request).execute().use { it.body?.bytes() }
        }
    }

    suspend fun saveEpisode(anime: AnimeItem, episode: EpisodeItem, quality: Int, onProgress: (Float) -> Unit): DownloadRecord = withContext(Dispatchers.IO) {
        val stream = episode.streams[quality] ?: episode.streams.entries.sortedByDescending { it.key }.firstOrNull()?.value ?: error("Серия недоступна")
        val id = digest("${anime.id}:${episode.number}:$quality:${System.currentTimeMillis()}").take(16)
        val dir = File(app.filesDir, "offline/$id")
        if (dir.exists()) dir.deleteRecursively()
        dir.mkdirs()
        val first = text(stream)
        val resolved = resolveMediaPlaylist(stream, first, quality)
        val playlistUrl = resolved.first
        val playlist = resolved.second
        val lines = playlist.lineSequence().toList()
        val mediaLines = lines.count { it.trim().isNotBlank() && !it.trim().startsWith("#") }
        var done = 0
        var bytes = 0L
        val output = ArrayList<String>(lines.size)
        for ((index, rawLine) in lines.withIndex()) {
            val line = rawLine.trim()
            if (line.startsWith("#EXT-X-KEY") && line.contains("URI=", true)) {
                output.add(saveKey(line, playlistUrl, dir))
                continue
            }
            if (line.isNotBlank() && !line.startsWith("#")) {
                val source = resolve(playlistUrl, line)
                val name = "s${index.toString().padStart(5, '0')}.${extension(source)}"
                val out = File(dir, name)
                bytes += download(source, out)
                done++
                if (mediaLines > 0) onProgress(done / mediaLines.toFloat())
                output.add(name)
            } else {
                output.add(rawLine)
            }
        }
        val playlistFile = File(dir, "index.m3u8")
        playlistFile.writeText(output.joinToString("\n"), Charsets.UTF_8)
        onProgress(1f)
        DownloadRecord(
            id = id,
            anime = anime,
            episode = episode.copy(streams = mapOf(quality to playlistFile.toURI().toString())),
            quality = quality,
            voice = episode.voice,
            path = playlistFile.absolutePath,
            bytes = bytes
        )
    }

    private fun resolveMediaPlaylist(url: String, playlist: String, quality: Int): Pair<String, String> {
        if (!playlist.contains("#EXT-X-STREAM-INF", true)) return url to playlist
        val rows = playlist.lines()
        val variants = ArrayList<Pair<Int, String>>()
        var lastRes = 0
        for (line in rows) {
            val trim = line.trim()
            if (trim.startsWith("#EXT-X-STREAM-INF", true)) lastRes = Regex("RESOLUTION=\\d+x(\\d+)").find(trim)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            else if (trim.isNotBlank() && !trim.startsWith("#")) variants.add(lastRes to resolve(url, trim))
        }
        val chosen = variants.sortedBy { kotlin.math.abs((it.first.takeIf { q -> q > 0 } ?: quality) - quality) }.firstOrNull()?.second ?: return url to playlist
        return chosen to text(chosen)
    }

    private fun saveKey(line: String, playlistUrl: String, dir: File): String {
        val match = Regex("URI=\"([^\"]+)\"").find(line) ?: Regex("URI=([^,]+)").find(line) ?: return line
        val keyUrl = resolve(playlistUrl, match.groupValues[1].trim('"'))
        val name = "key${digest(keyUrl).take(8)}.bin"
        download(keyUrl, File(dir, name))
        return line.replace(match.groupValues[1], name)
    }

    private fun download(url: String, out: File): Long {
        val request = Request.Builder().url(url).header("User-Agent", ua).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("network")
            val body = response.body ?: error("network")
            out.outputStream().use { sink -> body.byteStream().copyTo(sink) }
        }
        return out.length()
    }

    private suspend fun similarFast(anime: AnimeItem): List<AnimeItem> {
        val firstGenre = anime.genres.firstOrNull().orEmpty()
        return runCatching { catalog(firstGenre, CatalogFilter(sort = "RATING_DESC"), 12) }.getOrDefault(emptyList())
    }

    private fun parseAnime(j: JSONObject): AnimeItem {
        val name = j.optJSONObject("name")
        val poster = j.optJSONObject("poster")
        val type = j.optJSONObject("type")
        val age = j.optJSONObject("age_rating")
        val rating = j.optJSONObject("rating")
        val genres = j.optJSONArray("genres").strings("name", "title", limit = 16)
        val screenshots = imageArray(j.optJSONArray("screenshots")) + imageArray(j.optJSONArray("images")) + listOfNotNull(absolute(bestImage(j.optJSONObject("screenshot") ?: JSONObject()))).filter { it.isNotBlank() }
        val trailer = firstUrl(j, "trailer", "trailer_url", "youtube", "youtube_url", "video")
        val status = when {
            j.optBoolean("is_ongoing", false) -> "Сейчас выходит"
            j.optString("publish_status").contains("anons", true) -> "Анонсирован"
            else -> "Завершён"
        }
        return AnimeItem(
            id = j.optString("id", j.optString("alias", "")),
            title = name?.optString("main")?.takeIf { it.isNotBlank() } ?: j.optString("title", j.optString("name", "Аниме")),
            original = name?.optString("english")?.takeIf { it.isNotBlank() } ?: j.optString("english", ""),
            alias = j.optString("alias", ""),
            poster = posterUrl(poster, j),
            year = j.optInt("year", yearFrom(j.optString("release_date", j.optString("aired_on", "")))),
            type = type?.optString("description")?.takeIf { it.isNotBlank() } ?: j.optString("type", "Аниме"),
            episodes = j.optInt("episodes_total", j.optInt("episodes_count", j.optInt("episodes", 0))),
            status = status,
            age = age?.optString("label") ?: j.optString("age", ""),
            score = rating?.optDouble("average", 0.0)?.takeIf { it.isFinite() && it > 0.0 } ?: j.optDouble("rating", j.optDouble("score", 0.0)).takeIf { it.isFinite() } ?: 0.0,
            description = strip(j.optString("description", j.optString("annotation", ""))).take(1400),
            genres = genres,
            screenshots = screenshots.distinct().take(12),
            trailerUrl = safe(trailer)
        )
    }

    private fun parseEpisodes(root: JSONObject, anime: AnimeItem): List<EpisodeItem> {
        val rows = root.optJSONArray("episodes") ?: JSONArray()
        val out = ArrayList<EpisodeItem>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val streams = linkedMapOf<Int, String>()
            putStream(streams, 360, row.optString("hls_360"))
            putStream(streams, 480, row.optString("hls_480"))
            putStream(streams, 720, row.optString("hls_720"))
            putStream(streams, 1080, row.optString("hls_1080"))
            val opening = row.optJSONObject("opening")
            val ep = EpisodeItem(
                id = row.optString("id", "${anime.id}-$i"),
                number = row.optDouble("ordinal", row.optDouble("number", (i + 1).toDouble())).takeIf { it.isFinite() } ?: (i + 1).toDouble(),
                title = row.optString("name", row.optString("title", "")),
                duration = row.optInt("duration", 0),
                poster = absolute(bestImage(row)).ifBlank { anime.screenshots.getOrNull(i % anime.screenshots.size.coerceAtLeast(1)).orEmpty().ifBlank { anime.poster } },
                voice = "AniLibria.TV",
                streams = streams,
                openingStart = opening?.optInt("start", 0) ?: 0,
                openingEnd = opening?.optInt("stop", 0) ?: 0
            )
            out.add(ep)
        }
        return out.sortedBy { it.number }
    }

    private fun parseRelated(root: JSONObject): List<AnimeItem> {
        val out = LinkedHashMap<String, AnimeItem>()
        fun scanArray(array: JSONArray?) {
            for (i in 0 until (array?.length() ?: 0)) {
                val row = array?.optJSONObject(i) ?: continue
                val release = row.optJSONObject("release") ?: row.optJSONObject("anime") ?: row
                val item = parseAnime(release)
                if (item.id.isNotBlank()) out[item.id] = item
                scanArray(row.optJSONArray("releases"))
            }
        }
        scanArray(root.optJSONArray("related"))
        scanArray(root.optJSONArray("franchises"))
        root.optJSONObject("franchise")?.let { scanArray(it.optJSONArray("releases")) }
        return out.values.toList()
    }

    private fun posterUrl(poster: JSONObject?, source: JSONObject): String {
        val optimized = poster?.optJSONObject("optimized")
        val raw = optimized?.optString("preview")?.takeIf { it.isNotBlank() }
            ?: optimized?.optString("src")?.takeIf { it.isNotBlank() }
            ?: poster?.optString("preview")?.takeIf { it.isNotBlank() }
            ?: poster?.optString("src")?.takeIf { it.isNotBlank() }
            ?: bestImage(source)
        return absolute(raw)
    }

    private fun putStream(map: MutableMap<Int, String>, quality: Int, raw: String) {
        val url = safe(raw)
        if (url.isNotBlank()) map[quality] = url
    }

    private fun json(url: String): JSONObject = JSONObject(text(url))
    private fun text(url: String): String {
        val request = Request.Builder().url(url).header("Accept", "application/json,*/*").header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.5").header("User-Agent", ua).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("network")
            return response.body?.string().orEmpty()
        }
    }

    private fun bestImage(j: JSONObject?): String {
        if (j == null) return ""
        val keys = arrayOf("preview", "preview_url", "thumbnail", "thumbnail_url", "screenshot", "screenshot_url", "image", "image_url", "poster", "poster_url", "frame", "frame_url", "cover", "cover_url", "src")
        for (key in keys) {
            val s = j.optString(key, "")
            if (s.isNotBlank()) return s
            val o = j.optJSONObject(key)
            if (o != null) bestImage(o).takeIf { it.isNotBlank() }?.let { return it }
        }
        return ""
    }

    private fun imageArray(array: JSONArray?): List<String> {
        val out = ArrayList<String>()
        for (i in 0 until (array?.length() ?: 0)) {
            val raw = when (val value = array?.opt(i)) {
                is JSONObject -> bestImage(value)
                else -> value?.toString().orEmpty()
            }
            val url = absolute(raw)
            if (url.isNotBlank()) out.add(url)
        }
        return out
    }

    private fun firstUrl(j: JSONObject, vararg keys: String): String {
        for (key in keys) {
            val raw = j.optString(key, "")
            if (raw.isNotBlank()) return raw
            val o = j.optJSONObject(key)
            if (o != null) firstUrl(o, "url", "src", "player", "youtube").takeIf { it.isNotBlank() }?.let { return it }
        }
        return ""
    }

    private fun absolute(raw: String): String {
        if (raw.isBlank()) return ""
        val fixed = raw.trim().replace("\\/", "/")
        return safe(when {
            fixed.startsWith("//") -> "https:$fixed"
            fixed.startsWith("/") -> "https://anilibria.top$fixed"
            else -> fixed
        })
    }

    private fun safe(raw: String): String {
        val fixed = raw.trim().replace(" ", "%20").replace("\\/", "/").replace("&amp;", "&")
        if (!fixed.startsWith("http://") && !fixed.startsWith("https://") && !fixed.startsWith("file:")) return ""
        val uri = runCatching { Uri.parse(fixed) }.getOrNull() ?: return ""
        if (fixed.startsWith("file:")) return fixed
        val host = uri.host?.lowercase(Locale.ROOT) ?: return ""
        if (!host.contains('.') || host == "localhost" || host.endsWith(".local")) return ""
        return fixed
    }

    private fun strip(value: String): String = value.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
    private fun yearFrom(value: String): Int = Regex("(19|20)\\d{2}").find(value)?.value?.toIntOrNull() ?: 0
    private fun resolve(base: String, child: String): String = runCatching { URI(base).resolve(child).toString() }.getOrElse { child }
    private fun extension(url: String): String = URI(url).path.substringAfterLast('.', "ts").take(5).ifBlank { "ts" }
    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object { private const val ua = "YORU-Kotlin/1.1 Android" }
}

private fun JSONArray?.strings(vararg keys: String, limit: Int): List<String> {
    val out = ArrayList<String>()
    for (i in 0 until (this?.length() ?: 0)) {
        val item = this?.opt(i)
        val value = if (item is JSONObject) keys.firstNotNullOfOrNull { item.optString(it).takeIf(String::isNotBlank) }.orEmpty() else item?.toString().orEmpty()
        if (value.isNotBlank() && out.none { it.equals(value, true) }) out.add(value)
        if (out.size >= limit) break
    }
    return out
}
