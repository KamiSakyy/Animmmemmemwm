package app.yoru.sourcelab;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(7, 10, 18);
    private static final int CARD = Color.rgb(18, 23, 36);
    private static final int CARD2 = Color.rgb(27, 34, 53);
    private static final int TEXT = Color.rgb(238, 242, 255);
    private static final int MUTED = Color.rgb(156, 167, 190);
    private static final int ACCENT = Color.rgb(124, 92, 255);
    private static final int GOOD = Color.rgb(68, 220, 153);
    private static final int BAD = Color.rgb(255, 94, 117);
    private static final int WARN = Color.rgb(255, 196, 87);

    private final ExecutorService io = Executors.newFixedThreadPool(4);
    private final ArrayList<AnimeSource> sources = new ArrayList<>();
    private int selected = 0;
    private LinearLayout chips;
    private LinearLayout content;
    private TextView status;
    private EditText search;
    private ProgressBar progress;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        sources.add(new AnimeOnSource());
        sources.add(new CoaniSource());
        sources.add(new AnimeUaSource());
        buildUi();
        select(0, true);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(16), dp(14), dp(16), dp(10));
        top.setBackgroundColor(BG);
        TextView h = text("YORU Source Player", 25, TEXT, true);
        top.addView(h);
        TextView s = text("Отдельное приложение для проверки новых источников. YORU 4.13.0 не используется и не меняется.", 13, MUTED, false);
        s.setPadding(0, dp(4), 0, dp(10));
        top.addView(s);

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        hsv.addView(chips, new HorizontalScrollView.LayoutParams(-2, -2));
        top.addView(hsv);
        for (int i = 0; i < sources.size(); i++) {
            final int index = i;
            Button b = chip(sources.get(i).shortName());
            b.setOnClickListener(v -> select(index, true));
            chips.addView(b);
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        search = new EditText(this);
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setTextColor(TEXT);
        search.setHintTextColor(MUTED);
        search.setTextSize(15);
        search.setPadding(dp(12), 0, dp(12), 0);
        search.setBackgroundColor(CARD2);
        row.addView(search, new LinearLayout.LayoutParams(0, dp(46), 1f));
        Button go = action("Поиск");
        go.setOnClickListener(v -> loadCatalog(true));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(96), dp(46));
        bp.setMargins(dp(8), 0, 0, 0);
        row.addView(go, bp);
        top.addView(row);

        status = text("", 13, MUTED, false);
        status.setPadding(0, dp(8), 0, 0);
        top.addView(status);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        top.addView(progress, new LinearLayout.LayoutParams(-1, dp(4)));
        root.addView(top);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(4), dp(12), dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    private void select(int index, boolean refresh) {
        selected = index;
        AnimeSource src = sources.get(index);
        search.setHint("Поиск в " + src.shortName() + ": " + src.defaultQuery());
        search.setText(src.defaultQuery());
        for (int i = 0; i < chips.getChildCount(); i++) {
            Button b = (Button) chips.getChildAt(i);
            b.setBackgroundColor(i == selected ? ACCENT : CARD2);
            b.setTextColor(i == selected ? Color.WHITE : MUTED);
        }
        status.setText(src.name() + " · " + src.baseUrl());
        if (refresh) loadCatalog(false);
    }

    private void loadCatalog(boolean fromSearch) {
        AnimeSource src = sources.get(selected);
        String q = fromSearch && search.getText() != null ? search.getText().toString().trim() : "";
        if (fromSearch && q.isEmpty()) q = src.defaultQuery();
        final String query = q;
        progress.setVisibility(View.VISIBLE);
        status.setText("Загрузка каталога " + src.shortName() + (query.isEmpty() ? "" : " · “" + query + "”") + "…");
        content.removeAllViews();
        addInfo("Загружаю реальные карточки источника…", MUTED);
        io.execute(() -> {
            ArrayList<AnimeItem> items;
            Exception error = null;
            try { items = src.catalog(query); }
            catch (Exception e) { items = new ArrayList<>(); error = e; }
            final ArrayList<AnimeItem> out = items;
            final Exception err = error;
            runOnUiThread(() -> {
                progress.setVisibility(View.GONE);
                content.removeAllViews();
                if (err != null) {
                    status.setText("Ошибка " + src.shortName() + ": " + err.getMessage());
                    addInfo("Источник не отдал каталог:\n" + err.getClass().getSimpleName() + ": " + err.getMessage(), BAD);
                    return;
                }
                status.setText(src.name() + " · карточек: " + out.size());
                addSourceHeader(src);
                if (out.isEmpty()) addInfo("Пусто. Попробуй другой запрос.", WARN);
                for (AnimeItem item : out) addCard(src, item);
            });
        });
    }

    private void addSourceHeader(AnimeSource src) {
        LinearLayout p = panel();
        p.addView(text(src.name(), 20, TEXT, true));
        TextView n = text(src.description(), 13, MUTED, false);
        n.setPadding(0, dp(5), 0, 0);
        p.addView(n);
        content.addView(p);
    }

    private void addCard(AnimeSource src, AnimeItem item) {
        LinearLayout card = panel();
        card.setOrientation(LinearLayout.HORIZONTAL);
        ImageView img = new ImageView(this);
        img.setBackgroundColor(CARD2);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(92), dp(132));
        card.addView(img, ip);
        if (!empty(item.poster)) Images.load(item.poster, img);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), 0, 0, 0);
        card.addView(box, new LinearLayout.LayoutParams(0, -2, 1f));
        box.addView(text(item.title, 18, TEXT, true));
        TextView meta = text(item.metaLine(), 12, MUTED, false);
        meta.setPadding(0, dp(5), 0, dp(6));
        box.addView(meta);
        if (!empty(item.description)) box.addView(text(cut(item.description, 170), 13, Color.rgb(205, 213, 232), false));
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button open = action("Карточка");
        open.setOnClickListener(v -> openDetails(src, item));
        Button site = subtle("Сайт");
        site.setOnClickListener(v -> openUrl(item.webUrl));
        buttons.addView(open, new LinearLayout.LayoutParams(0, dp(42), 1f));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, dp(42), 1f);
        sp.setMargins(dp(8), 0, 0, 0);
        buttons.addView(site, sp);
        buttons.setPadding(0, dp(10), 0, 0);
        box.addView(buttons);
        content.addView(card);
    }

    private void openDetails(AnimeSource src, AnimeItem item) {
        progress.setVisibility(View.VISIBLE);
        status.setText("Открываю карточку: " + item.title + "…");
        content.removeAllViews();
        addInfo("Загружаю карточку, серии и ссылки для собственного плеера…", MUTED);
        io.execute(() -> {
            AnimeDetails details;
            Exception error = null;
            try { details = src.details(item); }
            catch (Exception e) { details = null; error = e; }
            final AnimeDetails out = details;
            final Exception err = error;
            runOnUiThread(() -> {
                progress.setVisibility(View.GONE);
                content.removeAllViews();
                if (err != null || out == null) {
                    status.setText("Ошибка карточки " + src.shortName());
                    addInfo((err == null ? "Не удалось открыть карточку" : err.getClass().getSimpleName() + ": " + err.getMessage()), BAD);
                    Button back = action("Назад в каталог");
                    back.setOnClickListener(v -> loadCatalog(false));
                    content.addView(back, new LinearLayout.LayoutParams(-1, dp(48)));
                    return;
                }
                status.setText(src.shortName() + " · " + out.title + " · серий/вариантов: " + out.episodes.size());
                renderDetails(src, out);
            });
        });
    }

    private void renderDetails(AnimeSource src, AnimeDetails d) {
        LinearLayout head = panel();
        head.setOrientation(LinearLayout.HORIZONTAL);
        ImageView img = new ImageView(this);
        img.setBackgroundColor(CARD2);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        head.addView(img, new LinearLayout.LayoutParams(dp(112), dp(160)));
        if (!empty(d.poster)) Images.load(d.poster, img);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), 0, 0, 0);
        head.addView(box, new LinearLayout.LayoutParams(0, -2, 1f));
        box.addView(text(d.title, 21, TEXT, true));
        box.addView(text(d.meta, 13, MUTED, false));
        if (!empty(d.description)) {
            TextView desc = text(cut(d.description, 560), 13, Color.rgb(207, 216, 236), false);
            desc.setPadding(0, dp(8), 0, 0);
            box.addView(desc);
        }
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button back = subtle("Каталог");
        back.setOnClickListener(v -> loadCatalog(false));
        Button site = subtle("Сайт");
        site.setOnClickListener(v -> openUrl(d.webUrl));
        actions.addView(back, new LinearLayout.LayoutParams(0, dp(42), 1f));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, dp(42), 1f); sp.setMargins(dp(8), 0, 0, 0);
        actions.addView(site, sp);
        actions.setPadding(0, dp(10), 0, 0);
        box.addView(actions);
        content.addView(head);

        if (d.episodes.isEmpty()) {
            addInfo("Серии/потоки не найдены. Этот источник пока не подтверждён для просмотра.", WARN);
            return;
        }
        addInfo("Серии и варианты озвучки. Нажимай — приложение попробует открыть поток в собственном плеере.", GOOD);
        for (Episode ep : d.episodes) addEpisode(src, d, ep);
    }

    private void addEpisode(AnimeSource src, AnimeDetails d, Episode ep) {
        LinearLayout row = panel();
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        TextView name = text(ep.title, 16, TEXT, true);
        row.addView(name);
        TextView m = text(ep.meta, 12, MUTED, false);
        m.setPadding(0, dp(3), 0, dp(8));
        row.addView(m);
        Button play = action("▶ Смотреть в собственном плеере");
        play.setOnClickListener(v -> playEpisode(src, d, ep));
        row.addView(play, new LinearLayout.LayoutParams(-1, dp(46)));
        content.addView(row);
    }

    private void playEpisode(AnimeSource src, AnimeDetails d, Episode ep) {
        progress.setVisibility(View.VISIBLE);
        status.setText("Ищу прямой HLS/MP4 поток: " + ep.title + "…");
        io.execute(() -> {
            PlayLink link;
            Exception error = null;
            try { link = src.resolve(ep); }
            catch (Exception e) { link = null; error = e; }
            final PlayLink out = link;
            final Exception err = error;
            runOnUiThread(() -> {
                progress.setVisibility(View.GONE);
                if (err != null || out == null || empty(out.url)) {
                    status.setText("Поток не найден: " + ep.title);
                    addInfo("Не удалось получить прямой поток для собственного плеера.\n" + (err == null ? "Источник не отдал HLS/MP4." : err.getClass().getSimpleName() + ": " + err.getMessage()), BAD);
                    return;
                }
                status.setText("Открываю player: " + out.qualitySummary);
                Intent i = new Intent(this, PlayerActivity.class);
                i.putExtra("url", out.url);
                i.putExtra("title", d.title + " · " + ep.title);
                i.putExtra("referer", out.referer);
                i.putExtra("source", src.shortName());
                i.putExtra("quality", out.qualitySummary);
                startActivity(i);
            });
        });
    }

    private void addInfo(String s, int color) {
        TextView t = text(s, 14, color, false);
        t.setPadding(dp(12), dp(12), dp(12), dp(12));
        t.setBackgroundColor(CARD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(8), 0, dp(8));
        content.addView(t, lp);
    }

    private void openUrl(String url) {
        try { if (!empty(url)) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Throwable ignored) {}
    }

    private LinearLayout panel() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(12), dp(12), dp(12), dp(12));
        l.setBackgroundColor(CARD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(8), 0, dp(8));
        l.setLayoutParams(lp);
        return l;
    }

    private Button chip(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setPadding(dp(10), 0, dp(10), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(42));
        lp.setMargins(0, 0, dp(8), dp(8));
        b.setLayoutParams(lp);
        return b;
    }

    private Button action(String s) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(s);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(ACCENT);
        return b;
    }

    private Button subtle(String s) {
        Button b = action(s);
        b.setTextColor(TEXT);
        b.setBackgroundColor(CARD2);
        return b;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s == null ? "" : s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(dp(2), 1.0f);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private static boolean empty(String s) { return s == null || s.trim().isEmpty(); }
    private static String cut(String s, int max) { if (s == null) return ""; s = Jsoup.parse(s).text().replaceAll("\\s+", " ").trim(); return s.length() <= max ? s : s.substring(0, max - 1) + "…"; }

    interface AnimeSource {
        String name();
        String shortName();
        String baseUrl();
        String defaultQuery();
        String description();
        ArrayList<AnimeItem> catalog(String query) throws Exception;
        AnimeDetails details(AnimeItem item) throws Exception;
        PlayLink resolve(Episode episode) throws Exception;
    }

    static final class AnimeItem {
        String sourceId, id, slug, title, original, poster, description, webUrl, status, type;
        int year, episodes;
        String metaLine() {
            ArrayList<String> p = new ArrayList<>();
            if (year > 0) p.add(String.valueOf(year));
            if (!empty(type)) p.add(type);
            if (episodes > 0) p.add("серий: " + episodes);
            if (!empty(status)) p.add(status);
            return String.join(" · ", p);
        }
    }

    static final class AnimeDetails {
        String title, poster, description, meta, webUrl;
        final ArrayList<Episode> episodes = new ArrayList<>();
    }

    static final class Episode {
        String sourceId, title, meta, streamUrl, referer, apiUrl, playerUrl;
        int animeId, episodeId, playerId, translationId;
    }

    static final class PlayLink {
        String url, referer, qualitySummary;
        PlayLink(String u, String r, String q) { url = u; referer = r; qualitySummary = q; }
    }

    static final class AnimeOnSource implements AnimeSource {
        private static final String BASE = "https://animeon.club";
        @Override public String name() { return "AnimeON"; }
        @Override public String shortName() { return "AnimeON"; }
        @Override public String baseUrl() { return BASE; }
        @Override public String defaultQuery() { return "naruto"; }
        @Override public String description() { return "Каталог, карточки, переводы, серии через публичные AnimeON API. Для Ashdi player приложение ищет публичный file:'...m3u8'."; }

        @Override public ArrayList<AnimeItem> catalog(String query) throws Exception {
            String url = BASE + "/api/anime?" + (empty(query) ? "" : "search=" + Http.enc(query) + "&") + "pageSize=24&pageIndex=1";
            JSONObject root = Http.json(url, BASE + "/");
            JSONArray arr = root.optJSONArray("results");
            ArrayList<AnimeItem> out = new ArrayList<>();
            for (int i = 0; arr != null && i < arr.length(); i++) {
                JSONObject j = arr.optJSONObject(i); if (j == null) continue;
                AnimeItem a = new AnimeItem();
                a.sourceId = "animeon";
                a.id = String.valueOf(j.optInt("id"));
                a.slug = j.optString("slug", a.id);
                a.title = first(j.optString("titleUa"), first(j.optString("titleEn"), j.optString("titleOriginal", "Anime"))).trim();
                a.original = first(j.optString("titleEn"), j.optString("titleOriginal"));
                a.description = j.optString("description", "");
                a.year = intYear(j.optString("releaseDate"));
                a.episodes = Math.max(j.optInt("episodesAired"), j.optInt("episodes"));
                a.status = j.optString("status", "");
                a.type = j.optString("type", "");
                a.poster = image(j.optJSONObject("image"));
                a.webUrl = BASE + "/anime/" + a.slug;
                out.add(a);
            }
            return out;
        }

        @Override public AnimeDetails details(AnimeItem item) throws Exception {
            JSONObject d = Http.json(BASE + "/api/anime/" + Http.enc(item.slug), BASE + "/");
            if (d.optBoolean("moved")) d = Http.json(BASE + "/api/anime/" + Http.enc(d.optString("redirectTo", item.slug)), BASE + "/");
            AnimeDetails out = new AnimeDetails();
            int animeId = d.optInt("id", parseInt(item.id));
            out.title = first(d.optString("titleUa"), first(d.optString("titleEn"), item.title)).trim();
            out.poster = image(d.optJSONObject("image")); if (empty(out.poster)) out.poster = item.poster;
            out.description = d.optString("description", item.description);
            out.webUrl = BASE + "/anime/" + d.optString("slug", item.slug);
            out.meta = meta(d.optString("titleEn"), d.optString("releaseDate"), d.optString("type"), d.optString("status"), d.optString("episodeTime"), genres(d.optJSONArray("genres")));

            JSONObject trRoot = Http.json(BASE + "/api/player/" + animeId + "/translations", BASE + "/");
            JSONArray trs = trRoot.optJSONArray("translations");
            int guard = 0;
            for (int i = 0; trs != null && i < trs.length() && guard < 80; i++) {
                JSONObject row = trs.optJSONObject(i); if (row == null) continue;
                JSONObject tr = row.optJSONObject("translation");
                JSONArray players = row.optJSONArray("player");
                int trId = tr == null ? 0 : tr.optInt("id");
                String trName = tr == null ? "Озвучка" : tr.optString("name", "Озвучка");
                for (int p = 0; players != null && p < players.length() && guard < 80; p++) {
                    JSONObject pl = players.optJSONObject(p); if (pl == null) continue;
                    int playerId = pl.optInt("id");
                    String playerName = pl.optString("name", "Player");
                    int epCount = pl.optInt("episodesCount", 0);
                    String epsUrl = BASE + "/api/player/" + animeId + "/episodes?take=100&playerId=" + playerId + "&translationId=" + trId + "&skip=0&includeAlternative=true";
                    JSONArray eps = null;
                    try { eps = Http.json(epsUrl, BASE + "/").optJSONArray("episodes"); } catch (Exception ignored) {}
                    if (eps != null && eps.length() > 0) {
                        for (int e = 0; e < eps.length() && guard < 80; e++) {
                            JSONObject ej = eps.optJSONObject(e); if (ej == null) continue;
                            Episode ep = new Episode();
                            ep.sourceId = "animeon"; ep.animeId = animeId; ep.episodeId = ej.optInt("id"); ep.playerId = playerId; ep.translationId = trId;
                            ep.title = "Серия " + cleanNumber(ej.opt("episode")) + " · " + trName;
                            ep.meta = playerName + " · episodeId=" + ep.episodeId;
                            out.episodes.add(ep); guard++;
                        }
                    } else if (epCount <= 1 && playerId > 0 && trId > 0) {
                        Episode ep = new Episode();
                        ep.sourceId = "animeon"; ep.animeId = animeId; ep.playerId = playerId; ep.translationId = trId;
                        ep.title = "Фильм / выпуск · " + trName;
                        ep.meta = playerName + " · direct player";
                        ep.apiUrl = BASE + "/api/player/" + playerId + "/" + trId;
                        out.episodes.add(ep); guard++;
                    }
                }
            }
            return out;
        }

        @Override public PlayLink resolve(Episode e) throws Exception {
            String videoUrl;
            if (!empty(e.apiUrl)) videoUrl = Http.json(e.apiUrl, BASE + "/").optString("videoUrl", "");
            else videoUrl = Http.json(BASE + "/api/player/" + e.episodeId + "/episode", BASE + "/").optString("videoUrl", "");
            if (empty(videoUrl)) throw new java.io.IOException("AnimeON не отдал videoUrl");
            if (isPlayable(videoUrl)) return checked(videoUrl, BASE + "/");
            String resolved = Resolver.findPlayable(videoUrl, BASE + "/");
            if (empty(resolved)) throw new java.io.IOException("Ashdi/player не показал прямой .m3u8/.mp4 в публичном HTML");
            return checked(resolved, "https://ashdi.vip/");
        }

        private static String image(JSONObject image) {
            if (image == null) return "";
            String p = first(image.optString("preview"), image.optString("original"));
            return empty(p) ? "" : BASE + "/api/uploads/images/" + p;
        }
    }

    static final class CoaniSource implements AnimeSource {
        private static final String BASE = "https://coani.net";
        @Override public String name() { return "Coani"; }
        @Override public String shortName() { return "Coani"; }
        @Override public String baseUrl() { return BASE; }
        @Override public String defaultQuery() { return ""; }
        @Override public String description() { return "Каталог и карточки через public API. Серии обычно отдают прямой HLS master.m3u8 — лучший кандидат для просмотра сразу."; }

        @Override public ArrayList<AnimeItem> catalog(String query) throws Exception {
            JSONObject root = Http.json(BASE + "/api/public/film/catalog?search=" + Http.enc(query) + "&page=1&limit=24", BASE + "/");
            JSONArray arr = root.optJSONArray("data");
            ArrayList<AnimeItem> out = parse(arr);
            if (out.isEmpty() && !empty(query)) out = parse(Http.json(BASE + "/api/public/film/catalog?search=&page=1&limit=24", BASE + "/").optJSONArray("data"));
            return out;
        }

        private ArrayList<AnimeItem> parse(JSONArray arr) {
            ArrayList<AnimeItem> out = new ArrayList<>();
            for (int i = 0; arr != null && i < arr.length(); i++) {
                JSONObject row = arr.optJSONObject(i); JSONObject j = row == null ? null : row.optJSONObject("data"); if (j == null) continue;
                AnimeItem a = new AnimeItem();
                a.sourceId = "coani"; a.id = String.valueOf(j.optInt("id")); a.slug = j.optString("seo_slug");
                a.title = j.optString("name", "Anime"); a.original = j.optString("film_name", ""); a.description = j.optString("short_description", j.optString("description", ""));
                a.year = j.optInt("year", 0); a.episodes = j.optInt("planed_series", 0); a.type = j.optString("type", "");
                JSONObject preview = j.optJSONObject("preview"); a.poster = preview == null ? "" : first(preview.optString("preview"), preview.optString("preview_main"));
                a.webUrl = BASE + "/catalog/" + j.optString("film_seo_slug", "film") + "/" + a.slug;
                JSONObject cnt = j.optJSONObject("seria_counter"); if (cnt != null) a.episodes = Math.max(a.episodes, cnt.optInt("max_seria_number"));
                out.add(a);
            }
            return out;
        }

        @Override public AnimeDetails details(AnimeItem item) throws Exception {
            JSONObject root = Http.json(BASE + "/api/public/film/season?slug=" + Http.enc(item.slug), BASE + "/");
            JSONObject j = root.optJSONObject("data"); if (j == null) throw new java.io.IOException("Coani season API пустой");
            AnimeDetails out = new AnimeDetails();
            int seasonId = j.optInt("id", parseInt(item.id));
            out.title = j.optString("name", item.title); out.description = j.optString("description", item.description); out.webUrl = item.webUrl;
            JSONObject preview = j.optJSONObject("preview"); out.poster = preview == null ? item.poster : first(preview.optString("preview"), first(preview.optString("preview_main"), item.poster));
            out.meta = meta(j.optJSONObject("film") == null ? item.original : j.optJSONObject("film").optString("origin_name"), String.valueOf(j.optInt("year", item.year)), j.optString("type"), "", "", coaniGenres(j.optJSONArray("categories")));
            JSONObject series = Http.json(BASE + "/api/public/film/season/" + seasonId + "/series", BASE + "/");
            JSONArray list = series.optJSONArray("data");
            for (int i = 0; list != null && i < list.length() && out.episodes.size() < 120; i++) {
                JSONObject row = list.optJSONObject(i); JSONObject d = row == null ? null : row.optJSONObject("data"); if (d == null) continue;
                String video = d.optString("video", ""); if (empty(video)) continue;
                Episode ep = new Episode(); ep.sourceId = "coani"; ep.streamUrl = video; ep.referer = BASE + "/";
                ep.title = "Серия " + d.optInt("number") + " · " + d.optString("voice_type", "VOICE");
                ep.meta = "duration=" + d.optInt("duration") + "s · " + Http.host(video);
                out.episodes.add(ep);
            }
            return out;
        }

        @Override public PlayLink resolve(Episode episode) throws Exception { return checked(episode.streamUrl, BASE + "/"); }
    }

    static final class AnimeUaSource implements AnimeSource {
        private static final String BASE = "https://animeua.club";
        @Override public String name() { return "AnimeUA"; }
        @Override public String shortName() { return "AnimeUA"; }
        @Override public String baseUrl() { return BASE; }
        @Override public String defaultQuery() { return "naruto"; }
        @Override public String description() { return "HTML-каталог и поиск. Сайт предупреждает о региональном плеере; приложение ищет только публичные iframe/HLS markers."; }

        @Override public ArrayList<AnimeItem> catalog(String query) throws Exception {
            Document doc;
            if (empty(query)) doc = Jsoup.parse(Http.get(BASE + "/", BASE + "/"), BASE + "/");
            else doc = Jsoup.parse(Http.postForm(BASE + "/index.php", "do=search&subaction=search&story=" + Http.enc(query.replace(' ', '+')), BASE + "/"), BASE + "/");
            return collect(doc);
        }

        @Override public AnimeDetails details(AnimeItem item) throws Exception {
            String html = Http.get(item.webUrl, BASE + "/");
            Document doc = Jsoup.parse(html, item.webUrl);
            AnimeDetails out = new AnimeDetails(); out.title = item.title; out.poster = item.poster; out.webUrl = item.webUrl;
            Element desc = doc.selectFirst("#news-id, .full-text, .story_c_text, .quote, article");
            out.description = desc == null ? item.description : desc.text();
            String body = doc.text();
            Matcher ep = Pattern.compile("Епізодів:\\s*(\\d+)").matcher(body);
            String region = body.contains("Плеєр доступний лише") ? " · региональный player UA" : "";
            out.meta = (ep.find() ? "серий: " + ep.group(1) : "HTML карточка") + region;
            LinkedHashSet<String> urls = new LinkedHashSet<>();
            Elements frames = doc.select("iframe[src], iframe[data-src], video[src], source[src]");
            for (Element f : frames) {
                String u = first(f.attr("abs:src"), f.attr("abs:data-src"));
                if (!empty(u)) urls.add(u);
            }
            Matcher m = Pattern.compile("https?://[^\\\"'<>\\s]+?(?:\\.m3u8|\\.mp4)[^\\\"'<>\\s]*", Pattern.CASE_INSENSITIVE).matcher(html);
            while (m.find()) urls.add(cleanUrl(m.group()));
            int n = 1;
            for (String u : urls) {
                Episode e = new Episode(); e.sourceId = "animeua"; e.playerUrl = u; e.referer = item.webUrl;
                e.title = "Видео вариант " + n++; e.meta = Http.host(u);
                out.episodes.add(e);
                if (out.episodes.size() >= 20) break;
            }
            if (out.episodes.isEmpty()) {
                Episode e = new Episode(); e.sourceId = "animeua"; e.playerUrl = item.webUrl; e.referer = item.webUrl;
                e.title = "Проверить player markers"; e.meta = "в HTML прямой поток не найден; возможно регион/JS";
                out.episodes.add(e);
            }
            return out;
        }

        @Override public PlayLink resolve(Episode e) throws Exception {
            if (isPlayable(e.playerUrl)) return checked(e.playerUrl, e.referer);
            String resolved = Resolver.findPlayable(e.playerUrl, e.referer);
            if (empty(resolved)) throw new java.io.IOException("AnimeUA не показал прямой .m3u8/.mp4 в публичном HTML; возможно нужен UA region/player JS");
            return checked(resolved, e.referer);
        }

        private ArrayList<AnimeItem> collect(Document doc) {
            ArrayList<AnimeItem> out = new ArrayList<>(); Set<String> seen = new LinkedHashSet<>();
            for (Element a : doc.select("a[href$=.html]")) {
                String href = a.attr("abs:href"); if (!href.startsWith(BASE) || !seen.add(href)) continue;
                Element img = a.selectFirst("img");
                String title = img == null ? "" : first(img.attr("alt"), img.attr("title"));
                if (empty(title)) title = a.select("b, h2, h3").text();
                if (empty(title)) title = a.text();
                title = cut(title, 90); if (empty(title) || title.contains("Увійти")) continue;
                AnimeItem item = new AnimeItem(); item.sourceId = "animeua"; item.title = title; item.webUrl = href; item.slug = href; item.poster = img == null ? "" : first(img.attr("abs:data-src"), img.attr("abs:src"));
                item.description = a.text(); item.type = "HTML";
                out.add(item); if (out.size() >= 24) break;
            }
            return out;
        }
    }

    static PlayLink checked(String url, String referer) throws Exception {
        if (empty(url)) throw new java.io.IOException("Пустой video URL");
        String q = "stream";
        if (url.toLowerCase(Locale.ROOT).contains(".m3u8")) q = Http.hlsQualities(url, referer);
        return new PlayLink(url, referer, q);
    }

    static boolean isPlayable(String u) {
        if (empty(u)) return false;
        String l = u.toLowerCase(Locale.ROOT);
        return l.contains(".m3u8") || l.contains(".mp4") || l.contains(".mpd") || l.contains(".webm") || l.contains(".mkv");
    }

    static String meta(String original, String year, String type, String status, String duration, String genres) {
        ArrayList<String> p = new ArrayList<>();
        if (!empty(original)) p.add(original);
        if (!empty(year) && !year.equals("0") && !year.equals("?")) p.add(year);
        if (!empty(type)) p.add(type);
        if (!empty(status)) p.add(status);
        if (!empty(duration)) p.add(duration);
        if (!empty(genres)) p.add(genres);
        return String.join(" · ", p);
    }

    static String genres(JSONArray arr) {
        ArrayList<String> g = new ArrayList<>();
        for (int i = 0; arr != null && i < arr.length() && g.size() < 5; i++) {
            JSONObject o = arr.optJSONObject(i); if (o == null) continue;
            g.add(first(o.optString("nameUa"), o.optString("nameEn")));
        }
        return String.join(", ", g);
    }

    static String coaniGenres(JSONArray arr) {
        ArrayList<String> g = new ArrayList<>();
        for (int i = 0; arr != null && i < arr.length() && g.size() < 5; i++) {
            JSONObject o = arr.optJSONObject(i); if (o == null) continue; g.add(o.optString("name"));
        }
        return String.join(", ", g);
    }

    static String first(String a, String b) { return !empty(a) ? a : (!empty(b) ? b : ""); }
    static int parseInt(String s) { try { return Integer.parseInt(String.valueOf(s).replaceAll("[^0-9]", "")); } catch (Exception e) { return 0; } }
    static int intYear(String s) { Matcher m = Pattern.compile("(19|20)\\d{2}").matcher(s == null ? "" : s); return m.find() ? parseInt(m.group()) : 0; }
    static String cleanNumber(Object o) { String s = String.valueOf(o == null ? "" : o); return s.replaceAll("\\.0$", ""); }
    static String cleanUrl(String s) { return s == null ? "" : s.replace("\\/", "/").replace("&amp;", "&").trim(); }

    static final class Resolver {
        static String findPlayable(String url, String referer) throws Exception {
            if (empty(url)) return "";
            String page = Http.get(url, referer);
            String found = scan(page, url);
            if (!empty(found)) return found;
            Document doc = Jsoup.parse(page, url);
            int count = 0;
            for (Element s : doc.select("script[src]")) {
                if (++count > 8) break;
                try {
                    String js = Http.get(s.attr("abs:src"), url);
                    found = scan(js, s.attr("abs:src"));
                    if (!empty(found)) return found;
                } catch (Exception ignored) {}
            }
            return "";
        }
        private static String scan(String text, String base) {
            if (text == null) return "";
            String t = text.replace("\\/", "/").replace("\\u002F", "/").replace("&amp;", "&");
            String[] pats = {
                    "file\\s*[:=]\\s*['\\\"]([^'\\\"]+?(?:\\.m3u8|\\.mp4)[^'\\\"]*)",
                    "source\\s*[:=]\\s*['\\\"]([^'\\\"]+?(?:\\.m3u8|\\.mp4)[^'\\\"]*)",
                    "src\\s*[:=]\\s*['\\\"]([^'\\\"]+?(?:\\.m3u8|\\.mp4)[^'\\\"]*)",
                    "https?://[^\\\"'<>\\s]+?(?:\\.m3u8|\\.mp4)[^\\\"'<>\\s]*"
            };
            for (String p : pats) {
                Matcher m = Pattern.compile(p, Pattern.CASE_INSENSITIVE).matcher(t);
                if (m.find()) return absolute(base, cleanUrl(m.groupCount() >= 1 ? m.group(1) : m.group()));
            }
            return "";
        }
        private static String absolute(String base, String raw) {
            if (empty(raw)) return "";
            try { return URI.create(base).resolve(raw.startsWith("//") ? "https:" + raw : raw).toString(); }
            catch (Exception e) { return raw; }
        }
    }

    static final class Http {
        private static final String UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Mobile Safari/537.36";
        static JSONObject json(String url, String referer) throws Exception { return new JSONObject(get(url, referer)); }
        static String get(String url, String referer) throws Exception { return request("GET", url, null, null, referer, "text/html,application/json,*/*"); }
        static String postForm(String url, String body, String referer) throws Exception { return request("POST", url, body, "application/x-www-form-urlencoded; charset=utf-8", referer, "text/html,application/json,*/*"); }
        private static String request(String method, String url, String body, String contentType, String referer, String accept) throws Exception {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(10000); c.setReadTimeout(15000); c.setInstanceFollowRedirects(true);
            c.setRequestMethod(method); c.setRequestProperty("User-Agent", UA); c.setRequestProperty("Accept", accept); c.setRequestProperty("Accept-Language", "ru,en-US;q=0.8,en;q=0.6,uk;q=0.5");
            if (!empty(referer)) c.setRequestProperty("Referer", referer);
            if (body != null) {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                c.setDoOutput(true); c.setRequestProperty("Content-Type", contentType); c.setRequestProperty("Content-Length", String.valueOf(bytes.length));
                try (OutputStream os = c.getOutputStream()) { os.write(bytes); }
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String text = read(in); c.disconnect();
            if (code >= 400) throw new java.io.IOException("HTTP " + code + " " + url + "\n" + cut(text, 300));
            return text;
        }
        static String read(InputStream in) throws Exception {
            if (in == null) return "";
            ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
        static String enc(String s) throws Exception { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); }
        static String host(String u) { try { String h = URI.create(u).getHost(); return h == null ? "" : h; } catch (Exception e) { return ""; } }
        static String hlsQualities(String url, String referer) {
            try {
                String m3u = get(url, referer);
                Matcher m = Pattern.compile("RESOLUTION=\\d+x(\\d+)").matcher(m3u);
                LinkedHashSet<String> q = new LinkedHashSet<>();
                while (m.find()) q.add(m.group(1) + "p");
                return q.isEmpty() ? "HLS" : "HLS " + q;
            } catch (Throwable e) { return "HLS, quality probe failed"; }
        }
    }

    static final class Images {
        private static final ExecutorService pool = Executors.newFixedThreadPool(2);
        static void load(String url, ImageView into) {
            if (empty(url)) return;
            pool.execute(() -> {
                Bitmap bmp = null;
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(5000); c.setReadTimeout(8000); c.setRequestProperty("User-Agent", Http.UA);
                    try (InputStream in = c.getInputStream()) {
                        BitmapFactory.Options o = new BitmapFactory.Options(); o.inSampleSize = 2;
                        bmp = BitmapFactory.decodeStream(in, null, o);
                    }
                    c.disconnect();
                } catch (Throwable ignored) {}
                final Bitmap out = bmp;
                into.post(() -> { if (out != null) into.setImageBitmap(out); });
            });
        }
    }
}
