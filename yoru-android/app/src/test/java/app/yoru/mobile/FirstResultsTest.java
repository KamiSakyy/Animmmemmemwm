package app.yoru.mobile;

import static org.junit.Assert.*;

import java.util.*;
import java.util.concurrent.*;
import org.junit.Test;

public class FirstResultsTest {

  @Test
  public void fastResultDoesNotWaitForSlowSource() throws Exception {
    ExecutorService sources = Executors.newFixedThreadPool(2),
      caller = Executors.newSingleThreadExecutor();
    CountDownLatch slowEntered = new CountDownLatch(1),
      neverRelease = new CountDownLatch(1),
      cancelled = new CountDownLatch(1);
    try {
      List<Callable<String>> jobs = Arrays.asList(
        () -> {
          slowEntered.await();
          return "ready";
        },
        () -> {
          slowEntered.countDown();
          try {
            neverRelease.await();
            return "slow";
          } finally {
            cancelled.countDown();
          }
        }
      );
      Future<List<String>> result = caller.submit(() ->
        FirstResults.collect(
          sources,
          jobs,
          value -> true,
          false,
          false,
          5000,
          0,
          0
        )
      );
      assertEquals(
        Collections.singletonList("ready"),
        result.get(3, TimeUnit.SECONDS)
      );
      assertTrue(cancelled.await(3, TimeUnit.SECONDS));
    } finally {
      neverRelease.countDown();
      sources.shutdownNow();
      caller.shutdownNow();
    }
  }

  @Test
  public void strictVoiceDoesNotReturnAnUnwantedVoice() throws Exception {
    ExecutorService sources = Executors.newFixedThreadPool(2);
    try {
      List<Callable<String>> jobs = Arrays.asList(
        () -> "wrong",
        () -> "also-wrong"
      );
      assertTrue(
        FirstResults.collect(
          sources,
          jobs,
          "wanted"::equals,
          true,
          false,
          1000,
          0,
          0
        ).isEmpty()
      );
    } finally {
      sources.shutdownNow();
    }
  }

  @Test
  public void strictVoiceWaitsForTheWantedResult() throws Exception {
    ExecutorService sources = Executors.newFixedThreadPool(2);
    try {
      List<Callable<String>> jobs = Arrays.asList(
        () -> "wrong",
        () -> "wanted"
      );
      List<String> result = FirstResults.collect(
        sources,
        jobs,
        "wanted"::equals,
        true,
        false,
        2000,
        0,
        0
      );
      assertTrue(result.contains("wanted"));
    } finally {
      sources.shutdownNow();
    }
  }

  @Test
  public void fullDiscoveryKeepsAllCompletedVoices() throws Exception {
    ExecutorService sources = Executors.newFixedThreadPool(3);
    try {
      List<Callable<String>> jobs = Arrays.asList(
        () -> "one",
        () -> "two",
        () -> "three"
      );
      List<String> result = FirstResults.collect(
        sources,
        jobs,
        value -> true,
        false,
        true,
        2000,
        0,
        0
      );
      assertEquals(
        new HashSet<>(Arrays.asList("one", "two", "three")),
        new HashSet<>(result)
      );
    } finally {
      sources.shutdownNow();
    }
  }

  @Test
  public void oneBrokenSourceDoesNotHideTheWorkingOne() throws Exception {
    ExecutorService sources = Executors.newFixedThreadPool(2);
    try {
      List<Callable<String>> jobs = Arrays.asList(
        () -> {
          throw new IllegalStateException();
        },
        () -> "working"
      );
      assertEquals(
        Collections.singletonList("working"),
        FirstResults.collect(
          sources,
          jobs,
          value -> true,
          false,
          false,
          2000,
          0,
          0
        )
      );
    } finally {
      sources.shutdownNow();
    }
  }
}
