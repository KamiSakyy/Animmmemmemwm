package app.yuro.guard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class GuardVpnService extends VpnService {
    public static final String ACTION_START = "app.yuro.guard.START";
    public static final String ACTION_STOP = "app.yuro.guard.STOP";
    public static final String ACTION_REFRESH = "app.yuro.guard.REFRESH";
    public static final String CHANNEL = "yuro_guard_vpn";

    private static volatile boolean alive;
    private final AtomicBoolean stopped = new AtomicBoolean(true);
    private ParcelFileDescriptor vpn;
    private Thread drainThread;
    private Thread workerThread;
    private TunBridge bridge;
    private String policyKey = "";
    private NetDb db;

    public static boolean isAlive() { return alive; }

    @Override public void onCreate() {
        super.onCreate();
        db = NetDb.get(this);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopGuard();
            return Service.START_NOT_STICKY;
        }
        GuardPrefs.enabled(this, true);
        stopped.set(false);
        alive = true;
        startForegroundCompat();
        startWorker();
        if (ACTION_REFRESH.equals(action)) policyKey = "force-refresh";
        return Service.START_STICKY;
    }

    private void startForegroundCompat() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "YURO Guard", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Локальный VPN-фильтр и мониторинг трафика");
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stop = new Intent(this, GuardVpnService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 2, stop, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        b.setSmallIcon(app.yuro.guard.R.drawable.ic_stat_guard)
                .setContentTitle("YURO Guard включён")
                .setContentText("Мониторинг, firewall и лимиты работают локально")
                .setContentIntent(pi)
                .setOngoing(true)
                .addAction(app.yuro.guard.R.drawable.ic_stat_guard, "Остановить", stopPi);
        Notification n = b.build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(7, n);
    }

    private synchronized void startWorker() {
        if (workerThread != null && workerThread.isAlive()) return;
        workerThread = new Thread(() -> {
            while (!stopped.get()) {
                try {
                    db.sampleAll(this);
                    db.sampleMobileSummary(this);
                    applyPolicy(computePolicy());
                    Thread.sleep(1000L);
                } catch (InterruptedException e) {
                    return;
                } catch (Throwable ignored) {
                    try { Thread.sleep(1500L); } catch (InterruptedException e) { return; }
                }
            }
        }, "yuro-guard-worker");
        workerThread.start();
    }

    private Policy computePolicy() {
        String mode = GuardPrefs.mode(this);
        Set<String> selected = GuardPrefs.selected(this);
        if (GuardPrefs.dpiBypass(this) && GuardPrefs.dpiVpnBridge(this)) {
            HashSet<String> targets = new HashSet<>(selected);
            if (targets.isEmpty()) targets.addAll(installedTelegramPackages());
            return targets.isEmpty() ? Policy.none() : Policy.bridge(targets, "Telegram VPN DPI bridge");
        }
        long cap = GuardPrefs.capBytes(this);
        if (GuardPrefs.MODE_BLOCK_SELECTED.equals(mode)) {
            return selected.isEmpty() ? Policy.none() : Policy.allowed(selected, "Блок выбранных");
        }
        if (GuardPrefs.MODE_LIMIT_SELECTED.equals(mode)) {
            HashSet<String> over = new HashSet<>();
            for (String pkg : selected) if (db.currentSpeedForPackage(pkg) > cap) over.add(pkg);
            return over.isEmpty() ? Policy.none() : Policy.allowed(over, "Пауза лимита выбранных");
        }
        if (GuardPrefs.MODE_LIMIT_ALL.equals(mode)) {
            if (db.totalCurrentSpeed() <= cap) return Policy.none();
            HashSet<String> except = new HashSet<>(selected);
            except.add(getPackageName());
            return Policy.disallowed(except, "Пауза общего лимита");
        }
        HashSet<String> except = new HashSet<>(selected);
        except.add(getPackageName());
        return Policy.disallowed(except, "Только выбранные онлайн");
    }

    private Set<String> installedTelegramPackages() {
        HashSet<String> out = new HashSet<>();
        String[] pkgs = {"org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram", "org.telegram.plus", "nekox.messenger", "tw.nekomimi.nekogram"};
        PackageManager pm = getPackageManager();
        for (String pkg : pkgs) try { pm.getPackageInfo(pkg, 0); out.add(pkg); } catch (Throwable ignored) {}
        return out;
    }

    private synchronized void applyPolicy(Policy p) {
        String key = p.key();
        if (key.equals(policyKey)) return;
        policyKey = key;
        closeVpn();
        if (p.type == Policy.NONE) return;
        try {
            Builder b = new Builder();
            b.setSession("YURO Guard");
            b.setMtu(1500);
            b.addAddress("10.77.0.2", 32);
            b.addRoute("0.0.0.0", 0);
            try {
                b.addAddress("fd00:77:77::2", 128);
                b.addRoute("::", 0);
            } catch (Throwable ignored) {}
            try { b.addDnsServer("1.1.1.1"); b.addDnsServer("8.8.8.8"); } catch (Throwable ignored) {}
            if (Build.VERSION.SDK_INT >= 29) b.setMetered(false);
            int added = 0;
            PackageManager pm = getPackageManager();
            List<String> list = new ArrayList<>(p.packages);
            Collections.sort(list);
            for (String pkg : list) {
                try {
                    pm.getPackageInfo(pkg, 0);
                    if (p.type == Policy.ALLOWED || p.type == Policy.BRIDGE) b.addAllowedApplication(pkg);
                    else b.addDisallowedApplication(pkg);
                    added++;
                } catch (PackageManager.NameNotFoundException ignored) {}
            }
            if ((p.type == Policy.ALLOWED || p.type == Policy.BRIDGE) && added == 0) return;
            vpn = b.establish();
            if (vpn != null) {
                if (p.type == Policy.BRIDGE) startBridge(vpn);
                else startDrain(vpn);
            }
        } catch (Throwable ignored) {
            closeVpn();
        }
    }

    private void startBridge(ParcelFileDescriptor fd) {
        bridge = new TunBridge(this, fd, db);
        bridge.start();
    }

    private void startDrain(ParcelFileDescriptor fd) {
        drainThread = new Thread(() -> {
            byte[] buf = new byte[32767];
            try (FileInputStream in = new FileInputStream(fd.getFileDescriptor())) {
                while (!stopped.get() && vpn == fd) {
                    int n = in.read(buf);
                    if (n < 0) break;
                    PacketInspector.inspect(this, buf, n, db);
                }
            } catch (Throwable ignored) {}
        }, "yuro-guard-vpn-drop");
        drainThread.start();
    }

    private synchronized void closeVpn() {
        TunBridge oldBridge = bridge; bridge = null;
        if (oldBridge != null) try { oldBridge.stop(); } catch (Exception ignored) {}
        ParcelFileDescriptor old = vpn;
        vpn = null;
        if (old != null) try { old.close(); } catch (Exception ignored) {}
        Thread t = drainThread;
        if (t != null) t.interrupt();
        drainThread = null;
    }

    private synchronized void stopGuard() {
        GuardPrefs.enabled(this, false);
        stopped.set(true);
        alive = false;
        closeVpn();
        Thread w = workerThread;
        if (w != null) w.interrupt();
        workerThread = null;
        policyKey = "";
        stopForeground(true);
        stopSelf();
    }

    @Override public void onRevoke() { stopGuard(); }
    @Override public void onDestroy() { stopGuard(); super.onDestroy(); }

    static class Policy {
        static final int NONE = 0, ALLOWED = 1, DISALLOWED = 2, BRIDGE = 3;
        final int type; final Set<String> packages; final String title;
        Policy(int type, Set<String> packages, String title) { this.type = type; this.packages = packages == null ? new HashSet<>() : packages; this.title = title; }
        static Policy none() { return new Policy(NONE, new HashSet<>(), "Мониторинг"); }
        static Policy allowed(Set<String> pkgs, String title) { return new Policy(ALLOWED, new HashSet<>(pkgs), title); }
        static Policy disallowed(Set<String> pkgs, String title) { return new Policy(DISALLOWED, new HashSet<>(pkgs), title); }
        static Policy bridge(Set<String> pkgs, String title) { return new Policy(BRIDGE, new HashSet<>(pkgs), title); }
        String key() {
            ArrayList<String> l = new ArrayList<>(packages); Collections.sort(l);
            return type + ":" + title + ":" + l.toString();
        }
    }
}
