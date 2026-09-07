package app.tsuyu.mobile;

import android.app.*;
import android.content.*;
import android.os.*;
import com.google.firebase.*;
import com.google.firebase.auth.*;
import com.google.firebase.database.*;
import java.util.concurrent.*;

public final class TsuyuApp extends Application {
    private static TsuyuApp self;
    public FirebaseAuth auth;
    public DatabaseReference db;
    public FirebaseDatabase database;
    public Handler main;
    public ExecutorService io;
    public TsuyuCrypto crypto;
    public TsuyuIndexDb index;
    public static TsuyuApp app(){return self;}

    @Override public void onCreate(){super.onCreate();self=this;main=new Handler(Looper.getMainLooper());io=Executors.newFixedThreadPool(Math.max(4,Runtime.getRuntime().availableProcessors()));initFirebase();crypto=new TsuyuCrypto(this);index=new TsuyuIndexDb(this);Notify.init(this);}

    private void initFirebase(){
        FirebaseOptions options=new FirebaseOptions.Builder()
                .setApiKey("AIzaSyBm0mIvHVznIeF2PoFk6dtdaiT5r877wyA")
                .setApplicationId("1:471541334599:web:567af3e7dbe70a37572762")
                .setProjectId("meow-874ce")
                .setDatabaseUrl("https://meow-874ce-default-rtdb.europe-west1.firebasedatabase.app")
                .setStorageBucket("meow-874ce.firebasestorage.app")
                .build();
        FirebaseApp firebase;
        if(FirebaseApp.getApps(this).isEmpty())firebase=FirebaseApp.initializeApp(this,options,"tsuyu");else firebase=FirebaseApp.getApps(this).get(0);
        auth=FirebaseAuth.getInstance(firebase);
        database=FirebaseDatabase.getInstance(firebase);
        try{database.setPersistenceEnabled(true);}catch(Throwable ignored){}
        db=database.getReference();
        try{db.keepSynced(true);}catch(Throwable ignored){}
    }

    public String uid(){FirebaseUser u=auth==null?null:auth.getCurrentUser();return u==null?"":u.getUid();}
    public boolean signed(){return !uid().isEmpty();}
    public static void bg(Runnable r){TsuyuApp a=app();if(a!=null&&a.io!=null)a.io.execute(r);else new Thread(r).start();}
    public static void ui(Runnable r){TsuyuApp a=app();if(a!=null&&a.main!=null)a.main.post(r);}
    public void startRealtime(){try{Intent i=new Intent(this,TsuyuRealtimeService.class);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}catch(Throwable ignored){}}
}
