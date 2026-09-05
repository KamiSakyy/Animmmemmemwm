package app.yoru.mobile;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.net.VpnService;
import android.os.*;
import org.json.*;
import java.io.*;
import java.util.*;

public final class YoruVpnService extends VpnService {
    static final String ACTION_CONNECT="app.yoru.mobile.vpn.CONNECT";
    static final String ACTION_DISCONNECT="app.yoru.mobile.vpn.DISCONNECT";
    static final String EXTRA_PROFILE="profile";
    static final String EXTRA_MODE="mode";
    static final String EXTRA_APPS="apps";
    private static volatile boolean active;
    private static volatile String text="Отключен";
    private ParcelFileDescriptor tun;private Object core;private Thread worker;
    public static boolean active(){return active;}
    public static String text(){return text+(YoruVpnCore.available()?" · Xray "+YoruVpnCore.version():" · Xray core нет в локальной сборке");}
    static void status(String s){if(s!=null&&!s.trim().isEmpty())text=s.trim();}
    @Override public int onStartCommand(Intent intent,int flags,int startId){if(intent!=null&&ACTION_DISCONNECT.equals(intent.getAction())){stopAll();stopSelf();return START_NOT_STICKY;}if(intent!=null&&ACTION_CONNECT.equals(intent.getAction()))start(intent);return START_STICKY;}
    private void start(Intent intent){stopAll();active=true;text="Подключаемся";startNotice("YORU VPN подключается",profileName(intent));String profile=intent.getStringExtra(EXTRA_PROFILE);String mode=intent.getStringExtra(EXTRA_MODE);ArrayList<String> apps=intent.getStringArrayListExtra(EXTRA_APPS);worker=new Thread(()->runVpn(profile,mode,apps),"YORU VPN");worker.start();}
    private void runVpn(String profile,String mode,ArrayList<String> apps){try{JSONObject p=new JSONObject(profile);String config=YoruVpnConfig.build(p);tun=builder(mode,apps).establish();if(tun==null)throw new IOException("tun");core=YoruVpnCore.start(this,config,tun.getFd());active=true;text="Подключено: "+p.optString("name",p.optString("host"));startNotice("YORU VPN подключен",p.optString("name",p.optString("host")));}catch(Throwable e){text="VPN не подключился: "+message(e);stopAll();stopSelf();}}
    private VpnService.Builder builder(String mode,ArrayList<String> apps)throws Exception{VpnService.Builder b=new VpnService.Builder();b.setSession("YORU VPN");b.setMtu(1400);b.addAddress("172.23.42.2",30);b.addRoute("0.0.0.0",0);b.addDnsServer("1.1.1.1");b.addDnsServer("8.8.8.8");if(Build.VERSION.SDK_INT>=21){if("selected".equals(mode)){if(apps==null||apps.isEmpty())b.addAllowedApplication(getPackageName());else for(String p:apps)try{b.addAllowedApplication(p);}catch(Exception ignored){}}else if(!"all".equals(mode))b.addAllowedApplication(getPackageName());}return b;}
    private void stopAll(){active=false;text="Отключен";try{YoruVpnCore.stop(core);}catch(Exception ignored){}core=null;try{if(tun!=null)tun.close();}catch(Exception ignored){}tun=null;if(worker!=null&&worker!=Thread.currentThread())try{worker.interrupt();}catch(Exception ignored){}worker=null;stopForeground(true);}
    private void startNotice(String title,String body){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel("yoru-vpn","YORU VPN",NotificationManager.IMPORTANCE_LOW);NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(nm!=null)nm.createNotificationChannel(c);}Intent open=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,4400,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"yoru-vpn"):new Notification.Builder(this);b.setSmallIcon(R.drawable.ic_yoru).setContentTitle(title).setContentText(body==null||body.isEmpty()?"Встроенный VPN":body).setContentIntent(pi).setOngoing(true);if(Build.VERSION.SDK_INT>=34)startForeground(4401,b.build(),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(4401,b.build());}
    private String profileName(Intent intent){try{return new JSONObject(intent.getStringExtra(EXTRA_PROFILE)).optString("name","Встроенный VPN");}catch(Exception e){return "Встроенный VPN";}}
    private String message(Throwable e){Throwable t=e;while(t.getCause()!=null)t=t.getCause();String m=t.getMessage();return m==null||m.isEmpty()?t.getClass().getSimpleName():m;}
    @Override public void onDestroy(){stopAll();super.onDestroy();}
}
