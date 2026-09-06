package app.yuro.guard;

import android.content.Context;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class PacketInspector {
    private PacketInspector() {}

    public static void inspect(Context context, byte[] packet, int len, NetDb db) {
        if (packet == null || db == null || len < 20 || !GuardPrefs.dpiLogging(context)) return;
        try {
            int version = (packet[0] >> 4) & 0x0f;
            if (version == 4) inspectIpv4(context, packet, len, db);
            else if (version == 6) inspectIpv6(context, packet, len, db);
        } catch (Throwable ignored) {}
    }

    private static void inspectIpv4(Context context, byte[] p, int len, NetDb db) {
        int ihl = (p[0] & 0x0f) * 4;
        if (ihl < 20 || len < ihl + 4) return;
        int proto = p[9] & 0xff;
        String src = ipv4(p, 12), dst = ipv4(p, 16);
        if (proto == 17) inspectUdp(context, p, len, ihl, src, dst, db);
        else if (proto == 6) inspectTcp(context, p, len, ihl, src, dst, db);
        else db.recordEvent(-1, "", "IP" + proto, "", dst, 0, "packet", false, "IPv4");
    }

    private static void inspectIpv6(Context context, byte[] p, int len, NetDb db) {
        if (len < 44) return;
        int proto = p[6] & 0xff;
        String src = ipv6(p, 8), dst = ipv6(p, 24);
        int off = 40;
        if (proto == 17) inspectUdp(context, p, len, off, src, dst, db);
        else if (proto == 6) inspectTcp(context, p, len, off, src, dst, db);
        else db.recordEvent(-1, "", "IP" + proto, "", dst, 0, "packet", false, "IPv6");
    }

    private static void inspectUdp(Context context, byte[] p, int len, int off, String src, String dst, NetDb db) {
        if (len < off + 8) return;
        int sport = u16(p, off), dport = u16(p, off + 2);
        int payload = off + 8;
        if (sport == 53 || dport == 53) inspectDns(context, p, len, payload, sport == 53, sport == 53 ? src : dst, db);
        else db.recordEvent(-1, "", "UDP", db.hostForIp(dst), dst, dport, "UDP", false, "packet capture");
    }

    private static void inspectTcp(Context context, byte[] p, int len, int off, String src, String dst, NetDb db) {
        if (len < off + 20) return;
        int sport = u16(p, off), dport = u16(p, off + 2);
        int dataOff = ((p[off + 12] >> 4) & 0x0f) * 4;
        int flags = p[off + 13] & 0xff;
        int payload = off + dataOff;
        boolean syn = (flags & 0x02) != 0;
        if (payload < len) {
            if (dport == 80 || sport == 80) {
                if (inspectHttp(context, p, len, payload, dst, dport, db)) return;
            }
            if (dport == 443) {
                String sni = tlsSni(p, payload, len);
                if (sni.length() > 0) {
                    boolean media = GuardPrefs.mediaShield(context) && isMediaHost(sni);
                    db.recordEvent(-1, "", "TLS", sni, dst, dport, "SNI", media, media ? "media/CDN domain" : "TLS ClientHello");
                    return;
                }
            }
        }
        if (syn) {
            String host = db.hostForIp(dst);
            boolean media = GuardPrefs.mediaShield(context) && isMediaHost(host);
            db.recordEvent(-1, "", "TCP", host, dst, dport, "TCP", media, media ? "media/CDN target" : "SYN");
        }
    }

    private static boolean inspectHttp(Context context, byte[] p, int len, int payload, String dst, int dport, NetDb db) {
        int count = Math.min(3072, len - payload);
        if (count <= 0) return false;
        String s = new String(p, payload, count, StandardCharsets.ISO_8859_1);
        String up = s.toUpperCase(Locale.ROOT);
        if (!(up.startsWith("GET ") || up.startsWith("POST ") || up.startsWith("HEAD ") || up.startsWith("PUT ") || up.startsWith("DELETE ") || up.startsWith("PATCH ") || up.startsWith("OPTIONS "))) return false;
        String first = line(s, 0);
        String host = header(s, "Host");
        String path = pathFromRequest(first);
        boolean media = GuardPrefs.mediaShield(context) && (isMediaHost(host) || isMediaPath(path) || acceptMedia(s));
        db.recordEvent(-1, "", "HTTP", host, dst, dport, "HTTP", media, media ? "media URL/header blocked by policy" : first);
        return true;
    }

    private static void inspectDns(Context context, byte[] p, int len, int off, boolean response, String serverIp, NetDb db) {
        if (len < off + 12) return;
        int qd = u16(p, off + 4), an = u16(p, off + 6);
        int pos = off + 12;
        String lastHost = "";
        for (int i=0;i<qd && pos < len;i++) {
            Name n = readName(p, len, pos, 0);
            if (n.name.length() == 0) return;
            lastHost = n.name;
            pos = n.next + 4;
            boolean media = GuardPrefs.mediaShield(context) && isMediaHost(lastHost);
            db.recordEvent(-1, "", "DNS", lastHost, serverIp, 53, response ? "DNS response" : "DNS query", media, media ? "media/CDN DNS" : "domain lookup");
        }
        if (response) {
            for (int i=0;i<an && pos + 10 <= len;i++) {
                Name name = readName(p, len, pos, 0);
                pos = name.next;
                if (pos + 10 > len) return;
                int type = u16(p, pos); int rdlen = u16(p, pos + 8); pos += 10;
                if (pos + rdlen > len) return;
                String host = name.name.length() > 0 ? name.name : lastHost;
                if (type == 1 && rdlen == 4) db.rememberDns(ipv4(p, pos), host);
                else if (type == 28 && rdlen == 16) db.rememberDns(ipv6(p, pos), host);
                pos += rdlen;
            }
        }
    }

    private static String tlsSni(byte[] p, int off, int len) {
        try {
            if (len < off + 9 || (p[off] & 0xff) != 22) return "";
            int recordLen = u16(p, off + 3);
            int hs = off + 5;
            if (len < hs + 4 || (p[hs] & 0xff) != 1) return "";
            int pos = hs + 4 + 2 + 32;
            if (pos >= len) return "";
            int sid = p[pos++] & 0xff; pos += sid;
            if (pos + 2 > len) return "";
            int cipherLen = u16(p, pos); pos += 2 + cipherLen;
            if (pos >= len) return "";
            int compLen = p[pos++] & 0xff; pos += compLen;
            if (pos + 2 > len) return "";
            int extEnd = Math.min(len, pos + 2 + u16(p, pos)); pos += 2;
            while (pos + 4 <= extEnd) {
                int type = u16(p, pos), elen = u16(p, pos + 2); pos += 4;
                if (pos + elen > extEnd) return "";
                if (type == 0 && pos + 5 <= extEnd) {
                    int listEnd = pos + 2 + u16(p, pos); int npos = pos + 2;
                    while (npos + 3 <= listEnd && npos + 3 <= len) {
                        int nt = p[npos++] & 0xff; int nl = u16(p, npos); npos += 2;
                        if (nt == 0 && npos + nl <= len) return new String(p, npos, nl, StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT);
                        npos += nl;
                    }
                }
                pos += elen;
            }
        } catch (Throwable ignored) {}
        return "";
    }

    public static boolean isMediaHost(String h) {
        if (h == null) return false;
        String s = h.toLowerCase(Locale.ROOT);
        return s.contains("googleusercontent.com") || s.contains("fbcdn.net") || s.contains("twimg.com") || s.contains("ytimg.com") || s.contains("instagram.com") || s.contains("cdninstagram.com") || s.contains("tiktokcdn.com") || s.contains("snapchat.com") || s.contains("cloudfront.net") || s.contains("akamaihd.net") || s.contains("fastly.net") || s.contains("video") || s.contains("images") || s.contains("img.") || s.contains("cdn.");
    }

    public static boolean isMediaPath(String path) {
        if (path == null) return false;
        String s = path.toLowerCase(Locale.ROOT).split("\\?")[0];
        return s.endsWith(".jpg") || s.endsWith(".jpeg") || s.endsWith(".png") || s.endsWith(".gif") || s.endsWith(".webp") || s.endsWith(".avif") || s.endsWith(".svg") || s.endsWith(".mp4") || s.endsWith(".webm") || s.endsWith(".m4v") || s.endsWith(".mov") || s.endsWith(".mp3") || s.endsWith(".m4a") || s.endsWith(".ogg") || s.endsWith(".flac") || s.endsWith(".m3u8") || s.endsWith(".ts") || s.endsWith(".woff") || s.endsWith(".woff2") || s.endsWith(".ttf") || s.endsWith(".otf");
    }

    private static boolean acceptMedia(String headers) {
        String a = header(headers, "Accept").toLowerCase(Locale.ROOT);
        return a.startsWith("image/") || a.startsWith("video/") || a.startsWith("audio/") || a.contains("image/") || a.contains("video/") || a.contains("audio/");
    }

    private static String header(String s, String key) {
        String[] lines = s.split("\\r?\\n");
        String pref = key.toLowerCase(Locale.ROOT) + ":";
        for (String l : lines) if (l.toLowerCase(Locale.ROOT).startsWith(pref)) return l.substring(l.indexOf(':') + 1).trim();
        return "";
    }
    private static String line(String s, int idx) { String[] lines = s.split("\\r?\\n"); return idx >=0 && idx < lines.length ? lines[idx] : ""; }
    private static String pathFromRequest(String first) { try { String[] p = first.split(" "); return p.length > 1 ? p[1] : ""; } catch (Throwable e) { return ""; } }
    private static int u16(byte[] p, int off) { return ((p[off] & 0xff) << 8) | (p[off+1] & 0xff); }
    private static String ipv4(byte[] p, int off) { return (p[off] & 0xff) + "." + (p[off+1] & 0xff) + "." + (p[off+2] & 0xff) + "." + (p[off+3] & 0xff); }
    private static String ipv6(byte[] p, int off) { StringBuilder sb = new StringBuilder(); for (int i=0;i<16;i+=2) { if (i>0) sb.append(':'); sb.append(Integer.toHexString(((p[off+i]&0xff)<<8)|(p[off+i+1]&0xff))); } return sb.toString(); }

    private static Name readName(byte[] p, int len, int pos, int depth) {
        StringBuilder sb = new StringBuilder(); int start = pos; boolean jumped = false; int next = pos;
        while (pos < len && depth < 8) {
            int l = p[pos] & 0xff;
            if (l == 0) { pos++; if (!jumped) next = pos; break; }
            if ((l & 0xc0) == 0xc0) { if (pos + 1 >= len) break; int ptr = ((l & 0x3f) << 8) | (p[pos+1] & 0xff); Name n = readName(p, len, ptr, depth + 1); if (sb.length() > 0 && n.name.length() > 0) sb.append('.'); sb.append(n.name); pos += 2; if (!jumped) next = pos; jumped = true; break; }
            pos++; if (pos + l > len) break; if (sb.length() > 0) sb.append('.'); sb.append(new String(p, pos, l, StandardCharsets.US_ASCII)); pos += l; if (!jumped) next = pos;
        }
        Name n = new Name(); n.name = sb.toString().toLowerCase(Locale.ROOT); n.next = jumped ? next : Math.max(next, pos); if (n.next <= start) n.next = start + 1; return n;
    }
    private static class Name { String name = ""; int next; }
}
