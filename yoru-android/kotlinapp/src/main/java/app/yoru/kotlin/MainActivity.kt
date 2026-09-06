package app.yoru.kotlin

import android.app.Activity
import android.app.Application
import android.app.PictureInPictureParams
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val bg = ComposeColor(0xff07050b)
private val surface = ComposeColor(0xff120c1b)
private val card = ComposeColor(0xff191123)
private val panel = ComposeColor(0xff241831)
private val accent = ComposeColor(0xffd9b8ff)
private val accent2 = ComposeColor(0xff91ddff)
private val text = ComposeColor(0xfffbf4ff)
private val muted = ComposeColor(0xffaa9ab7)
private val green = ComposeColor(0xff8ee6a5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.BLACK
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContent { YoruKotlinApp() }
    }

    fun enterYoruPip() {
        if (Build.VERSION.SDK_INT >= 26) runCatching {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        }
    }
}

class YoruKotlinVm(app: Application) : AndroidViewModel(app) {
    val repo = YoruRepository(app)
    val store = YoruStore(app)
    var home by mutableStateOf(CatalogState(loading = true))
        private set
    var catalog by mutableStateOf(CatalogState(loading = true))
        private set
    var settings by mutableStateOf(store.settings())
        private set
    var tick by mutableStateOf(0)
        private set
    var activeDownloads by mutableStateOf<Map<String, Float>>(emptyMap())
        private set
    var pulse by mutableStateOf("")
        private set

    init {
        loadHome()
        loadCatalog()
    }

    fun consumePulse() { pulse = "" }

    fun loadHome() {
        home = home.copy(loading = true, error = "")
        viewModelScope.launch {
            val result = runCatching { repo.catalog("", CatalogFilter(sort = "RATING_DESC"), 30) }
            home = result.fold({ CatalogState(loading = false, items = it) }, { CatalogState(loading = false, items = home.items, error = "Каталог откроется после сети") })
        }
    }

    fun setQuery(value: String) { catalog = catalog.copy(query = value) }

    fun loadCatalog(query: String = catalog.query, filter: CatalogFilter = catalog.filter) {
        catalog = catalog.copy(query = query, filter = filter, loading = true, error = "")
        viewModelScope.launch {
            val result = runCatching { repo.catalog(query.trim(), filter, 42) }
            catalog = result.fold({ CatalogState(query = query, filter = filter, loading = false, items = it) }, { CatalogState(query = query, filter = filter, loading = false, items = catalog.items, error = "Сеть слабая. Показываю сохранённое, если оно есть") })
        }
    }

    fun updateSettings(value: AppSettings) {
        settings = value
        store.updateSettings(value)
        tick++
    }

    fun toggleFavorite(anime: AnimeItem) {
        store.toggleFavorite(anime)
        tick++
        pulse = if (store.favorite(anime)) "В коллекции" else "Убрано"
    }

    fun saveProgress(anime: AnimeItem, episode: EpisodeItem, position: Int, duration: Int) {
        store.saveProgress(anime, episode.number, position, duration, episode.voice)
        tick++
    }

    fun removeDownload(record: DownloadRecord) {
        runCatching { File(record.path).parentFile?.deleteRecursively() }
        store.removeDownload(record.id)
        tick++
        pulse = "Загрузка удалена"
    }

    fun clearHistory() {
        store.clearHistory()
        tick++
        pulse = "История очищена"
    }

    fun download(anime: AnimeItem, episode: EpisodeItem, quality: Int) {
        val key = "${anime.id}:${episode.number}"
        if (activeDownloads.containsKey(key)) return
        if (settings.wifiDownloads && !wifi()) {
            pulse = "Загрузка ждёт Wi‑Fi"
            return
        }
        activeDownloads = activeDownloads + (key to 0f)
        viewModelScope.launch {
            val result = runCatching { repo.saveEpisode(anime, episode, quality) { p -> viewModelScope.launch { activeDownloads = activeDownloads + (key to p.coerceIn(0f, 1f)) } } }
            activeDownloads = activeDownloads - key
            result.onSuccess {
                store.saveDownload(it)
                tick++
                pulse = "Серия сохранена"
            }.onFailure { pulse = "Не удалось сохранить серию" }
        }
    }

    private fun wifi(): Boolean {
        val cm = getApplication<Application>().getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}


@Composable
fun YoruKotlinApp(vm: YoruKotlinVm = viewModel()) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route.orEmpty()
    val density = LocalDensity.current
    val fontScale = vm.settings.fontScale / 100f
    val context = LocalContext.current
    LaunchedEffect(vm.pulse) {
        if (vm.pulse.isNotBlank()) {
            Toast.makeText(context, vm.pulse, Toast.LENGTH_SHORT).show()
            vm.consumePulse()
        }
    }
    MaterialTheme(colorScheme = yoruColors()) {
        CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * fontScale)) {
            Surface(Modifier.fillMaxSize(), color = bg) {
                if (route.startsWith("player") || route.startsWith("offline") || route.startsWith("image") || route.startsWith("details")) {
                    YoruNav(vm, nav, Modifier.fillMaxSize())
                } else {
                    Scaffold(
                        containerColor = bg,
                        topBar = { YoruTopBar(nav) },
                        bottomBar = { YoruBottomBar(nav, route.ifBlank { "home" }) }
                    ) { padding -> YoruNav(vm, nav, Modifier.padding(padding)) }
                }
            }
        }
    }
}

