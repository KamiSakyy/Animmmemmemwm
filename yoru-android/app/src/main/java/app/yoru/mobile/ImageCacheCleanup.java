package app.yoru.mobile;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.List;

/** Delete ONLY the two private image-cache directories used by earlier versions.
 * No public storage, gallery, video cache, downloads or settings are traversed.
 * File-tree traversal deliberately does not follow symbolic links.
 */
final class ImageCacheCleanup {

  private static final List<String> DIRECTORIES = Arrays.asList(
    "covers",
    "image_manager_disk_cache"
  );

  private ImageCacheCleanup() {}

  static void clear(File privateCacheRoot) throws IOException {
    Path root = privateCacheRoot.toPath();
    IOException failure = null;
    for (String name : DIRECTORIES) {
      Path target = root.resolve(name);
      if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) continue;
      try {
        Files.walkFileTree(
          target,
          new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(
              Path file,
              BasicFileAttributes attributes
            ) throws IOException {
              Files.deleteIfExists(file);
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(
              Path directory,
              IOException error
            ) throws IOException {
              if (error != null) throw error;
              Files.deleteIfExists(directory);
              return FileVisitResult.CONTINUE;
            }
          }
        );
      } catch (IOException error) {
        failure = error;
      }
    }
    if (failure != null) throw failure;
  }
}
