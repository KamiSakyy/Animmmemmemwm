package app.yoru.vk;

import android.app.Activity;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import java.util.*;
import java.util.concurrent.Future;

@androidx.media3.common.util.UnstableApi
public final class PlaybackActivity extends Activity {
    private VkApp app;
    private ExoPlayer player;
    private PlayerView view;
    private TextView status;
    private Spinner quality;
    private final LinkedHashMap<String,String> streams=new LinkedHashMap<>();
    private Future<?> task;
    private int generation;
    private long position;
    private boolean selecting,resume=true;
    private String key,title,currentUrl="";
    @Override public void onCreate(Bundle saved){super.onCreate(saved);app=(VkApp)getApplication();key=getIntent().getStringExtra("key");title=getIntent().getStringExtra("title");if(key==null||!key.matches("-?[0-9]+_[0-9]+(?:_[A-Za-z0-9_-]+)?")){finish();return;}if(saved!=null){position=saved.getLong("position",0);resume=saved.getBoolean("resume",false);}LinearLayout root=Ui.column(this);Ui.base(this,root);LinearLayout top=Ui.row(this);top.setPadding(Ui.dp(this,12),Ui.dp(this,8),Ui.dp(this,12),Ui.dp(this,8));top.addView(Ui.button(this,"‹",false,this::finish),new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));TextView name=Ui.text(this,title==null?"Видео ВК":title,16,Ui.TEXT,true);name.setPadding(Ui.dp(this,10),0,0,0);name.setMaxLines(3);top.addView(name,new LinearLayout.LayoutParams(0,-2,1));root.addView(top);view=new PlayerView(this);view.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);view.setBackgroundColor(0xff000000);view.setControllerShowTimeoutMs(4000);root.addView(view,new LinearLayout.LayoutParams(-1,0,1));LinearLayout below=Ui.column(this);below.setPadding(Ui.dp(this,16),Ui.dp(this,12),Ui.dp(this,16),Ui.dp(this,16));status=Ui.text(this,"Запрашиваем video.get…",13,Ui.MUTED,false);below.addView(status);Ui.gap(below,10);quality=new Spinner(this);below.addView(quality,new LinearLayout.LayoutParams(-1,Ui.dp(this,48)));quality.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int at,long id){if(!selecting&&at<streams.size())start(new ArrayList<>(streams.values()).get(at));}});Ui.gap(below,8);below.addView(Ui.button(this,"Обновить доступ к видео",false,this::load));Ui.gap(below,8);below.addView(Ui.text(this,"Только прямой поток, разрешённый VK API. Без iframe, обхода ограничений и передачи OAuth-токена видеосерверу.",11,Ui.MUTED,false));root.addView(below);}
    @Override protected void onStart(){super.onStart();load();}
    private void load(){if(task!=null)task.cancel(true);int gen=++generation;status.setText("Проверяем доступ и прямые потоки video.get…");task=app.run(()->Api.video(app.vault.read(),key),(video,error)->{if(gen!=generation||isFinishing())return;if(error!=null){status.setText(Ui.error(error));return;}streams.clear();streams.putAll(Models.streams(video.files));if(streams.isEmpty()){release();quality.setAdapter(null);status.setText("Видео найдено, но VK API не выдал прямой поток files. Оно может открываться в самом ВК, но этот токен не даёт встроенный просмотр. Приложение не обходит это ограничение. Вернитесь и выберите другой результат.");return;}selecting=true;ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new ArrayList<>(streams.keySet()));quality.setAdapter(adapter);quality.setSelection(0);quality.post(()->{if(gen!=generation||isFinishing())return;selecting=false;start(new ArrayList<>(streams.values()).get(0));});});}
    private void start(String url){if(player!=null&&url.equals(currentUrl))return;if(!Models.safeMedia(url)){status.setText("VK API вернул неподдерживаемый адрес потока.");return;}if(player!=null){position=player.getCurrentPosition();resume=player.getPlayWhenReady();player.release();}currentUrl=url;DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setUserAgent("YORU-VK/0.1.0").setConnectTimeoutMs(12000).setReadTimeoutMs(15000).setAllowCrossProtocolRedirects(false);player=new ExoPlayer.Builder(this).setMediaSourceFactory(new DefaultMediaSourceFactory(http)).build();view.setPlayer(player);player.addListener(new Player.Listener(){@Override public void onPlayerError(PlaybackException error){status.setText("Прямой поток сейчас не воспроизводится. Обновите доступ, выберите другое качество или другое видео. Ссылки ВК могут истекать.");view.setKeepScreenOn(false);}@Override public void onIsPlayingChanged(boolean playing){view.setKeepScreenOn(playing);}@Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_READY)status.setText("Встроенный плеер · поток от VK API");}});String kind=url.contains(".m3u8")?MimeTypes.APPLICATION_M3U8:url.contains(".mpd")?MimeTypes.APPLICATION_MPD:null;MediaItem.Builder item=new MediaItem.Builder().setUri(url);String selected=String.valueOf(quality.getSelectedItem());if(selected.startsWith("HLS"))kind=MimeTypes.APPLICATION_M3U8;if(selected.startsWith("DASH"))kind=MimeTypes.APPLICATION_MPD;if(kind!=null)item.setMimeType(kind);player.setMediaItem(item.build());player.seekTo(position);player.prepare();player.setPlayWhenReady(resume);}
    private void release(){if(player!=null){position=player.getCurrentPosition();view.setPlayer(null);player.release();player=null;}view.setKeepScreenOn(false);}
    @Override protected void onPause(){if(player!=null){resume=player.getPlayWhenReady();player.pause();}super.onPause();}
    @Override protected void onStop(){generation++;if(task!=null)task.cancel(true);release();super.onStop();}
    @Override protected void onSaveInstanceState(Bundle out){if(player!=null)position=player.getCurrentPosition();out.putLong("position",position);out.putBoolean("resume",resume);super.onSaveInstanceState(out);}
}