@Composable
private fun YoruNav(vm: YoruKotlinVm, nav: NavHostController, modifier: Modifier) {
    NavHost(navController = nav, startDestination = "home", modifier = modifier) {
        composable("home") { HomeScreen(vm, nav) }
        composable("catalog") { CatalogScreen(vm, nav) }
        composable("library") { LibraryScreen(vm, nav) }
        composable("downloads") { DownloadsScreen(vm, nav) }
        composable("calendar") { CalendarScreen(vm, nav) }
        composable("settings") { SettingsScreen(vm) }
        composable("details/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { DetailsScreen(vm, nav, it.arguments?.getString("id").orEmpty()) }
        composable("player/{id}/{episode}", arguments = listOf(navArgument("id") { type = NavType.StringType }, navArgument("episode") { type = NavType.StringType })) { PlayerScreen(vm, nav, it.arguments?.getString("id").orEmpty(), it.arguments?.getString("episode").orEmpty(), null) }
        composable("offline/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { PlayerScreen(vm, nav, "", "", it.arguments?.getString("id").orEmpty()) }
        composable("image/{url}", arguments = listOf(navArgument("url") { type = NavType.StringType })) { ImageScreen(nav, it.arguments?.getString("url").orEmpty()) }
    }
}

private fun yoruColors(): ColorScheme = darkColorScheme(
    primary = accent,
    onPrimary = ComposeColor(0xff21152f),
    background = bg,
    onBackground = text,
    surface = card,
    onSurface = text,
    surfaceVariant = panel,
    onSurfaceVariant = muted,
    secondary = accent2
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YoruTopBar(nav: NavHostController) {
    TopAppBar(
        title = { Text("YORU.", fontWeight = FontWeight.Black) },
        actions = {
            IconButton({ nav.navigateRoot("catalog") }) { Icon(Icons.Rounded.Search, null) }
            IconButton({ nav.navigate("settings") }) { Icon(Icons.Rounded.Settings, null) }
        }
    )
}

@Composable
private fun YoruBottomBar(nav: NavHostController, route: String) {
    val tabs = listOf("home" to "Главная", "catalog" to "Каталог", "library" to "Коллекция", "downloads" to "Загрузки", "calendar" to "Календарь")
    NavigationBar(containerColor = surface) {
        tabs.forEach { (target, label) ->
            NavigationBarItem(
                selected = route == target,
                onClick = { nav.navigateRoot(target) },
                icon = { Text(label.take(1), fontWeight = FontWeight.Black) },
                label = { Text(label, maxLines = 1) }
            )
        }
    }
}

private fun NavHostController.navigateRoot(route: String) {
    navigate(route) {
        popUpTo("home") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun HomeScreen(vm: YoruKotlinVm, nav: NavHostController) {
    val state = vm.home
    val settings = vm.settings
    val recent = remember(vm.tick) { vm.store.recent() }
    val favorites = remember(vm.tick) { vm.store.favorites() }
    val listState = rememberLazyListState()
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionLabel("ВАШЕ СЛЕДУЮЩЕЕ ЛЮБИМОЕ АНИМЕ") }
        item { QuickStart(vm, nav, recent) }
        if (state.loading) item { CenterSpinner() }
        state.items.firstOrNull()?.let { anime -> item { HeroCard(anime, settings, nav) } }
        if (state.error.isNotBlank()) item { SmallNote(state.error) }
        shelf("Главное сейчас", state.items.take(8), settings, nav)
        shelf("В коллекции", favorites.take(8), settings, nav)
        shelf("Фильмы на вечер", state.items.filter { it.type.contains("фильм", true) || it.type.contains("movie", true) }.take(8), settings, nav)
        shelf("Сейчас выходит", state.items.filter { it.status.contains("выходит", true) }.take(8), settings, nav)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.shelf(title: String, rows: List<AnimeItem>, settings: AppSettings, nav: NavHostController) {
    if (rows.isEmpty()) return
    item { SectionTitle(title) }
    item { LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 8.dp)) { items(rows, key = { it.id }) { AnimeMiniCard(it, settings) { nav.navigate("details/${Uri.encode(it.id)}") } } } }
}

@Composable
private fun QuickStart(vm: YoruKotlinVm, nav: NavHostController, recent: List<WatchProgress>) {
    val last = recent.firstOrNull()
    Card(colors = CardDefaults.cardColors(containerColor = card), shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Быстрый старт", fontWeight = FontWeight.Black, color = text)
            Spacer(Modifier.height(6.dp))
            Text(if (last == null) "Каталог открывается сразу, видео запускается без лишних выборов." else "Продолжить: ${last.anime.displayTitle(vm.settings.originalTitles)} · серия ${numberLabel(last.episode)}", color = muted)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { if (last == null) nav.navigateRoot("catalog") else nav.navigate("player/${Uri.encode(last.anime.id)}/${Uri.encode(last.episode.toString())}") }, modifier = Modifier.weight(1f)) { Text(if (last == null) "Открыть каталог" else "Продолжить") }
                OutlinedButton(onClick = { nav.navigateRoot("downloads") }, modifier = Modifier.weight(1f)) { Text("Загрузки") }
            }
        }
    }
}

@Composable
private fun HeroCard(anime: AnimeItem, settings: AppSettings, nav: NavHostController) {
    Card(Modifier.fillMaxWidth().height(330.dp).clickable { nav.navigate("details/${Uri.encode(anime.id)}") }, colors = CardDefaults.cardColors(containerColor = card), shape = RoundedCornerShape(28.dp)) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(model = anime.poster, contentDescription = anime.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(ComposeColor(0x22120c1b), ComposeColor(0xee21162e)))))
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                SectionLabel("ВЫБОР YORU")
                Spacer(Modifier.height(10.dp))
                Text(anime.displayTitle(settings.originalTitles), color = text, fontWeight = FontWeight.Black, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(anime.meta(), color = ComposeColor(0xffdbcce7))
                Spacer(Modifier.height(14.dp))
                Button(onClick = { nav.navigate("details/${Uri.encode(anime.id)}") }) { Text("Начать смотреть") }
            }
        }
    }
}

