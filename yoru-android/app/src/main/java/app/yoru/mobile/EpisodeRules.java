package app.yoru.mobile;

import java.util.*;

final class EpisodeRules {
    private EpisodeRules(){}
    static boolean completed(Anime anime,double episode,int seconds,int duration){return anime!=null&&anime.totalEpisodes()>0&&"Закончен".equals(anime.statusLabel())&&Double.isFinite(episode)&&Math.abs(episode-anime.totalEpisodes())<.001&&duration>0&&seconds>=Math.max(0,duration-1);}
    static boolean conflicts(Anime a,Anime b){return a!=null&&b!=null&&((shikimoriId(a)>0&&shikimoriId(b)>0&&shikimoriId(a)!=shikimoriId(b))||(a.anilistId>0&&b.anilistId>0&&a.anilistId!=b.anilistId));}
    private static int shikimoriId(Anime anime){if(anime.malId>0)return anime.malId;if(!"shikimori".equals(anime.source))return 0;try{return Math.max(0,Integer.parseInt(anime.id));}catch(NumberFormatException ignored){return 0;}}
    static boolean allowsNumber(Anime anime,double number){
        if(!Double.isFinite(number)||number<0)return false;
        int total=anime==null?0:anime.shikimoriEpisodes>0?anime.shikimoriEpisodes:"shikimori".equals(anime.source)?anime.episodes:0;
        return total<=0||number!=Math.floor(number)||number<=total;
    }
    static ArrayList<Anime.Episode> downloadable(Anime catalog,List<Anime.Episode> episodes){ArrayList<Anime.Episode> rows=new ArrayList<>();for(Anime.Episode e:visible(catalog,episodes))if(!e.future)rows.add(e);return rows;}
    static int available(List<Anime.Episode> episodes){HashSet<Double> numbers=new HashSet<>();if(episodes!=null)for(Anime.Episode episode:episodes)if(episode!=null&&!episode.future&&Double.isFinite(episode.number)&&episode.number>=1&&episode.number==Math.floor(episode.number))numbers.add(episode.number);return numbers.size();}
    static List<Anime.Episode> visible(Anime catalog,List<Anime.Episode> episodes){TreeMap<Double,Anime.Episode> rows=new TreeMap<>();if(episodes!=null)for(Anime.Episode episode:episodes){if(episode==null||!allowsNumber(catalog,episode.number))continue;Anime.Episode old=rows.get(episode.number);if(old==null||(old.future&&!episode.future))rows.put(episode.number,episode);}return new ArrayList<>(rows.values());}
}
