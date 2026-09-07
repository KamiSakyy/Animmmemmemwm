package app.yoru.mobile;

import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy;
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
    load(view, model, 600, 900);
  }

  public void load(ImageView view, String url, String fallback) {
    load(view, remote(url), 1280, 1280);
  }

  private void load(ImageView view, Object model, int width, int height) {
    // All callers bind views on main. Glide.with(view) cancels on Activity destruction.
    Glide.with(view)
      .asBitmap()
      .load(model)
      .placeholder(R.drawable.ic_yoru)
      .error(R.drawable.ic_yoru)
      .format(DecodeFormat.PREFER_RGB_565)
      .downsample(DownsampleStrategy.AT_MOST)
      .override(width, height)
      .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
      .dontAnimate()
      .into(view);
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
    Glide.with(view).clear(view);
  }

  public void clear() {
    Runnable memory = () -> Glide.get(context).clearMemory();
    if (Looper.myLooper() == Looper.getMainLooper()) {
      memory.run();
      YoruApp.app().local.execute(() -> Glide.get(context).clearDiskCache());
    } else {
      Glide.get(context).clearDiskCache();
      YoruApp.app().main.post(memory);
    }
  }
}
