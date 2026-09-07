package app.yoru.mobile;

import android.app.*;
import android.os.*;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;

public final class YoruApp extends Application {

  public static YoruApp instance;
  public final ExecutorService io = TaskExecutors.fixed("yoru-io", 4);
  public final ExecutorService ui = TaskExecutors.fixed("yoru-foreground", 4);
  public final ExecutorService discovery = TaskExecutors.fixed(
    "yoru-background",
    2
  );
  public final ExecutorService sources = TaskExecutors.fixed("yoru-source", 4);
  public final ExecutorService local = TaskExecutors.fixed("yoru-local", 1);
  public final Handler main = new Handler(Looper.getMainLooper());
  public SecureStore store;
  public YoruCache cache;
  public ApiRepository api;
  public ImageLoader images;
  public volatile boolean original, initialized;
  public TrafficMeter traffic;
  public MediaCache mediaCache;
  private DownloadHub downloads;
  public volatile int activePlayers, calendarTodayCount;
  private boolean warmed;
  private final ArrayList<WeakReference<Activity>> waiting = new ArrayList<>();

  public synchronized DownloadHub downloads() {
    if (downloads == null) downloads = new DownloadHub(this, mediaCache);
    return downloads;
  }

  public boolean savingMobile() {
    return (
      store != null &&
      store.ready() &&
      traffic != null &&
      store.dataSaver() &&
      traffic.metered()
    );
  }

  public boolean autoNextAllowed() {
    return store.autoNext();
  }

  @Override
  public void onCreate() {
    super.onCreate();
    instance = this;
    store = new SecureStore(this);
    cache = new YoruCache(this);
    mediaCache = new MediaCache(this);
    traffic = new TrafficMeter(this, store);
    api = new ApiRepository(this); // Constructors must not read SecureStore.
    images = new ImageLoader(this);
    channels();
    local.execute(() -> {
      long started = Perf.start();
      try {
        store.preload();
        api.restoreProtection();
        api.seed();
        traffic.restore();
        original = AppSecurity.original(this);
      } catch (Exception error) {
        Perf.failure("bootstrap", error);
        original = false;
      } finally {
        Perf.end("bootstrap", started);
        main.post(() -> {
          initialized = true;
          ArrayList<WeakReference<Activity>> ready = new ArrayList<>(waiting);
          waiting.clear();
          for (WeakReference<Activity> ref : ready) {
            Activity activity = ref.get();
            if (
              activity != null &&
              !activity.isFinishing() &&
              !activity.isDestroyed()
            ) activity.recreate();
          }
          main.postDelayed(this::warmStartup, 1000);
        });
      }
    });
  }

  /** Called on main by the lightweight activity gate. Never blocks main. */
  public void resumeWhenReady(Activity activity) {
    if (initialized) {
      main.post(() -> {
        if (
          !activity.isFinishing() && !activity.isDestroyed()
        ) activity.recreate();
      });
    } else {
      waiting.removeIf(ref -> ref.get() == null || ref.get() == activity);
      waiting.add(new WeakReference<>(activity));
    }
  }

  /** Startup warms local data only. Network schedule refresh belongs to Calendar/jobs. */
  public void warmStartup() {
    if (warmed || !initialized || !original) return;
    warmed = true;
    local.execute(() -> {
      try {
        calendarTodayCount = cache.todayScheduleCount();
        cache.trimNow();
      } catch (Exception error) {
        Perf.failure("local-warmup", error);
      }
    });
    discovery.execute(() -> {
      try {
        EpisodeUpdateReceiver.schedule(this);
        if (
          System.currentTimeMillis() - store.lastEpisodeCheckAt() >
          35L * 60 * 1000
        ) EpisodeUpdateReceiver.checkSoon(this);
      } catch (Exception error) {
        Perf.failure("schedule-job", error);
      }
    });
  }

  private void channels() {
    NotificationManager manager = (NotificationManager) getSystemService(
      NOTIFICATION_SERVICE
    );
    if (manager != null) manager.createNotificationChannel(
      new NotificationChannel(
        "yoru-updates",
        getString(R.string.updates_channel_name),
        NotificationManager.IMPORTANCE_HIGH
      )
    );
  }

  public static YoruApp app() {
    return instance;
  }
}
