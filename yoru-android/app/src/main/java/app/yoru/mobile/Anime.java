package app.yoru.mobile;

import org.json.*;
import java.util.*;

public final class Anime {
    public String source="shikimori", id="", title="", original="", alias="", poster="", description="", type="Аниме", status="", age="", studio="";
    public int year, episodes, malId, anilistId, kpId;
    public String libriaAlias="";
    public double score;
    public boolean blocked, cached;
    public final ArrayList<String> genres=new ArrayList<>();
    public final ArrayList<Anime> related=new ArrayList<>();
    public final ArrayList<Episode> episodeList=new ArrayList<>();
    public String key(){ return "anilibria".equals(source)?id:source+":"+id; }
    public boolean metadataOnly(){return "shikimori".equals(source);}
    public String meta(){String s=year>0?String.valueOf(year):"";if(!type.isEmpty())s+=(s.isEmpty()?"":" · ")+type;return s;}
    public JSONObject json(){JSONObject j=new JSONObject();try{j.put("id",key());j.put("provider",source);j.put("nativeId",id);j.put("title",title);j.put("english",original);j.put("alias",alias);j.put("poster",poster);j.put("description",description.length()>1200?description.substring(0,1200):description);j.put("year",year);j.put("type",type);j.put("status",status);j.put("age",age);j.put("studio",studio);j.put("episodesTotal",episodes);j.put("malId",malId);j.put("anilistId",anilistId);j.put("kpId",kpId);j.put("libriaAlias",libriaAlias);j.put("ratingScore",score);JSONArray gs=new JSONArray();for(String g:genres)gs.put(new JSONObject().put("id",g).put("name",g));j.put("genres",gs);}catch(JSONException ignored){}return j;}
    private static String clip(String s,int max){return s==null?"":s.length()>max?s.substring(0,max):s;}
    public static Anime from(JSONObject j){Anime a=new Anime();if(j==null)return a;String key=j.optString("id","");String[] parts=key.split(":",2);a.source=j.optString("provider",parts.length==2?parts[0]:"anilibria");a.id=j.optString("nativeId",parts.length==2?parts[1]:key);a.title=j.optString("title","Без названия");a.original=j.optString("english","");a.alias=j.optString("alias","");a.poster=j.optString("poster","");a.description=j.optString("description","");a.year=j.optInt("year",0);a.type=j.optString("type","Аниме");a.status=j.optString("status",j.optBoolean("ongoing")?"ongoing":"released");a.age=j.optString("age","");a.studio=j.optString("studio","");a.episodes=j.optInt("episodesTotal",0);a.malId=j.optInt("malId",0);a.anilistId=j.optInt("anilistId",0);a.kpId=j.optInt("kpId",0);a.libriaAlias=j.optString("libriaAlias","");a.score=j.optDouble("ratingScore",0);if(!Double.isFinite(a.score))a.score=0;JSONArray gs=j.optJSONArray("genres");if(gs!=null)for(int i=0;i<gs.length();i++){Object g=gs.opt(i);if(g instanceof JSONObject)a.genres.add(((JSONObject)g).optString("name",""));else if(g instanceof String)a.genres.add((String)g);}a.title=clip(a.title,300);a.original=clip(a.original,300);a.alias=clip(a.alias,1800);a.poster=clip(a.poster,4096);a.description=clip(a.description,1200);a.studio=clip(a.studio,120);while(a.genres.size()>30)a.genres.remove(a.genres.size()-1);if(a.source.equals("shikimori")&&a.malId==0)try{a.malId=Integer.parseInt(a.id);}catch(Exception ignored){}return a;}
    public static boolean valid(Anime a){return a!=null&&ApiRepository.sourceNames.containsKey(a.source)&&a.id.matches("[1-9][0-9]{0,10}")&&!a.title.isEmpty();}
    public static final class Episode {
        public String id="", name="", lazy="", resolverUrl="";public double number;public int duration,openingStart,openingEnd;public final TreeMap<Integer,String> streams=new TreeMap<>();public final ArrayList<Variant> variants=new ArrayList<>();
        public String label(){String n=number==Math.floor(number)?String.valueOf((int)number):String.valueOf(number);return "Серия "+n+(name.isEmpty()?"":" · "+name);}
    }
    public static final class Variant {
        public String name="Озвучка",player="Плеер",url="",displayName="";public int duration,openingStart,openingEnd;
        public Variant(String n,String p,String u){name=n;player=p;url=u;}
        public String label(){return displayName.isEmpty()?name:displayName;}
    }
    public static final class Page {
        public ArrayList<Anime> items=new ArrayList<>();public long total=-1;public int page=1;public boolean more;public String note="";
    }
    public static final class Playback {
        public Anime catalog,video;public String directUrl="",provider="",initialVoice="";public boolean directKodik;
    }
}
