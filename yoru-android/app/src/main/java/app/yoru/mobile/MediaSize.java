package app.yoru.mobile;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.*;

final class MediaSize {
    private MediaSize(){}
    static long rangeTotal(String header){if(header==null)return -1;Matcher match=Pattern.compile("(?i)^bytes\\s+0-0/([0-9]+)$").matcher(header.trim());if(!match.matches())return -1;try{long size=Long.parseLong(match.group(1));return size>0?size:-1;}catch(NumberFormatException e){return -1;}}
    static boolean singleFile(String url){try{if(url.toLowerCase(Locale.ROOT).contains(".m3u8")||url.toLowerCase(Locale.ROOT).contains(".mpd"))return false;String path=new URL(url).getPath().toLowerCase(Locale.ROOT);return path.endsWith(".mp4")||path.endsWith(".webm")||path.endsWith(".mkv")||path.endsWith(".m4v");}catch(Exception e){return false;}}
    private static boolean manifest(String url){String value=url.toLowerCase(Locale.ROOT);return value.contains(".m3u8")||value.contains(".mpd");}
    static final class Info {
        final long bytes;final String mime;
        Info(long bytes,String mime){this.bytes=bytes;this.mime=mime;}
    }
    static boolean containerMime(String mime){return "video/mp4".equals(mime)||"application/mp4".equals(mime)||"video/webm".equals(mime)||"video/x-matroska".equals(mime)||"video/x-m4v".equals(mime);}
    static String canonicalMime(String mime){
        if(mime==null)return "";String value=mime.split(";",2)[0].trim().toLowerCase(Locale.ROOT);
        if(value.equals("application/x-mpegurl")||value.equals("application/vnd.apple.mpegurl")||value.equals("audio/mpegurl")||value.equals("audio/x-mpegurl"))return "application/x-mpegURL";
        if(value.equals("application/mp4")||value.equals("video/x-m4v"))return "video/mp4";
        return containerMime(value)||value.equals("application/dash+xml")?value:"";
    }
    static boolean mediaMime(String mime){return !canonicalMime(mime).isEmpty();}
    static long probe(String raw){return probeInfo(raw).bytes;}
    static Info probeInfo(String raw){return probeInfo(raw,false);}
    static Info probeInfo(String raw,boolean exact){
        String url=ApiRepository.safeUrl(raw);Info unknown=new Info(-1,"");if(url.isEmpty())return unknown;if(manifest(url))return exact?adaptiveInfo(url,url.toLowerCase(Locale.ROOT).contains(".mpd")):new Info(-1,url.toLowerCase(Locale.ROOT).contains(".mpd")?"application/dash+xml":"application/x-mpegURL");
        Info found=unknown;
        for(int attempt=0;attempt<2;attempt++){
            HttpURLConnection connection=null;
            try{
                TaskQueue.check();connection=HttpTransport.open(new URL(url));connection.setConnectTimeout(2500);connection.setReadTimeout(2500);connection.setRequestMethod(attempt==0?"HEAD":"GET");connection.setRequestProperty("Accept-Encoding","identity");connection.setRequestProperty("Referer","https://yani.tv/");if(attempt==1)connection.setRequestProperty("Range","bytes=0-0");
                int code=connection.getResponseCode();if(attempt==0&&code!=200)continue;if(code!=200&&code!=206)return found;
                String finalUrl=connection.getURL().toString();if(manifest(finalUrl))return exact?adaptiveInfo(finalUrl,finalUrl.toLowerCase(Locale.ROOT).contains(".mpd")):new Info(-1,finalUrl.toLowerCase(Locale.ROOT).contains(".mpd")?"application/dash+xml":"application/x-mpegURL");String encoding=connection.getContentEncoding();
                String type=connection.getContentType();String mime=type==null?"":type.toLowerCase(Locale.ROOT).split(";",2)[0].trim();if(mime.contains("mpegurl"))return exact?hlsInfo(finalUrl):new Info(-1,"application/x-mpegURL");if(mime.equals("application/dash+xml"))return new Info(-1,"application/dash+xml");boolean container=containerMime(mime);
                if(!container&&(!singleFile(finalUrl)||(!mime.isEmpty()&&!mime.startsWith("video/")&&!mime.equals("application/octet-stream"))))continue;
                String format=container?canonicalMime(mime):"";found=new Info(-1,format);if(encoding!=null&&!encoding.equalsIgnoreCase("identity"))continue;
                if(code==206)return new Info(rangeTotal(connection.getHeaderField("Content-Range")),format);
                long size=connection.getContentLengthLong();if(size>0&&connection.getHeaderField("Content-Range")==null)return new Info(size,format);
            }catch(java.net.SocketTimeoutException ignored){}catch(java.io.InterruptedIOException error){return found;}catch(Exception ignored){}finally{if(connection!=null)connection.disconnect();}
        }
        return found;
    }
    static Info adaptiveInfo(String url,boolean dash){return dash?dashInfo(url):hlsInfo(url);}
    private static String fetchText(String raw,String[] finalUrl)throws Exception{
        HttpURLConnection connection=null;
        try{
            connection=HttpTransport.open(new URL(raw));
            connection.setConnectTimeout(3000);connection.setReadTimeout(6000);
            connection.setRequestMethod("GET");connection.setRequestProperty("Accept-Encoding","identity");connection.setRequestProperty("Referer","https://yani.tv/");
            int code=connection.getResponseCode();if(code!=200)return null;
            if(finalUrl!=null&&finalUrl.length>0)finalUrl[0]=connection.getURL().toString();
            InputStream stream=connection.getInputStream();ByteArrayOutputStream buffer=new ByteArrayOutputStream();byte[] chunk=new byte[16384];int read;long loaded=0;
            while((read=stream.read(chunk))>0){TaskQueue.check();loaded+=read;if(loaded>4L*1024*1024)break;buffer.write(chunk,0,read);}
            return new String(buffer.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);
        }finally{if(connection!=null)connection.disconnect();}
    }
    static Info dashInfo(String url){return new Info(dashBytes(url),"application/dash+xml");}
    private static long dashBytes(String raw){
        String url=ApiRepository.safeUrl(raw);if(url.isEmpty())return -1;
        try{
            TaskQueue.check();
            String[] holder={url};String text=fetchText(url,holder);String base=holder[0];
            if(text==null||text.isEmpty())return -1;
            TaskQueue.check();
            ArrayList<String> files=new ArrayList<>();
            Matcher found=Pattern.compile("<BaseURL>([^<]+)</BaseURL>",Pattern.CASE_INSENSITIVE).matcher(text);
            while(found.find()){
                String value=found.group(1).trim();if(value.isEmpty())continue;
                String lower=value.toLowerCase(Locale.ROOT);if(lower.endsWith(".mpd")||lower.endsWith(".m3u8"))continue;
                String resolved=resolveSegment(base,value);if(resolved.isEmpty())return -1;
                if(!files.contains(resolved))files.add(resolved);
            }
            if(files.isEmpty())return -1;
            long total=measureSegments(files,base,0);
            if(total<0)total=measureSegments(files,"https://yani.tv/",0);
            return total;
        }catch(InterruptedIOException error){return -1;}catch(Exception error){return -1;}
    }
    static Info hlsInfo(String url){return new Info(hlsBytes(url),"application/x-mpegURL");}
    private static final int MAX_SEGMENTS=1800;
    private static long hlsBytes(String raw){
        String url=ApiRepository.safeUrl(raw);if(url.isEmpty())return -1;
        try{
            TaskQueue.check();
            String[] holder={url};String text=fetchText(url,holder);String base=holder[0];
            if(text==null)return -1;
            TaskQueue.check();
            if(text.isEmpty()||text.contains("#EXT-X-STREAM-INF"))return -1;
            long sum=0,pending=-1;ArrayList<String> measure=new ArrayList<>();
            for(String rawLine:text.split("\n")){
                String line=rawLine.trim();if(line.isEmpty())continue;
                if(line.charAt(0)=='#'){
                    if(line.regionMatches(true,0,"#EXT-X-BYTERANGE:",0,17)){pending=rangeLength(line.substring(17));continue;}
                    if(line.regionMatches(true,0,"#EXT-X-MAP:",0,11)){
                        String uri=attribute(line,"URI");if(uri.isEmpty())continue;
                        long length=rangeLength(attribute(line,"BYTERANGE"));
                        if(length>0)sum+=length;
                        else{String resolved=resolveSegment(base,uri);if(resolved.isEmpty())return -1;measure.add(resolved);}
                    }
                    continue;
                }
                if(pending>0){sum+=pending;pending=-1;continue;}
                String resolved=resolveSegment(base,line);
                if(resolved.isEmpty())return -1;
                measure.add(resolved);
            }
            if(measure.isEmpty())return sum>0?sum:-1;
            if(measure.size()>MAX_SEGMENTS)return -1;
            TaskQueue.check();
            long measured=measureSegments(measure,base,sum);
            if(measured<0)measured=measureSegments(measure,"https://yani.tv/",sum);
            return measured;
        }catch(InterruptedIOException error){return -1;}catch(Exception error){return -1;}
    }
    private static long measureSegments(ArrayList<String> segments,String referer,long initial)throws InterruptedIOException{
        try{
            CountDownLatch latch=new CountDownLatch(segments.size());
            AtomicLong total=new AtomicLong(initial);
            AtomicBoolean failed=new AtomicBoolean();
            ArrayList<Call> pendingCalls=new ArrayList<>();
            for(String segment:segments){
                if(failed.get())break;
                Request request=new Request.Builder().url(segment).head().header("Accept-Encoding","identity").header("Referer",referer).build();
                pendingCalls.add(HttpTransport.enqueueSized(request,new Callback(){
                    @Override public void onFailure(Call target,java.io.IOException error){failed.set(true);latch.countDown();}
                    @Override public void onResponse(Call target,Response response){
                        try{
                            if(!response.isSuccessful()){failed.set(true);return;}
                            long value=parseSize(response.header("Content-Length"));
                            if(value<=0){failed.set(true);return;}
                            total.addAndGet(value);
                        }finally{latch.countDown();}
                    }
                }));
            }
            try{
                long deadline=System.currentTimeMillis()+20000L;
                while(!latch.await(250,TimeUnit.MILLISECONDS)){TaskQueue.check();if(System.currentTimeMillis()>deadline)return -1;}
                TaskQueue.check();
            }catch(InterruptedException error){Thread.currentThread().interrupt();return -1;}
            finally{for(Call call:pendingCalls)call.cancel();}
            return failed.get()?-1:(total.get()>0?total.get():-1);
        }catch(InterruptedIOException error){return -1;}catch(Exception error){return -1;}
    }
    private static String attribute(String line,String name){int at=line.indexOf(name+"=\"");if(at<0)return "";int start=at+name.length()+2;int end=line.indexOf('"',start);return end<0?"":line.substring(start,end);}
    private static long rangeLength(String value){if(value==null)return -1;String v=value.trim();if(v.isEmpty())return -1;int at=v.indexOf('@');try{return Long.parseLong((at>=0?v.substring(0,at):v).trim());}catch(NumberFormatException e){return -1;}}
    private static long parseSize(String value){if(value==null)return -1;try{return Long.parseLong(value.trim());}catch(NumberFormatException e){return -1;}}
    private static String resolveSegment(String base,String value){String v=value==null?"":value.trim();if(v.isEmpty())return "";try{if(v.startsWith("//"))v=(base.startsWith("https:")?"https:":"http:")+v;return new URL(new URL(base),v).toString();}catch(Exception e){return "";}}

    static long total(java.util.Collection<Long> sizes){if(sizes==null||sizes.isEmpty())return -1;long sum=0;for(Long size:sizes){if(size==null||size<=0||sum>Long.MAX_VALUE-size)return -1;sum+=size;}return sum;}
    static String label(long bytes){return bytes>0?String.format(Locale.ROOT,"%,d байт",bytes):"Размер неизвестен";}
}
