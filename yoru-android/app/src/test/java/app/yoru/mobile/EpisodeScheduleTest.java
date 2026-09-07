package app.yoru.mobile;

import static org.junit.Assert.*;

import java.time.ZoneId;
import org.json.JSONObject;
import org.junit.Test;

public class EpisodeScheduleTest {

  private static Anime season(int total, int aired) {
    Anime anime = new Anime();
    anime.source = "yoru";
    anime.id = "123";
    anime.title = "Season";
    anime.status = "ongoing";
    anime.episodes = total;
    anime.episodesAired = aired;
    anime.episodesAiredKnown = true;
    for (int i = 1; i <= aired; i++) {
      Anime.Episode episode = new Anime.Episode();
      episode.id = "episode-" + i;
      episode.number = i;
      episode.streams.put(720, "https://fixture.invalid/" + i + ".m3u8");
      anime.episodeList.add(episode);
    }
    return anime;
  }

  @Test
  public void fifteenOutOfTwentyFourShowsEveryEpisodeWithCorrectStatus() {
    Anime anime = season(24, 15);
    anime.nextEpisodeAt = "2026-09-14T18:00:00Z";
    EpisodeSchedule.complete(anime);
    assertEquals(24, anime.episodeList.size());
    assertEquals(15, anime.aired());
    for (int i = 0; i < 24; i++) {
      Anime.Episode row = anime.episodeList.get(i);
      assertEquals(i + 1, row.number, 0);
      assertEquals(i >= 15, row.future);
      assertEquals(
        i >= 15 ? "Не вышла" : "Доступна",
        EpisodeSchedule.status(row)
      );
    }
    assertEquals(
      "14.09.2026, 18:00",
      EpisodeSchedule.dateText(
        anime.episodeList.get(15).airDate,
        ZoneId.of("UTC")
      )
    );
    assertEquals(
      "Дата уточняется",
      EpisodeSchedule.dateText(anime.episodeList.get(16).airDate)
    );
  }

  @Test
  public void upcomingRowsAreNotLimitedToTheNextTwentyFour() {
    Anime anime = season(60, 15);
    EpisodeSchedule.complete(anime);
    assertEquals(60, anime.episodeList.size());
    assertTrue(anime.episodeList.get(59).future);
  }

  @Test
  public void addingPlaceholdersDoesNotIncreaseAiredCount() {
    Anime anime = season(24, 15);
    anime.episodesAired = 0;
    anime.episodesAiredKnown = false;
    EpisodeSchedule.complete(anime);
    assertEquals(24, anime.episodeList.size());
    assertEquals(15, anime.aired());
    EpisodeSchedule.complete(anime);
    assertEquals(15, anime.aired());
  }

  @Test
  public void reconciliationIsIdempotentAndPreservesRealMedia() {
    Anime anime = season(24, 15);
    Anime.Episode first = anime.episodeList.get(0);
    EpisodeSchedule.complete(anime);
    EpisodeSchedule.complete(anime);
    assertEquals(24, anime.episodeList.size());
    assertSame(first, anime.episodeList.get(0));
    assertFalse(first.streams.isEmpty());
  }

  @Test
  public void explicitZeroDoesNotTurnGeneratedLocatorsIntoReleasedEpisodes() {
    Anime anime = season(24, 0);
    for (int i = 1; i <= 24; i++) {
      Anime.Episode locator = new Anime.Episode();
      locator.number = i;
      locator.synthetic = true;
      locator.variants.add(
        new Anime.Variant("", "", "https://fixture.invalid/player?episode=" + i)
      );
      anime.episodeList.add(locator);
    }
    EpisodeSchedule.complete(anime);
    assertEquals(0, anime.aired());
    for (Anime.Episode episode : anime.episodeList) assertTrue(episode.future);
  }

  @Test
  public void announcementShowsAllUpcomingRows() {
    Anime anime = season(12, 0);
    anime.status = "anons";
    EpisodeSchedule.complete(anime);
    assertEquals(12, anime.episodeList.size());
    assertEquals(0, anime.aired());
    assertEquals("Не вышла", EpisodeSchedule.status(anime.episodeList.get(0)));
  }

