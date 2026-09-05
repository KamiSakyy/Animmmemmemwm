package app.yoru.mobile;

import android.app.*;
import android.os.*;
import android.webkit.WebView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class YoruApp extends Application {
    public static YoruApp instance;
    public final ExecutorService io=Executors.newFixedThreadPool(4);
    public final Handler main=new Handler(Looper.getMainLooper());
    public SecureStore store;
    public ApiRepository api;
    public ImageLoader images;
    public boolean original;
    public TrafficMeter traffic;
    public MediaCache mediaCache;
    private DownloadHub downloads;
    public volatile int activePlayers;
    public synchronized DownloadHub downloads(){if(downloads==null)downloads=new DownloadHub(this,mediaCache);return downloads;}
    public boolean savingMobile(){return store.dataSaver()&&traffic.mobile();}
    public boolean autoNextAllowed(){return store.autoNext()&&!savingMobile();}
    @Override public void onCreate(){super.onCreate();instance=this;original=AppSecurity.original(this);WebView.setWebContentsDebuggingEnabled(false);store=new SecureStore(this);traffic=new TrafficMeter(this,store);mediaCache=new MediaCache(this);api=new ApiRepository(this);images=new ImageLoader(this);channels();EpisodeUpdateReceiver.schedule(this);main.postDelayed(()->io.execute(()->{try{api.refreshProtection(false);}catch(Exception ignored){}}),12000);}
    private void channels(){if(Build.VERSION.SDK_INT>=26){NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(nm!=null)nm.createNotificationChannel(new NotificationChannel("yoru-updates",getString(R.string.updates_channel_name),NotificationManager.IMPORTANCE_DEFAULT));}}
    public static YoruApp app(){return instance;}
}
