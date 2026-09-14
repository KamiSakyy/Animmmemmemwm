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
        if(helper.getPeriodCount()==0)return;
        boolean exact=quality>0&&quality<QualityPlus.BEST;
        for(int period=0;period<helper.getPeriodCount();period++){
            MappingTrackSelector.MappedTrackInfo tracks=helper.getMappedTrackInfo(period);
            boolean video=false;
            for(int renderer=0;renderer<tracks.getRendererCount();renderer++){
                int type=tracks.getRendererType(renderer);if(type!=C.TRACK_TYPE_VIDEO&&type!=C.TRACK_TYPE_AUDIO)continue;
                for(ExoTrackSelection selection:helper.getTrackSelections(period,renderer)){
                    for(int track=0;track<selection.length();track++){
                        Format format=selection.getFormat(track);
                        if(format.drmInitData!=null)throw new IOException();
                        if(type==C.TRACK_TYPE_VIDEO){if(exact&&format.height!=quality)throw new IOException();video=true;}
                    }
                }
            }
            if(!video)throw new IOException();
        }
    }
}
