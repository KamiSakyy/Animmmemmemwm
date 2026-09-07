package app.yoru.mobile;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Per-section change detection; an equal callback must not recreate views. */
final class RenderState {

  private final Map<String, String> values = new HashMap<>();

  boolean changed(String section, String signature) {
    if (
      values.containsKey(section) &&
      Objects.equals(values.get(section), signature)
    ) return false;
    values.put(section, signature);
    return true;
  }

  void clear() {
    values.clear();
  }
}
