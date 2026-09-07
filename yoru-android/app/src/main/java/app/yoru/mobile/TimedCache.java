package app.yoru.mobile;

import java.util.*;
import java.util.function.LongSupplier;

/** Small count-bounded cache using monotonic time, independent from wall-clock changes. */
final class TimedCache<K, V> {

  private static final class Entry<V> {

    final V value;
    final long at;

    Entry(V value, long at) {
      this.value = value;
      this.at = at;
    }
  }

  private final int maximum;
  private final long ttlNanos;
  private final LongSupplier clock;
  private final LinkedHashMap<K, Entry<V>> entries = new LinkedHashMap<>(
    16,
    .75f,
    true
  );

  TimedCache(int maximum, long ttlMillis) {
    this(maximum, ttlMillis, System::nanoTime);
  }

  TimedCache(int maximum, long ttlMillis, LongSupplier clock) {
    if (maximum < 1 || ttlMillis < 1) throw new IllegalArgumentException();
    this.maximum = maximum;
    this.ttlNanos = ttlMillis * 1_000_000L;
    this.clock = clock;
  }

  synchronized V get(K key) {
    Entry<V> entry = entries.get(key);
    if (entry == null) return null;
    if (clock.getAsLong() - entry.at >= ttlNanos) {
      entries.remove(key);
      return null;
    }
    return entry.value;
  }

  synchronized void put(K key, V value) {
    entries.put(key, new Entry<>(value, clock.getAsLong()));
    while (entries.size() > maximum)
      entries.remove(entries.keySet().iterator().next());
  }

  synchronized void remove(K key) {
    entries.remove(key);
  }

  synchronized void clear() {
    entries.clear();
  }

  synchronized int size() {
    return entries.size();
  }
}
