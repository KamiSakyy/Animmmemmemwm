package app.yoru.vk;

import android.app.Application;
import android.os.*;
import java.util.concurrent.*;

public final class VkApp extends Application {
    TokenVault vault;
    final Handler main=new Handler(Looper.getMainLooper());
    final ThreadPoolExecutor io=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(12),new ThreadPoolExecutor.AbortPolicy());
    final ThreadPoolExecutor images=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(48),new ThreadPoolExecutor.AbortPolicy());
    @Override public void onCreate(){super.onCreate();vault=new TokenVault(this);}
    interface Work<T>{T run() throws Exception;}
    interface Result<T>{void done(T value,Exception error);}
    <T> Future<?> run(Work<T> work,Result<T> result){
        try{return io.submit(()->{T data=null;Exception error=null;try{data=work.run();}catch(Exception e){error=e;}if(Thread.currentThread().isInterrupted())return;T value=data;Exception problem=error;main.post(()->result.done(value,problem));});}
        catch(RejectedExecutionException e){main.post(()->result.done(null,new IllegalStateException("Очередь занята. Повторите через несколько секунд.")));return null;}
    }
}
