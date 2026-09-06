package app.yuro.guard;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
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
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_VPN_CONNECT = 701;
    private static final int REQ_VPN_GRANT = 702;
    private static final int BG = 0xff100d18;
    private static final int BG2 = 0xff15101f;
    private static final int CARD = 0xff1d1728;
    private static final int CARD2 = 0xff271d36;
    private static final int CARD3 = 0xff332247;
    private static final int TEXT = 0xfff3eaff;
    private static final int MUTED = 0xffbbaacc;
    private static final int PURPLE = 0xffb56cff;
    private static final int CYAN = 0xff55f3ff;
    private static final int GREEN = 0xff5dffb2;
    private static final int RED = 0xffff6b9d;

    private FrameLayout content;
    private LinearLayout nav;
    private ScrollView currentScroll;
    private ListView currentList;
    private TextView metricTotal, metricSpeed, metricMobile, metricWifi, heroSub;
    private int tab = 0;
    private int renderedTab = -1;
    private String appQuery = "";
    private String period = "minute";
    private int reportNet = NetDb.NET_ALL;
    private final int[] scrollY = new int[]{0,0,0,0};
    private int listPos = 0, listTop = 0;
    private NetDb db;
    private SharedPreferences uiPrefs;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            io.execute(() -> {
                try { db.sampleAll(MainActivity.this); db.samplePreciseNetworks(MainActivity.this); } catch (Throwable ignored) {}
                ui.post(() -> { if (!isFinishing()) updateLiveMetrics(); });
            });
            ui.postDelayed(this, 3000L);
        }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        db = NetDb.get(this);
        uiPrefs = getSharedPreferences("yuro_guard_ui", MODE_PRIVATE);
        loadUiState();
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 9);
        LinearLayout root = column(); root.setBackgroundColor(BG);
        content = new FrameLayout(this); content.setBackgroundColor(BG);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        nav = row(); nav.setGravity(Gravity.CENTER); nav.setPadding(dp(10), dp(8), dp(10), dp(8)); nav.setBackgroundColor(BG2);
        root.addView(nav, new LinearLayout.LayoutParams(-1, dp(70)));
        setContentView(root);
        startMonitorIfNeeded();
        io.execute(() -> { db.ensureBaselines(); db.syncPackages(this, true); db.sampleAll(this); db.samplePreciseNetworks(this); ui.post(this::render); });
        render();
    }

    @Override public void onResume() { super.onResume(); ui.removeCallbacks(ticker); ui.postDelayed(ticker, 800L); }
    @Override public void onPause() { rememberScroll(); saveUiState(); ui.removeCallbacks(ticker); super.onPause(); }
    @Override protected void onDestroy() { rememberScroll(); saveUiState(); ui.removeCallbacks(ticker); io.shutdownNow(); super.onDestroy(); }

    private void loadUiState() {
        tab = uiPrefs.getInt("tab", 0);
        appQuery = uiPrefs.getString("app_query", "");
        period = uiPrefs.getString("period", "minute");
        reportNet = uiPrefs.getInt("report_net", NetDb.NET_ALL);
        for (int i=0;i<scrollY.length;i++) scrollY[i] = uiPrefs.getInt("scroll_"+i, 0);
        listPos = uiPrefs.getInt("list_pos", 0);
        listTop = uiPrefs.getInt("list_top", 0);
    }

    private void saveUiState() {
        SharedPreferences.Editor e = uiPrefs.edit().putInt("tab", tab).putString("app_query", appQuery).putString("period", period).putInt("report_net", reportNet).putInt("list_pos", listPos).putInt("list_top", listTop);
        for (int i=0;i<scrollY.length;i++) e.putInt("scroll_"+i, scrollY[i]);
        e.apply();
    }

    private void rememberScroll() {
        try {
            int slot = renderedTab >= 0 ? renderedTab : tab;
            if (currentScroll != null) scrollY[Math.max(0, Math.min(3, slot))] = currentScroll.getScrollY();
            if (currentList != null) {
                listPos = currentList.getFirstVisiblePosition();
                View first = currentList.getChildAt(0);
                listTop = first == null ? 0 : first.getTop() - currentList.getPaddingTop();
            }
        } catch (Throwable ignored) {}
    }

    private void restoreScroll() {
        try {
            int y = scrollY[Math.max(0, Math.min(3, tab))];
            if (currentScroll != null) currentScroll.post(() -> currentScroll.scrollTo(0, y));
        } catch (Throwable ignored) {}
    }

    private void restoreList() {
        try { if (currentList != null) currentList.post(() -> currentList.setSelectionFromTop(Math.max(0, listPos), listTop)); } catch (Throwable ignored) {}
    }

    private void render() {
        if (content == null) return;
        rememberScroll();
        content.removeAllViews(); currentScroll = null; currentList = null; metricTotal = metricSpeed = metricMobile = metricWifi = heroSub = null;
        try {
            if (tab == 1) appsScreen(); else if (tab == 2) reportScreen(); else if (tab == 3) browserScreen(); else dashboard();
        } catch (Throwable e) {
            content.removeAllViews(); currentScroll = null; currentList = null;
            errorScreen(tab == 2 ? "Отчёт" : tab == 3 ? "Браузер" : "Экран", e);
        }
        renderedTab = tab;
        renderNav(); restoreScroll(); saveUiState();
    }

    private void errorScreen(String title, Throwable e) {
        LinearLayout col = scroll();
        header(col, title, "Экран защищён от вылета: YURO не перекинет тебя в другой раздел");
        LinearLayout c = card();
        c.addView(text("Раздел восстановлен", 20, true, TEXT)); space(c, 8);
        c.addView(text("Поймана внутренняя ошибка данных/Android API. Нажми обновить — отчёт пересоберётся безопасно.", 12, false, MUTED)); space(c, 12);
        c.addView(button("Обновить этот экран", true, this::render), new LinearLayout.LayoutParams(-1, dp(48)));
        col.addView(c);
    }

    private void dashboard() {
        LinearLayout col = scroll();
        header(col, "YURO Guard", "Профессиональный контроль трафика, firewall, лимиты и экономный браузер");
        hero(col);
        monitorCard(col);
        modes(col);
        speedCard(col);
        mediaShieldCard(col);
        dpiBypassCard(col);
        totals(col);
        permissionCenter(col);
        infoCard(col, "Работает всегда", "Счётчик теперь отдельный от VPN: foreground-мониторинг считает TrafficStats постоянно, а при Usage Access добавляет точные Android NetworkStatsManager бакеты мобильной сети и Wi‑Fi по UID. VPN нужен только для блокировок и лимитов.");
    }

    private void hero(LinearLayout col) {
        boolean vpn = GuardVpnService.isAlive();
        boolean mon = GuardPrefs.monitorEnabled(this) || MonitorService.isAlive();
        LinearLayout card = card();
        GradientDrawable grad = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xff432066, 0xff171121, 0xff0d3141}); grad.setCornerRadius(dp(28)); card.setBackground(grad);
        card.addView(label(vpn ? "VPN FIREWALL ONLINE" : "YURO NETWORK COMMAND")); space(card, 12);
        card.addView(text(vpn ? "Интернет под жёстким контролем" : "Защита и точный счёт в один экран", 26, true, TEXT)); space(card, 9);
        heroSub = text((mon ? "Постоянный счётчик активен" : "Счётчик на паузе") + " · " + GuardPrefs.modeTitle(GuardPrefs.mode(this)) + " · выбрано " + GuardPrefs.selected(this).size(), 13, false, 0xffeadcf8);
        card.addView(heroSub);
        space(card, 18);
        LinearLayout actions = row();
        actions.addView(button(vpn ? "Остановить VPN" : "Включить VPN", !vpn, () -> { if (vpn) stopGuard(); else prepareVpn(); }), new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(52), 1); p.leftMargin = dp(9);
        actions.addView(button(mon ? "Счётчик ON" : "Счётчик OFF", mon, () -> { if (mon) stopMonitor(); else startMonitor(); }), p);
        card.addView(actions);
        col.addView(card, mlp(-1, -2, 0, 14, 0, 0));
    }

    private void monitorCard(LinearLayout col) {
        LinearLayout c = card();
        c.setPadding(dp(15), dp(15), dp(15), dp(15));
        LinearLayout top = row(); top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout texts = column();
        texts.addView(text("Постоянный мониторинг", 18, true, TEXT)); space(texts, 4);
        texts.addView(text("Считает всегда, даже когда VPN выключен. После перезагрузки поднимается снова.", 11, false, MUTED));
        top.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        CheckBox cb = new CheckBox(this); cb.setButtonTintList(android.content.res.ColorStateList.valueOf(PURPLE)); cb.setChecked(GuardPrefs.monitorEnabled(this) || MonitorService.isAlive());
        top.addView(cb);
        c.addView(top); space(c, 12);
        c.addView(text("Старт отчёта: " + new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(new Date(db.startedAt())), 11, false, MUTED));
        cb.setOnCheckedChangeListener((v, on) -> { if (on) startMonitor(); else stopMonitor(); render(); });
        col.addView(c, mlp(-1, -2, 0, 12, 0, 0));
    }

    private void modes(LinearLayout col) {
        section(col, "Режим VPN");
        HorizontalScrollView hs = new HorizontalScrollView(this); hs.setHorizontalScrollBarEnabled(false);
        LinearLayout r = row();
        r.addView(modeChip("Только выбранные онлайн", GuardPrefs.MODE_ALLOW_ONLY));
        r.addView(modeChip("Блок выбранных", GuardPrefs.MODE_BLOCK_SELECTED));
        r.addView(modeChip("Лимит выбранных", GuardPrefs.MODE_LIMIT_SELECTED));
        r.addView(modeChip("Лимит всем", GuardPrefs.MODE_LIMIT_ALL));
        hs.addView(r); col.addView(hs, new LinearLayout.LayoutParams(-1, dp(46)));
    }

    private TextView modeChip(String title, String mode) { return chip(title, GuardPrefs.mode(this).equals(mode), () -> { GuardPrefs.mode(this, mode); refreshGuard(); render(); }); }

    private void speedCard(LinearLayout col) {
        LinearLayout card = card();
        card.addView(text("Лимит скорости", 18, true, TEXT)); space(card, 8);
        card.addView(text("Задай КБ/с. При превышении окна скорости YURO Guard включает VPN-паузу для выбранного приложения или группы — это рабочий no-root shaping на Android.", 12, false, MUTED)); space(card, 12);
        LinearLayout r = row(); r.setGravity(Gravity.CENTER_VERTICAL);
        EditText kb = new EditText(this); kb.setSingleLine(true); kb.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); kb.setImeOptions(EditorInfo.IME_ACTION_DONE); kb.setText(String.valueOf(Math.max(1, GuardPrefs.capBytes(this) / 1024L))); kb.setTextColor(TEXT); kb.setHintTextColor(MUTED); kb.setTextSize(15); kb.setGravity(Gravity.CENTER); kb.setBackground(shape(CARD2, 16));
        r.addView(kb, new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView apply = button("Применить", true, () -> applyCap(kb));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(dp(112), dp(48)); ap.leftMargin = dp(8); r.addView(apply, ap);
        card.addView(r); space(card, 10);
        HorizontalScrollView hs = new HorizontalScrollView(this); hs.setHorizontalScrollBarEnabled(false);
        LinearLayout presets = row(); presets.addView(smallPreset("64 КБ/с", 64L*1024L)); presets.addView(smallPreset("200 КБ/с", 200L*1024L)); presets.addView(smallPreset("1 МБ/с", 1024L*1024L)); presets.addView(smallPreset("5 МБ/с", 5L*1024L*1024L)); hs.addView(presets); card.addView(hs);
        col.addView(card, mlp(-1, -2, 0, 14, 0, 0));
    }

    private void applyCap(EditText kb) { try { GuardPrefs.capBytes(this, Long.parseLong(kb.getText().toString().trim()) * 1024L); refreshGuard(); toast("Лимит применён"); render(); } catch (Exception e) { toast("Введите число"); } }
    private TextView smallPreset(String title, long bytes) { return chip(title, GuardPrefs.capBytes(this) == bytes, () -> { GuardPrefs.capBytes(this, bytes); refreshGuard(); render(); }); }

    private void mediaShieldCard(LinearLayout col) {
        LinearLayout c = card();
        c.addView(text("Media Shield / Только текст", 18, true, TEXT)); space(c, 8);
        c.addView(text("Браузер грузит текст и документы, а картинки/видео/аудио/гифки/шрифты/трекеры режет до сети. VPN-парсер дополнительно видит DNS, HTTP Host/URL и TLS SNI без расшифровки HTTPS.", 12, false, MUTED)); space(c, 10);
        c.addView(toggleRow("Максимальная экономия браузера", GuardPrefs.strictTextMode(this), "оставляет текст, HTML/JSON/CSS/JS и блокирует тяжёлые медиа", on -> { GuardPrefs.strictTextMode(this,on); GuardPrefs.browserBlockImages(this,on); GuardPrefs.browserSaver(this,on); }));
        c.addView(toggleRow("VPN Media Shield / DPI-lite", GuardPrefs.mediaShield(this), "логирует и помечает image/video/audio/CDN/трекеры по DNS/SNI/HTTP", on -> { GuardPrefs.mediaShield(this,on); refreshGuard(); }));
        c.addView(toggleRow("Журнал направлений", GuardPrefs.dpiLogging(this), "сохраняет куда приложения подключались: IP, порт, домен, протокол", on -> GuardPrefs.dpiLogging(this,on)));
        col.addView(c, mlp(-1, -2, 0, 14, 0, 0));
    }

    private interface BoolSet { void set(boolean on); }
    private View toggleRow(String title, boolean checked, String sub, BoolSet set) {
        LinearLayout r = row(); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(0, dp(6), 0, dp(6));
        LinearLayout txt = column(); txt.addView(text(title, 14, true, TEXT)); txt.addView(text(sub, 10, false, MUTED)); r.addView(txt, new LinearLayout.LayoutParams(0, -2, 1));
        CheckBox cb = new CheckBox(this); cb.setButtonTintList(android.content.res.ColorStateList.valueOf(PURPLE)); cb.setChecked(checked); cb.setOnCheckedChangeListener((v,on)->{ set.set(on); saveUiState(); }); r.addView(cb);
        return r;
    }

    private void dpiBypassCard(LinearLayout col) {
        LinearLayout c = card();
        LinearLayout head = row(); head.setGravity(Gravity.CENTER_VERTICAL);
        YuroIcon icon = new YuroIcon(this, YuroIcon.DPI); icon.setColor(CYAN); head.addView(icon, new LinearLayout.LayoutParams(dp(38), dp(38)));
        LinearLayout titleBox = column(); titleBox.setPadding(dp(10),0,0,0); titleBox.addView(text("DPI обход", 18, true, TEXT)); titleBox.addView(text("отдельный профиль, включается только вручную", 10, false, MUTED)); head.addView(titleBox, new LinearLayout.LayoutParams(0,-2,1));
        c.addView(head); space(c, 10);
        c.addView(text("Telegram Rescue теперь работает двумя путями: 1) локальный SOCKS5 proxy без отключения чужого VPN; 2) YURO VPN/DPI-lite только если нажать подключение. Proxy режет первый TCP/TLS поток adaptive split стратегиями.", 12, false, MUTED)); space(c, 10);
        c.addView(toggleRow("Авто DPI профиль", GuardPrefs.dpiBypass(this), "сам выбирает split/SNI/micro стратегии для новых соединений", on -> { GuardPrefs.dpiBypass(this,on); GuardPrefs.dpiLogging(this,true); refreshGuard(); }));
        c.addView(toggleRow("Блокировать QUIC UDP/443", GuardPrefs.dpiQuicBlock(this), "заставляет сервисы чаще уходить в TCP/TLS, где работает split", on -> { GuardPrefs.dpiQuicBlock(this,on); refreshGuard(); }));
        c.addView(dpiLevelRow());
        LinearLayout proxy = row();
        proxy.addView(button(DpiProxyService.isAlive() ? "SOCKS5 ON" : "Запустить SOCKS5", true, () -> { if (DpiProxyService.isAlive()) stopDpiProxy(); else startDpiProxy(); render(); }), new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(0, dp(48), 1); pp.leftMargin = dp(8);
        proxy.addView(button("Telegram", false, this::openTelegramProxy), pp);
        c.addView(proxy); space(c, 8);
        LinearLayout buttons = row();
        buttons.addView(button("Авто Telegram", true, () -> { selectTelegramApps(); startDpiProxy(); runTelegramAutoTune(); openTelegramProxy(); render(); }), new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(48), 1); bp.leftMargin = dp(8);
        buttons.addView(button("Автоподбор", false, this::runTelegramAutoTune), bp);
        c.addView(buttons); space(c, 8);
        c.addView(button("YURO VPN DPI", false, () -> { GuardPrefs.dpiBypass(this, true); prepareVpn(); }), new LinearLayout.LayoutParams(-1, dp(48)));
        col.addView(c, mlp(-1, -2, 0, 14, 0, 0));
    }

    private View dpiLevelRow() {
        LinearLayout wrap = column(); wrap.setPadding(0, dp(4), 0, dp(8));
        wrap.addView(text("Агрессивность авто-профиля: " + GuardPrefs.dpiAutoLevel(this) + "/4", 12, true, CYAN));
        LinearLayout r = row();
        for (int i=1;i<=4;i++) { final int level=i; r.addView(chip(String.valueOf(i), GuardPrefs.dpiAutoLevel(this)==i, () -> { GuardPrefs.dpiAutoLevel(this, level); refreshGuard(); render(); })); }
        wrap.addView(r); return wrap;
    }

    private void totals(LinearLayout col) {
        NetDb.Totals t = db.totals(); section(col, "Live-отчёт");
        LinearLayout a = row();
        LinearLayout m1 = metric("Всего устройство", fmt(t.deviceRx + t.deviceTx), "с начала YURO Guard"); metricTotal = metricMid(m1); a.addView(m1, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1); p.leftMargin = dp(10); LinearLayout m2 = metric("Сейчас", rate(t.speedRx + t.speedTx), "текущая UID-скорость", CYAN); metricSpeed = metricMid(m2); a.addView(m2, p); col.addView(a);
        LinearLayout b = row();
        LinearLayout m3 = metric("Мобильный точный", fmt((t.mobileRx + t.mobileTx) > 0 ? t.mobileRx + t.mobileTx : t.mobileDeviceRx + t.mobileDeviceTx), NetDb.hasUsageAccess(this) ? "NetworkStatsManager" : "нужен Usage Access", GREEN); metricMobile = metricMid(m3); b.addView(m3, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, -2, 1); p2.leftMargin = dp(10); LinearLayout m4 = metric("Wi‑Fi точный", fmt(t.wifiRx + t.wifiTx), NetDb.hasUsageAccess(this) ? "NetworkStatsManager" : "нужен Usage Access", PURPLE); metricWifi = metricMid(m4); b.addView(m4, p2); col.addView(b, mlp(-1, -2, 0, 10, 0, 0));
    }

    private LinearLayout metric(String top, String mid, String bottom) { return metric(top, mid, bottom, PURPLE); }
    private LinearLayout metric(String top, String mid, String bottom, int accent) { LinearLayout c = card(); c.setPadding(dp(14), dp(14), dp(14), dp(14)); c.addView(text(top, 11, true, MUTED)); space(c, 8); c.addView(text(mid, 20, true, accent)); space(c, 6); c.addView(text(bottom, 10, false, MUTED)); return c; }
    private TextView metricMid(LinearLayout m) { try { return (TextView)m.getChildAt(2); } catch (Throwable e) { return null; } }
    private void updateLiveMetrics() {
        try {
            if (metricTotal == null && metricSpeed == null && metricMobile == null && metricWifi == null && heroSub == null) return;
            NetDb.Totals t = db.totals();
            if (metricTotal != null) metricTotal.setText(fmt(t.deviceRx + t.deviceTx));
            if (metricSpeed != null) metricSpeed.setText(rate(t.speedRx + t.speedTx));
            if (metricMobile != null) metricMobile.setText(fmt((t.mobileRx + t.mobileTx) > 0 ? t.mobileRx + t.mobileTx : t.mobileDeviceRx + t.mobileDeviceTx));
            if (metricWifi != null) metricWifi.setText(fmt(t.wifiRx + t.wifiTx));
            if (heroSub != null) heroSub.setText(((GuardPrefs.monitorEnabled(this) || MonitorService.isAlive()) ? "Постоянный счётчик активен" : "Счётчик на паузе") + " · " + GuardPrefs.modeTitle(GuardPrefs.mode(this)) + " · выбрано " + GuardPrefs.selected(this).size());
        } catch (Throwable ignored) {}
    }

    private void permissionCenter(LinearLayout col) {
        section(col, "Центр точности");
        LinearLayout c = card();
        c.addView(permissionRow("Usage Access", NetDb.hasUsageAccess(this), "точный мобильный/Wi‑Fi отчёт по приложениям", () -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))));
        c.addView(permissionRow("Без экономии батареи", batteryOk(), "чтобы счётчик не засыпал", this::openBattery));
        c.addView(permissionRow("Уведомление", notificationOk(), "показывает постоянную работу сервиса", () -> { if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 9); else toast("Уже доступно"); }));
        c.addView(permissionRow("VPN-разрешение", GuardPrefs.vpnPermissionSeen(this) || GuardVpnService.isAlive(), "не проверяется при входе, чтобы не трогать другой VPN", this::prepareVpnOnly));
        col.addView(c);
    }

    private View permissionRow(String title, boolean ok, String sub, Runnable click) {
        LinearLayout r = row(); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(0, dp(7), 0, dp(7));
        TextView badge = text(ok ? "✓" : "!", 18, true, ok ? GREEN : RED); badge.setGravity(Gravity.CENTER); badge.setBackground(shape(ok ? 0x225dffb2 : 0x22ff6b9d, 14)); r.addView(badge, new LinearLayout.LayoutParams(dp(34), dp(34)));
        LinearLayout txt = column(); txt.setPadding(dp(10), 0, dp(8), 0); txt.addView(text(title, 14, true, TEXT)); txt.addView(text(sub, 10, false, MUTED)); r.addView(txt, new LinearLayout.LayoutParams(0, -2, 1));
        r.addView(button(ok ? "OK" : "Выдать", ok, click), new LinearLayout.LayoutParams(dp(92), dp(38)));
        return r;
    }

    private boolean notificationOk() { return Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED; }
    private boolean batteryOk() { try { if (Build.VERSION.SDK_INT < 23) return true; PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE); return pm == null || pm.isIgnoringBatteryOptimizations(getPackageName()); } catch (Throwable e) { return false; } }
    private void openBattery() { try { if (Build.VERSION.SDK_INT >= 23) startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()))); else toast("Не требуется"); } catch (Throwable e) { try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); } catch (Throwable ignored) {} } }

    private void appsScreen() {
        LinearLayout root = column(); root.setPadding(dp(14), dp(12), dp(14), dp(8)); root.setBackgroundColor(BG); content.addView(root, new FrameLayout.LayoutParams(-1, -1));
        header(root, "Приложения", "Правила VPN, лимитов и точный трафик по каждому UID");
        LinearLayout top = row(); top.setGravity(Gravity.CENTER_VERTICAL);
        EditText search = new EditText(this); search.setSingleLine(true); search.setText(appQuery); search.setHint("Поиск приложения"); search.setTextColor(TEXT); search.setHintTextColor(MUTED); search.setTextSize(14); search.setBackground(shape(CARD2, 16)); search.setPadding(dp(14), 0, dp(14), 0);
        top.addView(search, new LinearLayout.LayoutParams(0, dp(46), 1));
        CheckBox sys = new CheckBox(this); sys.setText("Системные"); sys.setTextColor(MUTED); sys.setTextSize(12); sys.setButtonTintList(android.content.res.ColorStateList.valueOf(PURPLE)); sys.setChecked(GuardPrefs.includeSystem(this)); top.addView(sys, mlp(-2, 46, 8, 0, 0, 0)); root.addView(top);
        TextView hint = text("Сейчас: " + GuardPrefs.modeTitle(GuardPrefs.mode(this)) + ". Нажатие по строке меняет выбор, состояние сохраняется.", 11, false, MUTED); root.addView(hint, mlp(-1, -2, 0, 8, 0, 8));
        ListView list = new ListView(this); currentList = list; list.setDivider(null); list.setCacheColorHint(Color.TRANSPARENT); list.setSelector(android.R.color.transparent); list.setVerticalScrollBarEnabled(false); list.setBackgroundColor(BG);
        AppAdapter adapter = new AppAdapter(); list.setAdapter(adapter); root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        sys.setOnCheckedChangeListener((v,on) -> { GuardPrefs.includeSystem(this,on); loadApps(adapter,on,appQuery); });
        search.addTextChangedListener(new TextWatcher() { @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {} @Override public void onTextChanged(CharSequence s, int st, int before, int count) { appQuery = s == null ? "" : s.toString(); saveUiState(); loadApps(adapter, GuardPrefs.includeSystem(MainActivity.this), appQuery); } @Override public void afterTextChanged(Editable e) {} });
        loadApps(adapter, GuardPrefs.includeSystem(this), appQuery);
    }

    private void loadApps(AppAdapter adapter, boolean includeSystem, String q) { io.execute(() -> { try { db.sampleAll(this); db.samplePreciseNetworks(this); } catch (Throwable ignored) {} List<AppEntry> rows = db.apps(this, includeSystem, q, false, 0); ui.post(() -> { adapter.setRows(rows); restoreList(); }); }); }

    private void reportScreen() {
        LinearLayout col = scroll();
        header(col, "Отчёт", "Минуты, часы, дни, приложения, мобильный и Wi‑Fi");
        LinearLayout opts = card(); opts.addView(text("Источник данных", 16, true, TEXT)); space(opts, 10);
        HorizontalScrollView nets = new HorizontalScrollView(this); nets.setHorizontalScrollBarEnabled(false); LinearLayout nr = row(); nr.addView(netChip("Все сети", NetDb.NET_ALL)); nr.addView(netChip("Мобильный точный", NetDb.NET_MOBILE)); nr.addView(netChip("Wi‑Fi точный", NetDb.NET_WIFI)); nets.addView(nr); opts.addView(nets);
        CheckBox sys = new CheckBox(this); sys.setText("Показывать системные приложения"); sys.setTextColor(MUTED); sys.setTextSize(13); sys.setButtonTintList(android.content.res.ColorStateList.valueOf(PURPLE)); sys.setChecked(GuardPrefs.includeSystem(this)); sys.setOnCheckedChangeListener((v,on)->{GuardPrefs.includeSystem(this,on);render();}); opts.addView(sys);
        col.addView(opts, mlp(-1, -2, 0, 12, 0, 0));
        if (reportNet != NetDb.NET_ALL && !NetDb.hasUsageAccess(this)) permissionCenter(col);
        HorizontalScrollView hs = new HorizontalScrollView(this); hs.setHorizontalScrollBarEnabled(false); LinearLayout periods = row(); periods.addView(periodChip("Минуты", "minute")); periods.addView(periodChip("Часы", "hour")); periods.addView(periodChip("Дни", "day")); hs.addView(periods); col.addView(hs, new LinearLayout.LayoutParams(-1, dp(46)));
        section(col, reportNet == NetDb.NET_MOBILE ? "Мобильные бакеты" : reportNet == NetDb.NET_WIFI ? "Wi‑Fi бакеты" : "Все сети по UID");
        List<BucketRow> timeline = db.timelineByNet(reportNet, period, 100);
        if (timeline.isEmpty()) col.addView(empty("Пока нет данных. Постоянный мониторинг уже включён — оставь приложение/сервис работать хотя бы минуту.")); else for (BucketRow r : timeline) col.addView(timelineRow(r));
        section(col, "Топ приложений");
        List<AppEntry> top = db.appsByNet(this, GuardPrefs.includeSystem(this), "", reportNet, 40); int shown = 0;
        for (AppEntry e : top) { long value = reportNet == NetDb.NET_MOBILE ? e.mobileTotal() : e.total(); if (value <= 0) continue; col.addView(appStatRow(e, reportNet)); shown++; if (shown >= 30) break; }
        if (shown == 0) col.addView(empty("Статистика приложений ещё набирается или нужен Usage Access для точного источника."));
        section(col, "Куда идёт трафик / DPI-lite");
        List<NetDb.EventRow> events = db.recentEvents(35);
        if (events.isEmpty()) col.addView(empty("Журнал направлений ещё пуст. Включи VPN-контроль или оставь постоянный мониторинг активным: будут DNS/SNI/HTTP/proc-net события."));
        else for (NetDb.EventRow ev : events) col.addView(eventRow(ev));
        col.addView(button("Сбросить отчёт и начать заново", false, () -> { db.reset(); toast("Отчёт сброшен"); render(); }), mlp(-1, dp(50), 0, 18, 0, 20));
    }

    private TextView netChip(String title, int net) { return chip(title, reportNet == net, () -> { reportNet = net; saveUiState(); render(); }); }
    private TextView periodChip(String title, String p) { return chip(title, period.equals(p), () -> { period = p; saveUiState(); render(); }); }

    private View timelineRow(BucketRow r) {
        LinearLayout c = card(); c.setPadding(dp(14), dp(12), dp(14), dp(12));
        String fmt = "minute".equals(period) ? "HH:mm" : ("hour".equals(period) ? "dd.MM HH:00" : "dd.MM.yyyy");
        c.addView(text(new SimpleDateFormat(fmt, Locale.getDefault()).format(new Date(r.bucket)), 14, true, TEXT)); space(c, 5);
        c.addView(text("Всего " + fmt(r.total()) + " · RX " + fmt(r.rx) + " · TX " + fmt(r.tx), 11, false, MUTED));
        c.setLayoutParams(mlp(-1, -2, 0, 0, 0, 8)); return c;
    }

    private View appStatRow(AppEntry e, int net) {
        LinearLayout c = row(); c.setGravity(Gravity.CENTER_VERTICAL); c.setPadding(dp(12), dp(10), dp(12), dp(10)); c.setBackground(shape(CARD, 16));
        ImageView icon = new ImageView(this); if (e.icon != null) icon.setImageDrawable(e.icon); c.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout texts = column(); texts.setPadding(dp(10), 0, dp(8), 0); TextView name = text(e.label, 14, true, TEXT); name.setSingleLine(true); texts.addView(name); texts.addView(text((e.system ? "Системное · " : "") + e.pkg, 10, false, MUTED)); c.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        long value = net == NetDb.NET_MOBILE ? e.mobileTotal() : e.total(); TextView val = text(fmt(value), 13, true, PURPLE); val.setGravity(Gravity.RIGHT); c.addView(val);
        c.setLayoutParams(mlp(-1, -2, 0, 0, 0, 8)); return c;
    }

    private View eventRow(NetDb.EventRow ev) {
        LinearLayout c = card(); c.setPadding(dp(13), dp(10), dp(13), dp(10));
        LinearLayout top = row(); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView k = text(ev.kind + (ev.blocked ? " · media" : ""), 12, true, ev.blocked ? RED : CYAN); top.addView(k, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(text(new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(ev.time)), 10, false, MUTED)); c.addView(top); space(c, 4);
        String host = ev.host == null || ev.host.length() == 0 ? ev.ip : ev.host;
        c.addView(text((ev.pkg == null || ev.pkg.length()==0 ? "uid " + ev.uid : ev.pkg) + " → " + host + (ev.port > 0 ? ":" + ev.port : ""), 12, true, TEXT));
        if (ev.note != null && ev.note.length() > 0) c.addView(text(ev.note, 10, false, MUTED));
        c.setLayoutParams(mlp(-1, -2, 0, 0, 0, 8)); return c;
    }

    private void browserScreen() {
        LinearLayout col = scroll();
        header(col, "Экономный браузер", "Вкладки, кэш, сохранение входа и запрет картинок");
        LinearLayout c = card(); GradientDrawable grad = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xff21162e, 0xff0d2733}); grad.setCornerRadius(dp(24)); c.setBackground(grad);
        c.addView(label("YURO BROWSER PRO")); space(c, 12); c.addView(text("Минимум перезагрузок — минимум трафика", 24, true, TEXT)); space(c, 8);
        c.addView(text("Вкладки держатся в памяти при переключении, URL/скролл/история WebView сохраняются, кэш включён, сетевые картинки/гифки/видео/шрифты и трекеры блокируются в режиме максимальной экономии.", 12, false, 0xffdfd2ec)); space(c, 16);
        c.addView(button("Открыть браузер", true, () -> startActivity(new Intent(this, BrowserActivity.class))), new LinearLayout.LayoutParams(-1, dp(52))); col.addView(c);
        section(col, "Быстрый доступ"); col.addView(linkCard("Arena.ai", "https://arena.ai")); col.addView(linkCard("Яндекс", "https://ya.ru")); col.addView(linkCard("Почта Яндекса", "https://mail.yandex.ru"));
    }

    private View linkCard(String title, String url) { LinearLayout c = card(); c.setPadding(dp(14), dp(13), dp(14), dp(13)); c.addView(text(title, 16, true, TEXT)); space(c, 5); c.addView(text(url, 11, false, MUTED)); c.setOnClickListener(v -> { Intent i = new Intent(this, BrowserActivity.class); i.putExtra("url", url); startActivity(i); }); return c; }

    private void startDpiProxy() {
        GuardPrefs.dpiProxyEnabled(this, true); GuardPrefs.dpiBypass(this, true); GuardPrefs.dpiLogging(this, true);
        Intent i = new Intent(this, DpiProxyService.class).setAction(DpiProxyService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        toast("SOCKS5 Rescue: 127.0.0.1:" + GuardPrefs.dpiProxyPort(this));
    }
    private void stopDpiProxy() { GuardPrefs.dpiProxyEnabled(this, false); try { startService(new Intent(this, DpiProxyService.class).setAction(DpiProxyService.ACTION_STOP)); } catch (Throwable ignored) {} toast("SOCKS5 Rescue остановлен"); }
    private void openTelegramProxy() {
        String port = String.valueOf(GuardPrefs.dpiProxyPort(this));
        Intent tg = new Intent(Intent.ACTION_VIEW, Uri.parse("tg://socks?server=127.0.0.1&port=" + port));
        try { startActivity(tg); }
        catch (Throwable e) { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/socks?server=127.0.0.1&port=" + port))); } catch (Throwable ignored) { toast("Telegram не найден"); } }
    }
    private void selectTelegramApps() {
        String[] pkgs = {"org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram", "org.telegram.plus", "nekox.messenger", "tw.nekomimi.nekogram"};
        int found = 0;
        for (String p : pkgs) { try { getPackageManager().getPackageInfo(p, 0); GuardPrefs.setSelected(this, p, true); found++; } catch (Throwable ignored) {} }
        toast(found > 0 ? "Telegram выбран: " + found : "Telegram-пакет не найден, proxy всё равно запущен");
    }

    private void runTelegramAutoTune() {
        toast("Проверяю Telegram DC…");
        io.execute(() -> {
            String[] dc = {"149.154.167.50", "149.154.167.51", "149.154.175.50", "149.154.175.53", "91.108.56.130", "91.108.56.131", "91.108.4.200", "91.108.8.8"};
            int ok = 0; long best = Long.MAX_VALUE; String bestIp = "";
            for (String ip : dc) {
                long t = System.currentTimeMillis(); Socket sock = null;
                try {
                    sock = new Socket(); sock.connect(new InetSocketAddress(ip, 443), 1800);
                    long ms = System.currentTimeMillis() - t; ok++; if (ms < best) { best = ms; bestIp = ip; }
                    db.recordEvent(-1, "Telegram Rescue", "TCP", "telegram-dc", ip, 443, "autotune", false, "connect " + ms + "ms");
                } catch (Throwable e) {
                    db.recordEvent(-1, "Telegram Rescue", "TCP", "telegram-dc", ip, 443, "autotune fail", true, e.getClass().getSimpleName());
                } finally { try { if (sock != null) sock.close(); } catch (Throwable ignored) {} }
            }
            int level = ok >= 3 ? 2 : ok >= 1 ? 3 : 4;
            GuardPrefs.dpiAutoLevel(this, level); GuardPrefs.dpiBypass(this, true); GuardPrefs.dpiLogging(this, true); GuardPrefs.dpiQuicBlock(this, true);
            final int finalOk = ok; final long finalBest = best; final String finalBestIp = bestIp; final int finalLevel = level;
            ui.post(() -> { toast(finalOk > 0 ? "Telegram DC OK: " + finalOk + ", уровень " + finalLevel + (finalBestIp.length()>0 ? " · " + finalBestIp + " " + finalBest + "ms" : "") : "DC не открылись, уровень 4"); render(); });
        });
    }

    private void startMonitorIfNeeded() { if (GuardPrefs.monitorEnabled(this)) startMonitor(); }
    private void startMonitor() { GuardPrefs.monitorEnabled(this, true); try { Intent i = new Intent(this, MonitorService.class).setAction(MonitorService.ACTION_START); if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i); } catch (Throwable ignored) {} }
    private void stopMonitor() { GuardPrefs.monitorEnabled(this, false); try { startService(new Intent(this, MonitorService.class).setAction(MonitorService.ACTION_STOP)); } catch (Throwable ignored) {} }
    private void prepareVpnOnly() { Intent prep = VpnService.prepare(this); if (prep != null) startActivityForResult(prep, REQ_VPN_GRANT); else { GuardPrefs.vpnPermissionSeen(this, true); toast("VPN-разрешение уже есть"); } }
    private void prepareVpn() { Intent prep = VpnService.prepare(this); if (prep != null) startActivityForResult(prep, REQ_VPN_CONNECT); else { GuardPrefs.vpnPermissionSeen(this, true); startGuard(); } }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) { super.onActivityResult(requestCode, resultCode, data); if (requestCode == REQ_VPN_CONNECT && resultCode == RESULT_OK) { GuardPrefs.vpnPermissionSeen(this, true); startGuard(); } else if (requestCode == REQ_VPN_GRANT && resultCode == RESULT_OK) { GuardPrefs.vpnPermissionSeen(this, true); toast("VPN-разрешение сохранено. Подключение только по кнопке."); render(); } }
    private void startGuard() { GuardPrefs.enabled(this, true); Intent i = new Intent(this, GuardVpnService.class).setAction(GuardVpnService.ACTION_START); if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i); toast("VPN-контроль включён"); render(); }
    private void stopGuard() { GuardPrefs.enabled(this, false); startService(new Intent(this, GuardVpnService.class).setAction(GuardVpnService.ACTION_STOP)); toast("VPN-контроль остановлен"); render(); }
    private void refreshGuard() { if (!GuardPrefs.enabled(this)) return; Intent i = new Intent(this, GuardVpnService.class).setAction(GuardVpnService.ACTION_REFRESH); if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i); }

    private void renderNav() { nav.removeAllViews(); nav.addView(navButton(YuroIcon.SHIELD,0,"Защита"), new LinearLayout.LayoutParams(0,-1,1)); nav.addView(navButton(YuroIcon.APPS,1,"Приложения"), new LinearLayout.LayoutParams(0,-1,1)); nav.addView(navButton(YuroIcon.CHART,2,"Отчёт"), new LinearLayout.LayoutParams(0,-1,1)); nav.addView(navButton(YuroIcon.BROWSER,3,"Браузер"), new LinearLayout.LayoutParams(0,-1,1)); }
    private View navButton(int iconType, int idx, String desc) {
        FrameLayout box = new FrameLayout(this); box.setContentDescription(desc); box.setPadding(dp(10), dp(6), dp(10), dp(6)); box.setBackground(shape(tab == idx ? PURPLE : 0x00101010, 20));
        YuroIcon icon = new YuroIcon(this, iconType); icon.setColor(tab == idx ? Color.WHITE : MUTED);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER); box.addView(icon, ip);
        box.setOnClickListener(x -> { tab = idx; render(); });
        return box;
    }

    private class AppAdapter extends BaseAdapter {
        private final ArrayList<AppEntry> rows = new ArrayList<>();
        void setRows(List<AppEntry> list) { rows.clear(); if (list != null) rows.addAll(list); notifyDataSetChanged(); }
        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int p) { return rows.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public View getView(int pos, View convertView, ViewGroup parent) {
            AppEntry e = rows.get(pos); LinearLayout outer = row(); outer.setGravity(Gravity.CENTER_VERTICAL); outer.setPadding(dp(12), dp(10), dp(10), dp(10)); outer.setBackground(shape(CARD, 16));
            ImageView icon = new ImageView(MainActivity.this); if (e.icon != null) icon.setImageDrawable(e.icon); outer.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));
            LinearLayout texts = column(); texts.setPadding(dp(10), 0, dp(8), 0); TextView name = text(e.label, 14, true, TEXT); name.setSingleLine(true); texts.addView(name); texts.addView(text((e.system ? "Системное · " : "") + e.pkg, 10, false, MUTED)); texts.addView(text("Трафик " + fmt(e.total()) + " · сейчас " + rate(e.speed()), 10, false, e.speed() > 0 ? CYAN : MUTED)); outer.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
            CheckBox cb = new CheckBox(MainActivity.this); cb.setButtonTintList(android.content.res.ColorStateList.valueOf(PURPLE)); cb.setChecked(e.selected); cb.setFocusable(false); cb.setClickable(false); outer.addView(cb);
            outer.setOnClickListener(v -> { boolean on = !e.selected; e.selected = on; GuardPrefs.setSelected(MainActivity.this, e.pkg, on); notifyDataSetChanged(); refreshGuard(); });
            LinearLayout wrap = column(); wrap.setPadding(0,0,0,dp(8)); wrap.addView(outer, new LinearLayout.LayoutParams(-1,-2)); return wrap;
        }
    }

    private LinearLayout scroll() { ScrollView sc = new ScrollView(this); currentScroll = sc; currentList = null; sc.setFillViewport(true); sc.setVerticalScrollBarEnabled(false); sc.setBackgroundColor(BG); LinearLayout col = column(); col.setPadding(dp(14), dp(12), dp(14), dp(22)); sc.addView(col, new ScrollView.LayoutParams(-1,-2)); content.addView(sc, new FrameLayout.LayoutParams(-1,-1)); return col; }
    private void header(LinearLayout col, String title, String sub) { col.addView(text(title, 30, true, TEXT)); space(col, 4); col.addView(text(sub, 12, false, MUTED)); space(col, 16); }
    private void section(LinearLayout col, String s) { space(col, 8); col.addView(text(s, 18, true, TEXT)); space(col, 10); }
    private void infoCard(LinearLayout col, String title, String body) { LinearLayout c = card(); c.addView(text(title, 17, true, TEXT)); space(c, 8); c.addView(text(body, 12, false, MUTED)); col.addView(c, mlp(-1, -2, 0, 14, 0, 0)); }
    private TextView empty(String s) { TextView v = text(s, 13, false, MUTED); v.setGravity(Gravity.CENTER); v.setPadding(dp(12), dp(20), dp(12), dp(20)); v.setBackground(shape(CARD, 16)); return v; }
    private LinearLayout card() { LinearLayout l = column(); l.setPadding(dp(16), dp(16), dp(16), dp(16)); l.setBackground(shape(CARD, 20)); return l; }
    private TextView label(String s) { TextView v = text(s, 11, true, CYAN); v.setLetterSpacing(0.08f); return v; }
    private TextView chip(String title, boolean active, Runnable click) { TextView v = text(title, 12, true, active ? Color.WHITE : TEXT); v.setGravity(Gravity.CENTER); v.setPadding(dp(14), 0, dp(14), 0); GradientDrawable gd = shape(active ? PURPLE : CARD, 16); gd.setStroke(dp(1), active ? PURPLE : 0x44b56cff); v.setBackground(gd); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(38)); lp.rightMargin = dp(8); v.setLayoutParams(lp); v.setOnClickListener(x -> click.run()); return v; }
    private TextView button(String s, boolean primary, Runnable r) { TextView v = text(s, 14, true, primary ? Color.WHITE : TEXT); v.setGravity(Gravity.CENTER); GradientDrawable gd = shape(primary ? PURPLE : CARD3, 17); gd.setStroke(dp(1), primary ? PURPLE : 0x55b56cff); v.setBackground(gd); v.setOnClickListener(x -> r.run()); return v; }
    private TextView text(String s, int sp, boolean bold, int color) { TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(color); if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); v.setLineSpacing(dp(2), 1.0f); return v; }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private void space(LinearLayout l, int h) { View v = new View(this); l.addView(v, new LinearLayout.LayoutParams(1, dp(h))); }
    private GradientDrawable shape(int color, float r) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(r)); return g; }
    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private LinearLayout.LayoutParams mlp(int w, int h, int l, int t, int r, int b) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w < 0 ? w : dp(w), h < 0 ? h : dp(h)); p.setMargins(dp(l), dp(t), dp(r), dp(b)); return p; }
    private String fmt(long b) { double v = Math.max(0, b); String[] u = {"Б", "КБ", "МБ", "ГБ", "ТБ"}; int i = 0; while (v >= 1024 && i < u.length - 1) { v /= 1024.0; i++; } return i == 0 ? String.format(Locale.getDefault(), "%.0f %s", v, u[i]) : String.format(Locale.getDefault(), "%.1f %s", v, u[i]); }
    private String rate(long b) { return fmt(b) + "/с"; }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
