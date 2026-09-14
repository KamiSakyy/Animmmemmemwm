package app.yoru.mobile;

import android.content.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class YoruShield {
    private static final String FORMAT="yoru.source.profile.v1";
    private static final String DEFAULT_PROFILE="{\"format\":\"yoru.source.profile.v1\",\"version\":2,\"routes\":[{\"from\":\"https://api.cdnlibs.org/api/\",\"to\":[\"https://api.cdnlibs.org/api/\"]},{\"from\":\"https://api.yani.tv/\",\"to\":[\"https://api.yani.tv/\"]},{\"from\":\"https://shikimori.one/\",\"to\":[\"https://shikimori.one/\"]}],\"remote\":[\"https://raw.githubusercontent.com/KamiSakyy/Animmmemmemwm/arena/01a07147-animmmemmemwm/yoru-android/app/src/main/assets/yoru_profile.json\"]}";
    private final Context context;private JSONObject profile;private long lastAsync;
    YoruShield(Context c){context=c.getApplicationContext();profile=assetProfile();if(profile==null)profile=parse(DEFAULT_PROFILE);YoruApp app=YoruApp.app();if(app!=null)app.io.execute(this::loadStored);}
    private JSONObject assetProfile(){try{return parse(ApiRepository.readStream(context.getAssets().open("yoru_profile.json"),160*1024));}catch(Exception ignored){return null;}}
    private void loadStored(){try{JSONObject stored=parse(YoruApp.app().store.sourceProfile());if(stored!=null)synchronized(this){profile=stored;}}catch(Exception ignored){}}
    synchronized ArrayList<String> routes(String url){LinkedHashSet<String> out=new LinkedHashSet<>();out.add(url);JSONArray rows=profile.optJSONArray("routes");for(int i=0;rows!=null&&i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r==null)continue;String from=r.optString("from","");if(from.isEmpty()||!url.startsWith(from))continue;JSONArray to=r.optJSONArray("to");for(int n=0;to!=null&&n<to.length();n++){String base=to.optString(n,"");if(!base.isEmpty())out.add(base+url.substring(from.length()));}}return new ArrayList<>(out);}
    void ok(String url){}
    void fail(String url){long now=System.currentTimeMillis();if(now-lastAsync<6*60*60*1000L)return;lastAsync=now;YoruApp app=YoruApp.app();if(app!=null)app.io.execute(()->refresh(false));}
    synchronized void refresh(boolean force){YoruApp app=YoruApp.app();if(app==null)return;long now=System.currentTimeMillis();if(!force&&now-app.store.sourceProfileAt()<48*60*60*1000L)return;if(!force&&app.traffic!=null&&app.traffic.metered())return;JSONArray remotes=profile.optJSONArray("remote");for(int i=0;remotes!=null&&i<remotes.length();i++){String u=ApiRepository.safeUrl(remotes.optString(i,""));if(u.isEmpty())continue;try{String text=download(u);JSONObject next=parse(text);if(next!=null&&FORMAT.equals(next.optString("format"))){profile=next;app.store.sourceProfile(next.toString());return;}}catch(Exception ignored){}}}
    private static JSONObject parse(String s){try{if(s==null||s.trim().isEmpty())return null;JSONObject j=new JSONObject(s);return FORMAT.equals(j.optString("format"))?j:null;}catch(Exception e){return null;}}
    private static String download(String u)throws Exception{HttpURLConnection c=Network.open(new URL(u));c.setConnectTimeout(2500);c.setReadTimeout(3200);c.setRequestProperty("User-Agent","YORU");c.setRequestProperty("Accept","application/json,text/plain;q=0.8,*/*;q=0.4");try{int code=c.getResponseCode();if(code<200||code>=300)throw new IOException();try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>160*1024)throw new IOException();out.write(b,0,n);}return out.toString(StandardCharsets.UTF_8.name());}}finally{c.disconnect();}}
}
