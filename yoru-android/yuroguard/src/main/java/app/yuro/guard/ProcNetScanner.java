package app.yuro.guard;

import android.content.Context;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Locale;

public final class ProcNetScanner {
    private ProcNetScanner() {}

    public static void scan(Context context, NetDb db) {
        if (db == null || !GuardPrefs.dpiLogging(context)) return;
        try { db.syncPackages(context, false); } catch (Throwable ignored) {}
        read(context, db, "/proc/net/tcp", "TCP4");
        read(context, db, "/proc/net/tcp6", "TCP6");
        read(context, db, "/proc/net/udp", "UDP4");
        read(context, db, "/proc/net/udp6", "UDP6");
    }

    private static void read(Context c, NetDb db, String path, String proto) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line; boolean first = true;
            while ((line = br.readLine()) != null) {
                if (first) { first = false; continue; }
                parse(c, db, line.trim(), proto);
            }
        } catch (Throwable ignored) {}
    }

    private static void parse(Context c, NetDb db, String line, String proto) {
        if (line.length() == 0) return;
        String[] p = line.split("\\s+");
        if (p.length < 8) return;
        String rem = p[2]; String state = p[3];
        String[] hp = rem.split(":"); if (hp.length != 2) return;
        int port = (int)Long.parseLong(hp[1], 16);
        if (port <= 0) return;
        String ip = proto.endsWith("6") ? ipv6(hp[0]) : ipv4(hp[0]);
        if (isZero(ip)) return;
        int uid = -1; try { uid = Integer.parseInt(p[7]); } catch (Throwable ignored) {}
        String host = db.hostForIp(ip);
        boolean media = GuardPrefs.mediaShield(c) && (PacketInspector.isMediaHost(host) || port == 554 || port == 1935);
        db.recordEvent(uid, "", proto.startsWith("TCP") ? "TCP" : "UDP", host, ip, port, "proc/net", media, media ? "media/CDN or streaming port · state " + state : "state " + state);
    }

    private static String ipv4(String hex) {
        try {
            long v = Long.parseLong(hex, 16);
            return (v & 0xff) + "." + ((v >> 8) & 0xff) + "." + ((v >> 16) & 0xff) + "." + ((v >> 24) & 0xff);
        } catch (Throwable e) { return ""; }
    }

    private static String ipv6(String hex) {
        try {
            if (hex.length() < 32) return "";
            StringBuilder out = new StringBuilder();
            for (int i=0;i<32;i+=4) { if (out.length() > 0) out.append(':'); out.append(hex.substring(i, i+4).toLowerCase(Locale.ROOT)); }
            return out.toString();
        } catch (Throwable e) { return ""; }
    }

    private static boolean isZero(String ip) {
        if (ip == null || ip.length() == 0) return true;
        return ip.equals("0.0.0.0") || ip.replace(":", "").replace("0", "").length() == 0;
    }
}
