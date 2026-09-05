package app.yoru.mobile;

import android.content.Context;
import java.io.*;
import java.lang.reflect.*;

final class YoruVpnCore {
    private static boolean initialized;
    private YoruVpnCore(){}
    static boolean available(){try{Class.forName("libv2ray.Libv2ray");return true;}catch(Throwable e){return false;}}
    static String version(){try{return String.valueOf(Class.forName("libv2ray.Libv2ray").getMethod("checkVersionX").invoke(null));}catch(Throwable e){return "ядро не найдено";}}
    static synchronized Object start(Context c,String config,int tunFd)throws Exception{Class<?> lib=Class.forName("libv2ray.Libv2ray");Class<?> cb=Class.forName("libv2ray.CoreCallbackHandler");ensureEnv(c,lib);Object handler=Proxy.newProxyInstance(cb.getClassLoader(),new Class[]{cb},(proxy,method,args)->{if(method.getName().equals("onEmitStatus")&&args!=null&&args.length>=2)YoruVpnService.status("Xray: "+String.valueOf(args[1]));Class<?> r=method.getReturnType();if(r==Long.TYPE)return 0L;if(r==Integer.TYPE)return 0;if(r==Boolean.TYPE)return false;return null;});Object controller=lib.getMethod("newCoreController",cb).invoke(null,handler);controller.getClass().getMethod("startLoop",String.class,Integer.TYPE).invoke(controller,config,tunFd);if(!running(controller))throw new IOException("core stopped");return controller;}
    static synchronized void stop(Object controller){try{if(controller!=null)controller.getClass().getMethod("stopLoop").invoke(controller);}catch(Throwable ignored){}}
    private static boolean running(Object controller){try{Object r=controller.getClass().getMethod("getIsRunning").invoke(controller);return Boolean.TRUE.equals(r);}catch(Throwable e){return true;}}
    private static void ensureEnv(Context c,Class<?> lib)throws Exception{if(initialized)return;File dir=new File(c.getFilesDir(),"xray-assets");if(!dir.exists()&&!dir.mkdirs())throw new IOException("assets dir");copy(c,"geoip.dat",new File(dir,"geoip.dat"));copy(c,"geosite.dat",new File(dir,"geosite.dat"));File geo=new File(dir,"geoip.dat");if(!geo.exists()||geo.length()==0)copy(c,"geoip-only-cn-private.dat",geo);lib.getMethod("initCoreEnv",String.class,String.class).invoke(null,dir.getAbsolutePath(),"");initialized=true;}
    private static void copy(Context c,String asset,File out){if(out.exists()&&out.length()>0)return;try(InputStream in=c.getAssets().open(asset);OutputStream dst=new BufferedOutputStream(new FileOutputStream(out))){byte[] b=new byte[256*1024];int n;while((n=in.read(b))!=-1)dst.write(b,0,n);}catch(Exception ignored){}}
}
