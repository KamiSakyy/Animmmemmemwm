package app.yoru.mobile;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class AnimeSnapshotTest {

  @Test
  public void preparedSnapshotKeepsEpisodesQualitiesAndLazySources() {
    Anime source = new Anime();
    source.id = "123";
    source.source = "yoru";
    source.title = "Fixture";
    source.blocked = true;
    Anime.Episode episode = new Anime.Episode();
    episode.number = 1;
    episode.streams.put(720, "https://fixture.invalid/720.m3u8");
    Anime.Variant variant = new Anime.Variant(
      "Voice",
      "Provider",
      "https://fixture.invalid/player"
    );
    variant.quality = 1080;
    episode.variants.add(variant);
    Anime.Episode lazy = new Anime.Episode();
    lazy.id = "55";
    lazy.source = "animelib";
    lazy.lazy = "animelib";
    episode.pending.add(lazy);
    source.episodeList.add(episode);
    Anime copy = Anime.copy(source);
    assertTrue(copy.blocked);
    assertEquals(1, copy.episodeList.size());
    assertEquals(1080, copy.episodeList.get(0).variants.get(0).quality);
    assertEquals("animelib", copy.episodeList.get(0).pending.get(0).source);
    copy.episodeList.get(0).streams.clear();
    copy.episodeList.get(0).variants.get(0).name = "Changed";
    copy.episodeList.get(0).pending.get(0).id = "changed";
    assertFalse(source.episodeList.get(0).streams.isEmpty());
    assertEquals("Voice", source.episodeList.get(0).variants.get(0).name);
    assertEquals("55", source.episodeList.get(0).pending.get(0).id);
  }

  @Test
  public void exactLookupDoesNotSubstituteNearestEpisode() {
    Anime.Episode one = new Anime.Episode();
    one.number = 1;
    Anime.Episode three = new Anime.Episode();
    three.number = 3;
    assertNull(EpisodeLookup.exact(Arrays.asList(one, three), 2));
    assertSame(three, EpisodeLookup.exact(Arrays.asList(one, three), 3));
  }

  @Test
  public void futureEpisodeCannotBeChosenAsPlayable() {
    Anime.Episode episode = new Anime.Episode();
    episode.number = 1;
    episode.future = true;
    assertNull(EpisodeLookup.exact(Collections.singletonList(episode), 1));
  }

  @Test
  public void fractionalEpisodesKeepTheirExactIdentity() {
    Anime.Episode episode = new Anime.Episode();
    episode.number = 12.5;
    assertSame(
      episode,
      EpisodeLookup.exact(Collections.singletonList(episode), 12.5)
    );
    assertNull(
      EpisodeLookup.exact(Collections.singletonList(episode), Double.NaN)
    );
  }
}
