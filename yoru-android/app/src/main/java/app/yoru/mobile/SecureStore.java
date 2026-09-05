package app.yoru.mobile;

import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.util.*;

public final class SecureStore {
    private final SharedPreferences prefs;private final javax.crypto.SecretKey key;
    private JSONObject favorites,history,settings;
    public static final String[] BUCKETS={"planned","watching","completed","onhold","dropped"};
    public static final String[] BUCKET_LABELS={"В планах","Смотрю","Просмотрено","Отложено","Брошено"};
    public SecureStore(Context c){prefs=c.getSharedPreferences("private-library",Context.MODE_PRIVATE);key=getKey();favorites=read("favorites");history=read("history");settings=read("settings");}
    private javax.crypto.SecretKey getKey(){try{KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);String alias="yoru-local-v1";if(!ks.containsAlias(alias)){KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");gen.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build());gen.generateKey();}return (javax.crypto.SecretKey)ks.getKey(alias,null);}catch(Exception e){return null;}}
    private JSONObject read(String name){String value=prefs.getString(name,"");if(value.isEmpty()||key==null)return new JSONObject();try{byte[] all=Base64.decode(value,Base64.NO_WRAP);int len=all[0]&255;if(len<12||len>16||all.length<=len+1)return new JSONObject();byte[] iv=Arrays.copyOfRange(all,1,1+len);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,iv));return new JSONObject(new String(c.doFinal(all,1+len,all.length-len-1),StandardCharsets.UTF_8));}catch(Exception e){return new JSONObject();}}
    private void write(String name,JSONObject json){if(key==null)return;try{Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key);byte[] iv=c.getIV(),cipher=c.doFinal(json.toString().getBytes(StandardCharsets.UTF_8));byte[] all=new byte[1+iv.length+cipher.length];all[0]=(byte)iv.length;System.arraycopy(iv,0,all,1,iv.length);System.arraycopy(cipher,0,all,1+iv.length,cipher.length);prefs.edit().putString(name,Base64.encodeToString(all,Base64.NO_WRAP)).apply();}catch(Exception ignored){}}
    public boolean persistent(){return key!=null;}
    public synchronized boolean favorite(Anime a){return favorites.has(a.key());}
    public synchronized String bucket(Anime a){JSONObject j=favorites.optJSONObject(a.key());return j==null?"":j.optString("bucket","planned");}
    public synchronized void favorite(Anime a,String bucket){if(!Anime.valid(a))return;try{if(bucket==null||bucket.isEmpty())favorites.remove(a.key());else{JSONObject old=favorites.optJSONObject(a.key());int seen=old==null?Math.max(0,a.episodes):old.optInt("episodesSeen",Math.max(0,a.episodes));favorites.put(a.key(),new JSONObject().put("release",a.json()).put("bucket",bucket).put("added",old==null?System.currentTimeMillis():old.optLong("added",System.currentTimeMillis())).put("episodesSeen",seen));}write("favorites",favorites);}catch(Exception ignored){}}
    public synchronized List<Anime> favorites(){return favorites("","");}
    public synchronized List<Anime> favorites(String bucket,String query){ArrayList<Anime> result=new ArrayList<>();Iterator<String> keys=favorites.keys();String q=query==null?"":query.toLowerCase(Locale.ROOT);while(keys.hasNext()){JSONObject j=favorites.optJSONObject(keys.next());if(j==null)continue;Anime a=Anime.from(j.optJSONObject("release"));if(!Anime.valid(a))continue;if(bucket!=null&&!bucket.isEmpty()&&!bucket.equals(j.optString("bucket","planned")))continue;if(!(a.title+" "+a.original).toLowerCase(Locale.ROOT).contains(q))continue;result.add(a);}result.sort((a,b)->a.title.compareToIgnoreCase(b.title));return result;}
    public synchronized boolean updateFavoriteEpisodes(Anime a,int episodes){if(!Anime.valid(a)||!favorites.has(a.key()))return false;try{JSONObject item=favorites.optJSONObject(a.key());if(item==null)return false;int seen=item.optInt("episodesSeen",0);boolean alert=seen>0&&episodes>seen;if(episodes>seen||seen<=0){Anime copy=Anime.from(a.json());copy.episodes=Math.max(episodes,copy.episodes);item.put("release",copy.json());item.put("episodesSeen",Math.max(episodes,seen));write("favorites",favorites);}return alert;}catch(Exception e){return false;}}
    public synchronized int favoriteCount(){return favorites.length();}
    public synchronized JSONObject progress(Anime a){JSONObject j=history.optJSONObject(a.key());return j==null?new JSONObject():j;}
    public synchronized void progress(Anime a,double episode,int seconds,int duration,String mode,String dubbing,boolean tracked){if(!Anime.valid(a)||episode<0||!Double.isFinite(episode))return;try{JSONObject j=new JSONObject().put("release",a.json()).put("episode",episode).put("time",Math.max(0,seconds)).put("duration",Math.max(0,duration)).put("updated",System.currentTimeMillis()).put("tracked",tracked).put("playerMode",mode==null?"":mode).put("dubbing",dubbing==null?"":dubbing);history.put(a.key(),j);if(history.length()>100){String oldest=null;long at=Long.MAX_VALUE;Iterator<String> keys=history.keys();while(keys.hasNext()){String k=keys.next();long t=history.optJSONObject(k).optLong("updated");if(t<at){oldest=k;at=t;}}if(oldest!=null)history.remove(oldest);}write("history",history);}catch(Exception ignored){}}
    public synchronized List<Anime> recent(){ArrayList<JSONObject> values=new ArrayList<>();Iterator<String> keys=history.keys();while(keys.hasNext()){JSONObject x=history.optJSONObject(keys.next());if(x!=null)values.add(x);}values.sort((a,b)->Long.compare(b.optLong("updated"),a.optLong("updated")));ArrayList<Anime> out=new ArrayList<>();for(JSONObject j:values){Anime a=Anime.from(j.optJSONObject("release"));if(Anime.valid(a))out.add(a);}return out;}
    public synchronized void clearHistory(){history=new JSONObject();write("history",history);}
    public synchronized int quality(){return settings.optInt("quality",720);}
    public synchronized boolean autoNext(){return settings.optBoolean("autoNext",true);}
    public synchronized void settings(int quality,boolean next){try{settings.put("quality",quality).put("autoNext",next);write("settings",settings);}catch(Exception ignored){}}
    public synchronized String exportData(){try{return new JSONObject().put("format","yoru.android.v2").put("exportedAt",new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",Locale.ROOT).format(new Date())).put("favorites",favorites).put("history",history).toString(2);}catch(Exception e){return "{}";}}
    public synchronized int importData(String input) throws Exception {if(input.length()>5*1024*1024)throw new Exception("Файл слишком большой");JSONObject root=new JSONObject(input);if(!root.optString("format").matches("yoru(?:\\.v[1-5]|\\.android\\.v[12])"))throw new Exception("Выберите файл коллекции YORU");int count=0;for(String name:new String[]{"favorites","history"}){JSONObject data=root.optJSONObject(name);if(data==null)continue;Iterator<String> keys=data.keys();while(keys.hasNext()){String k=keys.next();JSONObject item=data.optJSONObject(k);if(item==null)continue;Anime a=Anime.from(item.optJSONObject("release"));if(!Anime.valid(a)||!k.equals(a.key()))continue;if(name.equals("favorites")){String bucket=item.optString("bucket","planned");if(!Arrays.asList(BUCKETS).contains(bucket))bucket="planned";favorites.put(k,new JSONObject().put("release",a.json()).put("bucket",bucket).put("added",System.currentTimeMillis()).put("episodesSeen",Math.max(0,item.optInt("episodesSeen",a.episodes))));}else{double ep=item.optDouble("episode",1);if(!Double.isFinite(ep)||ep<0||ep>100000)continue;history.put(k,new JSONObject().put("release",a.json()).put("episode",ep).put("time",Math.min(100000,Math.max(0,item.optInt("time")))).put("duration",Math.min(100000,Math.max(0,item.optInt("duration")))).put("tracked",item.optBoolean("tracked",true)).put("updated",System.currentTimeMillis()).put("playerMode",item.optString("playerMode","")).put("dubbing",item.optString("dubbing","")));}count++;if(count>5000)break;}}write("favorites",favorites);write("history",history);return count;}
    public synchronized JSONObject traffic(){return read("traffic");}
    public synchronized void traffic(JSONObject value){write("traffic",value);}
    public synchronized boolean dataSaver(){return settings.optBoolean("dataSaver",false);}
    public synchronized boolean wifiDownloads(){return settings.optBoolean("wifiDownloads",true);}
    public synchronized void dataSaver(boolean enabled){try{settings.put("dataSaver",enabled);write("settings",settings);}catch(Exception ignored){}}
    public synchronized void wifiDownloads(boolean enabled){try{settings.put("wifiDownloads",enabled);write("settings",settings);}catch(Exception ignored){}}

}
