package app.yoru.mobile;

import android.net.Uri;
import android.text.TextUtils;
import org.json.JSONObject;

final class YummyFrame {
    static final String BASE="https://appassets.androidplatform.net/yoru-player/";
    static String html(String url,String nonce,int startSeconds,double episode){
        Uri uri=Uri.parse(url);String origin=uri.getScheme()+"://"+uri.getAuthority();
        String script="(function(){var f=document.getElementById('yummy'),secret="+JSONObject.quote(nonce)+",origin="+JSONObject.quote(origin)+",time=0,duration=0,active=false,seek="+Math.max(0,startSeconds)+";function send(x){try{x.n=secret;YoruPlayback.onEvent(JSON.stringify(x));}catch(e){}}function command(x){try{f.contentWindow.postMessage({key:'kodik_player_api',value:x},origin);}catch(e){}}function report(){send({kind:'time',time:time,duration:duration,playing:active});}window.__yoruPause=function(){command({method:'pause'});};window.__yoruPlay=function(){command({method:'play'});};f.addEventListener('load',function(){send({kind:'ready'});command({method:'play'});});window.addEventListener('message',function(e){if(e.source!==f.contentWindow||e.origin!==origin||!e.data||typeof e.data!=='object')return;var k=e.data.key,v=e.data.value;if(k==='kodik_player_current_episode'&&v){send({kind:'episode',episode:Number(v.episode),season:Number(v.season)});}else if(k==='kodik_player_duration_update'){duration=Number(v)||0;report();}else if(k==='kodik_player_time_update'){time=typeof v==='number'?v:Number(v&&v.time)||0;report();}else if(k==='kodik_player_play'||k==='kodik_player_video_started'){active=true;send({kind:'state',playing:true});if(seek>3){command({method:'seek',seconds:seek});seek=0;}report();}else if(k==='kodik_player_pause'){active=false;send({kind:'state',playing:false});report();}else if(k==='kodik_player_video_ended'){active=false;report();send({kind:'ended'});}});})();";
        return "<!doctype html><html lang=\"ru\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1\"><style>html,body{margin:0;width:100%;height:100%;overflow:hidden;background:#000}iframe{position:fixed;inset:0;width:100%;height:100%;border:0;background:#000}</style></head><body><iframe id=\"yummy\" src=\""+TextUtils.htmlEncode(url)+"\" title=\"YummyAnime\" allow=\"autoplay; fullscreen; picture-in-picture; encrypted-media\" allowfullscreen referrerpolicy=\"no-referrer\" sandbox=\"allow-scripts allow-same-origin allow-forms allow-presentation\"></iframe><script>"+script+"</script></body></html>";
    }
}
