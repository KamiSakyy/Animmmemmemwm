package app.yoru.mobile;

import java.util.Collection;

final class PlaybackSelection {

  private PlaybackSelection() {}

  /** Compare the stable variant/page key, not its resolved CDN URI. */
  static boolean currentGroup(
    String currentVariantKey,
    Collection<String> groupKeys
  ) {
    return (
      currentVariantKey != null &&
      !currentVariantKey.isEmpty() &&
      groupKeys != null &&
      groupKeys.contains(currentVariantKey)
    );
  }
}
