package app.yuro.guard;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Locale;

public class BrowserActivity extends Activity {
    private static final int BG = 0xff100d18;
    private static final int CARD = 0xff1d1728;
    private static final int CARD2 = 0xff271d36;
    private static final int TEXT = 0xfff3eaff;
    private static final int MUTED = 0xffbbaacc;
    private static final int PURPLE = 0xffb56cff;
    private static final int CYAN = 0xff55f3ff;

    private final ArrayList<Tab> tabs = new ArrayList<>();
    private int active = 0;
    private EditText address;
    private ProgressBar progress;
    private FrameLayout webHost;
    private LinearLayout tabBar;
    private SharedPreferences prefs;

    @Override @SuppressLint("SetJavaScriptEnabled") public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("yuro_browser", MODE_PRIVATE);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        LinearLayout root = col(); root.setBackgroundColor(BG); root.setPadding(dp(10), dp(9), dp(10), 0); setContentView(root);

        LinearLayout top = row(); top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(button("‹", () -> { WebView w = activeWeb(); if (w != null && w.canGoBack()) w.goBack(); else finish(); }), lp(40, 42));
        top.addView(button("›", () -> { WebView w = activeWeb(); if (w != null && w.canGoForward()) w.goForward(); }), mlp(40, 42, 6, 0, 0, 0));
        address = new EditText(this); address.setSingleLine(true); address.setTextColor(TEXT); address.setHintTextColor(MUTED); address.setTextSize(13); address.setHint("Адрес или Яндекс-поиск"); address.setPadding(dp(12), 0, dp(12), 0); address.setBackground(shape(CARD2, 16));
        address.setOnEditorActionListener((v, actionId, event) -> { if (event == null || event.getKeyCode() == KeyEvent.KEYCODE_ENTER) { loadInActive(address.getText().toString()); return true; } return false; });
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, dp(42), 1); ap.leftMargin = dp(7); ap.rightMargin = dp(7); top.addView(address, ap);
        top.addView(button("GO", () -> loadInActive(address.getText().toString())), lp(54, 42));
        top.addView(button("+", () -> addTab("https://arena.ai", true)), mlp(42, 42, 6, 0, 0, 0));
        root.addView(top, new LinearLayout.LayoutParams(-1, dp(48)));

        HorizontalScrollView tabsScroll = new HorizontalScrollView(this); tabsScroll.setHorizontalScrollBarEnabled(false); tabBar = row(); tabBar.setGravity(Gravity.CENTER_VERTICAL); tabsScroll.addView(tabBar); root.addView(tabsScroll, new LinearLayout.LayoutParams(-1, dp(42)));

        HorizontalScrollView rec = new HorizontalScrollView(this); rec.setHorizontalScrollBarEnabled(false); LinearLayout chips = row();
        chips.addView(chip("arena.ai", false, () -> loadInActive("https://arena.ai")));
        chips.addView(chip("Яндекс", false, () -> loadInActive("https://ya.ru")));
        chips.addView(chip("Почта", false, () -> loadInActive("https://mail.yandex.ru")));
        chips.addView(chip("Кэш + картинки OFF", true, () -> toast("Максимальная экономия активна")));
        rec.addView(chips); root.addView(rec, new LinearLayout.LayoutParams(-1, dp(42)));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress.setMax(100); progress.setProgressTintList(android.content.res.ColorStateList.valueOf(PURPLE)); progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x33291f38));
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));
        webHost = new FrameLayout(this); webHost.setBackground(shape(0xff0c0912, 18)); LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-1, 0, 1); wp.topMargin = dp(8); root.addView(webHost, wp);

        GuardPrefs.browserSaver(this, true); GuardPrefs.browserBlockImages(this, true);
        loadTabs();
        String start = getIntent() == null ? null : getIntent().getStringExtra("url");
        if (start != null && start.trim().length() > 0) addTab(start, true); else selectTab(Math.max(0, Math.min(active, tabs.size() - 1)));
    }

    private void loadTabs() {
        tabs.clear(); active = prefs.getInt("active", 0);
        String raw = prefs.getString("tabs", "");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i=0;i<arr.length();i++) {
                JSONObject o = arr.optJSONObject(i); if (o == null) continue;
                Tab t = new Tab(); t.url = o.optString("url", "https://arena.ai"); t.title = o.optString("title", host(t.url)); t.scrollY = o.optInt("scroll", 0); tabs.add(t);
            }
        } catch (Throwable ignored) {}
        if (tabs.isEmpty()) { Tab t = new Tab(); t.url = "https://arena.ai"; t.title = "Arena.ai"; tabs.add(t); active = 0; }
    }

    private void saveTabs() {
        saveActiveState();
        try {
            JSONArray arr = new JSONArray();
            for (Tab t : tabs) { JSONObject o = new JSONObject(); o.put("url", clean(t.url)); o.put("title", clean(t.title)); o.put("scroll", t.scrollY); arr.put(o); }
            prefs.edit().putString("tabs", arr.toString()).putInt("active", active).apply();
        } catch (Throwable ignored) {}
        try { CookieManager.getInstance().flush(); } catch (Throwable ignored) {}
    }

    private void addTab(String url, boolean select) {
        Tab t = new Tab(); t.url = normalize(url); t.title = host(t.url); tabs.add(t); if (select) selectTab(tabs.size()-1); else renderTabs(); saveTabs();
    }

    private void closeTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        Tab removed = tabs.remove(index);
        if (removed.view != null) { try { removed.view.stopLoading(); removed.view.destroy(); } catch (Throwable ignored) {} }
        if (tabs.isEmpty()) { Tab t = new Tab(); t.url = "https://arena.ai"; t.title = "Arena.ai"; tabs.add(t); active = 0; }
        if (active >= tabs.size()) active = tabs.size() - 1;
        selectTab(active); saveTabs();
    }

    private void selectTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        saveActiveState(); active = index; webHost.removeAllViews();
        Tab t = tabs.get(active); if (t.view == null) t.view = createWeb(t);
        try { if (t.view.getParent() instanceof FrameLayout) ((FrameLayout)t.view.getParent()).removeView(t.view); } catch (Throwable ignored) {}
        webHost.addView(t.view, new FrameLayout.LayoutParams(-1, -1));
        address.setText(t.url); renderTabs(); t.view.onResume(); saveTabs();
    }

    @SuppressLint("SetJavaScriptEnabled") private WebView createWeb(final Tab tab) {
        WebView web = new WebView(this); web.setBackgroundColor(0xff0c0912); web.setVerticalScrollBarEnabled(false); web.setHorizontalScrollBarEnabled(false);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true); s.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK); s.setLoadsImagesAutomatically(!GuardPrefs.strictTextMode(this)); s.setBlockNetworkImage(GuardPrefs.strictTextMode(this));
        s.setMediaPlaybackRequiresUserGesture(true); s.setSupportZoom(true); s.setBuiltInZoomControls(true); s.setDisplayZoomControls(false); s.setJavaScriptCanOpenWindowsAutomatically(false);
        if (Build.VERSION.SDK_INT >= 21) s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (Build.VERSION.SDK_INT >= 26) web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true);
        if (Build.VERSION.SDK_INT >= 29) s.setForceDark(WebSettings.FORCE_DARK_ON);
        if (Build.VERSION.SDK_INT >= 33) s.setAlgorithmicDarkeningAllowed(true);
        s.setUserAgentString(s.getUserAgentString() + " YUROGuard/1.1 EconomyChromium");
        CookieManager cm = CookieManager.getInstance(); cm.setAcceptCookie(true); if (Build.VERSION.SDK_INT >= 21) cm.setAcceptThirdPartyCookies(web, false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { Uri u = request == null ? null : request.getUrl(); if (u == null) return false; String scheme = u.getScheme(); return !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)); }
            @Override public void onPageFinished(WebView view, String url) { tab.url = url == null ? tab.url : url; tab.title = view.getTitle() == null ? host(tab.url) : view.getTitle(); address.setText(tab.url); if (tab.scrollY > 0) view.postDelayed(() -> view.scrollTo(0, tab.scrollY), 250); renderTabs(); saveTabs(); }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) { if (request == null || request.getUrl() == null) return null; String u = request.getUrl().toString().toLowerCase(Locale.ROOT); String accept = request.getRequestHeaders() == null ? "" : String.valueOf(request.getRequestHeaders().get("Accept")).toLowerCase(Locale.ROOT); if (blocked(u, accept)) return empty(); return null; }
        });
        web.setWebChromeClient(new WebChromeClient() { @Override public void onProgressChanged(WebView view, int newProgress) { progress.setProgress(newProgress); progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE); } });
        web.loadUrl(tab.url); return web;
    }

    private void renderTabs() {
        if (tabBar == null) return; tabBar.removeAllViews();
        for (int i=0;i<tabs.size();i++) {
            final int idx = i; Tab t = tabs.get(i); LinearLayout chip = row(); chip.setGravity(Gravity.CENTER_VERTICAL); chip.setPadding(dp(11), 0, dp(7), 0); chip.setBackground(shape(idx == active ? PURPLE : CARD, 16));
            TextView title = text((idx+1) + " " + shortTitle(t.title, t.url), 12, true, idx == active ? Color.WHITE : TEXT); title.setSingleLine(true); chip.addView(title, new LinearLayout.LayoutParams(-2, dp(34)));
            TextView close = text("  ×", 14, true, idx == active ? Color.WHITE : MUTED); close.setGravity(Gravity.CENTER); chip.addView(close, new LinearLayout.LayoutParams(dp(28), dp(34)));
            chip.setOnClickListener(v -> selectTab(idx)); close.setOnClickListener(v -> closeTab(idx)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(36)); lp.rightMargin = dp(7); tabBar.addView(chip, lp);
        }
    }

    private void loadInActive(String raw) { Tab t = active >=0 && active < tabs.size() ? tabs.get(active) : null; if (t == null) { addTab(raw, true); return; } t.url = normalize(raw); t.title = host(t.url); t.scrollY = 0; if (t.view != null) t.view.loadUrl(t.url); address.setText(t.url); renderTabs(); saveTabs(); }
    private void saveActiveState() { try { Tab t = active >=0 && active < tabs.size() ? tabs.get(active) : null; WebView w = activeWeb(); if (t != null && w != null) { t.url = w.getUrl() == null ? t.url : w.getUrl(); t.title = w.getTitle() == null ? t.title : w.getTitle(); t.scrollY = w.getScrollY(); } } catch (Throwable ignored) {} }
    private WebView activeWeb() { return active >=0 && active < tabs.size() ? tabs.get(active).view : null; }

    private String normalize(String raw) { try { String u = raw == null ? "" : raw.trim(); if (u.isEmpty()) u = "https://arena.ai"; if (!u.startsWith("http://") && !u.startsWith("https://")) { if (!u.contains(".")) u = "https://yandex.ru/search/?text=" + URLEncoder.encode(u, "UTF-8"); else u = "https://" + u; } return u; } catch (Exception e) { return "https://arena.ai"; } }
    private String host(String u) { try { Uri uri = Uri.parse(u); String h = uri.getHost(); return h == null ? u : h.replace("www.", ""); } catch (Throwable e) { return u; } }
    private String shortTitle(String title, String url) { String s = title == null || title.trim().isEmpty() ? host(url) : title.trim(); return s.length() > 22 ? s.substring(0, 22) + "…" : s; }
    private String clean(String s) { return s == null ? "" : s; }

    private boolean blocked(String u, String accept) { if (!GuardPrefs.strictTextMode(this)) return isTracker(u); return isImage(u) || isMedia(u) || isFont(u) || isTracker(u) || accept.startsWith("image/") || accept.startsWith("video/") || accept.startsWith("audio/") || accept.contains("image/") || accept.contains("video/") || accept.contains("audio/"); }
    private boolean isImage(String u) { return u.endsWith(".gif") || u.endsWith(".avif") || u.endsWith(".webp") || u.endsWith(".jpg") || u.endsWith(".jpeg") || u.endsWith(".png") || u.endsWith(".svg") || u.contains("/image/") || u.contains("/img/"); }
    private boolean isMedia(String u) { return u.endsWith(".mp4") || u.endsWith(".webm") || u.endsWith(".m4v") || u.endsWith(".mov") || u.endsWith(".mp3") || u.endsWith(".m3u8") || u.endsWith(".ts"); }
    private boolean isFont(String u) { return u.endsWith(".woff") || u.endsWith(".woff2") || u.endsWith(".ttf") || u.endsWith(".otf"); }
    private boolean isTracker(String u) { return u.contains("doubleclick.net") || u.contains("googletagmanager") || u.contains("google-analytics") || u.contains("mc.yandex") || u.contains("metrika") || u.contains("facebook.net/tr") || u.contains("/ads?") || u.contains("adservice") || u.contains("adfox") || u.contains("counter.yadro"); }
    private WebResourceResponse empty() { return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0])); }

    @Override public void onBackPressed() { WebView w = activeWeb(); if (w != null && w.canGoBack()) w.goBack(); else if (active > 0) selectTab(active - 1); else super.onBackPressed(); }
    @Override protected void onPause() { saveTabs(); WebView w = activeWeb(); if (w != null) w.onPause(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); WebView w = activeWeb(); if (w != null) w.onResume(); }
    @Override protected void onDestroy() { saveTabs(); super.onDestroy(); }

    private static class Tab { String url, title; int scrollY; WebView view; }
    private LinearLayout col() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w < 0 ? w : dp(w), h < 0 ? h : dp(h)); }
    private LinearLayout.LayoutParams mlp(int w, int h, int l, int t, int r, int b) { LinearLayout.LayoutParams p = lp(w,h); p.setMargins(dp(l),dp(t),dp(r),dp(b)); return p; }
    private GradientDrawable shape(int color, float r) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(r)); return g; }
    private TextView button(String text, Runnable r) { TextView v = text(text, 13, true, Color.WHITE); v.setGravity(Gravity.CENTER); v.setBackground(shape(PURPLE, 16)); v.setOnClickListener(x -> r.run()); return v; }
    private TextView chip(String text, boolean active, Runnable r) { TextView v = text(text, 12, true, active ? Color.WHITE : TEXT); v.setGravity(Gravity.CENTER); v.setPadding(dp(13), 0, dp(13), 0); GradientDrawable gd = shape(active ? PURPLE : CARD, 15); gd.setStroke(dp(1), 0x44b56cff); v.setBackground(gd); LinearLayout.LayoutParams p = lp(-2, 34); p.rightMargin = dp(8); v.setLayoutParams(p); v.setOnClickListener(x -> r.run()); return v; }
    private TextView text(String s, int sp, boolean bold, int color) { TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(color); if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return v; }
    private void toast(String s) { android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show(); }
}
