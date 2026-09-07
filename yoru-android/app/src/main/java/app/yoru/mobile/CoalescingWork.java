package app.yoru.mobile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Serial, latest-per-key background writer. No serialization on submit(). */
final class CoalescingWork implements AutoCloseable {

  private final ScheduledThreadPoolExecutor executor;
  private final Map<String, Runnable> pending = new LinkedHashMap<>();
  private final long delayMillis;
  private final Consumer<RuntimeException> errors;
  private ScheduledFuture<?> scheduled;

  CoalescingWork(
    String name,
    long delayMillis,
    Consumer<RuntimeException> errors
  ) {
    this.delayMillis = delayMillis;
    this.errors = errors;
    executor = new ScheduledThreadPoolExecutor(1, r -> {
      Thread thread = new Thread(r, name);
      thread.setDaemon(true);
      return thread;
    });
    executor.setRemoveOnCancelPolicy(true);
  }

  synchronized void submit(String key, Runnable work) {
    pending.put(key, work);
    if (scheduled == null) scheduled = executor.schedule(
      this::drain,
      delayMillis,
      TimeUnit.MILLISECONDS
    );
  }

  synchronized void flush() {
    if (scheduled != null) scheduled.cancel(false);
    scheduled = executor.schedule(this::drain, 0, TimeUnit.MILLISECONDS);
  }

  private void drain() {
    Map<String, Runnable> batch;
    synchronized (this) {
      batch = new LinkedHashMap<>(pending);
      pending.clear();
      scheduled = null;
    }
    for (Runnable work : batch.values()) {
      try {
        work.run();
      } catch (RuntimeException error) {
        errors.accept(error);
      }
    }
  }

  // For worker-side tests/shutdown only. Never await from Android main.
  void awaitIdle(long timeoutMillis) throws Exception {
    flush();
    executor.submit(() -> {}).get(timeoutMillis, TimeUnit.MILLISECONDS);
  }

  @Override
  public void close() {
    flush();
    executor.shutdown();
  }
}
