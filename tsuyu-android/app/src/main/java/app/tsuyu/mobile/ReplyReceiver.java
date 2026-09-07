package app.tsuyu.mobile;

import android.content.*;
import android.os.*;
import androidx.core.app.*;

public class ReplyReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){CharSequence cs=RemoteInput.getResultsFromIntent(i)==null?null:RemoteInput.getResultsFromIntent(i).getCharSequence(Notify.KEY_REPLY);String text=cs==null?"":cs.toString().trim();String peer=i.getStringExtra("peerUid");if(text.isEmpty()||peer==null)return;PendingResult pr=goAsync();TsuyuApp app=(TsuyuApp)c.getApplicationContext();Rt.sendText(peer,text,null,new Rt.Done(){@Override public void ok(){done(c,peer,"Отправлено");pr.finish();}@Override public void fail(String e){done(c,peer,"Не отправлено: "+e);pr.finish();}});} 
    private void done(Context c,String peer,String t){NotificationCompat.Builder b=new NotificationCompat.Builder(c,Notify.CHANNEL_MSG).setSmallIcon(R.drawable.ic_tsuyu).setContentTitle("Tsuyu").setContentText(t).setPriority(NotificationCompat.PRIORITY_DEFAULT).setAutoCancel(true);if(Notify.allowed(c))NotificationManagerCompat.from(c).notify((peer+"reply").hashCode(),b.build());}
}
