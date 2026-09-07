package app.yoru.mobile;

import static org.junit.Assert.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;

public class CoalescingWorkTest {

  @Test
  public void rapidUpdatesPersistOnlyLatestValueOffCallerThread()
    throws Exception {
    List<Integer> writes = Collections.synchronizedList(new ArrayList<>());
    AtomicReference<Thread> writerThread = new AtomicReference<>();
    Thread caller = Thread.currentThread();
    try (
      CoalescingWork worker = new CoalescingWork("test-writer", 60_000, error ->
        fail(error.toString())
      )
    ) {
      for (int i = 0; i < 100; i++) {
        int value = i;
        worker.submit("history", () -> {
          writerThread.set(Thread.currentThread());
          writes.add(value);
        });
      }
      assertTrue(writes.isEmpty());
      worker.awaitIdle(3000);
      assertEquals(Collections.singletonList(99), writes);
      assertNotSame(caller, writerThread.get());
    }
  }

  @Test
  public void distinctKeysAreAllPersistedSerially() throws Exception {
    List<String> writes = Collections.synchronizedList(new ArrayList<>());
    try (
      CoalescingWork worker = new CoalescingWork("test-writer", 60_000, error ->
        fail(error.toString())
      )
    ) {
      worker.submit("favorites", () -> writes.add("favorites"));
      worker.submit("history", () -> writes.add("history"));
      worker.submit("settings", () -> writes.add("settings"));
      worker.awaitIdle(3000);
      assertEquals(Arrays.asList("favorites", "history", "settings"), writes);
    }
  }

  @Test
  public void aFailureDoesNotDropOtherPendingWrites() throws Exception {
    AtomicInteger failures = new AtomicInteger(),
      saved = new AtomicInteger();
    try (
      CoalescingWork worker = new CoalescingWork("test-writer", 60_000, error ->
        failures.incrementAndGet()
      )
    ) {
      worker.submit("broken", () -> {
        throw new IllegalStateException();
      });
      worker.submit("history", saved::incrementAndGet);
      worker.awaitIdle(3000);
      assertEquals(1, failures.get());
      assertEquals(1, saved.get());
    }
  }

  @Test
  public void updatesDuringAnActiveWriteAreNotLost() throws Exception {
    CountDownLatch entered = new CountDownLatch(1),
      release = new CountDownLatch(1);
    AtomicInteger latest = new AtomicInteger();
    try (
      CoalescingWork worker = new CoalescingWork("test-writer", 60_000, error ->
        fail(error.toString())
      )
    ) {
      worker.submit("history", () -> {
        entered.countDown();
        try {
          release.await();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        latest.set(1);
      });
      worker.flush();
      assertTrue(entered.await(3, TimeUnit.SECONDS));
      worker.submit("history", () -> latest.set(2));
      release.countDown();
      worker.awaitIdle(3000);
      assertEquals(2, latest.get());
    } finally {
      release.countDown();
    }
  }
}
