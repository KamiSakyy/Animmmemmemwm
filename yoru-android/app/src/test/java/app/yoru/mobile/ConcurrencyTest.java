package app.yoru.mobile;

import static org.junit.Assert.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;

public class ConcurrencyTest {

  @Test
  public void allForegroundWorkersStartWithoutFillingTheQueue()
    throws Exception {
    ThreadPoolExecutor pool = TaskExecutors.fixed("test-foreground", 4);
    CountDownLatch started = new CountDownLatch(4),
      release = new CountDownLatch(1);
    try {
      for (int i = 0; i < 4; i++) pool.submit(() -> {
        started.countDown();
        release.await();
        return null;
      });
      assertTrue(started.await(3, TimeUnit.SECONDS));
      assertEquals(4, pool.getActiveCount());
    } finally {
      release.countDown();
      pool.shutdownNow();
    }
  }

  @Test
  public void cancelledQueuedTaskIsRemovedAndHasTerminalState()
    throws Exception {
    ThreadPoolExecutor pool = TaskExecutors.fixed("test-queue", 1);
    CountDownLatch started = new CountDownLatch(1),
      release = new CountDownLatch(1);
    try {
      pool.submit(() -> {
        started.countDown();
        release.await();
        return null;
      });
      assertTrue(started.await(3, TimeUnit.SECONDS));
      Future<?> queued = pool.submit(() -> fail("Cancelled task ran"));
      assertEquals(1, pool.getQueue().size());
      assertTrue(queued.cancel(true));
      assertTrue(queued.isDone());
      assertTrue(queued.isCancelled());
      assertEquals(0, pool.getQueue().size());
      assertThrows(CancellationException.class, () -> queued.get());
    } finally {
      release.countDown();
      pool.shutdownNow();
    }
  }

  @Test
  public void acceptedTasksAreNotSilentlyDiscarded() throws Exception {
    ThreadPoolExecutor pool = TaskExecutors.fixed("test-retain", 2);
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger completed = new AtomicInteger();
    ArrayList<Future<?>> tasks = new ArrayList<>();
    try {
      for (int i = 0; i < 300; i++) tasks.add(
        pool.submit(() -> {
          release.await();
          completed.incrementAndGet();
          return null;
        })
      );
      release.countDown();
      for (Future<?> task : tasks) task.get(5, TimeUnit.SECONDS);
      assertEquals(300, completed.get());
    } finally {
      release.countDown();
      pool.shutdownNow();
    }
  }

  @Test
  public void oneHttpOwnerServesConcurrentWaiter() throws Exception {
    SingleFlight<String, String> flight = new SingleFlight<>();
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch entered = new CountDownLatch(1),
      release = new CountDownLatch(1),
      waiting = new CountDownLatch(1);
    AtomicInteger calls = new AtomicInteger();
    try {
      Future<String> owner = pool.submit(() ->
        flight.run("same", () -> {
          calls.incrementAndGet();
          entered.countDown();
          release.await();
          return "value";
        })
      );
      assertTrue(entered.await(3, TimeUnit.SECONDS));
      Future<String> waiter = pool.submit(() -> {
        waiting.countDown();
        return flight.run("same", () -> {
          calls.incrementAndGet();
          return "unexpected";
        });
      });
      assertTrue(waiting.await(3, TimeUnit.SECONDS));
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
      while (flight.waitingCount() == 0 && System.nanoTime() < deadline)
        Thread.yield();
      assertEquals(1, flight.waitingCount());
      release.countDown();
      assertEquals("value", owner.get(3, TimeUnit.SECONDS));
      String result = waiter.get(3, TimeUnit.SECONDS);
      assertEquals("value", result);
      assertEquals(1, calls.get());
      assertEquals(0, flight.size());
    } finally {
      release.countDown();
      pool.shutdownNow();
    }
  }

  @Test
  public void failedFlightDoesNotPoisonTheNextAttempt() throws Exception {
    SingleFlight<String, Integer> flight = new SingleFlight<>();
    assertThrows(IllegalStateException.class, () ->
      flight.run("key", () -> {
        throw new IllegalStateException();
      })
    );
    assertEquals(0, flight.size());
    assertEquals(Integer.valueOf(7), flight.run("key", () -> 7));
  }
}
