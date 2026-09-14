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
    private Anime.Playback playback;
    private Anime.Episode episode;
    private ExoPlayer player;
    private String voice="";
    private int generation,quality,requestedQuality;
    private boolean busy,closed,locked;
    private final Runnable checkpoint=new Runnable(){public void run(){if(player!=null){save();postDelayed(this,5000);}}};

    InlinePlayer(Activity activity){
        super(activity);this.activity=activity;requestedQuality=YoruApp.app().store.quality();setOrientation(VERTICAL);setPadding(Ui.dp(activity,12),Ui.dp(activity,12),Ui.dp(activity,12),Ui.dp(activity,12));setBackground(Ui.stroke(Ui.CARD,16,activity));
        addView(Ui.text(activity,"YORU Player",17,Ui.TEXT,true));Ui.space(this,10);
        video=new PlayerView(activity);video.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);video.setKeepContentOnPlayerReset(true);addView(video,Ui.lp(activity,-1,220));
        status=Ui.text(activity,"Выберите серию и начните просмотр",11,Ui.MUTED,false);Ui.space(this,10);addView(status);
        start=Ui.button(activity,"Смотреть",true,()->{if(player!=null){if(player.isPlaying())player.pause();else{if(player.getPlaybackState()==Player.STATE_IDLE)player.prepare();player.play();}}else prepare();});Ui.space(this,10);addView(start,Ui.lp(activity,-1,-2));
        seasonButton=Ui.button(activity,"Сезон / часть",false,this::chooseSeason);addView(seasonButton,Ui.lp(activity,-1,-2));
        episodeButton=Ui.button(activity,"Серия",false,this::chooseEpisode);addView(episodeButton,Ui.lp(activity,-1,-2));
        voiceButton=Ui.button(activity,"Озвучка",false,this::chooseVoice);addView(voiceButton,Ui.lp(activity,-1,-2));
        qualityButton=Ui.button(activity,"Разрешение",false,this::chooseQuality);addView(qualityButton,Ui.lp(activity,-1,-2));
        LinearLayout tools=Ui.row(activity);
        tools.addView(Ui.iconButton(activity,"camera","Снимок видеокадра",()->{if(!locked)NativeFrame.capture(activity,video);}),Ui.lp(activity,48,48));
        tools.addView(Ui.iconButton(activity,"expand","На весь экран",()->{if(anime==null||locked)return;double number=episode==null?1:episode.number;suspend();activity.startActivity(new android.content.Intent(activity,PlayerActivity.class).putExtra("anime",anime.json().toString()).putExtra("mode","yoru").putExtra("episode",number).putExtra("voice",YoruApp.app().store.voicePreference().isEmpty()?voice:YoruApp.app().store.voicePreference()).putExtra("quality",quality>0?quality:YoruApp.app().store.quality()).putExtra("fullscreen",true));}),Ui.lp(activity,48,48));
        TextView lock=Ui.button(activity,"Блокировка",false,()->{});lock.setOnClickListener(v->{locked=!locked;video.setUseController(!locked);start.setEnabled(!locked&&!busy);seasonButton.setEnabled(!locked&&!busy);episodeButton.setEnabled(!locked&&!busy);voiceButton.setEnabled(!locked&&!busy);qualityButton.setEnabled(!locked&&!busy);lock.setText(locked?"Разблокировать":"Блокировка");});tools.addView(lock,new LayoutParams(0,-2,1));addView(tools);
    }
    void bind(Anime value){if(anime==null){anime=value;seasonButton.setText(value.title);}else if(anime.key().equals(value.key()))anime=value;}
    private boolean alive(int token){return !closed&&!activity.isFinishing()&&generation==token;}
    private void state(boolean value,String text){busy=value;status.setText(text);start.setEnabled(!value&&!locked);episodeButton.setEnabled(!value&&!locked);voiceButton.setEnabled(!value&&!locked);qualityButton.setEnabled(!value&&!locked);seasonButton.setEnabled(!value&&!locked);}
    private void prepare(){
        if(anime==null||busy||closed)return;if(anime.blocked){status.setText("Просмотр ограничен");return;}
        int token=++generation;tasks.cancel();state(true,"Загружаем серии…");Anime selected=Anime.from(anime.json());
        tasks.submit(YoruApp.app().ui,()->{try{Anime.Playback result=YoruApp.app().api.playback(selected,"yoru");TaskQueue.check();post(()->{if(!alive(token))return;playback=result;JSONObject history=YoruApp.app().store.progress(anime);double number=history.optDouble("episode",1);Anime.Episode found=null;for(Anime.Episode row:EpisodeRules.visible(anime,result.video.episodeList))if(!row.future&&(found==null||Math.abs(row.number-number)<.001)){found=row;if(Math.abs(row.number-number)<.001)break;}state(false,"Серии загружены");if(found!=null)load(found);else status.setText("Доступных серий пока нет");});}catch(Exception e){post(()->{if(alive(token))state(false,"Не удалось получить серии. Нажмите «Смотреть», чтобы повторить.");});}},()->state(false,"Очередь занята. Повторите позже."));
    }
    private void load(Anime.Episode value){
        if(value==null||value.future)return;release();tasks.cancel();int token=++generation;episode=value;voice="";streams.clear();state(true,"Готовим серию "+Ui.number(value.number)+"…");episodeButton.setText("Серия "+Ui.number(value.number));
        tasks.submit(YoruApp.app().ui,()->{try{YoruApp.app().api.loadEpisode(value);TaskQueue.check();post(()->{if(!alive(token))return;state(false,"Выберите озвучку");ArrayList<Anime.Variant> variants=variants();SecureStore store=YoruApp.app().store;boolean preferred=store.onlyPreferredVoice()||!store.voicePreference().isEmpty()||!store.favoriteVoices().isEmpty();boolean directMatches=!store.voicePreference().isEmpty()&&ApiRepository.voiceMatches(store.voicePreference(),ApiRepository.sourceVoice(playback.video.source));if(!value.streams.isEmpty()&&(directMatches||((!preferred||variants.isEmpty())&&!store.onlyPreferredVoice()))){voice=ApiRepository.sourceVoice(playback.video.source);accept(value.streams);}else if(!variants.isEmpty())resolve(variants.get(0));else status.setText("Выбранная озвучка недоступна для этой серии");});}catch(Exception e){post(()->{if(alive(token))state(false,"Серия недоступна. Выберите другую серию.");});}},()->state(false,"Очередь занята. Повторите позже."));
    }
    private ArrayList<Anime.Variant> variants(){ArrayList<Anime.Variant> rows=new ArrayList<>();if(episode==null)return rows;SecureStore store=YoruApp.app().store;for(Anime.Variant variant:SourceEngine.sortVariants(episode.variants)){String label=variant.name+" "+variant.displayName+" "+variant.player;if(!store.onlyPreferredVoice()||ApiRepository.voiceMatches(store.voicePreference(),label))rows.add(variant);}rows.sort((a,b)->Integer.compare(priority(a),priority(b)));return rows;}
    private int priority(Anime.Variant variant){SecureStore store=YoruApp.app().store;String label=variant.name+" "+variant.displayName+" "+variant.player;if(!store.voicePreference().isEmpty()&&ApiRepository.voiceMatches(store.voicePreference(),label))return -2;int rank=store.voicePriority(label);return rank<0?Integer.MAX_VALUE:rank;}
    private void resolve(Anime.Variant variant){
        release();tasks.cancel();int token=++generation;state(true,"Открываем "+variant.label()+"…");
        tasks.submit(YoruApp.app().ui,()->{try{TreeMap<Integer,String> result=YoruApp.app().api.resolveStreams(variant.url);TaskQueue.check();post(()->{if(!alive(token))return;voice=variant.label();accept(result);});}catch(Exception e){post(()->{if(alive(token))state(false,"Эта озвучка недоступна. Выберите другую.");});}},()->state(false,"Очередь занята. Повторите позже."));
    }
    private void accept(Map<Integer,String> values){streams.clear();for(Map.Entry<Integer,String> row:values.entrySet())if(!ApiRepository.safeUrl(row.getValue()).isEmpty())streams.put(row.getKey(),row.getValue());if(streams.isEmpty()){state(false,"Нет доступного видео для собственного плеера");return;}quality=QualityPlus.bestAtOrBelow(streams.keySet(),requestedQuality);voiceButton.setText(voice.isEmpty()?"Озвучка источника":voice);play(streams.get(quality),-1);}
    private void play(String url,long position){
        if(url==null||url.isEmpty()||closed)return;release();tasks.cancel();int token=++generation;state(true,"Готовим просмотр…");
        tasks.submit(YoruApp.app().ui,()->{long bytes=MediaSize.probe(url);post(()->{if(alive(token))startNative(url,position,bytes);});},()->state(false,"Не удалось подготовить просмотр. Попробуйте ещё раз."));
    }
    private void startNative(String url,long position,long bytes){
        if(url==null||url.isEmpty())return;release();player=NativePlayers.create(activity);YoruApp.app().activePlayers++;video.setPlayer(player);
        player.addListener(new Player.Listener(){@Override public void onIsPlayingChanged(boolean playing){start.setText(playing?"Пауза":"Продолжить");}@Override public void onPlayerError(PlaybackException error){state(false,"Видео не открылось. Выберите другую озвучку или разрешение.");}@Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_ENDED&&YoruApp.app().autoNextAllowed()&&playback!=null){Anime.Episode next=null;for(Anime.Episode row:EpisodeRules.visible(anime,playback.video.episodeList))if(!row.future&&episode!=null&&row.number>episode.number&&(next==null||row.number<next.number))next=row;if(next!=null)load(next);}}});
        player.setMediaSource(NativePlayers.source(url));
        JSONObject history=YoruApp.app().store.progress(anime);long seek=position>=0?position:Math.abs(history.optDouble("episode",-1)-episode.number)<.001?history.optLong("time",0)*1000L:0;player.seekTo(Math.max(0,seek));player.setPlaybackSpeed(YoruApp.app().store.playbackSpeed());qualityButton.setText("Разрешение · "+QualityPlus.streamLabel(quality));state(false,"Серия "+Ui.number(episode.number)+" · "+QualityPlus.streamLabel(quality)+" · "+MediaSize.label(bytes));player.prepare();player.play();removeCallbacks(checkpoint);postDelayed(checkpoint,5000);
    }
    private void chooseEpisode(){if(playback==null){prepare();return;}ArrayList<Anime.Episode> rows=new ArrayList<>();for(Anime.Episode row:EpisodeRules.visible(anime,playback.video.episodeList))if(!row.future)rows.add(row);String[] labels=new String[rows.size()];for(int i=0;i<labels.length;i++)labels[i]=rows.get(i).label();Ui.choices(activity,"Серия",labels,index->load(rows.get(index)));}
    private void chooseVoice(){ArrayList<Anime.Variant> rows=episode==null?new ArrayList<>():new ArrayList<>(SourceEngine.sortVariants(episode.variants));if(rows.isEmpty()){Ui.toast(activity,"Сначала откройте доступную серию");return;}String[] labels=new String[rows.size()];for(int i=0;i<labels.length;i++)labels[i]=rows.get(i).label();Ui.choices(activity,"Озвучка",labels,index->{Anime.Variant selected=rows.get(index);YoruApp.app().store.voicePreference(ApiRepository.voiceTitle(selected.name.isEmpty()?selected.label():selected.name));resolve(selected);});}
    private void chooseQuality(){ArrayList<Integer> rows=new ArrayList<>(streams.keySet());if(rows.isEmpty())return;String[] labels=new String[rows.size()];for(int i=0;i<labels.length;i++)labels[i]=QualityPlus.streamLabel(rows.get(i));Ui.choices(activity,"Разрешение",labels,index->{long position=player==null?-1:player.getCurrentPosition();quality=rows.get(index);requestedQuality=quality;play(streams.get(quality),position);});}
    private void chooseSeason(){if(anime==null||busy)return;int token=generation;state(true,"Загружаем части истории…");Anime selected=Anime.from(anime.json());tasks.submit(YoruApp.app().ui,()->{try{List<Anime> rows=YoruApp.app().api.franchise(selected);post(()->{if(!alive(token))return;state(false,"Выберите сезон или часть истории");seasons.clear();seasons.addAll(rows);String[] labels=new String[seasons.size()];for(int i=0;i<labels.length;i++){Anime row=seasons.get(i);labels[i]=row.title+(row.year>0?" · "+row.year:"");}Ui.choices(activity,"Сезон / часть",labels,index->{suspend();anime=seasons.get(index);playback=null;episode=null;streams.clear();seasonButton.setText(anime.title);episodeButton.setText("Серия");prepare();});});}catch(Exception e){post(()->{if(alive(token))state(false,"Список частей сейчас недоступен");});}},()->state(false,"Очередь занята. Повторите позже."));}
    private void save(){if(player==null||anime==null||episode==null||player.getPlaybackState()==Player.STATE_IDLE)return;long duration=player.getDuration();YoruApp.app().store.progress(anime,episode.number,(int)(player.getCurrentPosition()/1000),duration==C.TIME_UNSET?0:(int)Math.max(0,duration/1000),"yoru",voice,true);}
    private void release(){removeCallbacks(checkpoint);if(player!=null){save();video.setPlayer(null);player.release();player=null;YoruApp.app().activePlayers=Math.max(0,YoruApp.app().activePlayers-1);}}
    void suspend(){generation++;tasks.cancel();release();busy=false;if(!closed)state(false,"Просмотр приостановлен");}
    void close(){suspend();closed=true;}
}
