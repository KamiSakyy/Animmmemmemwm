package app.yoru.mobile;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.widget.ImageView;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

public final class ImageLoader {
    private static final String CHROME="Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Mobile Safari/537.36";
    private final Context context;
    private final ThreadPoolExecutor pool=new ThreadPoolExecutor(4,12,15L,TimeUnit.SECONDS,new LinkedBlockingQueue<>(260),r->{Thread t=new Thread(r,"yoru-image");t.setPriority(Thread.NORM_PRIORITY-1);return t;},new ThreadPoolExecutor.AbortPolicy());
    private final ConcurrentHashMap<String,ArrayList<ImageView>> waiters=new ConcurrentHashMap<>();
    private final Object waitLock=new Object();
    private volatile int generation;private volatile long lastTrim;
    public ImageLoader(Context c){context=c.getApplicationContext();pool.allowCoreThreadTimeOut(true);deleteOldDiskCache();ensureCacheDir();}
    public void clear(){generation++;synchronized(waitLock){waiters.clear();}erase(cacheDir());ensureCacheDir();}
    public void load(ImageView view,Anime a){loadInternal(view,a==null?"":a.poster,a==null?"yoru":a.key(),assetFor(a));}
    public void load(ImageView view,String url,String fallback){loadInternal(view,url,fallback,"");}
    private String assetFor(Anime a){if(a==null)return "";if(a.source.equals("anilibria"))return a.id;else if(a.malId==52991)return "9542";else if(a.malId==52299)return "9600";else if(a.malId==54492)return "9555";return "";}
    private void loadInternal(ImageView view,String raw,String fallback,String asset){String url=ApiRepository.safeUrl(raw);String key=!url.isEmpty()?url:(fallback==null||fallback.isEmpty()?"yoru":fallback);Object oldTag=view.getTag();boolean sameKey=key.equals(oldTag);view.setTag(key);File cachedFile=cacheFile(key);if(cachedFile.exists()){Bitmap quick=decodeFile(cachedFile);if(quick!=null){view.setImageBitmap(quick);return;}cachedFile.delete();}if(!sameKey)view.setImageResource(R.drawable.ic_yoru);boolean leader=false;synchronized(waitLock){ArrayList<ImageView> list=waiters.get(key);if(list==null){list=new ArrayList<>();waiters.put(key,list);leader=true;}list.add(view);}if(!leader)return;int gen=generation;try{pool.execute(()->{Bitmap ready=null;try{File file=cacheFile(key);byte[] bytes=null;if(file.exists()){bytes=readBytes(new BufferedInputStream(new FileInputStream(file)),12*1024*1024);if(decodeBytes(bytes)==null){file.delete();bytes=null;}else file.setLastModified(System.currentTimeMillis());}if((bytes==null||bytes.length<64)&&asset!=null&&!asset.isEmpty())try(InputStream in=context.getAssets().open("posters/"+asset+".webp")){bytes=readBytes(in,12*1024*1024);}catch(Exception ignored){}YoruApp app=YoruApp.app();boolean online=app!=null&&app.traffic!=null&&app.traffic.connected();if((bytes==null||bytes.length<64)&&!url.isEmpty()&&online){bytes=downloadBytes(url);if(bytes!=null&&bytes.length>=64)saveRaw(file,bytes);}Bitmap b=decodeBytes(bytes);if(b!=null&&gen==generation){trimMaybe();ready=b;}}catch(Exception ignored){}finally{ArrayList<ImageView> targets; synchronized(waitLock){targets=waiters.remove(key);}Bitmap bitmap=ready;YoruApp current=YoruApp.app();if(bitmap!=null&&targets!=null&&current!=null)current.main.post(()->{if(gen!=generation)return;for(ImageView target:targets)if(target!=null&&key.equals(target.getTag()))target.setImageBitmap(bitmap);});}});}catch(RejectedExecutionException rejected){synchronized(waitLock){waiters.remove(key);}}}
    private static BitmapFactory.Options decodeOptions(){BitmapFactory.Options o=new BitmapFactory.Options();o.inPreferredConfig=Bitmap.Config.ARGB_8888;o.inDither=true;return o;}
    private Bitmap decodeFile(File f){try{return decodeBytes(readBytes(new BufferedInputStream(new FileInputStream(f)),12*1024*1024));}catch(Exception e){return null;}}
    private Bitmap decodeBytes(byte[] data){if(data==null||data.length<64)return null;try{return BitmapFactory.decodeByteArray(data,0,data.length,decodeOptions());}catch(Exception e){return null;}}
    private byte[] downloadBytes(String url){for(String candidate:imageCandidates(url))try{return fetchBytes(candidate);}catch(Exception ignored){}return null;}
    private ArrayList<String> imageCandidates(String url){LinkedHashSet<String> out=new LinkedHashSet<>();String safe=ApiRepository.safeUrl(url);if(safe.isEmpty())return new ArrayList<>();String high=highQuality(safe);out.add(safe);out.add(high);String low=safe.toLowerCase(Locale.ROOT);if(low.contains("cover.cdnlibs.org")&&low.endsWith(".jpg"))out.add(safe.replace("_thumb.jpg",".jpg"));if(low.contains("?size=min")){out.add(safe.replace("?size=min",""));out.add(safe.replace("size=min","size=orig"));out.add(safe.replace("size=min","size=max"));}return new ArrayList<>(out);}
    private static String highQuality(String safe){String s=safe.replace("_thumb.jpg",".jpg").replace("/thumbs/","/").replace("/resized/preview/","/uploads/").replace("/resized/preview_main/","/uploads/");s=s.replace("?size=min","").replace("size=min","size=orig");return s;}
    private byte[] fetchBytes(String url)throws Exception{HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(2200);c.setReadTimeout(3600);c.setInstanceFollowRedirects(true);c.setRequestProperty("User-Agent",CHROME);c.setRequestProperty("Accept","image/avif,image/webp,image/apng,image/*,*/*;q=0.8");c.setRequestProperty("Accept-Language","ru-RU,ru;q=0.9,en;q=0.5");String referer=refererFor(url);if(!referer.isEmpty())c.setRequestProperty("Referer",referer);int code=c.getResponseCode();if(code<200||code>=300)throw new IOException("image");String type=c.getContentType();String ct=type==null?"":type.toLowerCase(Locale.ROOT);if(!ct.isEmpty()&&!ct.startsWith("image/")&&!ct.contains("octet-stream"))throw new IOException("image");byte[] data=readBytes(new BufferedInputStream(c.getInputStream()),12*1024*1024);if(data.length<64)throw new IOException("image");return data;}finally{if(c!=null)c.disconnect();}}
    private static byte[] readBytes(InputStream in,int limit)throws IOException{try(InputStream input=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] bytes=new byte[16384];int n;while((n=input.read(bytes))!=-1){if(out.size()+n>limit)break;out.write(bytes,0,n);}return out.toByteArray();}}
    private File cacheDir(){return new File(context.getCacheDir(),"yoru-image-cache");}
    private void ensureCacheDir(){try{File dir=cacheDir();if(!dir.exists())dir.mkdirs();new File(dir,".nomedia").createNewFile();}catch(Exception ignored){}}
    private File cacheFile(String key){String hash=Integer.toHexString(key.hashCode());try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));hash=out.toString();}catch(Exception ignored){}return new File(cacheDir(),hash+".bin");}
    private void saveRaw(File file,byte[] raw){try{ensureCacheDir();File tmp=new File(file.getParentFile(),file.getName()+".tmp");try(OutputStream out=new BufferedOutputStream(new FileOutputStream(tmp))){out.write(raw);}if(file.exists())file.delete();tmp.renameTo(file);}catch(Exception ignored){}}
    private static String refererFor(String url){String host=host(url);if(host.contains("cdnlibs.org"))return "https://anilib.me/";if(host.contains("shikimori"))return "https://shikimori.one/";return originFor(url)+"/";}
    private static String originFor(String url){try{Uri u=Uri.parse(url);String scheme=u.getScheme(),host=u.getHost();return scheme==null||host==null?"":scheme+"://"+host;}catch(Exception e){return "";}}
    private static String host(String url){try{String h=Uri.parse(url).getHost();return h==null?"":h.toLowerCase(Locale.ROOT);}catch(Exception e){return "";}}
    private void trimMaybe(){long now=System.currentTimeMillis();if(now-lastTrim<60*60*1000L)return;lastTrim=now;File[] files=cacheDir().listFiles();if(files==null||files.length<360)return;long size=0;for(File f:files)size+=f.length();if(size<160L*1024*1024)return;Arrays.sort(files,Comparator.comparingLong(File::lastModified));for(File f:files){if(f.getName().equals(".nomedia"))continue;long n=f.length();if(f.delete())size-=n;if(size<120L*1024*1024)break;}}
    private void deleteOldDiskCache(){erase(new File(context.getCacheDir(),"covers"));erase(new File(context.getFilesDir(),"yoru-image-cache"));}
    private static void erase(File f){try{if(f==null||!f.exists())return;if(f.isDirectory()){File[] kids=f.listFiles();if(kids!=null)for(File k:kids)erase(k);}f.delete();}catch(Exception ignored){}}
}
