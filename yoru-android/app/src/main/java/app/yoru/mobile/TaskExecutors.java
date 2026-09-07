package app.yoru.mobile;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Fixed concurrency: no grow-only-after-256-items behaviour or silent drops.
 * Foreground queues retain accepted work; owners cancel obsolete Futures,
 * which remove themselves from the queue immediately. Prefetch is admitted
 * separately by ApiRepository, not by dropping arbitrary user operations.
 */
final class TaskExecutors {

  private TaskExecutors() {}

  static ThreadPoolExecutor fixed(String name, int workers) {
    AtomicInteger sequence = new AtomicInteger();
    ThreadPoolExecutor pool = new ThreadPoolExecutor(
      workers,
      workers,
      30,
      TimeUnit.SECONDS,
      new LinkedBlockingQueue<>(),
      r -> {
        Thread thread = new Thread(r, name + "-" + sequence.incrementAndGet());
        thread.setDaemon(true);
        return thread;
      }
    ) {
      private final ConcurrentHashMap<Runnable, NetworkScope> active =
        new ConcurrentHashMap<>();

      @Override
      protected void beforeExecute(Thread thread, Runnable task) {
        super.beforeExecute(thread, task);
        active.put(task, NetworkScope.open(20_000));
      }

      @Override
      protected void afterExecute(Runnable task, Throwable failure) {
        NetworkScope scope = active.remove(task);
        if (scope != null) scope.close();
        super.afterExecute(task, failure);
      }

      @Override
      protected <T> RunnableFuture<T> newTaskFor(Callable<T> work) {
        return new FutureTask<T>(work) {
          @Override
          protected void done() {
            if (!isCancelled()) return;
            remove(this);
            NetworkScope scope = active.get(this);
            if (scope != null) scope.cancel();
          }
        };
      }

      @Override
      protected <T> RunnableFuture<T> newTaskFor(Runnable work, T value) {
        return newTaskFor(Executors.callable(work, value));
      }
    };
    pool.allowCoreThreadTimeOut(true);
    return pool;
  }
}
