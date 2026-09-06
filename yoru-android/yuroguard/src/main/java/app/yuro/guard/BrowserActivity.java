package app.yuro.guard;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.util.Locale;

public class BrowserActivity extends Activity {
    private static final int BG = 0xff100d18;
    private static final int CARD = 0xff1d1728;
    private static final int CARD2 = 0xff271d36;
    private static final int TEXT = 0xfff3eaff;
    private static final int MUTED = 0xffbbaacc;
    private static final int PURPLE = 0xffb56cff;
    private WebView web;
    private EditText address;
    private ProgressBar progress;
    private CheckBox saver;

    @Override @SuppressLint("SetJavaScriptEnabled") public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        LinearLayout root = col();
        root.setBackgroundColor(BG);
        root.setPadding(dp(12), dp(10), dp(12), 0);
        setContentView(root);

        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(button("‹", () -> { if (web != null && web.canGoBack()) web.goBack(); else finish(); }), lp(42, 42));
        address = new EditText(this);
        address.setSingleLine(true);
        address.setTextColor(TEXT); address.setHintTextColor(MUTED); address.setTextSize(13);
        address.setHint("Адрес или поиск через Яндекс");
        address.setPadding(dp(12), 0, dp(12), 0);
        address.setBackground(shape(CARD2, 16));
        address.setOnEditorActionListener((v, actionId, event) -> {
            if (event == null || event.getKeyCode() == KeyEvent.KEYCODE_ENTER) { load(address.getText().toString()); return true; }
            return false;
        });
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, dp(42), 1); ap.leftMargin = dp(8); ap.rightMargin = dp(8);
        top.addView(address, ap);
        top.addView(button("GO", () -> load(address.getText().toString())), lp(58, 42));
        root.addView(top, new LinearLayout.LayoutParams(-1, dp(48)));

        LinearLayout opts = row(); opts.setGravity(Gravity.CENTER_VERTICAL); opts.setPadding(0, dp(4), 0, dp(6));
        saver = new CheckBox(this); saver.setText("Экономия"); saver.setTextColor(MUTED); saver.setTextSize(12); saver.setButtonTintList(android.content.res.ColorStateList.valueOf(PURPLE)); saver.setChecked(GuardPrefs.browserSaver(this));
        saver.setOnCheckedChangeListener((v, on) -> { GuardPrefs.browserSaver(this, on); if (web != null) web.getSettings().setLoadsImagesAutomatically(!on); });
        opts.addView(saver);
        TextView reload = chip("Обновить", () -> { if (web != null) web.reload(); }); opts.addView(reload);
        root.addView(opts, new LinearLayout.LayoutParams(-1, dp(44)));

        HorizontalScrollView rec = new HorizontalScrollView(this); rec.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = row();
        chips.addView(chip("arena.ai", () -> load("https://arena.ai")));
        chips.addView(chip("Яндекс", () -> load("https://ya.ru")));
        chips.addView(chip("Почта", () -> load("https://mail.yandex.ru")));
        chips.addView(chip("YouTube", () -> load("https://youtube.com")));
        rec.addView(chips);
        root.addView(rec, new LinearLayout.LayoutParams(-1, dp(42)));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100); progress.setProgressTintList(android.content.res.ColorStateList.valueOf(PURPLE)); progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x33291f38));
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));

        FrameLayout box = new FrameLayout(this); box.setBackground(shape(0xff0c0912, 18));
        web = new WebView(this);
        web.setBackgroundColor(0xff0c0912);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setLoadsImagesAutomatically(!GuardPrefs.browserSaver(this));
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setUserAgentString(s.getUserAgentString() + " YUROGuard/1.0 ChromiumMobile");
        if (Build.VERSION.SDK_INT >= 29) s.setForceDark(WebSettings.FORCE_DARK_ON);
        if (Build.VERSION.SDK_INT >= 33) s.setAlgorithmicDarkeningAllowed(true);
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= 21) cm.setAcceptThirdPartyCookies(web, true);
        web.setWebViewClient(new GuardWebClient());
        web.setWebChromeClient(new WebChromeClient() { @Override public void onProgressChanged(WebView view, int newProgress) { progress.setProgress(newProgress); progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE); } });
        box.addView(web, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, 0, 1); bp.topMargin = dp(8);
        root.addView(box, bp);
        String start = getIntent() == null ? null : getIntent().getStringExtra("url");
        String last = start == null || start.trim().isEmpty() ? getPreferences(MODE_PRIVATE).getString("last", "https://arena.ai") : start;
        load(last);
    }

    private void load(String raw) {
        try {
            String u = raw == null ? "" : raw.trim();
            if (u.isEmpty()) u = "https://arena.ai";
            if (!u.contains(".") && !u.startsWith("http")) u = "https://yandex.ru/search/?text=" + URLEncoder.encode(u, "UTF-8");
            else if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u;
            address.setText(u);
            web.loadUrl(u);
            getPreferences(MODE_PRIVATE).edit().putString("last", u).apply();
        } catch (Exception ignored) {}
    }

    @Override public void onBackPressed() { if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed(); }

    private class GuardWebClient extends WebViewClient {
        @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri u = request == null ? null : request.getUrl();
            if (u == null) return false;
            String scheme = u.getScheme();
            return !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
        }
        @Override public void onPageFinished(WebView view, String url) { address.setText(url); getPreferences(MODE_PRIVATE).edit().putString("last", url).apply(); }
        @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if (request == null || request.getUrl() == null) return null;
            String u = request.getUrl().toString().toLowerCase(Locale.ROOT);
            boolean save = GuardPrefs.browserSaver(BrowserActivity.this);
            if (save && (isTracker(u) || isHeavy(u))) return empty();
            return null;
        }
    }

    private boolean isHeavy(String u) { return u.endsWith(".mp4") || u.endsWith(".webm") || u.endsWith(".m4v") || u.endsWith(".gif") || u.endsWith(".avif") || u.endsWith(".webp") || u.endsWith(".jpg") || u.endsWith(".jpeg") || u.endsWith(".png"); }
    private boolean isTracker(String u) { return u.contains("doubleclick.net") || u.contains("googletagmanager") || u.contains("google-analytics") || u.contains("mc.yandex") || u.contains("metrika") || u.contains("facebook.net/tr") || u.contains("/ads?") || u.contains("adservice"); }
    private WebResourceResponse empty() { return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0])); }

    private LinearLayout col() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w < 0 ? w : dp(w), h < 0 ? h : dp(h)); }
    private GradientDrawable shape(int color, float r) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(r)); return g; }
    private TextView button(String text, Runnable r) { TextView v = text(text, 13, true, Color.WHITE); v.setGravity(Gravity.CENTER); v.setBackground(shape(PURPLE, 16)); v.setOnClickListener(x -> r.run()); return v; }
    private TextView chip(String text, Runnable r) { TextView v = text(text, 12, true, TEXT); v.setGravity(Gravity.CENTER); v.setPadding(dp(13), 0, dp(13), 0); GradientDrawable gd = shape(CARD, 15); gd.setStroke(dp(1), 0x44b56cff); v.setBackground(gd); LinearLayout.LayoutParams p = lp(-2, 34); p.rightMargin = dp(8); v.setLayoutParams(p); v.setOnClickListener(x -> r.run()); return v; }
    private TextView text(String s, int sp, boolean bold, int color) { TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(color); if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return v; }
}
