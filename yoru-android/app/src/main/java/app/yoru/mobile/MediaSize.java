package app.yoru.mobile;

import java.net.*;
import java.util.Locale;
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
    static long probe(String raw){return probeInfo(raw).bytes;}
    static Info probeInfo(String raw){
        String url=ApiRepository.safeUrl(raw);Info unknown=new Info(-1,"");if(url.isEmpty())return unknown;if(manifest(url))return new Info(-1,url.toLowerCase(Locale.ROOT).contains(".mpd")?"application/dash+xml":"application/x-mpegURL");
        Info found=unknown;
        for(int attempt=0;attempt<2;attempt++){
            HttpURLConnection connection=null;
            try{
                TaskQueue.check();connection=HttpTransport.open(new URL(url));connection.setConnectTimeout(2500);connection.setReadTimeout(2500);connection.setRequestMethod(attempt==0?"HEAD":"GET");connection.setRequestProperty("Accept-Encoding","identity");connection.setRequestProperty("Referer","https://yani.tv/");if(attempt==1)connection.setRequestProperty("Range","bytes=0-0");
                int code=connection.getResponseCode();if(attempt==0&&code!=200)continue;if(code!=200&&code!=206)return found;
                String finalUrl=connection.getURL().toString();if(manifest(finalUrl))return new Info(-1,finalUrl.toLowerCase(Locale.ROOT).contains(".mpd")?"application/dash+xml":"application/x-mpegURL");String encoding=connection.getContentEncoding();if(encoding!=null&&!encoding.equalsIgnoreCase("identity"))continue;
                String type=connection.getContentType();String mime=type==null?"":type.toLowerCase(Locale.ROOT).split(";",2)[0].trim();if(mime.contains("mpegurl"))return new Info(-1,"application/x-mpegURL");if(mime.equals("application/dash+xml"))return new Info(-1,"application/dash+xml");boolean container=containerMime(mime);
                if(!container&&(!singleFile(finalUrl)||(!mime.isEmpty()&&!mime.startsWith("video/")&&!mime.equals("application/octet-stream"))))continue;
                String format=container?("application/mp4".equals(mime)?"video/mp4":mime):"";found=new Info(-1,format);
                if(code==206)return new Info(rangeTotal(connection.getHeaderField("Content-Range")),format);
                long size=connection.getContentLengthLong();if(size>0&&connection.getHeaderField("Content-Range")==null)return new Info(size,format);
            }catch(java.net.SocketTimeoutException ignored){}catch(java.io.InterruptedIOException error){return found;}catch(Exception ignored){}finally{if(connection!=null)connection.disconnect();}
        }
        return found;
    }
    static long total(java.util.Collection<Long> sizes){if(sizes==null||sizes.isEmpty())return -1;long sum=0;for(Long size:sizes){if(size==null||size<=0||sum>Long.MAX_VALUE-size)return -1;sum+=size;}return sum;}
    static String label(long bytes){return bytes>0?String.format(Locale.ROOT,"%,d байт",bytes):"Размер неизвестен";}
}
