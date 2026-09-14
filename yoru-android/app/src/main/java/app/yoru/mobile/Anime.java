package app.yoru.mobile;

import org.json.*;
import java.util.*;

public final class Anime {
    public String source="shikimori", id="", title="", original="", alias="", poster="", description="", type="Аниме", status="", age="", studio="", trailerUrl="", airedDate="", nextEpisodeAt="", countries="", cast="", crew="", ratings="", franchise="";
    public int year, episodes, episodesAired, malId, anilistId, kpId, durationMinutes, translationsCount, shikimoriOrder;
    public String libriaAlias="";
    public double score,shikimoriScore;public int shikimoriEpisodes;
    public boolean blocked, cached;
    public final ArrayList<String> genres=new ArrayList<>();
    public final ArrayList<Anime> related=new ArrayList<>();
    public final ArrayList<String> screenshots=new ArrayList<>();
    public final ArrayList<Episode> episodeList=new ArrayList<>();
    public String key(){ return "anilibria".equals(source)?id:source+":"+id; }
    public boolean metadataOnly(){return "shikimori".equals(source);}
    public String meta(){String s=year>0?String.valueOf(year):"";if(!type.isEmpty())s+=(s.isEmpty()?"":" · ")+type;return s;}
    public double ratingScore(){double value=shikimoriScore>0?shikimoriScore:("shikimori".equals(source)?score:0);return Double.isFinite(value)&&value>0&&value<=10?value:0;}
    public int totalEpisodes(){return shikimoriEpisodes>0?shikimoriEpisodes:Math.max(0,episodes);}
    public int aired(){int n=episodesAired>0?episodesAired:EpisodeRules.available(episodeList),total=totalEpisodes();return Math.max(0,total>0?Math.min(total,n):n);}
    private String releaseStatus(){return status==null?"":status.trim().toLowerCase(Locale.ROOT);}
    public boolean announced(){String s=releaseStatus();return s.equals("anons")||s.equals("announcement")||s.equals("announced")||s.equals("not_yet_released")||s.equals("not_yet_aired")||s.equals("upcoming")||s.equals("анонс");}
    public boolean ongoing(){String s=releaseStatus();return announced()||s.equals("ongoing")||s.equals("releasing")||s.equals("airing")||s.equals("currently_airing")||s.equals("выходит")||s.equals("онгоинг");}
    public String statusLabel(){String s=releaseStatus();if(announced())return "Анонс";if(ongoing())return "Онгоинг";if(s.equals("released")||s.equals("finished")||s.equals("complete")||s.equals("completed")||s.equals("закончен")||s.equals("закончено")||s.equals("завершен")||s.equals("завершён")||s.equals("завершено"))return "Закончен";return "Статус уточняется";}
    public String episodesLine(){int aired=aired(),total=totalEpisodes();if(total>0&&aired>0)return "Вышло "+aired+" из "+total;if(total>0)return total+" серий";if(aired>0)return "Вышло "+aired;return "";}
    public String cardMeta(){String m=meta(),e=episodesLine(),st=statusLabel();String out=m; if(!st.isEmpty())out+=(out.isEmpty()?"":" · ")+st; if(!e.isEmpty())out+=(out.isEmpty()?"":" · ")+e; return out;}
    public JSONObject json(){JSONObject j=new JSONObject();try{j.put("id",key());j.put("provider",source);j.put("nativeId",id);j.put("title",title);j.put("english",original);j.put("alias",alias);j.put("poster",poster);j.put("description",description.length()>1200?description.substring(0,1200):description);j.put("year",year);j.put("type",type);j.put("status",status);j.put("age",age);j.put("studio",studio);j.put("episodesTotal",episodes);j.put("episodesAired",episodesAired);j.put("nextEpisodeAt",nextEpisodeAt);j.put("malId",malId);j.put("anilistId",anilistId);j.put("kpId",kpId);j.put("libriaAlias",libriaAlias);j.put("ratingScore",score);j.put("shikimoriScore",ratingScore());j.put("shikimoriEpisodes",shikimoriEpisodes);j.put("trailerUrl",trailerUrl);j.put("airedDate",airedDate);j.put("countries",countries);j.put("cast",cast);j.put("crew",crew);j.put("ratings",ratings);j.put("franchise",franchise);j.put("durationMinutes",durationMinutes);j.put("translationsCount",translationsCount);j.put("shikimoriOrder",shikimoriOrder);JSONArray ss=new JSONArray();for(String x:screenshots)if(x!=null&&!x.isEmpty())ss.put(x);j.put("screenshots",ss);JSONArray gs=new JSONArray();for(String g:genres)gs.put(new JSONObject().put("id",g).put("name",g));j.put("genres",gs);}catch(JSONException ignored){}return j;}
    private static String clip(String s,int max){return s==null?"":s.length()>max?s.substring(0,max):s;}
    public static Anime from(JSONObject j){Anime a=new Anime();if(j==null)return a;String key=j.optString("id","");String[] parts=key.split(":",2);a.source=j.optString("provider",parts.length==2?parts[0]:"anilibria");a.id=j.optString("nativeId",parts.length==2?parts[1]:key);a.title=j.optString("title","Без названия");a.original=j.optString("english","");a.alias=j.optString("alias","");a.poster=j.optString("poster","");a.description=j.optString("description","");a.year=j.optInt("year",0);a.type=j.optString("type","Аниме");a.status=j.optString("status",j.has("ongoing")?(j.optBoolean("ongoing")?"ongoing":"released"):"");a.age=j.optString("age","");a.studio=j.optString("studio","");a.episodes=j.optInt("episodesTotal",0);a.episodesAired=j.optInt("episodesAired",0);a.nextEpisodeAt=j.optString("nextEpisodeAt","");a.malId=j.optInt("malId",0);a.anilistId=j.optInt("anilistId",0);a.kpId=j.optInt("kpId",0);a.libriaAlias=j.optString("libriaAlias","");a.score=j.optDouble("ratingScore",0);a.shikimoriScore=j.optDouble("shikimoriScore",0);a.shikimoriEpisodes=j.optInt("shikimoriEpisodes",0);a.trailerUrl=j.optString("trailerUrl","");a.airedDate=j.optString("airedDate","");a.countries=j.optString("countries","");a.cast=j.optString("cast","");a.crew=j.optString("crew","");a.ratings=j.optString("ratings","");a.franchise=j.optString("franchise","");a.durationMinutes=j.optInt("durationMinutes",0);a.translationsCount=j.optInt("translationsCount",0);a.shikimoriOrder=j.optInt("shikimoriOrder",0);JSONArray ss=j.optJSONArray("screenshots");if(ss!=null)for(int i=0;i<ss.length();i++){Object raw=ss.opt(i);String u="";if(raw instanceof JSONObject){JSONObject o=(JSONObject)raw;u=o.optString("original",o.optString("preview",o.optString("url",o.optString("src",""))));}else u=ss.optString(i,"");if(!u.isEmpty()&&!a.screenshots.contains(u)&&a.screenshots.size()<12)a.screenshots.add(u);}if(!Double.isFinite(a.score))a.score=0;if(!Double.isFinite(a.shikimoriScore)||a.shikimoriScore<0||a.shikimoriScore>10)a.shikimoriScore=0;a.shikimoriEpisodes=Math.max(0,a.shikimoriEpisodes);JSONArray gs=j.optJSONArray("genres");if(gs!=null)for(int i=0;i<gs.length();i++){Object g=gs.opt(i);if(g instanceof JSONObject)a.genres.add(((JSONObject)g).optString("name",""));else if(g instanceof String)a.genres.add((String)g);}a.title=clip(a.title,300);a.original=clip(a.original,300);a.alias=clip(a.alias,1800);a.poster=clip(a.poster,4096);a.trailerUrl=clip(a.trailerUrl,4096);a.airedDate=clip(a.airedDate,32);a.nextEpisodeAt=clip(a.nextEpisodeAt,48);a.description=clip(a.description,1200);a.studio=clip(a.studio,120);a.countries=clip(a.countries,160);a.cast=clip(a.cast,600);a.crew=clip(a.crew,600);a.ratings=clip(a.ratings,360);a.franchise=clip(a.franchise,120);while(a.screenshots.size()>12)a.screenshots.remove(a.screenshots.size()-1);while(a.genres.size()>30)a.genres.remove(a.genres.size()-1);if(a.source.equals("shikimori")&&a.malId==0)try{a.malId=Integer.parseInt(a.id);}catch(Exception ignored){}return a;}
    public static boolean valid(Anime a){return a!=null&&ApiRepository.sourceNames.containsKey(a.source)&&a.id.matches("[1-9][0-9]{0,10}")&&!a.title.isEmpty();}
    public static final class Episode {
        public String id="", name="", lazy="", resolverUrl="", poster="", airDate="";public double number;public int duration,openingStart,openingEnd;public boolean future;public final TreeMap<Integer,String> streams=new TreeMap<>();public final ArrayList<Variant> variants=new ArrayList<>();
        public String label(){String n=number==Math.floor(number)?String.valueOf((int)number):String.valueOf(number);return "Серия "+n+(future?(airDate.isEmpty()?" · дата уточняется":" · "+airDate):(name.isEmpty()?"":" · "+name));}
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
