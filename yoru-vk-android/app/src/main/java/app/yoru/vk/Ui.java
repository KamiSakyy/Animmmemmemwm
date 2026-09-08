package app.yoru.vk;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Build;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.RejectedExecutionException;

final class Ui {
    static final int BG=0xff0d0b12,CARD=0xff1c1724,SURFACE=0xff15111c,ACCENT=0xffc8a7ff,TEXT=0xfff7f0ff,MUTED=0xffa99bb8,LINE=0xff352a43,BLUE=0xff77b7ff;
    static int dp(Context c,int value){return Math.round(c.getResources().getDisplayMetrics().density*value);}
    static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
    static LinearLayout row(Context c){LinearLayout l=new LinearLayout(c);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    static GradientDrawable shape(Context c,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));d.setStroke(dp(c,1),LINE);return d;}
    static TextView text(Context c,String value,int size,int color,boolean bold){TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));t.setLineSpacing(dp(c,2),1);return t;}
    static TextView button(Context c,String value,boolean primary,Runnable action){TextView t=text(c,value,14,primary?BG:TEXT,true);t.setGravity(Gravity.CENTER);t.setPadding(dp(c,14),dp(c,12),dp(c,14),dp(c,12));t.setMinHeight(dp(c,48));t.setBackground(shape(c,primary?ACCENT:CARD,13));t.setFocusable(true);t.setClickable(true);t.setContentDescription(value);t.setOnClickListener(v->action.run());return t;}
    static EditText input(Context c,String hint,boolean number){EditText e=new EditText(c);e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setTextSize(14);e.setSingleLine(true);e.setHint(hint);e.setPadding(dp(c,12),dp(c,10),dp(c,12),dp(c,10));e.setMinHeight(dp(c,48));e.setBackground(shape(c,SURFACE,12));e.setInputType(number?InputType.TYPE_CLASS_NUMBER:InputType.TYPE_CLASS_TEXT);return e;}
    static void gap(LinearLayout col,int size){View v=new View(col.getContext());col.addView(v,new LinearLayout.LayoutParams(1,dp(col.getContext(),size)));}
    static LinearLayout card(Context c){LinearLayout box=column(c);box.setPadding(dp(c,15),dp(c,15),dp(c,15),dp(c,15));box.setBackground(shape(c,CARD,16));return box;}
    static void base(Activity a,LinearLayout root){root.setBackgroundColor(BG);a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);root.setOnApplyWindowInsetsListener((v,in)->{int left,top,right,bottom;if(Build.VERSION.SDK_INT>=30){android.graphics.Insets x=in.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());left=x.left;top=x.top;right=x.right;bottom=x.bottom;}else{left=in.getSystemWindowInsetLeft();top=in.getSystemWindowInsetTop();right=in.getSystemWindowInsetRight();bottom=in.getSystemWindowInsetBottom();}v.setPadding(left,top,right,bottom);return in;});a.setContentView(root);root.requestApplyInsets();}
    static void message(Activity a,String title,String text){if(!a.isFinishing())new AlertDialog.Builder(a).setTitle(title).setMessage(text).setPositiveButton("Понятно",null).show();}
    static String error(Exception e){if(e instanceof Api.Failure||e instanceof IllegalArgumentException||e instanceof IllegalStateException)return e.getMessage();if(e instanceof java.net.SocketTimeoutException)return "Сервис не ответил вовремя. Повторите запрос.";return "Не удалось получить данные. Проверьте сеть, доступность сервиса и повторите. ";}
    static ImageView image(Context c,int w,int h){ImageView i=new ImageView(c);i.setScaleType(ImageView.ScaleType.CENTER_CROP);i.setBackground(shape(c,SURFACE,14));i.setClipToOutline(true);i.setLayoutParams(new LinearLayout.LayoutParams(w<0?w:dp(c,w),h<0?h:dp(c,h)));return i;}
    static void load(ImageView view,String address){if(address!=null&&address.equals(view.getTag())&&view.getDrawable()!=null)return;view.setTag(address);view.setImageDrawable(null);if(address==null||!address.startsWith("https://"))return;VkApp app=(VkApp)view.getContext().getApplicationContext();
        try{app.images.execute(()->{if(!address.equals(view.getTag()))return;Bitmap bitmap=null;HttpURLConnection c=null;try{URI u=new URI(address);String h=u.getHost();boolean safe=h!=null&&(h.equals("shikimori.io")||h.equals("shikimori.one")||h.equals("shikimori.me"));if(!safe||u.getUserInfo()!=null)return;c=(HttpURLConnection)new URL(address).openConnection();c.setInstanceFollowRedirects(false);c.setUseCaches(false);c.setConnectTimeout(5000);c.setReadTimeout(7000);c.setRequestProperty("User-Agent","YORU-VK-Web/0.2.0");if(c.getResponseCode()!=200)return;try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>8*1024*1024)return;out.write(b,0,n);}byte[] data=out.toByteArray();BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,o);o.inSampleSize=1;while(o.outWidth/o.inSampleSize>700||o.outHeight/o.inSampleSize>1000)o.inSampleSize*=2;o.inJustDecodeBounds=false;bitmap=BitmapFactory.decodeByteArray(data,0,data.length,o);}}catch(Exception ignored){}finally{if(c!=null)c.disconnect();}Bitmap result=bitmap;if(result!=null)app.main.post(()->{if(address.equals(view.getTag()))view.setImageBitmap(result);});});}catch(RejectedExecutionException ignored){}
    }
}
