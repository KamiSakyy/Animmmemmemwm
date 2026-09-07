package app.yoru.mobile;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

public final class YoruCache extends SQLiteOpenHelper {
    private static final String DB="yoru-fast-cache.db";private static final int VERSION=1;
    private static final long DAY=24L*60*60*1000,TRIM_INTERVAL=60L*60L*1000L;
    private final ConcurrentHashMap<String,Long> trimAt=new ConcurrentHashMap<>();
    private volatile int cachedTodayCount=-1;private volatile long cachedTodayAt,cachedTodayDay;
    public YoruCache(Context c){super(c.getApplicationContext(),DB,null,VERSION);try{setWriteAheadLoggingEnabled(true);}catch(Exception ignored){}}
    @Override public void onConfigure(SQLiteDatabase db){super.onConfigure(db);try{db.enableWriteAheadLogging();}catch(Exception ignored){}}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS details(k TEXT PRIMARY KEY, mal INTEGER, json TEXT NOT NULL, updated INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS details_mal ON details(mal)");
        db.execSQL("CREATE TABLE IF NOT EXISTS schedule(k TEXT PRIMARY KEY, json TEXT NOT NULL, updated INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS franchise(mal INTEGER PRIMARY KEY, json TEXT NOT NULL, updated INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS offline(k TEXT PRIMARY KEY, json TEXT NOT NULL, updated INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS progress(k TEXT PRIMARY KEY, json TEXT NOT NULL, updated INTEGER NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){onCreate(db);}
    public static String key(Anime a){if(a==null)return "";if(a.malId>0)return "mal:"+a.malId;if(a.anilistId>0)return "ani:"+a.anilistId;if(a.kpId>0)return "kp:"+a.kpId;String k=a.key();return k==null?"":k;}
    public Anime detail(Anime base,long ttl){String k=key(base);if(k.isEmpty())return null;long min=System.currentTimeMillis()-Math.max(60_000,ttl);Cursor c=null;try{SQLiteDatabase db=getReadableDatabase();c=db.rawQuery("SELECT json FROM details WHERE k=? AND updated>=?",new String[]{k,String.valueOf(min)});if(!c.moveToFirst()&&base!=null&&base.malId>0){close(c);c=db.rawQuery("SELECT json FROM details WHERE mal=? AND updated>=? ORDER BY updated DESC LIMIT 1",new String[]{String.valueOf(base.malId),String.valueOf(min)});if(!c.moveToFirst())return null;}Anime a=unpack(new JSONObject(c.getString(0)));return Anime.valid(a)?a:null;}catch(Exception e){return null;}finally{close(c);}}
    public void detail(Anime a){if(!Anime.valid(a))return;try{SQLiteDatabase db=getWritableDatabase();ContentValues v=new ContentValues();v.put("k",key(a));v.put("mal",a.malId);v.put("json",pack(a).toString());v.put("updated",System.currentTimeMillis());db.insertWithOnConflict("details",null,v,SQLiteDatabase.CONFLICT_REPLACE);trimMaybe(db,"details",520,14*DAY);}catch(Exception ignored){}}
    public JSONArray schedule(long ttl){return jsonArray("schedule","main",ttl);}
    public void schedule(JSONArray rows){cachedTodayCount=-1;putJson("schedule","main",rows==null?new JSONArray():rows,4*DAY,6);}
    public JSONArray franchise(int mal,long ttl){if(mal<=0)return new JSONArray();return jsonArray("franchise",String.valueOf(mal),ttl);}
    public void franchise(int mal,JSONArray rows){if(mal<=0)return;putJson("franchise",String.valueOf(mal),rows==null?new JSONArray():rows,21*DAY,240);}
    public String offline(long ttl){JSONArray arr=jsonArray("offline","index",ttl);return arr.toString();}
    public void offline(String json){try{putJson("offline","index",new JSONArray(json==null||json.isEmpty()?"[]":json),7*DAY,4);}catch(Exception ignored){}}
    public void progressMirror(String k,JSONObject row){if(k==null||k.isEmpty()||row==null)return;try{SQLiteDatabase db=getWritableDatabase();ContentValues v=new ContentValues();v.put("k",k);v.put("json",row.toString());v.put("updated",System.currentTimeMillis());db.insertWithOnConflict("progress",null,v,SQLiteDatabase.CONFLICT_REPLACE);trimMaybe(db,"progress",1200,60*DAY);}catch(Exception ignored){}}
    public int todayScheduleCount(){long now=System.currentTimeMillis();Calendar c=Calendar.getInstance();c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);long start=c.getTimeInMillis();if(cachedTodayCount>=0&&cachedTodayDay==start&&now-cachedTodayAt<60_000L)return cachedTodayCount;JSONArray arr=schedule(3*60*60*1000L);long end=start+DAY;int n=0;for(int i=0;i<arr.length();i++){JSONObject j=arr.optJSONObject(i);long t=j==null?0:j.optLong("time");if(t>=start&&t<end)n++;}cachedTodayCount=n;cachedTodayDay=start;cachedTodayAt=now;return n;}
    public void trimNow(){try{SQLiteDatabase db=getWritableDatabase();trim(db,"details",520,14*DAY);trim(db,"franchise",240,21*DAY);trim(db,"schedule",6,4*DAY);trim(db,"offline",4,7*DAY);trim(db,"progress",1200,60*DAY);long now=System.currentTimeMillis();trimAt.put("details",now);trimAt.put("franchise",now);trimAt.put("schedule",now);trimAt.put("offline",now);trimAt.put("progress",now);}catch(Exception ignored){}}
    private JSONArray jsonArray(String table,String k,long ttl){Cursor c=null;try{SQLiteDatabase db=getReadableDatabase();long min=System.currentTimeMillis()-Math.max(60_000,ttl);String keyColumn=table.equals("franchise")?"mal":"k";c=db.rawQuery("SELECT json FROM "+table+" WHERE "+keyColumn+"=? AND updated>=?",new String[]{k,String.valueOf(min)});if(!c.moveToFirst())return new JSONArray();return new JSONArray(c.getString(0));}catch(Exception e){return new JSONArray();}finally{close(c);}}
    private void putJson(String table,String k,JSONArray rows,long age,int keep){try{SQLiteDatabase db=getWritableDatabase();ContentValues v=new ContentValues();if(table.equals("franchise"))v.put("mal",Integer.parseInt(k));else v.put("k",k);v.put("json",rows.toString());v.put("updated",System.currentTimeMillis());db.insertWithOnConflict(table,null,v,SQLiteDatabase.CONFLICT_REPLACE);trimMaybe(db,table,keep,age);}catch(Exception ignored){}}
    private void trimMaybe(SQLiteDatabase db,String table,int keep,long maxAge){long now=System.currentTimeMillis();Long last=trimAt.get(table);if(last!=null&&now-last<TRIM_INTERVAL)return;trimAt.put(table,now);YoruApp app=YoruApp.app();if(app!=null&&app.discovery!=null)app.discovery.execute(()->{try{SQLiteDatabase writable=getWritableDatabase();trim(writable,table,keep,maxAge);}catch(Exception ignored){}});}
    private static JSONObject pack(Anime a)throws JSONException{JSONObject j=a.json();JSONArray rel=new JSONArray();for(Anime r:a.related)if(Anime.valid(r)&&rel.length()<120)rel.put(r.json());j.put("related",rel);return j;}
    private static Anime unpack(JSONObject j){Anime a=Anime.from(j);JSONArray rel=j.optJSONArray("related");for(int i=0;rel!=null&&i<rel.length()&&a.related.size()<120;i++){Anime r=Anime.from(rel.optJSONObject(i));if(Anime.valid(r)&&!r.key().equals(a.key()))a.related.add(r);}return a;}
    private static void trim(SQLiteDatabase db,String table,int keep,long maxAge){long min=System.currentTimeMillis()-maxAge;try{db.delete(table,"updated<?",new String[]{String.valueOf(min)});}catch(Exception ignored){}Cursor c=null;try{String keyColumn=table.equals("franchise")?"mal":"k";c=db.rawQuery("SELECT "+keyColumn+" FROM "+table+" ORDER BY updated DESC LIMIT -1 OFFSET "+Math.max(1,keep),null);ArrayList<String> old=new ArrayList<>();while(c.moveToNext())old.add(c.getString(0));close(c);for(String k:old)db.delete(table,keyColumn+"=?",new String[]{k});}catch(Exception ignored){close(c);}}
    private static void close(Cursor c){try{if(c!=null)c.close();}catch(Exception ignored){}}
}
