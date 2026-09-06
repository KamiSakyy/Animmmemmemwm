package app.yoru.mobile;

import android.app.*;
import android.os.*;
import android.webkit.WebView;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

public final class YoruApp extends Application {
    public static YoruApp instance;
    public final ExecutorService io=ioPool(),ui=uiPool(),discovery=discoveryPool();
    public final Handler main=new Handler(Looper.getMainLooper());
    public SecureStore store;
    public YoruCache cache;
    public ApiRepository api;
    public ImageLoader images;
    public boolean original;
    public TrafficMeter traffic;
    public MediaCache mediaCache;
    private DownloadHub downloads;
    public volatile int activePlayers,calendarTodayCount;private volatile boolean warmed;
    private static ExecutorService ioPool(){int cores=Math.max(4,Runtime.getRuntime().availableProcessors());int n=Math.max(10,Math.min(16,cores*2));return Executors.newFixedThreadPool(n,r->{Thread t=new Thread(r,"yoru-io-turbo");t.setPriority(Thread.NORM_PRIORITY);return t;});}
    private static ExecutorService uiPool(){return Executors.newFixedThreadPool(4,r->{Thread t=new Thread(r,"yoru-ui-fast");t.setPriority(Thread.NORM_PRIORITY+1);return t;});}
    private static ExecutorService discoveryPool(){int cores=Math.max(4,Runtime.getRuntime().availableProcessors());int n=Math.max(8,Math.min(14,cores*2));return Executors.newFixedThreadPool(n,r->{Thread t=new Thread(r,"yoru-discovery");t.setPriority(Thread.NORM_PRIORITY-1);return t;});}
    public synchronized DownloadHub downloads(){if(downloads==null)downloads=new DownloadHub(this,mediaCache);return downloads;}
    public boolean savingMobile(){return store!=null&&traffic!=null&&store.dataSaver()&&traffic.mobile();}
    public boolean autoNextAllowed(){return store.autoNext();}
    @Override public void onCreate(){super.onCreate();instance=this;original=true;store=new SecureStore(this);cache=new YoruCache(this);traffic=new TrafficMeter(this,store);mediaCache=new MediaCache(this);api=new ApiRepository(this);images=new ImageLoader(this);channels();main.post(this::afterFirstFrame);}
    private void afterFirstFrame(){ui.execute(()->{boolean ok=AppSecurity.original(this);main.post(()->original=ok);});try{WebView.setWebContentsDebuggingEnabled(false);}catch(Exception ignored){}try{EpisodeUpdateReceiver.schedule(this);}catch(Exception ignored){}discovery.execute(()->{try{EpisodeUpdateReceiver.checkNow(this);}catch(Exception ignored){}});io.execute(()->{try{api.refreshProtection(false);}catch(Exception ignored){}});warmStartup();}
    public void warmStartup(){if(warmed)return;warmed=true;calendarTodayCount=cache==null?0:cache.todayScheduleCount();discovery.execute(()->{try{api.seed();}catch(Exception ignored){}});ui.execute(()->{try{if(cache!=null)cache.trimNow();}catch(Exception ignored){}});discovery.execute(()->{try{long at=store.calendarCacheAt();if(System.currentTimeMillis()-at<25*60*1000L&&calendarTodayCount>0)return;ArrayList<ApiRepository.AiringItem> rows=api.airingSchedule(28,store.favorites());JSONArray json=ApiRepository.airingJson(rows);if(cache!=null)cache.schedule(json);store.calendarCache(json);calendarTodayCount=ApiRepository.todayCount(rows);}catch(Exception ignored){}});}
    private void channels(){if(Build.VERSION.SDK_INT>=26){NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(nm!=null)nm.createNotificationChannel(new NotificationChannel("yoru-updates",getString(R.string.updates_channel_name),NotificationManager.IMPORTANCE_HIGH));}}
    public static YoruApp app(){return instance;}
}
