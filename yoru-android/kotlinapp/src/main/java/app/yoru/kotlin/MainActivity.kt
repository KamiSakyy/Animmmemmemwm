package app.yoru.kotlin

import android.app.Activity
import android.app.Application
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

private val bg = ComposeColor(0xff07050b)
private val card = ComposeColor(0xff16101f)
private val panel = ComposeColor(0xff21182d)
private val accent = ComposeColor(0xffd9b8ff)
private val text = ComposeColor(0xfffaf2ff)
private val muted = ComposeColor(0xffaa9ab7)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.BLACK
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContent { YoruKotlinApp() }
    }
}

data class AnimeItem(
    val id: String,
    val title: String,
    val original: String,
    val poster: String,
    val year: Int,
    val type: String,
    val episodes: Int,
    val status: String,
    val description: String
)

data class EpisodeItem(
    val id: String,
    val number: Double,
    val title: String,
    val duration: Int,
    val poster: String,
    val streams: Map<Int, String>
)

data class AnimeDetail(val anime: AnimeItem, val episodes: List<EpisodeItem>)
data class HomeState(val query: String = "", val loading: Boolean = true, val items: List<AnimeItem> = emptyList(), val error: String = "")
data class DetailState(val loading: Boolean = true, val detail: AnimeDetail? = null, val error: String = "")

class YoruKotlinVm(app: Application) : AndroidViewModel(app) {
    val repo = YoruKotlinRepository(app)
    var home by mutableStateOf(HomeState())
        private set
    fun setQuery(value: String) { home = home.copy(query = value) }
    fun loadHome(query: String = home.query) {
        home = home.copy(query = query, loading = true, error = "")
        viewModelScope.launch {
            val result = runCatching { repo.catalog(query.trim()) }
            home = result.fold(
                onSuccess = { HomeState(query = query, loading = false, items = it) },
                onFailure = { HomeState(query = query, loading = false, items = home.items, error = "Не удалось загрузить каталог") }
            )
        }
    }
}

class YoruKotlinRepository(app: Application) {
    private val client = OkHttpClient.Builder()
        .cache(Cache(File(app.cacheDir, "kotlin-http"), 48L * 1024L * 1024L))
        .connectTimeout(3500, TimeUnit.MILLISECONDS)
        .readTimeout(6500, TimeUnit.MILLISECONDS)
        .callTimeout(10000, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun catalog(query: String): List<AnimeItem> = withContext(Dispatchers.IO) {
        val url = Uri.parse("https://anilibria.top/api/v1/anime/catalog/releases").buildUpon()
            .appendQueryParameter("limit", "36")
            .appendQueryParameter("page", "1")
            .appendQueryParameter("f[sorting]", "RATING_DESC")
            .apply { if (query.isNotBlank()) appendQueryParameter("f[search]", query) }
            .build().toString()
        val rows = getJson(url).optJSONArray("data")
        buildList {
            for (i in 0 until (rows?.length() ?: 0)) rows?.optJSONObject(i)?.let { add(parseAnime(it)) }
        }
    }

    suspend fun details(id: String): AnimeDetail = withContext(Dispatchers.IO) {
        val root = getJson("https://anilibria.top/api/v1/anime/releases/${enc(id)}")
        val anime = parseAnime(root)
        val episodes = buildList {
            val rows = root.optJSONArray("episodes")
            for (i in 0 until (rows?.length() ?: 0)) {
                val row = rows?.optJSONObject(i) ?: continue
                val streams = linkedMapOf<Int, String>()
                putStream(streams, 480, row.optString("hls_480"))
                putStream(streams, 720, row.optString("hls_720"))
                putStream(streams, 1080, row.optString("hls_1080"))
                add(EpisodeItem(
                    id = row.optString("id", "${anime.id}-$i"),
                    number = row.optDouble("ordinal", (i + 1).toDouble()),
                    title = row.optString("name", ""),
                    duration = row.optInt("duration", 0),
                    poster = absolute(bestImage(row)),
                    streams = streams
                ))
            }
        }.sortedBy { it.number }
        AnimeDetail(anime, episodes)
    }

    private fun getJson(url: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.5")
            .header("User-Agent", "YORU-Kotlin/1.0 Android")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("network")
            return JSONObject(response.body?.string().orEmpty())
        }
    }

