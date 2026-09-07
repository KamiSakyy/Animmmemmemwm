package app.yoru.mobile;

import android.content.Context;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.cache.DiskCacheAdapter;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.request.RequestOptions;

/** Process-wide no-disk guarantee, including requests added outside ImageLoader. */
@GlideModule
public final class MemoryOnlyGlideModule extends AppGlideModule {

  @Override
  public void applyOptions(Context context, GlideBuilder builder) {
    builder.setDiskCache(DiskCacheAdapter::new);
    builder.setDefaultRequestOptions(
      new RequestOptions().diskCacheStrategy(DiskCacheStrategy.NONE)
    );
  }

  @Override
  public boolean isManifestParsingEnabled() {
    return false;
  }
}
