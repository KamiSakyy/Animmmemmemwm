package app.yoru.mobile;

import java.util.List;

final class EpisodeLookup {

  private EpisodeLookup() {}

  static Anime.Episode exact(List<Anime.Episode> episodes, double number) {
    if (episodes == null || !Double.isFinite(number)) return null;
    for (Anime.Episode episode : episodes)
      if (
        episode != null &&
        !episode.future &&
        Math.abs(episode.number - number) < .001
      ) return episode;
    return null;
  }
}
