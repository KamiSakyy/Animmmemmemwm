package app.yoru.mobile;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.Surface;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.transformer.Codec;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.ExoPlayerAssetLoader;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.ProgressHolder;
import androidx.media3.transformer.Transformer;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

final class AdaptiveVideoExport {
    private static final Semaphore slot=new Semaphore(1,true);
    private AdaptiveVideoExport(){}

    static boolean supports(Download download){
        if(download==null||download.state!=Download.STATE_COMPLETED||download.request==null)return false;
        if(download.request.keySetId!=null&&download.request.keySetId.length>0)return false;
        int type=Util.inferContentTypeForUriAndMimeType(download.request.uri,download.request.mimeType);
        return type==C.CONTENT_TYPE_HLS||type==C.CONTENT_TYPE_DASH;
    }

    static void discardDraft(Context context,String operation){if(operation!=null&&operation.matches("[a-zA-Z0-9-]+"))new File(context.getFilesDir(),"video-export-"+operation+".mp4").delete();}
    static void removeFinishedDrafts(Context context){
        File[] files=context.getFilesDir().listFiles(file->file.isFile()&&file.getName().matches("video-export-[0-9a-fA-F-]{36}\\.mp4"));if(files==null||files.length==0)return;long deadline=SystemClock.elapsedRealtime()+15_000;
        for(File file:files){if(SystemClock.elapsedRealtime()>=deadline||Thread.currentThread().isInterrupted())return;try{String name=file.getName();java.util.UUID id=java.util.UUID.fromString(name.substring(13,name.length()-4));androidx.work.WorkInfo work=androidx.work.WorkManager.getInstance(context).getWorkInfoById(id).get(5,TimeUnit.SECONDS);if(work==null||work.getState().isFinished())file.delete();}catch(Exception ignored){}}
    }

    static final class NoSpace extends IOException {}

    static final class Unsupported extends IOException {
        Unsupported(){super("Этот формат нельзя сохранить без изменения видео");}
    }

    private static final class CopyOnly implements Codec.DecoderFactory,Codec.EncoderFactory {
        private ExportException unavailable(){return ExportException.createForAssetLoader(new Unsupported(),ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED);}
        @Override public Codec createForAudioDecoding(Format format)throws ExportException{throw unavailable();}
        @Override public Codec createForVideoDecoding(Format format,Surface output,boolean toneMap)throws ExportException{throw unavailable();}
        @Override public Codec createForAudioEncoding(Format format)throws ExportException{throw unavailable();}
        @Override public Codec createForVideoEncoding(Format format)throws ExportException{throw unavailable();}
    }

    static File create(Context context,Download download,String operation,BooleanSupplier cancelled,IntConsumer progress)throws Exception {
        if(!supports(download)||!operation.matches("[a-zA-Z0-9-]+"))throw new Unsupported();
        long deadline=SystemClock.elapsedRealtime()+20L*60*1000;
        while(!slot.tryAcquire(1,TimeUnit.SECONDS)){if(cancelled.getAsBoolean()||SystemClock.elapsedRealtime()>=deadline)throw new InterruptedIOException();}
        HandlerThread thread=null;Handler handler=null;File output=new File(context.getFilesDir(),"video-export-"+operation+".mp4");
        AtomicReference<Transformer> transformer=new AtomicReference<>();AtomicReference<Exception> failure=new AtomicReference<>();AtomicBoolean disposed=new AtomicBoolean();CountDownLatch completed=new CountDownLatch(1);boolean success=false;
        try{
            if(cancelled.getAsBoolean())throw new InterruptedIOException();long expected=Math.max(0,download.getBytesDownloaded());if(expected>Math.max(0,context.getFilesDir().getUsableSpace()-32L*1024*1024))throw new NoSpace();
            if(output.exists()&&!output.delete())throw new IOException();
            thread=new HandlerThread("yoru-video-export");thread.start();handler=new Handler(thread.getLooper());Handler owner=handler;
            Runnable report=new Runnable(){@Override public void run(){if(disposed.get()||completed.getCount()==0||cancelled.getAsBoolean())return;Transformer active=transformer.get();if(active!=null){ProgressHolder holder=new ProgressHolder();int state=active.getProgress(holder);progress.accept(state==Transformer.PROGRESS_STATE_AVAILABLE?holder.progress:-1);}owner.postDelayed(this,1000);}};
            handler.post(()->{
                try{
                    if(disposed.get()||cancelled.getAsBoolean()){completed.countDown();return;}
                    CopyOnly codecs=new CopyOnly();DefaultMediaSourceFactory media=new DefaultMediaSourceFactory(YoruApp.app().mediaCache.offlineFactory());
                    Transformer active=new Transformer.Builder(context).setLooper(owner.getLooper()).setAssetLoaderFactory(new ExoPlayerAssetLoader.Factory(context,codecs,Clock.DEFAULT,media)).setEncoderFactory(codecs).addListener(new Transformer.Listener(){
                        @Override public void onCompleted(Composition composition,ExportResult result){if(result.videoFrameCount<=0)failure.set(new Unsupported());completed.countDown();}
                        @Override public void onError(Composition composition,ExportResult result,ExportException error){failure.set(error);completed.countDown();}
                    }).build();
                    transformer.set(active);if(disposed.get()||cancelled.getAsBoolean()){active.cancel();completed.countDown();return;}
                    active.start(new EditedMediaItem.Builder(YoruApp.app().downloads().offlineItem(download)).build(),output.getAbsolutePath());owner.post(report);
                }catch(Exception error){failure.set(error);completed.countDown();}
            });
            while(!completed.await(1,TimeUnit.SECONDS)){
                if(cancelled.getAsBoolean()||SystemClock.elapsedRealtime()>=deadline)throw new InterruptedIOException();
                if(context.getFilesDir().getUsableSpace()<16L*1024*1024)throw new NoSpace();
            }
            if(cancelled.getAsBoolean())throw new InterruptedIOException();Exception error=failure.get();if(error!=null)throw error;if(!output.isFile()||output.length()<=0)throw new IOException();success=true;return output;
        }finally{
            disposed.set(true);boolean interrupted=Thread.interrupted();
            try{
                if(handler!=null){CountDownLatch released=new CountDownLatch(1);handler.removeCallbacksAndMessages(null);handler.post(()->{try{Transformer active=transformer.get();if(active!=null)active.cancel();}finally{released.countDown();}});try{released.await(10,TimeUnit.SECONDS);}catch(InterruptedException error){interrupted=true;}}
                if(thread!=null){thread.quitSafely();try{thread.join(10_000);}catch(InterruptedException error){interrupted=true;}}
            }finally{if(!success)output.delete();slot.release();if(interrupted)Thread.currentThread().interrupt();}
        }
    }
}
