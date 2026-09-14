package app.yoru.mobile;

import android.app.Activity;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import androidx.media3.ui.PlayerView;
import java.io.File;

final class WallpaperStyle {
    private WallpaperStyle(){}
    static void apply(Activity activity,View root){
        if(root.getTag(R.id.wallpaper_style_listener)==null){ViewTreeObserver.OnGlobalLayoutListener listener=()->refreshSurfaces(root);root.setTag(R.id.wallpaper_style_listener,listener);root.getViewTreeObserver().addOnGlobalLayoutListener(listener);}
        SecureStore store=YoruApp.app().store;long version=store.wallpaperVersion();
        boolean enabled=activity instanceof WallpaperPreviewActivity?((WallpaperPreviewActivity)activity).previewEnabled():store.wallpaperEnabled();
        if(!enabled){root.setBackgroundColor(Ui.BG);surfaces(root,255);return;}
        refreshSurfaces(root);
        YoruApp.app().io.submit(()->{Bitmap image=null;try{BitmapFactory.Options options=new BitmapFactory.Options();options.inPreferredConfig=Bitmap.Config.ARGB_8888;File file=activity instanceof WallpaperPreviewActivity?((WallpaperPreviewActivity)activity).previewFile():new File(activity.getFilesDir(),"wallpaper.png");if(file!=null)image=BitmapFactory.decodeFile(file.getAbsolutePath(),options);}catch(Exception|OutOfMemoryError ignored){}Bitmap ready=image;
            activity.runOnUiThread(()->{if(activity.isFinishing()||activity.isDestroyed()||version!=store.wallpaperVersion()){if(ready!=null)ready.recycle();return;}if(ready!=null){root.setBackground(new Background(ready));refreshSurfaces(root);}});
        });
    }
    static void refreshSurfaces(View root){SecureStore store=YoruApp.app().store;boolean enabled=store.wallpaperEnabled(),transparent=store.wallpaperTransparent();int opacity=store.wallpaperTransparency();if(root.getContext() instanceof WallpaperPreviewActivity){WallpaperPreviewActivity preview=(WallpaperPreviewActivity)root.getContext();enabled=preview.previewEnabled();transparent=preview.previewTransparent();opacity=preview.previewOpacity();}int alpha=enabled&&transparent?Math.max(25,255*(100-opacity)/100):255;surfaces(root,alpha);}
    static void surfaces(View root,int alpha){if(root instanceof android.view.SurfaceView||root instanceof PlayerView)return;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){View child=group.getChildAt(i);if(!(child instanceof android.widget.ImageView)&&!(child instanceof PlayerView)&&child.getBackground()!=null&&child.getBackground().getAlpha()!=alpha)child.getBackground().mutate().setAlpha(alpha);surfaces(child,alpha);}}}
    private static final class Background extends Drawable {
        private final Bitmap image;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        Background(Bitmap image){this.image=image;}
        @Override public void draw(Canvas canvas){Rect bounds=getBounds();if(bounds.isEmpty()||image.isRecycled())return;int[] crop=CropGeometry.window(image.getWidth(),image.getHeight(),bounds.height()/(float)bounds.width(),1,.5f,.5f);paint.setColor(android.graphics.Color.WHITE);paint.setAlpha(255);canvas.drawBitmap(image,new Rect(crop[0],crop[1],crop[0]+crop[2],crop[1]+crop[3]),bounds,paint);paint.setColor(Ui.BG);paint.setAlpha(105);canvas.drawRect(bounds,paint);}
        @Override public void setAlpha(int value){}
        @Override public void setColorFilter(ColorFilter filter){}
        @Override public int getOpacity(){return PixelFormat.OPAQUE;}
    }
}