    private fun parseAnime(j: JSONObject): AnimeItem {
        val name = j.optJSONObject("name")
        val poster = j.optJSONObject("poster")
        val type = j.optJSONObject("type")
        return AnimeItem(
            id = j.optString("id"),
            title = name?.optString("main")?.takeIf { it.isNotBlank() } ?: j.optString("title", "Аниме"),
            original = name?.optString("english").orEmpty(),
            poster = posterUrl(poster),
            year = j.optInt("year", 0),
            type = type?.optString("description") ?: "Аниме",
            episodes = j.optInt("episodes_total", 0),
            status = if (j.optBoolean("is_ongoing", false)) "Онгоинг" else "Вышло",
            description = strip(j.optString("description", ""))
        )
    }

    private fun posterUrl(poster: JSONObject?): String {
        val optimized = poster?.optJSONObject("optimized")
        val raw = optimized?.optString("preview")?.takeIf { it.isNotBlank() }
            ?: poster?.optString("preview")?.takeIf { it.isNotBlank() }
            ?: poster?.optString("src")
            ?: ""
        return absolute(raw)
    }

    private fun putStream(map: MutableMap<Int, String>, q: Int, raw: String) {
        val url = safe(raw)
        if (url.isNotBlank()) map[q] = url
    }

    private fun bestImage(j: JSONObject): String {
        val keys = arrayOf("preview", "preview_url", "thumbnail", "image", "image_url", "poster", "poster_url")
        for (key in keys) {
            val s = j.optString(key, "")
            if (s.isNotBlank()) return s
            val o = j.optJSONObject(key)
            if (o != null) return bestImage(o)
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
        if (!fixed.startsWith("http://") && !fixed.startsWith("https://")) return ""
        val uri = runCatching { Uri.parse(fixed) }.getOrNull() ?: return ""
        val host = uri.host?.lowercase(Locale.ROOT) ?: return ""
        if (!host.contains('.') || host == "localhost" || host.endsWith(".local")) return ""
        return fixed
    }

    private fun strip(value: String): String = value.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}

@Composable
fun YoruKotlinApp(vm: YoruKotlinVm = viewModel()) {
    val nav = rememberNavController()
    MaterialTheme(colorScheme = yoruColors()) {
        Surface(Modifier.fillMaxSize(), color = bg) {
            NavHost(navController = nav, startDestination = "home") {
                composable("home") { HomeScreen(vm, nav) }
                composable("details/{id}") { entry -> DetailsScreen(vm, nav, entry.arguments?.getString("id").orEmpty()) }
                composable("player/{id}/{episode}") { entry -> PlayerScreen(vm, nav, entry.arguments?.getString("id").orEmpty(), entry.arguments?.getString("episode").orEmpty()) }
            }
        }
    }
}

private fun yoruColors(): ColorScheme = darkColorScheme(
    primary = accent,
    onPrimary = ComposeColor(0xff23122f),
    background = bg,
    onBackground = text,
    surface = card,
    onSurface = text,
    surfaceVariant = panel,
    onSurfaceVariant = muted,
    secondary = ComposeColor(0xffb6e3ff)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: YoruKotlinVm, nav: NavHostController) {
    val state = vm.home
    LaunchedEffect(Unit) { if (state.items.isEmpty()) vm.loadHome() }
    Scaffold(
        containerColor = bg,
        topBar = { TopAppBar(title = { Text("YORU Kotlin", fontWeight = FontWeight.Black) }) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Поиск") },
                trailingIcon = { IconButton(onClick = { vm.loadHome() }) { Icon(Icons.Rounded.Search, null) } }
            )
            Spacer(Modifier.height(12.dp))
            if (state.loading) CenterSpinner()
            if (state.error.isNotBlank()) Text(state.error, color = accent, modifier = Modifier.padding(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(148.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.items, key = { it.id }) { anime -> AnimeCard(anime) { nav.navigate("details/${Uri.encode(anime.id)}") } }
            }
        }
    }
}

@Composable
fun AnimeCard(anime: AnimeItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(210.dp)) {
            AsyncImage(model = anime.poster, contentDescription = anime.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(ComposeColor.Transparent, ComposeColor(0xee07050b)))))
            Text(anime.status, color = text, modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).clip(RoundedCornerShape(12.dp)).background(ComposeColor(0xcc21182d)).padding(horizontal = 9.dp, vertical = 6.dp))
        }
        Column(Modifier.padding(12.dp)) {
            Text(anime.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(listOfNotNull(anime.year.takeIf { it > 0 }?.toString(), anime.type, anime.episodes.takeIf { it > 0 }?.let { "$it сер." }).joinToString(" · "), color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(vm: YoruKotlinVm, nav: NavHostController, id: String) {
    var state by remember(id) { mutableStateOf(DetailState()) }
    LaunchedEffect(id) {
        state = DetailState(loading = true)
        val result = runCatching { vm.repo.details(id) }
        state = result.fold({ DetailState(loading = false, detail = it) }, { DetailState(loading = false, error = "Карточка сейчас недоступна") })
    }
    Scaffold(
        containerColor = bg,
        topBar = { TopAppBar(title = { Text(state.detail?.anime?.title ?: "Карточка", maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.Rounded.ArrowBack, null) } }) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CenterSpinner()
                state.error.isNotBlank() -> Text(state.error, modifier = Modifier.align(Alignment.Center), color = accent)
                state.detail != null -> DetailContent(state.detail!!, nav)
            }
        }
    }
}

@Composable
fun DetailContent(detail: AnimeDetail, nav: NavHostController) {
    val anime = detail.anime
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = card), shape = RoundedCornerShape(28.dp)) {
                Row(Modifier.padding(14.dp)) {
                    AsyncImage(model = anime.poster, contentDescription = anime.title, modifier = Modifier.width(126.dp).height(184.dp).clip(RoundedCornerShape(22.dp)), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(anime.title, fontWeight = FontWeight.Black, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(8.dp))
                        Text(listOfNotNull(anime.year.takeIf { it > 0 }?.toString(), anime.type, anime.status).joinToString(" · "), color = muted)
                        Spacer(Modifier.height(10.dp))
                        AssistChip(onClick = {}, label = { Text("Озвучка: AniLibria.TV") })
                        AssistChip(onClick = {}, label = { Text("Серий: ${detail.episodes.size}") })
                    }
                }
            }
        }
        if (anime.description.isNotBlank()) item { Text(anime.description, color = text, lineHeight = MaterialTheme.typography.bodyLarge.lineHeight) }
        item { Text("Все серии", fontWeight = FontWeight.Black, color = text) }
        items(detail.episodes, key = { it.id }) { episode -> EpisodeRow(anime.id, episode, nav) }
    }
}

