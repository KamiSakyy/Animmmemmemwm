package app.yoru.mobile;

import static org.junit.Assert.*;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import org.junit.Test;

public class NetworkAndResolutionTest {

  private static final class Connection extends HttpURLConnection {

    final CountDownLatch disconnected = new CountDownLatch(1);

    Connection() throws Exception {
      super(new URL("https://fixture.invalid/"));
    }

    @Override
    public void disconnect() {
      disconnected.countDown();
    }

    @Override
    public boolean usingProxy() {
      return false;
    }

    @Override
    public void connect() {}

    @Override
    public int getResponseCode() {
      return 200;
    }
  }

  @Test
  public void cancellationClosesTrackedConnection() throws Exception {
    Connection connection = new Connection();
    try (NetworkScope scope = NetworkScope.open(10_000)) {
      assertEquals(200, NetworkScope.responseCode(connection));
      scope.cancel();
      assertTrue(connection.disconnected.await(3, TimeUnit.SECONDS));
      assertThrows(InterruptedIOException.class, NetworkScope::check);
    }
    NetworkScope.check();
  }

  @Test
  public void parentCancellationAlsoClosesNestedConnections() throws Exception {
    Connection connection = new Connection();
    try (
      NetworkScope parent = NetworkScope.open(10_000);
      NetworkScope child = NetworkScope.open(20_000)
    ) {
      NetworkScope.track(connection);
      parent.cancel();
      assertTrue(connection.disconnected.await(3, TimeUnit.SECONDS));
      assertThrows(InterruptedIOException.class, NetworkScope::check);
    }
  }

  @Test
  public void nestedDeadlineCannotExtendParentBudget() throws Exception {
    try (
      NetworkScope parent = NetworkScope.open(10_000);
      NetworkScope child = NetworkScope.open(60_000)
    ) {
      assertTrue(NetworkScope.timeout(30_000) <= 10_000);
      assertEquals(5000, NetworkScope.timeout(5000));
    }
  }

  @Test
  public void interruptedCallerDoesNotStartMoreWork() throws Exception {
    try {
      Thread.currentThread().interrupt();
      assertThrows(InterruptedIOException.class, NetworkScope::check);
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  public void cyclicIframeChainFailsNormallyInsteadOfOverflowingStack() {
    assertThrows(IOException.class, () ->
      ResolutionWalk.visit("A", () ->
        ResolutionWalk.visit("B", () -> ResolutionWalk.visit("A", () -> "bad"))
      )
    );
  }

  @Test
  public void resolverStateIsClearedAfterAnError() throws Exception {
    assertThrows(IOException.class, () ->
      ResolutionWalk.visit("A", () -> ResolutionWalk.visit("A", () -> "bad"))
    );
    assertEquals("ok", ResolutionWalk.visit("A", () -> "ok"));
  }

  @Test
  public void longIframeChainHasABoundedDepth() {
    assertThrows(IOException.class, () -> descend(0));
  }

  private String descend(int depth) throws Exception {
    return ResolutionWalk.visit("page-" + depth, () ->
      depth == 20 ? "too-far" : descend(depth + 1)
    );
  }
}
