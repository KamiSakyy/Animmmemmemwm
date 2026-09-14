package app.yoru.mobile;

import java.net.*;
import java.util.Locale;
import java.util.regex.*;

final class MediaSize {
    private MediaSize(){}
    static long rangeTotal(String header){if(header==null)return -1;Matcher match=Pattern.compile("(?i)^bytes\\s+0-0/([0-9]+)$").matcher(header.trim());if(!match.matches())return -1;try{long size=Long.parseLong(match.group(1));return size>0?size:-1;}catch(NumberFormatException e){return -1;}}
    static boolean singleFile(String url){try{if(url.toLowerCase(Locale.ROOT).contains(".m3u8")||url.toLowerCase(Locale.ROOT).contains(".mpd"))return false;String path=new URL(url).getPath().toLowerCase(Locale.ROOT);return path.endsWith(".mp4")||path.endsWith(".webm")||path.endsWith(".mkv")||path.endsWith(".m4v");}catch(Exception e){return false;}}
    static long probe(String raw){String url=ApiRepository.safeUrl(raw);if(!singleFile(url))return -1;
        for(int attempt=0;attempt<2;attempt++){HttpURLConnection connection=null;try{TaskQueue.check();connection=HttpTransport.open(new URL(url));connection.setConnectTimeout(2500);connection.setReadTimeout(2500);connection.setRequestMethod(attempt==0?"HEAD":"GET");connection.setRequestProperty("Accept-Encoding","identity");connection.setRequestProperty("Referer","https://yani.tv/");if(attempt==1)connection.setRequestProperty("Range","bytes=0-0");int code=connection.getResponseCode();if(!singleFile(connection.getURL().toString()))return -1;String encoding=connection.getContentEncoding();if(encoding!=null&&!encoding.equalsIgnoreCase("identity"))return -1;String type=connection.getContentType();if(type!=null&&(type.toLowerCase(Locale.ROOT).contains("mpegurl")||type.toLowerCase(Locale.ROOT).contains("dash")||type.toLowerCase(Locale.ROOT).contains("text/")))return -1;if(attempt==0&&code==200){long size=connection.getContentLengthLong();if(size>0)return size;}if(attempt==1&&code==206)return rangeTotal(connection.getHeaderField("Content-Range"));}catch(Exception ignored){}finally{if(connection!=null)connection.disconnect();}}
        return -1;
    }
    static long total(java.util.Collection<Long> sizes){if(sizes==null||sizes.isEmpty())return -1;long sum=0;for(Long size:sizes){if(size==null||size<=0||sum>Long.MAX_VALUE-size)return -1;sum+=size;}return sum;}
    static String label(long bytes){return bytes>0?String.format(Locale.ROOT,"%,d байт",bytes):"Размер неизвестен";}
}