@Composable
private fun CatalogScreen(vm: YoruKotlinVm, nav: NavHostController) {
    val state = vm.catalog
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        SearchLine(state.query, onChange = vm::setQuery, onSearch = { vm.loadCatalog() })
        Spacer(Modifier.height(10.dp))
        FilterRow(state.filter) { vm.loadCatalog(filter = it) }
        Spacer(Modifier.height(12.dp))
        if (state.loading) CenterSpinner()
        if (state.error.isNotBlank()) SmallNote(state.error)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(148.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.items, key = { it.id }) { anime -> AnimeCard(anime, vm.settings) { nav.navigate("details/${Uri.encode(anime.id)}") } }
        }
    }
}

@Composable
private fun SearchLine(value: String, onChange: (String) -> Unit, onSearch: () -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Поиск") },
        trailingIcon = { IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, null) } },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() })
    )
}

@Composable
private fun FilterRow(filter: CatalogFilter, onFilter: (CatalogFilter) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChipText("Все", filter == CatalogFilter()) { onFilter(CatalogFilter()) }
        FilterChipText("Онгоинги", filter.status == "IS_ONGOING") { onFilter(filter.copy(status = if (filter.status == "IS_ONGOING") "" else "IS_ONGOING")) }
        FilterChipText("Фильмы", filter.type == "MOVIE") { onFilter(filter.copy(type = if (filter.type == "MOVIE") "" else "MOVIE")) }
        FilterChipText("Сериалы", filter.type == "TV") { onFilter(filter.copy(type = if (filter.type == "TV") "" else "TV")) }
        FilterChipText("2026", filter.year == 2026) { onFilter(filter.copy(year = if (filter.year == 2026) 0 else 2026)) }
        FilterChipText("Рейтинг", filter.sort == "RATING_DESC") { onFilter(filter.copy(sort = "RATING_DESC")) }
    }
}

@Composable
private fun FilterChipText(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = if (selected) accent else panel, modifier = Modifier.clickable(onClick = onClick)) {
        Text(label, color = if (selected) ComposeColor(0xff21152f) else text, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

@Composable
private fun AnimeCard(anime: AnimeItem, settings: AppSettings, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = card), shape = RoundedCornerShape(22.dp), elevation = CardDefaults.cardElevation(8.dp)) {
        Box(Modifier.fillMaxWidth().height(210.dp)) {
            AsyncImage(model = anime.poster, contentDescription = anime.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(ComposeColor.Transparent, ComposeColor(0xee07050b)))))
            Text(anime.status.ifBlank { "YORU" }, color = text, modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).clip(RoundedCornerShape(12.dp)).background(ComposeColor(0xcc21182d)).padding(horizontal = 9.dp, vertical = 6.dp))
        }
        Column(Modifier.padding(12.dp)) {
            Text(anime.displayTitle(settings.originalTitles), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(anime.meta(), color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AnimeMiniCard(anime: AnimeItem, settings: AppSettings, onClick: () -> Unit) {
    Card(Modifier.width(138.dp).clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = card), shape = RoundedCornerShape(20.dp)) {
        AsyncImage(model = anime.poster, contentDescription = anime.title, modifier = Modifier.fillMaxWidth().height(190.dp), contentScale = ContentScale.Crop)
        Text(anime.displayTitle(settings.originalTitles), modifier = Modifier.padding(10.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LibraryScreen(vm: YoruKotlinVm, nav: NavHostController) {
    var mode by remember { mutableStateOf("fav") }
    val favorites = remember(vm.tick) { vm.store.favorites() }
    val recent = remember(vm.tick) { vm.store.recent() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("Коллекция") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChipText("Избранное", mode == "fav") { mode = "fav" }
                FilterChipText("История", mode == "history") { mode = "history" }
                FilterChipText("Статистика", mode == "stats") { mode = "stats" }
            }
        }
        if (mode == "fav") {
            if (favorites.isEmpty()) item { EmptyCard("Коллекция пуста. Откройте карточку и добавьте тайтл.") }
            items(favorites, key = { it.id }) { anime -> LibraryAnimeRow(anime, vm.settings, "В коллекции") { nav.navigate("details/${Uri.encode(anime.id)}") } }
        } else if (mode == "history") {
            if (recent.isEmpty()) item { EmptyCard("История появится после просмотра.") }
            items(recent, key = { it.anime.id }) { progress -> LibraryAnimeRow(progress.anime, vm.settings, "Серия ${numberLabel(progress.episode)} · ${durationLabel(progress.position)}") { nav.navigate("player/${Uri.encode(progress.anime.id)}/${Uri.encode(progress.episode.toString())}") } }
        } else {
            val stats = remember(vm.tick) { vm.store.stats() }
            item { StatGrid(stats.optInt("favorites"), stats.optInt("history"), stats.optInt("downloads"), stats.optInt("minutes")) }
            item { OutlinedButton(onClick = { vm.clearHistory() }, modifier = Modifier.fillMaxWidth()) { Text("Очистить историю") } }
        }
    }
}


@Composable
private fun LibraryAnimeRow(anime: AnimeItem, settings: AppSettings, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = card), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = anime.poster, contentDescription = anime.title, modifier = Modifier.width(54.dp).height(76.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(anime.displayTitle(settings.originalTitles), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("▶", color = accent, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun StatGrid(fav: Int, history: Int, downloads: Int, minutes: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Metric("В коллекции", fav.toString())
        Metric("В истории", history.toString())
        Metric("Загрузок", downloads.toString())
        Metric("Минут просмотра", minutes.toString())
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = panel, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = muted, modifier = Modifier.weight(1f))
            Text(value, color = accent, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun DownloadsScreen(vm: YoruKotlinVm, nav: NavHostController) {
    val records = remember(vm.tick) { vm.store.downloadRecords() }
    val active = vm.activeDownloads
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("Загрузки") }
        item { SmallNote(if (vm.settings.wifiDownloads) "Сохранение ждёт Wi‑Fi. Готовые серии открываются без сети." else "Сохранение разрешено по текущей сети.") }
        if (active.isNotEmpty()) items(active.entries.toList(), key = { it.key }) { row -> DownloadProgressRow(row.key.substringAfter(':'), row.value) }
        if (records.isEmpty() && active.isEmpty()) item { EmptyCard("Скачанных серий пока нет. Кнопка сохранения находится рядом с сериями.") }
        items(records, key = { it.id }) { record -> DownloadRow(record, onPlay = { nav.navigate("offline/${Uri.encode(record.id)}") }, onDelete = { vm.removeDownload(record) }) }
    }
}

@Composable
private fun DownloadProgressRow(label: String, progress: Float) {
    Card(colors = CardDefaults.cardColors(containerColor = card), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text("Сохраняю серию $label", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Slider(value = progress, onValueChange = {}, enabled = false)
        }
    }
}

@Composable
private fun DownloadRow(record: DownloadRecord, onPlay: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = card), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = record.episode.poster.ifBlank { record.anime.poster }, contentDescription = record.anime.title, modifier = Modifier.width(64.dp).height(48.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).clickable(onClick = onPlay)) {
                Text(record.anime.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Серия ${numberLabel(record.episode.number)} · ${qualityName(record.quality)} · ${bytesLabel(record.bytes)}", color = muted, maxLines = 1)
            }
            IconButton(onClick = onPlay) { Icon(Icons.Rounded.PlayArrow, null, tint = accent) }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, null, tint = muted) }
        }
    }
}

@Composable
private fun CalendarScreen(vm: YoruKotlinVm, nav: NavHostController) {
    val rows = vm.home.items.filter { it.status.contains("выходит", true) }.ifEmpty { vm.home.items.take(12) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("Календарь выхода") }
        item { SmallNote("Лёгкий режим: показываем активные тайтлы без тяжёлой синхронизации при запуске.") }
        items(rows, key = { it.id }) { anime -> LibraryAnimeRow(anime, vm.settings, anime.meta()) { nav.navigate("details/${Uri.encode(anime.id)}") } }
    }
}

