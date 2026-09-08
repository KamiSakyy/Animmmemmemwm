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
}
