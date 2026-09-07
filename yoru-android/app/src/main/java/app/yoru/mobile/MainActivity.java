package app.yoru.mobile;

import android.app.*;
import android.content.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

@androidx.annotation.OptIn(
  markerClass = androidx.media3.common.util.UnstableApi.class
)
public final class MainActivity
  extends androidx.activity.ComponentActivity
  implements Ui.BackHandler
{

  private LinearLayout root, header, bottom;
  private FrameLayout content;
  private int tab = 0,
    renderedTab = 0,
    generation = 0,
    pageNumber = 0;
  private boolean historyView = false,
    sourceView = false,
    profileView = false,
    renderedProfileView = false;
  private boolean loading,
    more,
    firstResume = true;
  private final int[] scrollState = new int[6];
  private String selectedSource = "yoru",
    query = "",
    bucket = "";
  private ApiRepository.Filter filter = new ApiRepository.Filter();
  private final ArrayList<Anime> catalog = new ArrayList<>();
  private GridView grid;
  private CatalogAdapter adapter;
  private TextView summary;
  private EditText search;
  private LinearLayout searchPanel;
  private CalendarScreen calendarScreen;
  private final View[] screenCache = new View[5];
  private final String[] screenKeys = new String[5];
  private final ArrayList<Future<?>> uiTasks = new ArrayList<>();
  private Future<?> catalogFuture;
  private final ScreenWork screenWork = new ScreenWork();
  private final Handler handler = new Handler(Looper.getMainLooper());
  private Runnable searchTask;

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    if (b != null) {
      tab = b.getInt("tab", 0);
      profileView = b.getBoolean("profile", false);
      selectedSource = b.getString("source", "yoru");
      if (selectedSource.equals("all")) selectedSource = "yoru";
      query = b.getString("query", "");
      bucket = b.getString("bucket", "");
      int[] saved = b.getIntArray("scroll");
      if (saved != null) System.arraycopy(
        saved,
        0,
        scrollState,
        0,
        Math.min(saved.length, scrollState.length)
      );
    }
    if (!Ui.allow(this)) return;
    root = Ui.base(this);
    header = Ui.row(this);
    header.setPadding(
      Ui.dp(this, 18),
      Ui.dp(this, 13),
      Ui.dp(this, 18),
      Ui.dp(this, 13)
    );
    ImageView logo = new ImageView(this);
    logo.setImageResource(R.drawable.ic_yoru);
    header.addView(logo, Ui.lp(this, 36, 36));
    TextView brand = Ui.text(this, "YORU.", 25, Ui.TEXT, true);
    LinearLayout.LayoutParams bp = Ui.lp(this, 0, -2);
    bp.weight = 1;
    bp.leftMargin = Ui.dp(this, 9);
    header.addView(brand, bp);
    Ui.gap(
      header,
      Ui.iconButton(this, "search", "Найти аниме", () -> {
        profileView = false;
        tab = 1;
        render();
        search.requestFocus();
        (
          (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)
        ).showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
      }),
      38,
      38,
      0
    );
    Ui.gap(
      header,
      Ui.iconButton(this, "settings", "Настройки", () -> {
        profileView = true;
        render();
      }),
      38,
      38,
      8
    );
    root.addView(header, Ui.lp(this, -1, -2));
    content = new FrameLayout(this);
    root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
    bottom = Ui.row(this);
    bottom.setPadding(
      Ui.dp(this, 9),
      Ui.dp(this, 5),
      Ui.dp(this, 9),
      Ui.dp(this, 5)
    );
    bottom.setBackgroundColor(Ui.SURFACE);
    root.addView(bottom, Ui.lp(this, -1, 64));
    if (getIntent().getBooleanExtra("openDownloads", false)) {
      tab = 3;
      profileView = false;
    }
    render();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (firstResume) {
      firstResume = false;
      return;
    }
    YoruApp.app().discovery.execute(() -> {
      try {
        if (
          YoruApp.app().store.takeUpdatePulse()
        ) EpisodeUpdateReceiver.schedule(this);
      } catch (Exception ignored) {}
    });
    if (
      content != null &&
      !profileView &&
      !historyView &&
      !sourceView &&
      (tab == 0 || tab == 2)
    ) render();
  }

  private void render() {
    rememberScroll();
    cancelUiTasks();
    sourceView = false;
    historyView = false;
    generation++;
    loading = false;
    navigation();
    if (profileView) {
      content.removeAllViews();
      content.addView(
        new ProfileScreen(this, this::profileAction),
        new FrameLayout.LayoutParams(-1, -1)
      );
      restoreScroll();
      renderedTab = tab;
      renderedProfileView = profileView;
      return;
    }
    String key = screenKey(tab);
    if (screenCache[tab] != null && key.equals(screenKeys[tab])) {
      content.removeAllViews();
      attach(screenCache[tab]);
      if (tab == 4 && calendarScreen != null) calendarScreen.quickShow();
      restoreScroll();
      renderedTab = tab;
      renderedProfileView = profileView;
      return;
    }
    content.removeAllViews();
    if (tab == 0) home();
    else if (tab == 1) catalogue();
    else if (tab == 2) library(false);
    else if (tab == 3) screen("Загрузки", () -> new DownloadsScreen(this));
    else if (tab == 4) screen("Календарь", this::calendarView);
    capture(tab, key);
    restoreScroll();
    renderedTab = tab;
    renderedProfileView = profileView;
  }

  private void navigation() {
    bottom.removeAllViews();
    String[] names = {
        "Главная",
        "Каталог",
        "Коллекция",
        "Загрузки",
        "Календарь",
      },
      icons = { "home", "grid", "heart", "download", "calendar" };
    for (int i = 0; i < names.length; i++) {
      final int target = i;
      LinearLayout cell = Ui.column(this);
      cell.setGravity(Gravity.CENTER);
      Ui.Icon icon = new Ui.Icon(this, icons[i]);
      boolean active = tab == i && !profileView;
      icon.color = active ? Ui.PURPLE : Ui.MUTED;
      cell.addView(icon, Ui.lp(this, 31, 31));
      String text = names[i];
      TextView label = Ui.text(
        this,
        text,
        10,
        active ? Ui.PURPLE : Ui.MUTED,
        active
      );
      label.setGravity(Gravity.CENTER);
      Ui.space(cell, 3);
      cell.addView(label, Ui.lp(this, -1, -2));
      if (active) cell.setBackground(Ui.shape(0xff2a2036, 12, this));
      cell.setContentDescription(text);
      cell.setOnClickListener(v -> {
        if (tab != target || profileView) {
          tab = target;
          profileView = false;
          render();
        }
      });
      bottom.addView(cell, new LinearLayout.LayoutParams(0, -1, 1));
    }
  }

  private String screenKey(int t) {
    if (t == 0) return "home:" + YoruApp.app().store.viewVersion();
    if (t == 1) return (
      "catalog:" +
      selectedSource +
      ":" +
      query +
      ":" +
      filter.year +
      ":" +
      filter.genre +
      ":" +
      filter.type +
      ":" +
      filter.status +
      ":" +
      filter.sort +
      ":" +
      filter.season
    );
    if (t == 2) return (
      "library:" + bucket + ":" + YoruApp.app().store.libraryVersion()
    );
    return "tab:" + t;
  }

  private void attach(View v) {
    try {
      ViewParent p = v.getParent();
      if (p instanceof ViewGroup) ((ViewGroup) p).removeView(v);
    } catch (Exception ignored) {}
    content.addView(v, new FrameLayout.LayoutParams(-1, -1));
  }

  private void capture(int t, String key) {
    if (
      t < 0 || t >= screenCache.length || content.getChildCount() == 0
    ) return;
    if (t == 3) return;
    screenCache[t] = content.getChildAt(0);
    screenKeys[t] = key;
  }

  private void invalidateScreen(int t) {
    if (t >= 0 && t < screenCache.length) {
      screenCache[t] = null;
      screenKeys[t] = null;
    }
  }

  private Future<?> uiTask(Runnable r) {
    ScreenWork.Ticket ticket = screenWork.begin(tab);
    Future<?> future = YoruApp.app().ui.submit(() -> {
      try {
        r.run();
      } finally {
        YoruApp.app().main.post(() -> screenWork.finish(ticket));
      }
    });
    uiTasks.removeIf(Future::isDone);
    uiTasks.add(future);
    return future;
  }

  private void cancelUiTasks() {
    for (int screen : screenWork.cancelAll()) invalidateScreen(screen);
    boolean unfinished = false;
    for (Future<?> future : new ArrayList<>(uiTasks)) {
      if (future != null && !future.isDone()) {
        unfinished = true;
        future.cancel(true);
      }
    }
    uiTasks.clear();
    if (catalogFuture != null && !catalogFuture.isDone()) {
      unfinished = true;
      catalogFuture.cancel(true);
    }
    if (unfinished && !renderedProfileView) invalidateScreen(renderedTab);
    if (searchTask != null) {
      handler.removeCallbacks(searchTask);
      searchTask = null;
    }
  }

  private int slot(boolean profile, int value) {
    return profile ? 5 : Math.max(0, Math.min(4, value));
  }

  private void rememberScroll() {
    if (content == null || content.getChildCount() == 0) return;
    View view = content.getChildAt(0);
    int at = slot(renderedProfileView, renderedTab);
    ScrollView scroll = findScroll(view);
    if (scroll != null) scrollState[at] = scroll.getScrollY();
    else {
      AbsListView list = findList(view);
      if (list != null) scrollState[at] = list.getFirstVisiblePosition();
    }
  }

  private AbsListView findList(View view) {
    if (view instanceof AbsListView) return (AbsListView) view;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        AbsListView found = findList(group.getChildAt(i));
        if (found != null) return found;
      }
    }
    return null;
  }

  private void restoreScroll() {
    if (content == null || content.getChildCount() == 0) return;
    int position = scrollState[slot(profileView, tab)];
    View view = content.getChildAt(0);
    ScrollView scroll = findScroll(view);
    if (scroll != null) scroll.post(() -> scroll.scrollTo(0, position));
    else {
      AbsListView list = findList(view);
      if (list != null) list.post(() ->
        list.setSelection(Math.max(0, position))
      );
    }
  }

  private ScrollView findScroll(View v) {
    if (v instanceof ScrollView) return (ScrollView) v;
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) {
        ScrollView found = findScroll(g.getChildAt(i));
        if (found != null) return found;
      }
    }
    return null;
  }

  private interface ScreenFactory {
    View create();
  }

  private void screen(String title, ScreenFactory factory) {
    try {
      content.addView(factory.create(), new FrameLayout.LayoutParams(-1, -1));
    } catch (Throwable e) {
      LinearLayout col = Ui.column(this);
      col.setPadding(
        Ui.dp(this, 18),
        Ui.dp(this, 28),
        Ui.dp(this, 18),
        Ui.dp(this, 18)
      );
      col.addView(Ui.text(this, title, 28, Ui.TEXT, true));
      Ui.space(col, 12);
      col.addView(
        Ui.text(
          this,
          "Раздел восстановлен после ошибки. Нажмите ещё раз или обновите данные.",
          13,
          Ui.MUTED,
          false
        )
      );
      Ui.space(col, 14);
      col.addView(Ui.button(this, "Повторить", true, this::render));
      content.addView(col, new FrameLayout.LayoutParams(-1, -1));
    }
  }

  private View calendarView() {
    if (calendarScreen == null) calendarScreen = new CalendarScreen(this);
    else calendarScreen.quickShow();
    return calendarScreen;
  }

  private LinearLayout scrolling() {
    ScrollView sc = new ScrollView(this);
    sc.setFillViewport(true);
    sc.setVerticalScrollBarEnabled(false);
    LinearLayout col = Ui.column(this);
    col.setPadding(
      Ui.dp(this, 17),
      Ui.dp(this, 13),
      Ui.dp(this, 17),
      Ui.dp(this, 25)
    );
    sc.addView(col, new ScrollView.LayoutParams(-1, -2));
    content.addView(sc, new FrameLayout.LayoutParams(-1, -1));
    return col;
  }

  private void home() {
    LinearLayout col = scrolling();
    col.addView(Ui.label(this, "ВАШЕ СЛЕДУЮЩЕЕ ЛЮБИМОЕ АНИМЕ"));
    Ui.space(col, 14);
    Anime feature = new Anime();
    feature.source = "yoru";
    feature.id = "52991";
    feature.malId = 52991;
    feature.title = "Провожающая в последний путь Фрирен";
    feature.original = "Sousou no Frieren";
    feature.poster =
      "https://anilibria.top/storage/releases/posters/9542/8UGD4dHHp1kdjquBph2CUSk9pLNsGtYw.webp";
    feature.year = 2023;
    feature.type = "ТВ";
    feature.status = "released";
    feature.episodes = 28;
    final Anime heroAnime = feature;
    FrameLayout hero = new FrameLayout(this);
    hero.setBackground(Ui.shape(Ui.CARD, 20, this));
    hero.setClipToOutline(true);
    ImageView bg = new ImageView(this);
    bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
    hero.addView(bg, new FrameLayout.LayoutParams(-1, -1));
    YoruApp.app().images.load(bg, heroAnime);
    View shade = new View(this);
    shade.setBackground(
      new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {
        0x22120c1b,
        0xcc20172d,
        0xff21162e,
      })
    );
    hero.addView(shade, new FrameLayout.LayoutParams(-1, -1));
    LinearLayout text = Ui.column(this);
    text.setPadding(
      Ui.dp(this, 20),
      Ui.dp(this, 24),
      Ui.dp(this, 20),
      Ui.dp(this, 21)
    );
    text.addView(Ui.label(this, "ВЫБОР YORU"));
    Ui.space(text, 13);
    TextView title = Ui.text(this, heroAnime.title, 25, Ui.TEXT, true);
    title.setMaxLines(3);
    text.addView(title);
    Ui.space(text, 12);
    text.addView(
      Ui.text(this, "2023 · Фэнтези · 28 серий", 11, 0xffcdbbdc, false)
    );
    Ui.space(text, 12);
    text.addView(
      Ui.text(
        this,
        "Большое путешествие закончилось. Самое важное — только начинается.",
        12,
        0xffd7c8e1,
        false
      )
    );
    Ui.space(text, 18);
    LinearLayout actions = Ui.row(this);
    actions.addView(
      Ui.button(this, "Начать смотреть", true, () -> {
        YoruApp.app().api.remember(heroAnime);
        Ui.openPlayer(this, heroAnime, "auto", 1);
      }),
      new LinearLayout.LayoutParams(0, -2, 1)
    );
    LinearLayout.LayoutParams cp = Ui.lp(this, 0, -2);
    cp.weight = 1;
    cp.leftMargin = Ui.dp(this, 9);
    actions.addView(
      Ui.button(this, "Календарь", false, () -> {
        tab = 4;
        render();
      }),
      cp
    );
    text.addView(actions);
    hero.addView(text, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
    col.addView(hero, Ui.lp(this, -1, 330));
    Ui.space(col, 18);
    LinearLayout calendar = Ui.row(this);
    calendar.setPadding(
      Ui.dp(this, 15),
      Ui.dp(this, 16),
      Ui.dp(this, 15),
      Ui.dp(this, 16)
    );
    calendar.setBackground(Ui.stroke(Ui.CARD, 14, this));
    LinearLayout cw = Ui.column(this);
    cw.addView(Ui.text(this, "Календарь выхода", 13, Ui.TEXT, true));
    Ui.space(cw, 6);
    cw.addView(
      Ui.text(
        this,
        "Все ближайшие аниме и серии одним списком",
        11,
        Ui.MUTED,
        false
      )
    );
    calendar.addView(cw, new LinearLayout.LayoutParams(0, -2, 1));
    Ui.Icon ci = new Ui.Icon(this, "calendar");
    ci.color = Ui.PURPLE;
    calendar.addView(ci, Ui.lp(this, 24, 24));
    calendar.setOnClickListener(v -> {
      tab = 4;
      render();
    });
    col.addView(calendar, Ui.lp(this, -1, -2));
    moodBar(col);
    if (YoruApp.app().store.ready()) {
      dashboardPanel(col);
      newEpisodesPanel(col);
      focusQueue(col);
      watchPlan(col);
      List<Anime> history = YoruApp.app().store.recent();
      if (!history.isEmpty()) {
        Ui.space(col, 25);
        section(col, "Продолжим?", "Вся история", () -> libraryHistory());
        HorizontalScrollView row = new HorizontalScrollView(this);
        row.setHorizontalScrollBarEnabled(false);
        LinearLayout list = Ui.row(this);
        history = YoruBrain.visible(history);
        for (Anime rowAnime : history.subList(0, Math.min(6, history.size()))) {
          LinearLayout item = Ui.column(this);
          item.setPadding(0, 0, Ui.dp(this, 10), 0);
          Ui.Card card = new Ui.Card(this, 160);
          card.bind(rowAnime);
          JSONObject h = YoruApp.app().store.progress(rowAnime);
          card.setOnClickListener(v ->
            Ui.openPlayer(
              this,
              rowAnime,
              h.optString("playerMode", "auto"),
              h.optDouble("episode", 1)
            )
          );
          item.addView(card, Ui.lp(this, 132, -2));
          TextView progress = Ui.text(
            this,
            "Серия " +
              (int) h.optDouble("episode", 1) +
              " · " +
              Ui.time(h.optInt("time")),
            10,
            Ui.MUTED,
            false
          );
          item.addView(progress);
          list.addView(item);
        }
        row.addView(list);
        col.addView(row, Ui.lp(this, -1, -2));
      }
    }
    Ui.space(col, 25);
    section(col, "Стоит посмотреть", "Весь каталог", () -> {
      tab = 1;
      render();
    });
    LinearLayout host = Ui.column(this);
    col.addView(host);
    final int gen = generation;
    uiTask(() -> {
      try {
        List<Anime> seed = YoruApp.app().api.seed();
        ArrayList<Anime> first = new ArrayList<>();
        for (Anime a : seed)
          if (Anime.valid(a)) {
            first.add(a);
            if (first.size() >= 12) break;
          }
        YoruApp.app().main.post(() -> {
          if (gen == generation && tab == 0 && !isFinishing()) renderCards(
            host,
            first
          );
        });
        Anime.Page p = YoruApp.app().api.catalog(
          "yoru",
          "",
          1,
          new ApiRepository.Filter()
        );
        YoruApp.app().main.post(() -> {
          if (gen == generation && tab == 0 && !isFinishing()) renderCards(
            host,
            p.items.subList(0, Math.min(12, p.items.size()))
          );
        });
      } catch (Exception ignored) {}
    });
  }

  private void dashboardPanel(LinearLayout col) {
    Ui.space(col, 18);
    LinearLayout card = Ui.column(this);
    card.setPadding(
      Ui.dp(this, 16),
      Ui.dp(this, 16),
      Ui.dp(this, 16),
      Ui.dp(this, 16)
    );
    card.setBackground(Ui.stroke(Ui.CARD, 16, this));
    card.addView(Ui.text(this, "Сейчас в YORU", 16, Ui.TEXT, true));
    Ui.space(card, 8);
    card.addView(
      Ui.text(
        this,
        YoruBrain.dashboard() +
          "\nОзвучки: " +
          YoruApp.app().store.favoriteVoiceSummary() +
          " · " +
          QualityPlus.name(YoruApp.app().store.quality()),
        11,
        Ui.MUTED,
        false
      )
    );
    List<Anime> recent = YoruApp.app().store.recent();
    if (!recent.isEmpty()) {
      Ui.space(card, 12);
      TextView cont = Ui.button(this, "Продолжить просмотр", true, () -> {
        List<Anime> rows = YoruApp.app().store.recent();
        if (rows.isEmpty()) {
          tab = 1;
          render();
          return;
        }
        Anime a = rows.get(0);
        JSONObject h = YoruApp.app().store.progress(a);
        Ui.openPlayer(
          this,
          a,
          h.optString("playerMode", "yoru"),
          h.optDouble("episode", 1)
        );
      });
      card.addView(cont, Ui.lp(this, -1, -2));
    }
    col.addView(card, Ui.lp(this, -1, -2));
  }

  private void newEpisodesPanel(LinearLayout col) {
    ArrayList<Anime> rows = YoruBrain.newEpisodes(8);
    if (rows.isEmpty()) return;
    Ui.space(col, 24);
    section(col, "Новые серии для вас", "Коллекция", () -> {
      tab = 2;
      render();
    });
    HorizontalScrollView row = new HorizontalScrollView(this);
    row.setHorizontalScrollBarEnabled(false);
    LinearLayout list = Ui.row(this);
    for (Anime a : rows) {
      LinearLayout item = Ui.column(this);
      item.setPadding(0, 0, Ui.dp(this, 10), 0);
      Ui.Card card = new Ui.Card(this, 164);
      card.bind(a);
      JSONObject h = YoruApp.app().store.progress(a);
      double next = Math.max(1, Math.floor(h.optDouble("episode", 0)) + 1);
      card.setOnClickListener(v ->
        Ui.openPlayer(this, a, h.optString("playerMode", "yoru"), next)
      );
      item.addView(card, Ui.lp(this, 136, -2));
      String line = a.episodesLine();
      if (line.isEmpty()) line = "Продолжить";
      item.addView(Ui.text(this, line, 10, Ui.PURPLE, false));
      list.addView(item);
    }
    row.addView(list);
    col.addView(row, Ui.lp(this, -1, -2));
  }

  private void focusQueue(LinearLayout col) {
    ArrayList<Anime> rows = YoruBrain.priorities(6);
    if (rows.isEmpty()) return;
    Ui.space(col, 24);
    section(col, "Главное сейчас", "Смотреть", () -> {
      tab = 1;
      render();
    });
    LinearLayout host = Ui.column(this);
    col.addView(host);
    renderCards(host, rows.subList(0, Math.min(6, rows.size())));
  }

  private void quickShelves(LinearLayout col) {
    smartShelf(col, "Для вас", "forYou", "Каталог", () -> {
      tab = 1;
      render();
    });
    smartShelf(col, "Короткий марафон", "short", "Фильтры", () -> {
      filter = new ApiRepository.Filter();
      filter.type = "TV";
      tab = 1;
      render();
    });
    smartShelf(col, "Фильмы на вечер", "movies", "Каталог", () -> {
      filter = new ApiRepository.Filter();
      filter.type = "MOVIE";
      tab = 1;
      render();
    });
    smartShelf(col, "Скрытые жемчужины", "gems", "Открыть", () -> {
      tab = 1;
      render();
    });
    smartShelf(col, "Сейчас выходит", "ongoing", "Календарь", () -> {
      tab = 4;
      render();
    });
    smartShelf(col, "Классика", "classics", "Открыть", () -> {
      tab = 1;
      render();
    });
  }

  private void smartShelf(
    LinearLayout col,
    String title,
    String kind,
    String action,
    Runnable click
  ) {
    ArrayList<Anime> rows = YoruBrain.shelf(kind, 8);
    if (rows.isEmpty()) return;
    Ui.space(col, 24);
    section(col, title, action, click);
    LinearLayout host = Ui.column(this);
    col.addView(host);
    renderCards(host, rows);
  }

  private void surprisePick() {
    Anime a = YoruBrain.surprise();
    if (a == null) {
      Ui.toast(this, "Сначала откройте каталог");
      return;
    }
    Ui.openDetails(this, a);
  }

  private void watchPlan(LinearLayout col) {
    List<Anime> plan = YoruApp.app().store.watchQueue();
    if (plan.isEmpty()) return;
    Ui.space(col, 25);
    section(col, "План вечера", "Очистить", () -> {
      YoruApp.app().store.clearWatchQueue();
      render();
    });
    HorizontalScrollView row = new HorizontalScrollView(this);
    row.setHorizontalScrollBarEnabled(false);
    LinearLayout list = Ui.row(this);
    for (Anime a : plan.subList(0, Math.min(10, plan.size()))) {
      LinearLayout item = Ui.column(this);
      item.setPadding(0, 0, Ui.dp(this, 10), 0);
      Ui.Card card = new Ui.Card(this, 160);
      card.bind(a);
      JSONObject h = YoruApp.app().store.progress(a);
      card.setOnClickListener(v ->
        Ui.openPlayer(
          this,
          a,
          h.optString("playerMode", "yoru"),
          h.optDouble("episode", 1)
        )
      );
      item.addView(card, Ui.lp(this, 132, -2));
      item.addView(
        Ui.text(
          this,
          "Продолжить · серия " + Ui.number(h.optDouble("episode", 1)),
          10,
          Ui.PURPLE,
          false
        )
      );
      list.addView(item);
    }
    row.addView(list);
    col.addView(row, Ui.lp(this, -1, -2));
  }

  private void moodBar(LinearLayout col) {
    Ui.space(col, 18);
    HorizontalScrollView row = new HorizontalScrollView(this);
    row.setHorizontalScrollBarEnabled(false);
    LinearLayout chips = Ui.row(this);
    for (String mood : new String[] {
      "Уютное",
      "Экшен",
      "Романтика",
      "Короткое",
    }) {
      TextView chip = Ui.chip(this, mood, false, () -> showMood(mood));
      LinearLayout.LayoutParams p = Ui.lp(this, -2, -2);
      p.rightMargin = Ui.dp(this, 7);
      chips.addView(chip, p);
    }
    row.addView(chips);
    col.addView(row, Ui.lp(this, -1, -2));
  }

  private void showMood(String mood) {
    ArrayList<Anime> rows = YoruApp.app().store.mood(mood);
    if (rows.isEmpty()) {
      query = mood;
      tab = 1;
      render();
      return;
    }
    ScrollView sc = new ScrollView(this);
    LinearLayout col = Ui.column(this);
    sc.addView(col);
    sc.setLayoutParams(new LinearLayout.LayoutParams(-1, Ui.dp(this, 430)));
    for (Anime a : rows.subList(0, Math.min(20, rows.size()))) {
      LinearLayout line = Ui.row(this);
      line.setPadding(
        Ui.dp(this, 12),
        Ui.dp(this, 10),
        Ui.dp(this, 12),
        Ui.dp(this, 10)
      );
      line.setBackground(Ui.stroke(Ui.CARD, 13, this));
      line.addView(
        Ui.text(this, YoruBrain.title(a), 13, Ui.TEXT, true),
        new LinearLayout.LayoutParams(0, -2, 1)
      );
      Ui.Icon icon = new Ui.Icon(this, "play");
      icon.color = Ui.PURPLE;
      line.addView(icon, Ui.lp(this, 22, 22));
      Ui.press(line);
      line.setOnClickListener(v -> Ui.openDetails(this, a));
      col.addView(line, Ui.lp(this, -1, -2));
      Ui.space(col, 8);
    }
    Ui.custom(this, mood, sc, "Готово", null, null, null, null);
  }

  private void recommendations(LinearLayout col) {
    JSONArray genres = YoruApp.app().store.stats().optJSONArray("genres");
    if (genres == null || genres.length() == 0) return;
    String g = genres.optString(0, "");
    if (g.isEmpty()) return;
    ArrayList<Anime> rows = new ArrayList<>();
    for (Anime a : YoruApp.app().api.seed())
      for (String genre : a.genres)
        if (genre.equalsIgnoreCase(g) && YoruBrain.visible(a)) {
          rows.add(a);
          break;
        }
    if (rows.isEmpty()) return;
    Ui.space(col, 25);
    section(col, "Вам может понравиться", "Каталог", () -> {
      query = g;
      tab = 1;
      render();
    });
    LinearLayout host = Ui.column(this);
    col.addView(host);
    renderCards(host, rows.subList(0, Math.min(8, rows.size())));
  }

  private void seasonal(LinearLayout col) {
    Ui.space(col, 25);
    section(col, "Сезон сейчас", "Календарь", () -> {
      tab = 4;
      render();
    });
    LinearLayout host = Ui.column(this);
    col.addView(host);
    renderCards(
      host,
      YoruApp.app()
        .api.seed()
        .subList(0, Math.min(8, YoruApp.app().api.seed().size()))
    );
    ApiRepository.Filter f = new ApiRepository.Filter();
    f.year = String.valueOf(Calendar.getInstance().get(Calendar.YEAR));
    f.season = currentSeason();
    f.status = "ongoing";
    final int gen = generation;
    uiTask(() -> {
      try {
        Anime.Page p = YoruApp.app().api.catalog("shikimori", "", 1, f);
        YoruApp.app().main.post(() -> {
          if (
            gen == generation &&
            tab == 0 &&
            !isFinishing() &&
            !p.items.isEmpty()
          ) renderCards(host, p.items.subList(0, Math.min(8, p.items.size())));
        });
      } catch (Exception ignored) {}
    });
  }

  private String currentSeason() {
    int m = Calendar.getInstance().get(Calendar.MONTH) + 1;
    if (m <= 2 || m == 12) return "winter";
    if (m <= 5) return "spring";
    if (m <= 8) return "summer";
    return "fall";
  }

  private void section(
    LinearLayout host,
    String title,
    String action,
    Runnable runnable
  ) {
    LinearLayout row = Ui.row(this);
    row.addView(
      Ui.text(this, title, 19, Ui.TEXT, true),
      new LinearLayout.LayoutParams(0, -2, 1)
    );
    TextView more = Ui.text(this, action, 11, Ui.PURPLE, true);
    more.setPadding(12, 10, 0, 10);
    more.setOnClickListener(v -> runnable.run());
    row.addView(more);
    host.addView(row);
    Ui.space(host, 10);
  }

  private void renderCards(LinearLayout host, List<Anime> list) {
    if (YoruApp.app().store.ready()) list = YoruBrain.visible(list);
    else {
      LinkedHashMap<String, Anime> map = new LinkedHashMap<>();
      if (list != null) for (Anime a : list)
        if (Anime.valid(a)) map.put(a.key(), a);
      list = new ArrayList<>(map.values());
    }
    host.removeAllViews();
    int cols = getResources().getConfiguration().screenWidthDp >= 650 ? 3 : 2;
    for (int i = 0; i < list.size(); i += cols) {
      LinearLayout row = Ui.row(this);
      row.setGravity(Gravity.TOP);
      for (int k = 0; k < cols; k++) {
        if (i + k < list.size()) {
          Ui.Card c = new Ui.Card(
            this,
            ((getResources().getConfiguration().screenWidthDp / cols - 25) *
              3) /
              2
          );
          c.bind(list.get(i + k));
          row.addView(c, new LinearLayout.LayoutParams(0, -2, 1));
        } else row.addView(
          new View(this),
          new LinearLayout.LayoutParams(0, 1, 1)
        );
      }
      host.addView(row);
    }
    ArrayList<Anime> warm = new ArrayList<>(
      list.subList(0, Math.min(10, list.size()))
    );
    if (!warm.isEmpty()) YoruApp.app().discovery.execute(() -> {
      try {
        YoruApp.app().api.prefetchQuickDetails(warm, warm.size());
      } catch (Exception ignored) {}
    });
  }

  private void catalogue() {
    LinearLayout col = Ui.column(this);
    col.setPadding(Ui.dp(this, 15), Ui.dp(this, 8), Ui.dp(this, 15), 0);
    content.addView(col, new FrameLayout.LayoutParams(-1, -1));
    LinearLayout titleRow = Ui.row(this);
    titleRow.addView(
      Ui.text(this, "Каталог", 27, Ui.TEXT, true),
      new LinearLayout.LayoutParams(0, -2, 1)
    );
    TextView history = Ui.text(this, "История", 11, Ui.PURPLE, true);
    history.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), 0, Ui.dp(this, 8));
    history.setOnClickListener(v -> {
      query = "";
      if (search != null) search.setText("");
      renderSearchPanel();
    });
    titleRow.addView(history);
    col.addView(titleRow);
    Ui.space(col, 13);
    LinearLayout searchRow = Ui.row(this);
    search = new EditText(this);
    search.setTextColor(Ui.TEXT);
    search.setHintTextColor(Ui.MUTED);
    search.setSingleLine();
    search.setFilters(new android.text.InputFilter[] {
      new android.text.InputFilter.LengthFilter(150),
    });
    search.setTextSize((14 * YoruApp.app().store.fontScale()) / 100f);
    search.setTypeface(Ui.typeface(this, false));
    search.setHint("Найти аниме или озвучку");
    search.setText(query);
    search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
    search.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
    search.setBackground(Ui.stroke(Ui.CARD, 13, this));
    searchRow.addView(
      search,
      new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1)
    );
    Ui.gap(
      searchRow,
      Ui.iconButton(this, "close", "Очистить поиск", () -> {
        query = "";
        if (searchTask != null) handler.removeCallbacks(searchTask);
        search.setText("");
        renderSearchPanel();
        loadCatalog(true);
        search.requestFocus();
      }),
      44,
      44,
      8
    );
    Ui.gap(
      searchRow,
      Ui.iconButton(this, "filter", "Фильтры", this::filters),
      44,
      44,
      8
    );
    col.addView(searchRow);
    searchPanel = Ui.column(this);
    col.addView(searchPanel);
    renderSearchPanel();
    Ui.space(col, 10);
    LinearLayout voiceLine = Ui.row(this);
    String chosenVoice = YoruApp.app().store.onlyPreferredVoice()
      ? ApiRepository.voiceTitle(YoruApp.app().store.voicePreference())
      : YoruApp.app().store.favoriteVoiceSummary();
    if (chosenVoice.isEmpty()) chosenVoice = "Авто";
    voiceLine.addView(
      Ui.text(
        this,
        "Озвучки: " +
          chosenVoice +
          (YoruApp.app().store.onlyPreferredVoice()
            ? " · только выбранная"
            : ""),
        11,
        Ui.MUTED,
        false
      ),
      new LinearLayout.LayoutParams(0, -2, 1)
    );
    TextView tune = Ui.chip(this, "Настроить", false, this::playbackSettings);
    voiceLine.addView(tune, Ui.lp(this, -2, -2));
    col.addView(voiceLine, Ui.lp(this, -1, -2));
    selectedSource = "yoru";
    summary = Ui.text(this, "Загружаем…", 11, Ui.MUTED, false);
    summary.setPadding(0, Ui.dp(this, 13), 0, Ui.dp(this, 10));
    col.addView(summary);
    grid = new GridView(this);
    grid.setNumColumns(
      getResources().getConfiguration().screenWidthDp >= 650 ? 3 : 2
    );
    grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
    grid.setVerticalSpacing(Ui.dp(this, 9));
    grid.setHorizontalSpacing(Ui.dp(this, 5));
    grid.setClipToPadding(false);
    grid.setPadding(0, 0, 0, Ui.dp(this, 15));
    adapter = new CatalogAdapter();
    grid.setAdapter(adapter);
    col.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
    grid.setOnScrollListener(
      new AbsListView.OnScrollListener() {
        public void onScrollStateChanged(AbsListView v, int state) {}

        public void onScroll(AbsListView v, int first, int count, int total) {
          if (
            total > 0 && more && !loading && first + count >= total - 6
          ) loadCatalog(false);
        }
      }
    );
    search.setOnEditorActionListener((v, id, event) -> {
      if (
        id == EditorInfo.IME_ACTION_SEARCH ||
        (event != null &&
          event.getKeyCode() == KeyEvent.KEYCODE_ENTER &&
          event.getAction() == KeyEvent.ACTION_UP)
      ) {
        submitSearch(true);
        return true;
      }
      return false;
    });
    search.addTextChangedListener(
      new TextWatcher() {
        public void beforeTextChanged(
          CharSequence s,
          int st,
          int count,
          int after
        ) {}

        public void onTextChanged(
          CharSequence s,
          int st,
          int before,
          int count
        ) {
          generation++;
          if (catalogFuture != null) catalogFuture.cancel(true);
          loading = false;
          query = s.toString().trim().replaceAll("\\s+", " ");

          if (searchTask != null) handler.removeCallbacks(searchTask);
          searchTask = () -> {
            if (tab != 1 || profileView || isFinishing()) return;
            renderSearchPanel();
            if (query.length() == 1) {
              catalog.clear();
              more = false;
              loading = false;
              adapter.notifyDataSetChanged();
              summary.setText(
                "Введите ещё один символ — так поиск будет точнее и быстрее."
              );
            } else loadCatalog(true);
          };
          handler.postDelayed(searchTask, query.isEmpty() ? 180 : 650);
        }

        public void afterTextChanged(Editable e) {}
      }
    );
    loadCatalog(true);
  }

  private void submitSearch(boolean remember) {
    if (searchTask != null) handler.removeCallbacks(searchTask);
    query =
      search == null
        ? query
        : search.getText().toString().trim().replaceAll("\\s+", " ");
    if (remember) YoruApp.app().store.addSearch(query);
    renderSearchPanel();
    if (query.length() != 1) loadCatalog(true);
    InputMethodManager imm = (InputMethodManager) getSystemService(
      INPUT_METHOD_SERVICE
    );
    if (imm != null && search != null) imm.hideSoftInputFromWindow(
      search.getWindowToken(),
      0
    );
  }

  private TextView searchChip(String text, Runnable action) {
    TextView t = Ui.text(this, text, 11, Ui.TEXT, true);
    t.setSingleLine();
    t.setEllipsize(TextUtils.TruncateAt.END);
    t.setPadding(
      Ui.dp(this, 12),
      Ui.dp(this, 9),
      Ui.dp(this, 12),
      Ui.dp(this, 9)
    );
    t.setBackground(Ui.stroke(Ui.SURFACE, 10, this));
    t.setOnClickListener(v -> action.run());
    return t;
  }

  private void renderSearchPanel() {
    if (searchPanel == null) return;
    searchPanel.removeAllViews();
    String q = query == null ? "" : query.trim();
    if (q.isEmpty()) {
      List<String> rows = YoruApp.app().store.searchHistory();
      if (rows.isEmpty()) return;
      Ui.space(searchPanel, 10);
      LinearLayout head = Ui.row(this);
      head.addView(
        Ui.text(this, "История поиска", 12, Ui.TEXT, true),
        new LinearLayout.LayoutParams(0, -2, 1)
      );
      TextView clear = Ui.text(this, "Очистить", 10, Ui.PURPLE, true);
      clear.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), 0, Ui.dp(this, 6));
      clear.setOnClickListener(v -> {
        YoruApp.app().store.clearSearchHistory();
        renderSearchPanel();
      });
      head.addView(clear);
      searchPanel.addView(head);
      Ui.space(searchPanel, 8);
      HorizontalScrollView wrap = new HorizontalScrollView(this);
      wrap.setHorizontalScrollBarEnabled(false);
      LinearLayout list = Ui.row(this);
      for (String item : rows) {
        LinearLayout.LayoutParams lp = Ui.lp(this, -2, -2);
        lp.rightMargin = Ui.dp(this, 7);
        list.addView(
          searchChip(item, () -> {
            query = item;
            search.setText(item);
            search.setSelection(search.length());
            submitSearch(false);
          }),
          lp
        );
      }
      wrap.addView(list);
      searchPanel.addView(wrap, Ui.lp(this, -1, -2));
      return;
    }
    ArrayList<Anime> suggestions = localSuggestions(q);
    if (suggestions.isEmpty()) return;
    Ui.space(searchPanel, 10);
    searchPanel.addView(Ui.text(this, "Быстрые совпадения", 12, Ui.TEXT, true));
    Ui.space(searchPanel, 8);
    HorizontalScrollView wrap = new HorizontalScrollView(this);
    wrap.setHorizontalScrollBarEnabled(false);
    LinearLayout list = Ui.row(this);
    for (Anime a : suggestions) {
      String display = YoruBrain.title(a);
      String label =
        display.length() > 28 ? display.substring(0, 27).trim() + "…" : display;
      LinearLayout.LayoutParams lp = Ui.lp(this, -2, -2);
      lp.rightMargin = Ui.dp(this, 7);
      list.addView(
        searchChip(label, () -> {
          YoruApp.app().store.addSearch(q);
          Ui.openDetails(this, a);
        }),
        lp
      );
    }
    wrap.addView(list);
    searchPanel.addView(wrap, Ui.lp(this, -1, -2));
  }

  private ArrayList<Anime> localSuggestions(String q) {
    LinkedHashMap<String, Anime> found = new LinkedHashMap<>();
    String needle = ApiRepository.plainName(q);
    if (needle.length() < 2) return new ArrayList<>();
    ArrayList<Anime> pool = new ArrayList<>();
    pool.addAll(YoruApp.app().store.recent());
    pool.addAll(YoruApp.app().store.favorites());
    pool.addAll(YoruApp.app().api.seed());
    for (Anime a : pool) {
      if (
        !Anime.valid(a) || !YoruBrain.visible(a) || found.containsKey(a.key())
      ) continue;
      String hay = ApiRepository.plainName(
        a.title +
          " " +
          a.original +
          " " +
          a.alias +
          " " +
          String.join(" ", a.genres) +
          " " +
          YoruApp.app().store.note(a) +
          " " +
          YoruApp.app().store.tags(a)
      );
      if (hay.contains(needle)) {
        found.put(a.key(), a);
        if (found.size() >= 8) break;
      }
    }
    return new ArrayList<>(found.values());
  }

  private void catalogueReplace() {
    invalidateScreen(1);
    content.removeAllViews();
    loading = false;
    catalogue();
    capture(1, screenKey(1));
  }

  private void loadCatalog(boolean reset) {
    if (adapter == null || summary == null) return;
    if (reset && catalogFuture != null && !catalogFuture.isDone()) {
      catalogFuture.cancel(true);
      loading = false;
    }
    if (loading && !reset) return;
    String q = query == null ? "" : query.trim().replaceAll("\\s+", " ");
    query = q;
    if (screenCache[1] != null) screenKeys[1] = screenKey(1);
    if (reset) {
      generation++;
      pageNumber = 0;
      catalog.clear();
      more = false;
      adapter.notifyDataSetChanged();
    }
    if (q.length() == 1) {
      loading = false;
      more = false;
      summary.setText(
        "Введите ещё один символ — так поиск будет точнее и быстрее."
      );
      return;
    }
    loading = true;
    int gen = generation,
      requested = pageNumber + 1;
    String source = selectedSource;
    ApiRepository.Filter f = filter;
    summary.setOnClickListener(null);
    summary.setText(
      reset
        ? source.equals("yoru")
          ? q.isEmpty()
            ? "YORU подбирает аниме…"
            : "YORU ищет «" + q + "»…"
          : q.isEmpty()
            ? "Загружаем подборку…"
            : "Ищем «" + q + "»…"
        : "Загружаем ещё…"
    );
    catalogFuture = uiTask(() -> {
      try {
        if (Thread.currentThread().isInterrupted()) return;
        Anime.Page p = YoruApp.app().api.catalog(source, q, requested, f);
        if (Thread.currentThread().isInterrupted()) return;
        YoruApp.app().main.post(() -> {
          if (gen != generation || tab != 1 || isFinishing()) return;
          loading = false;
          pageNumber = requested;
          more = p.more;
          HashSet<String> ids = new HashSet<>();
          for (Anime a : catalog) ids.add(a.key());
          for (Anime a : p.items)
            if (YoruBrain.visible(a) && ids.add(a.key())) catalog.add(a);
          adapter.notifyDataSetChanged();
          String prefix =
            p.total >= 0
              ? "Найдено: " + p.total + " · "
              : q.isEmpty() && YoruApp.app().api.countOf(source) > 0
                ? "В базе: " + YoruApp.app().api.countOf(source) + " · "
                : "";
          if (p.note != null && !p.note.isEmpty()) prefix =
            p.note + " · " + prefix;
          summary.setText(
            prefix +
              "Показано: " +
              catalog.size() +
              (p.more ? " · листайте дальше" : "")
          );
          if (catalog.isEmpty()) summary.setText(
            q.isEmpty()
              ? "Пока ничего не загрузилось. Нажмите для повтора."
              : "Ничего не найдено. Попробуйте другое название."
          );
        });
      } catch (Exception e) {
        YoruApp.app().main.post(() -> {
          if (gen != generation || tab != 1 || isFinishing()) return;
          loading = false;
          more = false;
          if (
            catalog.isEmpty() && source.equals("anilibria") && q.isEmpty()
          ) catalog.addAll(YoruApp.app().api.seed());
          adapter.notifyDataSetChanged();
          summary.setText(
            catalog.isEmpty()
              ? "Не удалось подключиться. Нажмите для повтора."
              : "Сохранённая подборка · нажмите для обновления"
          );
          summary.setOnClickListener(v -> loadCatalog(true));
        });
      }
    });
  }

  private final class CatalogAdapter extends BaseAdapter {

    public int getCount() {
      return catalog.size();
    }

    public Object getItem(int p) {
      return catalog.get(p);
    }

    public long getItemId(int p) {
      return p;
    }

    public View getView(int p, View old, android.view.ViewGroup parent) {
      int cols = getResources().getConfiguration().screenWidthDp >= 650 ? 3 : 2;
      Ui.Card card =
        old instanceof Ui.Card
          ? (Ui.Card) old
          : new Ui.Card(
              MainActivity.this,
              ((getResources().getConfiguration().screenWidthDp / cols - 23) *
                3) /
                2
            );
      card.bind(catalog.get(p));
      return card;
    }
  }

  private void filters() {
    String filterSource =
      selectedSource.equals("shikimori") ||
      selectedSource.equals("yummy") ||
      selectedSource.equals("anilibria")
        ? selectedSource
        : "shikimori";
    LinearLayout c = Ui.column(this);
    c.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
    String[][] genreItems = genresFor(filterSource);
    String[] genreLabels = new String[genreItems.length],
      genreIds = new String[genreItems.length];
    for (int gi = 0; gi < genreItems.length; gi++) {
      genreIds[gi] = genreItems[gi][0];
      genreLabels[gi] = genreItems[gi][1];
    }
    Spinner genre = spinner(
      c,
      "Жанр",
      genreLabels,
      Arrays.asList(genreIds).indexOf(filter.genre)
    );
    ArrayList<String> ys = new ArrayList<>();
    ys.add("Все годы");
    for (
      int y = Calendar.getInstance().get(Calendar.YEAR) + 1;
      y >= 1970;
      y--
    ) ys.add(String.valueOf(y));
    Spinner year = spinner(
      c,
      "Год",
      ys.toArray(new String[0]),
      filter.year.isEmpty() ? 0 : ys.indexOf(filter.year)
    );
    String[] kinds = { "Любой формат", "ТВ", "Фильм", "ONA", "OVA", "Спешл" },
      values = { "", "TV", "MOVIE", "ONA", "OVA", "SPECIAL" };
    Spinner type = spinner(
      c,
      "Формат",
      kinds,
      Arrays.asList(values).indexOf(filter.type)
    );
    String[] statusLabels = { "Любой статус", "Выходит", "Завершён", "Анонс" },
      statusValues = { "", "ongoing", "released", "anons" };
    Spinner status = spinner(
      c,
      "Статус",
      statusLabels,
      Arrays.asList(statusValues).indexOf(filter.status)
    );
    String[] sortLabels = { "По рейтингу", "Популярные", "Сначала новые" },
      sortValues = { "RATING_DESC", "POPULARITY", "YEAR_DESC" };
    Spinner sort = spinner(
      c,
      "Порядок",
      sortLabels,
      Arrays.asList(sortValues).indexOf(filter.sort)
    );
    String filterNote = selectedSource.equals("yoru")
      ? "YORU подберёт подходящие результаты и удобный просмотр."
      : filterSource.equals(selectedSource)
        ? "Фильтры применяются к выбранной подборке."
        : "YORU построит подборку автоматически. Смотреть можно сразу через YORU Player.";
    TextView note = Ui.text(this, filterNote, 10, Ui.MUTED, false);
    note.setLineSpacing(Ui.dp(this, 4), 1);
    c.addView(note);
    Ui.custom(
      this,
      "Подберите историю",
      c,
      "Применить",
      () -> {
        ApiRepository.Filter f = new ApiRepository.Filter();
        f.genre = genreIds[genre.getSelectedItemPosition()];
        f.year =
          year.getSelectedItemPosition() == 0
            ? ""
            : ys.get(year.getSelectedItemPosition());
        f.type = values[type.getSelectedItemPosition()];
        f.status = statusValues[status.getSelectedItemPosition()];
        f.sort = sortValues[sort.getSelectedItemPosition()];
        filter = f;
        invalidateScreen(1);
        loadCatalog(true);
      },
      "Сбросить",
      () -> {
        filter = new ApiRepository.Filter();
        invalidateScreen(1);
        loadCatalog(true);
      },
      "Отмена"
    );
  }

  private String[][] genresFor(String source) {
    if (source.equals("shikimori")) return new String[][] {
      { "", "Все жанры" },
      { "1", "Экшен" },
      { "2", "Приключения" },
      { "4", "Комедия" },
      { "7", "Детектив" },
      { "8", "Драма" },
      { "10", "Фэнтези" },
      { "22", "Романтика" },
      { "24", "Фантастика" },
      { "30", "Спорт" },
      { "36", "Повседневность" },
    };
    if (source.equals("yummy")) return new String[][] {
      { "", "Все жанры" },
      { "63", "Экшен" },
      { "32", "Приключения" },
      { "16", "Комедия" },
      { "14", "Детектив" },
      { "15", "Драма" },
      { "42", "Фэнтези" },
      { "33", "Романтика" },
      { "37", "Фантастика" },
      { "86", "Повседневность" },
    };
    if (source.equals("anilibria")) return new String[][] {
      { "", "Все жанры" },
      { "14", "Экшен" },
      { "27", "Приключения" },
      { "1", "Комедия" },
      { "8", "Драма" },
      { "29", "Фэнтези" },
      { "11", "Романтика" },
    };
    return new String[][] { { "", "Все жанры" } };
  }

  private Spinner spinner(
    LinearLayout col,
    String title,
    String[] values,
    int initial
  ) {
    col.addView(Ui.text(this, title, 11, Ui.MUTED, false));
    Spinner s = new Spinner(this);
    ArrayAdapter<String> a = new ArrayAdapter<>(
      this,
      android.R.layout.simple_spinner_dropdown_item,
      values
    );
    s.setAdapter(a);
    s.setSelection(Math.max(0, initial));
    col.addView(s, Ui.lp(this, -1, 45));
    Ui.space(col, 8);
    return s;
  }

  private void libraryHistory() {
    cancelUiTasks();
    historyView = true;
    generation++;
    content.removeAllViews();
    library(true);
  }

  private void library(boolean history) {
    LinearLayout col = Ui.column(this);
    col.setPadding(Ui.dp(this, 17), Ui.dp(this, 13), Ui.dp(this, 17), 0);
    content.addView(col, new FrameLayout.LayoutParams(-1, -1));
    LinearLayout header = Ui.row(this);
    header.addView(
      Ui.text(this, history ? "История" : "Моя коллекция", 25, Ui.TEXT, true),
      new LinearLayout.LayoutParams(0, -2, 1)
    );
    TextView action = Ui.chip(
      this,
      history ? "Коллекция" : "История",
      false,
      () -> {
        if (history) {
          tab = 2;
          render();
        } else libraryHistory();
      }
    );
    header.addView(action);
    col.addView(header);
    Ui.space(col, 12);
    if (!history) {
      HorizontalScrollView scroll = new HorizontalScrollView(this);
      scroll.setHorizontalScrollBarEnabled(false);
      LinearLayout chips = Ui.row(this);
      ArrayList<String> labels = new ArrayList<>(),
        values = new ArrayList<>();
      labels.add("Все");
      values.add("");
      labels.addAll(Arrays.asList(SecureStore.BUCKET_LABELS));
      values.addAll(Arrays.asList(SecureStore.BUCKETS));
      for (String folder : YoruApp.app().store.libraryFolders()) {
        labels.add("Папка: " + folder);
        values.add("folder:" + folder);
      }
      for (int i = 0; i < values.size(); i++) {
        String value = values.get(i);
        TextView chip = Ui.chip(
          this,
          labels.get(i),
          bucket.equals(value),
          () -> {
            bucket = value;
            invalidateScreen(2);
            render();
          }
        );
        LinearLayout.LayoutParams params = Ui.lp(this, -2, -2);
        params.rightMargin = Ui.dp(this, 7);
        chips.addView(chip, params);
      }
      scroll.addView(chips);
      col.addView(scroll);
      Ui.space(col, 10);
    }
    TextView status = Ui.text(
      this,
      "Открываем сохранённые тайтлы…",
      12,
      Ui.MUTED,
      false
    );
    col.addView(status);
    Ui.space(col, 10);
    GridView list = new GridView(this);
    int columns =
      getResources().getConfiguration().screenWidthDp >= 650 ? 3 : 2;
    list.setNumColumns(columns);
    list.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
    list.setVerticalSpacing(Ui.dp(this, 8));
    list.setHorizontalSpacing(Ui.dp(this, 5));
    list.setVerticalScrollBarEnabled(false);
    list.setClipToPadding(false);
    list.setPadding(0, 0, 0, Ui.dp(this, 16));
    col.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
    final int gen = generation;
    final String selectedBucket = bucket;
    uiTask(() -> {
      List<Anime> rows = YoruBrain.visible(
        history
          ? YoruApp.app().store.recent()
          : YoruApp.app().store.favorites(selectedBucket, "")
      );
      YoruApp.app().main.post(() -> {
        if (gen != generation || isFinishing()) return;
        status.setText(
          rows.isEmpty()
            ? history
              ? "Пока нет просмотров"
              : "Коллекция пуста. Добавьте аниме из каталога."
            : "Тайтлов: " + rows.size()
        );
        list.setAdapter(
          new BaseAdapter() {
            public int getCount() {
              return rows.size();
            }

            public Object getItem(int position) {
              return rows.get(position);
            }

            public long getItemId(int position) {
              return position;
            }

            public View getView(int position, View old, ViewGroup parent) {
              Ui.Card card =
                old instanceof Ui.Card
                  ? (Ui.Card) old
                  : new Ui.Card(
                      MainActivity.this,
                      ((getResources().getConfiguration().screenWidthDp /
                        columns -
                        25) *
                        3) /
                        2
                    );
              Anime anime = rows.get(position);
              card.bind(anime);
              if (history) card.setOnClickListener(v -> {
                JSONObject progress = YoruApp.app().store.progress(anime);
                Ui.openPlayer(
                  MainActivity.this,
                  anime,
                  "auto",
                  progress.optDouble("episode", 1)
                );
              });
              return card;
            }
          }
        );
        list.setSelection(Math.max(0, scrollState[slot(false, tab)]));
      });
    });
  }

  private void settings() {
    String[] choices = {
      "Просмотр",
      "Внешний вид",
      "Данные и чистка",
      "О проекте",
    };
    Ui.choices(this, "YORU", choices, i -> {
      switch (i) {
        case 0:
          playbackSettings();
          break;
        case 1:
          appearanceSettings();
          break;
        case 2:
          dataPanel();
          break;
        default:
          aboutDialog();
          break;
      }
    });
  }

  private void playbackSettings() {
    LinearLayout col = Ui.column(this);
    col.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
    TextView fav = Ui.text(
      this,
      "Любимые озвучки: " +
        YoruApp.app().store.favoriteVoiceSummary() +
        "\nНовая выбранная озвучка добавляется в начало приоритета. Режим “только выбранная” оставлен отдельно.",
      11,
      Ui.MUTED,
      false
    );
    fav.setLineSpacing(Ui.dp(this, 4), 1);
    col.addView(fav);
    Ui.space(col, 10);
    String currentVoice = YoruApp.app().store.voicePreference();
    int voiceIndex = 0;
    for (int i = 0; i < ApiRepository.VOICE_PREF_VALUES.length; i++) if (
      ApiRepository.voiceKey(ApiRepository.VOICE_PREF_VALUES[i]).equals(
        ApiRepository.voiceKey(currentVoice)
      )
    ) voiceIndex = i;
    Spinner voice = spinner(
      col,
      "Озвучка по умолчанию",
      ApiRepository.VOICE_PREF_NAMES,
      voiceIndex
    );
    Switch onlyVoice = new Switch(this);
    onlyVoice.setText("Показывать только выбранную озвучку");
    onlyVoice.setTextColor(Ui.TEXT);
    onlyVoice.setTextSize(13);
    onlyVoice.setTypeface(Ui.typeface(this, false));
    onlyVoice.setChecked(YoruApp.app().store.onlyPreferredVoice());
    col.addView(onlyVoice, Ui.lp(this, -1, 50));
    Spinner quality = spinner(
      col,
      "Разрешение Quality+",
      QualityPlus.LABELS,
      QualityPlus.index(YoruApp.app().store.quality())
    );
    Switch next = new Switch(this);
    next.setText("Следующая серия автоматически");
    next.setTextColor(Ui.TEXT);
    next.setTextSize(13);
    next.setTypeface(Ui.typeface(this, false));
    next.setChecked(YoruApp.app().store.autoNext());
    col.addView(next, Ui.lp(this, -1, 50));
    Switch skip = new Switch(this);
    skip.setText("Автопропуск заставки");
    skip.setTextColor(Ui.TEXT);
    skip.setTextSize(13);
    skip.setTypeface(Ui.typeface(this, false));
    skip.setChecked(YoruApp.app().store.autoSkipOpening());
    col.addView(skip, Ui.lp(this, -1, 50));
    Spinner sleep = spinner(
      col,
      "Таймер сна",
      new String[] {
        "Выключен",
        "15 минут",
        "30 минут",
        "45 минут",
        "60 минут",
        "90 минут",
      },
      Arrays.asList(0, 15, 30, 45, 60, 90).indexOf(
        YoruApp.app().store.sleepTimer()
      )
    );
    Ui.custom(
      this,
      "Просмотр",
      col,
      "Сохранить",
      () -> {
        int[] sleepValues = { 0, 15, 30, 45, 60, 90 };
        String selectedVoice = ApiRepository.VOICE_PREF_VALUES[
          voice.getSelectedItemPosition()
        ];
        YoruApp.app().store.voicePreference(selectedVoice);
        if (!selectedVoice.isEmpty()) YoruApp.app().store.rememberVoice(
          selectedVoice
        );
        YoruApp.app().store.onlyPreferredVoice(
          onlyVoice.isChecked() && !selectedVoice.isEmpty()
        );
        YoruApp.app().store.settings(
          QualityPlus.values()[quality.getSelectedItemPosition()],
          next.isChecked()
        );
        YoruApp.app().store.smartSetting("autoSkipOpening", skip.isChecked());
        YoruApp.app().store.sleepTimer(
          sleepValues[sleep.getSelectedItemPosition()]
        );
        Ui.toast(this, "Настройки сохранены");
      },
      null,
      null,
      "Отмена"
    );
  }

  private void profileAction(String action) {
    if (action.equals("data")) {
      dataPanel();
      return;
    }
    if (action.equals("playback")) {
      playbackSettings();
      return;
    }
    if (action.equals("appearance")) {
      appearanceSettings();
      return;
    }
    if (action.equals("queue")) {
      FeaturePanels.queue(this);
      return;
    }
    if (action.equals("smart")) {
      FeaturePanels.smart(this);
      return;
    }
    if (action.equals("insights")) {
      FeaturePanels.stats(this);
      return;
    }
    if (action.equals("cleanup")) {
      FeaturePanels.cleanup(this, () -> render());
      return;
    }
    if (action.equals("import")) {
      startActivityForResult(
        new Intent(Intent.ACTION_OPEN_DOCUMENT)
          .setType("application/json")
          .addCategory(Intent.CATEGORY_OPENABLE),
        101
      );
      return;
    }
    if (action.equals("export")) {
      startActivityForResult(
        new Intent(Intent.ACTION_CREATE_DOCUMENT)
          .setType("application/json")
          .addCategory(Intent.CATEGORY_OPENABLE)
          .putExtra(Intent.EXTRA_TITLE, "yoru-android-collection.json"),
        102
      );
      return;
    }
    aboutDialog();
  }

  private void dataPanel() {
    LinearLayout col = Ui.column(this);
    col.setPadding(Ui.dp(this, 2), 0, Ui.dp(this, 2), 0);
    col.addView(
      Ui.text(
        this,
        "Импорт, экспорт и чистка собраны здесь, чтобы профиль не был перегружен.",
        12,
        Ui.MUTED,
        false
      )
    );
    Ui.space(col, 12);
    col.addView(
      Ui.button(this, "Импорт коллекции", false, () ->
        startActivityForResult(
          new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType("application/json")
            .addCategory(Intent.CATEGORY_OPENABLE),
          101
        )
      )
    );
    Ui.space(col, 8);
    col.addView(
      Ui.button(this, "Экспорт коллекции", false, () ->
        startActivityForResult(
          new Intent(Intent.ACTION_CREATE_DOCUMENT)
            .setType("application/json")
            .addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_TITLE, "yoru-android-collection.json"),
          102
        )
      )
    );
    Ui.space(col, 8);
    col.addView(
      Ui.button(this, "Чистка коллекции", false, () ->
        FeaturePanels.cleanup(this, () -> render())
      )
    );
    Ui.custom(this, "Данные YORU", col, "Готово", null, null, null, null);
  }

  private void appearanceSettings() {
    LinearLayout col = Ui.column(this);
    col.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
    TextView preview = Ui.text(
      this,
      "Так будет выглядеть текст в YORU",
      16,
      Ui.TEXT,
      true
    );
    preview.setPadding(
      Ui.dp(this, 14),
      Ui.dp(this, 14),
      Ui.dp(this, 14),
      Ui.dp(this, 14)
    );
    preview.setBackground(Ui.stroke(Ui.CARD, 14, this));
    col.addView(preview);
    Ui.space(col, 12);
    TextView size = Ui.text(
      this,
      "Размер текста: " + YoruApp.app().store.fontScale() + "%",
      12,
      Ui.MUTED,
      true
    );
    col.addView(size);
    SeekBar bar = new SeekBar(this);
    bar.setMax(45);
    bar.setProgress(YoruApp.app().store.fontScale() - 85);
    col.addView(bar, Ui.lp(this, -1, 42));
    bar.setOnSeekBarChangeListener(
      new SeekBar.OnSeekBarChangeListener() {
        public void onProgressChanged(SeekBar b, int p, boolean f) {
          int value = 85 + p;
          size.setText("Размер текста: " + value + "%");
          preview.setTextSize((16 * value) / 100f);
        }

        public void onStartTrackingTouch(SeekBar b) {}

        public void onStopTrackingTouch(SeekBar b) {}
      }
    );
    Switch custom = new Switch(this);
    custom.setText("Использовать свой .ttf / .otf");
    custom.setTextColor(Ui.TEXT);
    custom.setTextSize(13);
    custom.setTypeface(Ui.typeface(this, false));
    custom.setChecked(YoruApp.app().store.customFont());
    col.addView(custom, Ui.lp(this, -1, 50));
    TextView current = Ui.text(
      this,
      "Текущий шрифт: " + YoruApp.app().store.fontName(),
      11,
      Ui.MUTED,
      false
    );
    col.addView(current);
    Ui.space(col, 10);
    col.addView(
      Ui.button(this, "Выбрать файл шрифта", false, () -> {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("*/*");
        pick.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
          "font/ttf",
          "font/otf",
          "application/x-font-ttf",
          "application/x-font-otf",
          "application/octet-stream",
        });
        startActivityForResult(pick, 103);
      })
    );
    Ui.space(col, 8);
    col.addView(
      Ui.text(
        this,
        "Файл копируется внутрь приложения. Можно выбрать шрифт из Google Files или любого проводника.",
        10,
        Ui.MUTED,
        false
      )
    );
    Ui.custom(
      this,
      "Внешний вид",
      col,
      "Сохранить",
      () -> {
        YoruApp.app().store.appearance(
          85 + bar.getProgress(),
          custom.isChecked(),
          YoruApp.app().store.fontName()
        );
        Ui.toast(this, "Внешний вид обновлён");
        render();
      },
      "Сбросить",
      () -> {
        YoruApp.app().store.appearance(100, false, "Системный");
        Ui.toast(this, "Внешний вид сброшен");
        render();
      },
      "Отмена"
    );
  }

  private void aboutDialog() {
    Ui.message(
      this,
      "Это YORU.",
      "Ваше пространство для любимых историй. YORU, календарь выхода, умный просмотр, офлайн, уведомления и минималистичный просмотр.\n\nВерсия " +
        BuildConfig.VERSION_NAME +
        "\nМатериалы принадлежат их правообладателям.",
      "Приятного просмотра",
      null
    );
  }

  private String fileName(Uri uri) {
    String name = uri == null ? "" : uri.getLastPathSegment();
    if (name == null || name.trim().isEmpty()) return "Свой шрифт";
    int cut = Math.max(name.lastIndexOf('/'), name.lastIndexOf(':'));
    if (cut >= 0 && cut + 1 < name.length()) name = name.substring(cut + 1);
    return name.length() > 48 ? name.substring(name.length() - 48) : name;
  }

  private void importFont(Uri uri) {
    Ui.Progress wait = Ui.progress(this, "Импортируем шрифт…", false, null);
    YoruApp.app().io.execute(() -> {
      try {
        File outFile = new File(getFilesDir(), "yoru-custom-font.ttf");
        try (
          InputStream in = getContentResolver().openInputStream(uri);
          OutputStream out = new FileOutputStream(outFile)
        ) {
          if (in == null) throw new IOException();
          byte[] b = new byte[8192];
          int n;
          long total = 0;
          while ((n = in.read(b)) != -1) {
            total += n;
            if (total > 8 * 1024 * 1024) throw new IOException();
            out.write(b, 0, n);
          }
        }
        YoruApp.app().store.appearance(
          YoruApp.app().store.fontScale(),
          true,
          fileName(uri)
        );
        YoruApp.app().main.post(() -> {
          wait.dismiss();
          Ui.toast(this, "Шрифт применён");
          render();
        });
      } catch (Exception e) {
        YoruApp.app().main.post(() -> {
          wait.dismiss();
          Ui.toast(this, "Не удалось импортировать шрифт");
        });
      }
    });
  }

  @Override
  protected void onPause() {
    rememberScroll();
    YoruApp.app().store.flushAsync();
    super.onPause();
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    rememberScroll();
    if (search != null) query = search.getText().toString().trim();
    out.putInt("tab", tab);
    out.putBoolean("profile", profileView);
    out.putString("source", selectedSource);
    out.putString("query", query);
    out.putString("bucket", bucket);
    out.putIntArray("scroll", scrollState);
    super.onSaveInstanceState(out);
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    if (intent.getBooleanExtra("openDownloads", false)) {
      tab = 3;
      profileView = false;
      render();
    }
  }

  @Override
  protected void onActivityResult(int request, int code, Intent data) {
    super.onActivityResult(request, code, data);
    if (request == OfflineExporter.REQ_SAVE_DOCUMENT) {
      OfflineExporter.finishDocumentSave(this, code, data);
      return;
    }
    if (code != RESULT_OK || data == null || data.getData() == null) return;
    Uri uri = data.getData();
    if (request == 103) {
      importFont(uri);
      return;
    }
    YoruApp.app().io.execute(() -> {
      try {
        if (request == 101) {
          String text = ApiRepository.readStream(
            getContentResolver().openInputStream(uri),
            5 * 1024 * 1024
          );
          int n = YoruApp.app().store.importData(text);
          YoruApp.app().main.post(() -> {
            Ui.toast(this, "Импортировано: " + n);
            render();
          });
        } else if (request == 102) {
          try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out != null) out.write(
              YoruApp.app().store.exportData().getBytes(StandardCharsets.UTF_8)
            );
          }
          YoruApp.app().main.post(() -> Ui.toast(this, "Коллекция сохранена"));
        }
      } catch (Exception e) {
        YoruApp.app().main.post(() ->
          Ui.toast(
            this,
            "Не удалось обработать файл. Проверьте формат и доступ."
          )
        );
      }
    });
  }

  @Override
  public void handleBack() {
    if (content == null) {
      finish();
      return;
    }
    rememberScroll();
    if (profileView) {
      profileView = false;
      render();
    } else if (sourceView) {
      sourceView = false;
      render();
    } else if (historyView) {
      historyView = false;
      render();
    } else if (tab != 0) {
      tab = 0;
      render();
    } else if (Build.VERSION.SDK_INT >= 31 && isTaskRoot()) moveTaskToBack(
      true
    );
    else finish();
  }

  @Override
  protected void onDestroy() {
    rememberScroll();
    generation++;
    if (searchTask != null) handler.removeCallbacks(searchTask);
    handler.removeCallbacksAndMessages(null);
    cancelUiTasks();
    super.onDestroy();
  }
}
