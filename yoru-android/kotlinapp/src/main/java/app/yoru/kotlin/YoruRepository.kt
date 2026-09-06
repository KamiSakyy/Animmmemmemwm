package app.yoru.kotlin

import android.app.Application
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class YoruRepository(private val app: Application) {
    private val client = OkHttpClient.Builder()
        .cache(Cache(File(app.cacheDir, "http-v3"), 128L * 1024L * 1024L))
        .connectTimeout(2200, TimeUnit.MILLISECONDS)
        .readTimeout(6500, TimeUnit.MILLISECONDS)
        .callTimeout(10500, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val engine = YoruSourceEngine(client)

    suspend fun catalog(query: String, filter: CatalogFilter = CatalogFilter(), limit: Int = 36): List<AnimeItem> = withContext(Dispatchers.IO) {
        engine.catalog(query, filter, limit).distinctBy { it.key }
    }

    suspend fun details(key: String, settings: AppSettings = AppSettings()): AnimeDetail = withContext(Dispatchers.IO) {
        engine.details(key, settings.preferredVoice, settings.onlyPreferredVoice)
    }

    suspend fun resolveVariant(variant: PlaybackVariant, preferredQuality: Int): PlaybackVariant = withContext(Dispatchers.IO) {
        engine.resolveVariant(variant, preferredQuality)
    }

    suspend fun prewarm(url: String) = withContext(Dispatchers.IO) {
        runCatching {
            if (url.isBlank() || url.startsWith("file:")) return@runCatching
            val request = Request.Builder().url(url).header("Range", "bytes=0-2048").header("User-Agent", ua).build()
            client.newCall(request).execute().use { it.body?.bytes() }
        }
    }

    suspend fun saveEpisode(
        anime: AnimeItem,
        episode: EpisodeItem,
        quality: Int,
        voice: String,
        onProgress: (Float) -> Unit
    ): DownloadRecord = withContext(Dispatchers.IO) {
        val variant = episode.playableVariants().firstOrNull { voiceMatches(voice, it.voice) } ?: episode.playableVariants().firstOrNull() ?: error("Серия недоступна")
        val resolvedVariant = resolveForBlocking(variant, quality)
        val stream = chooseStream(resolvedVariant.streams, quality) ?: error("Серия недоступна")
        val id = digest("${anime.key}:${episode.number}:$quality:${realVoice(resolvedVariant.voice)}:${System.currentTimeMillis()}").take(16)
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
        val localStreams = mapOf(quality to playlistFile.toURI().toString())
        val localVariant = resolvedVariant.copy(streams = localStreams)
        DownloadRecord(
            id = id,
            anime = anime,
            episode = episode.copy(streams = localStreams, voice = realVoice(localVariant.voice).ifBlank { voice }, variants = listOf(localVariant)),
            quality = quality,
            voice = realVoice(localVariant.voice).ifBlank { voice },
            path = playlistFile.absolutePath,
            bytes = bytes
        )
    }

    private suspend fun resolveForBlocking(variant: PlaybackVariant, quality: Int): PlaybackVariant = if (variant.streams.isNotEmpty()) variant else engine.resolveVariant(variant, quality)

    private fun chooseStream(streams: Map<Int, String>, quality: Int): String? = streams.entries
        .filter { it.value.isNotBlank() }
        .sortedWith(compareBy<Map.Entry<Int, String>> { if (it.key <= 0) 10000 else abs(it.key - quality) }.thenByDescending { it.key })
        .firstOrNull()?.value

    private fun resolveMediaPlaylist(url: String, playlist: String, quality: Int): Pair<String, String> {
        if (!playlist.contains("#EXT-X-STREAM-INF", true)) return url to playlist
        val variants = ArrayList<Pair<Int, String>>()
        var lastRes = 0
        for (line in playlist.lines()) {
            val trim = line.trim()
            if (trim.startsWith("#EXT-X-STREAM-INF", true)) lastRes = Regex("RESOLUTION=\\d+x(\\d+)").find(trim)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            else if (trim.isNotBlank() && !trim.startsWith("#")) variants.add(lastRes to resolve(url, trim))
        }
        val chosen = variants.sortedWith(compareBy<Pair<Int, String>> { abs((it.first.takeIf { q -> q > 0 } ?: quality) - quality) }.thenByDescending { it.first }).firstOrNull()?.second ?: return url to playlist
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

    private fun text(url: String): String {
        val request = Request.Builder().url(url).header("Accept", "application/vnd.apple.mpegurl,video/*,*/*").header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.5").header("User-Agent", ua).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("network")
            return response.body?.string().orEmpty()
        }
    }

    private fun resolve(base: String, child: String): String = runCatching { URI(base).resolve(child).toString() }.getOrElse { child }
    private fun extension(url: String): String = runCatching { URI(url).path.substringAfterLast('.', "ts").lowercase(Locale.ROOT).take(5).ifBlank { "ts" } }.getOrDefault("ts")
    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object { private const val ua = "YORU-Kotlin/1.2 Android" }
}
