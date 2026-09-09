package app.yoru.vk;

import android.app.Application;
import android.os.*;
import java.util.concurrent.*;

public final class VkApp extends Application {
    final Handler main=new Handler(Looper.getMainLooper());
    final ThreadPoolExecutor io=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(12),new ThreadPoolExecutor.AbortPolicy());
    final ThreadPoolExecutor images=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(48),new ThreadPoolExecutor.AbortPolicy());
    @Override public void onCreate(){super.onCreate();}
    interface Work<T>{T run() throws Exception;}
    interface Result<T>{void done(T value,Exception error);}
    private static final class Job extends FutureTask<Void>{
        private volatile Thread runner;
        Job(Runnable work){super(work,null);}
        @Override public void run(){runner=Thread.currentThread();try{super.run();}finally{runner=null;}}
        @Override public boolean cancel(boolean interrupt){java.net.HttpURLConnection connection=Api.activeFor(runner);boolean cancelled=super.cancel(interrupt);if(cancelled&&interrupt)Api.abort(connection);return cancelled;}
    }
    <T> Future<?> run(Work<T> work,Result<T> result){
        try{io.purge();Job job=new Job(()->{T data=null;Exception error=null;try{data=work.run();}catch(Exception e){error=e;}if(Thread.currentThread().isInterrupted())return;T value=data;Exception problem=error;main.post(()->result.done(value,problem));});io.execute(job);return job;}
        catch(RejectedExecutionException e){main.post(()->result.done(null,new IllegalStateException("Очередь занята. Повторите через несколько секунд.")));return null;}
    }
}
