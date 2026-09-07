package app.yoru.mobile;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ImageCacheCleanupTest {

  @Rule
  public TemporaryFolder temporary = new TemporaryFolder();

  private Path write(Path path) throws Exception {
    Files.createDirectories(path.getParent());
    Files.write(path, "fixture".getBytes(StandardCharsets.UTF_8));
    return path;
  }

  @Test
  public void onlyKnownPrivateImageCachesAreDeleted() throws Exception {
    Path root = temporary.newFolder("app").toPath();
    Path cache = Files.createDirectories(root.resolve("cache"));
    write(cache.resolve("covers/poster.webp"));
    write(cache.resolve("image_manager_disk_cache/key.0"));
    Path video = write(cache.resolve("video/segment.bin"));
    Path sharedVideo = write(cache.resolve("shared-video/episode.mp4"));
    Path download = write(root.resolve("files/offline-media/episode.mp4"));
    Path preference = write(root.resolve("shared_prefs/library.xml"));
    Path photo = write(root.resolve("Pictures/personal.jpg"));
    ImageCacheCleanup.clear(cache.toFile());
    assertFalse(Files.exists(cache.resolve("covers")));
    assertFalse(Files.exists(cache.resolve("image_manager_disk_cache")));
    assertTrue(Files.exists(video));
    assertTrue(Files.exists(sharedVideo));
    assertTrue(Files.exists(download));
    assertTrue(Files.exists(preference));
    assertTrue(Files.exists(photo));
  }

  @Test
  public void missingCachesAreNotCreated() throws Exception {
    File cache = temporary.newFolder("cache");
    ImageCacheCleanup.clear(cache);
    ImageCacheCleanup.clear(cache);
    assertEquals(0, cache.list().length);
  }

  @Test
  public void nestedSymlinkDoesNotDeleteItsPhotoTarget() throws Exception {
    Path cache = temporary.newFolder("cache").toPath();
    Path photos = temporary.newFolder("photos").toPath();
    Path personal = write(photos.resolve("personal.jpg"));
    Path covers = Files.createDirectories(cache.resolve("covers"));
    Files.createSymbolicLink(covers.resolve("outside"), photos);
    ImageCacheCleanup.clear(cache.toFile());
    assertTrue(Files.exists(personal));
    assertTrue(Files.exists(photos));
    assertFalse(Files.exists(covers));
  }

  @Test
  public void topLevelSymlinkIsUnlinkedWithoutTraversingGallery()
    throws Exception {
    Path cache = temporary.newFolder("cache").toPath();
    Path photos = temporary.newFolder("photos").toPath();
    Path personal = write(photos.resolve("personal.jpg"));
    Files.createSymbolicLink(cache.resolve("image_manager_disk_cache"), photos);
    ImageCacheCleanup.clear(cache.toFile());
    assertTrue(Files.exists(personal));
    assertFalse(
      Files.exists(
        cache.resolve("image_manager_disk_cache"),
        LinkOption.NOFOLLOW_LINKS
      )
    );
  }

  @Test
  public void similarlyNamedOrUnknownDirectoriesAreNotRemoved()
    throws Exception {
    Path cache = temporary.newFolder("cache").toPath();
    Path unrelated = write(cache.resolve("covers-backup/keep.txt"));
    Path unknown = write(cache.resolve("images/user-owned.jpg"));
    ImageCacheCleanup.clear(cache.toFile());
    assertTrue(Files.exists(unrelated));
    assertTrue(Files.exists(unknown));
  }
}
