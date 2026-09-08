package app.yoru.vk;

import java.net.*;
import java.util.*;
import java.util.regex.*;

public final class WebRoutes {
    public static final String[] LABELS={"VK Видео · версия для ПК","VK Видео · мобильная версия","Яндекс · публичные страницы ВК","Google · публичные страницы ВК"};
    private WebRoutes(){}
    private static String enc(String s){try{return URLEncoder.encode(s,"UTF-8");}catch(Exception e){throw bad();}}
    private static String dec(String s){try{return URLDecoder.decode(s,"UTF-8");}catch(Exception e){throw bad();}}
    public static String search(int method,String query){
        String q=query==null?"":query.trim();if(q.isEmpty()||q.length()>250)throw new IllegalArgumentException("Введите запрос длиной до 250 символов.");
        switch(method){
            case 0:return "https://vkvideo.ru/?q="+enc(q);
            case 1:return "https://m.vkvideo.ru/?q="+enc(q)+"&action=search";
            case 2:return "https://yandex.ru/search/?text="+enc("(site:vkvideo.ru/video OR site:vk.com/video) "+q);
            case 3:return "https://www.google.com/search?q="+enc("(site:vkvideo.ru/video OR site:vk.com/video) "+q);
            default:throw bad();
        }
    }
    public static String modeUrl(String url,boolean desktop){try{URI u=uri(url);if(within(u.getHost().toLowerCase(Locale.ROOT),"vkvideo.ru")&&("/".equals(u.getPath())||"/video".equals(u.getPath()))){String q=params(u.getRawQuery()).get("q");if(q!=null&&!q.isEmpty())return search(desktop?0:1,q);}}catch(Exception ignored){}return url;}
    private static URI uri(String s){try{if(s==null||s.length()>8192)throw bad();URI u=new URI(s);if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||(u.getPort()!=-1&&u.getPort()!=443))throw bad();return u;}catch(Exception e){throw bad();}}
    private static boolean within(String h,String base){return h.equals(base)||h.endsWith("."+base);}
    public static boolean vkHost(String h){if(h==null)return false;h=h.toLowerCase(Locale.ROOT);return within(h,"vk.com")||within(h,"vk.ru")||within(h,"vkvideo.ru");}
    public static boolean allowedPage(String address){
        try{String h=uri(address).getHost().toLowerCase(Locale.ROOT);return vkHost(h)||within(h,"yandex.ru")||within(h,"yandex.com")||h.equals("ya.ru")||h.equals("www.google.com")||h.equals("google.com")||h.equals("consent.google.com")||h.equals("www.google.ee")||h.equals("google.ee");}catch(Exception e){return false;}
    }
    public static String origin(String address){try{return uri(address).getHost().toLowerCase(Locale.ROOT);}catch(Exception e){return "неподдерживаемый адрес";}}
    private static Map<String,String> params(String raw){Map<String,String> p=new LinkedHashMap<>();if(raw==null)return p;for(String pair:raw.split("&")){int i=pair.indexOf('=');if(i<0)continue;String key=dec(pair.substring(0,i));if(p.containsKey(key))throw bad();p.put(key,dec(pair.substring(i+1)));}return p;}
    public static String videoLink(String input){
        if(input==null||input.length()>8192)throw bad();String value=input.trim();Matcher extract=Pattern.compile("https://[^\\s<>\"]+").matcher(value);if(extract.find())value=extract.group().replaceAll("[).,!]+$","");else if(value.startsWith("vkvideo.ru/")||value.startsWith("vk.com/")||value.startsWith("m.vk.com/"))value="https://"+value;
        URI u=uri(value);if(!vkHost(u.getHost()))throw bad();if(dec(value).toLowerCase(Locale.ROOT).contains("access_token"))throw new IllegalArgumentException("Токен не нужен. Вставьте ссылку на видео, а не ссылку авторизации.");
        Map<String,String> args=params(u.getRawQuery());String path=u.getPath();Matcher direct=Pattern.compile("^/(?:video|clip)(-?[1-9][0-9]{0,18})_([1-9][0-9]{0,18})/?$").matcher(path);
        String key=null;if(direct.matches())key=direct.group(1)+"_"+direct.group(2);
        if(key==null&&args.containsKey("z")){Matcher z=Pattern.compile("^(?:video)(-?[1-9][0-9]{0,18}_[1-9][0-9]{0,18})(?:/.*)?$").matcher(args.get("z"));if(z.matches())key=z.group(1);}
        if(key!=null){String list=args.get("list");return "https://vkvideo.ru/video"+key+(list!=null&&list.matches("[A-Za-z0-9_-]{1,256}")?"?list="+enc(list):"");}
        if(path.equals("/video_ext.php")){String owner=args.get("oid"),id=args.get("id");if(owner==null||id==null||!owner.matches("-?[1-9][0-9]{0,18}")||!id.matches("[1-9][0-9]{0,18}"))throw bad();String hash=args.get("hash");return "https://vk.com/video_ext.php?oid="+owner+"&id="+id+(hash!=null&&hash.matches("[A-Za-z0-9_-]{1,128}")?"&hash="+enc(hash):"");}
        throw bad();
    }
    public static String embed(String video){URI u=uri(videoLink(video));if(u.getPath().equals("/video_ext.php"))return u.toString();Matcher m=Pattern.compile("/video(-?[0-9]+)_([0-9]+)").matcher(u.getPath());if(!m.matches())throw bad();return "https://vk.com/video_ext.php?oid="+m.group(1)+"&id="+m.group(2);}
    private static IllegalArgumentException bad(){return new IllegalArgumentException("Нужна HTTPS-ссылка на видео VK: vkvideo.ru/video… или vk.com/video… . Короткие ссылки, токены и посторонние сайты не принимаются.");}
}