@Composable
private fun SettingsScreen(vm: YoruKotlinVm) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/')?.takeLast(48) ?: "Свой шрифт"
            vm.updateSettings(vm.settings.copy(fontName = name))
            Toast.makeText(context, "Шрифт выбран", Toast.LENGTH_SHORT).show()
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SectionTitle("Настройки") }
        item { SettingsCard("Просмотр") {
            QualityPicker(vm.settings.quality) { vm.updateSettings(vm.settings.copy(quality = it)) }
            SwitchRow("Следующая серия автоматически", vm.settings.autoNext) { vm.updateSettings(vm.settings.copy(autoNext = it)) }
            SwitchRow("Пропуск начала", vm.settings.autoSkipOpening) { vm.updateSettings(vm.settings.copy(autoSkipOpening = it)) }
            SpeedPicker(vm.settings.playbackSpeed) { vm.updateSettings(vm.settings.copy(playbackSpeed = it)) }
        } }
        item { SettingsCard("Озвучка") {
            VoicePicker(vm.settings.preferredVoice) { vm.updateSettings(vm.settings.copy(preferredVoice = it, onlyPreferredVoice = true)) }
            SwitchRow("Показывать выбранную озвучку", vm.settings.onlyPreferredVoice) { vm.updateSettings(vm.settings.copy(onlyPreferredVoice = it)) }
        } }
        item { SettingsCard("Сеть") {
            SwitchRow("Экономия трафика", vm.settings.dataSaver) { vm.updateSettings(vm.settings.copy(dataSaver = it)) }
            SwitchRow("Загрузки только по Wi‑Fi", vm.settings.wifiDownloads) { vm.updateSettings(vm.settings.copy(wifiDownloads = it)) }
        } }
        item { SettingsCard("Комфорт") {
            SwitchRow("Без спойлеров", vm.settings.spoilerSafe) { vm.updateSettings(vm.settings.copy(spoilerSafe = it)) }
            SwitchRow("Оригинальные названия", vm.settings.originalTitles) { vm.updateSettings(vm.settings.copy(originalTitles = it)) }
            Text("Размер текста: ${vm.settings.fontScale}%", color = muted)
            Slider(value = vm.settings.fontScale.toFloat(), onValueChange = { vm.updateSettings(vm.settings.copy(fontScale = it.toInt())) }, valueRange = 85f..130f, steps = 8)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Шрифт: ${vm.settings.fontName}", color = muted, modifier = Modifier.weight(1f))
                TextButton(onClick = { launcher.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-otf")) }) { Text("Выбрать") }
            }
        } }
        item { SettingsCard("Данные") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { copyText(context, vm.store.exportData()); Toast.makeText(context, "Экспорт скопирован", Toast.LENGTH_SHORT).show() }, modifier = Modifier.weight(1f)) { Text("Экспорт") }
                OutlinedButton(onClick = { Toast.makeText(context, "Импорт через экспорт-файл будет добавлен без замедления старта", Toast.LENGTH_SHORT).show() }, modifier = Modifier.weight(1f)) { Text("Импорт") }
            }
        } }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = card), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.Black)
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = text, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun QualityPicker(current: Int, onPick: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(360, 480, 720, 1080).forEach { q -> FilterChipText(qualityName(q), current == q) { onPick(q) } }
    }
}

