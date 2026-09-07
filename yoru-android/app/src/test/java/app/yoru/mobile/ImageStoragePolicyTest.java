package app.yoru.mobile;

import static org.junit.Assert.*;

import com.bumptech.glide.load.engine.cache.DiskCacheAdapter;
import com.bumptech.glide.signature.ObjectKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

public class ImageStoragePolicyTest {

  private String source(String relative) throws IOException {
    for (
      Path root = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
      root != null;
      root = root.getParent()
    ) {
      for (String prefix : Arrays.asList("", "yoru-android/app/")) {
        Path path = root.resolve(prefix + relative);
        if (
          Files.isRegularFile(path) &&
          Files.isRegularFile(
            root.resolve(
              prefix + "src/main/java/app/yoru/mobile/ImageLoader.java"
            )
          )
        ) return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
      }
    }
    throw new IOException("Source fixture not found: " + relative);
  }

  @Test
  public void noOpDiskCacheNeverCallsAFileWriter() {
    DiskCacheAdapter disk = new DiskCacheAdapter();
    AtomicBoolean called = new AtomicBoolean();
    ObjectKey key = new ObjectKey("poster");
    disk.put(key, file -> {
      called.set(true);
      return true;
    });
    assertFalse(called.get());
    assertNull(disk.get(key));
  }

  @Test
  public void allImageRequestsAndGlobalModuleDisableDiskStorage()
    throws Exception {
    String loader = source("src/main/java/app/yoru/mobile/ImageLoader.java");
    assertTrue(loader.contains("DiskCacheStrategy.NONE"));
    for (String option : Arrays.asList("AUTOMATIC", "ALL", "DATA", "RESOURCE"))
      assertFalse(loader.contains("DiskCacheStrategy." + option));
    String module = source(
      "src/main/java/app/yoru/mobile/MemoryOnlyGlideModule.java"
    );
    assertTrue(module.contains("@GlideModule"));
    assertTrue(module.contains("DiskCacheAdapter::new"));
    assertTrue(
      source("build.gradle").contains(
        "annotationProcessor 'com.github.bumptech.glide:compiler:4.16.0'"
      )
    );
  }

  @Test
  public void imageCleanupDoesNotInvokeVideoOrPublicStorageDeletion()
    throws Exception {
    String cleanup = source(
      "src/main/java/app/yoru/mobile/ImageCacheCleanup.java"
    );
    assertFalse(cleanup.contains("MediaStore"));
    assertFalse(cleanup.contains("Environment"));
    assertFalse(cleanup.contains("FileVisitOption.FOLLOW_LINKS"));
    String loader = source("src/main/java/app/yoru/mobile/ImageLoader.java");
    assertTrue(
      loader.contains("ImageCacheCleanup.clear(context.getCacheDir())")
    );
    assertFalse(loader.contains("clearTemporary()"));
    assertFalse(loader.contains("offline()"));
    assertTrue(
      source("src/main/java/app/yoru/mobile/YoruApp.java").contains(
        "discovery.execute(images::removeLegacyDiskFiles)"
      )
    );
  }
}
