package app.yuro.guard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import java.util.concurrent.atomic.AtomicBoolean;

public class MonitorService extends Service {
    public static final String ACTION_START = "app.yuro.guard.MONITOR_START";
    public static final String ACTION_STOP = "app.yuro.guard.MONITOR_STOP";
    public static final String CHANNEL = "yuro_guard_monitor";
    private static volatile boolean alive;
    private final AtomicBoolean stopped = new AtomicBoolean(true);
    private Thread worker;
    private NetDb db;
    private PowerManager.WakeLock wakeLock;

    public static boolean isAlive() { return alive; }

    @Override public void onCreate() {
        super.onCreate();
        db = NetDb.get(this);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopMonitor();
            return START_NOT_STICKY;
        }
        GuardPrefs.monitorEnabled(this, true);
        stopped.set(false);
        alive = true;
        startForegroundCompat();
        startLoop();
        return START_STICKY;
    }

    private void startForegroundCompat() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "YURO постоянный мониторинг", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Считает трафик приложений даже когда VPN-фильтр выключен");
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 31, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stop = new Intent(this, MonitorService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 32, stop, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        b.setSmallIcon(app.yuro.guard.R.drawable.ic_stat_guard)
                .setContentTitle("YURO Guard считает трафик")
                .setContentText("Постоянный счётчик активен: приложения, минуты, часы, дни")
                .setContentIntent(pi)
                .setOngoing(true)
                .addAction(app.yuro.guard.R.drawable.ic_stat_guard, "Пауза", stopPi);
        Notification n = b.build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(11, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(11, n);
    }

    private synchronized void startLoop() {
        if (worker != null && worker.isAlive()) return;
        worker = new Thread(() -> {
            while (!stopped.get()) {
                try {
                    db.ensureBaselines();
                    db.syncPackages(this, false);
                    db.sampleAll(this);
                    db.samplePreciseNetworks(this);
                    long now = System.currentTimeMillis();
                    if (now - db.metaLong("last_proc_scan", 0) > 7000L) { ProcNetScanner.scan(this, db); db.putMeta("last_proc_scan", String.valueOf(now)); }
                    nap(2000L);
                } catch (Throwable ignored) {
                    nap(3500L);
                }
            }
        }, "yuro-guard-always-meter");
        worker.start();
    }

    private void nap(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    private synchronized void stopMonitor() {
        GuardPrefs.monitorEnabled(this, false);
        stopped.set(true);
        alive = false;
        Thread w = worker;
        if (w != null) w.interrupt();
        worker = null;
        if (wakeLock != null && wakeLock.isHeld()) try { wakeLock.release(); } catch (Throwable ignored) {}
        wakeLock = null;
        stopForeground(true);
        stopSelf();
    }

    @Override public void onDestroy() {
        stopped.set(true);
        alive = false;
        Thread w = worker;
        if (w != null) w.interrupt();
        if (wakeLock != null && wakeLock.isHeld()) try { wakeLock.release(); } catch (Throwable ignored) {}
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
