package app.yoru.mobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Looper;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy;
import com.bumptech.glide.request.Request;
import com.bumptech.glide.signature.ObjectKey;
import java.util.*;

/** Glide owns target replacement, in-flight deduplication, sizing and lifecycle.
 * No application-wide strong-reference waiter list or silently rejected images.
 */
public final class ImageLoader {

  private static final String CHROME =
    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/149 Mobile Safari/537.36";
  private static final Set<String> BUNDLED = new HashSet<>(
    Arrays.asList(
      "10187",
      "10213",
      "10227",
      "10230",
      "10234",
      "10235",
      "10255",
      "10265",
      "10277",
      "10281",
      "10292",
      "10295",
      "4857",
      "5255",
      "5692",
      "7439",
      "7462",
      "8030",
      "8324",
      "8325",
      "8395",
      "8398",
      "8452",
      "8789",
      "9265",
      "9542",
      "9555",
      "9600",
      "9839",
      "9841"
    )
  );
  private final Context context;
  private volatile int cacheGeneration;

  private static final class Binding {

    final String key, identity;
    final Object model;
    final int width, height, generation;
    final Request request;

    Binding(
      String key,
      String identity,
      Object model,
      int width,
      int height,
      int generation,
      Request request
    ) {
      this.key = key;
      this.identity = identity;
      this.model = model;
      this.width = width;
      this.height = height;
      this.generation = generation;
      this.request = request;
    }
  }

  public ImageLoader(Context context) {
    this.context = context.getApplicationContext();
  }

  public void load(ImageView view, Anime anime) {
    String asset = "anilibria".equals(anime.source)
      ? anime.id
      : anime.malId == 52991
        ? "9542"
        : anime.malId == 52299
          ? "9600"
          : anime.malId == 54492
            ? "9555"
            : "";
    Object model = BUNDLED.contains(asset)
      ? Uri.parse("file:///android_asset/posters/" + asset + ".webp")
      : remote(anime.poster);
    load(view, model, 600, 900, SourceEngine.identity(anime));
  }

  public void load(ImageView view, String url, String fallback) {
    load(view, remote(url), 1280, 1280, fallback == null ? "" : fallback);
  }

  private void load(
    ImageView view,
    Object model,
    int width,
    int height,
    String identity
  ) {
    String key =
      String.valueOf(model) +
      "|" +
      width +
      "x" +
      height +
      "|" +
      cacheGeneration;
    Binding old = (Binding) view.getTag(R.id.yoru_image_request);
    if (
      old != null &&
      old.request != null &&
      ImageBindPolicy.keepRequest(
        old.key,
        key,
        old.request.isRunning(),
        old.request.isComplete()
      )
    ) {
      view.setTag(
        R.id.yoru_image_request,
        new Binding(
          key,
          identity,
          model,
          width,
          height,
          cacheGeneration,
          old.request
        )
      );
      return;
    }
    RequestBuilder<Bitmap> next = request(
      view,
      model,
      width,
      height,
      cacheGeneration
    );
    if (
      old != null &&
      old.model != null &&
      old.request != null &&
      ImageBindPolicy.retainThumbnail(
        old.identity,
        identity,
        old.request.isComplete()
      )
    ) {
      next = next.thumbnail(
        request(
          view,
          old.model,
          old.width,
          old.height,
          old.generation
        ).onlyRetrieveFromCache(true)
      );
    }
    Request active = next.into(view).getRequest();
    view.setTag(
      R.id.yoru_image_request,
      new Binding(key, identity, model, width, height, cacheGeneration, active)
    );
  }

  private RequestBuilder<Bitmap> request(
    ImageView view,
    Object model,
    int width,
    int height,
    int generation
  ) {
    return Glide.with(view)
      .asBitmap()
      .load(model)
      .placeholder(R.drawable.ic_yoru)
      .error(R.drawable.ic_yoru)
      .format(DecodeFormat.PREFER_RGB_565)
      .downsample(DownsampleStrategy.AT_MOST)
      .override(width, height)
      .signature(new ObjectKey(generation))
      .diskCacheStrategy(DiskCacheStrategy.NONE)
      .dontAnimate();
  }

  private static Object remote(String raw) {
    String url = ApiRepository.safeUrl(raw);
    if (url.isEmpty()) return null;
    Uri uri = Uri.parse(url);
    String host =
      uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
    String origin = uri.getScheme() + "://" + uri.getAuthority();
    String referer = host.endsWith("cdnlibs.org")
      ? "https://anilib.me/"
      : host.contains("shikimori")
        ? "https://shikimori.one/"
        : origin + "/";
    return new GlideUrl(
      url,
      new LazyHeaders.Builder()
        .addHeader("User-Agent", CHROME)
        .addHeader("Referer", referer)
        .addHeader("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.5")
        .build()
    );
  }

  public void cancel(ImageView view) {
    view.setTag(R.id.yoru_image_request, null);
    // Clearing a recycled view must also be safe after its Activity was destroyed.
    Glide.with(context).clear(view);
  }

  public void clear() {
    cacheGeneration++;
    Runnable memory = () -> Glide.get(context).clearMemory();
    if (Looper.myLooper() == Looper.getMainLooper()) {
      memory.run();
      YoruApp.app().discovery.execute(this::removeLegacyDiskFiles);
    } else {
      removeLegacyDiskFiles();
      YoruApp.app().main.post(memory);
    }
  }

  public void removeLegacyDiskFiles() {
    try {
      ImageCacheCleanup.clear(context.getCacheDir());
    } catch (java.io.IOException error) {
      Perf.failure("image-cache-cleanup", error);
    }
  }
}
