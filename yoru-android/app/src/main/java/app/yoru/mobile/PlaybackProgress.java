package app.yoru.mobile;

final class PlaybackProgress {

  private PlaybackProgress() {}

  static boolean finished(int seconds, int duration) {
    return duration > 0 && seconds > 0 && seconds >= Math.ceil(duration * .95);
  }
}
