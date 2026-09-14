package app.yoru.mobile;

import android.content.Context;
import android.content.Intent;
import android.app.*;
import android.content.pm.ServiceInfo;
import android.os.Build;
import androidx.work.ForegroundInfo;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;
import androidx.annotation.NonNull;
import androidx.media3.exoplayer.offline.DefaultDownloadIndex;
import androidx.media3.exoplayer.offline.Download;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public final class DocumentExportWorker extends Worker {
    public DocumentExportWorker(@NonNull Context context,@NonNull WorkerParameters parameters){super(context,parameters);}
    private long lastProgress;
    private ForegroundInfo foreground(long copied,long total){return foreground("Сохраняем видео в папку",copied>0?"Сохранено "+Ui.bytes(copied)+(total>0?" из "+Ui.bytes(total):""):"Можно продолжать пользоваться YORU",total>0?(int)Math.min(99,copied*100.0/total):-1);}
    private ForegroundInfo foreground(String title,String message,int percent){
        Context context=getApplicationContext();NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);if(manager!=null)manager.createNotificationChannel(new NotificationChannel("yoru-exports","Сохранение видео",NotificationManager.IMPORTANCE_LOW));
        Intent intent=new Intent(context,MainActivity.class).putExtra("openDownloads",true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent open=PendingIntent.getActivity(context,4310,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(context,"yoru-exports").setSmallIcon(R.drawable.ic_download).setContentTitle(title).setContentText(message).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setProgress(percent>=0?100:0,Math.max(0,Math.min(99,percent)),percent<0).addAction(new Notification.Action.Builder(null,"Остановить",WorkManager.getInstance(context).createCancelPendingIntent(getId())).build()).build();
        int id=0x40000000|(getId().hashCode()&0x0fffffff);return Build.VERSION.SDK_INT>=29?new ForegroundInfo(id,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC):new ForegroundInfo(id,notification);
    }
    @NonNull @Override public Result doWork(){
        String id=getInputData().getString("download"),folder=getInputData().getString("folder");boolean manual=getInputData().getBoolean("manual",false);
        if(id==null||id.isEmpty()||folder==null||folder.isEmpty())return Result.failure();
        java.io.File prepared=null;
        try{
            if(isStopped())return Result.retry();
            Download download=new DefaultDownloadIndex(YoruApp.app().mediaCache.database()).getDownload(id);if(!DocumentDownloads.canExport(download))return Result.failure();
            if(DocumentDownloads.finished(getApplicationContext(),download,folder,manual,getId().toString())){AdaptiveVideoExport.discardDraft(getApplicationContext(),getId().toString());return Result.success();}
            DocumentDownloads.requireFolder(getApplicationContext(),folder);
            setForegroundAsync(foreground(0,download.contentLength)).get(15,TimeUnit.SECONDS);
            if(isStopped())return Result.retry();
            if(AdaptiveVideoExport.supports(download)){
                setForegroundAsync(foreground("Подготавливаем видеофайл","Видео и звук сохраняются без перекодирования",-1));
                prepared=AdaptiveVideoExport.create(getApplicationContext(),download,getId().toString(),this::isStopped,percent->{if(!isStopped())try{setForegroundAsync(foreground("Подготавливаем видеофайл",percent>=0?"Готово: "+percent+"%":"Объединяем видео и звук",percent));}catch(RuntimeException ignored){}});
            }
            long length=prepared==null?download.contentLength:prepared.length();lastProgress=0;
            DocumentDownloads.copy(getApplicationContext(),download,folder,manual,getId().toString(),this::isStopped,copied->{long now=android.os.SystemClock.elapsedRealtime();if(isStopped()||now-lastProgress<1000)return;lastProgress=now;try{setForegroundAsync(foreground(copied,length));}catch(RuntimeException ignored){}},prepared);
            if(manual&&!isStopped())YoruApp.app().main.post(()->Ui.toast(getApplicationContext(),"Видеофайл сохранён в выбранную папку"));
            return Result.success();
        }catch(Exception error){
            if(isStopped())return Result.failure();
            Throwable cause=error;for(int i=0;i<8&&cause.getCause()!=null&&cause.getCause()!=cause;i++)cause=cause.getCause();if(cause instanceof InterruptedException){Thread.currentThread().interrupt();return Result.retry();}
            if(cause instanceof AdaptiveVideoExport.Unsupported||error instanceof androidx.media3.transformer.ExportException){if(manual)YoruApp.app().main.post(()->Ui.toast(getApplicationContext(),"Этот вариант не удалось сохранить отдельным файлом. Он остался доступен внутри YORU."));return Result.failure();}
            if(!(cause instanceof SecurityException)&&getRunAttemptCount()<3)return Result.retry();
            if(manual)YoruApp.app().main.post(()->Ui.toast(getApplicationContext(),"Не удалось сохранить в папку. Проверьте доступ и свободное место. Офлайн-загрузка осталась в YORU."));
            return Result.failure();
        }finally{if(prepared!=null)prepared.delete();}
    }
}
