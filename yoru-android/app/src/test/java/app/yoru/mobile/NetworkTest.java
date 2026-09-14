package app.yoru.mobile;

import org.junit.Test;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class NetworkTest {
    @Test public void originIncludesSchemeAndPort()throws Exception{
        assertTrue(Network.sameOrigin(new URL("https://example.com/a"),new URL("https://example.com:443/b")));
        assertFalse(Network.sameOrigin(new URL("https://example.com"),new URL("http://example.com")));
        assertFalse(Network.sameOrigin(new URL("https://example.com"),new URL("https://other.example")));
        assertFalse(Network.sameOrigin(new URL("https://example.com"),new URL("https://example.com:444")));
    }
    @Test public void headersAreCaseInsensitiveAndBodyIsReadable()throws Exception{
        ExecutorService server=Executors.newSingleThreadExecutor();
        try(ServerSocket socket=new ServerSocket(0)){
            Future<?> response=server.submit(()->{try(Socket peer=socket.accept()){
                BufferedReader reader=new BufferedReader(new InputStreamReader(peer.getInputStream(),StandardCharsets.US_ASCII));
                while(true){String line=reader.readLine();if(line==null||line.isEmpty())break;}
                peer.getOutputStream().write("HTTP/1.1 200 OK\r\ncontent-length: 2\r\nset-cookie: test=yes\r\nConnection: close\r\n\r\nOK".getBytes(StandardCharsets.US_ASCII));
            }catch(IOException e){throw new UncheckedIOException(e);}});
            HttpURLConnection connection=Network.open(new URL("http://127.0.0.1:"+socket.getLocalPort()+"/"));
            try{assertEquals(200,connection.getResponseCode());assertEquals("test=yes",connection.getHeaderFields().get("Set-Cookie").get(0));try(InputStream input=connection.getInputStream()){assertEquals('O',input.read());assertEquals('K',input.read());assertEquals(-1,input.read());}}finally{connection.disconnect();}
            response.get(3,TimeUnit.SECONDS);
        }finally{server.shutdownNow();}
    }
    @Test public void taskCancellationClosesBlockedCall()throws Exception{
        ExecutorService workers=Executors.newFixedThreadPool(2);
        CountDownLatch accepted=new CountDownLatch(1),stopped=new CountDownLatch(1),release=new CountDownLatch(1);
        try(ServerSocket socket=new ServerSocket(0)){
            workers.submit(()->{try(Socket peer=socket.accept()){accepted.countDown();release.await(5,TimeUnit.SECONDS);}catch(Exception ignored){}});
            TaskQueue.CancelFuture<Void> task=new TaskQueue.CancelFuture<>(()->{
                HttpURLConnection connection=Network.open(new URL("http://127.0.0.1:"+socket.getLocalPort()+"/"));
                try{connection.getResponseCode();fail("Cancelled response completed");}catch(IOException expected){}finally{connection.disconnect();stopped.countDown();}return null;
            });
            workers.execute(task);assertTrue(accepted.await(3,TimeUnit.SECONDS));assertTrue(task.cancel(false));assertTrue(stopped.await(3,TimeUnit.SECONDS));
        }finally{release.countDown();workers.shutdownNow();}
    }
}
