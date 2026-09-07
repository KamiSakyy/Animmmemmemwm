package app.yoru.mobile;

import android.os.SystemClock;
import android.util.Log;

/** Local diagnostics contain stage/type/timing, never signed URLs or user data. */
final class Perf {

  private Perf() {}

  static long start() {
    return SystemClock.elapsedRealtime();
  }

  static void end(String stage, long start) {
    long elapsed = SystemClock.elapsedRealtime() - start;
    if (BuildConfig.DEBUG || elapsed >= 500) Log.w(
      "YoruPerf",
      stage + " " + elapsed + "ms"
    );
  }

  static void failure(String stage, Throwable failure) {
    Log.w("YoruPerf", stage + ": " + failure.getClass().getSimpleName());
  }
}
