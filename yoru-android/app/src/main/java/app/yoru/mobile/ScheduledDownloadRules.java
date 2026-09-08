package app.yoru.mobile;

final class ScheduledDownloadRules {
    private ScheduledDownloadRules() {}
    static boolean accepts(double number,int quality,double actualNumber,int actualQuality,boolean future) {
        return Double.isFinite(number) && number>0 && Double.isFinite(actualNumber) && !future
                && Math.abs(actualNumber-number)<=0.001 && (quality==QualityPlus.BEST||quality==actualQuality);
    }
    static boolean matches(double number,String voice,int quality,ApiRepository.DownloadOption option) {
        if(option==null||option.source==null||option.episode==null||option.episode.future)return false;
        if(!accepts(number,quality,option.episode.number,option.quality,option.episode.future))return false;
        if(!voice.isEmpty()&&!ApiRepository.voiceMatches(voice,option.voice+" "+option.episode.name))return false;
        String url=ApiRepository.safeUrl(option.episode.streams.get(option.quality));
        return !url.isEmpty()&&VideoResolver.downloadable(url);
    }
}
