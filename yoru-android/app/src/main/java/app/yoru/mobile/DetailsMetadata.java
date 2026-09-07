package app.yoru.mobile;

/** Main-thread merge rules. Arrival order must not decide the season length. */
final class DetailsMetadata {

  private DetailsMetadata() {}

  static void apply(Anime target, Anime metadata) {
    if (target == null || metadata == null) return;
    if (target == metadata) {
      EpisodeSchedule.complete(target);
      return;
    }
    if (!metadata.title.isEmpty()) target.title = metadata.title;
    if (!metadata.original.isEmpty()) target.original = metadata.original;
    if (!metadata.poster.isEmpty()) target.poster = metadata.poster;
    if (!metadata.description.isEmpty()) target.description =
      metadata.description;
    if (metadata.year > 0) target.year = metadata.year;
    if (!metadata.type.isEmpty()) target.type = metadata.type;
    if (!metadata.status.isEmpty()) target.status = metadata.status;
    if (!metadata.age.isEmpty()) target.age = metadata.age;
    if (!metadata.studio.isEmpty()) target.studio = metadata.studio;
    if (metadata.score > 0) target.score = metadata.score;
    if (metadata.episodes > 0) target.episodes = metadata.episodes;
    boolean known =
      metadata.episodesAiredKnown ||
      metadata.episodesAired > 0 ||
      EpisodeSchedule.announced(metadata.status) ||
      EpisodeSchedule.finished(metadata.status);
    if (known) {
      target.episodesAired = Math.max(0, metadata.episodesAired);
      target.episodesAiredKnown = true;
      target.nextEpisodeAt = EpisodeSchedule.clean(metadata.nextEpisodeAt);
    } else if (!EpisodeSchedule.clean(metadata.nextEpisodeAt).isEmpty()) {
      target.nextEpisodeAt = EpisodeSchedule.clean(metadata.nextEpisodeAt);
    }
    if (!metadata.genres.isEmpty()) {
      target.genres.clear();
      target.genres.addAll(metadata.genres);
    }
    if (target.related.isEmpty()) target.related.addAll(metadata.related);
    if (target.screenshots.isEmpty()) target.screenshots.addAll(
      metadata.screenshots
    );
    if (target.trailerUrl.isEmpty()) target.trailerUrl = metadata.trailerUrl;
    EpisodeSchedule.complete(target);
  }

  static void applyVisuals(Anime target, Anime rich) {
    if (target == null || rich == null) return;
    for (String screenshot : rich.screenshots)
      if (
        !screenshot.isEmpty() &&
        !target.screenshots.contains(screenshot) &&
        target.screenshots.size() < 12
      ) target.screenshots.add(screenshot);
    if (!rich.trailerUrl.isEmpty()) target.trailerUrl = rich.trailerUrl;
    if (!rich.related.isEmpty()) {
      target.related.clear();
      target.related.addAll(rich.related);
    }
    // Do not replace current episode data/counts with an older enrichment snapshot.
  }
}
