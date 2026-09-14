package app.yoru.mobile;

import okhttp3.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

final class Network {
    private static final OkHttpClient client=new OkHttpClient.Builder().cache(null).connectTimeout(6500,TimeUnit.MILLISECONDS).readTimeout(12000,TimeUnit.MILLISECONDS).callTimeout(30,TimeUnit.SECONDS).followSslRedirects(false).addNetworkInterceptor(chain->{Request request=chain.request();if(!sameOrigin(chain.call().request().url().url(),request.url().url()))request=request.newBuilder().removeHeader("Cookie").removeHeader("Authorization").removeHeader("Proxy-Authorization").build();return chain.proceed(request);}).build();
    private static final ConcurrentHashMap<AtomicBoolean,Set<Call>> calls=new ConcurrentHashMap<>();
    private Network(){}
    static OkHttpClient media(){return client.newBuilder().callTimeout(0,TimeUnit.MILLISECONDS).followSslRedirects(true).build();}
    static boolean sameOrigin(URL a,URL b){return a.getProtocol().equalsIgnoreCase(b.getProtocol())&&a.getHost().equalsIgnoreCase(b.getHost())&&(a.getPort()<0?a.getDefaultPort():a.getPort())==(b.getPort()<0?b.getDefaultPort():b.getPort());}
    static boolean sensitive(String name){return "Cookie".equalsIgnoreCase(name)||"Authorization".equalsIgnoreCase(name)||"Proxy-Authorization".equalsIgnoreCase(name);}
    static HttpURLConnection open(URL url)throws IOException{return new Connection(url);}
    static void cancel(AtomicBoolean flag){Set<Call> active=calls.remove(flag);if(active!=null)for(Call call:active)call.cancel();}
    private static final class Connection extends HttpURLConnection {
        private final Headers.Builder headers=new Headers.Builder();
        private final ByteArrayOutputStream output=new ByteArrayOutputStream();
        private Response reply;
        private Call call;
        private AtomicBoolean scope;
        private boolean closed;
        Connection(URL url){super(url);setConnectTimeout(6500);setReadTimeout(12000);}
        @Override public void setRequestProperty(String key,String value){if(connected)throw new IllegalStateException("Соединение уже открыто");headers.set(key,value);}
        @Override public void addRequestProperty(String key,String value){if(connected)throw new IllegalStateException("Соединение уже открыто");headers.add(key,value);}
        @Override public String getRequestProperty(String key){return headers.get(key);}
        @Override public Map<String,List<String>> getRequestProperties(){return headers.build().toMultimap();}
        @Override public OutputStream getOutputStream()throws IOException{if(connected||closed)throw new IOException("Соединение закрыто");doOutput=true;return output;}
        @Override public void connect()throws IOException{
            if(reply!=null)return;if(closed)throw new IOException("Соединение закрыто");TaskQueue.check();
            String verb=method;if(doOutput&&verb.equals("GET"))verb="POST";
            RequestBody body=null;
            if(doOutput||verb.equals("POST")||verb.equals("PUT")||verb.equals("PATCH"))body=RequestBody.create(output.toByteArray(),null);
            Request request=new Request.Builder().url(url).headers(headers.build()).method(verb,body).build();
            call=client.newBuilder().connectTimeout(getConnectTimeout(),TimeUnit.MILLISECONDS).readTimeout(getReadTimeout(),TimeUnit.MILLISECONDS).followRedirects(getInstanceFollowRedirects()).build().newCall(request);
            scope=TaskQueue.cancellationFlag();
            if(scope!=null){calls.compute(scope,(k,active)->{if(active==null)active=ConcurrentHashMap.newKeySet();active.add(call);return active;});if(scope.get()){call.cancel();unbind();throw new InterruptedIOException("Cancelled");}}
            try{reply=call.execute();connected=true;responseCode=reply.code();responseMessage=reply.message();url=reply.request().url().url();}catch(IOException e){unbind();throw e;}
        }
        private void unbind(){if(scope!=null&&call!=null){calls.computeIfPresent(scope,(k,active)->{active.remove(call);return active.isEmpty()?null:active;});}}
        @Override public int getResponseCode()throws IOException{connect();return reply.code();}
        @Override public String getResponseMessage()throws IOException{connect();return reply.message();}
        @Override public String getHeaderField(String name){try{connect();return name==null?"HTTP/1.1 "+reply.code()+" "+reply.message():reply.header(name);}catch(IOException e){return null;}}
        @Override public Map<String,List<String>> getHeaderFields(){try{connect();TreeMap<String,List<String>> result=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);result.putAll(reply.headers().toMultimap());return Collections.unmodifiableMap(result);}catch(IOException e){return Collections.emptyMap();}}
        @Override public InputStream getInputStream()throws IOException{connect();if(reply.code()>=400)throw new IOException("HTTP "+reply.code());return stream();}
        @Override public InputStream getErrorStream(){try{connect();return reply.code()>=400?stream():null;}catch(IOException e){return null;}}
        private InputStream stream(){ResponseBody b=reply.body();if(b==null)return new ByteArrayInputStream(new byte[0]);return new FilterInputStream(b.byteStream()){@Override public void close()throws IOException{try{super.close();}finally{unbind();}}};}
        @Override public long getContentLengthLong(){try{connect();String length=reply.header("Content-Length");if(length==null)return -1;try{return Long.parseLong(length);}catch(NumberFormatException e){return -1;}}catch(IOException e){return -1;}}
        @Override public int getContentLength(){long n=getContentLengthLong();return n<0||n>Integer.MAX_VALUE?-1:(int)n;}
        @Override public void disconnect(){closed=true;if(call!=null)call.cancel();if(reply!=null)reply.close();unbind();}
        @Override public boolean usingProxy(){return false;}
    }
}
