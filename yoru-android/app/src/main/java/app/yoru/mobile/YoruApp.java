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
  private final ArrayList<ReadyWork> waiting = new ArrayList<>();

  private final class ReadyWork
    implements androidx.lifecycle.DefaultLifecycleObserver
  {

    final WeakReference<Activity> owner;
    Runnable action;

    ReadyWork(Activity activity, Runnable action) {
      owner = new WeakReference<>(activity);
      this.action = action;
    }

    @Override
    public void onDestroy(androidx.lifecycle.LifecycleOwner ignored) {
      waiting.remove(this);
      action = null;
    }

    void dispatch() {
      Activity activity = owner.get();
      if (activity instanceof androidx.lifecycle.LifecycleOwner) (
        (androidx.lifecycle.LifecycleOwner) activity
      )
        .getLifecycle()
        .removeObserver(this);
      Runnable ready = action;
      action = null;
      if (
        ready != null &&
        activity != null &&
        !activity.isFinishing() &&
        !activity.isDestroyed()
      ) ready.run();
    }
  }

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
          ArrayList<ReadyWork> ready = new ArrayList<>(waiting);
          waiting.clear();
          for (ReadyWork work : ready) work.dispatch();
          main.postDelayed(this::warmStartup, 1000);
        });
      }
    });
  }

  /** Called on main by the lightweight activity gate. Never blocks main. */
  public void runWhenReady(Activity activity, Runnable action) {
    if (initialized) {
      if (!activity.isFinishing() && !activity.isDestroyed()) action.run();
      return;
    }
    ReadyWork work = new ReadyWork(activity, action);
    waiting.add(work);
    if (activity instanceof androidx.lifecycle.LifecycleOwner) (
      (androidx.lifecycle.LifecycleOwner) activity
    )
      .getLifecycle()
      .addObserver(work);
  }

  /** Startup warms local data only. Network schedule refresh belongs to Calendar/jobs. */
  public void warmStartup() {
    if (warmed || !initialized || !original) return;
    warmed = true;
    discovery.execute(images::removeLegacyDiskFiles);
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
