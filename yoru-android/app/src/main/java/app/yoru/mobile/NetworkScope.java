package app.yoru.mobile;

import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** One worker operation's monotonic deadline and cancellable connections. */
final class NetworkScope implements AutoCloseable {

  private static final ThreadLocal<NetworkScope> CURRENT = new ThreadLocal<>();
  private static final ExecutorService CLOSER =
    Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "yoru-net-close");
      t.setDaemon(true);
      return t;
    });
  private final NetworkScope previous;
  private final long deadline;
  private final Set<HttpURLConnection> connections =
    ConcurrentHashMap.newKeySet();
  private volatile boolean cancelled;

  private NetworkScope(long budgetMillis) {
    previous = CURRENT.get();
    long own =
      System.nanoTime() +
      TimeUnit.MILLISECONDS.toNanos(Math.max(1, budgetMillis));
    deadline = previous == null ? own : Math.min(own, previous.deadline);
    CURRENT.set(this);
  }

  static NetworkScope open(long budgetMillis) {
    return new NetworkScope(budgetMillis);
  }

  static boolean cancelled() {
    NetworkScope scope = CURRENT.get();
    return (
      Thread.currentThread().isInterrupted() ||
      (scope != null && scope.expired())
    );
  }

  static void check() throws InterruptedIOException {
    NetworkScope scope = CURRENT.get();
    if (
      Thread.currentThread().isInterrupted() ||
      (scope != null && scope.expired())
    ) {
      throw new InterruptedIOException(
        "Операция отменена или истёк срок ожидания"
      );
    }
  }

  private boolean expired() {
    return (
      cancelled ||
      System.nanoTime() >= deadline ||
      (previous != null && previous.expired())
    );
  }

  static int timeout(int configuredMillis) throws InterruptedIOException {
    check();
    NetworkScope scope = CURRENT.get();
    if (scope == null) return configuredMillis;
    long left = TimeUnit.NANOSECONDS.toMillis(
      scope.deadline - System.nanoTime()
    );
    return (int) Math.max(1, Math.min(configuredMillis, left));
  }

  static void track(HttpURLConnection connection)
    throws InterruptedIOException {
    check();
    NetworkScope scope = CURRENT.get();
    if (scope != null) {
      for (
        NetworkScope owner = scope;
        owner != null;
        owner = owner.previous
      ) owner.connections.add(connection);
      // Cancellation may have raced the first check/add.
      if (scope.expired()) {
        scope.connections.remove(connection);
        CLOSER.execute(connection::disconnect);
        check();
      }
    }
  }

  static int responseCode(HttpURLConnection connection)
    throws java.io.IOException {
    track(connection);
    return connection.getResponseCode();
  }

  static java.io.InputStream inputStream(HttpURLConnection connection)
    throws java.io.IOException {
    track(connection);
    return connection.getInputStream();
  }

  static java.io.OutputStream outputStream(HttpURLConnection connection)
    throws java.io.IOException {
    track(connection);
    return connection.getOutputStream();
  }

  static void disconnect(HttpURLConnection connection) {
    if (connection == null) return;
    NetworkScope scope = CURRENT.get();
    for (
      NetworkScope owner = scope;
      owner != null;
      owner = owner.previous
    ) owner.connections.remove(connection);
    connection.disconnect();
  }

  void cancel() {
    cancelled = true;
    for (HttpURLConnection connection : connections) {
      if (connections.remove(connection)) CLOSER.execute(
        connection::disconnect
      );
    }
  }

  @Override
  public void close() {
    cancel();
    if (CURRENT.get() == this) {
      if (previous == null) CURRENT.remove();
      else CURRENT.set(previous);
    }
  }
}
