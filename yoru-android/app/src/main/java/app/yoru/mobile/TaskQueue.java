package app.yoru.mobile;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

final class TaskQueue {
    private TaskQueue() {}

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
            FutureTask<Void> task = new FutureTask<Void>(() -> {
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
        FutureTask<Void> task=new FutureTask<>(()->{if(!signal.get())work.run();return null;});
        signal.bind(task);executor.execute(task);
        if(task.isCancelled()&&!signal.get()&&rejected!=null)rejected.run();
    }

    static void check() throws java.io.InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Cancelled");
    }
}
