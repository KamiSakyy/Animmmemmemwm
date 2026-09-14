package app.yoru.mobile;

import java.io.File;

public final class WallpaperPreviewActivity extends MainActivity {
    File previewFile(){String name=getIntent().getStringExtra("previewFile");return name!=null&&name.matches("wallpaper-preview-[0-9]+\\.png")?new File(getFilesDir(),name):null;}
    boolean previewEnabled(){File file=previewFile();return getIntent().getBooleanExtra("previewEnabled",false)&&file!=null&&file.isFile();}
    boolean previewTransparent(){return getIntent().getBooleanExtra("previewTransparent",false);}
    int previewOpacity(){return Math.max(0,Math.min(90,getIntent().getIntExtra("previewOpacity",45)));}
    @Override public void onBackPressed(){finish();}
    @Override protected void onDestroy(){if(!isChangingConfigurations()){File file=previewFile();if(file!=null)file.delete();}super.onDestroy();}
}
