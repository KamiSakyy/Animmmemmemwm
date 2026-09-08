package app.yoru.vk;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class Api {
    private static long lastCall;
    static final class Failure extends IOException {
        final int code;
        Failure(int code,String message){super(message);this.code=code;}
    }
    private static synchronized void pace() throws InterruptedException {
        long remaining=450-(System.currentTimeMillis()-lastCall);if(remaining>0)Thread.sleep(remaining);lastCall=System.currentTimeMillis();
    }
    static String encode(String s){try{return URLEncoder.encode(s,"UTF-8");}catch(Exception e){return "";}}
    static String request(String address,String body,String contentType) throws Exception {
        pace();if(Thread.currentThread().isInterrupted())throw new InterruptedException();
        HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();
        c.setConnectTimeout(7000);c.setReadTimeout(12000);c.setInstanceFollowRedirects(false);c.setUseCaches(false);
        c.setRequestProperty("User-Agent","YORU-VK-Web/0.2.0 (Android; independent prototype)");c.setRequestProperty("Accept","application/json");
        try{
            if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type",contentType);byte[] bytes=body.getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);try(OutputStream os=c.getOutputStream()){os.write(bytes);}}
            int status=c.getResponseCode();if(status!=200)throw new Failure(-status,"Сервис ответил HTTP "+status+". Проверьте сеть и повторите позже.");
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedException();if(out.size()+n>6*1024*1024)throw new IOException("Слишком большой ответ сервиса.");out.write(buffer,0,n);}return out.toString("UTF-8");}
        }finally{c.disconnect();}
    }
    private static final String FIELDS="id name russian english kind score status episodes episodesAired airedOn{year} poster{mainUrl originalUrl} genres{russian name}";
    static List<Models.Anime> catalog(String query,int page) throws Exception {
        String args="limit:20,page:"+page+",order:ranked,rating:\"!rx\""+(query.isEmpty()?"":",search:"+JSONObject.quote(query));
        try {JSONArray rows=graph("{animes("+args+"){"+FIELDS+"}}");return animeRows(rows,true);}
        catch(InterruptedException e){throw e;}
        catch(Exception ignored){return animeRows(restArray("/api/animes?limit=20&page="+page+"&order=ranked&censored=true&search="+encode(query)),false);}
    }
    static Models.Anime details(long id) throws Exception {
        try {JSONArray rows=graph("{animes(ids:"+JSONObject.quote(String.valueOf(id))+",limit:1){"+FIELDS+" descriptionHtml}}");if(rows.length()>0)return Models.anime(rows.getJSONObject(0),true);}
        catch(InterruptedException e){throw e;}catch(Exception ignored){}
        return Models.anime(new JSONObject(rest("/api/animes/"+id)),false);
    }
    static List<Models.Anime> related(long id) throws Exception {
        JSONArray rows=restArray("/api/animes/"+id+"/related");List<Models.Anime> result=new ArrayList<>();
        for(int i=0;i<rows.length();i++){JSONObject anime=rows.getJSONObject(i).optJSONObject("anime");if(anime!=null)result.add(Models.anime(anime,false));}return result;
    }
    private static JSONArray graph(String query) throws Exception {
        Exception last=null;for(String base:new String[]{"https://shikimori.io","https://shikimori.one"}){try{JSONObject json=new JSONObject(request(base+"/api/graphql",new JSONObject().put("query",query).toString(),"application/json; charset=UTF-8"));if(json.has("errors"))throw new IOException("Shikimori отклонил запрос.");return json.getJSONObject("data").getJSONArray("animes");}catch(InterruptedException e){throw e;}catch(Exception e){last=e;}}throw new IOException("Shikimori GraphQL недоступен.",last);
    }
    private static String rest(String path) throws Exception {
        for(String base:new String[]{"https://shikimori.io","https://shikimori.one"}){try{return request(base+path,null,null);}catch(InterruptedException e){throw e;}catch(Exception ignored){}}
        throw new IOException("Shikimori сейчас недоступен. Проверьте сеть и повторите запрос.");
    }
    private static JSONArray restArray(String path) throws Exception{return new JSONArray(rest(path));}
    private static List<Models.Anime> animeRows(JSONArray rows,boolean graph){List<Models.Anime> out=new ArrayList<>();for(int i=0;i<rows.length();i++){JSONObject j=rows.optJSONObject(i);if(j!=null){Models.Anime a=Models.anime(j,graph);if(a.id>0&&!a.title.isEmpty())out.add(a);}}return out;}
}