@Composable
fun EpisodeRow(animeId: String, episode: EpisodeItem, nav: NavHostController) {
    Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(episodeTitle(episode), fontWeight = FontWeight.Bold)
                    Text("AniLibria.TV", color = muted)
                }
                Button(onClick = { nav.navigate("player/${Uri.encode(animeId)}/${Uri.encode(episode.number.toString())}") }) { Text("Смотреть") }
            }
            if (episode.streams.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    episode.streams.keys.sorted().forEach { q -> AssistChip(onClick = {}, label = { Text("${q}p") }) }
                }
            }
        }
    }
}

@Composable
fun PlayerScreen(vm: YoruKotlinVm, nav: NavHostController, id: String, episodeValue: String) {
    val context = LocalContext.current
    val activity = context as Activity
    var detail by remember(id) { mutableStateOf<AnimeDetail?>(null) }
    var error by remember(id) { mutableStateOf("") }
    var episodeIndex by remember(id, episodeValue) { mutableStateOf(0) }
    var quality by remember(id, episodeValue) { mutableStateOf(0) }
    var ready by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(true) }
    var fill by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var speedIndex by remember { mutableStateOf(0) }
    val speeds = remember { listOf(1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f, 2.25f, 2.5f) }
    val player = remember {
        val control = DefaultLoadControl.Builder().setBufferDurationsMs(2200, 18000, 350, 850).build()
        ExoPlayer.Builder(context).setLoadControl(control).build()
    }

    LaunchedEffect(id) {
        val result = runCatching { vm.repo.details(id) }
        result.onSuccess { loaded ->
            detail = loaded
            val wanted = episodeValue.toDoubleOrNull() ?: loaded.episodes.firstOrNull()?.number ?: 1.0
            episodeIndex = loaded.episodes.indexOfFirst { it.number == wanted }.takeIf { it >= 0 } ?: 0
        }.onFailure { error = "Видео сейчас недоступно" }
    }

    val episodes = detail?.episodes.orEmpty()
    val episode = episodes.getOrNull(episodeIndex)
    LaunchedEffect(episode) { quality = chooseQuality(episode?.streams.orEmpty()) }
    val stream = episode?.streams?.get(quality)

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) { ready = playbackState == Player.STATE_READY }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(stream) {
        ready = false
        if (!stream.isNullOrBlank()) {
            player.setMediaItem(MediaItem.fromUri(stream))
            player.prepare()
            player.playWhenReady = true
        }
    }
    LaunchedEffect(speedIndex) { player.setPlaybackSpeed(speeds[speedIndex]) }
    DisposableEffect(Unit) { onDispose { player.release(); activity.yoruFullscreen(false) } }
    BackHandler(enabled = fullscreen) { fullscreen = false; activity.yoruFullscreen(false) }

    Box(Modifier.fillMaxSize().background(ComposeColor.Black)) {
        if (stream.isNullOrBlank() && error.isBlank() && episode != null) Text("У серии нет прямого потока", color = text, modifier = Modifier.align(Alignment.Center))
        AndroidView(
            factory = { PlayerView(it).apply { this.player = player; useController = false; resizeMode = if (fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT; setBackgroundColor(Color.BLACK) } },
            update = { it.resizeMode = if (fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT },
            modifier = Modifier.fillMaxSize()
        )
        if (!ready && error.isBlank()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(shape = CircleShape, color = ComposeColor(0xee21182d), shadowElevation = 14.dp, modifier = Modifier.size(74.dp)) { Box(contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(38.dp), strokeWidth = 3.dp, color = accent) } }
        }
        if (error.isNotBlank()) Text(error, color = accent, modifier = Modifier.align(Alignment.Center))
        PlayerOverlay(
            title = detail?.anime?.title ?: "YORU Kotlin",
            episode = episode,
            episodes = episodes,
            quality = quality,
            playing = playing,
            speed = speeds[speedIndex],
            fill = fill,
            fullscreen = fullscreen,
            onBack = { nav.popBackStack() },
            onQuality = { quality = it },
            onPrev = { if (episodeIndex > 0) episodeIndex-- },
            onNext = { if (episodeIndex + 1 < episodes.size) episodeIndex++ },
            onPlay = { if (player.isPlaying) player.pause() else player.play() },
            onRewind = { player.seekTo(max(0L, player.currentPosition - 10000L)) },
            onForward = { val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE; player.seekTo(min(duration, player.currentPosition + 10000L)) },
            onSpeed = { speedIndex = (speedIndex + 1) % speeds.size },
            onFullscreen = { fullscreen = !fullscreen; activity.yoruFullscreen(fullscreen) },
            onSmart = { if (!fullscreen) { fullscreen = true; activity.yoruFullscreen(true) }; fill = !fill }
        )
    }
}

