package app.yoru.mobile;

import java.util.Objects;

final class ImageBindPolicy {

  private ImageBindPolicy() {}

  static boolean keepRequest(
    String oldKey,
    String key,
    boolean running,
    boolean complete
  ) {
    return Objects.equals(oldKey, key) && (running || complete);
  }

  static boolean retainThumbnail(
    String oldIdentity,
    String identity,
    boolean complete
  ) {
    return (
      complete &&
      oldIdentity != null &&
      !oldIdentity.isEmpty() &&
      oldIdentity.equals(identity)
    );
  }
}
