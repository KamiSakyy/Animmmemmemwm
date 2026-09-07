package app.yoru.mobile;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

public final class ImageLoader {
    private static final String CHROME="Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Mobile Safari/537.36";
    private static final int MEMORY_LIMIT=Math.max(60*1024*1024,Math.min(96*1024*1024,(int)(Runtime.getRuntime().maxMemory()/4)));
    private final Context context;
    private final ThreadPoolExecutor pool=new ThreadPoolExecutor(4,12,15L,TimeUnit.SECONDS,new LinkedBlockingQueue<>(260),r->{Thread t=new Thread(r,"yoru-image");t.setPriority(Thread.NORM_PRIORITY-1);return t;},new ThreadPoolExecutor.CallerRunsPolicy());
    private final ConcurrentHashMap<String,ArrayList<ImageView>> waiters=new ConcurrentHashMap<>();
    private final Object waitLock=new Object();
    private final ConcurrentLinkedQueue<Bitmap> reuse=new ConcurrentLinkedQueue<>();
    private volatile int generation;
    private final LruCache<String,Bitmap> memory=new LruCache<String,Bitmap>(MEMORY_LIMIT){protected int sizeOf(String k,Bitmap v){return Math.max(1,v.getByteCount());}protected void entryRemoved(boolean evicted,String k,Bitmap oldValue,Bitmap newValue){if(evicted&&oldValue!=null&&oldValue.isMutable()&&!oldValue.isRecycled()&&reuse.size()<12)reuse.offer(oldValue);}};
    public ImageLoader(Context c){context=c.getApplicationContext();pool.allowCoreThreadTimeOut(true);deleteOldDiskCache();}
    public void clear(){generation++;memory.evictAll();synchronized(waitLock){waiters.clear();}}
    public void load(ImageView view,Anime a){loadInternal(view,a==null?"":a.poster,a==null?"yoru":a.key(),assetFor(a));}
    public void load(ImageView view,String url,String fallback){loadInternal(view,url,fallback,"");}
    private String assetFor(Anime a){if(a==null)return "";if(a.source.equals("anilibria"))return a.id;else if(a.malId==52991)return "9542";else if(a.malId==52299)return "9600";else if(a.malId==54492)return "9555";return "";}
    private void loadInternal(ImageView view,String raw,String fallback,String asset){String url=ApiRepository.safeUrl(raw);String key=!url.isEmpty()?url:(fallback==null||fallback.isEmpty()?"yoru":fallback);Object oldTag=view.getTag();boolean sameKey=key.equals(oldTag);view.setTag(key);Bitmap cached=memory.get(key);if(cached!=null){view.setImageBitmap(cached);return;}if(!sameKey)view.setImageResource(R.drawable.ic_yoru);boolean leader=false;synchronized(waitLock){ArrayList<ImageView> list=waiters.get(key);if(list==null){list=new ArrayList<>();waiters.put(key,list);leader=true;}list.add(view);}if(!leader)return;int gen=generation;try{pool.execute(()->{Bitmap ready=null;try{Bitmap b=null;if(asset!=null&&!asset.isEmpty())try(InputStream in=context.getAssets().open("posters/"+asset+".webp")){b=BitmapFactory.decodeStream(in,null,decodeOptions(true));}catch(Exception ignored){}YoruApp app=YoruApp.app();boolean online=app!=null&&app.traffic!=null&&app.traffic.connected();if(b==null&&!url.isEmpty()&&online)b=downloadImage(url);if(b!=null&&gen==generation){memory.put(key,b);ready=b;}}finally{ArrayList<ImageView> targets; synchronized(waitLock){targets=waiters.remove(key);}Bitmap bitmap=ready;YoruApp current=YoruApp.app();if(bitmap!=null&&targets!=null&&current!=null)current.main.post(()->{if(gen!=generation)return;for(ImageView target:targets)if(target!=null&&key.equals(target.getTag()))target.setImageBitmap(bitmap);});}});}catch(RejectedExecutionException rejected){synchronized(waitLock){waiters.remove(key);}}}
    private static BitmapFactory.Options decodeOptions(boolean asset){BitmapFactory.Options o=new BitmapFactory.Options();o.inPreferredConfig=Bitmap.Config.ARGB_8888;o.inDither=true;o.inMutable=true;return o;}
    private Bitmap downloadImage(String url){for(String candidate:imageCandidates(url))try{Bitmap b=fetchBitmap(candidate);if(b!=null)return b;}catch(Exception ignored){}return null;}
    private ArrayList<String> imageCandidates(String url){LinkedHashSet<String> out=new LinkedHashSet<>();String safe=ApiRepository.safeUrl(url);if(safe.isEmpty())return new ArrayList<>();String high=highQuality(safe);out.add(high);out.add(safe);String low=safe.toLowerCase(Locale.ROOT);if(low.contains("cover.cdnlibs.org")&&low.endsWith(".jpg")){out.add(safe.replace("_thumb.jpg",".jpg"));}if(low.contains("?size=min")){out.add(safe.replace("?size=min",""));out.add(safe.replace("size=min","size=orig"));out.add(safe.replace("size=min","size=max"));}return new ArrayList<>(out);}
    private static String highQuality(String safe){String s=safe.replace("_thumb.jpg",".jpg").replace("/thumbs/","/").replace("/resized/preview/","/uploads/").replace("/resized/preview_main/","/uploads/");s=s.replace("?size=min","").replace("size=min","size=orig");return s;}
    private Bitmap fetchBitmap(String url)throws Exception{HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(2200);c.setReadTimeout(3600);c.setInstanceFollowRedirects(true);c.setRequestProperty("User-Agent",CHROME);c.setRequestProperty("Accept","image/avif,image/webp,image/apng,image/*,*/*;q=0.8");c.setRequestProperty("Accept-Language","ru-RU,ru;q=0.9,en;q=0.5");String referer=refererFor(url);if(!referer.isEmpty())c.setRequestProperty("Referer",referer);int code=c.getResponseCode();if(code<200||code>=300)throw new IOException("image");String type=c.getContentType();String ct=type==null?"":type.toLowerCase(Locale.ROOT);if(!ct.isEmpty()&&!ct.startsWith("image/")&&!ct.contains("octet-stream"))throw new IOException("image");try(InputStream in=new BufferedInputStream(c.getInputStream());ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] bytes=new byte[16384];int n;int limit=12*1024*1024;while((n=in.read(bytes))!=-1){if(out.size()+n>limit)break;out.write(bytes,0,n);}byte[] data=out.toByteArray();if(data.length<64)throw new IOException("image");BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,bounds);BitmapFactory.Options o=decodeOptions(false);Bitmap reuseBitmap=reuseFor(bounds.outWidth,bounds.outHeight);if(reuseBitmap!=null)o.inBitmap=reuseBitmap;try{return BitmapFactory.decodeByteArray(data,0,data.length,o);}catch(IllegalArgumentException badReuse){o.inBitmap=null;return BitmapFactory.decodeByteArray(data,0,data.length,o);}}}finally{if(c!=null)c.disconnect();}}

    private Bitmap reuseFor(int w,int h){if(w<=0||h<=0)return null;long need=(long)w*h*4L;for(int i=0;i<16;i++){Bitmap b=reuse.poll();if(b==null)return null;if(!b.isRecycled()&&b.isMutable()&&b.getAllocationByteCount()>=need)return b;}return null;}
    private static String refererFor(String url){String host=host(url);if(host.contains("cdnlibs.org"))return "https://anilib.me/";if(host.contains("shikimori"))return "https://shikimori.one/";return originFor(url)+"/";}
    private static String originFor(String url){try{Uri u=Uri.parse(url);String scheme=u.getScheme(),host=u.getHost();return scheme==null||host==null?"":scheme+"://"+host;}catch(Exception e){return "";}}
    private static String host(String url){try{String h=Uri.parse(url).getHost();return h==null?"":h.toLowerCase(Locale.ROOT);}catch(Exception e){return "";}}
    private void deleteOldDiskCache(){File dir=new File(context.getCacheDir(),"covers");erase(dir);}
    private static void erase(File f){try{if(f==null||!f.exists())return;if(f.isDirectory()){File[] kids=f.listFiles();if(kids!=null)for(File k:kids)erase(k);}f.delete();}catch(Exception ignored){}}
}
