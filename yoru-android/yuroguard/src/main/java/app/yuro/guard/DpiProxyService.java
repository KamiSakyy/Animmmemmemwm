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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class DpiProxyService extends Service {
    public static final String ACTION_START = "app.yuro.guard.DPI_PROXY_START";
    public static final String ACTION_STOP = "app.yuro.guard.DPI_PROXY_STOP";
    public static final String CHANNEL = "yuro_guard_dpi_proxy";
    private static volatile boolean alive;
    private final AtomicBoolean stopped = new AtomicBoolean(true);
    private final AtomicInteger strategyRound = new AtomicInteger();
    private ExecutorService pool;
    private Thread acceptThread;
    private ServerSocket server;
    private NetDb db;

    public static boolean isAlive() { return alive; }

    @Override public void onCreate() { super.onCreate(); db = NetDb.get(this); pool = Executors.newCachedThreadPool(); }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) { stopProxy(); return START_NOT_STICKY; }
        GuardPrefs.dpiProxyEnabled(this, true);
        GuardPrefs.dpiBypass(this, true);
        stopped.set(false); alive = true;
        startForegroundCompat(); startAccept();
        return START_STICKY;
    }

    private void startForegroundCompat() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "YURO Telegram DPI Rescue", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Локальный SOCKS5 proxy с adaptive DPI split для Telegram");
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 41, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stop = new Intent(this, DpiProxyService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 42, stop, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        b.setSmallIcon(app.yuro.guard.R.drawable.ic_stat_guard)
                .setContentTitle("YURO Telegram Rescue активен")
                .setContentText("SOCKS5 127.0.0.1:" + GuardPrefs.dpiProxyPort(this) + " · adaptive split")
                .setContentIntent(pi)
                .setOngoing(true)
                .addAction(app.yuro.guard.R.drawable.ic_stat_guard, "Остановить", stopPi);
        Notification n = b.build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(17, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(17, n);
    }

    private synchronized void startAccept() {
        if (acceptThread != null && acceptThread.isAlive()) return;
        acceptThread = new Thread(() -> {
            try {
                int port = GuardPrefs.dpiProxyPort(this);
                server = new ServerSocket();
                server.setReuseAddress(true);
                server.bind(new InetSocketAddress("127.0.0.1", port));
                db.recordEvent(-1, "YURO Guard", "SOCKS5", "127.0.0.1", "127.0.0.1", port, "proxy", false, "Telegram Rescue proxy started");
                while (!stopped.get()) {
                    Socket client = server.accept();
                    client.setTcpNoDelay(true);
                    client.setSoTimeout(120000);
                    pool.execute(() -> handle(client));
                }
            } catch (Throwable e) {
                if (!stopped.get()) db.recordEvent(-1, "YURO Guard", "SOCKS5", "127.0.0.1", "127.0.0.1", GuardPrefs.dpiProxyPort(this), "proxy error", true, String.valueOf(e.getClass().getSimpleName()));
            }
        }, "yuro-dpi-socks5-accept");
        acceptThread.start();
    }

    private void handle(Socket client) {
        Socket remote = null;
        String host = "";
        int port = 0;
        try {
            InputStream in = client.getInputStream();
            OutputStream out = client.getOutputStream();
            int v = in.read(); if (v != 5) throw new IOException("not socks5");
            int n = in.read(); if (n < 0) throw new EOFException();
            readFully(in, new byte[n], 0, n);
            out.write(new byte[]{0x05,0x00}); out.flush();
            int ver = in.read(), cmd = in.read(); in.read(); int atyp = in.read();
            if (ver != 5 || cmd != 1) { fail(out); return; }
            if (atyp == 1) { byte[] a = new byte[4]; readFully(in,a,0,4); host = (a[0]&255)+"."+(a[1]&255)+"."+(a[2]&255)+"."+(a[3]&255); }
            else if (atyp == 3) { int l = in.read(); byte[] a = new byte[l]; readFully(in,a,0,l); host = new String(a, StandardCharsets.UTF_8); }
            else if (atyp == 4) { byte[] a = new byte[16]; readFully(in,a,0,16); host = InetAddress.getByAddress(a).getHostAddress(); }
            else { fail(out); return; }
            port = (in.read() << 8) | in.read();
            remote = new Socket();
            remote.setTcpNoDelay(true);
            remote.setKeepAlive(true);
            remote.setSendBufferSize(1024);
            remote.connect(new InetSocketAddress(host, port), 9000);
            String remoteIp = remote.getInetAddress() == null ? host : remote.getInetAddress().getHostAddress();
            if (!remoteIp.equals(host)) db.rememberDns(remoteIp, host);
            out.write(new byte[]{0x05,0x00,0x00,0x01,0,0,0,0,0,0}); out.flush();
            db.recordEvent(-1, "YURO SOCKS5", "SOCKS5", host, remoteIp, port, "connect", false, "auto strategy · Telegram/DPI rescue");
            Socket finalRemote = remote;
            String fHost = host; int fPort = port;
            pool.execute(() -> pipe(finalRemote, client, false, fHost, fPort));
            pipe(client, remote, true, host, port);
        } catch (Throwable e) {
            db.recordEvent(-1, "YURO SOCKS5", "SOCKS5", host, host, port, "connect error", true, e.getClass().getSimpleName());
        } finally { close(client); close(remote); }
    }

    private void pipe(Socket from, Socket to, boolean outbound, String host, int port) {
        byte[] buf = new byte[8192]; boolean first = true; int strategy = nextStrategy();
        try {
            InputStream in = from.getInputStream(); OutputStream out = to.getOutputStream();
            while (!stopped.get()) {
                int n = in.read(buf); if (n < 0) break;
                if (outbound && first && GuardPrefs.dpiBypass(this)) { first = false; sendAdaptive(out, buf, n, host, port, strategy); }
                else { out.write(buf,0,n); out.flush(); }
            }
        } catch (Throwable ignored) {}
    }

    private int nextStrategy() {
        int level = GuardPrefs.dpiAutoLevel(this);
        if (level <= 0) return 0;
        int max = level >= 4 ? 5 : level >= 3 ? 4 : 3;
        return Math.abs(strategyRound.getAndIncrement()) % max;
    }

    private void sendAdaptive(OutputStream out, byte[] data, int len, String host, int port, int strategy) throws IOException {
        int sni = findSniOffset(data, len);
        int first = 2;
        if (strategy == 1) first = sni > 8 ? Math.max(1, sni - 1) : Math.min(5, Math.max(1, len/3));
        else if (strategy == 2) first = Math.min(17, Math.max(1, len/4));
        else if (strategy == 3) first = Math.min(1, len);
        else if (strategy == 4) first = Math.min(31, Math.max(1, len/5));
        if (len <= 3 || strategy == 0) { out.write(data,0,len); out.flush(); return; }
        db.recordEvent(-1, "YURO SOCKS5", "DPI", host, host, port, "adaptive split", false, strategyName(strategy) + (sni > 0 ? " · SNI offset " + sni : " · first payload " + len + "b"));
        if (strategy == 3) {
            for (int p=0;p<len;p++) { out.write(data,p,1); out.flush(); sleep(3); }
            return;
        }
        first = Math.max(1, Math.min(first, len-1));
        out.write(data,0,first); out.flush(); sleep(strategy >= 2 ? 28 : 10);
        int second = strategy >= 4 ? Math.min(3, len-first) : Math.min(9, len-first);
        if (second > 0) { out.write(data, first, second); out.flush(); sleep(strategy >= 2 ? 35 : 12); }
        if (first + second < len) { out.write(data, first + second, len - first - second); out.flush(); }
    }

    private String strategyName(int s) {
        if (s == 1) return "SNI split";
        if (s == 2) return "delayed disorder-safe split";
        if (s == 3) return "micro TCP split";
        if (s == 4) return "aggressive first-flight split";
        return "plain fallback";
    }

    private int findSniOffset(byte[] p, int len) {
        try {
            if (len < 9 || (p[0] & 0xff) != 22) return -1;
            int hs = 5;
            if ((p[hs] & 0xff) != 1) return -1;
            int pos = hs + 4 + 2 + 32;
            int sid = p[pos++] & 0xff; pos += sid;
            int cipher = u16(p,pos); pos += 2 + cipher;
            int comp = p[pos++] & 0xff; pos += comp;
            int extEnd = Math.min(len, pos + 2 + u16(p,pos)); pos += 2;
            while (pos + 4 <= extEnd) {
                int type = u16(p,pos), elen = u16(p,pos+2); pos += 4;
                if (type == 0 && pos + 5 <= extEnd) {
                    int npos = pos + 2;
                    int nt = p[npos++] & 0xff; int nl = u16(p,npos); npos += 2;
                    if (nt == 0 && nl > 0 && npos + nl <= len) return npos;
                }
                pos += elen;
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    private static int u16(byte[] p, int off) { return ((p[off] & 255) << 8) | (p[off+1] & 255); }
    private void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } }
    private void readFully(InputStream in, byte[] b, int off, int len) throws IOException { int r; while (len > 0 && (r = in.read(b, off, len)) >= 0) { off += r; len -= r; } if (len > 0) throw new EOFException(); }
    private void fail(OutputStream out) { try { out.write(new byte[]{0x05,0x07,0x00,0x01,0,0,0,0,0,0}); out.flush(); } catch (Throwable ignored) {} }
    private void close(Socket s) { if (s != null) try { s.close(); } catch (Throwable ignored) {} }

    private synchronized void stopProxy() {
        GuardPrefs.dpiProxyEnabled(this, false);
        stopped.set(true); alive = false;
        try { if (server != null) server.close(); } catch (Throwable ignored) {}
        server = null;
        if (acceptThread != null) acceptThread.interrupt(); acceptThread = null;
        if (pool != null) pool.shutdownNow(); pool = Executors.newCachedThreadPool();
        stopForeground(true); stopSelf();
    }

    @Override public void onDestroy() { stopped.set(true); alive = false; try { if (server != null) server.close(); } catch (Throwable ignored) {} if (pool != null) pool.shutdownNow(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
