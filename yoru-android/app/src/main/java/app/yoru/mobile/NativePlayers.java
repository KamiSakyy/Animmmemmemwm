package app.yoru.mobile;

import android.content.Context;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;

final class NativePlayers {
    private NativePlayers(){}
    static ExoPlayer create(Context context){DefaultLoadControl control=new DefaultLoadControl.Builder().setBufferDurationsMs(6500,32000,450,950).setBackBuffer(12000,false).build();ExoPlayer player=new ExoPlayer.Builder(context).setLoadControl(control).build();player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);player.setHandleAudioBecomingNoisy(true);return player;}
    static void limitQuality(ExoPlayer player,int height){
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setMaxVideoSize(Integer.MAX_VALUE,height>0&&height!=QualityPlus.BEST?height:Integer.MAX_VALUE).setForceLowestBitrate(false).build());
        androidx.media3.exoplayer.trackselection.TrackSelector selector=player.getTrackSelector();
        if(selector instanceof androidx.media3.exoplayer.trackselection.DefaultTrackSelector){
            androidx.media3.exoplayer.trackselection.DefaultTrackSelector defaults=(androidx.media3.exoplayer.trackselection.DefaultTrackSelector)selector;
            defaults.setParameters(defaults.getParameters().buildUpon().setExceedVideoConstraintsIfNecessary(false));
        }
    }
    static MediaSource source(String url){return source(url,"");}
    static MediaSource source(String url,String mime){return new DefaultMediaSourceFactory(YoruApp.app().mediaCache.onlineFactory()).createMediaSource(DownloadHub.item(url,mime));}
}
