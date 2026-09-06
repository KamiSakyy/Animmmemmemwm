package app.yuro.guard;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_VPN = 701;
    private static final int BG = 0xff100d18;
    private static final int BG2 = 0xff15101f;
    private static final int CARD = 0xff1d1728;
    private static final int CARD2 = 0xff271d36;
    private static final int TEXT = 0xfff3eaff;
    private static final int MUTED = 0xffbbaacc;
    private static final int PURPLE = 0xffb56cff;
    private static final int CYAN = 0xff55f3ff;
    private FrameLayout content;
    private LinearLayout nav;
    private int tab = 0;
    private String appQuery = "";
    private String period = "minute";
    private NetDb db;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newFixedThreadPool(2);
    private final Runnable ticker = new Runnable() { @Override public void run() { io.execute(() -> { try { db.sampleAll(MainActivity.this); db.sampleMobileSummary(MainActivity.this); } catch (Throwable ignored) {} ui.post(() -> { if (!isFinishing() && tab != 1) render(); }); }); ui.postDelayed(this, 3000); } };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        db = NetDb.get(this);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 9);
        LinearLayout root = column();
        root.setBackgroundColor(BG);
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        nav = row(); nav.setGravity(Gravity.CENTER); nav.setPadding(dp(10), dp(8), dp(10), dp(8)); nav.setBackgroundColor(BG2);
        root.addView(nav, new LinearLayout.LayoutParams(-1, dp(70)));
        setContentView(root);
        io.execute(() -> { db.ensureBaselines(); db.syncPackages(this, true); db.sampleAll(this); db.sampleMobileSummary(this); ui.post(this::render); });
        render();
    }

    @Override public void onResume() { super.onResume(); ui.removeCallbacks(ticker); ui.postDelayed(ticker, 1000); }
    @Override public void onPause() { ui.removeCallbacks(ticker); super.onPause(); }
    @Override protected void onDestroy() { ui.removeCallbacks(ticker); io.shutdownNow(); super.onDestroy(); }

    private void render() {
        if (content == null) return;
        content.removeAllViews();
        if (tab == 1) appsScreen();
        else if (tab == 2) reportScreen();
        else if (tab == 3) browserScreen();
        else dashboard();
        renderNav();
    }

    private void dashboard() {
        LinearLayout col = scroll();
        header(col, "YURO Guard", "Точный контроль интернета, firewall и отчёты");
        hero(col);
        modes(col);
        speedCard(col);
        totals(col);
        permissions(col);
        infoCard(col, "Как работает защита", "Android разрешает локальный firewall через VpnService. YURO Guard не подключается к чужому VPN-серверу: трафик выбранных для блокировки приложений уходит в локальный туннель и отбрасывается. Выбранные разрешённые приложения обходят туннель и продолжают работать.");
    }

    private void hero(LinearLayout col) {
        boolean on = GuardPrefs.enabled(this) || GuardVpnService.isAlive();
        LinearLayout card = card();
        GradientDrawable grad = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xff2f1a4a, 0xff1b1427, 0xff0f2630});
        grad.setCornerRadius(dp(24)); card.setBackground(grad);
        TextView label = label(on ? "ЗАЩИТА АКТИВНА" : "ОДИН КЛИК ДО КОНТРОЛЯ"); card.addView(label);
        space(card, 12);
        TextView title = text(on ? "Интернет под контролем" : "Включить мощный контроль сети", 25, true, TEXT); title.setLineSpacing(dp(2), 1.0f); card.addView(title);
        space(card, 10);
        String mode = GuardPrefs.modeTitle(GuardPrefs.mode(this));
        int count = GuardPrefs.selected(this).size();
        card.addView(text(mode + " · выбрано приложений: " + count, 13, false, 0xffdfd2ec));
        space(card, 18);
        TextView power = button(on ? "Остановить" : "Включить в один клик", !on, () -> { if (on) stopGuard(); else prepareVpn(); });
        card.addView(power, new LinearLayout.LayoutParams(-1, dp(52)));
        col.addView(card, mlp(-1, -2, 0, 14, 0, 0));
    }

    private void modes(LinearLayout col) {
        section(col, "Режим");
        HorizontalScrollView hs = new HorizontalScrollView(this); hs.setHorizontalScrollBarEnabled(false);
        LinearLayout r = row();
        r.addView(modeChip("Только выбранные онлайн", GuardPrefs.MODE_ALLOW_ONLY));
        r.addView(modeChip("Блок выбранных", GuardPrefs.MODE_BLOCK_SELECTED));
        r.addView(modeChip("Лимит выбранных", GuardPrefs.MODE_LIMIT_SELECTED));
        r.addView(modeChip("Лимит всем", GuardPrefs.MODE_LIMIT_ALL));
        hs.addView(r);
        col.addView(hs, new LinearLayout.LayoutParams(-1, dp(46)));
    }

    private TextView modeChip(String title, String mode) {
        boolean active = GuardPrefs.mode(this).equals(mode);
        TextView v = chip(title, active, () -> { GuardPrefs.mode(this, mode); refreshGuard(); render(); });
        return v;
    }

    private void speedCard(LinearLayout col) {
        LinearLayout card = card();
        card.addView(text("Лимит скорости", 18, true, TEXT));
        space(card, 8);
        card.addView(text("Укажите скорость в КБ/с. Для режимов лимита YURO Guard делает реальную сетевую паузу приложению/группе при превышении окна скорости.", 12, false, MUTED));
        space(card, 12);
        LinearLayout r = row(); r.setGravity(Gravity.CENTER_VERTICAL);
        EditText kb = new EditText(this); kb.setSingleLine(true); kb.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); kb.setImeOptions(EditorInfo.IME_ACTION_DONE); kb.setText(String.valueOf(Math.max(1, GuardPrefs.capBytes(this) / 1024L))); kb.setTextColor(TEXT); kb.setHintTextColor(MUTED); kb.setTextSize(15); kb.setGravity(Gravity.CENTER); kb.setBackground(shape(CARD2, 16));
        r.addView(kb, new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView apply = button("КБ/с", true, () -> { try { GuardPrefs.capBytes(this, Long.parseLong(kb.getText().toString().trim()) * 1024L); refreshGuard(); toast("Лимит применён"); render(); } catch (Exception e) { toast("Введите число"); } });
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(dp(86), dp(48)); ap.leftMargin = dp(8); r.addView(apply, ap);
        card.addView(r);
        space(card, 10);
        LinearLayout presets = row();
        presets.addView(smallPreset("200 КБ/с", 200L * 1024L));
        presets.addView(smallPreset("1 МБ/с", 1024L * 1024L));
        presets.addView(smallPreset("5 МБ/с", 5L * 1024L * 1024L));
        card.addView(presets);
        col.addView(card, mlp(-1, -2, 0, 14, 0, 0));
    }

    private TextView smallPreset(String title, long bytes) { return chip(title, GuardPrefs.capBytes(this) == bytes, () -> { GuardPrefs.capBytes(this, bytes); refreshGuard(); render(); }); }

    private void totals(LinearLayout col) {
        NetDb.Totals t = db.totals();
        section(col, "Отчёт с запуска приложения");
        LinearLayout grid = column();
        LinearLayout a = row();
        a.addView(metric("Всего устройство", fmt(t.deviceRx + t.deviceTx), "RX " + fmt(t.deviceRx) + " · TX " + fmt(t.deviceTx)), new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, -2, 1); sp.leftMargin = dp(10);
        a.addView(metric("Мобильный", fmt(t.mobileDeviceRx + t.mobileDeviceTx), "по счётчику Android", CYAN), sp);
        grid.addView(a);
        LinearLayout b = row();
        b.addView(metric("По приложениям", fmt(t.uidRx + t.uidTx), "UID-сумма без дублей"), new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams sp2 = new LinearLayout.LayoutParams(0, -2, 1); sp2.leftMargin = dp(10);
        b.addView(metric("Сейчас", rate(t.speedRx + t.speedTx), "текущая скорость"), sp2);
        grid.addView(b, mlp(-1, -2, 0, 10, 0, 0));
        col.addView(grid);
    }

    private LinearLayout metric(String top, String mid, String bottom) { return metric(top, mid, bottom, PURPLE); }
    private LinearLayout metric(String top, String mid, String bottom, int accent) {
        LinearLayout c = card(); c.setPadding(dp(14), dp(14), dp(14), dp(14));
        c.addView(text(top, 11, true, MUTED)); space(c, 8);
        c.addView(text(mid, 20, true, accent)); space(c, 6);
        c.addView(text(bottom, 10, false, MUTED));
        return c;
    }

    private void permissions(LinearLayout col) {
        if (NetDb.hasUsageAccess(this)) return;
        LinearLayout c = card();
        c.addView(text("Для точного мобильного отчёта по UID", 17, true, TEXT)); space(c, 8);
        c.addView(text("Выдай Usage Access: тогда Android NetworkStatsManager отдаст мобильные бакеты по приложениям, включая системные при включённой галочке.", 12, false, MUTED)); space(c, 12);
        c.addView(button("Открыть разрешение", true, () -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))), new LinearLayout.LayoutParams(-1, dp(48)));
        col.addView(c, mlp(-1, -2, 0, 14, 0, 0));
    }

    private void appsScreen() {
        LinearLayout root = column(); root.setPadding(dp(14), dp(12), dp(14), dp(8)); root.setBackgroundColor(BG);
        content.addView(root, new FrameLayout.LayoutParams(-1, -1));
        header(root, "Приложения", "Выбери, кого разрешать, блокировать или ограничивать");
        LinearLayout top = row(); top.setGravity(Gravity.CENTER_VERTICAL);
        EditText search = new EditText(this); search.setSingleLine(true); search.setText(appQuery); search.setHint("Поиск приложения"); search.setTextColor(TEXT); search.setHintTextColor(MUTED); search.setTextSize(14); search.setBackground(shape(CARD2, 16)); search.setPadding(dp(14), 0, dp(14), 0);
        top.addView(search, new LinearLayout.LayoutParams(0, dp(46), 1));
        CheckBox sys = new CheckBox(this); sys.setText("Системные"); sys.setTextColor(MUTED); sys.setTextSize(12); sys.setButtonTintList(android.content.res.ColorStateList.valueOf(PURPLE)); sys.setChecked(GuardPrefs.includeSystem(this));
        top.addView(sys, mlp(-2, 46, 8, 0, 0, 0));
        root.addView(top);
        TextView hint = text("Режим сейчас: " + GuardPrefs.modeTitle(GuardPrefs.mode(this)) + ". Нажатие по строке меняет выбор.", 11, false, MUTED); root.addView(hint, mlp(-1, -2, 0, 8, 0, 8));
        ListView list = new ListView(this); list.setDivider(null); list.setCacheColorHint(Color.TRANSPARENT); list.setSelector(android.R.color.transparent); list.setVerticalScrollBarEnabled(false); list.setBackgroundColor(BG);
        AppAdapter adapter = new AppAdapter(); list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        Runnable reload = () -> loadApps(adapter, GuardPrefs.includeSystem(this), appQuery);
        sys.setOnCheckedChangeListener((v, on) -> { GuardPrefs.includeSystem(this, on); loadApps(adapter, on, appQuery); });
        search.addTextChangedListener(new TextWatcher() { @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {} @Override public void onTextChanged(CharSequence s, int st, int before, int count) { appQuery = s == null ? "" : s.toString(); loadApps(adapter, GuardPrefs.includeSystem(MainActivity.this), appQuery); } @Override public void afterTextChanged(Editable e) {} });
        reload.run();
    }

    private void loadApps(AppAdapter adapter, boolean includeSystem, String q) {
        io.execute(() -> { try { db.sampleAll(this); } catch (Throwable ignored) {} List<AppEntry> rows = db.apps(this, includeSystem, q, false, 0); ui.post(() -> adapter.setRows(rows)); });
    }

    private void reportScreen() {
        LinearLayout col = scroll();
        header(col, "Отчёт", "Поминутно, почасово, по дням и по приложениям");
        LinearLayout opts = card();
        CheckBox mobile = new CheckBox(this); mobile.setText("Только мобильный интернет"); mobile.setTextColor(TEXT); mobile.setTextSize(14); mobile.setButtonTintList(android.content.res.ColorStateList.valueOf(PURPLE)); mobile.setChecked(GuardPrefs.mobileReport(this));
        mobile.setOnCheckedChangeListener((v, on) -> { GuardPrefs.mobileReport(this, on); render(); });
        opts.addView(mobile);
        CheckBox sys = new CheckBox(this); sys.setText("Показывать системные приложения"); sys.setTextColor(MUTED); sys.setTextSize(13); sys.setButtonTintList(android.content.res.ColorStateList.valueOf(PURPLE)); sys.setChecked(GuardPrefs.includeSystem(this));
        sys.setOnCheckedChangeListener((v, on) -> { GuardPrefs.includeSystem(this, on); render(); });
        opts.addView(sys);
        col.addView(opts, mlp(-1, -2, 0, 12, 0, 0));
        if (GuardPrefs.mobileReport(this) && !NetDb.hasUsageAccess(this)) permissions(col);
        HorizontalScrollView hs = new HorizontalScrollView(this); hs.setHorizontalScrollBarEnabled(false);
        LinearLayout periods = row(); periods.addView(periodChip("Минуты", "minute")); periods.addView(periodChip("Часы", "hour")); periods.addView(periodChip("Дни", "day")); hs.addView(periods); col.addView(hs, new LinearLayout.LayoutParams(-1, dp(46)));
        boolean mob = GuardPrefs.mobileReport(this);
        section(col, mob ? "Мобильные бакеты" : "Все сети по UID");
        List<BucketRow> timeline = db.timeline(mob, period, 80);
        if (timeline.isEmpty()) col.addView(empty("Пока нет данных. Оставь мониторинг включённым хотя бы минуту."));
        else for (BucketRow r : timeline) col.addView(timelineRow(r));
        section(col, "Топ приложений");
        List<AppEntry> top = db.apps(this, GuardPrefs.includeSystem(this), "", mob, 30);
        int shown = 0;
        for (AppEntry e : top) {
            long value = mob ? e.mobileTotal() : e.total();
            if (value <= 0) continue;
            col.addView(appStatRow(e, mob)); shown++; if (shown >= 20) break;
        }
        if (shown == 0) col.addView(empty("Статистика приложений ещё набирается."));
        TextView reset = button("Сбросить отчёт и начать заново", false, () -> { db.reset(); toast("Отчёт сброшен"); render(); });
        col.addView(reset, mlp(-1, dp(50), 0, 18, 0, 20));
    }

    private TextView periodChip(String title, String p) { return chip(title, period.equals(p), () -> { period = p; render(); }); }

    private View timelineRow(BucketRow r) {
        LinearLayout c = card(); c.setPadding(dp(14), dp(12), dp(14), dp(12));
        String fmt = "minute".equals(period) ? "HH:mm" : ("hour".equals(period) ? "dd.MM HH:00" : "dd.MM.yyyy");
        c.addView(text(new SimpleDateFormat(fmt, Locale.getDefault()).format(new Date(r.bucket)), 14, true, TEXT));
        space(c, 5);
        c.addView(text("Всего " + fmt(r.total()) + " · RX " + fmt(r.rx) + " · TX " + fmt(r.tx), 11, false, MUTED));
        return c;
    }

    private View appStatRow(AppEntry e, boolean mobile) {
        LinearLayout c = row(); c.setGravity(Gravity.CENTER_VERTICAL); c.setPadding(dp(12), dp(10), dp(12), dp(10)); c.setBackground(shape(CARD, 16));
        ImageView icon = new ImageView(this); if (e.icon != null) icon.setImageDrawable(e.icon); c.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout texts = column(); TextView name = text(e.label, 14, true, TEXT); name.setSingleLine(true); texts.addView(name); texts.addView(text((e.system ? "Системное · " : "") + e.pkg, 10, false, MUTED));
        c.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        TextView val = text(fmt(mobile ? e.mobileTotal() : e.total()), 13, true, PURPLE); val.setGravity(Gravity.RIGHT); c.addView(val);
        LinearLayout.LayoutParams lp = mlp(-1, -2, 0, 0, 0, 8); c.setLayoutParams(lp);
        return c;
    }

    private void browserScreen() {
        LinearLayout col = scroll();
        header(col, "Браузер", "Chromium WebView, Яндекс-поиск, тёмная тема, сохранение входа");
        LinearLayout c = card();
        GradientDrawable grad = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xff21162e, 0xff0d2733}); grad.setCornerRadius(dp(24)); c.setBackground(grad);
        c.addView(label("YURO BROWSER")); space(c, 12);
        c.addView(text("Быстрый внутренний браузер", 24, true, TEXT)); space(c, 8);
        c.addView(text("Cookies и DOM-хранилище включены для сохранения входа. Режим экономии режет тяжёлые медиа и трекеры. Arena.ai закреплён в рекомендациях.", 12, false, 0xffdfd2ec)); space(c, 16);
        c.addView(button("Открыть браузер", true, () -> startActivity(new Intent(this, BrowserActivity.class))), new LinearLayout.LayoutParams(-1, dp(52)));
        col.addView(c);
        section(col, "Рекомендации");
        col.addView(linkCard("Arena.ai", "https://arena.ai"));
        col.addView(linkCard("Яндекс", "https://ya.ru"));
        col.addView(linkCard("Почта Яндекса", "https://mail.yandex.ru"));
    }

    private View linkCard(String title, String url) { LinearLayout c = card(); c.setPadding(dp(14), dp(13), dp(14), dp(13)); c.addView(text(title, 16, true, TEXT)); space(c, 5); c.addView(text(url, 11, false, MUTED)); c.setOnClickListener(v -> { Intent i = new Intent(this, BrowserActivity.class); i.putExtra("url", url); startActivity(i); }); return c; }

    private void prepareVpn() {
        Intent prep = VpnService.prepare(this);
        if (prep != null) startActivityForResult(prep, REQ_VPN);
        else startGuard();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VPN && resultCode == RESULT_OK) startGuard();
    }

    private void startGuard() {
        GuardPrefs.enabled(this, true);
        Intent i = new Intent(this, GuardVpnService.class).setAction(GuardVpnService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        toast("YURO Guard включён"); render();
    }

    private void stopGuard() {
        GuardPrefs.enabled(this, false);
        Intent i = new Intent(this, GuardVpnService.class).setAction(GuardVpnService.ACTION_STOP);
        startService(i);
        toast("YURO Guard остановлен"); render();
    }

    private void refreshGuard() {
        if (!GuardPrefs.enabled(this)) return;
        Intent i = new Intent(this, GuardVpnService.class).setAction(GuardVpnService.ACTION_REFRESH);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
    }

    private void renderNav() {
        nav.removeAllViews();
        nav.addView(navButton("Защита", 0), new LinearLayout.LayoutParams(0, -1, 1));
        nav.addView(navButton("Приложения", 1), new LinearLayout.LayoutParams(0, -1, 1));
        nav.addView(navButton("Отчёт", 2), new LinearLayout.LayoutParams(0, -1, 1));
        nav.addView(navButton("Браузер", 3), new LinearLayout.LayoutParams(0, -1, 1));
    }

    private TextView navButton(String s, int idx) {
        TextView v = text(s, 12, tab == idx, tab == idx ? Color.WHITE : MUTED); v.setGravity(Gravity.CENTER); v.setBackground(shape(tab == idx ? PURPLE : 0x00101010, 18)); v.setOnClickListener(x -> { tab = idx; render(); }); return v;
    }

    private class AppAdapter extends BaseAdapter {
        private final ArrayList<AppEntry> rows = new ArrayList<>();
        void setRows(List<AppEntry> list) { rows.clear(); if (list != null) rows.addAll(list); notifyDataSetChanged(); }
        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int p) { return rows.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public View getView(int pos, View convertView, ViewGroup parent) {
            AppEntry e = rows.get(pos);
            LinearLayout outer = row(); outer.setGravity(Gravity.CENTER_VERTICAL); outer.setPadding(dp(12), dp(10), dp(10), dp(10)); outer.setBackground(shape(CARD, 16));
            ImageView icon = new ImageView(MainActivity.this); if (e.icon != null) icon.setImageDrawable(e.icon); outer.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));
            LinearLayout texts = column(); texts.setPadding(dp(10), 0, dp(8), 0);
            TextView name = text(e.label, 14, true, TEXT); name.setSingleLine(true); texts.addView(name);
            texts.addView(text((e.system ? "Системное · " : "") + e.pkg, 10, false, MUTED));
            texts.addView(text("Трафик " + fmt(e.total()) + " · сейчас " + rate(e.speed()), 10, false, e.speed() > 0 ? CYAN : MUTED));
            outer.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
            CheckBox cb = new CheckBox(MainActivity.this); cb.setButtonTintList(android.content.res.ColorStateList.valueOf(PURPLE)); cb.setChecked(e.selected); cb.setFocusable(false); cb.setClickable(false); outer.addView(cb);
            outer.setOnClickListener(v -> { boolean on = !e.selected; e.selected = on; GuardPrefs.setSelected(MainActivity.this, e.pkg, on); notifyDataSetChanged(); refreshGuard(); });
            LinearLayout wrap = column(); wrap.setPadding(0, 0, 0, dp(8)); wrap.addView(outer, new LinearLayout.LayoutParams(-1, -2));
            return wrap;
        }
    }

    private LinearLayout scroll() {
        ScrollView sc = new ScrollView(this); sc.setFillViewport(true); sc.setVerticalScrollBarEnabled(false); sc.setBackgroundColor(BG);
        LinearLayout col = column(); col.setPadding(dp(14), dp(12), dp(14), dp(22));
        sc.addView(col, new ScrollView.LayoutParams(-1, -2));
        content.addView(sc, new FrameLayout.LayoutParams(-1, -1));
        return col;
    }

    private void header(LinearLayout col, String title, String sub) { col.addView(text(title, 30, true, TEXT)); space(col, 4); col.addView(text(sub, 12, false, MUTED)); space(col, 16); }
    private void section(LinearLayout col, String s) { space(col, 8); col.addView(text(s, 18, true, TEXT)); space(col, 10); }
    private void infoCard(LinearLayout col, String title, String body) { LinearLayout c = card(); c.addView(text(title, 17, true, TEXT)); space(c, 8); c.addView(text(body, 12, false, MUTED)); col.addView(c, mlp(-1, -2, 0, 14, 0, 0)); }
    private TextView empty(String s) { TextView v = text(s, 13, false, MUTED); v.setGravity(Gravity.CENTER); v.setPadding(dp(12), dp(20), dp(12), dp(20)); v.setBackground(shape(CARD, 16)); return v; }
    private LinearLayout card() { LinearLayout l = column(); l.setPadding(dp(16), dp(16), dp(16), dp(16)); l.setBackground(shape(CARD, 20)); return l; }
    private TextView label(String s) { TextView v = text(s, 11, true, CYAN); v.setLetterSpacing(0.08f); return v; }
    private TextView chip(String title, boolean active, Runnable click) { TextView v = text(title, 12, true, active ? Color.WHITE : TEXT); v.setGravity(Gravity.CENTER); v.setPadding(dp(14), 0, dp(14), 0); GradientDrawable gd = shape(active ? PURPLE : CARD, 16); gd.setStroke(dp(1), active ? PURPLE : 0x44b56cff); v.setBackground(gd); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(38)); lp.rightMargin = dp(8); v.setLayoutParams(lp); v.setOnClickListener(x -> click.run()); return v; }
    private TextView button(String s, boolean primary, Runnable r) { TextView v = text(s, 14, true, primary ? Color.WHITE : TEXT); v.setGravity(Gravity.CENTER); GradientDrawable gd = shape(primary ? PURPLE : CARD2, 17); gd.setStroke(dp(1), primary ? PURPLE : 0x55b56cff); v.setBackground(gd); v.setOnClickListener(x -> r.run()); return v; }
    private TextView text(String s, int sp, boolean bold, int color) { TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(color); if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); v.setLineSpacing(dp(2), 1.0f); return v; }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private void space(LinearLayout l, int h) { View v = new View(this); l.addView(v, new LinearLayout.LayoutParams(1, dp(h))); }
    private GradientDrawable shape(int color, float r) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(r)); return g; }
    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private LinearLayout.LayoutParams mlp(int w, int h, int l, int t, int r, int b) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w < 0 ? w : dp(w), h < 0 ? h : dp(h)); p.setMargins(dp(l), dp(t), dp(r), dp(b)); return p; }
    private String fmt(long b) { double v = Math.max(0, b); String[] u = {"Б", "КБ", "МБ", "ГБ", "ТБ"}; int i = 0; while (v >= 1024 && i < u.length - 1) { v /= 1024.0; i++; } return (i == 0 ? String.format(Locale.getDefault(), "%.0f %s", v, u[i]) : String.format(Locale.getDefault(), "%.1f %s", v, u[i])); }
    private String rate(long b) { return fmt(b) + "/с"; }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
