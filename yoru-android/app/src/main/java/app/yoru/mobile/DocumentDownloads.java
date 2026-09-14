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
    private DocumentDownloads(){}
    static void choose(Activity activity){Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);try{activity.startActivityForResult(intent,REQUEST_FOLDER);}catch(Exception e){Ui.toast(activity,"Системный выбор папки недоступен");}}
    static void receive(Activity activity,int code,Intent data){if(code!=Activity.RESULT_OK||data==null||data.getData()==null)return;try{int grants=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);if((grants&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)==0||data.getData().toString().length()>4096)throw new SecurityException();activity.getContentResolver().takePersistableUriPermission(data.getData(),grants);YoruApp.app().store.downloadFolder(data.getData().toString());Ui.toast(activity,"Папка выбрана. Готовые видеофайлы будут сохраняться сюда. Некоторые форматы доступны только внутри YORU.");}catch(Exception e){Ui.toast(activity,"Не удалось получить постоянный доступ к папке");}}
    static void save(Context context,Download download,boolean manual){
        if(!OfflineExporter.canExport(download)){if(manual)Ui.toast(context,"Это видео можно смотреть без интернета в YORU, но пока нельзя сохранить отдельным файлом");return;}
        String folder=YoruApp.app().store.downloadFolder();if(folder.isEmpty()){if(manual)Ui.toast(context,"Сначала выберите папку сохранения");return;}
        String identity=download.request.id+"|"+folder;SharedPreferences journal=journal(context);if(!manual&&!journal.getString("done:"+identity,"").isEmpty())return;
        Data data=new Data.Builder().putString("download",download.request.id).putString("folder",folder).putBoolean("manual",manual).build();
        OneTimeWorkRequest task=new OneTimeWorkRequest.Builder(DocumentExportWorker.class).setInputData(data).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build();
        WorkManager.getInstance(context).enqueueUniqueWork("yoru-document:"+identity,ExistingWorkPolicy.KEEP,task);
        if(manual)Ui.toast(context,"Сохранение в папку поставлено в очередь");
    }
    static SharedPreferences journal(Context context){return context.getSharedPreferences("document-export-journal",Context.MODE_PRIVATE);}
    static void copy(Context context,Download download,String folder,boolean manual,String operation,BooleanSupplier cancelled,java.util.function.LongConsumer progress)throws Exception{
        String identity=download.request.id+"|"+folder;SharedPreferences journal=journal(context);if(operation.equals(journal.getString("operation:"+identity,""))||(!manual&&!journal.getString("done:"+identity,"").isEmpty()))return;
        ContentResolver resolver=context.getContentResolver();String pending=journal.getString("pending:"+identity,"");if(!pending.isEmpty()){try{if(!DocumentsContract.deleteDocument(resolver,Uri.parse(pending)))throw new IOException();}catch(FileNotFoundException ignored){}if(!journal.edit().remove("pending:"+identity).commit())throw new IOException();}
        Uri target=null;DataSource source=null;
        try{
            Uri tree=Uri.parse(folder);Uri parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
            String path=download.request.uri.getPath();String extension=path==null?"mp4":path.substring(path.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);String mime=extension.equals("webm")?"video/webm":extension.equals("mkv")?"video/x-matroska":"video/mp4";
            org.json.JSONObject metadata=DownloadHub.metadata(download);Anime anime=Anime.from(metadata.optJSONObject("anime"));String title=(anime.title+" — серия "+Ui.number(metadata.optDouble("episode",1))).replaceAll("[\\\\/:*?\"<>|]"," ");if(title.length()>120)title=title.substring(0,120);
            target=DocumentsContract.createDocument(resolver,parent,mime,title+" — "+System.currentTimeMillis()+"."+extension);if(target==null)throw new IOException();if(!journal.edit().putString("pending:"+identity,target.toString()).commit())throw new IOException();
            DataSpec.Builder spec=new DataSpec.Builder().setUri(download.request.uri);if(download.request.customCacheKey!=null)spec.setKey(download.request.customCacheKey);source=YoruApp.app().mediaCache.offlineFactory().createDataSource();source.open(spec.build());long total=0;
            try(OutputStream output=resolver.openOutputStream(target,"w")){if(output==null)throw new IOException();byte[] buffer=new byte[65536];int count;while((count=source.read(buffer,0,buffer.length))!=-1){if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new InterruptedIOException();output.write(buffer,0,count);total+=count;progress.accept(total);}output.flush();}
            if(cancelled.getAsBoolean())throw new InterruptedIOException();if(total<=0||(download.contentLength>0&&total!=download.contentLength))throw new EOFException();
            if(!journal.edit().putString("done:"+identity,target.toString()).putString("operation:"+identity,operation).remove("pending:"+identity).commit())throw new IOException();
        }catch(Exception error){if(target!=null)try{if(DocumentsContract.deleteDocument(resolver,target))journal.edit().remove("pending:"+identity).commit();}catch(Exception ignored){}throw error;}finally{if(source!=null)try{source.close();}catch(Exception ignored){}}
    }
}
