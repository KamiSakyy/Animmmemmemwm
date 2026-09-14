package app.yoru.mobile;

import java.util.*;

final class EpisodeRules {
    private EpisodeRules(){}
    static boolean conflicts(Anime a,Anime b){return a!=null&&b!=null&&((a.malId>0&&b.malId>0&&a.malId!=b.malId)||(a.anilistId>0&&b.anilistId>0&&a.anilistId!=b.anilistId));}
    static ArrayList<Anime.Episode> downloadable(Anime catalog,List<Anime.Episode> episodes){ArrayList<Anime.Episode> rows=new ArrayList<>();for(Anime.Episode e:visible(catalog,episodes))if(!e.future)rows.add(e);return rows;}
    static int available(List<Anime.Episode> episodes){HashSet<Double> numbers=new HashSet<>();if(episodes!=null)for(Anime.Episode episode:episodes)if(episode!=null&&!episode.future&&Double.isFinite(episode.number)&&episode.number>=1&&episode.number==Math.floor(episode.number))numbers.add(episode.number);return numbers.size();}
    static List<Anime.Episode> visible(Anime catalog,List<Anime.Episode> episodes){TreeMap<Double,Anime.Episode> rows=new TreeMap<>();int total=catalog==null?0:catalog.shikimoriEpisodes>0?catalog.shikimoriEpisodes:catalog.source.equals("shikimori")?catalog.episodes:0;boolean bounded=total>0;if(episodes!=null)for(Anime.Episode episode:episodes){if(episode==null||!Double.isFinite(episode.number)||episode.number<0)continue;if(bounded&&episode.number==Math.floor(episode.number)&&episode.number>total)continue;Anime.Episode old=rows.get(episode.number);if(old==null||(old.future&&!episode.future))rows.put(episode.number,episode);}return new ArrayList<>(rows.values());}
}
