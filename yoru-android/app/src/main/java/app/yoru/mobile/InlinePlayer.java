package app.yoru.mobile;

import android.app.Activity;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import org.json.JSONObject;
import java.util.*;

final class InlinePlayer extends LinearLayout {
    private final Activity activity;
    private final PlayerView video;
    private final TextView status,start,episodeButton,voiceButton,qualityButton,seasonButton;
    private final TaskQueue.Scope tasks=new TaskQueue.Scope();
    private final TreeMap<Integer,String> streams=new TreeMap<>();
    private final ArrayList<Anime> seasons=new ArrayList<>();
    private Anime anime;
    private java.util.function.Consumer<Anime> seasonListener;
    private Anime.Playback playback;
    private Anime.Episode episode;
    private ExoPlayer player;
    private String voice="",sizeLabel="Размер неизвестен";
    private int generation,quality,requestedQuality,actualHeight;private long resumePosition;private boolean playRequested;
    private boolean busy,closed,locked,foreground,restorePending;
    private double restoredEpisode=Double.NaN;
    private long initialPosition=-1,progressAtPause=Long.MIN_VALUE;
    private TextView lockButton;
    private final Runnable checkpoint=new Runnable(){public void run(){if(player!=null){save();postDelayed(this,5000);}}};

    InlinePlayer(Activity activity){
        super(activity);this.activity=activity;requestedQuality=YoruApp.app().store.quality();setOrientation(VERTICAL);setPadding(Ui.dp(activity,12),Ui.dp(activity,12),Ui.dp(activity,12),Ui.dp(activity,12));setBackground(Ui.stroke(Ui.CARD,16,activity));
        addView(Ui.text(activity,"YORU Player",17,Ui.TEXT,true));Ui.space(this,10);
        video=new PlayerView(activity);video.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);video.setKeepContentOnPlayerReset(true);addView(video,Ui.lp(activity,-1,220));
        status=Ui.text(activity,"Выберите серию и начните просмотр",11,Ui.MUTED,false);Ui.space(this,10);addView(status);
        start=Ui.button(activity,"Смотреть",true,()->{if(player!=null){playRequested=!player.getPlayWhenReady();player.setPlayWhenReady(playRequested);if(playRequested&&player.getPlaybackState()==Player.STATE_IDLE)player.prepare();}else if(!streams.isEmpty()){playRequested=true;play(streams.get(quality),resumePosition);}else prepare();});Ui.space(this,10);addView(start,Ui.lp(activity,-1,-2));
        seasonButton=Ui.button(activity,"Сезон / часть",false,this::chooseSeason);addView(seasonButton,Ui.lp(activity,-1,-2));
        episodeButton=Ui.button(activity,"Список серий",false,this::chooseEpisode);addView(episodeButton,Ui.lp(activity,-1,-2));
        voiceButton=Ui.button(activity,"Озвучка",false,this::chooseVoice);addView(voiceButton,Ui.lp(activity,-1,-2));
        qualityButton=Ui.button(activity,"Разрешение",false,this::chooseQuality);addView(qualityButton,Ui.lp(activity,-1,-2));
        LinearLayout tools=Ui.row(activity);
        tools.addView(Ui.iconButton(activity,"camera","Снимок видеокадра",()->{if(!locked)NativeFrame.capture(activity,video);}),Ui.lp(activity,48,48));
        tools.addView(Ui.iconButton(activity,"expand","На весь экран",()->{if(anime==null||locked)return;double number=episode==null?(restorePending?restoredEpisode:YoruApp.app().store.progress(anime).optDouble("episode",1)):episode.number;restorePending=false;suspend();activity.startActivity(new android.content.Intent(activity,PlayerActivity.class).putExtra("anime",anime.json().toString()).putExtra("mode","yoru").putExtra("episode",number).putExtra("voice",YoruApp.app().store.voicePreference(anime).isEmpty()?voice:YoruApp.app().store.voicePreference(anime)).putExtra("quality",requestedQuality).putExtra("position",resumePosition).putExtra("playRequested",playRequested).putExtra("fullscreen",true));}),Ui.lp(activity,48,48));
        TextView lock=Ui.button(activity,"Блокировка",false,()->{});lockButton=lock;lock.setOnClickListener(v->{locked=!locked;video.setUseController(!locked);start.setEnabled(!locked&&!busy);seasonButton.setEnabled(!locked&&!busy);episodeButton.setEnabled(!locked&&!busy);voiceButton.setEnabled(!locked&&!busy);qualityButton.setEnabled(!locked&&!busy);lock.setText(locked?"Разблокировать":"Блокировка");});tools.addView(lock,new LayoutParams(0,-2,1));addView(tools);
    }
    android.os.Bundle saveState(){
        android.os.Bundle state=new android.os.Bundle();
        if(anime!=null)state.putString("anime",anime.key());
        double number=episode==null?restoredEpisode:episode.number;
        if(Double.isFinite(number)&&number>=0)state.putDouble("episode",number);
        state.putLong("position",player==null?resumePosition:Math.max(0,player.getCurrentPosition()));
        state.putBoolean("play",player==null?playRequested:player.getPlayWhenReady());
        state.putBoolean("locked",locked);state.putInt("quality",requestedQuality);
        return state;
    }
    void restoreState(android.os.Bundle state){
        if(state==null||anime==null||!anime.key().equals(state.getString("anime","")))return;
        int restoredQuality=state.getInt("quality",requestedQuality);
        if(restoredQuality>=0&&restoredQuality<=4320)requestedQuality=restoredQuality;
        restoredEpisode=state.getDouble("episode",Double.NaN);
        restorePending=Double.isFinite(restoredEpisode)&&restoredEpisode>=0;
        resumePosition=Math.max(0,state.getLong("position",0));playRequested=state.getBoolean("play",false);
        locked=state.getBoolean("locked",false);video.setUseController(!locked);
        lockButton.setText(locked?"Разблокировать":"Блокировка");
        if(restorePending)episodeButton.setText("Серия "+Ui.number(restoredEpisode));
        state(false,restorePending?"Восстанавливаем выбранную серию…":"Выберите серию и начните просмотр");
    }
    void resumeLifecycle(){
        foreground=true;
        if(!restorePending&&player==null&&episode!=null&&anime!=null&&progressAtPause!=Long.MIN_VALUE){
            JSONObject history=YoruApp.app().store.progress(anime);double number=history.optDouble("episode",Double.NaN);
            if(history.optLong("updated",Long.MIN_VALUE)!=progressAtPause&&Double.isFinite(number)&&number>=0){
                restoredEpisode=number;resumePosition=Math.max(0,history.optLong("time",0))*1000L;
                episode=null;streams.clear();restorePending=true;playRequested=false;episodeButton.setText("Серия "+Ui.number(number));
            }
        }
        restoreIfReady();
    }
    private void restoreIfReady(){
        if(!restorePending||!foreground||busy||closed||playback==null||playback.video==null)return;
        for(Anime.Episode row:EpisodeRules.visible(anime,playback.video.episodeList)){
            if(!row.future&&Math.abs(row.number-restoredEpisode)<.001){load(row,playRequested,resumePosition);return;}
        }
        restorePending=false;state(false,"Сохранённая серия сейчас недоступна. Выберите другую серию.");
    }
    void setSeasonListener(java.util.function.Consumer<Anime> listener){seasonListener=listener;}
    void bind(Anime value){if(anime==null){anime=value;seasonButton.setText(value.title);}else if(anime.key().equals(value.key()))anime=value;}
    private boolean alive(int token){return !closed&&!activity.isFinishing()&&!activity.isDestroyed()&&generation==token;}
    private void state(boolean value,String text){busy=value;status.setText(text);start.setEnabled(!value&&!locked);episodeButton.setEnabled(!value&&!locked);voiceButton.setEnabled(!value&&!locked);qualityButton.setEnabled(!value&&!locked);seasonButton.setEnabled(!value&&!locked);}
    void offerEpisodes(Anime owner,Anime.Playback value){
        if(closed||busy||episode!=null||anime==null||!anime.key().equals(owner.key())||value==null||value.video==null)return;
        playback=value;if(!restorePending)episodeButton.setText("Список серий");restoreIfReady();
    }
    private void prepare(){if(restorePending)playRequested=true;prepare(false);}
    private void prepare(boolean selectEpisode){
        if(anime==null||busy||closed)return;if(anime.blocked){status.setText("Просмотр ограничен");return;}
        int token=++generation;tasks.cancel();state(true,"Загружаем серии…");Anime selected=Anime.from(anime.json());
        tasks.submit(YoruApp.app().ui,()->{try{Anime.Playback result=YoruApp.app().api.playback(selected,"yoru");TaskQueue.check();if(result==null||result.video==null)throw new java.io.IOException();post(()->{if(!alive(token))return;playback=result;JSONObject history=YoruApp.app().store.progress(anime);double number=restorePending?restoredEpisode:episode==null?history.optDouble("episode",1):episode.number;Anime.Episode found=null;for(Anime.Episode row:EpisodeRules.visible(anime,result.video.episodeList))if(!row.future&&Math.abs(row.number-number)<.001){found=row;break;}if(found==null&&!restorePending&&episode==null&&!history.has("episode"))for(Anime.Episode row:EpisodeRules.visible(anime,result.video.episodeList))if(!row.future){found=row;break;}state(false,"Серии загружены");if(selectEpisode){chooseEpisode();return;}if(restorePending){restoreIfReady();return;}if(found!=null)load(found);else status.setText("Доступных серий пока нет");});}catch(Exception e){post(()->{if(alive(token))state(false,"Не удалось получить серии. Нажмите «Смотреть», чтобы повторить.");});}},()->state(false,"Очередь занята. Повторите позже."));
    }
    private void load(Anime.Episode value){load(value,true,-1);}
    private void load(Anime.Episode value,boolean autoplay,long position){
        if(value==null||value.future)return;restorePending=false;playRequested=autoplay;initialPosition=position;resumePosition=Math.max(0,position);release();tasks.cancel();int token=++generation;episode=value;voice="";quality=0;sizeLabel="Размер неизвестен";voiceButton.setText("Озвучка");qualityButton.setText("Разрешение");streams.clear();state(true,"Готовим серию "+Ui.number(value.number)+"…");episodeButton.setText("Серия "+Ui.number(value.number));
        tasks.submit(YoruApp.app().ui,()->{try{YoruApp.app().api.loadEpisode(value);TaskQueue.check();post(()->{if(!alive(token))return;state(false,"Выберите озвучку");ArrayList<Anime.Variant> variants=variants();SecureStore store=YoruApp.app().store;boolean preferred=store.onlyPreferredVoice()||!store.voicePreference(anime).isEmpty()||!store.favoriteVoices().isEmpty();boolean matches=!store.voicePreference(anime).isEmpty()&&ApiRepository.voiceMatches(store.voicePreference(anime),ApiRepository.sourceVoice(playback.video.source));if(!value.streams.isEmpty()&&(matches||((!preferred||variants.isEmpty())&&!store.onlyPreferredVoice()))){voice=ApiRepository.sourceVoice(playback.video.source);accept(value.streams);}else if(!variants.isEmpty())resolve(variants.get(0));else status.setText("Выбранная озвучка недоступна для этой серии");});}catch(Exception e){post(()->{if(alive(token))state(false,"Серия недоступна. Выберите другую серию.");});}},()->state(false,"Очередь занята. Повторите позже."));
    }
    private ArrayList<Anime.Variant> variants(){ArrayList<Anime.Variant> rows=new ArrayList<>();if(episode==null)return rows;SecureStore store=YoruApp.app().store;for(Anime.Variant variant:SourceEngine.sortVariants(episode.variants)){String label=variant.name+" "+variant.displayName+" "+variant.player;if(!store.onlyPreferredVoice()||ApiRepository.voiceMatches(store.voicePreference(anime),label))rows.add(variant);}rows.sort((a,b)->Integer.compare(priority(a),priority(b)));return rows;}
    private int priority(Anime.Variant variant){SecureStore store=YoruApp.app().store;String label=variant.name+" "+variant.displayName+" "+variant.player;if(!store.voicePreference(anime).isEmpty()&&ApiRepository.voiceMatches(store.voicePreference(anime),label))return -2;int rank=store.voicePriority(label);return rank<0?Integer.MAX_VALUE:rank;}
    private void resolve(Anime.Variant variant){
        if(player!=null){initialPosition=Math.max(0,player.getCurrentPosition());resumePosition=initialPosition;playRequested=player.getPlayWhenReady();}
        release();streams.clear();voice="";quality=0;sizeLabel="Размер неизвестен";voiceButton.setText("Озвучка");qualityButton.setText("Разрешение");tasks.cancel();int token=++generation;state(true,"Открываем "+variant.label()+"…");
        tasks.submit(YoruApp.app().ui,()->{try{TreeMap<Integer,String> result=YoruApp.app().api.resolveStreams(variant.url);TaskQueue.check();post(()->{if(!alive(token))return;voice=variant.label();accept(result);});}catch(Exception e){post(()->{if(alive(token))state(false,"Эта озвучка недоступна. Выберите другую.");});}},()->state(false,"Очередь занята. Повторите позже."));
    }
    private void accept(Map<Integer,String> values){streams.clear();for(Map.Entry<Integer,String> row:values.entrySet())if(!ApiRepository.safeUrl(row.getValue()).isEmpty())streams.put(row.getKey(),row.getValue());if(streams.isEmpty()){state(false,"Нет доступного видео для собственного плеера");return;}quality=QualityPlus.bestAtOrBelow(streams.keySet(),requestedQuality);voiceButton.setText(voice.isEmpty()?"Озвучка источника":voice);long position=initialPosition;initialPosition=-1;play(streams.get(quality),position);}
    private void play(String url,long position){
        if(url==null||url.isEmpty())return;release();tasks.cancel();int token=++generation;state(true,"Подготавливаем видео…");
        tasks.submit(YoruApp.app().ui,()->{try{MediaSize.Info info=MediaSize.probeInfo(url);TaskQueue.check();post(()->{if(!alive(token))return;sizeLabel=MediaSize.label(info.bytes);startVideo(url,position,info.mime);});}catch(Exception error){post(()->{if(alive(token))state(false,"Не удалось подготовить видео. Повторите позже.");});}},()->state(false,"Очередь занята. Повторите позже."));
    }
    private void startVideo(String url,long position,String mime){
        if(url==null||url.isEmpty())return;release();player=NativePlayers.create(activity);YoruApp.app().activePlayers++;video.setPlayer(player);actualHeight=0;ExoPlayer active=player;
        NativePlayers.limitQuality(player,quality);
        player.addListener(new Player.Listener(){@Override public void onVideoSizeChanged(VideoSize size){if(player!=active||closed)return;actualHeight=Math.max(0,size.height);if(episode!=null)status.setText(streamStatus());}@Override public void onIsPlayingChanged(boolean playing){if(player!=active||closed)return;start.setText(playing?"Пауза":"Продолжить");}@Override public void onPlayerError(PlaybackException error){if(player!=active||closed)return;state(false,"Видео не открылось. Выберите другую озвучку или разрешение.");}@Override public void onPlaybackStateChanged(int state){if(player!=active||closed)return;if(state==Player.STATE_READY&&!player.getCurrentTracks().isTypeSelected(C.TRACK_TYPE_VIDEO)){player.pause();state(false,"Для выбранного разрешения нет доступного видео. Выберите другой вариант.");return;}if(state==Player.STATE_ENDED&&YoruApp.app().autoNextAllowed()&&playback!=null){Anime.Episode next=null;for(Anime.Episode row:EpisodeRules.visible(anime,playback.video.episodeList))if(!row.future&&episode!=null&&row.number>episode.number&&(next==null||row.number<next.number))next=row;if(next!=null)load(next);}}});
        player.setMediaSource(NativePlayers.source(url,mime));
        JSONObject history=YoruApp.app().store.progress(anime);long seek=position>=0?position:Math.abs(history.optDouble("episode",-1)-episode.number)<.001?history.optLong("time",0)*1000L:0;player.seekTo(Math.max(0,seek));player.setPlaybackSpeed(YoruApp.app().store.playbackSpeed());player.setPlayWhenReady(playRequested);player.prepare();qualityButton.setText("Разрешение · "+QualityPlus.streamLabel(quality));state(false,streamStatus());removeCallbacks(checkpoint);postDelayed(checkpoint,5000);
    }
    private String streamStatus(){String resolution=actualHeight>0?QualityPlus.streamLabel(actualHeight):quality>0?"до "+QualityPlus.streamLabel(quality):"Разрешение уточняется";return "Серия "+Ui.number(episode.number)+" · "+resolution+" · "+sizeLabel;}
    private void chooseEpisode(){restorePending=false;if(playback==null){prepare(true);return;}ArrayList<Anime.Episode> rows=new ArrayList<>();for(Anime.Episode row:EpisodeRules.visible(anime,playback.video.episodeList))if(!row.future)rows.add(row);if(rows.isEmpty()){Ui.toast(activity,"Доступных серий пока нет");return;}String[] labels=new String[rows.size()];for(int i=0;i<labels.length;i++)labels[i]=rows.get(i).label();Ui.choices(activity,"Список серий",labels,index->load(rows.get(index)));}
    private void chooseVoice(){ArrayList<Anime.Variant> rows=episode==null?new ArrayList<>():new ArrayList<>(SourceEngine.sortVariants(episode.variants));if(rows.isEmpty()){Ui.toast(activity,"Сначала откройте доступную серию");return;}String[] labels=new String[rows.size()];for(int i=0;i<labels.length;i++)labels[i]=rows.get(i).label();Ui.choices(activity,"Озвучка",labels,index->{Anime.Variant selected=rows.get(index);String name=ApiRepository.voiceTitle(selected.name.isEmpty()?selected.label():selected.name);if(!name.isEmpty()){YoruApp.app().store.voicePreference(anime,name);YoruApp.app().store.rememberVoice(name);}resolve(selected);});}
    private void chooseQuality(){ArrayList<Integer> rows=new ArrayList<>(streams.keySet());if(rows.isEmpty()){Ui.toast(activity,"Сначала выберите доступную серию и озвучку");return;}String[] labels=new String[rows.size()];for(int i=0;i<labels.length;i++)labels[i]=QualityPlus.streamLabel(rows.get(i));Ui.choices(activity,"Разрешение",labels,index->{long position=player==null?-1:player.getCurrentPosition();playRequested=player!=null&&player.getPlayWhenReady();quality=rows.get(index);requestedQuality=quality;play(streams.get(quality),position);});}
    private void chooseSeason(){if(anime==null||busy)return;int token=generation;state(true,"Загружаем части истории…");Anime selected=Anime.from(anime.json());tasks.submit(YoruApp.app().ui,()->{try{List<Anime> rows=YoruApp.app().api.franchise(selected);post(()->{if(!alive(token))return;state(false,"Выберите сезон или часть истории");seasons.clear();seasons.addAll(rows);String[] labels=new String[seasons.size()];for(int i=0;i<labels.length;i++){Anime row=seasons.get(i);labels[i]=row.title+(row.year>0?" · "+row.year:"");}Ui.choices(activity,"Сезон / часть",labels,index->{suspend();anime=seasons.get(index);playback=null;episode=null;streams.clear();voice="";quality=0;sizeLabel="Размер неизвестен";voiceButton.setText("Озвучка");qualityButton.setText("Разрешение");seasonButton.setText(anime.title);episodeButton.setText("Список серий");resumePosition=0;initialPosition=-1;restoredEpisode=Double.NaN;restorePending=false;playRequested=false;if(seasonListener!=null)seasonListener.accept(anime);else prepare(true);});});}catch(Exception e){post(()->{if(alive(token))state(false,"Список частей сейчас недоступен");});}},()->state(false,"Очередь занята. Повторите позже."));}
    private void save(){if(player==null||anime==null||episode==null||!player.getCurrentTracks().isTypeSelected(C.TRACK_TYPE_VIDEO)||(player.getPlaybackState()!=Player.STATE_READY&&player.getPlaybackState()!=Player.STATE_ENDED))return;long duration=player.getDuration();YoruApp.app().store.progress(anime,episode.number,(int)(player.getCurrentPosition()/1000),duration==C.TIME_UNSET?0:(int)Math.max(0,duration/1000),"yoru",voice,true);}
    private void release(){removeCallbacks(checkpoint);if(player!=null){save();video.setPlayer(null);player.release();player=null;YoruApp.app().activePlayers=Math.max(0,YoruApp.app().activePlayers-1);}}
    void suspend(){foreground=false;generation++;tasks.cancel();if(player!=null){resumePosition=Math.max(0,player.getCurrentPosition());playRequested=player.getPlayWhenReady();}release();if(anime!=null)progressAtPause=YoruApp.app().store.progress(anime).optLong("updated",Long.MIN_VALUE);busy=false;if(!closed)state(false,"Просмотр приостановлен");}
    void close(){suspend();closed=true;}
}
