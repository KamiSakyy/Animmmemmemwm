package app.yoru.mobile;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.media3.common.*;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.offline.*;
import androidx.media3.exoplayer.scheduler.Requirements;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DownloadHub {
    public static final int USER_PAUSED=1;
    public final DownloadManager manager;
    private final ConcurrentHashMap<String,TaskQueue.Signal> retryTasks=new ConcurrentHashMap<>();
    private final Map<String,DownloadHelper> preparations=new HashMap<>();
    private final Map<String,Runnable> preparationTimeouts=new HashMap<>();
    private final Context context;private final MediaCache caches;private final Handler main=new Handler(Looper.getMainLooper());private final Set<String> preparing=Collections.synchronizedSet(new HashSet<>());private final Map<String,DownloadRequest> replacement=new ConcurrentHashMap<>();private final ConcurrentHashMap<String,Download> completedIndex=new ConcurrentHashMap<>();private volatile long indexAt;private final AtomicBoolean indexing=new AtomicBoolean(false);
    public DownloadHub(Context c,MediaCache cache){context=c.getApplicationContext();caches=cache;manager=new DownloadManager(context,caches.database(),caches.offline(),caches.http(),Executors.newFixedThreadPool(3));manager.setMaxParallelDownloads(3);applyRequirements();manager.addListener(new DownloadManager.Listener(){@Override public void onDownloadChanged(DownloadManager dm,Download d,Exception error){refreshIndexAsync();if(d.state==Download.STATE_COMPLETED)DocumentDownloads.save(context,d,false);}@Override public void onDownloadRemoved(DownloadManager dm,Download d){DownloadRequest pending=replacement.remove(d.request.id);if(pending!=null)dm.addDownload(pending);refreshIndexAsync();}});refreshIndexAsync();}
    public void applyRequirements(){manager.setRequirements(new Requirements(Requirements.NETWORK|(YoruApp.app().store.wifiDownloads()?Requirements.NETWORK_UNMETERED:0)));}
    public List<Download> all(){ArrayList<Download> list=new ArrayList<>();DownloadCursor cursor=null;try{cursor=manager.getDownloadIndex().getDownloads();while(cursor.moveToNext())list.add(cursor.getDownload());}catch(IOException ignored){}finally{if(cursor!=null)cursor.close();}list.sort((a,b)->Long.compare(b.startTimeMs,a.startTimeMs));return list;}
    public Download get(String id){try{return manager.getDownloadIndex().getDownload(id);}catch(IOException e){return null;}}
    public static JSONObject metadata(Download d){try{return new JSONObject(new String(d.request.data,StandardCharsets.UTF_8));}catch(Exception e){return new JSONObject();}}
    public static String status(Download d){switch(d.state){case Download.STATE_COMPLETED:return "Готово к просмотру офлайн";case Download.STATE_DOWNLOADING:return "Скачивается";case Download.STATE_QUEUED:return "В очереди / ожидание сети";case Download.STATE_STOPPED:return "Приостановлено";case Download.STATE_FAILED:return "Не удалось скачать";case Download.STATE_REMOVING:return "Удаляется";case Download.STATE_RESTARTING:return "Перезапускается";default:return "Подготовка";}}
    public static String idFor(Anime source,Anime.Episode ep,int quality){String input=source.key()+"|"+ep.id+"|"+ep.number+"|"+quality+"|"+ep.name;try{byte[] hash=MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:hash)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}catch(Exception e){return input.replace(':','_');}}
    public boolean preparing(String id){return preparing.contains(id);}
    private static String episodePoster(Anime catalog,Anime.Episode ep){String p=ep==null?"":ApiRepository.safeUrl(ep.poster);if(!p.isEmpty())return p;if(catalog!=null&&!catalog.screenshots.isEmpty()&&ep!=null)return catalog.screenshots.get(Math.abs((int)Math.floor(ep.number)-1)%catalog.screenshots.size());return "";}
    public void enqueue(Anime catalog,Anime source,Anime.Episode ep,int quality){String voice=source==null?"":ApiRepository.sourceVoice(source.source);if(voice.isEmpty()&&ep!=null)voice=ApiRepository.voiceTitle(ep.name);enqueue(catalog,source,ep,quality,voice);}
    public void enqueue(Anime catalog,Anime source,Anime.Episode ep,int quality,String voice){enqueue(catalog,source,ep,quality,voice,"");}
    public void enqueue(Anime catalog,Anime source,Anime.Episode ep,int quality,String voice,String mime){enqueue(catalog,source,ep,quality,voice,mime,"");}
    private void enqueue(Anime catalog,Anime source,Anime.Episode ep,int quality,String voice,String mime,String replacementId){
        if(Looper.myLooper()!=Looper.getMainLooper()){main.post(()->enqueue(catalog,source,ep,quality,voice,mime,replacementId));return;}
        if(!Anime.valid(catalog)||!Anime.valid(source)||ep==null||ep.future||!EpisodeRules.allowsNumber(catalog,ep.number)||EpisodeRules.conflicts(catalog,source)){Ui.toast(context,"Выбранная серия недоступна для этого аниме");return;}
        String url=ep.streams.get(quality);if(url==null||ApiRepository.safeUrl(url).isEmpty()){Ui.toast(context,"Серия пока не подготовлена для загрузки");return;}
        String id=replacementId.isEmpty()?idFor(source,ep,quality):replacementId;Download exists=get(id);
        if(exists!=null&&exists.state==Download.STATE_COMPLETED){Ui.toast(context,"Эта серия уже скачана");return;}
        if(exists!=null&&exists.state!=Download.STATE_FAILED&&exists.state!=Download.STATE_STOPPED&&exists.state!=Download.STATE_REMOVING){Ui.toast(context,"Эта серия уже в очереди");return;}
        if(!preparing.add(id)){Ui.toast(context,"Серия уже подготавливается");return;}
        if(context.getFilesDir().getUsableSpace()<100L*1024*1024){preparing.remove(id);Ui.toast(context,"Недостаточно свободного места");return;}
        Ui.toast(context,"Подготавливаем серию к скачиванию…");
        try{
            JSONObject meta=new JSONObject().put("anime",catalog.json()).put("source",source.json()).put("episode",ep.number).put("episodeId",ep.id).put("title",ep.name).put("voice",voice==null?"":voice).put("episodePoster",episodePoster(catalog,ep)).put("quality",quality).put("created",System.currentTimeMillis());
            DownloadHelper helper=DownloadHelper.forMediaItem(item(url,mime),DownloadTracks.parameters(context,quality),new DefaultRenderersFactory(context),caches.http());preparations.put(id,helper);
            Runnable timeout=()->{if(preparations.get(id)!=helper)return;finishPreparation(id,helper);Ui.toast(context,"Подготовка заняла слишком много времени. Повторите загрузку позже.");};
            preparationTimeouts.put(id,timeout);main.postDelayed(timeout,30000);
            helper.prepare(new DownloadHelper.Callback(){
                @Override public void onPrepared(DownloadHelper h){
                    if(preparations.get(id)!=h)return;
                    try{
                        DownloadTracks.requireSelectedQuality(h,quality);DownloadRequest request=h.getDownloadRequest(id,meta.toString().getBytes(StandardCharsets.UTF_8));Download current=get(id);
                        if(!replacementId.isEmpty()&&(exists==null||current==null||current.updateTimeMs!=exists.updateTimeMs||current.state==Download.STATE_REMOVING))return;
                        if(current!=null&&(current.state==Download.STATE_COMPLETED||current.state==Download.STATE_DOWNLOADING||current.state==Download.STATE_QUEUED||current.state==Download.STATE_RESTARTING))return;
                        if(current!=null&&(current.state==Download.STATE_FAILED||current.state==Download.STATE_REMOVING)){replacement.put(id,request);DownloadService.sendRemoveDownload(context,YoruDownloadService.class,id,true);}
                        else DownloadService.sendAddDownload(context,YoruDownloadService.class,request,true);
                        DownloadService.sendResumeDownloads(context,YoruDownloadService.class,true);
                        if(!source.metadataOnly())SourceEngine.record(source.source,true,0,1,1,quality);
                        Ui.toast(context,YoruApp.app().store.wifiDownloads()&&YoruApp.app().traffic.metered()?"Добавлено. Ожидаем Wi-Fi.":"Серия добавлена в загрузки");
                    }catch(Exception error){replacement.remove(id);Ui.toast(context,"Не удалось начать загрузку. Попробуйте другой вариант.");}
                    finally{finishPreparation(id,h);}
                }
                @Override public void onPrepareError(DownloadHelper h,IOException error){
                    if(preparations.get(id)!=h)return;finishPreparation(id,h);
                    if(!source.metadataOnly())SourceEngine.record(source.source,false,0,0,0,quality);
                    Ui.toast(context,"Серию не удалось подготовить для офлайн. Попробуйте другой вариант.");
                }
            });
        }catch(Exception error){DownloadHelper helper=preparations.get(id);if(helper!=null)finishPreparation(id,helper);else preparing.remove(id);Ui.toast(context,"Эта загрузка сейчас недоступна.");}
    }
    private void finishPreparation(String id,DownloadHelper helper){
        if(preparations.get(id)!=helper)return;preparations.remove(id);Runnable timeout=preparationTimeouts.remove(id);if(timeout!=null)main.removeCallbacks(timeout);preparing.remove(id);helper.release();
    }
    private void cancelPreparation(String id){
        TaskQueue.Signal retry=retryTasks.remove(id);if(retry!=null)retry.set(true);
        DownloadHelper helper=preparations.get(id);if(helper!=null)finishPreparation(id,helper);replacement.remove(id);
    }
    public static MediaItem item(String uri){MediaItem.Builder b=new MediaItem.Builder().setUri(uri);String path=Uri.parse(uri).getPath();String p=path==null?"":path.toLowerCase(Locale.ROOT);String lower=uri.toLowerCase(Locale.ROOT);if(p.endsWith(".m3u8")||lower.contains(".m3u8")||lower.contains("vid.php"))b.setMimeType(MimeTypes.APPLICATION_M3U8);else if(p.endsWith(".mpd")||lower.contains(".mpd"))b.setMimeType(MimeTypes.APPLICATION_MPD);else if(p.endsWith(".mp4")||lower.contains(".mp4"))b.setMimeType(MimeTypes.VIDEO_MP4);return b.build();}
    static MediaItem item(String uri,String mime){MediaItem item=item(uri);return MediaSize.mediaMime(mime)?item.buildUpon().setMimeType(MediaSize.canonicalMime(mime)).build():item;}
    public MediaItem offlineItem(Download download){MediaItem.Builder item=item(download.request.uri.toString()).buildUpon().setStreamKeys(download.request.streamKeys).setCustomCacheKey(download.request.customCacheKey);if(download.request.mimeType!=null)item.setMimeType(download.request.mimeType);return item.build();}
    public void pause(String id){if(Looper.myLooper()!=Looper.getMainLooper()){main.post(()->pause(id));return;}cancelPreparation(id);DownloadService.sendSetStopReason(context,YoruDownloadService.class,id,USER_PAUSED,true);}
    public void resume(String id){DownloadService.sendSetStopReason(context,YoruDownloadService.class,id,Download.STOP_REASON_NONE,true);DownloadService.sendResumeDownloads(context,YoruDownloadService.class,true);}
    public void remove(String id){if(Looper.myLooper()!=Looper.getMainLooper()){main.post(()->remove(id));return;}cancelPreparation(id);DownloadService.sendRemoveDownload(context,YoruDownloadService.class,id,true);}
    public void retry(String id){
        Download previous=get(id);if(previous==null||(previous.state!=Download.STATE_FAILED&&previous.state!=Download.STATE_STOPPED))return;
        TaskQueue.Signal operation=new TaskQueue.Signal();if(preparing.contains(id)||retryTasks.putIfAbsent(id,operation)!=null){Ui.toast(context,"Эта загрузка уже подготавливается");return;}
        JSONObject meta=metadata(previous);Anime catalog=Anime.from(meta.optJSONObject("anime"));double number=meta.optDouble("episode",1);int quality=meta.optInt("quality",720);String voice=meta.optString("voice","");
        Ui.toast(context,"Обновляем варианты загрузки…");
        TaskQueue.run(YoruApp.app().io,operation,()->{
            try{
                YoruApp.app().api.clearMemory();List<ApiRepository.DownloadOption> options=YoruApp.app().api.downloadOptions(catalog,null,number,voice,quality,!voice.isEmpty());
                ApiRepository.DownloadOption selected=null;
                for(ApiRepository.DownloadOption option:options){
                    if(option==null||option.episode==null||option.episode.future)continue;
                    boolean sameVoice=voice.isEmpty()||ApiRepository.voiceMatches(voice,option.voice+" "+option.episode.name);
                    if(Math.abs(option.episode.number-number)<.001&&!EpisodeRules.conflicts(catalog,option.source)&&option.quality==quality&&sameVoice){selected=option;break;}
                }
                if(retryTasks.get(id)!=operation)return;if(selected==null)throw new IOException();TaskQueue.check();
                ApiRepository.DownloadOption ready=selected;MediaSize.Info info=MediaSize.probeInfo(ready.episode.streams.get(ready.quality));TaskQueue.check();
                main.post(()->{
                    try{
                        Download current=get(id);
                        if(retryTasks.get(id)!=operation||current==null||current.updateTimeMs!=previous.updateTimeMs||(current.state!=Download.STATE_FAILED&&current.state!=Download.STATE_STOPPED))return;
                        enqueue(catalog,ready.source,ready.episode,ready.quality,ready.voice,info.mime,id);
                    }finally{retryTasks.remove(id,operation);}
                });
            }catch(Exception error){main.post(()->{boolean pending=retryTasks.remove(id,operation);Download current=get(id);if(pending&&current!=null&&current.updateTimeMs==previous.updateTimeMs)Ui.toast(context,"Выбранная серия, озвучка или разрешение пока недоступны. Загрузка сохранена без подмены варианта.");});}
        },()->{retryTasks.remove(id,operation);Ui.toast(context,"Очередь занята. Повторите загрузку позже.");});
    }

    public void pauseAll(){if(Looper.myLooper()!=Looper.getMainLooper()){main.post(this::pauseAll);return;}replacement.clear();for(String id:new ArrayList<>(retryTasks.keySet()))cancelPreparation(id);for(String id:new ArrayList<>(preparations.keySet()))cancelPreparation(id);for(Download d:all())if(d!=null&&d.request!=null&&d.state!=Download.STATE_COMPLETED&&d.state!=Download.STATE_REMOVING)pause(d.request.id);}
    public void resumeAll(){try{DownloadService.sendSetStopReason(context,YoruDownloadService.class,null,Download.STOP_REASON_NONE,true);DownloadService.sendResumeDownloads(context,YoruDownloadService.class,true);}catch(Exception ignored){}}
    public void retryFailed(){for(Download d:all())if(d!=null&&d.request!=null&&d.state==Download.STATE_FAILED)retry(d.request.id);}
    public void removeCompleted(){for(Download d:all())if(d!=null&&d.request!=null&&d.state==Download.STATE_COMPLETED)remove(d.request.id);}
    public Download neighbour(Download current,int direction){
        if(current==null||direction==0)return null;
        JSONObject own=metadata(current);Anime anime=Anime.from(own.optJSONObject("anime"));double number=own.optDouble("episode",Double.NaN);if(!Anime.valid(anime)||!Double.isFinite(number))return null;
        String voice=own.optString("voice",""),voiceKey=ApiRepository.voiceKey(voice);Anime source=Anime.from(own.optJSONObject("source"));int preferred=own.optInt("quality",0);Download best=null;double nearest=direction>0?Double.POSITIVE_INFINITY:Double.NEGATIVE_INFINITY;
        for(Download candidate:all()){
            if(candidate.state!=Download.STATE_COMPLETED)continue;JSONObject meta=metadata(candidate);Anime other=Anime.from(meta.optJSONObject("anime"));double episode=meta.optDouble("episode",Double.NaN);
            if(!Anime.valid(other)||!(anime.key().equals(other.key())||(anime.malId>0&&anime.malId==other.malId))||EpisodeRules.conflicts(anime,other)||!Double.isFinite(episode)||(direction>0?episode<=number:episode>=number))continue;
            String actual=meta.optString("voice","");
            if(!voiceKey.isEmpty()){if(!voiceKey.equals(ApiRepository.voiceKey(actual)))continue;}
            else{Anime candidateSource=Anime.from(meta.optJSONObject("source"));if(!voice.equals(actual)||!Anime.valid(source)||!Anime.valid(candidateSource)||!source.key().equals(candidateSource.key()))continue;}
            if(best==null||(direction>0?episode<nearest:episode>nearest)){best=candidate;nearest=episode;}
            else if(Double.compare(episode,nearest)==0){int quality=meta.optInt("quality",0),previous=metadata(best).optInt("quality",0);if(quality==preferred||(previous!=preferred&&quality>0&&quality<=preferred&&(previous>preferred||previous<=0||quality>previous)))best=candidate;}
        }
        return best;
    }
    public void refreshIndexAsync(){if(!indexing.compareAndSet(false,true))return;YoruApp.app().ui.execute(()->{try{rebuildIndex();}finally{indexing.set(false);}});}
    private void rebuildIndex(){ConcurrentHashMap<String,Download> next=new ConcurrentHashMap<>();JSONArray mirror=new JSONArray();for(Download d:all()){if(d==null||d.request==null||d.state!=Download.STATE_COMPLETED)continue;JSONObject m=metadata(d);Anime parent=Anime.from(m.optJSONObject("anime")),source=Anime.from(m.optJSONObject("source"));double ep=m.optDouble("episode",-1);putIndex(next,parent,ep,d,m);putIndex(next,source,ep,d,m);try{mirror.put(new JSONObject().put("id",d.request.id).put("anime",parent.json()).put("source",source.json()).put("episode",ep).put("quality",m.optInt("quality",0)).put("updated",System.currentTimeMillis()));}catch(Exception ignored){}}completedIndex.clear();completedIndex.putAll(next);indexAt=System.currentTimeMillis();try{YoruApp.app().cache.offline(mirror.toString());}catch(Exception ignored){}}
    private static void putIndex(ConcurrentHashMap<String,Download> map,Anime a,double ep,Download d,JSONObject meta){if(!Anime.valid(a)||ep<0)return;String e=episodeKey(ep);ArrayList<String> keys=new ArrayList<>();keys.add(a.key());keys.add(SourceEngine.identity(a));if(a.malId>0)keys.add("mal:"+a.malId);if(a.anilistId>0)keys.add("ani:"+a.anilistId);if(a.kpId>0)keys.add("kp:"+a.kpId);for(String k:keys){if(k==null||k.isEmpty())continue;String id=k+"|"+e;Download old=map.get(id);if(old==null||metadata(old).optInt("quality")<meta.optInt("quality"))map.put(id,d);}}
    private ArrayList<String> lookupKeys(Anime a,double episode){ArrayList<String> keys=new ArrayList<>();if(!Anime.valid(a))return keys;String e=episodeKey(episode);keys.add(a.key()+"|"+e);keys.add(SourceEngine.identity(a)+"|"+e);if(a.malId>0)keys.add("mal:"+a.malId+"|"+e);if(a.anilistId>0)keys.add("ani:"+a.anilistId+"|"+e);if(a.kpId>0)keys.add("kp:"+a.kpId+"|"+e);return keys;}
    public HashMap<String,Download> completedEpisodes(Anime a){HashMap<String,Download> out=new HashMap<>();if(!Anime.valid(a))return out;if(indexAt==0||System.currentTimeMillis()-indexAt>90_000)refreshIndexAsync();String[] prefixes={a.key()+"|",SourceEngine.identity(a)+"|",a.malId>0?"mal:"+a.malId+"|":"",a.anilistId>0?"ani:"+a.anilistId+"|":"",a.kpId>0?"kp:"+a.kpId+"|":""};for(Map.Entry<String,Download> e:completedIndex.entrySet()){for(String p:prefixes)if(!p.isEmpty()&&e.getKey().startsWith(p)){String ep=e.getKey().substring(p.length());Download old=out.get(ep);if(old==null||metadata(old).optInt("quality")<metadata(e.getValue()).optInt("quality"))out.put(ep,e.getValue());break;}}return out;}
    public static String episodeKey(double episode){return Math.abs(episode-Math.floor(episode))<0.001?String.valueOf((int)Math.floor(episode)):String.valueOf(episode);}
    public Download findCompleted(Anime a,double episode){if(indexAt==0||System.currentTimeMillis()-indexAt>90_000)refreshIndexAsync();for(String key:lookupKeys(a,episode)){Download d=completedIndex.get(key);if(d!=null)return d;}if(Looper.myLooper()==Looper.getMainLooper())return null;HashMap<String,Download> rows=completedEpisodes(a);return rows.get(episodeKey(episode));}
}
