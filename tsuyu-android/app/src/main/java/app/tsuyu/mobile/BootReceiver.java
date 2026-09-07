package app.tsuyu.mobile;

import android.content.*;
import android.os.*;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){String a=i==null?"":i.getAction();if(Intent.ACTION_BOOT_COMPLETED.equals(a)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)){Intent s=new Intent(c,TsuyuRealtimeService.class);try{if(Build.VERSION.SDK_INT>=26)c.startForegroundService(s);else c.startService(s);}catch(Exception ignored){}}}
}
