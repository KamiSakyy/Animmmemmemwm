package app.yoru.mobile;

import static org.junit.Assert.*;

import java.util.Arrays;
import org.junit.Test;

public class StableRenderingTest {

  @Test
  public void identicalApiCallbackDoesNotRequestASectionRebuild() {
    RenderState state = new RenderState();
    assertTrue(state.changed("hero", "poster-1"));
    assertFalse(state.changed("hero", "poster-1"));
    assertTrue(state.changed("episodes", "15/24"));
    assertFalse(state.changed("hero", "poster-1"));
  }

  @Test
  public void changingOneSectionDoesNotInvalidateTheOthers() {
    RenderState state = new RenderState();
    state.changed("hero", "same");
    state.changed("episodes", "15/24");
    assertTrue(state.changed("episodes", "16/24"));
    assertFalse(state.changed("hero", "same"));
  }

  @Test
  public void equivalentImageRequestIsRetainedWhileRunningOrComplete() {
    assertTrue(ImageBindPolicy.keepRequest("same", "same", true, false));
    assertTrue(ImageBindPolicy.keepRequest("same", "same", false, true));
  }

  @Test
  public void failedOrDifferentImageRequestCanRetry() {
    assertFalse(ImageBindPolicy.keepRequest("same", "same", false, false));
    assertFalse(ImageBindPolicy.keepRequest("old", "new", false, true));
  }

  @Test
  public void recycledCardDoesNotRetainAnotherAnimesPoster() {
    assertFalse(ImageBindPolicy.retainThumbnail("anime-1", "anime-2", true));
    assertTrue(ImageBindPolicy.retainThumbnail("anime-1", "anime-1", true));
  }

  @Test
  public void programmaticVoiceCallbackMatchesVariantNotResolvedCdnUri() {
    String variant = "https://fixture.invalid/player";
    assertTrue(PlaybackSelection.currentGroup(variant, Arrays.asList(variant)));
    assertFalse(
      PlaybackSelection.currentGroup(
        "https://cdn.invalid/video.m3u8",
        Arrays.asList(variant)
      )
    );
  }

  @Test
  public void fallbackInsideSameVoiceGroupDoesNotRestartTheFirstVariant() {
    assertTrue(PlaybackSelection.currentGroup("B", Arrays.asList("A", "B")));
    assertFalse(PlaybackSelection.currentGroup("B", Arrays.asList("C", "D")));
    assertFalse(PlaybackSelection.currentGroup("", Arrays.asList("A", "B")));
  }

  @Test
  public void releaseStatusChangeKeepsTheRowIdentity() {
    EpisodeRow future = new EpisodeRow(
      16,
      true,
      false,
      "",
      "Дата уточняется",
      "",
      0,
      "",
      "Скачать"
    );
    EpisodeRow available = new EpisodeRow(
      16,
      false,
      false,
      "",
      "",
      "poster",
      1440,
      "",
      "Скачать"
    );
    assertEquals(future.stableId(), available.stableId());
    assertNotEquals(future, available);
    assertEquals("Не вышла", future.status());
    assertEquals("Доступна", available.status());
  }

  @Test
  public void duplicateRowSnapshotDoesNotTriggerAContentChange() {
    EpisodeRow first = new EpisodeRow(
      16,
      true,
      false,
      "",
      "Дата уточняется",
      "",
      0,
      "",
      "Скачать"
    );
    EpisodeRow same = new EpisodeRow(
      16,
      true,
      false,
      "",
      "Дата уточняется",
      "",
      0,
      "",
      "Скачать"
    );
    assertEquals(first, same);
    assertEquals(first.hashCode(), same.hashCode());
  }
}
