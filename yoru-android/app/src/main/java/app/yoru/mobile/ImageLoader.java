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
    private final Context context;private final ExecutorService pool=Executors.newFixedThreadPool(3);private volatile int generation;
    private final LruCache<String,Bitmap> memory=new LruCache<String,Bitmap>(20*1024*1024){protected int sizeOf(String k,Bitmap v){return v.getByteCount();}};
    public ImageLoader(Context c){context=c.getApplicationContext();}
    public void clear(){generation++;memory.evictAll();}
    private File cached(String key){String hash=Integer.toHexString(key.hashCode());try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));hash=out.toString();}catch(Exception ignored){}return new File(new File(context.getCacheDir(),"covers"),hash+".webp");}
    public void load(ImageView view,Anime a){String key=a.poster.isEmpty()?a.key():a.poster;view.setTag(key);view.setImageResource(R.drawable.ic_yoru);Bitmap cached=memory.get(key);if(cached!=null){view.setImageBitmap(cached);return;}int gen=generation;pool.execute(()->{Bitmap b=null;File file=cached(key);if(file.exists()){b=BitmapFactory.decodeFile(file.getAbsolutePath());file.setLastModified(System.currentTimeMillis());}String asset="";if(a.source.equals("anilibria"))asset=a.id;else if(a.malId==52991)asset="9542";else if(a.malId==52299)asset="9600";else if(a.malId==54492)asset="9555";if(b==null&&!asset.isEmpty())try(InputStream in=context.getAssets().open("posters/"+asset+".webp")){b=BitmapFactory.decodeStream(in);}catch(Exception ignored){}
        String url=ApiRepository.safeUrl(a.poster);YoruApp app=YoruApp.app();boolean online=app!=null&&!app.savingMobile()&&app.traffic.connected();if(b==null&&!url.isEmpty()&&online)b=downloadImage(url);
        if(b!=null&&gen==generation){memory.put(key,b);if(!file.exists()){try{file.getParentFile().mkdirs();try(OutputStream out=new FileOutputStream(file)){b.compress(Bitmap.CompressFormat.WEBP,82,out);}}catch(Exception ignored){}}trim();Bitmap ready=b;YoruApp.app().main.post(()->{if(gen==generation&&key.equals(view.getTag()))view.setImageBitmap(ready);});}});}
    private Bitmap downloadImage(String url){for(String candidate:imageCandidates(url))try{Bitmap b=fetchBitmap(candidate);if(b!=null)return b;}catch(Exception ignored){}return null;}
    private ArrayList<String> imageCandidates(String url){LinkedHashSet<String> out=new LinkedHashSet<>();String safe=ApiRepository.safeUrl(url);if(safe.isEmpty())return new ArrayList<>();out.add(safe);String low=safe.toLowerCase(Locale.ROOT);if(low.contains("cover.cdnlibs.org")&&low.endsWith(".jpg")){if(low.contains("_thumb.jpg"))out.add(safe.replace("_thumb.jpg",".jpg"));else out.add(safe.replace(".jpg","_thumb.jpg"));}if(low.contains("?size=min")){out.add(safe.replace("?size=min",""));out.add(safe.replace("size=min","size=mid"));}return new ArrayList<>(out);}
    private Bitmap fetchBitmap(String url)throws Exception{HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(8000);c.setReadTimeout(12000);c.setInstanceFollowRedirects(true);c.setRequestProperty("User-Agent",CHROME);c.setRequestProperty("Accept","image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8");c.setRequestProperty("Accept-Language","ru-RU,ru;q=0.9,en;q=0.5");c.setRequestProperty("Cache-Control","no-cache");String referer=refererFor(url);if(!referer.isEmpty())c.setRequestProperty("Referer",referer);String origin=originFor(referer.isEmpty()?url:referer);if(!origin.isEmpty())c.setRequestProperty("Origin",origin);int code=c.getResponseCode();if(code<200||code>=300)throw new IOException("image");String type=c.getContentType();String ct=type==null?"":type.toLowerCase(Locale.ROOT);if(!ct.isEmpty()&&!ct.startsWith("image/")&&!ct.contains("octet-stream"))throw new IOException("image");try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1&&out.size()<5*1024*1024)out.write(bytes,0,n);byte[] data=out.toByteArray();if(data.length<64)throw new IOException("image");BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,o);if(o.outWidth<=0||o.outHeight<=0)throw new IOException("image");int sample=1;while(o.outWidth/sample>700||o.outHeight/sample>1100)sample*=2;o.inSampleSize=sample;o.inJustDecodeBounds=false;o.inPreferredConfig=Bitmap.Config.RGB_565;return BitmapFactory.decodeByteArray(data,0,data.length,o);}}finally{if(c!=null)c.disconnect();}}
    private static String refererFor(String url){String host=host(url);if(host.contains("cdnlibs.org"))return "https://anilib.me/";if(host.contains("shikimori"))return "https://shikimori.one/";return originFor(url)+"/";}
    private static String originFor(String url){try{Uri u=Uri.parse(url);String scheme=u.getScheme(),host=u.getHost();return scheme==null||host==null?"":scheme+"://"+host;}catch(Exception e){return "";}}
    private static String host(String url){try{String h=Uri.parse(url).getHost();return h==null?"":h.toLowerCase(Locale.ROOT);}catch(Exception e){return "";}}
    private void trim(){File dir=new File(context.getCacheDir(),"covers");File[] files=dir.listFiles();if(files==null||files.length<300)return;long size=0;for(File f:files)size+=f.length();if(size<80L*1024*1024)return;Arrays.sort(files,Comparator.comparingLong(File::lastModified));for(File f:files){long n=f.length();if(f.delete())size-=n;if(size<64L*1024*1024)break;}}
}
