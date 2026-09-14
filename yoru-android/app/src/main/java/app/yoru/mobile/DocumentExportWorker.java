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
    private ForegroundInfo foreground(){
        Context context=getApplicationContext();NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);if(manager!=null)manager.createNotificationChannel(new NotificationChannel("yoru-exports","Сохранение видео",NotificationManager.IMPORTANCE_LOW));
        Intent intent=new Intent(context,MainActivity.class).putExtra("openDownloads",true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent open=PendingIntent.getActivity(context,4310,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(context,"yoru-exports").setSmallIcon(R.drawable.ic_download).setContentTitle("Сохраняем видео в папку").setContentText("Можно продолжать пользоваться YORU").setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setProgress(0,0,true).addAction(new Notification.Action.Builder(null,"Остановить",WorkManager.getInstance(context).createCancelPendingIntent(getId())).build()).build();
        int id=0x40000000|(getId().hashCode()&0x0fffffff);return Build.VERSION.SDK_INT>=29?new ForegroundInfo(id,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC):new ForegroundInfo(id,notification);
    }
    @NonNull @Override public Result doWork(){
        String id=getInputData().getString("download"),folder=getInputData().getString("folder");boolean manual=getInputData().getBoolean("manual",false);
        if(id==null||id.isEmpty()||folder==null||folder.isEmpty())return Result.failure();
        try{
            if(isStopped())return Result.retry();
            Download download=new DefaultDownloadIndex(YoruApp.app().mediaCache.database()).getDownload(id);if(!OfflineExporter.canExport(download))return Result.failure();
            setForegroundAsync(foreground()).get(15,TimeUnit.SECONDS);
            if(isStopped())return Result.retry();
            DocumentDownloads.copy(getApplicationContext(),download,folder,manual,getId().toString(),this::isStopped);
            if(manual)YoruApp.app().main.post(()->Ui.toast(getApplicationContext(),"Видеофайл сохранён в выбранную папку"));
            return Result.success();
        }catch(Exception error){
            if(!(error instanceof SecurityException)&&getRunAttemptCount()<3)return Result.retry();
            if(manual)YoruApp.app().main.post(()->Ui.toast(getApplicationContext(),"Не удалось сохранить в папку. Проверьте доступ и свободное место. Офлайн-загрузка осталась в YORU."));
            return Result.failure();
        }
    }
}
