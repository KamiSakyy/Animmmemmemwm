package app.yoru.mobile;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class RetryAndScreenTest {

  @Test
  public void retriesAdvanceToThirdVariantInsteadOfOscillating() {
    RetryPlan plan = new RetryPlan();
    plan.failVariant("A");
    plan.failVariant("B");
    String next = Arrays.asList("A", "B", "C")
      .stream()
      .filter(plan::canTry)
      .findFirst()
      .orElse(null);
    assertEquals("C", next);
  }

  @Test
  public void lowestQualityDoesNotBounceBackToHigherFailure() {
    RetryPlan plan = new RetryPlan();
    plan.failQuality("A", 1080);
    assertEquals(
      Integer.valueOf(720),
      plan.nextLower("A", Arrays.asList(720, 1080), 1080)
    );
    plan.failQuality("A", 720);
    assertNull(plan.nextLower("A", Arrays.asList(720, 1080), 720));
  }

  @Test
  public void anotherVariantCanUseItsOwnQuality() {
    RetryPlan plan = new RetryPlan();
    plan.failQuality("A", 720);
    assertEquals(
      Integer.valueOf(720),
      plan.nextLower("B", Arrays.asList(720, 1080), 1080)
    );
  }

  @Test
  public void newEpisodeResetsRetryHistory() {
    RetryPlan plan = new RetryPlan();
    plan.failVariant("A");
    plan.reset();
    assertTrue(plan.canTry("A"));
  }

  @Test
  public void completedWorkerStillOwnsLoadingUntilUiCallback() {
    ScreenWork work = new ScreenWork();
    ScreenWork.Ticket ticket = work.begin(1);
    assertTrue(work.busy(1));
    assertEquals(Collections.singleton(1), work.cancelAll());
    work.finish(ticket);
    assertFalse(work.busy(1));
  }

  @Test
  public void staleCompletionCannotClearNewLoadingState() {
    ScreenWork work = new ScreenWork();
    ScreenWork.Ticket old = work.begin(1);
    work.cancelAll();
    ScreenWork.Ticket current = work.begin(1);
    work.finish(old);
    assertTrue(work.busy(1));
    work.finish(current);
    assertFalse(work.busy(1));
  }

  @Test
  public void multipleTasksKeepScreenBusyUntilAllUiResultsApply() {
    ScreenWork work = new ScreenWork();
    ScreenWork.Ticket one = work.begin(0),
      two = work.begin(0);
    work.finish(one);
    assertTrue(work.busy(0));
    work.finish(two);
    assertFalse(work.busy(0));
  }

  @Test
  public void enteringFinalEpisodeDoesNotMarkItCompleted() {
    assertFalse(PlaybackProgress.finished(0, 1440));
    assertFalse(PlaybackProgress.finished(100, 1440));
    assertTrue(PlaybackProgress.finished(1440, 1440));
    assertFalse(PlaybackProgress.finished(400, 0));
  }
}
