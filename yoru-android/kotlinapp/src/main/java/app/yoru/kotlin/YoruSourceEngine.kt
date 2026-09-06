package app.yoru.kotlin

import android.net.Uri
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class YoruSourceEngine(private val client: OkHttpClient) {
    private val memory = linkedMapOf<String, AnimeDetail>()
    private val shellMemory = linkedMapOf<String, AnimeItem>()
    private var kodikToken = ""
    private var kodikTokenAt = 0L

    suspend fun catalog(query: String, filter: CatalogFilter, limit: Int): List<AnimeItem> = coroutineScope {
        val source = filter.source.ifBlank { "yoru" }
        if (source != "yoru") return@coroutineScope runCatching { catalogSource(source, query, filter, limit) }.getOrDefault(emptyList())
        val order = if (query.isBlank()) listOf("anilibria", "shikimori", "animelib", "animevost") else listOf("anilibria", "animelib", "animevost", "yummy", "animedia", "animetka", "anidub", "shikimori")
        val timeout = if (query.isBlank()) 5200L else 6800L
        val jobs = order.map { src -> async { runCatching { catalogSource(src, query, filter.copy(source = src), min(24, limit)) }.getOrDefault(emptyList()) } }
        val rows = withTimeoutOrNull(timeout) { jobs.awaitAll().flatten() } ?: jobs.filter { it.isCompleted && !it.isCancelled }.flatMap { runCatching { it.await() }.getOrDefault(emptyList()) }
        mergeCatalog(rows, limit)
    }

    suspend fun details(key: String, preferredVoice: String, strictVoice: Boolean): AnimeDetail = coroutineScope {
        val parsed = parseKey(key)
        val memoryKey = parsed.first + ":" + parsed.second + ":" + voiceKey(preferredVoice) + ":" + strictVoice
        memory[memoryKey]?.let { return@coroutineScope it }
        val base = detailSource(parsed.first, parsed.second, episodes = true)
        if (parsed.first != "yoru") {
            val detail = enrich(base)
            remember(memoryKey, detail)
            return@coroutineScope detail
        }
        val candidates = listOf("yummy", "anilibria", "animevost", "animelib", "animedia", "animetka", "anidub", "kodik", "anixsekai")
        val terms = searchTerms(base.anime)
        val jobs = candidates.map { src -> async { runCatching { findAndDetail(src, base.anime, terms) }.getOrNull() } }
        val found = withTimeoutOrNull(11500) { jobs.awaitAll().filterNotNull() } ?: jobs.filter { it.isCompleted && !it.isCancelled }.mapNotNull { runCatching { it.await() }.getOrNull() }
        val merged = mergeDetails(base, found, preferredVoice, strictVoice)
        val detail = enrich(merged)
        remember(memoryKey, detail)
        detail
    }

    suspend fun resolveVariant(variant: PlaybackVariant, preferredQuality: Int): PlaybackVariant {
        if (variant.streams.isNotEmpty()) return variant
        val streams = when (variant.source) {
            "animelib" -> resolveAnimelibVariant(variant.resolverUrl)
            "animedia" -> resolveAnimediaVariant(variant.resolverUrl)
            "anidub" -> YoruVideoResolver(client).resolve(variant.resolverUrl)
            "kodik", "yummy", "animetka", "anixsekai" -> YoruVideoResolver(client).resolve(variant.resolverUrl)
            else -> YoruVideoResolver(client).resolve(variant.resolverUrl)
        }
        return variant.copy(streams = sortQualities(streams, preferredQuality))
    }

    private fun remember(key: String, detail: AnimeDetail) {
        memory[key] = detail
        while (memory.size > 36) memory.remove(memory.keys.first())
    }

    private fun catalogSource(source: String, query: String, filter: CatalogFilter, limit: Int): List<AnimeItem> = when (source) {
        "anilibria" -> catalogLibria(query, filter, limit)
        "animevost" -> catalogVost(query, limit)
        "animelib", "animelib4k" -> catalogAnimelib(query, filter, limit, source == "animelib4k")
        "yummy" -> catalogYummy(query, filter, limit)
        "shikimori" -> catalogShiki(query, filter, limit)
        "animedia" -> catalogAnimedia(query, limit)
        "animetka" -> catalogAnimetka(query, limit)
        "anidub" -> catalogAnidub(query, limit)
        "kodik" -> catalogShiki(query, filter, limit).map { kodikShell(it) }
        "anixsekai" -> catalogAnix(query, filter, limit)
        else -> catalogLibria(query, filter, limit)
    }

    private fun detailSource(source: String, id: String, episodes: Boolean): AnimeDetail = when (source) {
        "yoru" -> yoruShell(id)
        "anilibria" -> detailLibria(id)
        "animevost" -> detailVost(id, episodes)
        "animelib", "animelib4k" -> detailAnimelib(source, id, episodes)
        "yummy" -> detailYummy(id, episodes)
        "shikimori" -> detailShiki(id)
        "animedia" -> detailAnimedia(id, episodes)
        "animetka" -> detailAnimetka(id, episodes)
        "anidub" -> detailAnidub(id, episodes)
        "kodik" -> detailKodik(id, episodes)
        "anixsekai" -> detailAnix(id, episodes)
        else -> detailLibria(id)
    }

    private fun yoruShell(id: String): AnimeDetail {
        val clean = id.substringAfter("yoru:", id)
        shellMemory["yoru:$clean"]?.let { return AnimeDetail(it, emptyList()) }
        val mal = clean.removePrefix("mal-").toIntOrNull() ?: clean.toIntOrNull()
        if (mal != null && mal > 0) return runCatching { detailShiki(mal.toString()).let { it.copy(anime = it.anime.copy(source = "yoru", id = clean)) } }.getOrDefault(AnimeDetail(AnimeItem(id = clean, title = "YORU", source = "yoru", malId = mal), emptyList()))
        return AnimeDetail(AnimeItem(id = clean, title = "YORU", source = "yoru"), emptyList())
    }

    private fun findAndDetail(source: String, base: AnimeItem, terms: List<String>): AnimeDetail? {
        val direct = if (base.source == source) runCatching { detailSource(source, base.id, true) }.getOrNull() else null
        if (direct != null && direct.episodes.isNotEmpty()) return direct
        for (term in terms) {
            val rows = runCatching { catalogSource(source, term, CatalogFilter(source = source), 12) }.getOrDefault(emptyList())
            val match = rows.firstOrNull { matches(it, base) } ?: rows.firstOrNull()
            if (match != null) {
                val detail = runCatching { detailSource(match.source, match.id, true) }.getOrNull()
                if (detail != null && detail.episodes.isNotEmpty()) return detail
            }
        }
        return null
    }

    private fun mergeDetails(base: AnimeDetail, found: List<AnimeDetail>, preferredVoice: String, strictVoice: Boolean): AnimeDetail {
        val meta = found.fold(base.anime) { acc, next -> absorb(acc, next.anime) }
        val map = linkedMapOf<String, EpisodeItem>()
        (listOf(base) + found).forEach { detail ->
            detail.episodes.forEach { episode ->
                val key = numberLabel(episode.number)
                val old = map[key]
                val variants = ArrayList<PlaybackVariant>()
                if (old != null) variants.addAll(old.playableVariants())
                variants.addAll(episode.playableVariants().map { v ->
                    val clean = realVoice(v.voice).ifBlank { sourceVoice(detail.anime.source) }
                    v.copy(voice = clean, route = v.route.ifBlank { sourceLabel(detail.anime.source) }, source = v.source.ifBlank { detail.anime.source })
                })
                val filtered = variants
                    .filter { !strictVoice || voiceMatches(preferredVoice, it.voice) }
                    .distinctBy { voiceKey(it.voice) + "|" + it.source + "|" + it.resolverUrl + "|" + it.streams.values.firstOrNull().orEmpty() }
                    .sortedWith(compareBy<PlaybackVariant> { variantRank(it) }.thenByDescending { it.streams.keys.maxOrNull() ?: 0 })
                    .let { if (strictVoice) it.take(3) else it.take(16) }
                if (filtered.isEmpty()) return@forEach
                map[key] = EpisodeItem(
                    id = "yoru-$key",
                    number = episode.number,
                    title = old?.title?.ifBlank { episode.title } ?: episode.title,
                    duration = max(old?.duration ?: 0, episode.duration),
                    poster = old?.poster?.ifBlank { episode.poster } ?: episode.poster,
                    voice = filtered.first().voice,
                    streams = filtered.first().streams,
                    openingStart = if ((old?.openingEnd ?: 0) > 0) old?.openingStart ?: 0 else episode.openingStart,
                    openingEnd = max(old?.openingEnd ?: 0, episode.openingEnd),
                    variants = filtered
                )
            }
        }
        val related = found.flatMap { it.related }.distinctBy { it.key }.filter { it.key != meta.key }.take(36)
        val similar = found.flatMap { it.similar }.distinctBy { it.key }.filter { it.key != meta.key }.take(12)
        return AnimeDetail(meta.copy(source = "yoru", id = if (meta.malId > 0) meta.malId.toString() else stableId(meta.key)), map.values.sortedBy { it.number }, related, similar)
    }

    private fun enrich(detail: AnimeDetail): AnimeDetail {
        val shots = LinkedHashSet(detail.anime.screenshots)
        var trailer = detail.anime.trailerUrl
        if (detail.anime.malId > 0) {
            runCatching {
                val arr = JSONArray(text("https://shikimori.one/api/animes/${detail.anime.malId}/screenshots"))
                for (i in 0 until min(12, arr.length())) arr.optJSONObject(i)?.let { shikiImage(it.optString("original", it.optString("preview", ""))).takeIf(String::isNotBlank)?.let(shots::add) }
            }
            runCatching {
                val arr = JSONArray(text("https://shikimori.one/api/animes/${detail.anime.malId}/videos"))
                for (i in 0 until arr.length()) {
                    val row = arr.optJSONObject(i) ?: continue
                    val url = safe(row.optString("url", row.optString("player_url", "")))
                    if (url.isNotBlank()) { trailer = url; break }
                }
            }
        }
        val anime = detail.anime.copy(screenshots = shots.take(12), trailerUrl = trailer)
        val episodes = detail.episodes.mapIndexed { index, ep -> ep.copy(poster = ep.poster.ifBlank { anime.screenshots.getOrNull(index % anime.screenshots.size.coerceAtLeast(1)).orEmpty().ifBlank { anime.poster } }) }
        return detail.copy(anime = anime, episodes = episodes)
    }

    private fun catalogLibria(query: String, filter: CatalogFilter, limit: Int): List<AnimeItem> {
        val uri = Uri.parse("https://anilibria.top/api/v1/anime/catalog/releases").buildUpon()
            .appendQueryParameter("limit", limit.coerceIn(12, 48).toString())
            .appendQueryParameter("page", "1")
            .appendQueryParameter("f[sorting]", filter.sort)
        if (query.isNotBlank()) uri.appendQueryParameter("f[search]", query.trim())
        if (filter.type.isNotBlank()) uri.appendQueryParameter("f[types]", filter.type)
        if (filter.status.isNotBlank()) uri.appendQueryParameter("f[publish_statuses]", filter.status)
        if (filter.year > 0) {
            uri.appendQueryParameter("f[years][from_year]", filter.year.toString())
            uri.appendQueryParameter("f[years][to_year]", filter.year.toString())
        }
        return json(uri.build().toString()).optJSONArray("data").items(limit) { libriaAnime(it) }
    }

    private fun detailLibria(id: String): AnimeDetail {
        val root = json("https://anilibria.top/api/v1/anime/releases/${enc(id)}")
        val anime = libriaAnime(root)
        val episodes = parseLibriaEpisodes(root, anime)
        return AnimeDetail(anime, episodes)
    }

    private fun parseLibriaEpisodes(root: JSONObject, anime: AnimeItem): List<EpisodeItem> {
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
            val variant = PlaybackVariant(id = row.optString("id"), voice = "AniLibria.TV", route = sourceLabel("anilibria"), source = "anilibria", streams = streams, duration = row.optInt("duration", 0), openingStart = opening?.optInt("start", 0) ?: 0, openingEnd = opening?.optInt("stop", 0) ?: 0)
            out.add(EpisodeItem(
                id = row.optString("id", "${anime.id}-$i"),
                number = row.optDouble("ordinal", row.optDouble("number", (i + 1).toDouble())).takeIf { it.isFinite() } ?: (i + 1).toDouble(),
                title = row.optString("name", row.optString("title", "")),
                duration = row.optInt("duration", 0),
                poster = absolute("https://anilibria.top/", bestImage(row)).ifBlank { anime.poster },
                voice = "AniLibria.TV",
                streams = streams,
                openingStart = variant.openingStart,
                openingEnd = variant.openingEnd,
                variants = listOf(variant)
            ))
        }
        return out.sortedBy { it.number }
    }

    private fun catalogVost(query: String, limit: Int): List<AnimeItem> {
        val rows = if (query.isBlank()) json("https://api.animevost.org/v1/last?quantity=${limit.coerceIn(12, 48)}&page=1").optJSONArray("data") else formJson("https://api.animevost.org/v1/search", mapOf("name" to query)).optJSONArray("data")
        return rows.items(limit) { vostAnime(it) }
    }

    private fun detailVost(id: String, episodes: Boolean): AnimeDetail {
        val info = formJson("https://api.animevost.org/v1/info", mapOf("id" to id)).optJSONArray("data")?.optJSONObject(0) ?: error("empty")
        val anime = vostAnime(info)
        if (!episodes) return AnimeDetail(anime, emptyList())
        val rows = JSONArray(request("https://api.animevost.org/v1/playlist", method = "POST", form = mapOf("id" to id)))
        val out = ArrayList<EpisodeItem>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val streams = linkedMapOf<Int, String>()
            putStream(streams, 480, row.optString("std"))
            putStream(streams, 720, row.optString("hd"))
            val n = numberIn(row.optString("name"), (i + 1).toDouble())
            val variant = PlaybackVariant("animevost-$id-$n", "AnimeVost", sourceLabel("animevost"), "animevost", streams = streams)
            out.add(EpisodeItem("animevost-$id-$n", n, row.optString("name"), poster = anime.poster, voice = "AnimeVost", streams = streams, variants = listOf(variant)))
        }
        return AnimeDetail(anime.copy(episodes = max(anime.episodes, out.size)), out.sortedBy { it.number })
    }

    private fun catalogYummy(query: String, filter: CatalogFilter, limit: Int): List<AnimeItem> {
        val uri = Uri.parse("https://api.yani.tv/anime").buildUpon()
            .appendQueryParameter("limit", limit.coerceIn(12, 48).toString())
            .appendQueryParameter("offset", "0")
            .appendQueryParameter("sort", if (filter.sort == "YEAR_DESC") "year" else "rating")
            .appendQueryParameter("sort_forward", "false")
        if (query.isNotBlank()) uri.appendQueryParameter("q", query)
        if (filter.year > 0) { uri.appendQueryParameter("from_year", filter.year.toString()); uri.appendQueryParameter("to_year", filter.year.toString()) }
        return json(uri.build().toString()).optJSONArray("response").items(limit) { yummyAnime(it) }
    }

    private fun detailYummy(id: String, episodes: Boolean): AnimeDetail {
        val root = json("https://api.yani.tv/anime/${enc(id)}").optJSONObject("response") ?: error("empty")
        val anime = yummyAnime(root)
        val eps = if (episodes) yummyEpisodes(id, anime) else emptyList()
        return AnimeDetail(anime, eps)
    }

    private fun yummyEpisodes(id: String, anime: AnimeItem): List<EpisodeItem> {
        val rows = json("https://api.yani.tv/anime/$id/videos").optJSONArray("response") ?: JSONArray()
        val map = linkedMapOf<String, EpisodeItem>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val n = row.optDouble("number", (i + 1).toDouble()).takeIf { it.isFinite() } ?: continue
            val data = row.optJSONObject("data")
            val skips = row.optJSONObject("skips")?.optJSONObject("opening")
            val voice = realVoice(data?.optString("dubbing", row.optString("dubbing", "")) ?: "").ifBlank { "Дорожка без названия" }
            val player = sourceLabel(data?.optString("player", row.optString("player", "YORU Max")) ?: "YORU Max")
            val url = embed(row.optString("iframe_url", row.optString("url", "")))
            if (url.isBlank()) continue
            val key = numberLabel(n)
            val old = map[key]
            val variant = PlaybackVariant("yummy-$id-$key-${stableId(url)}", voice, player, "yummy", resolverUrl = url, duration = row.optInt("duration", 0), openingStart = skips?.optInt("time", 0) ?: 0, openingEnd = (skips?.optInt("time", 0) ?: 0) + (skips?.optInt("length", 0) ?: 0))
            val variants = (old?.variants.orEmpty() + variant).distinctBy { it.voice + it.resolverUrl }
            map[key] = EpisodeItem("yummy-$id-$key", n, old?.title ?: "", max(old?.duration ?: 0, variant.duration), old?.poster?.ifBlank { imageFrom(row) } ?: imageFrom(row).ifBlank { anime.poster }, voice, variants.first().streams, variant.openingStart, variant.openingEnd, variants)
        }
        return map.values.sortedBy { it.number }
    }

    private fun catalogAnimelib(query: String, filter: CatalogFilter, limit: Int, ultra: Boolean): List<AnimeItem> {
        val uri = Uri.parse("https://api.cdnlibs.org/api/anime").buildUpon()
            .appendQueryParameter("page", "1")
            .appendQueryParameter("q", query)
            .appendQueryParameter("sort_by", if (filter.sort == "FRESH_AT_DESC") "last_episode_at" else "rate_avg")
            .appendQueryParameter("fields[]", "rate")
            .appendQueryParameter("fields[]", "rate_avg")
            .appendQueryParameter("fields[]", "releaseDate")
        return json(uri.build().toString()).optJSONArray("data").items(limit) { animelibAnime(it).let { a -> if (ultra) a.copy(source = "animelib4k") else a } }
    }

    private fun detailAnimelib(source: String, id: String, episodes: Boolean): AnimeDetail {
        val base = json("https://api.cdnlibs.org/api/anime/${enc(id)}?fields[]=genres&fields[]=releaseDate&fields[]=shiki_id&fields[]=rate&fields[]=rate_avg").optJSONObject("data") ?: error("empty")
        val anime = animelibAnime(base).copy(source = source)
        if (!episodes) return AnimeDetail(anime, emptyList())
        val rows = json("https://api.cdnlibs.org/api/episodes?anime_id=${enc(anime.alias.ifBlank { id })}").optJSONArray("data") ?: JSONArray()
        val out = ArrayList<EpisodeItem>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val n = row.optDouble("number", -1.0)
            if (!n.isFinite() || n < 0) continue
            val epId = row.optString("id")
            val variant = PlaybackVariant("animelib-$epId", "", sourceLabel(source), "animelib", resolverUrl = "animelib:$epId")
            out.add(EpisodeItem(epId, n, row.optString("name", ""), poster = absolute("https://cover.cdnlibs.org/", bestImage(row)).ifBlank { anime.poster }, variants = listOf(variant)))
        }
        return AnimeDetail(anime.copy(episodes = max(anime.episodes, out.size)), out.sortedBy { it.number })
    }

    private fun resolveAnimelibVariant(token: String): Map<Int, String> {
        val id = token.substringAfter("animelib:", token)
        val rows = json("https://api.cdnlibs.org/api/episodes/${enc(id)}").optJSONObject("data")?.optJSONArray("players") ?: JSONArray()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val url = embed(row.optString("src"))
            val streams = YoruVideoResolver(client).resolve(url)
            if (streams.isNotEmpty()) return streams
        }
        return emptyMap()
    }

    private fun catalogShiki(query: String, filter: CatalogFilter, limit: Int): List<AnimeItem> {
        val args = buildString {
            append("limit:${limit.coerceIn(12, 48)},page:1,order:${if (filter.sort == "POPULARITY") "popularity" else "ranked"},rating:\"!rx\"")
            if (query.isNotBlank()) append(",search:${JSONObject.quote(query)}")
            if (filter.year > 0) append(",season:${JSONObject.quote(filter.year.toString())}")
            if (filter.type.isNotBlank()) append(",kind:${JSONObject.quote(filter.type.lowercase(Locale.ROOT))}")
            if (filter.status.isNotBlank()) append(",status:${JSONObject.quote(if (filter.status == "IS_ONGOING") "ongoing" else "released")}")
        }
        val q = "{animes($args){id malId name russian english kind rating score status episodes episodesAired airedOn{year date} poster{mainUrl originalUrl} genres{id russian name} studios{name}}}"
        val rows = shiki(q).optJSONArray("animes")
        return rows.items(limit) { shikiAnime(it) }
    }

    private fun detailShiki(id: String): AnimeDetail {
        val q = "{animes(ids:${JSONObject.quote(id)},limit:1){id malId name russian english kind rating score status episodes episodesAired airedOn{year date} poster{mainUrl originalUrl} genres{id russian name} studios{name} descriptionHtml related{relationKind anime{id malId name russian english kind rating score status episodes episodesAired airedOn{year date} poster{mainUrl originalUrl} genres{id russian name}}}}}"
        val root = shiki(q).optJSONArray("animes")?.optJSONObject(0) ?: error("empty")
        val anime = shikiAnime(root).copy(description = strip(root.optString("descriptionHtml", "")))
        val related = root.optJSONArray("related").items(36) { it.optJSONObject("anime")?.let(::shikiAnime) }.filterNotNull()
        return AnimeDetail(anime, emptyList(), related)
    }

    private fun detailKodik(id: String, episodes: Boolean): AnimeDetail {
        val shiki = runCatching { detailShiki(id) }.getOrElse { AnimeDetail(AnimeItem(id, "YORU", source = "kodik", malId = id.toIntOrNull() ?: 0), emptyList()) }
        val anime = kodikShell(shiki.anime)
        if (!episodes) return AnimeDetail(anime, emptyList(), shiki.related)
        val mal = anime.malId.takeIf { it > 0 } ?: id.toIntOrNull() ?: 0
        val url = directKodik(mal)
        val count = anime.episodes.takeIf { it > 0 }?.coerceAtMost(250) ?: 1
        val eps = (1..count).map { n ->
            val epUrl = Uri.parse(url).buildUpon().appendQueryParameter("episode", n.toString()).build().toString()
            val variant = PlaybackVariant("kodik-$mal-$n", "", "YORU Prime", "kodik", resolverUrl = epUrl)
            EpisodeItem("kodik-$mal-$n", n.toDouble(), poster = anime.poster, variants = listOf(variant))
        }
        return AnimeDetail(anime, eps, shiki.related)
    }

    private fun catalogAnimedia(query: String, limit: Int): List<AnimeItem> {
        val base = "https://amd.online"
        val html = if (query.isBlank()) text(base) else request("$base/index.php?do=search", method = "POST", form = mapOf("do" to "search", "subaction" to "search", "full_search" to "0", "result_from" to "1", "search_start" to "1", "story" to query))
        return parseCards(html, base, "animedia", limit)
    }

    private fun detailAnimedia(id: String, episodes: Boolean): AnimeDetail {
        val url = if (id.startsWith("http")) id else "https://amd.online/$id.html"
        val html = text(url)
        val anime = AnimeItem(id = stableId(url), source = "animedia", title = heading(html).ifBlank { "Аниме" }, alias = url, poster = firstImage(html, url), description = description(html), status = "Опубликован", type = "Аниме")
        if (!episodes) return AnimeDetail(anime, emptyList())
        val map = linkedMapOf<String, EpisodeItem>()
        Regex("data-vid=[\"']([0-9.]+)[\"'][^>]+data-vlnk=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).findAll(html).forEach { m ->
            val n = m.groupValues[1].toDoubleOrNull() ?: return@forEach
            val vod = absolute(url, m.groupValues[2])
            val variant = PlaybackVariant("animedia-${stableId(vod)}", "AniMedia", "AniMedia", "animedia", resolverUrl = vod)
            map[numberLabel(n)] = EpisodeItem("animedia-${stableId(vod)}", n, poster = anime.poster, voice = "AniMedia", variants = listOf(variant))
        }
        return AnimeDetail(anime.copy(episodes = map.size), map.values.sortedBy { it.number })
    }

    private fun resolveAnimediaVariant(url: String): Map<Int, String> {
        val page = text(url)
        val file = Regex("file\\s*:\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(page)?.groupValues?.getOrNull(1) ?: return emptyMap()
        return YoruVideoResolver(client).resolve(absolute(url, file))
    }

    private fun catalogAnimetka(query: String, limit: Int): List<AnimeItem> {
        val uri = Uri.parse(if (query.isBlank()) "https://animetka.com/api/anime/top" else "https://animetka.com/api/anime/search").buildUpon()
            .appendQueryParameter(if (query.isBlank()) "limit" else "name", if (query.isBlank()) limit.toString() else query)
        if (!query.isBlank()) uri.appendQueryParameter("limit", limit.toString())
        uri.appendQueryParameter("offset", "0")
        val text = request(uri.build().toString(), headers = animetkaHeaders())
        val rows = JSONArray(text)
        return rows.items(limit) { animetkaAnime(it) }
    }

    private fun detailAnimetka(id: String, episodes: Boolean): AnimeDetail {
        val d = JSONObject(request("https://animetka.com/api/anime/${enc(id)}", headers = animetkaHeaders()))
        val anime = animetkaAnime(d)
        val related = d.optJSONArray("franchise").items(36) { animetkaAnime(it) }
        if (!episodes) return AnimeDetail(anime, emptyList(), related)
        val map = linkedMapOf<String, EpisodeItem>()
        d.optJSONObject("AnilibriaMapping")?.optJSONArray("episodes")?.let { rows ->
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val n = row.optDouble("ordinal", (i + 1).toDouble())
                val streams = linkedMapOf<Int, String>()
                putStream(streams, 480, row.optString("hls_480"))
                putStream(streams, 720, row.optString("hls_720"))
                putStream(streams, 1080, row.optString("hls_1080"))
                val op = row.optJSONObject("opening")
                val variant = PlaybackVariant("animetka-libria-$id-${numberLabel(n)}", "AniLibria.TV", "Animetka", "animetka", streams = streams, openingStart = op?.optInt("start", 0) ?: 0, openingEnd = op?.optInt("stop", 0) ?: 0)
                map[numberLabel(n)] = EpisodeItem("animetka-$id-${numberLabel(n)}", n, row.optString("name", ""), row.optInt("duration", 0), anime.poster, "AniLibria.TV", streams, variant.openingStart, variant.openingEnd, listOf(variant))
            }
        }
        val names = animetkaTranslationNames(d)
        val rows = d.optJSONArray("Animes") ?: JSONArray()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val link = embed(row.optString("link"))
            if (link.isBlank()) continue
            val tid = row.optInt("translation", 0)
            val count = max(row.optInt("episodes_total", 0), row.optInt("episodes_aired", 0)).let { if (it <= 0) max(1, anime.episodes) else min(250, it) }
            val voice = realVoice(names[tid] ?: if (tid > 0) "Перевод $tid" else "").ifBlank { "Дорожка без названия" }
            val quality = row.optString("quality", "").replace("WEB-DLRip", "").trim()
            for (n in 1..count) {
                val key = numberLabel(n.toDouble())
                val old = map[key]
                val url = animetkaEpisodeUrl(link, n)
                val variant = PlaybackVariant("animetka-$id-$tid-$n", voice, if (quality.isBlank()) "YORU Prime" else "YORU Prime · $quality", "animetka", resolverUrl = url)
                val variants = (old?.variants.orEmpty() + variant).distinctBy { it.voice + it.resolverUrl }
                map[key] = EpisodeItem("animetka-$id-$key", n.toDouble(), old?.title.orEmpty(), old?.duration ?: 0, old?.poster?.ifBlank { anime.poster } ?: anime.poster, variants.first().voice, variants.first().streams, old?.openingStart ?: 0, old?.openingEnd ?: 0, variants)
            }
        }
        return AnimeDetail(anime.copy(episodes = max(anime.episodes, map.size)), map.values.sortedBy { it.number }, related)
    }

    private fun animetkaTranslationNames(detail: JSONObject): Map<Int, String> {
        val material = detail.optInt("id", detail.optInt("animetka_id", 0))
        val rows = detail.optJSONArray("Animes") ?: return emptyMap()
        val tid = (0 until rows.length()).firstNotNullOfOrNull { rows.optJSONObject(it)?.optInt("translation", 0)?.takeIf { v -> v > 0 } } ?: return emptyMap()
        return runCatching {
            val uri = Uri.parse("https://animetka.com/api/anime/playlist").buildUpon().appendQueryParameter("material", material.toString()).appendQueryParameter("tid", tid.toString()).build().toString()
            val arr = JSONObject(request(uri, headers = animetkaHeaders())).optJSONArray("translations") ?: JSONArray()
            val out = HashMap<Int, String>()
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { out[it.optInt("value", it.optInt("id", 0))] = it.optString("name", it.optString("title", "")) }
            out
        }.getOrDefault(emptyMap())
    }

    private fun catalogAnidub(query: String, limit: Int): List<AnimeItem> {
        val url = if (query.isBlank()) "https://online.anidub.com/" else Uri.parse("https://online.anidub.com/index.php").buildUpon().appendQueryParameter("do", "search").appendQueryParameter("subaction", "search").appendQueryParameter("story", query).build().toString()
        return parseCards(text(url), url, "anidub", limit)
    }

    private fun detailAnidub(id: String, episodes: Boolean): AnimeDetail {
        val url = if (id.startsWith("http")) id else "https://online.anidub.com/$id.html"
        val html = text(url)
        val anime = AnimeItem(id = stableId(url), source = "anidub", title = heading(html).ifBlank { "AniDUB" }, alias = url, poster = firstImage(html, url), description = description(html), status = if (html.contains("онгоинг", true)) "Сейчас выходит" else "Завершён", type = "Аниме")
        if (!episodes) return AnimeDetail(anime, emptyList())
        val map = linkedMapOf<String, EpisodeItem>()
        listOf("sel" to "Sibnet", "sel2" to "AniDUB", "sel3" to "Stormo").forEach { (select, player) ->
            val block = Regex("<select[^>]+id=[\"']$select[\"'][^>]*>(.*?)</select>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)?.groupValues?.getOrNull(1).orEmpty()
            Regex("<option[^>]+value=[\"']([^\"']+)[\"'][^>]*>(.*?)</option>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(block).forEach { m ->
                val value = unescape(m.groupValues[1])
                val cut = value.lastIndexOf('|')
                val raw = if (cut > 0) value.substring(0, cut) else value
                val n = numberIn((if (cut > 0) value.substring(cut + 1) else "") + " " + strip(m.groupValues[2]), (map.size + 1).toDouble())
                val link = absolute(url, raw)
                if (link.isBlank()) return@forEach
                val key = numberLabel(n)
                val old = map[key]
                val variant = PlaybackVariant("anidub-${stableId(link)}", "AniDUB", player, "anidub", resolverUrl = link)
                map[key] = EpisodeItem("anidub-${stableId(url)}-$key", n, "", old?.duration ?: 0, anime.poster, "AniDUB", variants = (old?.variants.orEmpty() + variant).distinctBy { it.resolverUrl })
            }
        }
        if (map.isEmpty()) {
            val links = mediaLinks(html)
            if (links.isNotEmpty()) {
                val streams = links.associateBy { qualityOf(it) }
                val variant = PlaybackVariant("anidub-${stableId(links.first())}", "AniDUB", "AniDUB", "anidub", streams = streams)
                map["1"] = EpisodeItem("anidub-${stableId(url)}-1", 1.0, poster = anime.poster, voice = "AniDUB", streams = streams, variants = listOf(variant))
            }
        }
        return AnimeDetail(anime.copy(episodes = max(anime.episodes, map.size)), map.values.sortedBy { it.number })
    }

    private fun catalogAnix(query: String, filter: CatalogFilter, limit: Int): List<AnimeItem> {
        val body = JSONObject().put("sort", if (filter.sort == "POPULARITY") 2 else 1).put("age_rating", JSONArray()).put("genres", JSONArray()).put("years", JSONArray()).put("seasons", JSONArray()).put("types", JSONArray()).put("statuses", JSONArray()).put("countries", JSONArray())
        if (query.isNotBlank()) body.put("search", query).put("query", query).put("title", query)
        if (filter.year > 0) body.getJSONArray("years").put(filter.year)
        val root = anixPost("/filter/0", body)
        val rows = firstArray(root, "releases", "content", "data", "list", "items")
        return rows.items(limit) { anixAnime(it) }
    }

    private fun detailAnix(id: String, episodes: Boolean): AnimeDetail {
        val root = runCatching { anixGet("/release/$id") }.getOrElse { anixGet("/releases/$id") }
        val obj = firstObject(root, "release", "data", "item") ?: root
        val anime = anixAnime(obj)
        if (!episodes) return AnimeDetail(anime, emptyList())
        val types = firstArray(obj, "types", "translations", "videos") ?: firstArray(root, "types", "translations", "videos") ?: JSONArray()
        val map = linkedMapOf<String, EpisodeItem>()
        for (i in 0 until types.length()) {
            val type = types.optJSONObject(i) ?: continue
            val voice = realVoice(type.optString("name", type.optString("title", ""))).ifBlank { "Дорожка без названия" }
            val typeId = type.optString("id", type.optString("type_id", ""))
            val source = chooseAnixSource(firstArray(type, "sources", "players"))
            val sourceId = source?.optString("id")?.takeIf { it.isNotBlank() } ?: source?.optString("source_id").orEmpty()
            val eps = firstArray(source ?: type, "episodes", "list", "items") ?: JSONArray()
            for (n in 0 until eps.length()) {
                val row = eps.optJSONObject(n) ?: continue
                val number = row.optDouble("position", row.optDouble("episode", row.optDouble("number", (n + 1).toDouble())))
                val direct = embed(row.optString("url", row.optString("link", row.optString("iframe_url", ""))))
                val resolver = if (direct.isNotBlank()) direct else anixEpisodePath(id, typeId, sourceId, numberLabel(number))
                val key = numberLabel(number)
                val old = map[key]
                val variant = PlaybackVariant("anix-$id-$typeId-$sourceId-$key", voice, "YORU Reserve", "anixsekai", resolverUrl = resolver)
                map[key] = EpisodeItem("anix-$id-$key", number, row.optString("title", row.optString("name", old?.title.orEmpty())), row.optInt("duration", old?.duration ?: 0), anixImage(bestImage(row)).ifBlank { old?.poster ?: anime.poster }, variant.voice, variants = (old?.variants.orEmpty() + variant).distinctBy { it.voice + it.resolverUrl })
            }
        }
        return AnimeDetail(anime.copy(episodes = max(anime.episodes, map.size)), map.values.sortedBy { it.number })
    }

    private fun catalogShikiFallback(base: AnimeItem): List<AnimeItem> = runCatching { catalogShiki(base.title, CatalogFilter(source = "shikimori"), 12) }.getOrDefault(emptyList())

    private fun mergeCatalog(rows: List<AnimeItem>, limit: Int): List<AnimeItem> {
        val map = linkedMapOf<String, AnimeItem>()
        rows.forEach { item ->
            if (item.id.isBlank() || item.title.isBlank()) return@forEach
            val id = identity(item)
            map[id] = map[id]?.let { absorb(it, item) } ?: item
        }
        return map.values.sortedWith(compareByDescending<AnimeItem> { sourceScore(it.source) + (if (it.poster.isNotBlank()) 8 else 0) + (if (it.description.isNotBlank()) 4 else 0) + it.score.toInt() }.thenByDescending { it.year }).take(limit)
            .map {
                val id = if (it.malId > 0) "mal-${it.malId}" else stableId(it.key)
                val shell = it.copy(source = "yoru", id = id)
                shellMemory[shell.key] = shell
                while (shellMemory.size > 120) shellMemory.remove(shellMemory.keys.first())
                shell
            }
    }

    private fun absorb(a: AnimeItem, b: AnimeItem): AnimeItem = a.copy(
        title = a.title.ifBlank { b.title },
        original = a.original.ifBlank { b.original },
        alias = a.alias.ifBlank { b.alias },
        poster = a.poster.ifBlank { b.poster },
        year = if (a.year > 0) a.year else b.year,
        type = if (a.type != "Аниме") a.type else b.type,
        episodes = max(a.episodes, b.episodes),
        status = a.status.ifBlank { b.status },
        age = a.age.ifBlank { b.age },
        score = if (a.score > 0) a.score else b.score,
        description = a.description.ifBlank { b.description },
        genres = (a.genres + b.genres).distinct().take(30),
        screenshots = (a.screenshots + b.screenshots).distinct().take(12),
        trailerUrl = a.trailerUrl.ifBlank { b.trailerUrl },
        malId = if (a.malId > 0) a.malId else b.malId,
        anilistId = if (a.anilistId > 0) a.anilistId else b.anilistId,
        kpId = if (a.kpId > 0) a.kpId else b.kpId
    )

    private fun matches(candidate: AnimeItem, base: AnimeItem): Boolean {
        if (candidate.malId > 0 && base.malId > 0 && candidate.malId == base.malId) return true
        val ct = plainName(candidate.title)
        val co = plainName(candidate.original)
        val bt = plainName(base.title)
        val bo = plainName(base.original)
        val title = (bt.isNotBlank() && (bt == ct || bt == co || ct.contains(bt) || bt.contains(ct))) || (bo.isNotBlank() && (bo == ct || bo == co || co.contains(bo)))
        val year = base.year == 0 || candidate.year == 0 || abs(base.year - candidate.year) <= 1
        return title && year
    }

    private fun libriaAnime(j: JSONObject): AnimeItem {
        val name = j.optJSONObject("name")
        val poster = j.optJSONObject("poster")
        val type = j.optJSONObject("type")
        val age = j.optJSONObject("age_rating")
        val rating = j.optJSONObject("rating")
        return AnimeItem(
            id = j.optString("id", j.optString("alias", "")),
            source = "anilibria",
            title = name?.optString("main")?.takeIf { it.isNotBlank() } ?: j.optString("title", j.optString("name", "Аниме")),
            original = name?.optString("english")?.takeIf { it.isNotBlank() } ?: j.optString("english", ""),
            alias = j.optString("alias", ""),
            poster = posterUrl(poster, j, "https://anilibria.top/"),
            year = j.optInt("year", yearFrom(j.optString("release_date", j.optString("aired_on", "")))),
            type = kind(type?.optString("value") ?: type?.optString("description") ?: j.optString("type", "")),
            episodes = j.optInt("episodes_total", j.optInt("episodes_count", j.optInt("episodes", 0))),
            status = if (j.optBoolean("is_ongoing", false)) "Сейчас выходит" else "Завершён",
            age = age?.optString("label") ?: j.optString("age", ""),
            score = rating?.optDouble("average", 0.0)?.takeIf { it.isFinite() && it > 0.0 } ?: j.optDouble("rating", j.optDouble("score", 0.0)).takeIf { it.isFinite() } ?: 0.0,
            description = strip(j.optString("description", j.optString("annotation", ""))).take(1400),
            genres = genres(j.optJSONArray("genres"), "name", "title"),
            screenshots = imageArray(j.optJSONArray("screenshots"), "https://anilibria.top/") + imageArray(j.optJSONArray("images"), "https://anilibria.top/"),
            trailerUrl = safe(firstUrl(j, "trailer", "trailer_url", "youtube", "youtube_url", "video"))
        )
    }

    private fun vostAnime(j: JSONObject): AnimeItem {
        val raw = j.optString("title", "Аниме")
        val clean = raw.replace(Regex("\\s*\\[[^]]*]\\s*" + 36.toChar()), "")
        val names = clean.split(" / ", limit = 2)
        return AnimeItem(j.optString("id"), names.firstOrNull().orEmpty().ifBlank { "Аниме" }, "animevost", original = names.getOrNull(1).orEmpty(), poster = safe(j.optString("urlImagePreview")), year = j.optInt("year", yearFrom(raw)), type = j.optString("type", "Аниме"), episodes = Regex("из\\s*(\\d+)").find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0, status = "Опубликован", description = strip(j.optString("description", "")), genres = j.optString("genre", "").split(',').map { it.trim() }.filter { it.isNotBlank() })
    }

    private fun yummyAnime(j: JSONObject): AnimeItem {
        val poster = j.optJSONObject("poster")
        val ids = j.optJSONObject("remote_ids")
        val type = j.optJSONObject("type")
        val age = j.optJSONObject("min_age")
        val rating = j.optJSONObject("rating")
        val eps = j.optJSONObject("episodes")
        val status = j.optJSONObject("anime_status")
        val posterUrl = poster?.optString("medium")?.takeIf { it.isNotBlank() } ?: poster?.optString("fullsize").orEmpty()
        val mal = ids?.optInt("myanimelist_id", 0)?.takeIf { it > 0 } ?: ids?.optInt("shikimori_id", 0) ?: 0
        return AnimeItem(j.optString("anime_id", j.optString("id")), j.optString("title", "Аниме"), "yummy", alias = j.optString("anime_url", ""), poster = safe(posterUrl), year = j.optInt("year", 0), type = type?.optString("shortname", "Аниме") ?: "Аниме", episodes = eps?.optInt("count") ?: j.optInt("ep_count", 0), status = status?.optString("alias", "") ?: "", age = age?.optString("title", "") ?: "", score = rating?.optDouble("average", 0.0) ?: 0.0, description = strip(j.optString("description", "")), genres = genres(j.optJSONArray("genres"), "title", "name"), malId = mal, kpId = ids?.optInt("kp_id", 0) ?: 0)
    }

    private fun animelibAnime(j: JSONObject): AnimeItem {
        val cover = j.optJSONObject("cover")
        val type = j.optJSONObject("type")
        val age = j.optJSONObject("ageRestriction")
        val rating = j.optJSONObject("rating")
        val status = j.optJSONObject("status")
        val episodes = j.optJSONObject("episodes")
        return AnimeItem(j.optString("id"), j.optString("rus_name", j.optString("name", "Аниме")), "animelib", original = j.optString("name", j.optString("eng_name", "")), alias = j.optString("slug_url", j.optString("id")), poster = animelibCover(cover, j.optString("id")), year = yearFrom(j.optString("releaseDate", "")), type = type?.optString("label", "Аниме") ?: "Аниме", episodes = episodes?.optInt("count", 0) ?: j.optInt("episodes_count", 0), status = if (status?.optInt("id", 0) == 1) "Сейчас выходит" else "Завершён", age = age?.optString("label", "") ?: "", score = rating?.optDouble("average", j.optDouble("shiki_rate", 0.0)) ?: j.optDouble("shiki_rate", 0.0), description = strip(j.optString("description", prose(j.opt("summary")))), genres = genres(j.optJSONArray("genres"), "name", "title"), malId = j.optInt("shiki_id", 0))
    }

    private fun shikiAnime(j: JSONObject): AnimeItem {
        val aired = j.optJSONObject("airedOn")
        val poster = j.optJSONObject("poster")
        val posterUrl = poster?.optString("mainUrl")?.takeIf { it.isNotBlank() } ?: poster?.optString("originalUrl").orEmpty()
        return AnimeItem(j.optString("id"), j.optString("russian", "").ifBlank { j.optString("name", "Аниме") }, "shikimori", original = j.optString("name", j.optString("english", "")), poster = safe(posterUrl), year = aired?.optInt("year", 0) ?: 0, type = kind(j.optString("kind", "")), episodes = j.optInt("episodes", j.optInt("episodesAired", 0)), status = statusName(j.optString("status", "")), age = if (j.optString("rating") == "pg_13") "13+" else if (j.optString("rating").startsWith("r")) "18+" else "", score = j.optDouble("score", 0.0), genres = genres(j.optJSONArray("genres"), "russian", "name"), malId = j.optInt("malId", j.optInt("id", 0)))
    }

    private fun animetkaAnime(j: JSONObject): AnimeItem = AnimeItem(j.optString("animetka_id", j.optString("id")), j.optString("anime_title", j.optString("title", "Аниме")), "animetka", original = j.optString("title_orig", j.optString("title_en", "")), alias = j.optString("title_en", ""), poster = safe(j.optString("anime_poster_url", j.optString("poster_url", ""))), year = j.optInt("year", 0), type = kind(j.optString("anime_kind", j.optString("kind", ""))), episodes = j.optInt("episodes_total", j.optInt("episodes", 0)), status = statusName(j.optString("all_status", j.optString("anime_status", ""))), age = j.optInt("minimal_age", 0).takeIf { it > 0 }?.let { "$it+" } ?: j.optString("rating_mpaa", ""), score = j.optDouble("shikimori_rating", j.optDouble("rating", 0.0)).takeIf { it.isFinite() } ?: 0.0, description = strip(j.optString("anime_description", j.optString("description", ""))), genres = genres(j.optJSONArray("anime_genres"), "name", "title") + genres(j.optJSONArray("all_genres"), "name", "title"), malId = j.optInt("shikimori_id", 0), kpId = j.optInt("kinopoisk_id", j.optInt("kp_id", 0)))

    private fun anixAnime(j: JSONObject): AnimeItem {
        val rawPoster = j.optString("poster").ifBlank {
            j.optString("image").ifBlank {
                j.optString("image_url").ifBlank {
                    j.optString("poster_url").ifBlank { j.optString("screenshot", "") }
                }
            }
        }
        val genres = genres(j.optJSONArray("genres"), "name", "title") + genres(j.optJSONArray("categories"), "name", "title")
        val shots = listOf(anixImage(j.optString("screenshot", j.optString("frame", "")))).filter { it.isNotBlank() } + imageArray(j.optJSONArray("screenshots"), "https://api-s.anixsekai.com")
        return AnimeItem(
            id = j.optString("id", j.optString("releaseId", "")),
            title = j.optString("title_ru", j.optString("title", j.optString("name", "Аниме"))),
            source = "anixsekai",
            original = j.optString("title_original", j.optString("title_en", j.optString("title_alt", ""))),
            alias = j.optString("alias", j.optString("code", "")),
            poster = anixImage(rawPoster),
            year = j.optInt("year", yearFrom(j.optString("aired_on", j.optString("season", "")))),
            type = kind(j.optString("type", j.optString("category", ""))),
            episodes = j.optInt("episodes_count", j.optInt("episodes", j.optInt("episode_count", 0))),
            status = statusName(j.optString("status", j.optString("publish_status", ""))),
            age = j.optInt("age_rating", 0).takeIf { it > 0 }?.let { "$it+" } ?: "",
            score = j.optDouble("grade", j.optDouble("rating", j.optDouble("score", 0.0))).takeIf { it.isFinite() } ?: 0.0,
            description = strip(j.optString("description", j.optString("annotation", ""))),
            genres = genres,
            screenshots = shots,
            trailerUrl = safe(j.optString("trailer", j.optString("trailer_url", j.optString("youtube_url", "")))),
            malId = j.optInt("myanimelist_id", j.optInt("mal_id", j.optInt("malId", 0)))
        )
    }

    private fun kodikShell(base: AnimeItem): AnimeItem = base.copy(source = "kodik", id = (base.malId.takeIf { it > 0 } ?: base.id.toIntOrNull() ?: stableId(base.key).toIntOrNull() ?: 0).toString())

    private fun parseCards(html: String, base: String, source: String, limit: Int): List<AnimeItem> {
        val out = linkedMapOf<String, AnimeItem>()
        Regex("<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(html).forEach { m ->
            if (out.size >= limit) return@forEach
            val href = absolute(base, m.groupValues[1])
            if (href.isBlank() || href.contains("/user/") || href.contains("/news/")) return@forEach
            if (source == "anidub" && !Regex("/(\\d+)-[^/]+\\.html").containsMatchIn(href)) return@forEach
            val label = cleanTitle(strip(m.groupValues[2])).ifBlank { cleanTitle(Regex("title=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(m.value)?.groupValues?.getOrNull(1).orEmpty()) }
            if (label.length < 2 || !Regex("[\\p{L}]").containsMatchIn(label)) return@forEach
            val id = if (source == "animedia") stableId(href) else Regex("/(\\d+)-").find(href)?.groupValues?.getOrNull(1) ?: stableId(href)
            val poster = firstImage(m.value, base)
            out[id] = AnimeItem(id, label, source, alias = href, poster = poster, year = yearFrom(m.value), episodes = episodesIn(m.value + label), status = "Опубликован")
        }
        return out.values.toList()
    }

    private fun directKodik(mal: Int): String {
        if (mal <= 0) error("empty")
        val url = Uri.parse("https://kodik-api.com/get-player").buildUpon()
            .appendQueryParameter("title", "Player")
            .appendQueryParameter("hasPlayer", "false")
            .appendQueryParameter("url", "https://kodikdb.com/find-player?shikimoriID=$mal")
            .appendQueryParameter("token", token())
            .appendQueryParameter("shikimoriID", mal.toString())
            .build().toString()
        val root = json(url)
        if (root.has("error") || !root.optBoolean("found", false) || root.optInt("allowed", 1) == 0) error("empty")
        return embed(root.optString("link"))
    }

    private fun token(): String {
        if (kodikToken.isNotBlank() && System.currentTimeMillis() - kodikTokenAt < 300000) return kodikToken
        val script = text("https://kodik-add.com/add-players.min.js?v=2")
        val patterns = listOf("token\\s*=\\s*[\"']([A-Za-z0-9]{16,120})[\"']", "[\"']token[\"']\\s*:\\s*[\"']([A-Za-z0-9]{16,120})[\"']")
        patterns.forEach { pattern -> Regex(pattern).find(script)?.groupValues?.getOrNull(1)?.let { kodikToken = it; kodikTokenAt = System.currentTimeMillis(); return it } }
        error("empty")
    }

    private fun shiki(query: String): JSONObject {
        val body = JSONObject().put("query", query).toString().toRequestBody()
        for (url in listOf("https://shikimori.io/api/graphql", "https://shikimori.one/api/graphql", "https://shikimori.me/api/graphql")) runCatching {
            val root = JSONObject(request(url, "POST", body = body, headers = mapOf("Content-Type" to "application/json")))
            if (!root.has("errors")) return root.getJSONObject("data")
        }
        error("empty")
    }

    private fun anixGet(path: String): JSONObject {
        for (base in listOf("https://api-s.anixsekai.com", "https://api.anixsekai.com")) runCatching { return anixOk(request(base + path, headers = anixHeaders(base))) }
        error("empty")
    }

    private fun anixPost(path: String, body: JSONObject): JSONObject {
        for (base in listOf("https://api-s.anixsekai.com", "https://api.anixsekai.com")) runCatching { return anixOk(request(base + path, method = "POST", body = body.toString().toRequestBody(), headers = anixHeaders(base) + ("Content-Type" to "application/json"))) }
        error("empty")
    }

    private fun anixOk(text: String): JSONObject {
        val root = JSONObject(text)
        if (root.has("code") && root.optInt("code", 0) != 0) error("empty")
        return root
    }

    private fun request(url: String, method: String = "GET", body: okhttp3.RequestBody? = null, form: Map<String, String>? = null, headers: Map<String, String> = emptyMap()): String {
        val requestBody = body ?: form?.let { values -> FormBody.Builder().apply { values.forEach { (k, v) -> add(k, v) } }.build() }
        val builder = Request.Builder().url(url).header("Accept", "application/json,text/html,*/*").header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.5").header("User-Agent", chrome)
        headers.forEach { (k, v) -> builder.header(k, v) }
        if (method == "POST") builder.post(requestBody ?: ByteArray(0).toRequestBody()) else builder.get()
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) error("network")
            return response.body?.string().orEmpty()
        }
    }

    private fun text(url: String): String = request(url)
    private fun json(url: String): JSONObject = JSONObject(text(url))
    private fun formJson(url: String, form: Map<String, String>): JSONObject = JSONObject(request(url, "POST", form = form))

    private fun putStream(map: MutableMap<Int, String>, q: Int, raw: String) { safe(raw).takeIf(String::isNotBlank)?.let { map[q] = it } }
    private fun parseKey(key: String): Pair<String, String> = if (key.contains(':')) key.substringBefore(':') to key.substringAfter(':') else "anilibria" to key
    private fun sourceVoice(source: String): String = when (source) { "animevost" -> "AnimeVost"; "anidub" -> "AniDUB"; "animedia" -> "AniMedia"; "anilibria", "animelib", "animelib4k" -> "AniLibria.TV"; else -> "" }
    private fun sourceScore(source: String): Int = when (source) { "yummy" -> 100; "anilibria" -> 96; "animevost" -> 92; "animelib" -> 88; "animelib4k" -> 87; "animedia" -> 82; "animetka" -> 78; "anidub" -> 76; "kodik" -> 70; "anixsekai" -> 68; "shikimori" -> 42; else -> 10 }
    private fun variantRank(v: PlaybackVariant): Int = (100 - sourceScore(v.source)) * 10 + when { voiceKey(v.voice) == "anilibria" -> 0; voiceKey(v.voice) == "animevost" -> 1; voiceKey(v.voice) == "anidub" -> 2; else -> 5 }
    private fun identity(a: AnimeItem): String = if (a.malId > 0) "mal:${a.malId}" else plainName(a.original.ifBlank { a.title }) + ":" + (a.year.takeIf { it > 0 } ?: 0)
    private fun searchTerms(a: AnimeItem): List<String> = listOf(a.title, a.original, a.alias).flatMap { raw -> listOf(raw, raw.substringBefore('/'), raw.replace(Regex("\\s*\\([^)]*\\)"), " ").replace(Regex("\\s*\\[[^]]*]"), " ").replace(Regex("(?iu)\\b(tv|ova|ona|movie|special|season|фильм|спешл|сезон)\\b"), " ")) }.map { cleanTitle(it).replace(Regex("(?iu)\\b(смотреть|онлайн|аниме|все серии|озвучка|субтитры)\\b"), " ").replace(Regex("\\s+"), " ").trim() }.filter { it.length in 2..120 && Regex("[\\p{L}]").containsMatchIn(it) }.distinct().take(8)

    private fun safe(raw: String): String {
        val fixed = raw.trim().replace(" ", "%20").replace("\\/", "/").replace("&amp;", "&")
        if (!fixed.startsWith("http://") && !fixed.startsWith("https://") && !fixed.startsWith("file:")) return ""
        if (fixed.startsWith("file:")) return fixed
        val uri = runCatching { Uri.parse(fixed) }.getOrNull() ?: return ""
        val host = uri.host?.lowercase(Locale.ROOT) ?: return ""
        if (!host.contains('.') || host == "localhost" || host.endsWith(".local") || host.matches(Regex("^(127|10|0|192\\.168|169\\.254)\\..*"))) return ""
        return fixed
    }

    private fun embed(raw: String): String {
        var s = raw.trim().replace("&amp;", "&")
        Regex("src=[\"']([^\"']+)", RegexOption.IGNORE_CASE).find(s)?.let { s = it.groupValues[1] }
        if (s.startsWith("//")) s = "https:$s"
        val uri = runCatching { Uri.parse(s) }.getOrNull()
        val host = uri?.host.orEmpty()
        if (host in listOf("aniqit.com", "kodik.info", "kodik.cc", "kodik.biz")) s = uri!!.buildUpon().scheme("https").authority("kodikplayer.com").build().toString()
        return safe(s)
    }

    private fun absolute(base: String, raw: String): String = runCatching {
        if (raw.isBlank()) return ""
        val fixed = raw.trim().replace(" ", "%20").replace("\\/", "/").replace("&amp;", "&")
        safe(if (fixed.startsWith("//")) "https:$fixed" else URI(base).resolve(fixed).toString())
    }.getOrDefault("")

    private fun posterUrl(poster: JSONObject?, source: JSONObject, base: String): String {
        val opt = poster?.optJSONObject("optimized")
        val raw = opt?.optString("preview")?.takeIf { it.isNotBlank() } ?: opt?.optString("src")?.takeIf { it.isNotBlank() } ?: poster?.optString("preview")?.takeIf { it.isNotBlank() } ?: poster?.optString("src")?.takeIf { it.isNotBlank() } ?: bestImage(source)
        return absolute(base, raw)
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

    private fun imageArray(array: JSONArray?, base: String): List<String> = (0 until (array?.length() ?: 0)).mapNotNull { i ->
        val raw = when (val value = array?.opt(i)) { is JSONObject -> bestImage(value); else -> value?.toString().orEmpty() }
        absolute(base, raw).takeIf { it.isNotBlank() }
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

    private fun genres(rows: JSONArray?, vararg keys: String): List<String> {
        val out = ArrayList<String>()
        for (i in 0 until (rows?.length() ?: 0)) {
            val value = when (val item = rows?.opt(i)) {
                is JSONObject -> keys.firstNotNullOfOrNull { item.optString(it).takeIf(String::isNotBlank) }.orEmpty()
                else -> item?.toString().orEmpty()
            }.trim()
            if (value.isNotBlank() && out.none { it.equals(value, true) }) out.add(value)
            if (out.size >= 30) break
        }
        return out
    }

    private fun animelibCover(cover: JSONObject?, id: String): String {
        if (cover == null) return ""
        val keys = arrayOf("default", "md", "thumbnail", "filename", "url", "src")
        fun coverUrl(raw: String): String {
            val s = raw.trim().replace("\\/", "/").replace(" ", "%20")
            if (s.isBlank() || s.equals("null", true)) return ""
            return when { s.startsWith("http") -> safe(s); s.startsWith("//") -> safe("https:$s"); s.startsWith("/") -> safe("https://cover.cdnlibs.org$s"); else -> safe("https://cover.cdnlibs.org/${s.replace(Regex("^/+"), "")}") }
        }
        for (key in keys) coverUrl(cover.optString(key, "")).takeIf(String::isNotBlank)?.let { return it }
        listOf("optimized", "images").forEach { obj -> cover.optJSONObject(obj)?.let { nested -> for (key in keys) coverUrl(nested.optString(key, "")).takeIf(String::isNotBlank)?.let { return it } } }
        return coverUrl(cover.optString("path", id))
    }

    private fun prose(value: Any?): String = when (value) {
        is String -> strip(value)
        is JSONObject -> value.optString("text").ifBlank { value.optJSONArray("content")?.let { arr -> (0 until arr.length()).joinToString("\n") { prose(arr.opt(it)) } }.orEmpty() }
        else -> ""
    }

    private fun imageFrom(j: JSONObject): String = absolute("https://api.yani.tv/", bestImage(j))

    private fun anixImage(raw: String): String {
        if (raw.isBlank()) return ""
        return when {
            raw.startsWith("http") -> safe(raw)
            raw.startsWith("//") -> safe("https:$raw")
            raw.startsWith("/") -> safe("https://api-s.anixsekai.com$raw")
            else -> safe(raw)
        }
    }

    private fun shikiImage(raw: String): String {
        return when {
            raw.startsWith("//") -> safe("https:$raw")
            raw.startsWith("/") -> safe("https://shikimori.one$raw")
            else -> safe(raw)
        }
    }

    private fun strip(value: String): String {
        return value
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\[[^]]+]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun cleanTitle(raw: String): String {
        return strip(raw)
            .replace('\u00a0', ' ')
            .replace(Regex("\\s+"), " ")
            .replace(Regex("(?iu)^смотреть\\s+"), "")
            .replace(Regex("(?iu)\\s+все\\s+серии.*" + 36.toChar()), "")
            .replace(Regex("\\s*\\[[^]]*]\\s*" + 36.toChar()), "")
            .trim()
    }

    private fun plainName(raw: String): String {
        return raw.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    }

    private fun numberIn(raw: String, fallback: Double): Double {
        return Regex("[0-9]+(?:\\.[0-9]+)?").find(raw)?.value?.toDoubleOrNull() ?: fallback
    }

    private fun episodesIn(raw: String): Int {
        return Regex("(?iu)(?:из|серий:?|episodes?)\\s*(\\d{1,4})").find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }

    private fun yearFrom(raw: String): Int {
        return Regex("(19|20)\\d{2}").find(raw)?.value?.toIntOrNull() ?: 0
    }

    private fun kind(raw: String): String = when (raw.lowercase(Locale.ROOT)) {
        "tv", "tv_short" -> "ТВ"
        "movie" -> "Фильм"
        "ona" -> "ONA"
        "ova" -> "OVA"
        "special", "tv_special" -> "Спешл"
        else -> raw.ifBlank { "Аниме" }
    }

    private fun statusName(raw: String): String = when {
        raw.contains("ongo", true) || raw.contains("выход", true) -> "Сейчас выходит"
        raw.contains("anons", true) || raw.contains("анонс", true) -> "Анонсирован"
        raw.isBlank() -> ""
        else -> "Завершён"
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun stableId(value: String): String {
        val hex = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
        return ((hex.take(12).toLong(16) % 999999999L) + 1L).toString()
    }

    private fun firstImage(html: String, base: String): String {
        val raw = Regex("<img[^>]+(?:data-src|data-original|src)=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1).orEmpty()
        return absolute(base, raw)
    }

    private fun heading(html: String): String {
        val raw = Regex("<h1[^>]*>(.*?)</h1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.getOrNull(1).orEmpty()
        return cleanTitle(raw)
    }

    private fun description(html: String): String {
        for (cls in listOf("description", "full-text", "story", "entry")) {
            val raw = Regex("<[^>]+class=[\"'][^\"']*" + Regex.escape(cls) + "[^\"']*[\"'][^>]*>(.*?)</[^>]+>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(html)?.groupValues?.getOrNull(1).orEmpty()
            val clean = strip(raw).take(1200)
            if (clean.length > 40) return clean
        }
        return ""
    }

    private fun unescape(value: String): String {
        return value
            .replace("&amp;", "&")
            .replace("&#039;", "'")
            .replace("&quot;", 34.toChar().toString())
    }

    private fun mediaLinks(html: String): List<String> {
        val pattern = Regex("https?:\\?//?[^\"'<>\\s]+?(?:\\.m3u8|\\.mp4|\\.mpd)[^\"'<>\\s]*", RegexOption.IGNORE_CASE)
        return pattern.findAll(html)
            .mapNotNull { safe(it.value.replace("\\/", "/")) }
            .distinct()
            .take(12)
            .toList()
    }

    private fun qualityOf(url: String): Int {
        val pattern = Regex("(?:^|[^0-9])([1-9][0-9]{2,3})p?(?:[^0-9]|" + 36.toChar() + ")")
        return pattern.find(url)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }

    private fun sortQualities(streams: Map<Int, String>, preferred: Int): Map<Int, String> {
        return streams
    }


}

private const val chrome = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Mobile Safari/537.36"
private const val anixUa = "Anixart/8.5.2 (Android 13; Pixel 7)"

private fun animetkaEpisodeUrl(link: String, episode: Int): String {
    return runCatching {
        val uri = Uri.parse(link)
        if (uri.path?.startsWith("/video/") == true) link else uri.buildUpon().appendQueryParameter("episode", episode.toString()).build().toString()
    }.getOrDefault(link)
}

private fun anixEpisodePath(id: String, typeId: String, sourceId: String, episode: String): String {
    return "https://api-s.anixsekai.com/release/$id/type/$typeId/source/$sourceId/episode/$episode"
}

private fun chooseAnixSource(sources: JSONArray?): JSONObject? {
    var first: JSONObject? = null
    for (i in 0 until (sources?.length() ?: 0)) {
        val row = sources?.optJSONObject(i) ?: continue
        if (first == null) first = row
        val name = row.optString("name", row.optString("title", "")).lowercase(Locale.ROOT)
        if (row.optInt("id", 0) == 12 || name.contains("kodik")) return row
    }
    return first
}

private fun firstArray(root: JSONObject?, vararg keys: String): JSONArray? {
    if (root == null) return null
    for (key in keys) {
        root.optJSONArray(key)?.let { return it }
        val nested = root.optJSONObject(key)
        val found = firstArray(nested, "releases", "content", "data", "list", "items", "types", "sources", "episodes")
        if (found != null) return found
    }
    return null
}

private fun firstObject(root: JSONObject?, vararg keys: String): JSONObject? {
    if (root == null) return null
    for (key in keys) root.optJSONObject(key)?.let { return it }
    return null
}

private fun animetkaHeaders(): Map<String, String> = mapOf(
    "Accept" to "application/json,text/plain,*/*",
    "Origin" to "https://animetka.com",
    "Referer" to "https://animetka.com/",
    "User-Agent" to chrome
)

private fun anixHeaders(base: String): Map<String, String> = mapOf(
    "Accept" to "application/json",
    "User-Agent" to anixUa,
    "Origin" to base,
    "Referer" to "$base/",
    "Accept-Language" to "ru-RU,ru;q=0.9,en;q=0.5"
)

private fun <T> JSONArray?.items(limit: Int, mapper: (JSONObject) -> T?): List<T> {
    val out = ArrayList<T>()
    for (i in 0 until (this?.length() ?: 0)) {
        val obj = this?.optJSONObject(i) ?: continue
        mapper(obj)?.let { out.add(it) }
        if (out.size >= limit) break
    }
    return out
}