@Composable
fun PlayerOverlay(
    title: String,
    episode: EpisodeItem?,
    episodes: List<EpisodeItem>,
    quality: Int,
    playing: Boolean,
    speed: Float,
    fill: Boolean,
    fullscreen: Boolean,
    onBack: () -> Unit,
    onQuality: (Int) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPlay: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onSpeed: () -> Unit,
    onFullscreen: () -> Unit,
    onSmart: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(ComposeColor(0xcc000000), ComposeColor.Transparent))).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIcon(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                if (episode != null) Text(episodeTitle(episode), color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            QualityMenu(episode?.streams?.keys?.sorted().orEmpty(), quality, onQuality)
            Spacer(Modifier.width(8.dp))
            GlassIcon(onClick = onFullscreen) { Icon(if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, null) }
            Spacer(Modifier.width(8.dp))
            GlassIcon(accented = true, onClick = onSmart) { Icon(Icons.Rounded.ZoomOutMap, null) }
        }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(ComposeColor.Transparent, ComposeColor(0xdd000000)))).padding(horizontal = 16.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            GlassIcon(enabled = episodes.indexOf(episode) > 0, onClick = onPrev) { Icon(Icons.Rounded.SkipPrevious, null) }
            Spacer(Modifier.width(10.dp))
            GlassIcon(onClick = onRewind) { Icon(Icons.Rounded.Replay10, null) }
            Spacer(Modifier.width(10.dp))
            GlassIcon(accented = true, big = true, onClick = onPlay) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null) }
            Spacer(Modifier.width(10.dp))
            GlassIcon(onClick = onForward) { Icon(Icons.Rounded.Forward10, null) }
            Spacer(Modifier.width(10.dp))
            GlassIcon(enabled = episodes.indexOf(episode) >= 0 && episodes.indexOf(episode) + 1 < episodes.size, onClick = onNext) { Icon(Icons.Rounded.SkipNext, null) }
            Spacer(Modifier.width(10.dp))
            Surface(shape = RoundedCornerShape(22.dp), color = ComposeColor(0xcc21182d), shadowElevation = 8.dp, modifier = Modifier.height(46.dp).clickable(onClick = onSpeed)) { Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 13.dp)) { Text(speedName(speed), fontWeight = FontWeight.Black) } }
        }
        AnimatedVisibility(visible = fill, modifier = Modifier.align(Alignment.Center)) {
            Surface(shape = RoundedCornerShape(24.dp), color = accent.copy(alpha = 0.92f), shadowElevation = 12.dp) { Text("SMART FILL", color = ComposeColor(0xff21152f), fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) }
        }
    }
}

