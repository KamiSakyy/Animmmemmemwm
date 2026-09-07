package app.yoru.mobile;

import java.util.Objects;

/** Immutable UI snapshot: DiffUtil never reads mutable player variants. */
final class EpisodeRow {

  final double number;
  final boolean future, current;
  final String name, date, poster, downloadId, downloadLabel;
  final int duration;

  EpisodeRow(
    double number,
    boolean future,
    boolean current,
    String name,
    String date,
    String poster,
    int duration,
    String downloadId,
    String downloadLabel
  ) {
    this.number = EpisodeSchedule.canonical(number);
    this.future = future;
    this.current = current;
    this.name = name;
    this.date = date;
    this.poster = poster;
    this.duration = duration;
    this.downloadId = downloadId;
    this.downloadLabel = downloadLabel;
  }

  long stableId() {
    return Double.doubleToLongBits(number);
  }

  String status() {
    return future ? "Не вышла" : "Доступна";
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof EpisodeRow)) return false;
    EpisodeRow row = (EpisodeRow) other;
    return (
      Double.compare(number, row.number) == 0 &&
      future == row.future &&
      current == row.current &&
      duration == row.duration &&
      Objects.equals(name, row.name) &&
      Objects.equals(date, row.date) &&
      Objects.equals(poster, row.poster) &&
      Objects.equals(downloadId, row.downloadId) &&
      Objects.equals(downloadLabel, row.downloadLabel)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(
      number,
      future,
      current,
      name,
      date,
      poster,
      duration,
      downloadId,
      downloadLabel
    );
  }
}