@Composable
private fun VoicePicker(current: String, onPick: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        voiceChoices().forEach { voice -> FilterChipText(voice, current == voice) { onPick(voice) } }
    }
}

@Composable
private fun SpeedPicker(current: Float, onPick: (Float) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        speedChoices().forEach { speed -> FilterChipText(speedLabel(speed), abs(current - speed) < 0.01f) { onPick(speed) } }
    }
}

private fun copyText(context: Context, value: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(ClipData.newPlainText("YORU", value))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsScreen(vm: YoruKotlinVm, nav: NavHostController, id: String) {
    var state by remember(id) { mutableStateOf(DetailState()) }
    LaunchedEffect(id) {
        state = DetailState(loading = true)
        val result = runCatching { vm.repo.details(id) }
        state = result.fold({ DetailState(loading = false, detail = it) }, { DetailState(loading = false, error = "Карточка сейчас недоступна") })
    }
    Scaffold(containerColor = bg, topBar = { TopAppBar(title = { Text(state.detail?.anime?.displayTitle(vm.settings.originalTitles) ?: "Карточка", maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.Rounded.ArrowBack, null) } }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CenterSpinner()
                state.error.isNotBlank() -> Text(state.error, modifier = Modifier.align(Alignment.Center), color = accent)
                state.detail != null -> DetailContent(vm, nav, state.detail!!)
            }
        }
    }
}

@Composable
private fun DetailContent(vm: YoruKotlinVm, nav: NavHostController, detail: AnimeDetail) {
    val anime = detail.anime
    val settings = vm.settings
    val progress = remember(vm.tick, anime.id) { vm.store.progress(anime.id) }
    val startEpisode = progress?.episode ?: detail.episodes.firstOrNull()?.number ?: 1.0
    val quality = chooseQuality(detail.episodes.firstOrNull { abs(it.number - startEpisode) < 0.001 }?.streams.orEmpty(), settings.quality)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { DetailHero(vm, anime) }
        item { GenreChips(anime) }
        item { AboutBlock(anime, settings) }
        item { ScreenshotBlock(anime, nav) }
        item { TrailerBlock(anime) }
        item { ActionBlock(vm, nav, detail, startEpisode, quality) }
        if (progress != null) item { SmallNote("Вы смотрели серию ${numberLabel(progress.episode)} · ${durationLabel(progress.position)}") }
        item { SectionTitle("Серии") }
        if (detail.episodes.isEmpty()) item { EmptyCard("Список серий появится после обновления карточки.") }
        items(detail.episodes, key = { it.id }) { episode -> EpisodeRow(vm, nav, anime, episode) }
        if (detail.related.isNotEmpty()) {
            item { SectionTitle("Связанные аниме и фильмы") }
            items(detail.related, key = { it.id }) { item -> LibraryAnimeRow(item, settings, item.meta()) { nav.navigate("details/${Uri.encode(item.id)}") } }
        }
        if (detail.similar.isNotEmpty()) {
            item { SectionTitle("Похожее") }
            item { LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(detail.similar, key = { it.id }) { AnimeMiniCard(it, settings) { nav.navigate("details/${Uri.encode(it.id)}") } } } }
        }
    }
}

