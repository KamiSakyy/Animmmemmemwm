package app.yoru.vk;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class Api {
    static final String VERSION="5.199";
    private static long lastCall;
    static final class Failure extends IOException {
        final int code;
        Failure(int code,String message){super(message);this.code=code;}
    }
    static String vkError(int code) {
        switch(code){
            case 5:return "Токен истёк, отозван или не подходит. Подключите ВК заново.";
            case 6:case 9:case 29:return "ВК ограничил частоту запросов. Подождите и повторите вручную.";
            case 7:case 15:case 20:case 27:case 28:return "Нет доступа к методу или видео. Нужен пользовательский токен с правом video. Даже токен Kate Mobile не гарантирует доступ.";
            case 14:return "ВК запросил CAPTCHA. Подтвердите действия в официальном ВК; автоматического обхода нет.";
            case 17:return "ВК требует подтверждения входа. Проверьте аккаунт в официальном приложении и обновите токен.";
            case 18:return "Аккаунт заблокирован или удалён.";
            case 100:return "ВК отклонил параметры запроса. Проверьте запрос или выберите другое видео.";
            case 3:return "Метод недоступен для этой версии API или приложения.";
            default:return "ВК вернул ошибку " + code + ". Повторите позже или проверьте права токена.";
        }
    }
    private static synchronized void pace() throws InterruptedException {
        long remaining=450-(System.currentTimeMillis()-lastCall);if(remaining>0)Thread.sleep(remaining);lastCall=System.currentTimeMillis();
    }
    static String encode(String s){try{return URLEncoder.encode(s,"UTF-8");}catch(Exception e){return "";}}
    static String request(String address,String body,String contentType) throws Exception {
        pace();if(Thread.currentThread().isInterrupted())throw new InterruptedException();
        HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();
        c.setConnectTimeout(7000);c.setReadTimeout(12000);c.setInstanceFollowRedirects(false);c.setUseCaches(false);
        c.setRequestProperty("User-Agent","YORU-VK/0.1.0 (Android; independent prototype)");c.setRequestProperty("Accept","application/json");
        try{
            if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type",contentType);byte[] bytes=body.getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);try(OutputStream os=c.getOutputStream()){os.write(bytes);}}
            int status=c.getResponseCode();if(status!=200)throw new Failure(-status,"Сервис ответил HTTP "+status+". Проверьте сеть и повторите позже.");
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedException();if(out.size()+n>6*1024*1024)throw new IOException("Слишком большой ответ сервиса.");out.write(buffer,0,n);}return out.toString("UTF-8");}
        }finally{c.disconnect();}
    }
    static Object vk(String method,String token,String... args) throws Exception {
        if(!Arrays.asList("users.get","video.search","video.get").contains(method))throw new IOException("Метод не разрешён приложением.");
        StringBuilder body=new StringBuilder("access_token=").append(encode(token)).append("&v=").append(VERSION);
        for(int i=0;i<args.length;i+=2)body.append('&').append(encode(args[i])).append('=').append(encode(args[i+1]));
        JSONObject json=new JSONObject(request("https://api.vk.com/method/"+method,body.toString(),"application/x-www-form-urlencoded; charset=UTF-8"));
        JSONObject error=json.optJSONObject("error");if(error!=null){int code=error.optInt("error_code");throw new Failure(code,vkError(code));}
        if(!json.has("response"))throw new IOException("ВК вернул неизвестный формат ответа.");return json.get("response");
    }
    static String account(String token) throws Exception {
        Object raw=vk("users.get",token);if(!(raw instanceof JSONArray)||((JSONArray)raw).length()==0)throw new IOException("Не удалось проверить пользовательский токен.");
        JSONObject user=((JSONArray)raw).getJSONObject(0);if(user.optLong("id")<=0)throw new IOException("Токен не содержит пользовательский аккаунт.");
        return user.optString("first_name")+" "+user.optString("last_name");
    }
    static List<Models.Video> search(String token,String q,int offset,boolean episodesOnly) throws Exception {
        if(q.trim().isEmpty())throw new IOException("Введите запрос.");
        if(offset>=500)throw new IllegalArgumentException("VK API даёт только первые 500 результатов. Уточните запрос.");
        JSONObject response=(JSONObject)vk("video.search",token,"q",q,"count",String.valueOf(Math.min(30,500-offset)),"offset",String.valueOf(offset),"sort","2","adult","0","filters","vk","longer",episodesOnly?"600":"0");
        return Models.videos(response);
    }
    static Models.Video video(String token,String key) throws Exception {
        JSONObject response=(JSONObject)vk("video.get",token,"videos",key);List<Models.Video> rows=Models.videos(response);if(rows.isEmpty())throw new IOException("Видео удалено, закрыто или недоступно этому токену.");return rows.get(0);
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
