package app.yoru.mobile;

import android.content.Context;
import android.graphics.*;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

public final class ImageLoader {
    private final Context context;private final ExecutorService pool=Executors.newFixedThreadPool(3);private volatile int generation;
    private final LruCache<String,Bitmap> memory=new LruCache<String,Bitmap>(20*1024*1024){protected int sizeOf(String k,Bitmap v){return v.getByteCount();}};
    public ImageLoader(Context c){context=c.getApplicationContext();}
    public void clear(){generation++;memory.evictAll();}
    private File cached(String key){String hash=Integer.toHexString(key.hashCode());try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));hash=out.toString();}catch(Exception ignored){}return new File(new File(context.getCacheDir(),"covers"),hash+".webp");}
    public void load(ImageView view,Anime a){String key=a.poster.isEmpty()?a.key():a.poster;view.setTag(key);view.setImageResource(R.drawable.ic_yoru);Bitmap cached=memory.get(key);if(cached!=null){view.setImageBitmap(cached);return;}int gen=generation;pool.execute(()->{Bitmap b=null;File file=cached(key);if(file.exists()){b=BitmapFactory.decodeFile(file.getAbsolutePath());file.setLastModified(System.currentTimeMillis());}String asset="";if(a.source.equals("anilibria"))asset=a.id;else if(a.malId==52991)asset="9542";else if(a.malId==52299)asset="9600";else if(a.malId==54492)asset="9555";if(b==null&&!asset.isEmpty())try(InputStream in=context.getAssets().open("posters/"+asset+".webp")){b=BitmapFactory.decodeStream(in);}catch(Exception ignored){}
        String url=ApiRepository.safeUrl(a.poster);if(b==null&&!url.isEmpty()&&!YoruApp.app().savingMobile()&&YoruApp.app().traffic.connected())try{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(8000);c.setReadTimeout(10000);c.setRequestProperty("User-Agent","YORU-Android/2.0");try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1&&out.size()<4*1024*1024)out.write(bytes,0,n);byte[] data=out.toByteArray();BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,o);int sample=1;while(o.outWidth/sample>600)sample*=2;o.inSampleSize=sample;o.inJustDecodeBounds=false;o.inPreferredConfig=Bitmap.Config.RGB_565;b=BitmapFactory.decodeByteArray(data,0,data.length,o);}finally{c.disconnect();}}catch(Exception ignored){}
        if(b!=null&&gen==generation){memory.put(key,b);if(!file.exists()){try{file.getParentFile().mkdirs();try(OutputStream out=new FileOutputStream(file)){b.compress(Bitmap.CompressFormat.WEBP,82,out);}}catch(Exception ignored){}}trim();Bitmap ready=b;YoruApp.app().main.post(()->{if(gen==generation&&key.equals(view.getTag()))view.setImageBitmap(ready);});}});}
    private void trim(){File dir=new File(context.getCacheDir(),"covers");File[] files=dir.listFiles();if(files==null||files.length<300)return;long size=0;for(File f:files)size+=f.length();if(size<80L*1024*1024)return;Arrays.sort(files,Comparator.comparingLong(File::lastModified));for(File f:files){long n=f.length();if(f.delete())size-=n;if(size<64L*1024*1024)break;}}
}
