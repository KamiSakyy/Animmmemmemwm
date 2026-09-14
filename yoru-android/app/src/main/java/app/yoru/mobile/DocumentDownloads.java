package app.yoru.mobile;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.provider.DocumentsContract;
import androidx.media3.datasource.*;
import androidx.media3.exoplayer.offline.Download;
import androidx.work.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

final class DocumentDownloads {
    static final int REQUEST_FOLDER=4304;
    static final String EXPORT_TAG="yoru-export",EXPORT_REVISION_TAG="yoru-export-revision:",EXPORT_ITEM_TAG="yoru-export-download:",EXPORT_CREATED_TAG="yoru-export-created:";
    private DocumentDownloads(){}
    static void cancel(Context context,String download){WorkManager.getInstance(context).cancelAllWorkByTag(EXPORT_ITEM_TAG+download);String folder=YoruApp.app().store.downloadFolder();if(!folder.isEmpty())WorkManager.getInstance(context).cancelUniqueWork("yoru-document:"+download+"|"+folder);}
    static boolean canExport(Download download){if(download==null||download.request==null||(download.request.keySetId!=null&&download.request.keySetId.length>0))return false;return OfflineExporter.canExport(download)||AdaptiveVideoExport.supports(download)||(download!=null&&download.state==Download.STATE_COMPLETED&&download.request!=null&&(download.request.keySetId==null||download.request.keySetId.length==0)&&download.request.streamKeys.isEmpty()&&MediaSize.containerMime(download.request.mimeType));}
    static final class Changed extends IOException {}
    static String identity(Download download,String folder){return download.request.id+"|"+folder+"|"+DownloadHub.revision(download);}
    static void requireCurrent(Context context,Download download)throws IOException{
        Download current=new androidx.media3.exoplayer.offline.DefaultDownloadIndex(YoruApp.app().mediaCache.database()).getDownload(download.request.id);
        if(current==null||current.state!=Download.STATE_COMPLETED||!DownloadHub.revision(download).equals(DownloadHub.revision(current)))throw new Changed();
    }
    static boolean finished(Context context,Download download,String folder,boolean manual,String operation){String identity=identity(download,folder);SharedPreferences state=journal(context);return operation.equals(state.getString("operation:"+identity,""))||(!manual&&!state.getString("done:"+identity,"").isEmpty());}
    static void choose(Activity activity){choose(activity,"");}
    private static void choose(Activity activity,String download){
        if(!journal(activity).edit().putString("folder-request",download).commit()){Ui.toast(activity,"Не удалось подготовить выбор папки");return;}
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        try{activity.startActivityForResult(intent,REQUEST_FOLDER);}catch(Exception error){journal(activity).edit().remove("folder-request").apply();Ui.toast(activity,"Системный выбор папки недоступен");}
    }
    static void receive(Activity activity,int code,Intent data){
        String pending=journal(activity).getString("folder-request","");if(!journal(activity).edit().remove("folder-request").commit()){Ui.toast(activity,"Не удалось завершить выбор папки");return;}if(code!=Activity.RESULT_OK||data==null||data.getData()==null)return;
        try{int grants=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);if((grants&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)==0||data.getData().toString().length()>4096)throw new SecurityException();activity.getContentResolver().takePersistableUriPermission(data.getData(),grants);YoruApp.app().store.downloadFolder(data.getData().toString());Ui.toast(activity,"Папка выбрана. Готовые видео и звук будут сохраняться сюда.");if(!pending.isEmpty()){Download download=YoruApp.app().downloads().get(pending);if(download!=null)save(activity,download,true);}}catch(Exception error){Ui.toast(activity,"Не удалось получить постоянный доступ к папке");}
    }
    static void requireFolder(Context context,String folder){Uri uri=Uri.parse(folder);if(!"content".equals(uri.getScheme())||!DocumentsContract.isTreeUri(uri))throw new SecurityException();for(UriPermission permission:context.getContentResolver().getPersistedUriPermissions())if(permission.getUri().equals(uri)&&permission.isWritePermission())return;throw new SecurityException();}
    static void save(Context context,Download download,boolean manual){
        if(!canExport(download)){if(manual)Ui.toast(context,"Это видео можно смотреть без интернета в YORU, но пока нельзя сохранить отдельным файлом");return;}
        String folder=YoruApp.app().store.downloadFolder();if(folder.isEmpty()){if(manual&&context instanceof Activity)choose((Activity)context,download.request.id);else if(manual)Ui.toast(context,"Сначала выберите папку сохранения");return;}
        if(manual&&context instanceof Activity)try{requireFolder(context,folder);}catch(SecurityException error){choose((Activity)context,download.request.id);return;}
        String identity=identity(download,folder);SharedPreferences journal=journal(context);if(!manual&&!journal.getString("done:"+identity,"").isEmpty())return;
        Data data=new Data.Builder().putString("download",download.request.id).putString("folder",folder).putBoolean("manual",manual).putString("revision",DownloadHub.revision(download)).build();
        OneTimeWorkRequest task=new OneTimeWorkRequest.Builder(DocumentExportWorker.class).addTag(EXPORT_TAG).addTag(EXPORT_REVISION_TAG+DownloadHub.revision(download)).addTag(EXPORT_ITEM_TAG+download.request.id).addTag(EXPORT_CREATED_TAG+System.currentTimeMillis()).setInputData(data).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build();
        WorkManager.getInstance(context).enqueueUniqueWork("yoru-document:"+identity,ExistingWorkPolicy.KEEP,task);
        if(manual)Ui.toast(context,"Сохранение в папку поставлено в очередь");
    }
    static SharedPreferences journal(Context context){return context.getSharedPreferences("document-export-journal",Context.MODE_PRIVATE);}
    static void removeFinishedDocuments(Context context){
        SharedPreferences state=journal(context);long deadline=android.os.SystemClock.elapsedRealtime()+15000;
        for(Map.Entry<String,?> row:state.getAll().entrySet()){
            if(Thread.currentThread().isInterrupted()||android.os.SystemClock.elapsedRealtime()>=deadline)return;
            if(!row.getKey().startsWith("pending:")||!(row.getValue() instanceof String))continue;
            String identity=row.getKey().substring(8),operation=state.getString("pending-operation:"+identity,"");
            try{
                UUID id=UUID.fromString(operation);WorkInfo work=WorkManager.getInstance(context).getWorkInfoById(id).get(5,TimeUnit.SECONDS);
                if(work!=null&&!work.getState().isFinished())continue;
                Uri target=Uri.parse((String)row.getValue());
                synchronized(DocumentDownloads.class){if(!ownsPending(state,identity,operation,target))continue;}
                try{if(!DocumentsContract.deleteDocument(context.getContentResolver(),target))continue;}catch(FileNotFoundException ignored){}
                clearPending(state,identity,operation,target);
            }catch(InterruptedException error){Thread.currentThread().interrupt();return;}catch(Exception ignored){}
        }
    }
    private static boolean ownsPending(SharedPreferences journal,String identity,String operation,Uri target){return target.toString().equals(journal.getString("pending:"+identity,""))&&operation.equals(journal.getString("pending-operation:"+identity,""));}
    private static void clearPending(SharedPreferences journal,String identity,String operation,Uri target)throws IOException{
        synchronized(DocumentDownloads.class){
            if(ownsPending(journal,identity,operation,target)&&!journal.edit().remove("pending:"+identity).remove("pending-operation:"+identity).commit())throw new IOException();
        }
    }
    private static void check(BooleanSupplier cancelled)throws InterruptedIOException{if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new InterruptedIOException();}
    static void copy(Context context,Download download,String folder,boolean manual,String operation,BooleanSupplier cancelled,java.util.function.LongConsumer progress,File prepared,ExportCancellation cancellation)throws Exception{
        String identity=identity(download,folder);SharedPreferences journal=journal(context);
        if(finished(context,download,folder,manual,operation))return;check(cancelled);requireFolder(context,folder);
        ContentResolver resolver=context.getContentResolver();String pending,previous;
        synchronized(DocumentDownloads.class){pending=journal.getString("pending:"+identity,"");previous=journal.getString("pending-operation:"+identity,"");}
        if(!pending.isEmpty()){
            try{if(!DocumentsContract.deleteDocument(resolver,Uri.parse(pending)))throw new IOException();}catch(FileNotFoundException ignored){}
            clearPending(journal,identity,previous,Uri.parse(pending));
        }
        Uri target=null;DataSource source=null;
        try{
            check(cancelled);Uri tree=Uri.parse(folder);Uri parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
            String path=download.request.uri.getPath(),extension="video/webm".equals(download.request.mimeType)?"webm":"video/x-matroska".equals(download.request.mimeType)?"mkv":"mp4";
            if(prepared!=null)extension="mp4";
            if(prepared==null&&path!=null&&!MediaSize.containerMime(download.request.mimeType)){String lower=path.toLowerCase(Locale.ROOT);if(lower.endsWith(".webm"))extension="webm";else if(lower.endsWith(".mkv"))extension="mkv";else if(lower.endsWith(".m4v"))extension="m4v";}
            String mime=extension.equals("webm")?"video/webm":extension.equals("mkv")?"video/x-matroska":"video/mp4";
            org.json.JSONObject metadata=DownloadHub.metadata(download);Anime anime=Anime.from(metadata.optJSONObject("anime"));
            String title=((Anime.valid(anime)?YoruBrain.title(anime):"Аниме")+" — серия "+Ui.number(metadata.optDouble("episode",1))).replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]"," ").trim();if(title.length()>120)title=title.substring(0,120);
            target=DocumentsContract.createDocument(resolver,parent,mime,title+" — "+System.currentTimeMillis()+"."+extension);if(target==null)throw new IOException();
            synchronized(DocumentDownloads.class){check(cancelled);if(!journal.edit().putString("pending:"+identity,target.toString()).putString("pending-operation:"+identity,operation).commit())throw new IOException();}
            DataSpec.Builder spec=new DataSpec.Builder().setUri(prepared==null?download.request.uri:Uri.fromFile(prepared));if(prepared==null&&download.request.customCacheKey!=null)spec.setKey(download.request.customCacheKey);
            source=prepared==null?YoruApp.app().mediaCache.offlineFactory().createDataSource():new FileDataSource();long available=source.open(spec.build()),total=0;
            check(cancelled);
            android.os.ParcelFileDescriptor descriptor=resolver.openFileDescriptor(target,"w",cancellation.signal);
            if(descriptor==null)throw new IOException();
            OutputStream output=new android.os.ParcelFileDescriptor.AutoCloseOutputStream(descriptor);
            try{
                cancellation.attach(output);
                byte[] buffer=new byte[65536];int count;
                while(true){check(cancelled);count=source.read(buffer,0,buffer.length);if(count==-1)break;check(cancelled);output.write(buffer,0,count);total+=count;progress.accept(total);}
                check(cancelled);output.flush();
            }finally{try{output.close();}finally{cancellation.detach(output);}}
            check(cancelled);requireCurrent(context,download);long expected=prepared==null?(download.contentLength>0?download.contentLength:available>0?available:download.getBytesDownloaded()):prepared.length();if(total<=0||(expected>0&&total!=expected))throw new EOFException();
            synchronized(DocumentDownloads.class){
                check(cancelled);if(!ownsPending(journal,identity,operation,target))throw new InterruptedIOException();
                if(!journal.edit().putString("done:"+identity,target.toString()).putString("operation:"+identity,operation).putLong("bytes:"+identity,total).remove("pending:"+identity).remove("pending-operation:"+identity).commit())throw new IOException();
            }
        }catch(Exception error){
            if(target!=null)try{if(DocumentsContract.deleteDocument(resolver,target))clearPending(journal,identity,operation,target);}catch(Exception ignored){}
            throw error;
        }finally{if(source!=null)try{source.close();}catch(Exception ignored){}}
    }
}
