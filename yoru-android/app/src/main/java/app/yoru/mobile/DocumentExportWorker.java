package app.yoru.mobile;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.media3.exoplayer.offline.DefaultDownloadIndex;
import androidx.media3.exoplayer.offline.Download;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public final class DocumentExportWorker extends Worker {
    public DocumentExportWorker(@NonNull Context context,@NonNull WorkerParameters parameters){super(context,parameters);}
    @NonNull @Override public Result doWork(){
        String id=getInputData().getString("download"),folder=getInputData().getString("folder");boolean manual=getInputData().getBoolean("manual",false);
        if(id==null||id.isEmpty()||folder==null||folder.isEmpty())return Result.failure();
        try{
            if(isStopped())return Result.retry();
            Download download=new DefaultDownloadIndex(YoruApp.app().mediaCache.database()).getDownload(id);if(!OfflineExporter.canExport(download))return Result.failure();
            DocumentDownloads.copy(getApplicationContext(),download,folder,manual,this::isStopped);
            if(manual)YoruApp.app().main.post(()->Ui.toast(getApplicationContext(),"Видеофайл сохранён в выбранную папку"));
            return Result.success();
        }catch(Exception error){
            if(!(error instanceof SecurityException)&&getRunAttemptCount()<3)return Result.retry();
            if(manual)YoruApp.app().main.post(()->Ui.toast(getApplicationContext(),"Не удалось сохранить в папку. Проверьте доступ и свободное место. Офлайн-загрузка осталась в YORU."));
            return Result.failure();
        }
    }
}
