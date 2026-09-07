package app.yoru.mobile;

import java.util.concurrent.*;

/** Coalesces simultaneous identical work. Failed entries are removed for retry. */
final class SingleFlight<K, V> {

  private final ConcurrentHashMap<K, FutureTask<V>> running =
    new ConcurrentHashMap<>();

  private final java.util.concurrent.atomic.AtomicInteger waiting =
    new java.util.concurrent.atomic.AtomicInteger();

  int waitingCount() {
    return waiting.get();
  }

  V run(K key, Callable<V> work) throws Exception {
    NetworkScope.check();
    FutureTask<V> mine = new FutureTask<>(work);
    FutureTask<V> task = running.putIfAbsent(key, mine);
    boolean waiter = task != null;
    if (waiter) waiting.incrementAndGet();
    if (task == null) {
      task = mine;
      mine.run();
    }
    try {
      return task.get(NetworkScope.timeout(20_000), TimeUnit.MILLISECONDS);
    } catch (ExecutionException failure) {
      Throwable cause = failure.getCause();
      if (cause instanceof Exception) throw (Exception) cause;
      if (cause instanceof Error) throw (Error) cause;
      throw failure;
    } finally {
      if (waiter) waiting.decrementAndGet();
      // A cancelled waiter must not remove another owner's still-running task.
      if (task.isDone()) running.remove(key, task);
    }
  }

  int size() {
    return running.size();
  }
}
