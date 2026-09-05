package app.yoru.mobile;

import android.util.Base64;
import org.json.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

final class YoruVpnProfile {
    static final String[] SCHEMES={"vless","vmess","trojan","ss","socks"};
    private YoruVpnProfile(){}
    static JSONArray parseSubscription(String input)throws Exception{String text=input==null?"":input.trim();ArrayList<String> links=new ArrayList<>();collectLinks(text,links);if(links.isEmpty()){String decoded=decodeBase64(text);if(decoded!=null)collectLinks(decoded,links);}JSONArray out=new JSONArray();HashSet<String> seen=new HashSet<>();for(String link:links){String clean=cleanup(link);String key=clean.toLowerCase(Locale.ROOT);if(clean.isEmpty()||!seen.add(key))continue;JSONObject p=profile(clean);if(p!=null)out.put(p);}return out;}
    static JSONObject profile(String raw){try{String scheme=scheme(raw);if(!supported(scheme))return null;Parts parts=scheme.equals("vmess")?vmessParts(raw):standard(raw,scheme);String name=nameOf(raw,scheme,parts);return new JSONObject().put("id",id(raw)).put("name",name).put("scheme",scheme).put("host",parts.host).put("port",parts.port).put("raw",raw);}catch(Exception e){return null;}}
    static String label(JSONObject p){String name=p.optString("name");String scheme=p.optString("scheme").toUpperCase(Locale.ROOT);String host=p.optString("host");int port=p.optInt("port",0);String tail=host.isEmpty()?scheme:scheme+" · "+host+(port>0?":"+port:"");return name.isEmpty()?tail:name+"\n"+tail;}
    static JSONObject selected(JSONArray list,String id){if(list==null||list.length()==0)return null;for(int i=0;i<list.length();i++){JSONObject p=list.optJSONObject(i);if(p!=null&&p.optString("id").equals(id))return p;}return list.optJSONObject(0);}
    static String decode(String value){try{return URLDecoder.decode(value==null?"":value,"UTF-8");}catch(Exception e){return value==null?"":value;}}
    static Map<String,String> query(String q){LinkedHashMap<String,String> map=new LinkedHashMap<>();if(q==null)return map;for(String token:q.split("&")){if(token.isEmpty())continue;String k=token.contains("=")?token.substring(0,token.indexOf('=')):token;String v=token.contains("=")?token.substring(token.indexOf('=')+1):"";map.put(decode(k).toLowerCase(Locale.ROOT),decode(v));}return map;}
    static Parts standard(String raw,String scheme){String body=raw.substring((scheme+"://").length());String main=body.contains("#")?body.substring(0,body.indexOf('#')):body;String frag=body.contains("#")?body.substring(body.indexOf('#')+1):"";String query="";int qi=main.indexOf('?');if(qi>=0){query=main.substring(qi+1);main=main.substring(0,qi);}String path="";int slash=main.indexOf('/');if(slash>=0){path=main.substring(slash);main=main.substring(0,slash);}String user="";String hostPort=main;int at=main.lastIndexOf('@');if(at>=0){user=main.substring(0,at);hostPort=main.substring(at+1);}String host=hostPort;int port=scheme.equals("socks")?1080:443;if(hostPort.startsWith("[")){int end=hostPort.indexOf(']');if(end>0){host=hostPort.substring(1,end);String rest=hostPort.substring(end+1);if(rest.startsWith(":"))port=parseInt(rest.substring(1),port);}}else{int colon=hostPort.lastIndexOf(':');if(colon>0&&colon<hostPort.length()-1){host=hostPort.substring(0,colon);port=parseInt(hostPort.substring(colon+1),port);}}return new Parts(decode(user),decode(host),port,query,decode(frag),decode(path));}
    static Parts vmessParts(String raw)throws Exception{String tmp=raw.substring("vmess://".length());String payload=(tmp.contains("#")?tmp.substring(0,tmp.indexOf('#')):tmp).trim();String decoded=decodeBase64(payload);if(decoded==null)throw new Exception();JSONObject o=new JSONObject(decoded);String host=o.optString("add");int port=parseInt(o.optString("port"),443);String name=o.optString("ps");return new Parts(o.optString("id"),host,port,"",name,"");}
    static String decodeBase64(String data){String out=decodeBase64Any(data);return out!=null&&(out.contains("://")||out.trim().startsWith("{"))?out:null;}
    static String decodeBase64Any(String data){try{String s=data.replace('\n',' ').replace('\r',' ').trim().replace(" ","");int pad=(4-s.length()%4)%4;for(int i=0;i<pad;i++)s+="=";byte[] b=Base64.decode(s,Base64.DEFAULT|Base64.URL_SAFE);return new String(b,StandardCharsets.UTF_8);}catch(Exception e){try{String s=data.trim();int pad=(4-s.length()%4)%4;for(int i=0;i<pad;i++)s+="=";byte[] b=Base64.decode(s,Base64.DEFAULT);return new String(b,StandardCharsets.UTF_8);}catch(Exception ignored){return null;}}}
    private static void collectLinks(String text,ArrayList<String> out){String[] raw=text.split("[\\r\\n\\t ]+");for(String line:raw){String v=line.trim();if(v.isEmpty())continue;for(String scheme:SCHEMES)if(v.toLowerCase(Locale.ROOT).startsWith(scheme+"://")){out.add(v);break;}}}
    private static String cleanup(String s){int cut=s.indexOf('\u0000');if(cut>=0)s=s.substring(0,cut);return s.trim();}
    private static boolean supported(String s){for(String x:SCHEMES)if(x.equals(s))return true;return false;}
    private static String scheme(String raw){int i=raw.indexOf("://");return i<1?"":raw.substring(0,i).toLowerCase(Locale.ROOT);}
    private static String nameOf(String raw,String scheme,Parts parts){if(scheme.equals("vmess"))return parts.fragment.isEmpty()?parts.host+":"+parts.port:parts.fragment;if(!parts.fragment.isEmpty())return parts.fragment;return parts.host+":"+parts.port;}
    private static int parseInt(String s,int def){try{return Integer.parseInt(s.trim());}catch(Exception e){return def;}}
    private static String id(String raw)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");byte[] b=md.digest(raw.getBytes(StandardCharsets.UTF_8));StringBuilder sb=new StringBuilder();for(int i=0;i<8;i++)sb.append(String.format(Locale.ROOT,"%02x",b[i]));return sb.toString();}
    static final class Parts {final String user,host,query,fragment,path;final int port;Parts(String user,String host,int port,String query,String fragment,String path){this.user=user;this.host=host;this.port=port;this.query=query;this.fragment=fragment;this.path=path;}}
}
