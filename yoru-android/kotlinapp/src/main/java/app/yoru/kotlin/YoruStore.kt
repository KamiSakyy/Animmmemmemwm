package app.yoru.kotlin

import android.content.Context
import org.json.JSONObject
import java.util.Locale

class YoruStore(context: Context) {
    private val prefs = context.getSharedPreferences("yoru_kotlin_store", Context.MODE_PRIVATE)
    private var favorites = readObject("favorites")
    private var history = readObject("history")
    private var settingsJson = readObject("settings")
    private var downloads = readObject("downloads")

    @Synchronized fun settings(): AppSettings = AppSettings(
        quality = settingsJson.optInt("quality", 720).coerceIn(360, 1080),
        autoNext = settingsJson.optBoolean("autoNext", true),
        dataSaver = settingsJson.optBoolean("dataSaver", false),
        wifiDownloads = settingsJson.optBoolean("wifiDownloads", true),
        spoilerSafe = settingsJson.optBoolean("spoilerSafe", false),
        originalTitles = settingsJson.optBoolean("originalTitles", false),
        autoSkipOpening = settingsJson.optBoolean("autoSkipOpening", true),
        playbackSpeed = settingsJson.optDouble("playbackSpeed", 1.0).toFloat().coerceIn(0.75f, 2.5f),
        fontScale = settingsJson.optInt("fontScale", 100).coerceIn(85, 130),
        fontName = settingsJson.optString("fontName", "Системный"),
        preferredVoice = realVoice(settingsJson.optString("preferredVoice", "AniLibria.TV")).ifBlank { "AniLibria.TV" },
        onlyPreferredVoice = settingsJson.optBoolean("onlyPreferredVoice", true)
    )

    @Synchronized fun updateSettings(value: AppSettings) {
        settingsJson = JSONObject()
            .put("quality", value.quality.coerceIn(360, 1080))
            .put("autoNext", value.autoNext)
            .put("dataSaver", value.dataSaver)
            .put("wifiDownloads", value.wifiDownloads)
            .put("spoilerSafe", value.spoilerSafe)
            .put("originalTitles", value.originalTitles)
            .put("autoSkipOpening", value.autoSkipOpening)
            .put("playbackSpeed", value.playbackSpeed.coerceIn(0.75f, 2.5f).toDouble())
            .put("fontScale", value.fontScale.coerceIn(85, 130))
            .put("fontName", value.fontName.ifBlank { "Системный" })
            .put("preferredVoice", realVoice(value.preferredVoice).ifBlank { "AniLibria.TV" })
            .put("onlyPreferredVoice", value.onlyPreferredVoice)
        writeObject("settings", settingsJson)
    }

    @Synchronized fun favorite(anime: AnimeItem): Boolean = favorites.has(anime.key)

    @Synchronized fun toggleFavorite(anime: AnimeItem) {
        if (anime.id.isBlank()) return
        if (favorites.has(anime.key)) favorites.remove(anime.key) else favorites.put(anime.key, JSONObject().put("anime", anime.toJson()).put("added", System.currentTimeMillis()).put("bucket", "watching"))
        writeObject("favorites", favorites)
    }

    @Synchronized fun favorites(): List<AnimeItem> = jsonRows(favorites, "added") { AnimeItem.fromJson(it.optJSONObject("anime")) }

    @Synchronized fun saveProgress(anime: AnimeItem, episode: Double, position: Int, duration: Int, voice: String) {
        if (anime.id.isBlank()) return
        val cleanPosition = position.coerceAtLeast(0)
        val cleanDuration = duration.coerceAtLeast(0)
        history.put(anime.key, JSONObject()
            .put("anime", anime.toJson())
            .put("episode", episode)
            .put("position", cleanPosition)
            .put("duration", cleanDuration)
            .put("voice", realVoice(voice).ifBlank { "AniLibria.TV" })
            .put("updated", System.currentTimeMillis()))
        writeObject("history", history)
    }

    @Synchronized fun progress(animeId: String): WatchProgress? {
        val row = history.optJSONObject(animeId) ?: return null
        val anime = AnimeItem.fromJson(row.optJSONObject("anime"))
        if (anime.id.isBlank()) return null
        return WatchProgress(
            anime = anime,
            episode = row.optDouble("episode", 1.0),
            position = row.optInt("position", 0),
            duration = row.optInt("duration", 0),
            voice = realVoice(row.optString("voice", "AniLibria.TV")).ifBlank { "AniLibria.TV" },
            updated = row.optLong("updated", 0L)
        )
    }

