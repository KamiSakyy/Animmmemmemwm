package app.yoru.mobile;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.widget.RemoteViews;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class YoruWidgetProvider extends AppWidgetProvider {

  private static final AtomicLong LAST_REFRESH = new AtomicLong();

  @Override
  public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
    PendingResult pending = goAsync();
    YoruApp.app().local.execute(() -> {
      try {
        update(context.getApplicationContext(), manager, ids);
      } finally {
        pending.finish();
      }
    });
  }

  static void refresh(Context context) {
    long now = android.os.SystemClock.elapsedRealtime(),
      last = LAST_REFRESH.get();
    if (last != 0 && now - last < 30_000) return;
    if (!LAST_REFRESH.compareAndSet(last, now)) return;
    Context appContext = context.getApplicationContext();
    YoruApp.app().local.execute(() -> {
      try {
        AppWidgetManager manager = AppWidgetManager.getInstance(appContext);
        update(
          appContext,
          manager,
          manager.getAppWidgetIds(
            new ComponentName(appContext, YoruWidgetProvider.class)
          )
        );
      } catch (Exception error) {
        Perf.failure("widget-refresh", error);
      }
    });
  }

  private static void update(
    Context context,
    AppWidgetManager manager,
    int[] ids
  ) {
    if (ids == null || ids.length == 0) return;
    SecureStore store = YoruApp.app().store;
    store.preload();
    List<Anime> recent = store.recent();
    Anime anime = recent.isEmpty() ? null : recent.get(0);
    RemoteViews view = new RemoteViews(
      context.getPackageName(),
      R.layout.widget_yoru
    );
    view.setTextViewText(
      R.id.widget_title,
      anime == null ? "YORU" : "Продолжить"
    );
    view.setTextViewText(
      R.id.widget_text,
      anime == null
        ? "Откройте каталог и выберите аниме"
        : "Смотреть: " + anime.title
    );
    Intent open = new Intent(context, MainActivity.class).addFlags(
      Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
    );
    view.setOnClickPendingIntent(
      R.id.widget_root,
      PendingIntent.getActivity(
        context,
        9001,
        open,
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
      )
    );
    manager.updateAppWidget(ids, view);
  }
}
