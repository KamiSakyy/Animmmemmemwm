package app.yuro.guard;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class GuardPrefs {
    private GuardPrefs() {}

    public static final String PREFS = "yuro_guard";
    public static final String MODE_ALLOW_ONLY = "allow_only";
    public static final String MODE_BLOCK_SELECTED = "block_selected";
    public static final String MODE_LIMIT_SELECTED = "limit_selected";
    public static final String MODE_LIMIT_ALL = "limit_all";

    static SharedPreferences sp(Context c) { return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    public static boolean enabled(Context c) { return sp(c).getBoolean("enabled", false); }
    public static void enabled(Context c, boolean v) { sp(c).edit().putBoolean("enabled", v).apply(); }

    public static String mode(Context c) { return sp(c).getString("mode", MODE_ALLOW_ONLY); }
    public static void mode(Context c, String v) { sp(c).edit().putString("mode", v).apply(); }

    public static boolean includeSystem(Context c) { return sp(c).getBoolean("include_system", false); }
    public static void includeSystem(Context c, boolean v) { sp(c).edit().putBoolean("include_system", v).apply(); }

    public static boolean mobileReport(Context c) { return sp(c).getBoolean("mobile_report", true); }
    public static void mobileReport(Context c, boolean v) { sp(c).edit().putBoolean("mobile_report", v).apply(); }

    public static boolean browserSaver(Context c) { return sp(c).getBoolean("browser_saver", true); }
    public static void browserSaver(Context c, boolean v) { sp(c).edit().putBoolean("browser_saver", v).apply(); }

    public static boolean monitorEnabled(Context c) { return sp(c).getBoolean("monitor_enabled", true); }
    public static void monitorEnabled(Context c, boolean v) { sp(c).edit().putBoolean("monitor_enabled", v).apply(); }

    public static boolean browserBlockImages(Context c) { return sp(c).getBoolean("browser_block_images", true); }
    public static void browserBlockImages(Context c, boolean v) { sp(c).edit().putBoolean("browser_block_images", v).apply(); }

    public static long capBytes(Context c) { return sp(c).getLong("cap_bytes", 200L * 1024L); }
    public static void capBytes(Context c, long v) { sp(c).edit().putLong("cap_bytes", Math.max(8 * 1024L, v)).apply(); }

    public static Set<String> selected(Context c) {
        return new HashSet<>(sp(c).getStringSet("selected", Collections.emptySet()));
    }

    public static void selected(Context c, Set<String> pkgs) {
        sp(c).edit().putStringSet("selected", new HashSet<>(pkgs)).apply();
    }

    public static boolean isSelected(Context c, String pkg) { return selected(c).contains(pkg); }

    public static void setSelected(Context c, String pkg, boolean on) {
        Set<String> set = selected(c);
        if (on) set.add(pkg); else set.remove(pkg);
        selected(c, set);
    }

    public static List<String> selectedSorted(Context c) {
        ArrayList<String> list = new ArrayList<>(selected(c));
        Collections.sort(list);
        return list;
    }

    public static String modeTitle(String mode) {
        if (MODE_BLOCK_SELECTED.equals(mode)) return "Блок выбранных";
        if (MODE_LIMIT_SELECTED.equals(mode)) return "Лимит выбранных";
        if (MODE_LIMIT_ALL.equals(mode)) return "Лимит всем";
        return "Только выбранные онлайн";
    }
}
