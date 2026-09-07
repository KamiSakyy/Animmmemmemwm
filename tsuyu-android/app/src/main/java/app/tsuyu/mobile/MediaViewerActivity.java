package app.tsuyu.mobile;

import android.app.*;
import android.graphics.*;
import android.net.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.exoplayer.*;
import androidx.media3.ui.*;
import java.io.*;

public class MediaViewerActivity extends Activity {
    ExoPlayer player;
    @Override protected void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(0xff000000);getWindow().setNavigationBarColor(0xff000000);MediaTools.Pack p=MediaVault.get(getIntent().getStringExtra("mediaId"));if(p==null){finish();return;}FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0xff000000);setContentView(root);Button close=Tui.iconButton(this,R.drawable.ic_close);FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.LEFT);cp.setMargins(dp(12),dp(18),0,0);root.addView(close,cp);close.setOnClickListener(v->finish());try{if("photo".equals(p.kind)){ZoomImageView iv=new ZoomImageView(this);Bitmap bm=Tui.bitmap(p.base64);iv.setImageBitmap(bm);iv.setScaleType(ImageView.ScaleType.FIT_CENTER);root.addView(iv,0,new FrameLayout.LayoutParams(-1,-1));}else{File f=MediaTools.writeTemp(this,p);PlayerView view=new PlayerView(this);view.setUseController(true);root.addView(view,0,new FrameLayout.LayoutParams(-1,-1));player=new ExoPlayer.Builder(this).build();view.setPlayer(player);player.setMediaItem(MediaItem.fromUri(Uri.fromFile(f)));player.prepare();player.play();}}catch(Exception e){TextView err=Tui.text(this,e.getMessage(),16,true);err.setGravity(Gravity.CENTER);root.addView(err,new FrameLayout.LayoutParams(-1,-1));}}
    @Override protected void onStop(){super.onStop();if(player!=null){player.release();player=null;}}
    int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);} 
    public static class ZoomImageView extends android.widget.ImageView {float scale=1f,dx,dy,lastX,lastY;ScaleGestureDetector detector;public ZoomImageView(android.content.Context c){super(c);detector=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){public boolean onScale(ScaleGestureDetector d){scale=Math.max(1f,Math.min(6f,scale*d.getScaleFactor()));setScaleX(scale);setScaleY(scale);return true;}});}public boolean onTouchEvent(android.view.MotionEvent e){detector.onTouchEvent(e);if(e.getPointerCount()==1&&!detector.isInProgress()){if(e.getAction()==android.view.MotionEvent.ACTION_DOWN){lastX=e.getX();lastY=e.getY();}else if(e.getAction()==android.view.MotionEvent.ACTION_MOVE&&scale>1f){dx+=e.getX()-lastX;dy+=e.getY()-lastY;setTranslationX(dx);setTranslationY(dy);lastX=e.getX();lastY=e.getY();}}return true;}}
}
