package app.tsuyu.mobile;

import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;

public final class TsuyuIndexDb extends SQLiteOpenHelper {
    public TsuyuIndexDb(Context c){super(c,"tsuyu_index.db",null,1);} 
    @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE IF NOT EXISTS profiles(uid TEXT PRIMARY KEY, username TEXT, name TEXT, avatar TEXT, bio TEXT, online INTEGER, lastSeen INTEGER, updated INTEGER)");db.execSQL("CREATE TABLE IF NOT EXISTS messages(chatId TEXT, msgId TEXT, peerUid TEXT, sender TEXT, ts INTEGER, type TEXT, preview TEXT, json TEXT, PRIMARY KEY(chatId,msgId))");db.execSQL("CREATE INDEX IF NOT EXISTS msg_peer ON messages(peerUid,ts DESC)");db.execSQL("CREATE INDEX IF NOT EXISTS msg_chat ON messages(chatId,ts DESC)");}
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){onCreate(db);} 
    public synchronized void profile(String uid,JSONObject p){if(uid==null||uid.isEmpty())return;ContentValues v=new ContentValues();v.put("uid",uid);v.put("username",p.optString("username"));v.put("name",p.optString("name"));v.put("avatar",p.optString("avatar"));v.put("bio",p.optString("bio"));v.put("online",p.optBoolean("online")?1:0);v.put("lastSeen",p.optLong("lastSeen"));v.put("updated",System.currentTimeMillis());getWritableDatabase().insertWithOnConflict("profiles",null,v,SQLiteDatabase.CONFLICT_REPLACE);} 
    public synchronized void profile(JSONObject p){profile(p.optString("uid"),p);} 
    public synchronized JSONObject profile(String uid){JSONObject o=new JSONObject();try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM profiles WHERE uid=?",new String[]{uid})){if(c.moveToFirst())fill(o,c);}catch(Exception ignored){}return o;}
    public synchronized void message(String chatId,String msgId,String peerUid,String sender,long ts,String type,String preview,JSONObject json){ContentValues v=new ContentValues();v.put("chatId",chatId);v.put("msgId",msgId);v.put("peerUid",peerUid);v.put("sender",sender);v.put("ts",ts);v.put("type",type);v.put("preview",preview);v.put("json",json==null?"{}":json.toString());getWritableDatabase().insertWithOnConflict("messages",null,v,SQLiteDatabase.CONFLICT_REPLACE);} 
    public synchronized void message(JSONObject o){String sender=o.optString("sender"),receiver=o.optString("receiver"),me=TsuyuApp.app()==null?"":TsuyuApp.app().uid();String peer=sender.equals(me)?receiver:sender;message(o.optString("chatId"),o.optString("id"),peer,sender,o.optLong("ts"),o.optString("type"),o.optString("decryptedPreview",o.optString("text")),o);} 
    public synchronized void deleteMessage(String chatId,String msgId){getWritableDatabase().delete("messages","chatId=? AND msgId=?",new String[]{chatId,msgId});}
    public synchronized JSONObject last(String peerUid){JSONObject o=new JSONObject();try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM messages WHERE peerUid=? ORDER BY ts DESC LIMIT 1",new String[]{peerUid})){if(c.moveToFirst())fill(o,c);}catch(Exception ignored){}return o;}
    public synchronized JSONArray lastDialogs(){JSONArray arr=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT m.* FROM messages m INNER JOIN (SELECT peerUid,MAX(ts) mts FROM messages GROUP BY peerUid) x ON x.peerUid=m.peerUid AND x.mts=m.ts ORDER BY m.ts DESC LIMIT 80",null)){while(c.moveToNext()){JSONObject o=new JSONObject();fill(o,c);arr.put(o);}}catch(Exception ignored){}return arr;}
    private static void fill(JSONObject o,Cursor c)throws JSONException{for(int i=0;i<c.getColumnCount();i++){String k=c.getColumnName(i);int t=c.getType(i);if(t==Cursor.FIELD_TYPE_INTEGER)o.put(k,c.getLong(i));else o.put(k,c.getString(i));}}
}
