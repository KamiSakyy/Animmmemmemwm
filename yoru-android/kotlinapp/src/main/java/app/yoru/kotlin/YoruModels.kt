package app.yoru.kotlin

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private const val voiceDefault = "AniLibria.TV"

data class AnimeItem(
    val id: String,
    val title: String,
    val original: String = "",
    val alias: String = "",
    val poster: String = "",
    val year: Int = 0,
    val type: String = "Аниме",
    val episodes: Int = 0,
    val status: String = "",
    val age: String = "",
    val score: Double = 0.0,
    val description: String = "",
    val genres: List<String> = emptyList(),
    val screenshots: List<String> = emptyList(),
    val trailerUrl: String = ""
) {
    val key: String get() = id
    fun displayTitle(originalTitles: Boolean = false): String = if (originalTitles && original.isNotBlank()) original else title
    fun meta(): String = listOfNotNull(year.takeIf { it > 0 }?.toString(), type.takeIf { it.isNotBlank() }, episodes.takeIf { it > 0 }?.let { "$it сер." }).joinToString(" · ")
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("original", original)
        .put("alias", alias)
        .put("poster", poster)
        .put("year", year)
        .put("type", type)
        .put("episodes", episodes)
        .put("status", status)
        .put("age", age)
        .put("score", score)
        .put("description", description)
        .put("genres", JSONArray(genres))
        .put("screenshots", JSONArray(screenshots))
        .put("trailerUrl", trailerUrl)

    companion object {
        fun fromJson(j: JSONObject?): AnimeItem {
            if (j == null) return AnimeItem("", "Аниме")
            val rawId = j.optString("nativeId").ifBlank { j.optString("id") }
            val id = if (rawId.contains(":")) rawId.substringAfter(":") else rawId
            val score = j.optDouble("score", j.optDouble("ratingScore", 0.0)).takeIf { it.isFinite() } ?: 0.0
            return AnimeItem(
                id = id,
                title = j.optString("title", "Аниме"),
                original = j.optString("original", j.optString("english", "")),
                alias = j.optString("alias", ""),
                poster = j.optString("poster", ""),
                year = j.optInt("year", 0),
                type = j.optString("type", "Аниме"),
                episodes = j.optInt("episodes", j.optInt("episodesTotal", 0)),
                status = j.optString("status", ""),
                age = j.optString("age", ""),
                score = score,
                description = j.optString("description", ""),
                genres = j.optJSONArray("genres").strings(30),
                screenshots = j.optJSONArray("screenshots").strings(12),
                trailerUrl = j.optString("trailerUrl", "")
            )
        }
    }
}

data class EpisodeItem(
    val id: String,
    val number: Double,
    val title: String = "",
    val duration: Int = 0,
    val poster: String = "",
    val voice: String = voiceDefault,
    val streams: Map<Int, String> = emptyMap(),
    val openingStart: Int = 0,
    val openingEnd: Int = 0
) {
    fun label(spoilerSafe: Boolean = false): String {
        val cleanTitle = if (spoilerSafe) "" else title
        return "Серия ${numberLabel(number)}" + if (cleanTitle.isBlank()) "" else " · $cleanTitle"
    }

    fun toJson(): JSONObject {
        val s = JSONObject()
        streams.forEach { (q, u) -> s.put(q.toString(), u) }
        return JSONObject()
            .put("id", id)
            .put("number", number)
            .put("title", title)
            .put("duration", duration)
            .put("poster", poster)
            .put("voice", voice)
            .put("streams", s)
            .put("openingStart", openingStart)
            .put("openingEnd", openingEnd)
    }

    companion object {
        fun fromJson(j: JSONObject?): EpisodeItem {
            val streams = linkedMapOf<Int, String>()
            val raw = j?.optJSONObject("streams")
            val keys = raw?.keys()
            while (keys != null && keys.hasNext()) {
                val key = keys.next()
                val q = key.toIntOrNull() ?: continue
                val url = raw.optString(key)
                if (url.isNotBlank()) streams[q] = url
            }
            return EpisodeItem(
                id = j?.optString("id") ?: "",
                number = j?.optDouble("number", 1.0) ?: 1.0,
                title = j?.optString("title", "") ?: "",
                duration = j?.optInt("duration", 0) ?: 0,
                poster = j?.optString("poster", "") ?: "",
                voice = realVoice(j?.optString("voice", voiceDefault) ?: voiceDefault),
                streams = streams,
                openingStart = j?.optInt("openingStart", 0) ?: 0,
                openingEnd = j?.optInt("openingEnd", 0) ?: 0
            )
        }
    }
}

data class AnimeDetail(
    val anime: AnimeItem,
    val episodes: List<EpisodeItem>,
    val related: List<AnimeItem> = emptyList(),
    val similar: List<AnimeItem> = emptyList()
)

data class CatalogFilter(
    val type: String = "",
    val status: String = "",
    val year: Int = 0,
    val sort: String = "RATING_DESC"
)

