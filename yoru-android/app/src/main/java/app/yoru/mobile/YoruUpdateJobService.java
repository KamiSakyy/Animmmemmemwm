package app.yoru.mobile;

import android.app.job.JobParameters;
import android.app.job.JobService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

public final class YoruUpdateJobService extends JobService {

  private final ConcurrentHashMap<JobParameters, Future<?>> jobs =
    new ConcurrentHashMap<>();

  @Override
  public boolean onStartJob(JobParameters params) {
    Future<?> future = YoruApp.app().discovery.submit(() -> {
      boolean retry;
      try {
        EpisodeUpdateReceiver.CheckResult result =
          EpisodeUpdateReceiver.performCheck(getApplicationContext(), "job");
        retry = result != null && result.shouldRetry;
      } catch (Exception error) {
        Perf.failure("episode-job", error);
        retry = true;
      }
      final boolean reschedule = retry;
      YoruApp.app().store.flushAsync();
      YoruApp.app().main.post(() -> {
        Future<?> finished = jobs.remove(params);
        if (finished != null && !finished.isCancelled()) jobFinished(
          params,
          reschedule
        );
      });
    });
    jobs.put(params, future);
    return true;
  }

  @Override
  public boolean onStopJob(JobParameters params) {
    Future<?> future = jobs.remove(params);
    if (future != null) future.cancel(true);
    return true;
  }

  @Override
  public void onDestroy() {
    for (Future<?> future : jobs.values()) future.cancel(true);
    jobs.clear();
    super.onDestroy();
  }
}
