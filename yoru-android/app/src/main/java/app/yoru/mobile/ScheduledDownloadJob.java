package app.yoru.mobile;

import android.app.job.*;
import android.os.SystemClock;
import androidx.media3.common.*;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.offline.*;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class ScheduledDownloadJob extends JobService {
    private volatile Thread worker;
    private volatile AtomicBoolean cancelled;

    @Override public boolean onStartJob(JobParameters params) {
        if(worker!=null)return false;
        AtomicBoolean stop=new AtomicBoolean(false);
        cancelled=stop;
        AtomicBoolean finishedJob=new AtomicBoolean(false);
        Runnable timeout=()->{if(stop.get()||!finishedJob.compareAndSet(false,true))return;stop.set(true);HttpTransport.cancel(stop);Thread active=worker;if(cancelled==stop&&active!=null)active.interrupt();jobFinished(params,true);};
        worker=new Thread(()->{
            try { TaskQueue.attach(stop);checkPlans(stop); }
            catch(Exception ignored) {}
            finally {
                TaskQueue.detach();worker=null;YoruApp.app().main.removeCallbacks(timeout);
                YoruApp.app().main.post(()->{if(!stop.get()&&finishedJob.compareAndSet(false,true)){jobFinished(params,false);YoruApp.app().discovery.execute(()->ScheduledDownloads.schedule(this));}});
            }
        },"yoru-scheduled-downloads");
        worker.setPriority(Thread.NORM_PRIORITY-1);
        YoruApp.app().main.postDelayed(timeout,75_000);
        worker.start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        if(cancelled!=null){cancelled.set(true);HttpTransport.cancel(cancelled);}
        Thread t=worker;
        if(t!=null)t.interrupt();
        return true;
    }

    private void checkPlans(AtomicBoolean stop) throws Exception {
        YoruApp app=YoruApp.app();
        long deadline=SystemClock.elapsedRealtime()+75_000;
        try(ScheduledDownloads store=new ScheduledDownloads(this)) {
            for(ScheduledDownloads.Plan p:store.all()) {
                TaskQueue.check();
                if(stop.get()||SystemClock.elapsedRealtime()>=deadline)return;
                if(p.state.equals("queued")||p.next>System.currentTimeMillis())continue;
                if(app.store.wifiDownloads()&&app.traffic.metered()) {
                    store.update(p,"waiting","Ожидает Wi-Fi",ScheduledDownloads.nextCheck(p.due));
                    continue;
                }
                Download existing=app.downloads().get(p.downloadId());
                if(existing!=null) {
                    store.update(p,"queued","Передано в загрузки — управление в списке загрузок",0);
                    continue;
                }
                store.update(p,"waiting","Проверяем выбранную озвучку и качество",ScheduledDownloads.nextCheck(p.due));
                try {
                    Anime fresh=app.api.details(Anime.from(p.anime.json()),false);
                    TaskQueue.check();
                    if(EpisodeRules.conflicts(p.anime,fresh)||!EpisodeRules.allowsNumber(fresh,p.episode)){store.update(p,"waiting","Уточняем состав серий — план сохранён",ScheduledDownloads.nextCheck(0));continue;}
                    List<ApiRepository.DownloadOption> options=app.api.downloadOptions(fresh,null,p.episode,p.voice,p.quality,!p.voice.isEmpty());
                    ApiRepository.DownloadOption best=null;
                    for(ApiRepository.DownloadOption option:options) {
                        if(!ScheduledDownloadRules.matches(p.episode,p.voice,p.quality,option))continue;
                        if(best==null||option.quality>best.quality)best=option;
                    }
                    TaskQueue.check();
                    if(stop.get()||!store.isCurrent(p))continue;
                    if(best==null) {
                        store.update(p,"waiting","Ожидает выбранную озвучку и качество",ScheduledDownloads.nextCheck(p.due));
                        continue;
                    }
                    if(getFilesDir().getUsableSpace()<100L*1024*1024) {
                        store.update(p,"waiting","Недостаточно места — освободите память",ScheduledDownloads.nextCheck(0));
                        continue;
                    }
                    if(EpisodeRules.conflicts(p.anime,best.source)){store.update(p,"waiting","Ожидает подходящий выпуск",ScheduledDownloads.nextCheck(p.due));continue;}
                    if(SystemClock.elapsedRealtime()+10_000>=deadline)return;
                    String stream=best.episode.streams.get(best.quality);MediaSize.Info info=MediaSize.probeInfo(stream);long bytes=info.bytes;TaskQueue.check();
                    if(!VideoResolver.downloadable(stream)&&!MediaSize.mediaMime(info.mime)){store.update(p,"waiting","Источник пока не подтвердил формат видео",ScheduledDownloads.nextCheck(p.due));continue;}
                    if(bytes>0&&bytes>getFilesDir().getUsableSpace()-32L*1024*1024){store.update(p,"waiting","Недостаточно места для файла · "+MediaSize.label(bytes),ScheduledDownloads.nextCheck(0));continue;}
                    if(stop.get()||!store.isCurrent(p))continue;
                    store.update(p,"waiting","Подготавливаем скачивание · "+MediaSize.label(bytes),ScheduledDownloads.nextCheck(p.due));
                    if(SystemClock.elapsedRealtime()+5_000>=deadline)return;
                    boolean accepted=enqueue(p,best,info.mime,stop,Math.min(30_000,deadline-SystemClock.elapsedRealtime()));
                    if(accepted)store.update(p,"queued","Передано в загрузки · "+MediaSize.label(bytes),0);
                    else if(!stop.get())store.update(p,"waiting","Повторим запуск позже; при ограничениях Android откройте YORU",ScheduledDownloads.nextCheck(0));
                } catch(java.io.InterruptedIOException|InterruptedException e) { Thread.currentThread().interrupt();return; }
                catch(Exception e) { store.update(p,"waiting","Источник пока недоступен — повторим проверку",ScheduledDownloads.nextCheck(p.due)); }
            }
        }
    }

    private boolean enqueue(ScheduledDownloads.Plan plan,ApiRepository.DownloadOption option,String mime,AtomicBoolean stop,long timeout) throws Exception {
        YoruApp app=YoruApp.app();
        CountDownLatch prepared=new CountDownLatch(1);
        AtomicBoolean finished=new AtomicBoolean(false),sent=new AtomicBoolean(false);
        AtomicReference<DownloadHelper> helper=new AtomicReference<>();
        JSONObject meta=new JSONObject().put("anime",plan.anime.json()).put("source",option.source.json())
                .put("episode",plan.episode).put("episodeId",option.episode.id).put("title",option.episode.name)
                .put("voice",option.voice).put("quality",option.quality).put("created",System.currentTimeMillis())
                .put("episodePoster",option.episode.poster);
        app.main.post(()->{
            if(stop.get()||finished.get()){prepared.countDown();return;}
            try {
                TrackSelectionParameters selection=DownloadTracks.parameters(this,option.quality);
                DownloadHelper h=DownloadHelper.forMediaItem(DownloadHub.item(option.episode.streams.get(option.quality),mime),selection,new DefaultRenderersFactory(this),app.mediaCache.http());
                helper.set(h);
                h.prepare(new DownloadHelper.Callback() {
                    @Override public void onPrepared(DownloadHelper ready) {
                        try {
                            if(stop.get()||finished.get())return;
                            DownloadTracks.requireSelectedQuality(ready,option.quality);
                            DownloadRequest request=ready.getDownloadRequest(plan.downloadId(),meta.toString().getBytes(StandardCharsets.UTF_8));
                            app.io.execute(()->{
                                try(ScheduledDownloads store=new ScheduledDownloads(ScheduledDownloadJob.this)){
                                    store.withCurrent(plan,()->{
                                        synchronized(finished){
                                            if(stop.get()||finished.get())return false;
                                            DownloadService.sendAddDownload(ScheduledDownloadJob.this,YoruDownloadService.class,request,true);sent.set(true);return true;
                                        }
                                    });
                                }catch(Exception ignored){}finally{prepared.countDown();}
                            });
                        } catch(Exception e) { prepared.countDown(); } finally { ready.release();helper.set(null); }
                    }
                    @Override public void onPrepareError(DownloadHelper ready,IOException error){ready.release();helper.set(null);prepared.countDown();}
                });
            } catch(Exception e){DownloadHelper h=helper.getAndSet(null);if(h!=null)h.release();prepared.countDown();}
        });
        try {
            if(!prepared.await(Math.max(1,timeout),TimeUnit.MILLISECONDS)||!sent.get())return false;
            long until=SystemClock.elapsedRealtime()+5000;
            while(!stop.get()&&SystemClock.elapsedRealtime()<until) {
                if(app.downloads().get(plan.downloadId())!=null)return true;
                Thread.sleep(100);
            }
            return false;
        } finally {
            synchronized(finished){finished.set(true);}
            app.main.post(()->{DownloadHelper h=helper.getAndSet(null);if(h!=null)h.release();});
        }
    }
}