data class CatalogState(
    val query: String = "",
    val filter: CatalogFilter = CatalogFilter(),
    val loading: Boolean = false,
    val items: List<AnimeItem> = emptyList(),
    val error: String = ""
)

data class DetailState(
    val loading: Boolean = true,
    val detail: AnimeDetail? = null,
    val error: String = ""
)

data class AppSettings(
    val quality: Int = 720,
    val autoNext: Boolean = true,
    val dataSaver: Boolean = false,
    val wifiDownloads: Boolean = true,
    val spoilerSafe: Boolean = false,
    val originalTitles: Boolean = false,
    val autoSkipOpening: Boolean = true,
    val playbackSpeed: Float = 1f,
    val fontScale: Int = 100,
    val fontName: String = "Системный",
    val preferredVoice: String = voiceDefault,
    val onlyPreferredVoice: Boolean = true
)

data class WatchProgress(
    val anime: AnimeItem,
    val episode: Double,
    val position: Int = 0,
    val duration: Int = 0,
    val voice: String = voiceDefault,
    val updated: Long = System.currentTimeMillis()
)

data class DownloadRecord(
    val id: String,
    val anime: AnimeItem,
    val episode: EpisodeItem,
    val quality: Int,
    val voice: String,
    val path: String,
    val bytes: Long,
    val created: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("anime", anime.toJson())
        .put("episode", episode.toJson())
        .put("quality", quality)
        .put("voice", realVoice(voice))
        .put("path", path)
        .put("bytes", bytes)
        .put("created", created)

    companion object {
        fun fromJson(j: JSONObject?): DownloadRecord? {
            if (j == null) return null
            val anime = AnimeItem.fromJson(j.optJSONObject("anime"))
            if (anime.id.isBlank()) return null
            return DownloadRecord(
                id = j.optString("id"),
                anime = anime,
                episode = EpisodeItem.fromJson(j.optJSONObject("episode")),
                quality = j.optInt("quality", 720),
                voice = realVoice(j.optString("voice", voiceDefault)),
                path = j.optString("path"),
                bytes = j.optLong("bytes", 0L),
                created = j.optLong("created", System.currentTimeMillis())
            )
        }
    }
}

fun numberLabel(value: Double): String = if (value == value.toInt().toDouble()) value.toInt().toString() else String.format(Locale.US, "%.1f", value).trimEnd('0').trimEnd('.')
fun durationLabel(seconds: Int): String = if (seconds <= 0) "Видео-превью" else "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
fun bytesLabel(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f ГБ", bytes / 1024.0 / 1024.0 / 1024.0)
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.0f МБ", bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> String.format(Locale.US, "%.0f КБ", bytes / 1024.0)
    else -> "$bytes Б"
}
fun qualityName(q: Int): String = if (q > 0) "${q}p" else "AUTO"
fun estimateSize(quality: Int, duration: Int): String {
    val minutes = if (duration > 0) duration / 60.0 else 24.0
    val mbPerMinute = when {
        quality <= 360 -> 3.8
        quality <= 480 -> 6.0
        quality <= 720 -> 11.0
        else -> 19.0
    }
    return "≈ ${String.format(Locale.US, "%.0f", minutes * mbPerMinute)} МБ"
}
fun realVoice(raw: String): String {
    val v = raw.trim()
    val low = v.lowercase(Locale.ROOT).replace('ё', 'е')
    if (low.contains("anilibria") || low.contains("aniliberty") || low.contains("анилибри") || low.contains("анилиберт")) return voiceDefault
    if (low.contains("animevost") || low.contains("анимевост")) return "AnimeVost"
    if (low.contains("anidub") || low.contains("анидаб")) return "AniDUB"
    if (low.contains("animaunt") || low.contains("анимаунт")) return "AniMaunt"
    if (low.contains("beyond") && low.contains("studio")) return "Beyond:Studio"
    if (low.contains("dream") && low.contains("cast")) return "Dream Cast"
    if (low.contains("anistar") && low.contains("deep")) return "AniStar & DEEP"
    if (low.contains("animedia") || low.contains("анимедиа")) return "AniMedia"
    if (low.contains("sub") || low.contains("суб")) return "Субтитры"
    val hidden = listOf("yoru", "yummy", "player", "iframe", "route", "source", "dash", "hls", "internal")
    if (hidden.any { low.contains(it) }) return ""
    return v.take(48)
}
private fun JSONArray?.strings(limit: Int): List<String> {
    val out = ArrayList<String>()
    if (this == null) return out
    for (i in 0 until length()) {
        val item = opt(i)
        val value = when (item) {
            is JSONObject -> item.optString("name", item.optString("title", item.optString("russian", "")))
            else -> item?.toString().orEmpty()
        }.trim()
        if (value.isNotBlank() && out.none { it.equals(value, true) }) out.add(value)
        if (out.size >= limit) break
    }
    return out
}
