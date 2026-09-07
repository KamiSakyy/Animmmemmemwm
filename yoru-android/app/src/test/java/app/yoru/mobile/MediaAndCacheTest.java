package app.yoru.mobile;

import static org.junit.Assert.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

public class MediaAndCacheTest {

  @Test
  public void hlsRetainsMasterWithAudioAndSubtitles() {
    String master = "https://fixture.invalid/master.m3u8";
    String text =
      "#EXTM3U\n#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"ru\",URI=\"audio.m3u8\"\n" +
      "#EXT-X-STREAM-INF:BANDWIDTH=2000000,RESOLUTION=1280x720,AUDIO=\"ru\"\n720.m3u8\n" +
      "#EXT-X-STREAM-INF:BANDWIDTH=4000000,RESOLUTION=1920x1080,AUDIO=\"ru\"\n1080.m3u8\n";
    TreeMap<Integer, String> qualities = HlsManifests.qualities(text, master);
    assertEquals(new TreeSet<>(Arrays.asList(720, 1080)), qualities.keySet());
    assertEquals(master, qualities.get(720));
    assertEquals(master, qualities.get(1080));
  }

  @Test
  public void invalidManifestDoesNotInventVideo() {
    assertTrue(
      HlsManifests.qualities(
        "<html>Error</html>",
        "https://fixture.invalid/x"
      ).isEmpty()
    );
  }

  @Test
  public void renditionNeedsAnActualUriLine() {
    assertTrue(
      HlsManifests.qualities(
        "#EXTM3U\n#EXT-X-STREAM-INF:RESOLUTION=1920x1080\n",
        "https://fixture.invalid/x"
      ).isEmpty()
    );
  }

  @Test
  public void mediaContextIsPerUrlNotGlobalAcrossDownloads() {
    MediaHeaders.clear();
    MediaHeaders.remember("https://cdn.invalid/a", "https://a.invalid/player");
    MediaHeaders.remember("https://cdn.invalid/b", "https://b.invalid/player");
    assertEquals(
      "https://a.invalid/player",
      MediaHeaders.forUrl("https://cdn.invalid/a").get("Referer")
    );
    assertEquals(
      "https://b.invalid/player",
      MediaHeaders.forUrl("https://cdn.invalid/b").get("Referer")
    );
    assertFalse(
      MediaHeaders.forUrl("https://cdn.invalid/a").containsKey("Cookie")
    );
  }

  @Test
  public void invalidHeaderInputIsRejected() {
    MediaHeaders.clear();
    MediaHeaders.remember(
      "https://cdn.invalid/a",
      "https://fixture.invalid/\r\nInjected: value"
    );
    assertEquals(
      "https://yani.tv/",
      MediaHeaders.forUrl("https://cdn.invalid/a").get("Referer")
    );
  }

  @Test
  public void ttlUsesMonotonicInjectedClock() {
    AtomicLong clock = new AtomicLong();
    TimedCache<String, String> cache = new TimedCache<>(2, 100, clock::get);
    cache.put("a", "one");
    clock.set(99_000_000);
    assertEquals("one", cache.get("a"));
    clock.set(100_000_000);
    assertNull(cache.get("a"));
  }

  @Test
  public void cacheEvictsLeastRecentlyUsedEntry() {
    TimedCache<String, String> cache = new TimedCache<>(2, 60_000);
    cache.put("a", "one");
    cache.put("b", "two");
    cache.get("a");
    cache.put("c", "three");
    assertNull(cache.get("b"));
    assertEquals(2, cache.size());
    assertEquals("one", cache.get("a"));
    cache.clear();
    assertEquals(0, cache.size());
  }

  @Test
  public void cacheRejectsInvalidBounds() {
    assertThrows(IllegalArgumentException.class, () ->
      new TimedCache<>(0, 100)
    );
  }

  @Test
  public void actualResolutionLabelIsNotClampedToAFakeQuality() {
    assertEquals("240p", QualityPlus.streamLabel(240));
    assertEquals("4320p", QualityPlus.streamLabel(4320));
    assertEquals(
      720,
      QualityPlus.bestAtOrBelow(Arrays.asList(480, 720, 1080), 720)
    );
  }
}
