package app.yoru.mobile;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;

/** First useful results with a short settling window; explicit full discovery is optional. */
final class FirstResults {

  private FirstResults() {}

  static <T> List<T> collect(
    ExecutorService executor,
    List<Callable<T>> jobs,
    Predicate<T> preferred,
    boolean requirePreferred,
    boolean all,
    long budgetMillis,
    long settleMillis,
    long fallbackGraceMillis
  ) throws Exception {
    CompletionService<T> completion = new ExecutorCompletionService<>(executor);
    ArrayList<Future<T>> futures = new ArrayList<>();
    ArrayList<T> results = new ArrayList<>();
    long deadline =
      System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(budgetMillis);
    long readyDeadline = Long.MAX_VALUE;
    boolean preferredSeen = false;
    try {
      for (Callable<T> job : jobs) futures.add(completion.submit(job));
      for (int i = 0; i < futures.size(); i++) {
        NetworkScope.check();
        long limit = all ? deadline : Math.min(deadline, readyDeadline);
        long remaining = limit - System.nanoTime();
        if (remaining <= 0) break;
        Future<T> future = completion.poll(remaining, TimeUnit.NANOSECONDS);
        if (future == null) break;
        T result;
        try {
          result = future.get();
        } catch (ExecutionException | CancellationException failure) {
          continue;
        }
        if (result == null) continue;
        results.add(result);
        if (preferred.test(result)) {
          preferredSeen = true;
          readyDeadline = Math.min(
            readyDeadline,
            System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(settleMillis)
          );
        } else if (!requirePreferred && !preferredSeen) {
          readyDeadline = Math.min(
            readyDeadline,
            System.nanoTime() +
              TimeUnit.MILLISECONDS.toNanos(fallbackGraceMillis)
          );
        }
      }
      if (requirePreferred && !preferredSeen) return Collections.emptyList();
      return results;
    } finally {
      for (Future<T> future : futures)
        if (!future.isDone()) future.cancel(true);
    }
  }
}
