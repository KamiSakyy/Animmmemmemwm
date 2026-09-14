package app.yoru.mobile;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

final class TaskQueue {
    private TaskQueue() {}
    private static final ThreadLocal<AtomicBoolean> cancellation=new ThreadLocal<>();
    private static final ConcurrentHashMap<Thread,AtomicBoolean> runningFlags=new ConcurrentHashMap<>();
    static void attach(AtomicBoolean value){cancellation.set(value);runningFlags.put(Thread.currentThread(),value);}
    static AtomicBoolean cancellationFlag(){return cancellation.get();}
    static void detach(){runningFlags.remove(Thread.currentThread());cancellation.remove();}

    static final class CancelFuture<V> extends FutureTask<V> {
        private final AtomicBoolean cancelled;
        CancelFuture(Callable<V> work){this(new AtomicBoolean(),work);}
        private CancelFuture(AtomicBoolean flag,Callable<V> work){
            super(()->{attach(flag);try{check();return work.call();}finally{detach();}});
            cancelled=flag;
        }
        @Override public boolean cancel(boolean interrupt){cancelled.set(true);HttpTransport.cancel(cancelled);return super.cancel(interrupt);}
    }

    static final class Executor extends ThreadPoolExecutor {
        private final java.util.Set<Thread> active=ConcurrentHashMap.newKeySet();
        @Override protected void beforeExecute(Thread thread,Runnable work){super.beforeExecute(thread,work);active.add(thread);}
        @Override protected void afterExecute(Runnable work,Throwable error){active.remove(Thread.currentThread());super.afterExecute(work,error);}
        @Override public java.util.List<Runnable> shutdownNow(){for(Thread thread:active){AtomicBoolean flag=runningFlags.get(thread);if(flag!=null){flag.set(true);HttpTransport.cancel(flag);}}return super.shutdownNow();}
        Executor(int core,int max,long timeout,TimeUnit unit,BlockingQueue<Runnable> queue,ThreadFactory factory,RejectedExecutionHandler policy){super(core,max,timeout,unit,queue,factory,policy);}
        @Override protected <T> RunnableFuture<T> newTaskFor(Callable<T> work){return new CancelFuture<>(work);}
        @Override protected <T> RunnableFuture<T> newTaskFor(Runnable work,T value){return new CancelFuture<>(Executors.callable(work,value));}
    }


    static ExecutorService pool(int count){int size=Math.max(1,Math.min(8,count));return new Executor(size,size,15,TimeUnit.SECONDS,new LinkedBlockingQueue<>(),Executors.defaultThreadFactory(),new ThreadPoolExecutor.AbortPolicy());}

    static final class Policy implements RejectedExecutionHandler {
        @Override public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
            executor.purge();
            if (!executor.isShutdown() && executor.getQueue().offer(task)) return;
            if (task instanceof Future<?>) ((Future<?>) task).cancel(false);
            YoruApp app = YoruApp.app();
            if (app != null) app.main.post(() -> Ui.toast(app, "Очередь занята. Повторите действие чуть позже."));
        }
    }

    static final class Scope {
        private final java.util.ArrayList<Future<?>> tasks = new java.util.ArrayList<>();
        synchronized Future<?> submit(ExecutorService executor, Runnable work, Runnable rejected) {
            tasks.removeIf(Future::isDone);
            AtomicBoolean started = new AtomicBoolean(false);
            FutureTask<Void> task = new CancelFuture<Void>(() -> {
                started.set(true);
                if (!Thread.currentThread().isInterrupted()) work.run();
                return null;
            });
            tasks.add(task);
            executor.execute(task);
            if (task.isCancelled() && !started.get() && rejected != null) rejected.run();
            return task;
        }
        synchronized void cancel() {
            for (Future<?> task : tasks) task.cancel(true);
            tasks.clear();
        }
    }

    static final class Signal {
        private final AtomicBoolean cancelled=new AtomicBoolean();
        private Future<?> future;
        boolean get(){return cancelled.get();}
        synchronized void set(boolean value){cancelled.set(value);if(value&&future!=null)future.cancel(true);}
        synchronized void bind(Future<?> task){future=task;if(cancelled.get())task.cancel(true);}
    }

    static void run(ExecutorService executor,Signal signal,Runnable work,Runnable rejected) {
        FutureTask<Void> task=new CancelFuture<>(()->{if(!signal.get())work.run();return null;});
        signal.bind(task);executor.execute(task);
        if(task.isCancelled()&&!signal.get()&&rejected!=null)rejected.run();
    }

    static void check() throws java.io.InterruptedIOException {
        AtomicBoolean flag=cancellation.get();
        if (Thread.currentThread().isInterrupted() || (flag!=null&&flag.get())) throw new java.io.InterruptedIOException("Cancelled");
    }
}
