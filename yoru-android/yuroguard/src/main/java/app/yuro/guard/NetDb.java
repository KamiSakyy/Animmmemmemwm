package app.yuro.guard;

import android.app.AppOpsManager;
import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.ConnectivityManager;
import android.net.TrafficStats;
import android.os.Process;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class NetDb extends SQLiteOpenHelper {
    private static final String DB = "yuro_guard_stats.db";
    private static final int VER = 2;
    public static final int NET_ALL = 0;
    public static final int NET_MOBILE = 1;
    public static final int NET_WIFI = 2;
    private static volatile NetDb instance;

    public static NetDb get(Context c) {
        if (instance == null) synchronized (NetDb.class) {
            if (instance == null) instance = new NetDb(c.getApplicationContext());
        }
        return instance;
    }

    private NetDb(Context c) { super(c, DB, null, VER); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS meta(k TEXT PRIMARY KEY, v TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS packages(pkg TEXT PRIMARY KEY, uid INTEGER, label TEXT, system INTEGER, updated INTEGER)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_packages_uid ON packages(uid)");
        db.execSQL("CREATE TABLE IF NOT EXISTS uid_totals(uid INTEGER PRIMARY KEY, first_rx INTEGER, first_tx INTEGER, last_rx INTEGER, last_tx INTEGER, total_rx INTEGER, total_tx INTEGER, speed_rx INTEGER, speed_tx INTEGER, updated INTEGER)");
        db.execSQL("CREATE TABLE IF NOT EXISTS samples(uid INTEGER, minute INTEGER, mobile INTEGER, rx INTEGER, tx INTEGER, PRIMARY KEY(uid, minute, mobile))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_samples_time ON samples(minute, mobile)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_samples_uid ON samples(uid, mobile)");
        db.execSQL("CREATE TABLE IF NOT EXISTS net_samples(uid INTEGER, minute INTEGER, net INTEGER, rx INTEGER, tx INTEGER, PRIMARY KEY(uid, minute, net))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_net_samples_time ON net_samples(minute, net)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_net_samples_uid ON net_samples(uid, net)");
        db.execSQL("CREATE TABLE IF NOT EXISTS dns_cache(ip TEXT PRIMARY KEY, host TEXT, updated INTEGER)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dns_cache_host ON dns_cache(host)");
        db.execSQL("CREATE TABLE IF NOT EXISTS net_events(id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER, uid INTEGER, pkg TEXT, proto TEXT, host TEXT, ip TEXT, port INTEGER, kind TEXT, blocked INTEGER, note TEXT)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_net_events_time ON net_events(time DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_net_events_uid ON net_events(uid,time DESC)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { onCreate(db); }

    public synchronized void ensureBaselines() {
        long now = System.currentTimeMillis();
        if (metaLong("started", 0) == 0) {
            putMeta("started", String.valueOf(now));
            putMeta("base_total_rx", String.valueOf(safe(TrafficStats.getTotalRxBytes())));
            putMeta("base_total_tx", String.valueOf(safe(TrafficStats.getTotalTxBytes())));
            putMeta("base_mobile_rx", String.valueOf(safe(TrafficStats.getMobileRxBytes())));
            putMeta("base_mobile_tx", String.valueOf(safe(TrafficStats.getMobileTxBytes())));
            putMeta("last_mobile_poll", String.valueOf(now));
        }
    }

    private static long safe(long v) { return v == TrafficStats.UNSUPPORTED ? 0 : Math.max(0, v); }

    public synchronized long startedAt() {
        ensureBaselines();
        return metaLong("started", System.currentTimeMillis());
    }

    public synchronized long metaLong(String key, long def) {
        Cursor c = getReadableDatabase().rawQuery("SELECT v FROM meta WHERE k=?", new String[]{key});
        try {
            if (c.moveToFirst()) {
                try { return Long.parseLong(c.getString(0)); } catch (Exception ignored) { return def; }
            }
            return def;
        } finally { c.close(); }
    }

    public synchronized void putMeta(String key, String value) {
        ContentValues cv = new ContentValues();
        cv.put("k", key); cv.put("v", value);
        getWritableDatabase().insertWithOnConflict("meta", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized void syncPackages(Context context, boolean force) {
        ensureBaselines();
        long now = System.currentTimeMillis();
        long last = metaLong("last_pkg_sync", 0);
        if (!force && now - last < 60_000) return;
        PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (ApplicationInfo ai : apps) {
                if (ai == null || ai.packageName == null) continue;
                ContentValues cv = new ContentValues();
                cv.put("pkg", ai.packageName);
                cv.put("uid", ai.uid);
                CharSequence label = ai.loadLabel(pm);
                cv.put("label", label == null ? ai.packageName : label.toString());
                cv.put("system", (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0 ? 1 : 0);
                cv.put("updated", now);
                db.insertWithOnConflict("packages", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            }
            ContentValues meta = new ContentValues();
            meta.put("k", "last_pkg_sync"); meta.put("v", String.valueOf(now));
            db.insertWithOnConflict("meta", null, meta, SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    private synchronized Set<Integer> knownUids() {
        HashSet<Integer> set = new HashSet<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT DISTINCT uid FROM packages WHERE uid>=0", null);
        try { while (c.moveToNext()) set.add(c.getInt(0)); }
        finally { c.close(); }
        return set;
    }

    public synchronized void sampleAll(Context context) {
        ensureBaselines();
        syncPackages(context, false);
        long now = System.currentTimeMillis();
        long minute = (now / 60_000L) * 60_000L;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (int uid : knownUids()) {
                long rxNow = TrafficStats.getUidRxBytes(uid);
                long txNow = TrafficStats.getUidTxBytes(uid);
                if (rxNow == TrafficStats.UNSUPPORTED && txNow == TrafficStats.UNSUPPORTED) continue;
                rxNow = safe(rxNow); txNow = safe(txNow);
                Cursor c = db.rawQuery("SELECT last_rx,last_tx,total_rx,total_tx,updated FROM uid_totals WHERE uid=?", new String[]{String.valueOf(uid)});
                long lastRx = rxNow, lastTx = txNow, totalRx = 0, totalTx = 0, updated = now;
                boolean exists = false;
                try {
                    if (c.moveToFirst()) {
                        exists = true;
                        lastRx = c.getLong(0); lastTx = c.getLong(1); totalRx = c.getLong(2); totalTx = c.getLong(3); updated = c.getLong(4);
                    }
                } finally { c.close(); }
                long dRx = exists ? (rxNow >= lastRx ? rxNow - lastRx : rxNow) : 0;
                long dTx = exists ? (txNow >= lastTx ? txNow - lastTx : txNow) : 0;
                long dt = Math.max(1_000L, now - updated);
                long sRx = dRx * 1000L / dt;
                long sTx = dTx * 1000L / dt;
                ContentValues cv = new ContentValues();
                cv.put("uid", uid);
                cv.put("first_rx", exists ? 0 : rxNow);
                cv.put("first_tx", exists ? 0 : txNow);
                cv.put("last_rx", rxNow);
                cv.put("last_tx", txNow);
                cv.put("total_rx", totalRx + dRx);
                cv.put("total_tx", totalTx + dTx);
                cv.put("speed_rx", sRx);
                cv.put("speed_tx", sTx);
                cv.put("updated", now);
                if (exists) {
                    db.update("uid_totals", cv, "uid=?", new String[]{String.valueOf(uid)});
                } else {
                    db.insertWithOnConflict("uid_totals", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                }
                if (dRx > 0 || dTx > 0) addSampleLocked(db, uid, minute, false, dRx, dTx);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public synchronized void sampleMobileSummary(Context context) { samplePreciseNetworks(context); }

    public synchronized void samplePreciseNetworks(Context context) {
        ensureBaselines();
        if (!hasUsageAccess(context)) return;
        long now = System.currentTimeMillis();
        long last = metaLong("last_precise_poll", startedAt());
        if (now <= last || now - last < 45_000L) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            NetworkStatsManager nsm = (NetworkStatsManager) context.getSystemService(Context.NETWORK_STATS_SERVICE);
            if (nsm == null) return;
            long minute = (now / 60_000L) * 60_000L;
            queryNetworkLocked(db, nsm, ConnectivityManager.TYPE_MOBILE, NET_MOBILE, last, now, minute);
            queryNetworkLocked(db, nsm, ConnectivityManager.TYPE_WIFI, NET_WIFI, last, now, minute);
            putMetaLocked(db, "last_precise_poll", String.valueOf(now));
            putMetaLocked(db, "last_mobile_poll", String.valueOf(now));
            db.setTransactionSuccessful();
        } catch (Throwable ignored) {
        } finally {
            db.endTransaction();
        }
    }

    private void queryNetworkLocked(SQLiteDatabase db, NetworkStatsManager nsm, int androidType, int net, long start, long end, long minute) {
        NetworkStats stats = null;
        try {
            stats = nsm.querySummary(androidType, null, start, end);
            NetworkStats.Bucket bucket = new NetworkStats.Bucket();
            while (stats != null && stats.hasNextBucket()) {
                stats.getNextBucket(bucket);
                int uid = bucket.getUid();
                long rx = Math.max(0, bucket.getRxBytes());
                long tx = Math.max(0, bucket.getTxBytes());
                if (uid >= 0 && (rx > 0 || tx > 0)) {
                    addNetSampleLocked(db, uid, minute, net, rx, tx);
                    if (net == NET_MOBILE) addSampleLocked(db, uid, minute, true, rx, tx);
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (stats != null) try { stats.close(); } catch (Exception ignored) {}
        }
    }

    private void putMetaLocked(SQLiteDatabase db, String key, String value) {
        ContentValues cv = new ContentValues(); cv.put("k", key); cv.put("v", value);
        db.insertWithOnConflict("meta", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private void addNetSampleLocked(SQLiteDatabase db, int uid, long minute, int net, long rx, long tx) {
        ContentValues plus = new ContentValues();
        Cursor c = db.rawQuery("SELECT rx,tx FROM net_samples WHERE uid=? AND minute=? AND net=?", new String[]{String.valueOf(uid), String.valueOf(minute), String.valueOf(net)});
        try {
            if (c.moveToFirst()) {
                plus.put("rx", c.getLong(0) + rx);
                plus.put("tx", c.getLong(1) + tx);
                db.update("net_samples", plus, "uid=? AND minute=? AND net=?", new String[]{String.valueOf(uid), String.valueOf(minute), String.valueOf(net)});
            } else {
                plus.put("uid", uid); plus.put("minute", minute); plus.put("net", net); plus.put("rx", rx); plus.put("tx", tx);
                db.insert("net_samples", null, plus);
            }
        } finally { c.close(); }
    }

    private void addSampleLocked(SQLiteDatabase db, int uid, long minute, boolean mobile, long rx, long tx) {
        ContentValues plus = new ContentValues();
        Cursor c = db.rawQuery("SELECT rx,tx FROM samples WHERE uid=? AND minute=? AND mobile=?", new String[]{String.valueOf(uid), String.valueOf(minute), mobile ? "1" : "0"});
        try {
            if (c.moveToFirst()) {
                plus.put("rx", c.getLong(0) + rx);
                plus.put("tx", c.getLong(1) + tx);
                db.update("samples", plus, "uid=? AND minute=? AND mobile=?", new String[]{String.valueOf(uid), String.valueOf(minute), mobile ? "1" : "0"});
            } else {
                plus.put("uid", uid); plus.put("minute", minute); plus.put("mobile", mobile ? 1 : 0); plus.put("rx", rx); plus.put("tx", tx);
                db.insert("samples", null, plus);
            }
        } finally { c.close(); }
    }

    public synchronized List<AppEntry> appsByNet(Context context, boolean includeSystem, String query, int net, int limit) {
        if (net == NET_ALL) return apps(context, includeSystem, query, false, limit);
        ensureBaselines();
        syncPackages(context, false);
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        Set<String> selected = GuardPrefs.selected(context);
        ArrayList<AppEntry> out = new ArrayList<>();
        String sql = "SELECT p.pkg,p.uid,p.label,p.system,COALESCE(n.rx,0),COALESCE(n.tx,0),COALESCE(t.speed_rx,0),COALESCE(t.speed_tx,0) " +
                "FROM packages p LEFT JOIN uid_totals t ON p.uid=t.uid LEFT JOIN (SELECT uid,SUM(rx) rx,SUM(tx) tx FROM net_samples WHERE net=" + net + " GROUP BY uid) n ON p.uid=n.uid " +
                (includeSystem ? "" : "WHERE p.system=0 ") + "ORDER BY (COALESCE(n.rx,0)+COALESCE(n.tx,0)) DESC, lower(p.label) ASC";
        Cursor c = getReadableDatabase().rawQuery(sql, null);
        PackageManager pm = context.getPackageManager();
        try {
            while (c.moveToNext()) {
                AppEntry e = new AppEntry();
                e.pkg = c.getString(0); e.uid = c.getInt(1); e.label = c.getString(2); e.system = c.getInt(3) == 1;
                long rx = c.getLong(4), tx = c.getLong(5);
                if (net == NET_MOBILE) { e.mobileRx = rx; e.mobileTx = tx; } else { e.rx = rx; e.tx = tx; }
                e.speedRx = c.getLong(6); e.speedTx = c.getLong(7); e.selected = selected.contains(e.pkg);
                if (!q.isEmpty()) {
                    String hay = (e.label + " " + e.pkg).toLowerCase(Locale.ROOT);
                    if (!hay.contains(q)) continue;
                }
                try { e.icon = pm.getApplicationIcon(e.pkg); } catch (Exception ignored) {}
                out.add(e);
                if (limit > 0 && out.size() >= limit) break;
            }
        } finally { c.close(); }
        return out;
    }

    public synchronized List<AppEntry> apps(Context context, boolean includeSystem, String query, boolean mobile, int limit) {
        ensureBaselines();
        syncPackages(context, false);
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        Set<String> selected = GuardPrefs.selected(context);
        ArrayList<AppEntry> out = new ArrayList<>();
        String sql;
        if (mobile) {
            sql = "SELECT p.pkg,p.uid,p.label,p.system,0,0,COALESCE(m.rx,0),COALESCE(m.tx,0),COALESCE(t.speed_rx,0),COALESCE(t.speed_tx,0) " +
                    "FROM packages p LEFT JOIN uid_totals t ON p.uid=t.uid LEFT JOIN (SELECT uid,SUM(rx) rx,SUM(tx) tx FROM samples WHERE mobile=1 GROUP BY uid) m ON p.uid=m.uid " +
                    (includeSystem ? "" : "WHERE p.system=0 ") + "ORDER BY (COALESCE(m.rx,0)+COALESCE(m.tx,0)) DESC, lower(p.label) ASC";
        } else {
            sql = "SELECT p.pkg,p.uid,p.label,p.system,COALESCE(t.total_rx,0),COALESCE(t.total_tx,0),0,0,COALESCE(t.speed_rx,0),COALESCE(t.speed_tx,0) " +
                    "FROM packages p LEFT JOIN uid_totals t ON p.uid=t.uid " +
                    (includeSystem ? "" : "WHERE p.system=0 ") + "ORDER BY (COALESCE(t.total_rx,0)+COALESCE(t.total_tx,0)) DESC, lower(p.label) ASC";
        }
        Cursor c = getReadableDatabase().rawQuery(sql, null);
        PackageManager pm = context.getPackageManager();
        try {
            while (c.moveToNext()) {
                AppEntry e = new AppEntry();
                e.pkg = c.getString(0); e.uid = c.getInt(1); e.label = c.getString(2); e.system = c.getInt(3) == 1;
                e.rx = c.getLong(4); e.tx = c.getLong(5); e.mobileRx = c.getLong(6); e.mobileTx = c.getLong(7); e.speedRx = c.getLong(8); e.speedTx = c.getLong(9);
                e.selected = selected.contains(e.pkg);
                if (!q.isEmpty()) {
                    String hay = (e.label + " " + e.pkg).toLowerCase(Locale.ROOT);
                    if (!hay.contains(q)) continue;
                }
                try { e.icon = pm.getApplicationIcon(e.pkg); } catch (Exception ignored) {}
                out.add(e);
                if (limit > 0 && out.size() >= limit) break;
            }
        } finally { c.close(); }
        return out;
    }

    public synchronized List<BucketRow> timelineByNet(int net, String period, int limit) {
        if (net == NET_ALL) return timeline(false, period, limit);
        long div = 60_000L;
        if ("hour".equals(period)) div = 3_600_000L;
        else if ("day".equals(period)) div = 86_400_000L;
        String sql = "SELECT (minute/" + div + ")*" + div + " bucket,SUM(rx),SUM(tx) FROM net_samples WHERE net=? GROUP BY bucket ORDER BY bucket DESC LIMIT ?";
        Cursor c = getReadableDatabase().rawQuery(sql, new String[]{String.valueOf(net), String.valueOf(limit)});
        ArrayList<BucketRow> out = new ArrayList<>();
        try {
            while (c.moveToNext()) {
                BucketRow r = new BucketRow(); r.bucket = c.getLong(0); r.rx = c.getLong(1); r.tx = c.getLong(2); out.add(r);
            }
        } finally { c.close(); }
        return out;
    }

    public synchronized List<BucketRow> timeline(boolean mobile, String period, int limit) {
        long div = 60_000L;
        if ("hour".equals(period)) div = 3_600_000L;
        else if ("day".equals(period)) div = 86_400_000L;
        String sql = "SELECT (minute/" + div + ")*" + div + " bucket,SUM(rx),SUM(tx) FROM samples WHERE mobile=? GROUP BY bucket ORDER BY bucket DESC LIMIT ?";
        Cursor c = getReadableDatabase().rawQuery(sql, new String[]{mobile ? "1" : "0", String.valueOf(limit)});
        ArrayList<BucketRow> out = new ArrayList<>();
        try {
            while (c.moveToNext()) {
                BucketRow r = new BucketRow(); r.bucket = c.getLong(0); r.rx = c.getLong(1); r.tx = c.getLong(2); out.add(r);
            }
        } finally { c.close(); }
        return out;
    }

    public synchronized Totals totals() {
        ensureBaselines();
        Totals t = new Totals();
        long brx = metaLong("base_total_rx", 0), btx = metaLong("base_total_tx", 0);
        long bmrx = metaLong("base_mobile_rx", 0), bmtx = metaLong("base_mobile_tx", 0);
        long crx = safe(TrafficStats.getTotalRxBytes()), ctx = safe(TrafficStats.getTotalTxBytes());
        long cmrx = safe(TrafficStats.getMobileRxBytes()), cmtx = safe(TrafficStats.getMobileTxBytes());
        t.deviceRx = crx >= brx ? crx - brx : crx;
        t.deviceTx = ctx >= btx ? ctx - btx : ctx;
        t.mobileDeviceRx = cmrx >= bmrx ? cmrx - bmrx : cmrx;
        t.mobileDeviceTx = cmtx >= bmtx ? cmtx - bmtx : cmtx;
        Cursor c = getReadableDatabase().rawQuery("SELECT SUM(total_rx),SUM(total_tx),SUM(speed_rx),SUM(speed_tx) FROM uid_totals", null);
        try { if (c.moveToFirst()) { t.uidRx = c.getLong(0); t.uidTx = c.getLong(1); t.speedRx = c.getLong(2); t.speedTx = c.getLong(3); } }
        finally { c.close(); }
        c = getReadableDatabase().rawQuery("SELECT SUM(rx),SUM(tx) FROM net_samples WHERE net=" + NET_MOBILE, null);
        try { if (c.moveToFirst()) { t.mobileRx = c.getLong(0); t.mobileTx = c.getLong(1); } }
        finally { c.close(); }
        c = getReadableDatabase().rawQuery("SELECT SUM(rx),SUM(tx) FROM net_samples WHERE net=" + NET_WIFI, null);
        try { if (c.moveToFirst()) { t.wifiRx = c.getLong(0); t.wifiTx = c.getLong(1); } }
        finally { c.close(); }
        return t;
    }

    public synchronized long totalCurrentSpeed() {
        Cursor c = getReadableDatabase().rawQuery("SELECT SUM(speed_rx)+SUM(speed_tx) FROM uid_totals", null);
        try { return c.moveToFirst() ? Math.max(0, c.getLong(0)) : 0; }
        finally { c.close(); }
    }

    public synchronized long currentSpeedForPackage(String pkg) {
        Cursor c = getReadableDatabase().rawQuery("SELECT COALESCE(t.speed_rx,0)+COALESCE(t.speed_tx,0) FROM packages p LEFT JOIN uid_totals t ON p.uid=t.uid WHERE p.pkg=?", new String[]{pkg});
        try { return c.moveToFirst() ? Math.max(0, c.getLong(0)) : 0; }
        finally { c.close(); }
    }

    public synchronized void reset() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("uid_totals", null, null);
            db.delete("samples", null, null);
            db.delete("net_samples", null, null);
            db.delete("dns_cache", null, null);
            db.delete("net_events", null, null);
            db.delete("meta", null, null);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        ensureBaselines();
    }

    public synchronized void rememberDns(String ip, String host) {
        if (ip == null || host == null || ip.length() == 0 || host.length() == 0) return;
        ContentValues cv = new ContentValues(); cv.put("ip", ip); cv.put("host", host); cv.put("updated", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("dns_cache", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized String hostForIp(String ip) {
        if (ip == null || ip.length() == 0) return "";
        Cursor c = getReadableDatabase().rawQuery("SELECT host FROM dns_cache WHERE ip=?", new String[]{ip});
        try { return c.moveToFirst() ? c.getString(0) : ""; }
        finally { c.close(); }
    }

    public synchronized String packageForUid(int uid) {
        Cursor c = getReadableDatabase().rawQuery("SELECT label,pkg FROM packages WHERE uid=? ORDER BY system ASC, lower(label) ASC LIMIT 1", new String[]{String.valueOf(uid)});
        try { if (c.moveToFirst()) { String label = c.getString(0); String pkg = c.getString(1); return (label == null || label.length()==0 ? pkg : label) + " (" + pkg + ")"; } return ""; }
        finally { c.close(); }
    }

    public synchronized void recordEvent(int uid, String pkg, String proto, String host, String ip, int port, String kind, boolean blocked, String note) {
        if (proto == null) proto = ""; if (host == null) host = ""; if (ip == null) ip = ""; if (kind == null) kind = ""; if (note == null) note = "";
        long now = System.currentTimeMillis();
        String resolvedPkg = pkg == null || pkg.length() == 0 ? (uid >= 0 ? packageForUid(uid) : "") : pkg;
        Cursor dupe = getReadableDatabase().rawQuery("SELECT id FROM net_events WHERE time>? AND uid=? AND proto=? AND host=? AND ip=? AND port=? AND kind=? LIMIT 1", new String[]{String.valueOf(now - 45_000L), String.valueOf(uid), proto, host, ip, String.valueOf(port), kind});
        try { if (dupe.moveToFirst()) return; } finally { dupe.close(); }
        ContentValues cv = new ContentValues(); cv.put("time", now); cv.put("uid", uid); cv.put("pkg", resolvedPkg); cv.put("proto", proto); cv.put("host", host); cv.put("ip", ip); cv.put("port", port); cv.put("kind", kind); cv.put("blocked", blocked ? 1 : 0); cv.put("note", note);
        SQLiteDatabase db = getWritableDatabase(); db.insert("net_events", null, cv);
        db.execSQL("DELETE FROM net_events WHERE id NOT IN (SELECT id FROM net_events ORDER BY time DESC LIMIT 5000)");
        db.execSQL("DELETE FROM dns_cache WHERE updated < " + (now - 7L*24L*3600L*1000L));
    }

    public synchronized List<EventRow> recentEvents(int limit) {
        ArrayList<EventRow> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT time,uid,pkg,proto,host,ip,port,kind,blocked,note FROM net_events ORDER BY time DESC LIMIT ?", new String[]{String.valueOf(Math.max(1, limit))});
        try {
            while (c.moveToNext()) {
                EventRow e = new EventRow(); e.time = c.getLong(0); e.uid = c.getInt(1); e.pkg = c.getString(2); e.proto = c.getString(3); e.host = c.getString(4); e.ip = c.getString(5); e.port = c.getInt(6); e.kind = c.getString(7); e.blocked = c.getInt(8) == 1; e.note = c.getString(9); out.add(e);
            }
        } finally { c.close(); }
        return out;
    }

    public static boolean hasUsageAccess(Context context) {
        try {
            AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            if (appOps == null) return false;
            int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Throwable e) { return false; }
    }

    public static class EventRow { public long time; public int uid; public String pkg, proto, host, ip, kind, note; public int port; public boolean blocked; }

    public static class Totals {
        public long deviceRx, deviceTx;
        public long mobileDeviceRx, mobileDeviceTx;
        public long uidRx, uidTx;
        public long mobileRx, mobileTx;
        public long wifiRx, wifiTx;
        public long speedRx, speedTx;
    }
}
