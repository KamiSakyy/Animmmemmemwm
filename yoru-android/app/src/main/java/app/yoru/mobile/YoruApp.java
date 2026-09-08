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
    private static ExecutorService pool(String name,int core,int max,int priority){ThreadPoolExecutor e=new ThreadPoolExecutor(core,max,20L,TimeUnit.SECONDS,new LinkedBlockingQueue<>(256),r->{Thread t=new Thread(r,name);t.setPriority(priority);return t;},new TaskQueue.Policy());e.allowCoreThreadTimeOut(true);return e;}
    private static ExecutorService ioPool(){int cores=Math.max(2,Runtime.getRuntime().availableProcessors());return pool("yoru-io",Math.max(3,Math.min(6,cores)),Math.max(6,Math.min(10,cores*2)),Thread.NORM_PRIORITY);}
    private static ExecutorService uiPool(){int cores=Math.max(4,Runtime.getRuntime().availableProcessors());return pool("yoru-ui",4,Math.max(6,Math.min(10,cores*2)),Thread.NORM_PRIORITY+1);}
    private static ExecutorService discoveryPool(){int cores=Math.max(2,Runtime.getRuntime().availableProcessors());return pool("yoru-bg",2,Math.max(3,Math.min(6,cores)),Thread.NORM_PRIORITY-1);}
    public synchronized DownloadHub downloads(){if(downloads==null)downloads=new DownloadHub(this,mediaCache);return downloads;}
    public boolean savingMobile(){return store!=null&&traffic!=null&&store.dataSaver()&&traffic.mobile();}
    public boolean autoNextAllowed(){return store.autoNext();}
    @Override public void onCreate(){super.onCreate();instance=this;original=true;store=new SecureStore(this);cache=new YoruCache(this);traffic=new TrafficMeter(this,store);mediaCache=new MediaCache(this);api=new ApiRepository(this);images=new ImageLoader(this);channels();io.execute(()->{try{store.preload();}catch(Exception ignored){}});main.post(this::afterFirstFrame);}
    private void afterFirstFrame(){ui.execute(()->{boolean ok=AppSecurity.original(this);main.post(()->original=ok);});try{WebView.setWebContentsDebuggingEnabled(false);}catch(Exception ignored){}discovery.execute(()->{try{EpisodeUpdateReceiver.schedule(this);ScheduledDownloads.schedule(this);if(System.currentTimeMillis()-store.lastEpisodeCheckAt()>35L*60L*1000L)EpisodeUpdateReceiver.checkSoon(this);}catch(Exception ignored){}});io.execute(()->{try{api.refreshProtection(false);}catch(Exception ignored){}});warmStartup();}
    public void warmStartup(){if(warmed)return;warmed=true;discovery.execute(()->{try{calendarTodayCount=cache==null?0:cache.todayScheduleCount();}catch(Exception ignored){calendarTodayCount=0;}try{api.seed();}catch(Exception ignored){}try{long at=store.calendarCacheAt();if(System.currentTimeMillis()-at<25*60*1000L&&calendarTodayCount>0)return;ArrayList<ApiRepository.AiringItem> rows=api.airingSchedule(28,store.favorites());JSONArray json=ApiRepository.airingJson(rows);if(cache!=null)cache.schedule(json);store.calendarCache(json);calendarTodayCount=ApiRepository.todayCount(rows);}catch(Exception ignored){}});io.execute(()->{try{if(cache!=null)cache.trimNow();}catch(Exception ignored){}});}
    private void channels(){if(Build.VERSION.SDK_INT>=26){NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(nm!=null)nm.createNotificationChannel(new NotificationChannel("yoru-updates",getString(R.string.updates_channel_name),NotificationManager.IMPORTANCE_HIGH));}}
    public static YoruApp app(){return instance;}
}
