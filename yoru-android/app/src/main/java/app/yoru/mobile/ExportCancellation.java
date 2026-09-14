package app.yoru.mobile;

import android.os.CancellationSignal;
import java.io.Closeable;
import java.io.IOException;
import java.io.InterruptedIOException;

final class ExportCancellation {
    final CancellationSignal signal=new CancellationSignal();
    private boolean stopped;
    private Closeable output;
    void attach(Closeable value)throws IOException{
        synchronized(this){if(!stopped){output=value;return;}}
        try{value.close();}catch(IOException ignored){}
        throw new InterruptedIOException();
    }
    synchronized void detach(Closeable value){if(output==value)output=null;}
    synchronized boolean isStopped(){return stopped;}
    void cancel(){
        Closeable current;
        synchronized(this){if(stopped)return;stopped=true;current=output;output=null;}
        Thread close=new Thread(()->{if(current!=null)try{current.close();}catch(IOException ignored){}},"yoru-export-close");
        close.setDaemon(true);close.start();
        Thread interrupt=new Thread(()->{try{signal.cancel();}catch(RuntimeException ignored){}},"yoru-export-cancel");
        interrupt.setDaemon(true);interrupt.start();
    }
}
