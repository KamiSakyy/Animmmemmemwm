package app.yoru.mobile;

import java.util.*;

final class QualityPlus {
    static final int BEST=9999;
    static final int[] VALUES={360,480,720,1080,1440,2160};
    static final String[] LABELS={"360p","480p","720p","1080p","1440p · 2K","2160p · 4K"};
    static final String[] LABELS_WITH_BEST={"360p","480p","720p","1080p","1440p · 2K","2160p · 4K","Лучшее доступное"};
    private QualityPlus(){}
    static int clamp(int value){if(value>=BEST)return 2160;if(value<=360)return 360;if(value<=480)return 480;if(value<=720)return 720;if(value<=1080)return 1080;if(value<=1440)return 1440;return 2160;}
    static int index(int value){int q=clamp(value);for(int i=0;i<VALUES.length;i++)if(VALUES[i]==q)return i;return 2;}
    static int indexWithBest(int value){return value==BEST?VALUES.length:index(value);}
    static int[] values(){return Arrays.copyOf(VALUES,VALUES.length);}
    static int[] valuesWithBest(){int[] out=Arrays.copyOf(VALUES,VALUES.length+1);out[out.length-1]=BEST;return out;}
    static String name(int value){return value>=BEST?"Лучшее доступное":streamLabel(value);}

    static String streamLabel(int value){if(value<=0)return "Оригинал";if(value==1440)return "1440p · 2K";if(value==2160)return "2160p · 4K";return value+"p";}
    static int bestAtOrBelow(Collection<Integer> rows,int cap){if(rows==null||rows.isEmpty())return 0;int limit=cap>=BEST?Integer.MAX_VALUE:Math.max(0,cap),best=0;for(Integer q:rows){if(q==null)continue;int v=q; if(v<=0){if(best==0)best=v;continue;} if(v<=limit&&v>best)best=v;}if(best==0)for(Integer q:rows)if(q!=null){best=q;break;}return best;}
}