  @Test
  public void completedSeasonCanUseItsKnownTotalWhenProviderOmitsAired() {
    Anime anime = season(12, 0);
    anime.status = "released";
    EpisodeSchedule.complete(anime);
    assertEquals(12, anime.aired());
    for (Anime.Episode episode : anime.episodeList) assertFalse(episode.future);
  }

  @Test
  public void nextEpisodeBecomesAvailableAndNextDateMovesForward() {
    Anime anime = season(24, 15);
    anime.nextEpisodeAt = "2026-09-14T18:00:00Z";
    EpisodeSchedule.complete(anime);
    anime.episodesAired = 16;
    anime.nextEpisodeAt = "2026-09-21T18:00:00Z";
    EpisodeSchedule.complete(anime);
    assertFalse(anime.episodeList.get(15).future);
    assertEquals("", anime.episodeList.get(15).airDate);
    assertTrue(anime.episodeList.get(16).future);
    assertEquals("2026-09-21T18:00:00Z", anime.episodeList.get(16).airDate);
  }

  @Test
  public void explicitLaterDateIsRetainedWithoutInventingWeeklyDates() {
    Anime anime = season(24, 15);
    Anime.Episode later = new Anime.Episode();
    later.number = 18;
    later.future = true;
    later.synthetic = true;
    later.airDate = "2026-10-03";
    anime.episodeList.add(later);
    EpisodeSchedule.complete(anime);
    assertEquals(
      "Дата уточняется",
      EpisodeSchedule.dateText(anime.episodeList.get(16).airDate)
    );
    assertEquals(
      "03.10.2026",
      EpisodeSchedule.dateText(anime.episodeList.get(17).airDate)
    );
  }

  @Test
  public void dateIsConvertedToTheDeviceZone() {
    assertEquals(
      "14.09.2026, 21:00",
      EpisodeSchedule.dateText(
        "2026-09-14T18:00:00Z",
        ZoneId.of("Europe/Tallinn")
      )
    );
  }

  @Test
  public void missingDateDoesNotProduceNullOrFakeMidnight() {
    assertEquals("Дата уточняется", EpisodeSchedule.dateText("null"));
    assertEquals(
      "Дата уточняется",
      EpisodeSchedule.dateText("Дата уточняется")
    );
    assertEquals("14.09.2026", EpisodeSchedule.dateText("2026-09-14"));
  }

  @Test
  public void fractionalSpecialsAreNotLostOrCountedAsAnExtraMainEpisode() {
    Anime anime = season(24, 15);
    Anime.Episode special = new Anime.Episode();
    special.number = 12.5;
    anime.episodeList.add(special);
    EpisodeSchedule.complete(anime);
    assertEquals(25, anime.episodeList.size());
    assertEquals(15, anime.aired());
    assertSame(special, EpisodeLookup.exact(anime.episodeList, 12.5));
  }

  @Test
  public void unknownSeasonLengthDoesNotInventAFinalTotal() {
    Anime anime = season(0, 15);
    anime.nextEpisodeAt = "2026-09-14T18:00:00Z";
    EpisodeSchedule.complete(anime);
    assertEquals(0, anime.episodes);
    assertEquals(16, anime.episodeList.size());
    assertTrue(anime.episodeList.get(15).future);
  }

  @Test
  public void explicitZeroAndSyntheticFlagsSurviveSnapshots() throws Exception {
    Anime anime = season(24, 0);
    EpisodeSchedule.complete(anime);
    Anime copy = Anime.copy(anime);
    assertTrue(copy.episodesAiredKnown);
    assertEquals(0, copy.aired());
    assertTrue(copy.episodeList.get(0).synthetic);
    Anime legacy = Anime.from(
      new JSONObject()
        .put("provider", "yoru")
        .put("nativeId", "123")
        .put("title", "Legacy")
        .put("episodesAired", 15)
    );
    assertTrue(legacy.episodesAiredKnown);
  }

  @Test
  public void generatedNumbersAreBoundedForMalformedMetadata() {
    Anime anime = season(Integer.MAX_VALUE, 0);
    EpisodeSchedule.complete(anime);
    assertEquals(EpisodeSchedule.MAX_EPISODES, anime.episodeList.size());
  }
}
