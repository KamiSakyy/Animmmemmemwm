package app.yoru.mobile;

import android.app.Activity;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import androidx.core.content.FileProvider;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;
import java.io.*;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;

final class NativeFrame {
    private static final AtomicBoolean capturing=new AtomicBoolean();
    private NativeFrame(){}
    private static boolean alive(Activity activity){return !activity.isFinishing()&&!activity.isDestroyed();}
    static void capture(Activity activity,PlayerView playerView){
        if(!alive(activity))return;
        if((activity.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE)!=0){Ui.toast(activity,"Защищённое видео нельзя сохранить");return;}
        Player player=playerView.getPlayer();View surface=playerView.getVideoSurfaceView();
        if(player==null||!player.getCurrentTracks().isTypeSelected(C.TRACK_TYPE_VIDEO)||(player.getPlaybackState()!=Player.STATE_READY&&player.getPlaybackState()!=Player.STATE_ENDED)||!(surface instanceof SurfaceView)||surface.getWidth()<=0||surface.getHeight()<=0){Ui.toast(activity,"Видеокадр пока недоступен");return;}
        if(!capturing.compareAndSet(false,true))return;
        int width=surface.getWidth(),height=surface.getHeight();double scale=Math.min(1,Math.sqrt(2097152.0/((long)width*height)));
        Bitmap bitmap;
        try{bitmap=Bitmap.createBitmap(Math.max(1,(int)(width*scale)),Math.max(1,(int)(height*scale)),Bitmap.Config.ARGB_8888);}catch(OutOfMemoryError error){capturing.set(false);Ui.toast(activity,"Недостаточно памяти для кадра");return;}
        try{
            PixelCopy.request((SurfaceView)surface,bitmap,result->{
                if(result!=PixelCopy.SUCCESS){release(bitmap);if(alive(activity))Ui.toast(activity,"Кадр недоступен или защищён");return;}
                AtomicBoolean claimed=new AtomicBoolean();
                FutureTask<Void> task=new FutureTask<Void>(()->{
                    if(!claimed.compareAndSet(false,true))return null;
                    try{save(activity,bitmap);}finally{release(bitmap);}return null;
                }){
                    @Override protected void done(){if(isCancelled()&&claimed.compareAndSet(false,true))release(bitmap);}
                };
                try{YoruApp.app().io.execute(task);}catch(RuntimeException error){task.cancel(false);}
                if(task.isCancelled()&&alive(activity))Ui.toast(activity,"Очередь занята. Повторите снимок позже.");
            },new Handler(Looper.getMainLooper()));
        }catch(IllegalArgumentException error){release(bitmap);Ui.toast(activity,"Видеокадр пока недоступен");}
    }
    private static void release(Bitmap bitmap){bitmap.recycle();capturing.set(false);}
    private static void save(Activity activity,Bitmap bitmap){
        Uri uri=null;File file=null;boolean gallery=Build.VERSION.SDK_INT>=29;
        try{
            TaskQueue.check();String name="YORU-"+System.currentTimeMillis()+".png";ContentResolver resolver=activity.getApplicationContext().getContentResolver();
            if(gallery){
                ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,name);values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/YORU");values.put(MediaStore.Images.Media.IS_PENDING,1);
                uri=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new IOException();
                try(OutputStream output=resolver.openOutputStream(uri)){if(output==null||!bitmap.compress(Bitmap.CompressFormat.PNG,100,output))throw new IOException();}
                TaskQueue.check();ContentValues done=new ContentValues();done.put(MediaStore.Images.Media.IS_PENDING,0);if(resolver.update(uri,done,null,null)<=0)throw new IOException();
            }else{
                File folder=new File(activity.getFilesDir(),"captures");if(!folder.exists()&&!folder.mkdirs())throw new IOException();file=new File(folder,name);
                try(OutputStream output=new FileOutputStream(file)){if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,output))throw new IOException();}
                TaskQueue.check();uri=FileProvider.getUriForFile(activity,activity.getPackageName()+".files",file);
            }
            Uri saved=uri;activity.runOnUiThread(()->{
                if(!alive(activity))return;
                if(gallery)Ui.toast(activity,"Кадр сохранён в галерею");
                else{
                    Intent share=new Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,saved).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);share.setClipData(ClipData.newRawUri("YORU",saved));
                    try{activity.startActivity(Intent.createChooser(share,"Сохранить или отправить кадр"));}catch(Exception error){Ui.toast(activity,"Кадр сохранён в приложении");}
                }
            });
        }catch(Exception error){
            if(gallery&&uri!=null)try{activity.getContentResolver().delete(uri,null,null);}catch(Exception ignored){}
            if(file!=null)file.delete();activity.runOnUiThread(()->{if(alive(activity))Ui.toast(activity,"Не удалось сохранить кадр");});
        }
    }
}
