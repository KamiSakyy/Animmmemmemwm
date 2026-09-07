package app.yoru.mobile;

import android.content.Context;
import android.net.*;
import android.os.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.json.*;

public final class TrafficMeter {

  private final ConnectivityManager connectivity;
  private final SecureStore store;
  private final Handler main = new Handler(Looper.getMainLooper());
  private long mobile,
    wifi,
    other,
    since,
    lastTotal = -1,
    lastElapsed;
  private long lastPersisted = -60_000;
  private int lastKind;
  private boolean available = true,
    started;
  private final CopyOnWriteArrayList<Runnable> listeners =
    new CopyOnWriteArrayList<>();

  public TrafficMeter(Context context, SecureStore s) {
    store = s;
    connectivity = (ConnectivityManager) context.getSystemService(
      Context.CONNECTIVITY_SERVICE
    );
    since = System.currentTimeMillis();
    lastKind = 2;
    try {
      connectivity.registerDefaultNetworkCallback(
        new ConnectivityManager.NetworkCallback() {
          public void onAvailable(Network n) {
            changed();
          }

          public void onLost(Network n) {
            changed();
          }

          public void onCapabilitiesChanged(Network n, NetworkCapabilities c) {
            changed();
          }
        }
      );
    } catch (Exception ignored) {}
    main.postDelayed(
      new Runnable() {
        public void run() {
          started = true;
          sample();
          main.postDelayed(this, 5000);
        }
      },
      5000
    );
  }

  private void changed() {
    main.post(() -> {
      if (!started) return;
      sample();
      for (Runnable r : listeners) r.run();
    });
  }

  public void preferencesChanged() {
    changed();
  }

  public void addListener(Runnable r) {
    listeners.add(r);
  }

  public void removeListener(Runnable r) {
    listeners.remove(r);
  }

  public int networkKind() {
    try {
      Network n = connectivity.getActiveNetwork();
      NetworkCapabilities c =
        n == null ? null : connectivity.getNetworkCapabilities(n);
      if (c == null) return 2;
      if (c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return 0;
      if (
        c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
        c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
      ) return 1;
    } catch (Exception ignored) {}
    return 2;
  }

  public boolean mobile() {
    return networkKind() == 0;
  }

  public boolean metered() {
    try {
      return connectivity.isActiveNetworkMetered();
    } catch (Exception e) {
      return true;
    }
  }

  public boolean connected() {
    try {
      Network n = connectivity.getActiveNetwork();
      NetworkCapabilities c =
        n == null ? null : connectivity.getNetworkCapabilities(n);
      return (
        c != null &&
        c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
      );
    } catch (Exception e) {
      return false;
    }
  }

  public synchronized void restore() {
    JSONObject saved = store.traffic();
    mobile = saved.optLong("mobile", 0);
    wifi = saved.optLong("wifi", 0);
    other = saved.optLong("other", 0);
    since = saved.optLong("since", System.currentTimeMillis());
    lastTotal = saved.optLong("lastTotal", -1);
    lastElapsed = saved.optLong("lastElapsed", 0);
    lastKind = saved.optInt("lastKind", 2);
  }

  public synchronized void sample() {
    if (!store.ready()) return;
    long rx = TrafficStats.getUidRxBytes(android.os.Process.myUid()),
      tx = TrafficStats.getUidTxBytes(android.os.Process.myUid());
    long now = SystemClock.elapsedRealtime();
    if (rx < 0 || tx < 0) {
      available = false;
      return;
    }
    available = true;
    long total = rx + tx;
    if (lastTotal >= 0 && total >= lastTotal && now >= lastElapsed) {
      long delta = total - lastTotal;
      int kind = now - lastElapsed > 120000 ? 2 : lastKind;
      if (kind == 0) mobile += delta;
      else if (kind == 1) wifi += delta;
      else other += delta;
    }
    lastTotal = total;
    lastElapsed = now;
    lastKind = networkKind();
    persist();
  }

  private void persist() {
    long now = SystemClock.elapsedRealtime();
    if (now - lastPersisted < 60_000) return;
    lastPersisted = now;
    try {
      store.traffic(
        new JSONObject()
          .put("mobile", mobile)
          .put("wifi", wifi)
          .put("other", other)
          .put("since", since)
          .put("lastTotal", lastTotal)
          .put("lastElapsed", lastElapsed)
          .put("lastKind", lastKind)
      );
    } catch (Exception ignored) {}
  }

  public synchronized JSONObject snapshot() {
    sample();
    try {
      return new JSONObject()
        .put("mobile", mobile)
        .put("wifi", wifi)
        .put("other", other)
        .put("since", since)
        .put("supported", available);
    } catch (Exception e) {
      return new JSONObject();
    }
  }

  public synchronized void reset() {
    mobile = wifi = other = 0;
    since = System.currentTimeMillis();
    lastTotal = -1;
    lastPersisted = -60_000;
    sample();
  }
}