@Composable
private fun DetailHero(vm: YoruKotlinVm, anime: AnimeItem) {
    val fav = remember(vm.tick, anime.id) { vm.store.favorite(anime) }
    Card(colors = CardDefaults.cardColors(containerColor = card), shape = RoundedCornerShape(28.dp)) {
        Row(Modifier.padding(14.dp)) {
            AsyncImage(model = anime.poster, contentDescription = anime.title, modifier = Modifier.width(126.dp).height(184.dp).clip(RoundedCornerShape(22.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("YORU SOURCE")
                    Spacer(Modifier.weight(1f))
                    IconButton({ vm.toggleFavorite(anime) }) { Icon(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = if (fav) accent else muted) }
                }
                Spacer(Modifier.height(8.dp))
                Text(anime.displayTitle(vm.settings.originalTitles), fontWeight = FontWeight.Black, maxLines = 5, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(9.dp))
                Text(anime.meta(), color = muted)
                if (anime.score > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(String.format(Locale.US, "★ %.2f", anime.score), color = accent, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(8.dp))
                Text("Озвучка: ${vm.settings.preferredVoice}", color = muted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun GenreChips(anime: AnimeItem) {
    if (anime.genres.isEmpty() && anime.age.isBlank() && anime.status.isBlank()) return
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        anime.genres.take(10).forEach { AssistChip(onClick = {}, label = { Text(it) }) }
        if (anime.status.isNotBlank()) AssistChip(onClick = {}, label = { Text(anime.status) })
        if (anime.age.isNotBlank()) AssistChip(onClick = {}, label = { Text(anime.age) })
    }
}

@Composable
private fun AboutBlock(anime: AnimeItem, settings: AppSettings) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Об истории")
        Text(if (settings.spoilerSafe && anime.description.isNotBlank()) "Описание скрыто режимом без спойлеров." else anime.description.ifBlank { "Описание пока недоступно." }, color = ComposeColor(0xffcdbdde), lineHeight = MaterialTheme.typography.bodyLarge.lineHeight)
    }
}

@Composable
private fun ScreenshotBlock(anime: AnimeItem, nav: NavHostController) {
    val rows = anime.screenshots.ifEmpty { listOf(anime.poster).filter { it.isNotBlank() } }
    if (rows.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Скриншоты")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(rows.take(12)) { url ->
                AsyncImage(model = url, contentDescription = anime.title, modifier = Modifier.width(180.dp).height(102.dp).clip(RoundedCornerShape(16.dp)).clickable { nav.navigate("image/${Uri.encode(url)}") }, contentScale = ContentScale.Crop)
            }
        }
    }
}

@Composable
private fun TrailerBlock(anime: AnimeItem) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth().clickable {
        val url = anime.trailerUrl.ifBlank { "https://www.youtube.com/results?search_query=${Uri.encode(anime.title + " trailer")}" }
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }, colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Трейлер", fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            Text("Открыть", color = accent, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ActionBlock(vm: YoruKotlinVm, nav: NavHostController, detail: AnimeDetail, startEpisode: Double, quality: Int) {
    val anime = detail.anime
    val episode = detail.episodes.firstOrNull { abs(it.number - startEpisode) < 0.001 } ?: detail.episodes.firstOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = { if (episode != null) nav.navigate("player/${Uri.encode(anime.id)}/${Uri.encode(episode.number.toString())}") }, enabled = episode != null, modifier = Modifier.fillMaxWidth()) {
            Text("Смотреть · ${vm.settings.preferredVoice} · ${qualityName(quality)}")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { vm.toggleFavorite(anime) }, modifier = Modifier.weight(1f)) { Text(if (vm.store.favorite(anime)) "В коллекции" else "В коллекцию") }
            OutlinedButton(onClick = { if (episode != null) vm.download(anime, episode, quality) }, enabled = episode != null, modifier = Modifier.weight(1f)) { Text("Скачать · ${estimateSize(quality, episode?.duration ?: 0)}") }
        }
    }
}

@Composable
private fun EpisodeRow(vm: YoruKotlinVm, nav: NavHostController, anime: AnimeItem, episode: EpisodeItem) {
    val settings = vm.settings
    val quality = chooseQuality(episode.streams, settings.quality)
    val done = remember(vm.tick, anime.id, episode.number) { vm.store.downloadFor(anime.id, episode.number) }
    val key = "${anime.id}:${episode.number}"
    val progress = vm.activeDownloads[key]
    Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { if (done != null) nav.navigate("offline/${Uri.encode(done.id)}") else nav.navigate("player/${Uri.encode(anime.id)}/${Uri.encode(episode.number.toString())}") }) {
                Box(Modifier.width(94.dp).height(56.dp).clip(RoundedCornerShape(13.dp)).background(surface)) {
                    AsyncImage(model = episode.poster.ifBlank { anime.poster }, contentDescription = episode.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Surface(shape = CircleShape, color = ComposeColor(0x77000000), modifier = Modifier.size(34.dp).align(Alignment.Center)) { Box(contentAlignment = Alignment.Center) { Text("▶", color = text, fontWeight = FontWeight.Black) } }
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(episode.label(settings.spoilerSafe), color = text, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text("${episode.voice} · ${durationLabel(episode.duration)}", color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { if (done != null) nav.navigate("offline/${Uri.encode(done.id)}") else vm.download(anime, episode, quality) }) { Icon(if (done != null) Icons.Rounded.PlayArrow else Icons.Rounded.Download, null, tint = accent) }
            }
            if (progress != null) Slider(value = progress, onValueChange = {}, enabled = false)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                episode.streams.keys.sorted().forEach { q -> AssistChip(onClick = {}, label = { Text(qualityName(q)) }) }
                AssistChip(onClick = {}, label = { Text(estimateSize(quality, episode.duration)) })
            }
        }
    }
}

