package app.yoru.mobile;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

/** Bounds the whole iframe graph, not just children of an individual page. */
final class ResolutionWalk {

  private static final ThreadLocal<ResolutionWalk> CURRENT =
    new ThreadLocal<>();
  private final Set<String> visited = new HashSet<>();
  private int depth;

  static <T> T visit(String url, Callable<T> action) throws Exception {
    NetworkScope.check();
    ResolutionWalk walk = CURRENT.get();
    boolean root = walk == null;
    if (root) {
      walk = new ResolutionWalk();
      CURRENT.set(walk);
    }
    try {
      if (
        walk.depth >= 6 || walk.visited.size() >= 16 || !walk.visited.add(url)
      ) throw new IOException("Повтор или слишком длинная цепочка плееров");
      walk.depth++;
      try {
        return action.call();
      } finally {
        walk.depth--;
      }
    } finally {
      if (root) CURRENT.remove();
    }
  }
}
