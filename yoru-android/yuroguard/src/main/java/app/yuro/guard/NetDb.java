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
    private static final int VER = 1;
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

    public synchronized void sampleMobileSummary(Context context) {
        ensureBaselines();
        if (!hasUsageAccess(context)) return;
        long now = System.currentTimeMillis();
        long last = metaLong("last_mobile_poll", startedAt());
        if (now - last < 45_000L) return;
        NetworkStats stats = null;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            NetworkStatsManager nsm = (NetworkStatsManager) context.getSystemService(Context.NETWORK_STATS_SERVICE);
            if (nsm == null) return;
            stats = nsm.querySummary(ConnectivityManager.TYPE_MOBILE, null, last, now);
            NetworkStats.Bucket bucket = new NetworkStats.Bucket();
            long minute = (now / 60_000L) * 60_000L;
            while (stats != null && stats.hasNextBucket()) {
                stats.getNextBucket(bucket);
                int uid = bucket.getUid();
                long rx = Math.max(0, bucket.getRxBytes());
                long tx = Math.max(0, bucket.getTxBytes());
                if (uid >= 0 && (rx > 0 || tx > 0)) addSampleLocked(db, uid, minute, true, rx, tx);
            }
            putMetaLocked(db, "last_mobile_poll", String.valueOf(now));
            db.setTransactionSuccessful();
        } catch (Throwable ignored) {
        } finally {
            if (stats != null) try { stats.close(); } catch (Exception ignored) {}
            db.endTransaction();
        }
    }

    private void putMetaLocked(SQLiteDatabase db, String key, String value) {
        ContentValues cv = new ContentValues(); cv.put("k", key); cv.put("v", value);
        db.insertWithOnConflict("meta", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
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
        c = getReadableDatabase().rawQuery("SELECT SUM(rx),SUM(tx) FROM samples WHERE mobile=1", null);
        try { if (c.moveToFirst()) { t.mobileRx = c.getLong(0); t.mobileTx = c.getLong(1); } }
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
            db.delete("meta", null, null);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        ensureBaselines();
    }

    public static boolean hasUsageAccess(Context context) {
        try {
            AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            if (appOps == null) return false;
            int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Throwable e) { return false; }
    }

    public static class Totals {
        public long deviceRx, deviceTx;
        public long mobileDeviceRx, mobileDeviceTx;
        public long uidRx, uidTx;
        public long mobileRx, mobileTx;
        public long speedRx, speedTx;
    }
}
