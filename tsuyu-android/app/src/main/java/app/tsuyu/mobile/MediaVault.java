package app.tsuyu.mobile;

import java.util.concurrent.ConcurrentHashMap;

public final class MediaVault {
    private static final ConcurrentHashMap<String,MediaTools.Pack> map=new ConcurrentHashMap<>();
    private MediaVault(){}
    public static String put(MediaTools.Pack p){String id="m"+System.nanoTime();map.put(id,p);return id;}
    public static MediaTools.Pack get(String id){return map.get(id);} 
}