@Composable
private fun PlayerScreen(vm: YoruKotlinVm, nav: NavHostController, id: String, episodeValue: String, offlineId: String?) {
    val context = LocalContext.current
    val activity = context as Activity
    var detail by remember(id, offlineId) { mutableStateOf<AnimeDetail?>(null) }
    var error by remember(id, offlineId) { mutableStateOf("") }
    var episodeIndex by remember(id, episodeValue, offlineId) { mutableStateOf(0) }
    var quality by remember(id, episodeValue, offlineId) { mutableStateOf(vm.settings.quality) }
    var ready by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(true) }
    var fill by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var controls by remember { mutableStateOf(true) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var seekFlash by remember { mutableStateOf("") }
    var startApplied by remember(id, episodeValue, offlineId) { mutableStateOf(false) }
    val player = remember {
        val control = DefaultLoadControl.Builder().setBufferDurationsMs(1700, if (vm.settings.dataSaver) 12000 else 18000, 250, 650).build()
        ExoPlayer.Builder(context).setLoadControl(control).build().apply { setPlaybackSpeed(vm.settings.playbackSpeed) }
    }

    LaunchedEffect(id, offlineId) {
        error = ""
        val result = runCatching {
            if (offlineId != null) {
                val record = vm.store.download(offlineId) ?: error("offline")
                AnimeDetail(record.anime, listOf(record.episode.copy(streams = mapOf(record.quality to File(record.path).toURI().toString()))))
            } else vm.repo.details(id)
        }
        result.onSuccess { loaded ->
            detail = loaded
            val wanted = episodeValue.toDoubleOrNull() ?: vm.store.progress(loaded.anime.id)?.episode ?: loaded.episodes.firstOrNull()?.number ?: 1.0
            episodeIndex = loaded.episodes.indexOfFirst { abs(it.number - wanted) < 0.001 }.takeIf { it >= 0 } ?: 0
        }.onFailure { error = "Видео сейчас недоступно" }
    }

    val loaded = detail
    val episodes = loaded?.episodes.orEmpty()
    val episode = episodes.getOrNull(episodeIndex)
    LaunchedEffect(episode) { quality = chooseQuality(episode?.streams.orEmpty(), vm.settings.quality); startApplied = false }
    val stream = episode?.streams?.get(quality) ?: episode?.streams?.entries?.sortedByDescending { it.key }?.firstOrNull()?.value

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                ready = playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED
                if (playbackState == Player.STATE_ENDED && vm.settings.autoNext && episodeIndex + 1 < episodes.size) episodeIndex++
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(stream) {
        ready = false
        if (!stream.isNullOrBlank()) {
            val keep = player.currentPosition.takeIf { it > 5000 && startApplied } ?: ((if (offlineId == null && loaded != null) vm.store.progress(loaded.anime.id)?.position ?: 0 else 0) * 1000L)
            player.setMediaItem(MediaItem.fromUri(stream))
            player.prepare()
            if (keep > 0) player.seekTo(keep)
            player.playWhenReady = true
            startApplied = true
            val next = episodes.getOrNull(episodeIndex + 1)?.streams?.get(quality)
            if (!vm.settings.dataSaver && next != null) vm.repo.prewarm(next)
        }
    }

    LaunchedEffect(vm.settings.playbackSpeed) { player.setPlaybackSpeed(vm.settings.playbackSpeed) }

    LaunchedEffect(player, episode, loaded) {
        var lastSave = 0L
        while (isActive) {
            position = player.currentPosition.coerceAtLeast(0)
            duration = player.duration.takeIf { it > 0 } ?: 0L
            val sec = (position / 1000).toInt()
            if (episode != null && loaded != null && offlineId == null && sec - lastSave >= 4) {
                lastSave = sec.toLong()
                vm.saveProgress(loaded.anime, episode, sec, (duration / 1000).toInt())
            }
            if (episode != null && vm.settings.autoSkipOpening && episode.openingEnd > episode.openingStart && sec in episode.openingStart until episode.openingEnd) player.seekTo(episode.openingEnd * 1000L)
            delay(1000)
        }
    }

    DisposableEffect(Unit) { onDispose { episode?.let { ep -> loaded?.let { if (offlineId == null) vm.saveProgress(it.anime, ep, (player.currentPosition / 1000).toInt(), (player.duration.takeIf { d -> d > 0 } ?: 0L).let { d -> (d / 1000).toInt() }) } }; player.release(); activity.yoruFullscreen(false) } }
    BackHandler(enabled = fullscreen) { fullscreen = false; activity.yoruFullscreen(false) }

    Box(Modifier.fillMaxSize().background(ComposeColor.Black).pointerInput(episodeIndex) {
        detectTapGestures(
            onTap = { controls = !controls },
            onDoubleTap = { point: Offset ->
                if (point.x < size.width / 2f) {
                    player.seekTo(max(0L, player.currentPosition - 10000L)); seekFlash = "−10"
                } else {
                    val d = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                    player.seekTo(min(d, player.currentPosition + 10000L)); seekFlash = "+10"
                }
                controls = true
            }
        )
    }) {
        AndroidView(
            factory = { PlayerView(it).apply { this.player = player; useController = false; resizeMode = if (fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT; setBackgroundColor(Color.BLACK) } },
            update = { it.resizeMode = if (fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT },
            modifier = Modifier.fillMaxSize()
        )
        if (!ready && error.isBlank()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Surface(shape = CircleShape, color = ComposeColor(0xee21182d), shadowElevation = 14.dp, modifier = Modifier.size(74.dp)) { Box(contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(38.dp), strokeWidth = 3.dp, color = accent) } } }
        if (stream.isNullOrBlank() && error.isBlank() && episode != null) Text("У серии нет прямого потока", color = text, modifier = Modifier.align(Alignment.Center))
        if (error.isNotBlank()) Text(error, color = accent, modifier = Modifier.align(Alignment.Center))
        AnimatedVisibility(visible = seekFlash.isNotBlank(), modifier = Modifier.align(Alignment.Center)) { Surface(shape = CircleShape, color = ComposeColor(0xaa000000), modifier = Modifier.size(82.dp)) { Box(contentAlignment = Alignment.Center) { Text(seekFlash, color = text, fontWeight = FontWeight.Black) } } }
        LaunchedEffect(seekFlash) { if (seekFlash.isNotBlank()) { delay(650); seekFlash = "" } }
        AnimatedVisibility(visible = controls) {
            PlayerOverlay(
                title = loaded?.anime?.displayTitle(vm.settings.originalTitles) ?: "YORU",
                episode = episode,
                episodes = episodes,
                quality = quality,
                position = position,
                duration = duration,
                playing = playing,
                speed = vm.settings.playbackSpeed,
                fill = fill,
                fullscreen = fullscreen,
                onBack = { nav.popBackStack() },
                onQuality = { quality = it },
                onPrev = { if (episodeIndex > 0) episodeIndex-- },
                onNext = { if (episodeIndex + 1 < episodes.size) episodeIndex++ },
                onPlay = { if (player.isPlaying) player.pause() else player.play() },
                onRewind = { player.seekTo(max(0L, player.currentPosition - 10000L)); seekFlash = "−10" },
                onForward = { val d = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE; player.seekTo(min(d, player.currentPosition + 10000L)); seekFlash = "+10" },
                onSeek = { player.seekTo(it) },
                onSpeed = { val speeds = speedChoices(); val index = speeds.indexOfFirst { s -> abs(s - vm.settings.playbackSpeed) < 0.01f }.takeIf { it >= 0 } ?: 1; val next = speeds[(index + 1) % speeds.size]; vm.updateSettings(vm.settings.copy(playbackSpeed = next)) },
                onFullscreen = { fullscreen = !fullscreen; activity.yoruFullscreen(fullscreen) },
                onSmart = { if (!fullscreen) { fullscreen = true; activity.yoruFullscreen(true) }; fill = !fill },
                onPip = { (activity as? MainActivity)?.enterYoruPip() }
            )
        }
    }
}

@Composable
private fun PlayerOverlay(
    title: String,
    episode: EpisodeItem?,
    episodes: List<EpisodeItem>,
    quality: Int,
    position: Long,
    duration: Long,
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
    onSeek: (Long) -> Unit,
    onSpeed: () -> Unit,
    onFullscreen: () -> Unit,
    onSmart: () -> Unit,
    onPip: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(ComposeColor(0xdd000000), ComposeColor.Transparent))).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassIcon(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                    if (episode != null) Text(episode.label(), color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                QualityMenu(episode?.streams?.keys?.sorted().orEmpty(), quality, onQuality)
                Spacer(Modifier.width(8.dp))
                GlassText("PiP", onClick = onPip)
                Spacer(Modifier.width(8.dp))
                GlassIcon(onClick = onFullscreen) { Icon(if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, null) }
                Spacer(Modifier.width(8.dp))
                GlassIcon(accented = true, onClick = onSmart) { Icon(Icons.Rounded.ZoomOutMap, null) }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(ComposeColor.Transparent, ComposeColor(0xee000000)))).padding(horizontal = 16.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (duration > 0) Slider(value = position.coerceIn(0, duration).toFloat(), onValueChange = { onSeek(it.toLong()) }, valueRange = 0f..duration.toFloat())
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(timeLabel(position), color = muted, modifier = Modifier.weight(1f))
                Text(if (duration > 0) timeLabel(duration) else "", color = muted)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
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
                GlassText(speedLabel(speed), onClick = onSpeed)
            }
        }
        AnimatedVisibility(visible = fill, modifier = Modifier.align(Alignment.Center)) { Surface(shape = RoundedCornerShape(24.dp), color = accent.copy(alpha = 0.92f), shadowElevation = 12.dp) { Text("SMART FILL", color = ComposeColor(0xff21152f), fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) } }
    }
}

