package app.yoru.mobile;

import java.util.*;

/** Per-episode attempt history. Never oscillate A/B or revisit failed qualities. */
final class RetryPlan {

  private final Set<String> failedVariants = new HashSet<>();
  private final Set<String> failedQualities = new HashSet<>();

  void reset() {
    failedVariants.clear();
    failedQualities.clear();
  }

  void failVariant(String key) {
    if (key != null) failedVariants.add(key);
  }

  boolean canTry(String key) {
    return key != null && !key.isEmpty() && !failedVariants.contains(key);
  }

  void failQuality(String variant, int quality) {
    failedQualities.add(variant + "|" + quality);
  }

  Integer nextLower(
    String variant,
    Collection<Integer> qualities,
    int current
  ) {
    Integer next = null;
    for (Integer quality : qualities)
      if (
        quality != null &&
        quality > 0 &&
        quality < current &&
        !failedQualities.contains(variant + "|" + quality) &&
        (next == null || quality > next)
      ) next = quality;
    return next;
  }
}
