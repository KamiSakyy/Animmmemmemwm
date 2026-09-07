package app.tsuyu.mobile;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.os.*;
import androidx.core.app.*;
import androidx.core.graphics.drawable.IconCompat;

public final class Notify {
    public static final String CHANNEL_MSG="tsuyu.messages", CHANNEL_SERVICE="tsuyu.realtime", KEY_REPLY="tsuyu.reply";
    private Notify(){}
    public static void init(Context c){if(Build.VERSION.SDK_INT>=26){NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);NotificationChannel msg=new NotificationChannel(CHANNEL_MSG,"Tsuyu сообщения",NotificationManager.IMPORTANCE_HIGH);msg.setDescription("Зашифрованные сообщения Tsuyu");msg.enableVibration(true);NotificationChannel svc=new NotificationChannel(CHANNEL_SERVICE,"Tsuyu realtime",NotificationManager.IMPORTANCE_LOW);svc.setDescription("Мгновенные статусы и уведомления");nm.createNotificationChannel(msg);nm.createNotificationChannel(svc);}}
    public static boolean allowed(Context c){return Build.VERSION.SDK_INT<33||c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;}
    public static Notification service(Context c){Intent i=new Intent(c,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(c,1,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);return new NotificationCompat.Builder(c,CHANNEL_SERVICE).setSmallIcon(R.drawable.ic_tsuyu).setContentTitle("Tsuyu realtime").setContentText("Сообщения и статусы обновляются мгновенно").setContentIntent(pi).setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW).setColor(Tui.ACCENT).build();}
    public static void message(Context c,String peerUid,String chatId,String msgId,String name,String avatar,String text,long ts){if(!allowed(c))return;Intent open=new Intent(c,ChatActivity.class).putExtra("peerUid",peerUid);PendingIntent openPi=PendingIntent.getActivity(c,peerUid.hashCode(),open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Intent reply=new Intent(c,ReplyReceiver.class).putExtra("peerUid",peerUid).putExtra("chatId",chatId);PendingIntent replyPi=PendingIntent.getBroadcast(c,(peerUid+msgId).hashCode(),reply,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE);androidx.core.app.RemoteInput input=new androidx.core.app.RemoteInput.Builder(KEY_REPLY).setLabel("Ответить").build();NotificationCompat.Action action=new NotificationCompat.Action.Builder(R.drawable.ic_reply,"Ответить",replyPi).addRemoteInput(input).setAllowGeneratedReplies(false).build();Bitmap large=Tui.bitmap(avatar);NotificationCompat.MessagingStyle style=new NotificationCompat.MessagingStyle(new androidx.core.app.Person.Builder().setName("Вы").build()).addMessage(text,ts,new androidx.core.app.Person.Builder().setName(name).setIcon(large==null?null:IconCompat.createWithBitmap(large)).build());Notification n=new NotificationCompat.Builder(c,CHANNEL_MSG).setSmallIcon(R.drawable.ic_tsuyu).setColor(Tui.ACCENT).setContentTitle(name).setContentText(text).setWhen(ts>0?ts:System.currentTimeMillis()).setShowWhen(true).setCategory(NotificationCompat.CATEGORY_MESSAGE).setPriority(NotificationCompat.PRIORITY_HIGH).setLargeIcon(large).setContentIntent(openPi).setAutoCancel(true).setStyle(style).addAction(action).build();NotificationManagerCompat.from(c).notify(peerUid.hashCode(),n);} 
}
