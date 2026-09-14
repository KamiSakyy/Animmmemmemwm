package app.yoru.mobile;

import android.app.Activity;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import androidx.core.content.FileProvider;
import androidx.media3.ui.PlayerView;
import java.io.*;

final class NativeFrame {
    private NativeFrame(){}
    static void capture(Activity activity,PlayerView playerView){
        if(activity.isFinishing()||(activity.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE)!=0){Ui.toast(activity,"Защищённое видео нельзя сохранить");return;}
        View surface=playerView.getVideoSurfaceView();if(!(surface instanceof SurfaceView)||surface.getWidth()<=0||surface.getHeight()<=0){Ui.toast(activity,"Видеокадр пока недоступен");return;}
        int width=surface.getWidth(),height=surface.getHeight();double scale=Math.min(1,Math.sqrt(2097152.0/((long)width*height)));
        Bitmap bitmap;
        try{bitmap=Bitmap.createBitmap(Math.max(1,(int)(width*scale)),Math.max(1,(int)(height*scale)),Bitmap.Config.ARGB_8888);}catch(OutOfMemoryError e){Ui.toast(activity,"Недостаточно памяти для кадра");return;}
        try{PixelCopy.request((SurfaceView)surface,bitmap,result->{
            if(result!=PixelCopy.SUCCESS){bitmap.recycle();if(!activity.isFinishing())Ui.toast(activity,"Кадр недоступен или защищён");return;}
            java.util.concurrent.Future<?> save=YoruApp.app().io.submit(()->{Uri uri=null;File file=null;boolean gallery=Build.VERSION.SDK_INT>=29;try{
                String name="YORU-"+System.currentTimeMillis()+".png";ContentResolver resolver=activity.getContentResolver();
                if(gallery){ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,name);values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/YORU");values.put(MediaStore.Images.Media.IS_PENDING,1);uri=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new IOException();try(OutputStream output=resolver.openOutputStream(uri)){if(output==null||!bitmap.compress(Bitmap.CompressFormat.PNG,100,output))throw new IOException();}ContentValues valuesDone=new ContentValues();valuesDone.put(MediaStore.Images.Media.IS_PENDING,0);resolver.update(uri,valuesDone,null,null);}
                else{File folder=new File(activity.getFilesDir(),"captures");if(!folder.exists()&&!folder.mkdirs())throw new IOException();file=new File(folder,name);try(OutputStream output=new FileOutputStream(file)){if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,output))throw new IOException();}uri=FileProvider.getUriForFile(activity,activity.getPackageName()+".files",file);}
                Uri saved=uri;activity.runOnUiThread(()->{if(activity.isFinishing())return;if(gallery)Ui.toast(activity,"Кадр сохранён в Pictures/YORU");else{Intent share=new Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,saved).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);share.setClipData(ClipData.newRawUri("YORU",saved));try{activity.startActivity(Intent.createChooser(share,"Сохранить или отправить кадр"));}catch(Exception e){Ui.toast(activity,"Кадр сохранён в приложении");}}});
            }catch(Exception e){if(gallery&&uri!=null)try{activity.getContentResolver().delete(uri,null,null);}catch(Exception ignored){}if(file!=null)file.delete();activity.runOnUiThread(()->{if(!activity.isFinishing())Ui.toast(activity,"Не удалось сохранить кадр");});}finally{bitmap.recycle();}});if(save.isCancelled())bitmap.recycle();
        },new Handler(Looper.getMainLooper()));}catch(IllegalArgumentException e){bitmap.recycle();Ui.toast(activity,"Видеокадр пока недоступен");}
    }
}
