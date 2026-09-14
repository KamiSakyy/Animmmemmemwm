package app.yoru.mobile;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.provider.DocumentsContract;
import androidx.media3.datasource.*;
import androidx.media3.exoplayer.offline.Download;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

final class DocumentDownloads {
    static final int REQUEST_FOLDER=4304;
    private static final Set<String> running=ConcurrentHashMap.newKeySet();
    private DocumentDownloads(){}
    static void choose(Activity activity){Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);try{activity.startActivityForResult(intent,REQUEST_FOLDER);}catch(Exception e){Ui.toast(activity,"Системный выбор папки недоступен");}}
    static void receive(Activity activity,int code,Intent data){if(code!=Activity.RESULT_OK||data==null||data.getData()==null)return;try{int grants=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);if((grants&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)==0)throw new SecurityException();activity.getContentResolver().takePersistableUriPermission(data.getData(),grants);YoruApp.app().store.downloadFolder(data.getData().toString());Ui.toast(activity,"Папка выбрана. Готовые видеофайлы будут сохранены сюда; HLS остаётся в офлайн YORU.");}catch(Exception e){Ui.toast(activity,"Не удалось получить постоянный доступ к папке");}}
    static void save(Context context,Download download,boolean manual){
        if(!OfflineExporter.canExport(download)){if(manual)Ui.toast(context,"Этот HLS/DASH доступен офлайн в YORU, но пока не экспортируется в папку");return;}
        String folder=YoruApp.app().store.downloadFolder();if(folder.isEmpty()){if(manual)Ui.toast(context,"Сначала выберите папку сохранения");return;}
        String identity=download.request.id+"|"+folder;if(!manual&&YoruApp.app().store.exportedDocument(identity).length()>0)return;if(!running.add(identity))return;
        Future<?> work=YoruApp.app().io.submit(()->{Uri target=null;DataSource source=null;try{
            ContentResolver resolver=context.getContentResolver();Uri tree=Uri.parse(folder);Uri parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
            String path=download.request.uri.getPath();String extension=path==null?"mp4":path.substring(path.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);String mime=extension.equals("webm")?"video/webm":extension.equals("mkv")?"video/x-matroska":"video/mp4";
            org.json.JSONObject metadata=DownloadHub.metadata(download);Anime anime=Anime.from(metadata.optJSONObject("anime"));String title=(anime.title+" — серия "+Ui.number(metadata.optDouble("episode",1))).replaceAll("[\\\\/:*?\"<>|]"," ");if(title.length()>120)title=title.substring(0,120);String name=title+" — "+System.currentTimeMillis()+"."+extension;
            target=DocumentsContract.createDocument(resolver,parent,mime,name);if(target==null)throw new IOException();
            DataSpec.Builder spec=new DataSpec.Builder().setUri(download.request.uri);if(download.request.customCacheKey!=null)spec.setKey(download.request.customCacheKey);source=YoruApp.app().mediaCache.offlineFactory().createDataSource();source.open(spec.build());long total=0;
            try(OutputStream output=resolver.openOutputStream(target,"w")){if(output==null)throw new IOException();byte[] buffer=new byte[65536];int count;while((count=source.read(buffer,0,buffer.length))!=-1){TaskQueue.check();output.write(buffer,0,count);total+=count;}output.flush();}
            if(total<=0||(download.contentLength>0&&total!=download.contentLength))throw new EOFException();YoruApp.app().store.exportedDocument(identity,target.toString());YoruApp.app().main.post(()->Ui.toast(context,"Видеофайл сохранён в выбранную папку"));
        }catch(Exception e){if(target!=null)try{DocumentsContract.deleteDocument(context.getContentResolver(),target);}catch(Exception ignored){}YoruApp.app().main.post(()->Ui.toast(context,"Не удалось сохранить в папку. Проверьте доступ и свободное место. Офлайн-загрузка осталась в YORU."));}finally{if(source!=null)try{source.close();}catch(Exception ignored){}running.remove(identity);}});
        if(work.isCancelled())running.remove(identity);
    }
}
