package app.yoru.sourcelab;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
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
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ArrayList<SourceProbe> probes = new ArrayList<>();
    private int selected = 0;
    private TextView title;
    private TextView meta;
    private TextView output;
    private EditText query;
    private LinearLayout chips;

    private static final int BG = Color.rgb(7, 10, 18);
    private static final int CARD = Color.rgb(18, 23, 36);
    private static final int CARD2 = Color.rgb(28, 34, 52);
    private static final int TEXT = Color.rgb(238, 242, 255);
    private static final int MUTED = Color.rgb(155, 166, 190);
    private static final int ACCENT = Color.rgb(124, 92, 255);
    private static final int GOOD = Color.rgb(61, 220, 151);
    private static final int BAD = Color.rgb(255, 92, 115);

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        probes.add(new AnimeOnProbe());
        probes.add(new CoaniProbe());
        probes.add(new AnimeUaProbe());
        probes.add(new AllAnimeProbe());
        probes.add(new AnichiProbe());
        buildUi();
        select(0);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    private void buildUi() {
        ScrollView rootScroll = new ScrollView(this);
        rootScroll.setFillViewport(true);
        rootScroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(28));
        rootScroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView header = text("YORU Source Lab", 26, TEXT, true);
        root.addView(header);
        TextView sub = text("Отдельное тестовое приложение. Внутри только новые источники; YORU 4.13.0 и его текущие источники не используются и не изменяются.", 13, MUTED, false);
        sub.setPadding(0, dp(6), 0, dp(12));
        root.addView(sub);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        hs.addView(chips, new HorizontalScrollView.LayoutParams(-2, -2));
        root.addView(hs);

        for (int i = 0; i < probes.size(); i++) {
            final int index = i;
            Button b = chip(probes.get(i).shortName());
            b.setOnClickListener(v -> select(index));
            chips.addView(b);
        }

        LinearLayout card = panel();
        title = text("", 20, TEXT, true);
        meta = text("", 13, MUTED, false);
        query = new EditText(this);
        query.setSingleLine(true);
        query.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        query.setTextColor(TEXT);
        query.setHintTextColor(MUTED);
        query.setTextSize(15);
        query.setPadding(dp(12), dp(8), dp(12), dp(8));
        query.setBackgroundColor(CARD2);
        card.addView(title);
        card.addView(meta);
        card.addView(query, new LinearLayout.LayoutParams(-1, dp(48)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button one = action("Проверить");
        Button all = action("Все источники");
        Button site = action("Сайт");
        one.setOnClickListener(v -> runSelected());
        all.setOnClickListener(v -> runAll());
        site.setOnClickListener(v -> openSite());
        actions.addView(one, weight());
        actions.addView(all, weight());
        actions.addView(site, weight());
        card.addView(actions);
        root.addView(card);

        output = text("", 14, TEXT, false);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextIsSelectable(true);
        output.setPadding(dp(12), dp(12), dp(12), dp(12));
        output.setBackgroundColor(CARD);
        root.addView(output, new LinearLayout.LayoutParams(-1, -2));
        setContentView(rootScroll);
    }

    private void select(int i) {
        selected = i;
        SourceProbe p = probes.get(i);
        title.setText(p.name());
        meta.setText(p.baseUrl() + "\n" + p.note());
        query.setHint("Поиск: " + p.defaultQuery());
        query.setText(p.defaultQuery());
        for (int n = 0; n < chips.getChildCount(); n++) {
            Button b = (Button) chips.getChildAt(n);
            b.setTextColor(n == i ? Color.WHITE : MUTED);
            b.setBackgroundColor(n == i ? ACCENT : CARD2);
        }
        output.setText("Выбран: " + p.name() + "\nНажми “Проверить”, чтобы выполнить live-probe: каталог → поиск → карточка/серии → видео-маркеры/качества.\n\nНикакие текущие источники YORU здесь не подключены.");
    }

    private void runSelected() {
        SourceProbe p = probes.get(selected);
        String q = query.getText() == null ? p.defaultQuery() : query.getText().toString().trim();
        if (q.isEmpty()) q = p.defaultQuery();
        output.setText("Проверяю " + p.name() + "…\n");
        io.execute(() -> {
            String result;
            try {
                result = p.run(q);
            } catch (Throwable e) {
                result = "✗ Ошибка проверки " + p.name() + "\n" + e.getClass().getSimpleName() + ": " + e.getMessage();
            }
            final String out = result;
            runOnUiThread(() -> output.setText(out));
        });
    }

    private void runAll() {
        output.setText("Проверяю все новые источники…\n");
        String q = query.getText() == null ? "naruto" : query.getText().toString().trim();
        if (q.isEmpty()) q = "naruto";
        final String queryText = q;
        io.execute(() -> {
            StringBuilder all = new StringBuilder();
            for (SourceProbe p : probes) {
                try {
                    all.append(p.run(queryText));
                } catch (Throwable e) {
                    all.append("\n━━━━━━━━━━━━━━━━━━━━\n").append(p.name()).append("\n✗ ").append(e.getClass().getSimpleName()).append(": ").append(e.getMessage()).append('\n');
                }
                all.append("\n\n");
            }
            runOnUiThread(() -> output.setText(all.toString()));
        });
    }

    private void openSite() {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(probes.get(selected).baseUrl()))); }
        catch (Throwable ignored) {}
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(dp(2), 1.0f);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private LinearLayout panel() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(14), dp(14), dp(14), dp(14));
        l.setBackgroundColor(CARD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(14), 0, dp(14));
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
        lp.setMargins(0, 0, dp(8), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private Button action(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(ACCENT);
        return b;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        lp.setMargins(dp(3), dp(10), dp(3), 0);
        return lp;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private static abstract class SourceProbe {
        abstract String name();
        abstract String shortName();
        abstract String baseUrl();
        abstract String defaultQuery();
        abstract String note();
        abstract String run(String query) throws Exception;

        String header() {
            return "\n━━━━━━━━━━━━━━━━━━━━\n" + name() + "\n" + baseUrl() + "\n" + note() + "\n";
        }
        void ok(StringBuilder sb, String what) { sb.append("✓ ").append(what).append('\n'); }
        void warn(StringBuilder sb, String what) { sb.append("! ").append(what).append('\n'); }
        void fail(StringBuilder sb, String what) { sb.append("✗ ").append(what).append('\n'); }
        String cut(String s, int max) { if (s == null) return ""; s = Jsoup.parse(s).text().replaceAll("\\s+", " ").trim(); return s.length() <= max ? s : s.substring(0, max - 1) + "…"; }
    }

    private static final class AnimeOnProbe extends SourceProbe {
        private static final String BASE = "https://animeon.club";
        @Override String name() { return "AnimeON / animeon.club"; }
        @Override String shortName() { return "AnimeON"; }
        @Override String baseUrl() { return BASE; }
        @Override String defaultQuery() { return "naruto"; }
        @Override String note() { return "Новый источник. Проверка публичных API: catalog, search, details, translations, episodes, player videoUrl."; }
        @Override String run(String query) throws Exception {
            StringBuilder sb = new StringBuilder(header());
            JSONObject catalog = Http.json(BASE + "/api/anime?pageSize=5&pageIndex=1");
            JSONArray results = catalog.optJSONArray("results");
            if (results != null && results.length() > 0) ok(sb, "catalog API живой: " + results.length() + " items, total=" + catalog.optInt("totalCount", -1));
            else fail(sb, "catalog API не вернул results");
            printAnimeOnRows(sb, results);

            JSONObject search = Http.json(BASE + "/api/anime?search=" + Http.enc(query) + "&pageSize=5&pageIndex=1");
            JSONArray sr = search.optJSONArray("results");
            if (sr != null && sr.length() > 0) ok(sb, "search “" + query + "”: " + sr.length() + " items");
            else warn(sb, "search “" + query + "” пустой, беру item из catalog");
            JSONObject picked = pickPlayable(sr != null && sr.length() > 0 ? sr : results);
            if (picked == null) return sb.append("✗ Нечего проверять дальше\n").toString();
            int id = picked.optInt("id");
            String slug = picked.optString("slug", String.valueOf(id));
            sb.append("\nВыбран: #").append(id).append(" ").append(picked.optString("titleUa", picked.optString("titleEn", "anime"))).append('\n');

            JSONObject details = Http.json(BASE + "/api/anime/" + Http.enc(slug));
            if (details.optBoolean("moved")) details = Http.json(BASE + "/api/anime/" + Http.enc(details.optString("redirectTo", slug)));
            ok(sb, "details: " + details.optString("titleUa") + " / " + details.optString("titleEn") + ", episodesAired=" + details.optInt("episodesAired", -1));
            JSONArray players = details.optJSONArray("players");
            if (players != null) sb.append("players: ").append(players).append('\n');

            JSONObject trans = Http.json(BASE + "/api/player/" + id + "/translations");
            JSONArray translations = trans.optJSONArray("translations");
            if (translations == null || translations.length() == 0) {
                warn(sb, "translations API пустой — каталог/карточка работают, видео пока не подтверждено");
                return sb.toString();
            }
            ok(sb, "translations: " + translations.length());
            JSONObject first = translations.optJSONObject(0);
            JSONObject tr = first == null ? null : first.optJSONObject("translation");
            JSONArray pl = first == null ? null : first.optJSONArray("player");
            int trId = tr == null ? 0 : tr.optInt("id");
            String trName = tr == null ? "?" : tr.optString("name", "?");
            JSONObject player = pl == null || pl.length() == 0 ? null : pl.optJSONObject(0);
            int playerId = player == null ? 0 : player.optInt("id");
            sb.append("translation[0]: ").append(trName).append(" #").append(trId).append(", player=").append(player == null ? "?" : player.optString("name") + " #" + playerId).append('\n');
            if (playerId <= 0 || trId <= 0) return sb.append("! player/translation ids нет — stop\n").toString();

            JSONObject eps = Http.json(BASE + "/api/player/" + id + "/episodes?take=5&playerId=" + playerId + "&translationId=" + trId + "&skip=0&includeAlternative=true");
            JSONArray list = eps.optJSONArray("episodes");
            if (list != null && list.length() > 0) ok(sb, "episodes API: " + list.length() + " из total=" + eps.optJSONObject("pagination").optInt("total", -1));
            else { warn(sb, "episodes API пустой, пробую direct player endpoint"); }
            int episodeId = list != null && list.length() > 0 ? list.optJSONObject(0).optInt("id") : 0;
            if (episodeId > 0) {
                JSONObject video = Http.json(BASE + "/api/player/" + episodeId + "/episode");
                String videoUrl = video.optString("videoUrl", "");
                if (!videoUrl.isEmpty()) ok(sb, "episode videoUrl найден: " + Http.host(videoUrl));
                sb.append("videoUrl: ").append(videoUrl).append('\n');
                if (videoUrl.contains(".m3u8")) sb.append(Http.hlsReport(videoUrl, BASE));
                else warn(sb, "прямой HLS не найден на этом шаге; это внешний player URL, нужен отдельный extractor, если источник пойдёт в YORU");
            } else {
                JSONObject video = Http.json(BASE + "/api/player/" + playerId + "/" + trId);
                String videoUrl = video.optString("videoUrl", "");
                if (!videoUrl.isEmpty()) ok(sb, "direct player videoUrl найден: " + Http.host(videoUrl));
                sb.append("videoUrl: ").append(videoUrl).append('\n');
            }
            sb.append("\nВердикт: AnimeON реально отвечает по catalog/search/details/translations/episodes. Для YORU-кандидата нужно отдельно решить extractor внешнего player host.\n");
            return sb.toString();
        }
        private static JSONObject pickPlayable(JSONArray arr) {
            for (int i = 0; arr != null && i < arr.length(); i++) { JSONObject o = arr.optJSONObject(i); if (o != null && o.optInt("episodesAired", o.optInt("episodes", 0)) > 0) return o; }
            return arr != null && arr.length() > 0 ? arr.optJSONObject(0) : null;
        }
        private static void printAnimeOnRows(StringBuilder sb, JSONArray arr) {
            for (int i = 0; arr != null && i < Math.min(3, arr.length()); i++) {
                JSONObject o = arr.optJSONObject(i); if (o == null) continue;
                sb.append(" • #").append(o.optInt("id")).append(' ').append(o.optString("titleUa", o.optString("titleEn"))).append(" ep=").append(o.optInt("episodesAired", o.optInt("episodes", 0))).append(" status=").append(o.optString("status")).append('\n');
            }
        }
    }

    private static final class CoaniProbe extends SourceProbe {
        private static final String BASE = "https://coani.net";
        @Override String name() { return "Coani / coani.net"; }
        @Override String shortName() { return "Coani"; }
        @Override String baseUrl() { return BASE; }
        @Override String defaultQuery() { return ""; }
        @Override String note() { return "Новый источник. Проверка public catalog API, season API, series API и прямых HLS master.m3u8."; }
        @Override String run(String query) throws Exception {
            StringBuilder sb = new StringBuilder(header());
            String q = query == null ? "" : query.trim();
            JSONObject catalog = Http.json(BASE + "/api/public/film/catalog?search=" + Http.enc(q) + "&page=1&limit=5");
            JSONArray data = catalog.optJSONArray("data");
            if ((data == null || data.length() == 0) && !q.isEmpty()) {
                warn(sb, "search “" + q + "” пустой, пробую общий catalog");
                catalog = Http.json(BASE + "/api/public/film/catalog?search=&page=1&limit=5");
                data = catalog.optJSONArray("data");
            }
            if (data != null && data.length() > 0) ok(sb, "catalog API живой: " + data.length() + " items"); else return sb.append("✗ catalog пустой\n").toString();
            for (int i = 0; i < Math.min(3, data.length()); i++) {
                JSONObject row = data.optJSONObject(i); JSONObject d = row == null ? null : row.optJSONObject("data"); if (d == null) continue;
                sb.append(" • #").append(d.optInt("id")).append(' ').append(d.optString("name")).append(" seasonSlug=").append(d.optString("seo_slug")).append('\n');
            }
            JSONObject first = data.optJSONObject(0).optJSONObject("data");
            int id = first.optInt("id");
            String slug = first.optString("seo_slug");
            JSONObject season = Http.json(BASE + "/api/public/film/season?slug=" + Http.enc(slug));
            JSONObject seasonData = season.optJSONObject("data");
            if (seasonData != null) ok(sb, "season API: " + seasonData.optString("name") + ", id=" + seasonData.optInt("id", id));
            int seasonId = seasonData == null ? id : seasonData.optInt("id", id);
            JSONObject series = Http.json(BASE + "/api/public/film/season/" + seasonId + "/series");
            JSONArray list = series.optJSONArray("data");
            if (list == null || list.length() == 0) return sb.append("! series API пустой — карточка есть, видео не подтверждено\n").toString();
            ok(sb, "series API: " + list.length() + " rows");
            LinkedHashSet<String> voices = new LinkedHashSet<>();
            String firstVideo = "";
            for (int i = 0; i < Math.min(6, list.length()); i++) {
                JSONObject row = list.optJSONObject(i); JSONObject d = row == null ? null : row.optJSONObject("data"); if (d == null) continue;
                voices.add(d.optString("voice_type", "?"));
                if (firstVideo.isEmpty()) firstVideo = d.optString("video", "");
                sb.append(" • ep ").append(d.optInt("number")).append(" voice=").append(d.optString("voice_type")).append(" videoHost=").append(Http.host(d.optString("video", ""))).append('\n');
            }
            sb.append("voices: ").append(voices).append('\n');
            if (!firstVideo.isEmpty()) {
                ok(sb, "прямой video URL найден: " + firstVideo);
                sb.append(Http.hlsReport(firstVideo, BASE));
            }
            sb.append("\nВердикт: Coani выглядит самым прямым кандидатом: public catalog + public series + прямой HLS без отдельного iframe-extractor.\n");
            return sb.toString();
        }
    }

    private static final class AnimeUaProbe extends SourceProbe {
        private static final String BASE = "https://animeua.club";
        @Override String name() { return "AnimeUA / animeua.club"; }
        @Override String shortName() { return "AnimeUA"; }
        @Override String baseUrl() { return BASE; }
        @Override String defaultQuery() { return "naruto"; }
        @Override String note() { return "Новый источник. Проверка HTML catalog/search/detail/player markers. Сайт сам предупреждает о региональной доступности плеера."; }
        @Override String run(String query) throws Exception {
            StringBuilder sb = new StringBuilder(header());
            Document home = Jsoup.parse(Http.get(BASE + "/"), BASE + "/");
            ArrayList<Item> homeItems = collectItems(home, BASE);
            if (!homeItems.isEmpty()) ok(sb, "home/catalog HTML живой: " + homeItems.size() + " ссылок .html"); else warn(sb, "home не дал карточек .html");
            printItems(sb, homeItems);

            String body = "do=search&subaction=search&story=" + Http.enc(query.replace(' ', '+'));
            Document search = Jsoup.parse(Http.postForm(BASE + "/index.php", body, BASE), BASE + "/");
            ArrayList<Item> found = collectItems(search, BASE);
            if (!found.isEmpty()) ok(sb, "search POST “" + query + "”: " + found.size() + " ссылок"); else warn(sb, "search пустой, беру home item");
            printItems(sb, found);
            Item picked = !found.isEmpty() ? found.get(0) : (!homeItems.isEmpty() ? homeItems.get(0) : null);
            if (picked == null) return sb.append("✗ нет item для detail\n").toString();
            sb.append("\nВыбран: ").append(picked.title).append("\n").append(picked.url).append('\n');

            String html = Http.get(picked.url);
            Document doc = Jsoup.parse(html, picked.url);
            String text = doc.text();
            if (text.contains("Плеєр доступний лише")) warn(sb, "сайт предупреждает: плеер доступен только Украина/VPN UA");
            Matcher ep = Pattern.compile("Епізодів:\\s*(\\d+)").matcher(text);
            if (ep.find()) ok(sb, "details episodes=" + ep.group(1)); else ok(sb, "details открыт, title=" + doc.title());
            Elements frames = doc.select("iframe[src], iframe[data-src], video[src], source[src], .video-responsive iframe");
            LinkedHashSet<String> urls = new LinkedHashSet<>();
            for (Element f : frames) {
                String u = f.attr("abs:src"); if (u.isEmpty()) u = f.attr("abs:data-src"); if (!u.isEmpty()) urls.add(u);
            }
            Matcher m3u = Pattern.compile("https?://[^\\\"'<>\\s]+?\\.m3u8[^\\\"'<>\\s]*", Pattern.CASE_INSENSITIVE).matcher(html);
            while (m3u.find() && urls.size() < 8) urls.add(m3u.group());
            if (urls.isEmpty()) warn(sb, "player iframe/HLS в HTML не найден; возможно, нужен JS/региональная доступность");
            else {
                ok(sb, "player/video markers: " + urls.size());
                int n = 0;
                for (String u : urls) {
                    sb.append(" • ").append(Http.host(u)).append(" → ").append(u).append('\n');
                    if (++n >= 5) break;
                }
            }
            sb.append("\nВердикт: AnimeUA catalog/search/details живые, но video-часть вероятно региональная/iframe-JS. В YORU добавлять только после проверки прямого HLS на устройстве/UA-сети.\n");
            return sb.toString();
        }
        private static ArrayList<Item> collectItems(Document doc, String base) {
            ArrayList<Item> out = new ArrayList<>(); Set<String> seen = new LinkedHashSet<>();
            for (Element a : doc.select("a[href$=.html]")) {
                String href = a.attr("abs:href");
                if (!href.startsWith(base) || href.contains("/uploads/") || !seen.add(href)) continue;
                String title = a.select("img[alt]").attr("alt");
                if (title.isEmpty()) title = a.select("b, h2, h3").text();
                if (title.isEmpty()) title = a.text();
                title = Jsoup.parse(title).text().replaceAll("\\s+", " ").trim();
                if (title.length() > 80) title = title.substring(0, 79) + "…";
                if (!title.isEmpty()) out.add(new Item(title, href));
                if (out.size() >= 12) break;
            }
            return out;
        }
        private static void printItems(StringBuilder sb, ArrayList<Item> items) {
            for (int i = 0; i < Math.min(3, items.size()); i++) sb.append(" • ").append(items.get(i).title).append('\n');
        }
        private static final class Item { final String title, url; Item(String t, String u) { title = t; url = u; } }
    }

    private static final class AllAnimeProbe extends SourceProbe {
        private static final String API = "https://api.allanime.day/api";
        private static final String SITE = "https://allanime.to";
        private static final String SEARCH_QUERY = "query($search: SearchInput,$limit: Int,$page: Int,$translationType: VaildTranslationTypeEnumType,$countryOrigin: VaildCountryOriginEnumType){shows(search:$search,limit:$limit,page:$page,translationType:$translationType,countryOrigin:$countryOrigin){pageInfo{total} edges{_id name thumbnail englishName nativeName slugTime}}}";
        private static final String EPISODES_QUERY = "query($_id: String!){show(_id: $_id){_id availableEpisodesDetail}}";
        private static final String STREAMS_QUERY = "query($showId: String!,$translationType: VaildTranslationTypeEnumType!,$episodeString: String!){episode(showId:$showId,translationType:$translationType,episodeString:$episodeString){sourceUrls}}";
        @Override String name() { return "AllAnime / api.allanime.day"; }
        @Override String shortName() { return "AllAnime"; }
        @Override String baseUrl() { return SITE; }
        @Override String defaultQuery() { return "naruto"; }
        @Override String note() { return "Новый источник. Проверка public GraphQL POST: search → availableEpisodesDetail → sourceUrls. Host extractors не тащим."; }
        @Override String run(String query) throws Exception {
            StringBuilder sb = new StringBuilder(header());
            JSONObject payload = new JSONObject()
                    .put("query", SEARCH_QUERY)
                    .put("variables", new JSONObject()
                            .put("search", new JSONObject().put("query", query).put("allowAdult", false).put("allowUnknown", false))
                            .put("limit", 5).put("page", 1).put("translationType", "sub").put("countryOrigin", "ALL"));
            JSONObject search = Http.postJson(API, payload.toString(), SITE, API + "/");
            JSONArray edges = search.optJSONObject("data").optJSONObject("shows").optJSONArray("edges");
            if (edges != null && edges.length() > 0) ok(sb, "GraphQL search живой: " + edges.length() + " items"); else return sb.append("✗ search пустой/непонятный ответ\n").toString();
            for (int i = 0; i < Math.min(3, edges.length()); i++) {
                JSONObject o = edges.optJSONObject(i);
                sb.append(" • ").append(o.optString("_id")).append(' ').append(o.optString("name", o.optString("englishName"))).append('\n');
            }
            JSONObject picked = edges.optJSONObject(0);
            String id = picked.optString("_id");
            JSONObject epsReq = new JSONObject().put("query", EPISODES_QUERY).put("variables", new JSONObject().put("_id", id));
            JSONObject eps = Http.postJson(API, epsReq.toString(), SITE, API + "/");
            JSONObject show = eps.optJSONObject("data").optJSONObject("show");
            JSONObject detail = show == null ? null : show.optJSONObject("availableEpisodesDetail");
            JSONArray sub = detail == null ? null : detail.optJSONArray("sub");
            JSONArray dub = detail == null ? null : detail.optJSONArray("dub");
            String type = sub != null && sub.length() > 0 ? "sub" : "dub";
            JSONArray arr = type.equals("sub") ? sub : dub;
            if (arr == null || arr.length() == 0) return sb.append("! availableEpisodesDetail пустой — search работает, видео не подтверждено\n").toString();
            String ep = String.valueOf(arr.opt(0));
            ok(sb, "episodes detail: " + type + " count≈" + arr.length() + ", first=" + ep);
            JSONObject streamsReq = new JSONObject().put("query", STREAMS_QUERY).put("variables", new JSONObject().put("showId", id).put("translationType", type).put("episodeString", ep));
            JSONObject streams = Http.postJson(API, streamsReq.toString(), SITE, API + "/");
            JSONArray sources = streams.optJSONObject("data").optJSONObject("episode").optJSONArray("sourceUrls");
            if (sources == null || sources.length() == 0) return sb.append("! sourceUrls пустой — episodes есть, источники не подтверждены\n").toString();
            ok(sb, "sourceUrls: " + sources.length());
            for (int i = 0; i < Math.min(6, sources.length()); i++) {
                JSONObject src = sources.optJSONObject(i); if (src == null) continue;
                String raw = src.optString("sourceUrl", "");
                String dec = decryptSource(raw);
                sb.append(" • ").append(src.optString("sourceName", "source")).append(" type=").append(src.optString("type", "?")).append(" → ").append(shortUrl(dec)).append('\n');
            }
            sb.append("\nВердикт: AllAnime API/search/episodes/sourceUrls рабочие. Для полноценного просмотра нужны отдельные hoster extractors; в Source Lab они намеренно не копируются.\n");
            return sb.toString();
        }
        private static String decryptSource(String s) {
            if (s == null || !s.startsWith("-")) return s == null ? "" : s;
            String hex = s.substring(s.lastIndexOf('-') + 1);
            StringBuilder out = new StringBuilder();
            for (int i = 0; i + 1 < hex.length(); i += 2) {
                try { out.append((char) (Integer.parseInt(hex.substring(i, i + 2), 16) ^ 56)); }
                catch (Exception e) { return s; }
            }
            return out.toString();
        }
        private static String shortUrl(String s) { return s == null ? "" : (s.length() > 140 ? s.substring(0, 139) + "…" : s); }
    }

    private static final class AnichiProbe extends SourceProbe {
        @Override String name() { return "Anichi / Cloudstream Anichi"; }
        @Override String shortName() { return "Anichi"; }
        @Override String baseUrl() { return "https://github.com/hexated/cloudstream-extensions-hexated/tree/master/Anichi"; }
        @Override String defaultQuery() { return "naruto"; }
        @Override String note() { return "Категория есть, но live-видео не подтверждаем: extension хранит ANICHI_API/SERVER/ENDPOINT/APP вне репозитория в local.properties."; }
        @Override String run(String query) throws Exception {
            StringBuilder sb = new StringBuilder(header());
            warn(sb, "в open-source коде Anichi endpoints не раскрыты: BuildConfig.ANICHI_API / SERVER / ENDPOINT / APP берутся из local.properties");
            warn(sb, "не использую guessed/private endpoints и не добавляю обходы");
            JSONObject jikan = Http.json("https://api.jikan.moe/v4/anime?q=" + Http.enc(query) + "&limit=3");
            JSONArray data = jikan.optJSONArray("data");
            if (data != null && data.length() > 0) {
                ok(sb, "публичный Jikan meta-probe живой: " + data.length() + " items");
                for (int i = 0; i < Math.min(3, data.length()); i++) {
                    JSONObject a = data.optJSONObject(i); if (a == null) continue;
                    sb.append(" • MAL#").append(a.optInt("mal_id")).append(' ').append(a.optString("title")).append('\n');
                }
            } else warn(sb, "даже meta-probe пустой");
            sb.append("\nВердикт: Anichi пока НЕ считать реально рабочим источником для YORU: нет публичной video/API цепочки без внешней конфигурации.\n");
            return sb.toString();
        }
    }

    private static final class Http {
        private static final String UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Mobile Safari/537.36";
        static JSONObject json(String url) throws Exception { return new JSONObject(get(url)); }
        static String get(String url) throws Exception { return request("GET", url, null, null, null, "application/json,text/html,*/*"); }
        static String postForm(String url, String body, String referer) throws Exception { return request("POST", url, body, "application/x-www-form-urlencoded; charset=utf-8", referer, "text/html,application/json,*/*"); }
        static JSONObject postJson(String url, String body, String origin, String referer) throws Exception {
            return new JSONObject(request("POST", url, body, "application/json; charset=utf-8", referer, "application/json,*/*", origin));
        }
        private static String request(String method, String url, String body, String contentType, String referer, String accept) throws Exception { return request(method, url, body, contentType, referer, accept, null); }
        private static String request(String method, String url, String body, String contentType, String referer, String accept, String origin) throws Exception {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(9000); c.setReadTimeout(13000); c.setInstanceFollowRedirects(true);
            c.setRequestMethod(method); c.setRequestProperty("User-Agent", UA); c.setRequestProperty("Accept", accept); c.setRequestProperty("Accept-Language", "ru,en-US;q=0.8,en;q=0.6,uk;q=0.5");
            if (referer != null && !referer.isEmpty()) c.setRequestProperty("Referer", referer);
            if (origin != null && !origin.isEmpty()) c.setRequestProperty("Origin", origin);
            if (body != null) {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                c.setDoOutput(true); c.setRequestProperty("Content-Type", contentType); c.setRequestProperty("Content-Length", String.valueOf(bytes.length));
                try (OutputStream os = c.getOutputStream()) { os.write(bytes); }
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String text = read(in);
            c.disconnect();
            if (code >= 400) throw new java.io.IOException("HTTP " + code + " for " + url + "\n" + text);
            return text;
        }
        static String read(InputStream in) throws Exception {
            if (in == null) return "";
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
        static String enc(String s) throws Exception { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); }
        static String host(String u) { try { URI uri = URI.create(u); return uri.getHost() == null ? "" : uri.getHost(); } catch (Exception e) { return ""; } }
        static String hlsReport(String url, String referer) {
            StringBuilder sb = new StringBuilder();
            try {
                String manifest = request("GET", url, null, null, referer, "application/vnd.apple.mpegurl,application/x-mpegURL,*/*");
                if (!manifest.trim().startsWith("#EXTM3U")) return "! HLS URL ответил, но это не #EXTM3U\n";
                Matcher m = Pattern.compile("RESOLUTION=\\d+x(\\d+)").matcher(manifest);
                LinkedHashSet<String> q = new LinkedHashSet<>();
                while (m.find()) q.add(m.group(1) + "p");
                if (q.isEmpty()) sb.append("✓ HLS master/media доступен, quality variants в manifest не указаны\n");
                else sb.append("✓ HLS доступен, качества: ").append(q).append('\n');
            } catch (Throwable e) {
                sb.append("! HLS probe не прошёл: ").append(e.getClass().getSimpleName()).append(": ").append(e.getMessage()).append('\n');
            }
            return sb.toString();
        }
    }
}
