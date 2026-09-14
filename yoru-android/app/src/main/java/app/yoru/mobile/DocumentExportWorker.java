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
    private final ExportCancellation cancellation=new ExportCancellation();
    @Override public void onStopped(){cancellation.cancel();super.onStopped();}
    private boolean cancelled(){return isStopped()||cancellation.isStopped();}
    private long lastProgress;
    private androidx.work.Data output(String result){return new androidx.work.Data.Builder().putString("result",result).putLong("updated",System.currentTimeMillis()).build();}
    private void publish(String stage,int percent,long copied,long total){
        if(isStopped())return;
        try{setProgressAsync(new androidx.work.Data.Builder().putString("stage",stage).putInt("percent",percent<0?-1:Math.min(99,percent)).putLong("copied",copied).putLong("total",total).build());}catch(RuntimeException ignored){}
    }
    private static String failureReason(Throwable error){
        Throwable cause=error;
        for(int i=0;cause!=null&&i<10;i++){
            if(cause instanceof SecurityException)return "permission";
            if(cause instanceof DocumentDownloads.Changed)return "changed";
            if(cause instanceof AdaptiveVideoExport.NoSpace)return "space";
            if(cause instanceof android.system.ErrnoException&&((android.system.ErrnoException)cause).errno==android.system.OsConstants.ENOSPC)return "space";
            if(cause instanceof AdaptiveVideoExport.Unsupported)return "unsupported";
            if(cause.getCause()==cause)break;cause=cause.getCause();
        }
        return "failed";
    }
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
        if(id==null||id.isEmpty()||folder==null||folder.isEmpty())return Result.failure(output("failed"));
        java.io.File prepared=null;
        try{
            if(isStopped())return Result.retry();
            Download download=new DefaultDownloadIndex(YoruApp.app().mediaCache.database()).getDownload(id);if(download==null||download.state!=Download.STATE_COMPLETED)return Result.failure(output("missing"));if(!DocumentDownloads.canExport(download))return Result.failure(output("unsupported"));
            String revision=getInputData().getString("revision");if(revision==null||!revision.equals(DownloadHub.revision(download)))return Result.failure(output("changed"));
            if(DocumentDownloads.finished(getApplicationContext(),download,folder,manual,getId().toString())){AdaptiveVideoExport.discardDraft(getApplicationContext(),getId().toString());return Result.success(output("saved"));}
            DocumentDownloads.requireFolder(getApplicationContext(),folder);
            setForegroundAsync(foreground(0,download.contentLength)).get(15,TimeUnit.SECONDS);
            publish("copy",-1,0,download.contentLength);
            if(isStopped())return Result.retry();
            if(AdaptiveVideoExport.supports(download)){
                setForegroundAsync(foreground("Подготавливаем видеофайл","Видео и звук сохраняются без перекодирования",-1));
                publish("prepare",-1,0,-1);
                prepared=AdaptiveVideoExport.create(getApplicationContext(),download,getId().toString(),this::cancelled,percent->{publish("prepare",percent,0,-1);if(!isStopped())try{setForegroundAsync(foreground("Подготавливаем видеофайл",percent>=0?"Готово: "+percent+"%":"Объединяем видео и звук",percent));}catch(RuntimeException ignored){}});
            }
            long length=prepared==null?(download.contentLength>0?download.contentLength:download.getBytesDownloaded()):prepared.length();lastProgress=0;publish("copy",length>0?0:-1,0,length);
            DocumentDownloads.copy(getApplicationContext(),download,folder,manual,getId().toString(),this::cancelled,copied->{long now=android.os.SystemClock.elapsedRealtime();if(isStopped()||now-lastProgress<1000)return;lastProgress=now;publish("copy",length>0?(int)Math.min(99,copied*100.0/length):-1,copied,length);try{setForegroundAsync(foreground(copied,length));}catch(RuntimeException ignored){}},prepared,cancellation);
            if(manual&&!isStopped())YoruApp.app().main.post(()->Ui.toast(getApplicationContext(),"Видеофайл сохранён в выбранную папку"));
            return Result.success(output("saved"));
        }catch(Exception error){
            if(isStopped())return Result.failure(output("failed"));
            Throwable cause=error;for(int i=0;i<8&&cause.getCause()!=null&&cause.getCause()!=cause;i++)cause=cause.getCause();if(cause instanceof InterruptedException){Thread.currentThread().interrupt();return Result.retry();}
            String reason=failureReason(error);
            if("changed".equals(reason))return Result.failure(output(reason));
            if("unsupported".equals(reason)){if(manual)YoruApp.app().main.post(()->Ui.toast(getApplicationContext(),"Этот вариант нельзя сохранить без изменения видео. Офлайн-загрузка не удалена."));return Result.failure(output(reason));}
            if("failed".equals(reason)&&getRunAttemptCount()<3)return Result.retry();
            if(manual)YoruApp.app().main.post(()->Ui.toast(getApplicationContext(),"Не удалось сохранить в папку. Проверьте доступ и свободное место. Офлайн-загрузка не удалена."));
            return Result.failure(output(reason));
        }finally{if(prepared!=null)prepared.delete();}
    }
}
