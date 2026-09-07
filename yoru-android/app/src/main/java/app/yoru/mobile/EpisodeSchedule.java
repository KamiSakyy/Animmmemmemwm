package app.yoru.mobile;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Broadcast state is separate from the number of links returned by a player. */
final class EpisodeSchedule {

  static final int MAX_EPISODES = 10_000;

  private EpisodeSchedule() {}

  static boolean announced(String status) {
    String value = clean(status).toLowerCase(Locale.ROOT);
    return (
      value.equals("anons") ||
      value.contains("announcement") ||
      value.contains("анонс")
    );
  }

  static boolean finished(String status) {
    String value = clean(status).toLowerCase(Locale.ROOT);
    return (
      value.equals("released") ||
      value.equals("finished") ||
      value.equals("completed") ||
      value.contains("заверш")
    );
  }

  static int aired(Anime anime) {
    if (anime == null) return 0;
    int declared = Math.max(0, Math.min(MAX_EPISODES, anime.episodesAired));
    int total = Math.max(0, Math.min(MAX_EPISODES, anime.episodes));
    if (declared > 0) return total > 0 ? Math.min(declared, total) : declared;
    if (announced(anime.status)) return 0;
    if (finished(anime.status) && total > 0) return total;
    if (anime.episodesAiredKnown) return 0;
    int observed = 0;
    for (Anime.Episode episode : anime.episodeList) {
      if (
        episode == null ||
        episode.future ||
        episode.synthetic ||
        !validNumber(episode.number)
      ) continue;
      if (
        Math.abs(episode.number - Math.rint(episode.number)) < .001
      ) observed = Math.max(observed, (int) Math.rint(episode.number));
    }
    return total > 0 ? Math.min(observed, total) : observed;
  }

  static boolean validNumber(double number) {
    return Double.isFinite(number) && number >= 0 && number <= MAX_EPISODES;
  }

  static double canonical(double number) {
    return Math.rint(number * 1000) / 1000;
  }

  static String clean(String value) {
    if (value == null) return "";
    String text = value.trim();
    return text.equalsIgnoreCase("null") ? "" : text;
  }

  private static String knownDate(String raw) {
    String value = clean(raw);
    if (value.startsWith("Выйдет ")) value = value.substring(7).trim();
    if (value.toLowerCase(Locale.ROOT).contains("уточня")) return "";
    return value;
  }

  /** Idempotent reconciliation. Never invent a weekly release cadence. */
  static void complete(Anime anime) {
    if (anime == null) return;
    int aired = aired(anime);
    int total = Math.max(0, Math.min(MAX_EPISODES, anime.episodes));
    TreeMap<Double, Anime.Episode> rows = new TreeMap<>();
    for (Anime.Episode episode : anime.episodeList) {
      if (episode == null || !validNumber(episode.number)) continue;
      double number = canonical(episode.number);
      Anime.Episode previous = rows.get(number);
      if (
        previous == null || (previous.synthetic && !episode.synthetic)
      ) rows.put(number, episode);
    }
    int limit = total > 0 ? total : aired;
    if (
      total == 0 &&
      !knownDate(anime.nextEpisodeAt).isEmpty() &&
      aired < MAX_EPISODES
    ) limit = aired + 1;
    for (int number = 1; number <= limit; number++) {
      Anime.Episode episode = rows.get((double) number);
      if (episode == null) {
        episode = new Anime.Episode();
        episode.id = "schedule-" + number;
        episode.number = number;
        episode.synthetic = true;
        rows.put((double) number, episode);
      }
      boolean future = number > aired;
      boolean oldFuture = episode.future;
      episode.future = future;
      if (future) {
        String explicit = knownDate(episode.airDate);
        String next = number == aired + 1 ? knownDate(anime.nextEpisodeAt) : "";
        episode.airDate = !next.isEmpty() ? next : explicit;
        if (episode.synthetic) episode.name = "";
      } else if (oldFuture) {
        // Old generated date labels must not remain as an episode title after release.
        if (
          episode.synthetic ||
          episode.name.startsWith("Выйдет ") ||
          episode.name.contains("уточняется")
        ) episode.name = "";
        episode.airDate = "";
      }
    }
    anime.episodeList.clear();
    anime.episodeList.addAll(rows.values());
  }

  static String dateText(String raw) {
    return dateText(raw, ZoneId.systemDefault());
  }

  static String dateText(String raw, ZoneId zone) {
    String value = knownDate(raw);
    if (value.isEmpty()) return "Дата уточняется";
    DateTimeFormatter time = DateTimeFormatter.ofPattern(
      "dd.MM.yyyy, HH:mm",
      Locale.ROOT
    );
    try {
      return OffsetDateTime.parse(value).atZoneSameInstant(zone).format(time);
    } catch (RuntimeException ignored) {}
    try {
      return Instant.parse(value).atZone(zone).format(time);
    } catch (RuntimeException ignored) {}
    try {
      return LocalDateTime.parse(value).format(time);
    } catch (RuntimeException ignored) {}
    try {
      return LocalDate.parse(value).format(
        DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT)
      );
    } catch (RuntimeException ignored) {}
    // Older caches can already contain a human-readable date; keep it, not a fabricated time.
    return value;
  }

  static String label(Anime.Episode episode, boolean hideName) {
    String number =
      episode.number == Math.floor(episode.number)
        ? String.valueOf((int) episode.number)
        : String.valueOf(episode.number);
    String text = "Серия " + number + " · " + status(episode);
    if (episode.future) return text + " · " + dateText(episode.airDate);
    return (
      text + (hideName || episode.name.isEmpty() ? "" : " · " + episode.name)
    );
  }

  static String status(Anime.Episode episode) {
    return episode != null && episode.future ? "Не вышла" : "Доступна";
  }
}
