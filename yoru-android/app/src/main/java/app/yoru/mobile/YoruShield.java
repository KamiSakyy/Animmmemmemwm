package app.yoru.mobile;

import android.content.Context;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.json.*;

final class YoruShield {

  private static final String FORMAT = "yoru.source.profile.v1";
  private static final String DEFAULT_PROFILE =
    "{\"format\":\"yoru.source.profile.v1\",\"version\":3,\"routes\":[],\"remote\":[]}";
  private final Context context;
  private volatile JSONObject profile = parse(DEFAULT_PROFILE);
  private final AtomicBoolean refreshing = new AtomicBoolean();
  private final AtomicLong lastAttempt = new AtomicLong();

  YoruShield(Context context) {
    this.context = context.getApplicationContext();
  }

  /** Explicit worker-only restoration; never called by a constructor. */
  void restore() {
    JSONObject saved = parse(YoruApp.app().store.sourceProfile());
    if (saved == null) {
      try {
        saved = parse(
          ApiRepository.readStream(
            context.getAssets().open("yoru_profile.json"),
            160 * 1024
          )
        );
      } catch (Exception error) {
        Perf.failure("source-profile-asset", error);
      }
    }
    if (saved != null) profile = saved;
  }

  ArrayList<String> routes(String url) {
    JSONObject snapshot = profile;
    LinkedHashSet<String> routes = new LinkedHashSet<>();
    routes.add(url);
    JSONArray rows = snapshot.optJSONArray("routes");
    for (int i = 0; rows != null && i < Math.min(rows.length(), 20); i++) {
      JSONObject row = rows.optJSONObject(i);
      if (row == null) continue;
      String from = row.optString("from");
      if (from.isEmpty() || !url.startsWith(from)) continue;
      JSONArray alternatives = row.optJSONArray("to");
      for (
        int n = 0;
        alternatives != null && n < Math.min(3, alternatives.length());
        n++
      ) {
        String candidate = ApiRepository.safeUrl(
          alternatives.optString(n) + url.substring(from.length())
        );
        if (candidate.startsWith("https://")) routes.add(candidate);
      }
    }
    return new ArrayList<>(routes);
  }

  void ok(String url) {}

  void fail(String url) {
    YoruApp app = YoruApp.app();
    if (app == null || app.activePlayers > 0 || !app.initialized) return;
    // The bundled profile intentionally has no private-repository remote.
    JSONArray remotes = profile.optJSONArray("remote");
    if (remotes == null || remotes.length() == 0) return;
    if (
      System.currentTimeMillis() - lastAttempt.get() < 60L * 60 * 1000
    ) return;
    app.discovery.execute(() -> refresh(false));
  }

  void refresh(boolean force) {
    YoruApp app = YoruApp.app();
    if (
      !force &&
      (app.savingMobile() ||
        app.activePlayers > 0 ||
        System.currentTimeMillis() - app.store.sourceProfileAt() <
          48L * 60 * 60 * 1000)
    ) return;
    if (!refreshing.compareAndSet(false, true)) return;
    long now = System.currentTimeMillis();
    try {
      if (!force && now - lastAttempt.get() < 60L * 60 * 1000) return;
      lastAttempt.set(now);
      JSONArray remotes = profile.optJSONArray("remote");
      for (
        int i = 0;
        remotes != null && i < Math.min(3, remotes.length());
        i++
      ) {
        String url = ApiRepository.safeUrl(remotes.optString(i));
        if (
          !url.startsWith("https://") ||
          url.contains("raw.githubusercontent.com/KamiSakyy/Animmmemmemwm/")
        ) continue;
        try {
          HttpURLConnection connection = (HttpURLConnection) new URL(
            url
          ).openConnection();
          try {
            connection.setConnectTimeout(NetworkScope.timeout(2500));
            connection.setReadTimeout(NetworkScope.timeout(3200));
            connection.setRequestProperty("User-Agent", "YORU");
            NetworkScope.track(connection);
            if (connection.getResponseCode() != 200) continue;
            JSONObject next = parse(
              ApiRepository.readStream(connection.getInputStream(), 160 * 1024)
            );
            if (next != null) {
              profile = next; // Publish immutable snapshot after I/O, without a global lock.
              app.store.sourceProfile(next.toString());
              return;
            }
          } finally {
            NetworkScope.disconnect(connection);
          }
        } catch (Exception error) {
          Perf.failure("source-profile-refresh", error);
        }
      }
    } finally {
      refreshing.set(false);
    }
  }

  private static JSONObject parse(String text) {
    try {
      if (text == null || text.trim().isEmpty()) return null;
      JSONObject value = new JSONObject(text);
      return FORMAT.equals(value.optString("format")) ? value : null;
    } catch (JSONException error) {
      return null;
    }
  }
}