@Composable
fun GlassIcon(enabled: Boolean = true, accented: Boolean = false, big: Boolean = false, onClick: () -> Unit, content: @Composable () -> Unit) {
    val size = if (big) 58.dp else 46.dp
    val color = if (accented) accent else ComposeColor(0xcc21182d)
    FilledIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(size)) { Box(Modifier.fillMaxSize().background(color, CircleShape), contentAlignment = Alignment.Center) { content() } }
}

@Composable
fun QualityMenu(qualities: List<Int>, quality: Int, onQuality: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(shape = RoundedCornerShape(20.dp), color = ComposeColor(0xcc21182d), modifier = Modifier.height(42.dp).clickable { open = true }) { Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) { Text(if (quality > 0) "${quality}p" else "AUTO", fontWeight = FontWeight.Bold) } }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            qualities.forEach { q -> DropdownMenuItem(text = { Text("${q}p") }, onClick = { open = false; onQuality(q) }) }
        }
    }
}

@Composable
fun CenterSpinner() {
    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = accent) }
}

private fun chooseQuality(streams: Map<Int, String>): Int = streams.keys.sorted().lastOrNull { it <= 720 } ?: streams.keys.sorted().firstOrNull() ?: 0
private fun episodeTitle(e: EpisodeItem): String = "Серия ${number(e.number)}" + if (e.title.isBlank()) "" else " · ${e.title}"
private fun number(n: Double): String = if (n == n.toInt().toDouble()) n.toInt().toString() else n.toString()
private fun speedName(speed: Float): String = if (speed == speed.toInt().toFloat()) "${speed.toInt()}×" else String.format(Locale.US, "%.2f×", speed).replace(".00", "")

private fun Activity.yoruFullscreen(enabled: Boolean) {
    if (enabled) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    } else {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }
}
