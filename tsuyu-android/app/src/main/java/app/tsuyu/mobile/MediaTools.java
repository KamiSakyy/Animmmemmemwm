package app.tsuyu.mobile;

import android.content.*;
import android.graphics.*;
import android.media.*;
import android.net.*;
import android.provider.*;
import android.util.Base64;
import java.io.*;
import java.util.*;

public final class MediaTools {
    public static final int MAX_IMAGE=7*1024*1024, MAX_MEDIA=18*1024*1024;
    public static final class Pack {public String kind="text",mime="",name="",base64="",thumb="";public int width,height,duration,bytes;public boolean circle;}
    private MediaTools(){}
    public static Pack image(Context c,Uri uri)throws Exception{byte[] raw=read(c,uri,MAX_IMAGE);Bitmap src=BitmapFactory.decodeByteArray(raw,0,raw.length);if(src==null)throw new IOException("Фото не открылось");Bitmap full=scale(src,1600);ByteArrayOutputStream out=new ByteArrayOutputStream();full.compress(Bitmap.CompressFormat.JPEG,82,out);Bitmap th=scale(src,420);ByteArrayOutputStream t=new ByteArrayOutputStream();th.compress(Bitmap.CompressFormat.JPEG,62,t);Pack p=new Pack();p.kind="photo";p.mime="image/jpeg";p.name=name(c,uri,"photo.jpg");p.base64=b64(out.toByteArray());p.thumb=b64(t.toByteArray());p.width=full.getWidth();p.height=full.getHeight();p.bytes=out.size();return p;}
    public static Pack binary(Context c,Uri uri,String kind,boolean circle)throws Exception{byte[] raw=read(c,uri,MAX_MEDIA);Pack p=new Pack();p.kind=kind;p.circle=circle;p.mime=mime(c,uri,kind);p.name=name(c,uri,kind);p.base64=b64(raw);p.bytes=raw.length;if(kind.equals("video")||circle){Bitmap bm=videoFrame(c,uri);if(bm!=null){Bitmap th=scale(bm,420);ByteArrayOutputStream t=new ByteArrayOutputStream();th.compress(Bitmap.CompressFormat.JPEG,62,t);p.thumb=b64(t.toByteArray());p.width=th.getWidth();p.height=th.getHeight();}try{MediaMetadataRetriever r=new MediaMetadataRetriever();r.setDataSource(c,uri);p.duration=Integer.parseInt(first(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION),"0"))/1000;r.release();}catch(Exception ignored){}}return p;}
    public static Pack fromFile(File f,String kind,int duration)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();try(FileInputStream in=new FileInputStream(f)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1){if(out.size()+n>MAX_MEDIA)throw new IOException("Файл слишком большой для RTDB");out.write(b,0,n);}}Pack p=new Pack();p.kind=kind;p.mime="audio/mp4";p.name=f.getName();p.base64=b64(out.toByteArray());p.bytes=out.size();p.duration=duration;return p;}
    public static byte[] read(Context c,Uri uri,int max)throws Exception{try(InputStream in=c.getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){if(in==null)throw new IOException("Файл не открыт");byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1){if(out.size()+n>max)throw new IOException("Файл слишком большой для быстрой RTDB отправки");out.write(b,0,n);}return out.toByteArray();}}
    public static File writeTemp(Context c,Pack p)throws Exception{String ext=p.kind.equals("photo")?".jpg":p.kind.equals("audio")||p.kind.equals("voice")?".m4a":".mp4";File dir=new File(c.getCacheDir(),"media");dir.mkdirs();File f=File.createTempFile("tsuyu_",ext,dir);try(FileOutputStream out=new FileOutputStream(f)){out.write(Base64.decode(p.base64,Base64.DEFAULT));}return f;}
    public static Pack fromJson(org.json.JSONObject j){Pack p=new Pack();if(j==null)return p;p.kind=j.optString("kind","text");p.mime=j.optString("mime","");p.name=j.optString("name","");p.base64=j.optString("base64","");p.thumb=j.optString("thumb","");p.width=j.optInt("width");p.height=j.optInt("height");p.duration=j.optInt("duration");p.bytes=j.optInt("bytes");p.circle=j.optBoolean("circle");return p;}
    public static org.json.JSONObject json(Pack p)throws org.json.JSONException{return new org.json.JSONObject().put("kind",p.kind).put("mime",p.mime).put("name",p.name).put("base64",p.base64).put("thumb",p.thumb).put("width",p.width).put("height",p.height).put("duration",p.duration).put("bytes",p.bytes).put("circle",p.circle);} 
    public static String preview(String type,org.json.JSONArray media){if("photo".equals(type))return "Фото";if("video".equals(type))return "Видео";if("circle".equals(type))return "Кружочек";if("voice".equals(type))return "Голосовое";if("audio".equals(type))return "Аудиофайл";if(media!=null&&media.length()>1)return "Коллаж · "+media.length();return "Сообщение";}
    private static Bitmap scale(Bitmap b,int max){int w=b.getWidth(),h=b.getHeight();if(w<=max&&h<=max)return b;float k=Math.min(max/(float)w,max/(float)h);return Bitmap.createScaledBitmap(b,Math.max(1,(int)(w*k)),Math.max(1,(int)(h*k)),true);} 
    private static Bitmap videoFrame(Context c,Uri u){try{MediaMetadataRetriever r=new MediaMetadataRetriever();r.setDataSource(c,u);Bitmap b=r.getFrameAtTime(0);r.release();return b;}catch(Exception e){return null;}}
    private static String mime(Context c,Uri uri,String kind){String m=c.getContentResolver().getType(uri);if(m!=null&&!m.isEmpty())return m;if(kind.equals("audio")||kind.equals("voice"))return "audio/mp4";if(kind.equals("video")||kind.equals("circle"))return "video/mp4";return "application/octet-stream";}
    private static String name(Context c,Uri uri,String fallback){String n=null;try(android.database.Cursor cur=c.getContentResolver().query(uri,null,null,null,null)){if(cur!=null&&cur.moveToFirst()){int i=cur.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(i>=0)n=cur.getString(i);}}catch(Exception ignored){}return n==null||n.trim().isEmpty()?fallback:n;}
    private static String b64(byte[] b){return Base64.encodeToString(b,Base64.NO_WRAP);}private static String first(String a,String b){return a!=null&&!a.isEmpty()?a:b;}
}