@Composable
private fun GlassIcon(enabled: Boolean = true, accented: Boolean = false, big: Boolean = false, onClick: () -> Unit, content: @Composable () -> Unit) {
    val size = if (big) 58.dp else 46.dp
    FilledIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(size)) { Box(Modifier.fillMaxSize().background(if (accented) accent else ComposeColor(0xcc21182d), CircleShape), contentAlignment = Alignment.Center) { content() } }
}

@Composable
private fun GlassText(label: String, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = ComposeColor(0xcc21182d), shadowElevation = 8.dp, modifier = Modifier.height(46.dp).clickable(onClick = onClick)) { Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 13.dp)) { Text(label, fontWeight = FontWeight.Black) } }
}

@Composable
private fun QualityMenu(qualities: List<Int>, quality: Int, onQuality: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(shape = RoundedCornerShape(20.dp), color = ComposeColor(0xcc21182d), modifier = Modifier.height(42.dp).clickable { open = true }) { Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) { Text(qualityName(quality), fontWeight = FontWeight.Bold) } }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) { qualities.forEach { q -> DropdownMenuItem(text = { Text(qualityName(q)) }, onClick = { open = false; onQuality(q) }) } }
    }
}

@Composable
private fun ImageScreen(nav: NavHostController, rawUrl: String) {
    val url = Uri.decode(rawUrl)
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset += offsetChange
    }
    Box(Modifier.fillMaxSize().background(ComposeColor.Black).transformable(state), contentAlignment = Alignment.Center) {
        AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxWidth().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y), contentScale = ContentScale.Fit)
        IconButton(onClick = { nav.popBackStack() }, modifier = Modifier.align(Alignment.TopStart).padding(12.dp).background(ComposeColor(0xaa000000), CircleShape)) { Icon(Icons.Rounded.Close, null, tint = text) }
    }
}

@Composable
private fun SectionLabel(value: String) { Text(value, color = accent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium) }

@Composable
private fun SectionTitle(value: String) { Text(value, color = text, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge) }

@Composable
private fun SmallNote(value: String) { Text(value, color = muted, modifier = Modifier.padding(vertical = 4.dp)) }

@Composable
private fun EmptyCard(value: String) { Card(colors = CardDefaults.cardColors(containerColor = card), shape = RoundedCornerShape(18.dp)) { Text(value, color = muted, modifier = Modifier.padding(16.dp)) } }

@Composable
private fun CenterSpinner() { Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = accent) } }

private fun chooseQuality(streams: Map<Int, String>, preferred: Int): Int {
    if (streams.isEmpty()) return preferred
    if (streams.containsKey(preferred)) return preferred
    return streams.keys.sortedBy { abs(it - preferred) }.first()
}

private fun timeLabel(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

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
