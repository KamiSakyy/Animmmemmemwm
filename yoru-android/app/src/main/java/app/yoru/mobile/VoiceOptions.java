package app.yoru.mobile;

import java.util.ArrayList;
import java.util.Arrays;

final class VoiceOptions {
    private final ArrayList<String> names=new ArrayList<>(Arrays.asList(ApiRepository.VOICE_PREF_NAMES));
    private final ArrayList<String> values=new ArrayList<>(Arrays.asList(ApiRepository.VOICE_PREF_VALUES));
    private final String preferred;
    private final int selected;

    VoiceOptions(String preferred){
        this.preferred=preferred==null?"":preferred;String key=ApiRepository.voiceKey(this.preferred);int found=-1;
        for(int i=0;i<values.size();i++)if(ApiRepository.voiceKey(values.get(i)).equals(key)){found=i;break;}
        if(found<0&&!this.preferred.isEmpty()){found=values.size();values.add(this.preferred);names.add(this.preferred);}
        selected=Math.max(0,found);
    }

    static void retainPreferred(ArrayList<String> names,ArrayList<String> values,String preferred){
        String key=ApiRepository.voiceKey(preferred);if(key.isEmpty())return;
        for(String value:values)if(key.equals(ApiRepository.voiceKey(value)))return;
        String title=ApiRepository.voiceTitle(preferred);if(title.isEmpty())title=preferred;
        names.add(title+" · пока недоступна");values.add(preferred);
    }

    String[] names(){return names.toArray(new String[0]);}
    int index(){return selected;}
    String value(int index){return index<0||index>=values.size()||index==selected?preferred:values.get(index);}
}