    @Synchronized fun recent(): List<WatchProgress> = jsonRows(history, "updated") { row ->
        val anime = AnimeItem.fromJson(row.optJSONObject("anime"))
        if (anime.id.isBlank()) null else WatchProgress(anime, row.optDouble("episode", 1.0), row.optInt("position", 0), row.optInt("duration", 0), row.optString("voice", "AniLibria.TV"), row.optLong("updated", 0L))
    }

    @Synchronized fun clearHistory() {
        history = JSONObject()
        writeObject("history", history)
    }

    @Synchronized fun saveDownload(record: DownloadRecord) {
        downloads.put(record.id, record.toJson())
        writeObject("downloads", downloads)
    }

    @Synchronized fun removeDownload(id: String) {
        downloads.remove(id)
        writeObject("downloads", downloads)
    }

    @Synchronized fun download(id: String): DownloadRecord? = DownloadRecord.fromJson(downloads.optJSONObject(id))

    @Synchronized fun downloadFor(animeKey: String, episode: Double): DownloadRecord? = downloadRecords().firstOrNull { it.anime.key == animeKey && kotlin.math.abs(it.episode.number - episode) < 0.001 }

    @Synchronized fun downloadRecords(): List<DownloadRecord> = jsonRows(downloads, "created") { DownloadRecord.fromJson(it) }

    @Synchronized fun stats(): JSONObject = JSONObject()
        .put("favorites", favorites.length())
        .put("history", history.length())
        .put("downloads", downloads.length())
        .put("minutes", recent().sumOf { (it.position / 60).coerceAtLeast(0) })

    @Synchronized fun exportData(): String = JSONObject()
        .put("format", "yoru.kotlin.v2")
        .put("favorites", favorites)
        .put("history", history)
        .put("settings", settingsJson)
        .put("downloads", downloads)
        .toString(2)

    @Synchronized fun importData(input: String): Int {
        if (input.length > 5 * 1024 * 1024) error("Файл слишком большой")
        val root = JSONObject(input)
        val format = root.optString("format")
        if (!format.startsWith("yoru")) error("Выберите файл коллекции YORU")
        root.optJSONObject("favorites")?.let { favorites = sanitizeAnimeMap(it) }
        root.optJSONObject("history")?.let { history = sanitizeHistoryMap(it) }
        root.optJSONObject("settings")?.let { settingsJson = it }
        root.optJSONObject("downloads")?.let { downloads = it }
        writeObject("favorites", favorites)
        writeObject("history", history)
        writeObject("settings", settingsJson)
        writeObject("downloads", downloads)
        return favorites.length() + history.length()
    }

    private fun readObject(name: String): JSONObject = runCatching { JSONObject(prefs.getString(name, "{}") ?: "{}") }.getOrDefault(JSONObject())
    private fun writeObject(name: String, value: JSONObject) { prefs.edit().putString(name, value.toString()).apply() }

    private fun sanitizeAnimeMap(source: JSONObject): JSONObject {
        val out = JSONObject()
        val keys = source.keys()
        while (keys.hasNext() && out.length() < 5000) {
            val key = keys.next()
            val row = source.optJSONObject(key) ?: continue
            val anime = AnimeItem.fromJson(row.optJSONObject("anime") ?: row.optJSONObject("release"))
            if (anime.id.isNotBlank()) out.put(anime.key, JSONObject().put("anime", anime.toJson()).put("added", row.optLong("added", System.currentTimeMillis())).put("bucket", row.optString("bucket", "watching")))
        }
        return out
    }

    private fun sanitizeHistoryMap(source: JSONObject): JSONObject {
        val out = JSONObject()
        val keys = source.keys()
        while (keys.hasNext() && out.length() < 5000) {
            val row = source.optJSONObject(keys.next()) ?: continue
            val anime = AnimeItem.fromJson(row.optJSONObject("anime") ?: row.optJSONObject("release"))
            if (anime.id.isBlank()) continue
            out.put(anime.key, JSONObject()
                .put("anime", anime.toJson())
                .put("episode", row.optDouble("episode", 1.0))
                .put("position", row.optInt("position", row.optInt("time", 0)))
                .put("duration", row.optInt("duration", 0))
                .put("voice", realVoice(row.optString("voice", row.optString("dubbing", "AniLibria.TV"))).ifBlank { "AniLibria.TV" })
                .put("updated", row.optLong("updated", System.currentTimeMillis())))
        }
        return out
    }

    private fun <T> jsonRows(root: JSONObject, timeKey: String, mapper: (JSONObject) -> T?): List<T> {
        val rows = ArrayList<Pair<Long, T>>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val row = root.optJSONObject(keys.next()) ?: continue
            val item = mapper(row) ?: continue
            rows.add(row.optLong(timeKey, 0L) to item)
        }
        return rows.sortedByDescending { it.first }.map { it.second }
    }
}
