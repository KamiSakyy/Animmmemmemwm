package app.yoru.mobile;

import android.content.Context;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.exoplayer.offline.DownloadHelper;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.trackselection.MappingTrackSelector;
import java.io.IOException;

final class DownloadTracks {
    private DownloadTracks() {}

    static TrackSelectionParameters parameters(Context context,int quality){
        TrackSelectionParameters.Builder builder=new TrackSelectionParameters.Builder(context)
                .setPreferredAudioLanguage("ru").setForceHighestSupportedBitrate(true);
        if(quality>0&&quality<QualityPlus.BEST)builder.setMinVideoSize(0,quality).setMaxVideoSize(Integer.MAX_VALUE,quality);
        return builder.build();
    }

    static void requireSelectedQuality(DownloadHelper helper,int quality)throws IOException{
        if(quality<=0||quality==QualityPlus.BEST||helper.getPeriodCount()==0)return;
        for(int period=0;period<helper.getPeriodCount();period++){
            MappingTrackSelector.MappedTrackInfo tracks=helper.getMappedTrackInfo(period);
            boolean video=false;
            for(int renderer=0;renderer<tracks.getRendererCount();renderer++){
                if(tracks.getRendererType(renderer)!=C.TRACK_TYPE_VIDEO)continue;
                for(ExoTrackSelection selection:helper.getTrackSelections(period,renderer)){
                    for(int track=0;track<selection.length();track++){
                        Format format=selection.getFormat(track);
                        if(format.height!=quality)throw new IOException();
                        video=true;
                    }
                }
            }
            if(!video)throw new IOException();
        }
    }
}
