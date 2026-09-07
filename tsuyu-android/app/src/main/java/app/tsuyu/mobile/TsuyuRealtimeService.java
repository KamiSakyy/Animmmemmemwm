package app.tsuyu.mobile;

import android.app.*;
import android.content.*;
import android.os.*;
import com.google.firebase.database.*;
import org.json.*;
import java.util.*;

public class TsuyuRealtimeService extends Service {
    private ValueEventListener chatsListener;private final Map<String,ValueEventListener> msgListeners=new HashMap<>();
    @Override public void onCreate(){super.onCreate();Notify.init(this);try{startForeground(7,Notify.service(this));}catch(Exception ignored){}listen();}
    @Override public int onStartCommand(Intent i,int f,int id){listen();return START_STICKY;}
    @Override public IBinder onBind(Intent i){return null;}
    private void listen(){String id=Rt.uid();if(id.isEmpty())return;Rt.online();if(chatsListener!=null)return;DatabaseReference ref=Rt.db().child("userChats").child(id);chatsListener=new ValueEventListener(){@Override public void onDataChange(DataSnapshot root){for(DataSnapshot s:root.getChildren()){String peer=s.getKey();String chat=s.child("chatId").getValue(String.class);String last=s.child("lastId").getValue(String.class);if(peer!=null&&chat!=null&&last!=null)watch(peer,chat,last);}}@Override public void onCancelled(DatabaseError e){}};ref.addValueEventListener(chatsListener);} 
    private void watch(String peer,String chat,String msg){String key=chat+"/"+msg;if(msgListeners.containsKey(key))return;DatabaseReference r=Rt.db().child("chats").child(chat).child("messages").child(msg);ValueEventListener l=new ValueEventListener(){@Override public void onDataChange(DataSnapshot s){if(!s.exists())return;handle(peer,chat,msg,s);}@Override public void onCancelled(DatabaseError e){}};msgListeners.put(key,l);r.addValueEventListener(l);} 
    private void handle(String peer,String chat,String msg,DataSnapshot s){try{String me=Rt.uid();String sender=s.child("sender").getValue(String.class);String receiver=s.child("receiver").getValue(String.class);JSONObject payload=Rt.decrypt(chat,msg,s);String prev=Rt.preview(payload);long ts=valLong(s.child("ts"),valLong(s.child("clientAt"),System.currentTimeMillis()));JSONObject cached=new JSONObject().put("id",msg).put("chatId",chat).put("sender",sender).put("receiver",receiver).put("text",payload.optString("text","")).put("type",payload.optString("type","text")).put("decryptedPreview",prev).put("ts",ts).put("edited",s.child("edited").getValue(Boolean.class)!=null&&Boolean.TRUE.equals(s.child("edited").getValue(Boolean.class))).put("deletedForAll",false).put("payload",payload.toString());TsuyuApp.app().index.message(cached);if(sender!=null&&!sender.equals(me)&&!ChatActivity.isActive(peer)&&!seen(msg)){mark(msg);notifyPeer(peer,chat,msg,prev,ts);}}catch(Exception ignored){}}
    private void notifyPeer(String peer,String chat,String msg,String prev,long ts){Rt.getProfile(peer,new Rt.Got(){@Override public void ok(JSONObject p){String name=p.optString("name",p.optString("username","Tsuyu"));String av=p.optString("avatar","");TsuyuApp.app().index.profile(p);Notify.message(TsuyuRealtimeService.this,peer,chat,msg,name,av,prev,ts);}@Override public void fail(String e){Notify.message(TsuyuRealtimeService.this,peer,chat,msg,"Tsuyu","",prev,ts);}});} 
    private boolean seen(String id){return getSharedPreferences("notify",0).getBoolean(id,false);}private void mark(String id){getSharedPreferences("notify",0).edit().putBoolean(id,true).apply();}
    private long valLong(DataSnapshot s,long def){Object v=s.getValue();if(v instanceof Number)return ((Number)v).longValue();try{return Long.parseLong(String.valueOf(v));}catch(Exception e){return def;}}
    @Override public void onDestroy(){super.onDestroy();String id=Rt.uid();if(!id.isEmpty()&&chatsListener!=null)Rt.db().child("userChats").child(id).removeEventListener(chatsListener);for(Map.Entry<String,ValueEventListener> e:msgListeners.entrySet()){String[] p=e.getKey().split("/");if(p.length==2)Rt.db().child("chats").child(p[0]).child("messages").child(p[1]).removeEventListener(e.getValue());}msgListeners.clear();Rt.offline();}
}
