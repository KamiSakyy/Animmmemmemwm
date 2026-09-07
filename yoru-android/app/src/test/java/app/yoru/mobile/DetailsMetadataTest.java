package app.yoru.mobile;

import static org.junit.Assert.*;

import org.junit.Test;

public class DetailsMetadataTest {

  private Anime metadata() {
    Anime value = new Anime();
    value.source = "yoru";
    value.id = "1";
    value.title = "Season";
    value.episodes = 24;
    value.episodesAired = 15;
    value.episodesAiredKnown = true;
    value.status = "ongoing";
    value.nextEpisodeAt = "2026-09-14T18:00:00Z";
    return value;
  }

  @Test
  public void lateMetadataExpandsTheShortVideoList() {
    Anime video = metadata();
    video.episodes = 15;
    video.nextEpisodeAt = "";
    for (int i = 1; i <= 15; i++) {
      Anime.Episode episode = new Anime.Episode();
      episode.number = i;
      video.episodeList.add(episode);
    }
    DetailsMetadata.apply(video, metadata());
    assertEquals(24, video.episodeList.size());
    assertEquals(24, video.episodes);
    assertEquals(15, video.aired());
    assertTrue(video.episodeList.get(15).future);
  }

  @Test
  public void laterVideoCannotReplaceKnownMetadataWithAllReleasedGuess() {
    Anime video = metadata();
    video.episodesAired = 24;
    video.status = "released";
    DetailsMetadata.apply(video, metadata());
    assertEquals(15, video.aired());
    assertEquals("ongoing", video.status);
    assertEquals("2026-09-14T18:00:00Z", video.episodeList.get(15).airDate);
  }

  @Test
  public void olderEnrichmentDoesNotUndoNewerScheduleOrEpisodeGraph() {
    Anime current = metadata();
    EpisodeSchedule.complete(current);
    Anime.Episode first = current.episodeList.get(0);
    Anime rich = metadata();
    rich.episodes = 15;
    rich.episodesAired = 15;
    rich.nextEpisodeAt = "";
    rich.screenshots.add("https://fixture.invalid/shot.jpg");
    DetailsMetadata.applyVisuals(current, rich);
    assertEquals(24, current.episodes);
    assertSame(first, current.episodeList.get(0));
    assertEquals("2026-09-14T18:00:00Z", current.nextEpisodeAt);
    assertEquals(1, current.screenshots.size());
  }

  @Test
  public void changedBroadcastStatusDoesNotEraseMediaPayload() {
    Anime anime = metadata();
    Anime.Episode episode = new Anime.Episode();
    episode.number = 16;
    episode.streams.put(720, "https://fixture.invalid/video.m3u8");
    anime.episodeList.add(episode);
    EpisodeSchedule.complete(anime);
    assertTrue(episode.future);
    assertFalse(episode.streams.isEmpty());
    Anime next = metadata();
    next.episodesAired = 16;
    DetailsMetadata.apply(anime, next);
    assertFalse(episode.future);
    assertFalse(episode.streams.isEmpty());
  }

  @Test
  public void applyingSameObjectDoesNotClearGenres() {
    Anime metadata = metadata();
    metadata.genres.add("Fantasy");
    DetailsMetadata.apply(metadata, metadata);
    assertEquals(1, metadata.genres.size());
  }
}
