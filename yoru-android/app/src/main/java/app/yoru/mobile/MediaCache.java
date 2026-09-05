package app.yoru.mobile;

import android.content.Context;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.*;
import androidx.media3.datasource.cache.*;
import java.io.File;
import java.util.*;

public final class MediaCache {
    private final Context context;private final StandaloneDatabaseProvider database;private SimpleCache stream,offline;
    public MediaCache(Context c){context=c.getApplicationContext();database=new StandaloneDatabaseProvider(context);}
    public StandaloneDatabaseProvider database(){return database;}
    public DefaultHttpDataSource.Factory http(){HashMap<String,String> headers=new HashMap<>();headers.put("Accept-Language","ru-RU,ru;q=0.9,en;q=0.5");headers.put("Referer","https://animego.me/");return new DefaultHttpDataSource.Factory().setUserAgent("YORU-Android/2.1").setDefaultRequestProperties(headers).setConnectTimeoutMs(15000).setReadTimeoutMs(25000).setAllowCrossProtocolRedirects(false);}
    public synchronized SimpleCache offline(){if(offline==null)offline=new SimpleCache(new File(context.getFilesDir(),"offline-media"),new NoOpCacheEvictor(),database);return offline;}
    public synchronized SimpleCache temporary(){if(stream==null)stream=new SimpleCache(new File(context.getCacheDir(),"video"),new LeastRecentlyUsedCacheEvictor(256L*1024*1024),database);return stream;}
    public CacheDataSource.Factory onlineFactory(){return new CacheDataSource.Factory().setCache(temporary()).setUpstreamDataSourceFactory(new DefaultDataSource.Factory(context,http())).setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);}
    public CacheDataSource.Factory offlineFactory(){return new CacheDataSource.Factory().setCache(offline()).setUpstreamDataSourceFactory(null).setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE);}
    public synchronized long offlineBytes(){return offline().getCacheSpace();}
    public synchronized void clearVideoCache(){if(stream!=null){stream.release();stream=null;}erase(new File(context.getCacheDir(),"video"));}
    public static long size(File f){if(!f.exists())return 0;if(f.isFile())return f.length();long n=0;File[] children=f.listFiles();if(children!=null)for(File x:children)n+=size(x);return n;}
    public static void erase(File f){if(!f.exists())return;if(f.isDirectory()){File[] children=f.listFiles();if(children!=null)for(File x:children)erase(x);}f.delete();}
    public long temporaryBytes(){long n=size(context.getCacheDir());File web=new File(context.getDataDir(),"app_webview/Default");for(String path:new String[]{"Cache","HTTP Cache","Code Cache","GPUCache"})n+=size(new File(web,path));return n;}
    public void clearTemporary(){clearVideoCache();File[] kids=context.getCacheDir().listFiles();if(kids!=null)for(File f:kids)erase(f);File web=new File(context.getDataDir(),"app_webview/Default");for(String path:new String[]{"Cache","HTTP Cache","Code Cache","GPUCache"})erase(new File(web,path));}
}
