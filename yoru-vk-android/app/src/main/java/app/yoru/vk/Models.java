package app.yoru.vk;

import org.json.*;
import java.net.URI;
import java.util.*;

final class Models {
    static final class Anime {
        long id;
        String title="", original="", english="", poster="", description="", kind="", status="", genres="";
        int episodes, aired, year;
        double score;
        boolean movie() { return "movie".equals(kind); }
        String meta() { return (year > 0 ? year + " · " : "") + (movie() ? "Фильм" : kind.toUpperCase(Locale.ROOT)) + (episodes > 0 ? " · " + episodes + " серий" : "") + (score > 0 ? String.format(Locale.US," · ★ %.2f",score) : ""); }
    }
    static final class Video {
        long owner, id, views;
        int duration;
        String title="", poster="", accessKey="";
        JSONObject files = new JSONObject();
        String key() { return owner + "_" + id + (accessKey.isEmpty() ? "" : "_" + accessKey); }
        String durationText() { return String.format(Locale.US, "%d:%02d", duration / 60, duration % 60); }
    }
    static Anime anime(JSONObject j, boolean graph) {
        Anime a=new Anime(); a.id=j.optLong("id"); a.original=j.optString("name"); a.title=j.optString("russian");
        if(a.title.isEmpty() || a.title.equals("null")) a.title=a.original;
        Object english=j.opt("english"); a.english=english instanceof JSONArray?((JSONArray)english).optString(0,""):j.optString("english","");
        if(a.english.equals("null")) a.english="";
        a.kind=j.optString("kind"); a.status=j.optString("status"); a.episodes=j.optInt("episodes");a.aired=j.optInt(graph?"episodesAired":"episodes_aired"); a.score=j.optDouble("score",0);
        if(graph){JSONObject poster=j.optJSONObject("poster"),date=j.optJSONObject("airedOn");a.poster=poster==null?"":poster.optString("mainUrl",poster.optString("originalUrl"));a.year=date==null?0:date.optInt("year");a.description=j.optString("descriptionHtml","");}
        else{JSONObject image=j.optJSONObject("image");a.poster=image==null?"":image.optString("original","");String date=j.optString("aired_on","");if(date.matches("\\d{4}.*"))a.year=Integer.parseInt(date.substring(0,4));a.description=j.optString("description_html",j.optString("description", ""));}
        if(a.poster.startsWith("/"))a.poster="https://shikimori.io"+a.poster;
        if(a.description.equals("null"))a.description="";
        JSONArray genres=j.optJSONArray("genres");ArrayList<String> names=new ArrayList<>();for(int i=0;genres!=null&&i<genres.length();i++){JSONObject g=genres.optJSONObject(i);if(g!=null)names.add(g.optString("russian",g.optString("name")));}a.genres=String.join(" · ",names);
        return a;
    }
    static Video video(JSONObject j) {
        Video v=new Video();v.owner=j.optLong("owner_id");v.id=j.optLong("id");v.title=j.optString("title", "Видео ВК");v.duration=j.optInt("duration");v.views=j.optLong("views");v.accessKey=j.optString("access_key","");
        if(v.accessKey.equals("null"))v.accessKey="";
        v.files=j.optJSONObject("files");if(v.files==null)v.files=new JSONObject();
        JSONArray images=j.optJSONArray("image");int best=0;for(int i=0;images!=null&&i<images.length();i++){JSONObject im=images.optJSONObject(i);if(im!=null&&im.optInt("width")>best){best=im.optInt("width");v.poster=im.optString("url");}}
        if(v.poster.isEmpty())v.poster=j.optString("photo_320",j.optString("photo_130",""));
        return v;
    }
    static List<Video> videos(JSONObject response) {
        LinkedHashMap<String,Video> map=new LinkedHashMap<>();JSONArray items=response.optJSONArray("items");
        for(int i=0;items!=null&&i<items.length();i++){JSONObject item=items.optJSONObject(i);if(item==null)continue;Video v=video(item);if(v.id>0&&v.owner!=0)map.put(v.owner+"_"+v.id,v);}
        return new ArrayList<>(map.values());
    }
    static LinkedHashMap<String,String> streams(JSONObject files) {
        LinkedHashMap<String,String> out=new LinkedHashMap<>();
        for(int q:new int[]{1080,720,480,360,240,144,1440,2160}){String u=files.optString("mp4_"+q,"");if(safeMedia(u))out.put(q+"p",u);}
        for(String k:new String[]{"hls","hls_ondemand","dash_uni","dash"}){String u=files.optString(k,"");if(safeMedia(u))out.put(k.startsWith("hls")?"HLS · авто":"DASH · авто",u);}
        return out;
    }
    static boolean safeMedia(String value) {
        try {URI u=new URI(value);String host=u.getHost();if(!"https".equals(u.getScheme())||host==null||u.getUserInfo()!=null||(u.getPort()!=-1&&u.getPort()!=443))return false;
            for(String suffix:new String[]{"vk.com","vk.ru","vkvideo.ru","vkvideo.net","vkuser.net","vkuserlive.net","userapi.com","vk-cdn.net","vk-cdn.me","mycdn.me","okcdn.ru"})if(host.equals(suffix)||host.endsWith("."+suffix))return true;
        } catch(Exception ignored) {}return false;
    }
}
