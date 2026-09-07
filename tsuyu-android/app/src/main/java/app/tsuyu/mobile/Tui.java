package app.tsuyu.mobile;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.text.*;
import android.util.Base64;
import android.view.*;
import android.view.animation.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public final class Tui {
    public static final int BG=0xff050509, CARD=0xff111018, SURFACE=0xff171620, SURFACE2=0xff201d2b, TEXT=0xfff7f1ff, FG=TEXT, MUTED=0xffa69cb4, LINE=0xff302a3d, ACCENT=0xffd6beff, ACCENT2=0xffffb7dc, GREEN=0xff45e884, RED=0xffff6b9d;
    private Tui(){}
    public static int dp(Context c,float v){return (int)(v*c.getResources().getDisplayMetrics().density+.5f);} 
    public static LinearLayout row(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    public static LinearLayout col(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);return v;}
    public static LinearLayout.LayoutParams lp(Context c,int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(c,w),h<0?h:dp(c,h));}
    public static void space(LinearLayout v,int h){View s=new View(v.getContext());v.addView(s,lp(v.getContext(),1,h));}
    public static GradientDrawable shape(int color,float radius,Context c){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(c,radius));return g;}
    public static GradientDrawable oval(int color){GradientDrawable g=new GradientDrawable();g.setShape(GradientDrawable.OVAL);g.setColor(color);return g;}
    public static GradientDrawable round(int color,float radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(radius*2.5f);return g;}
    public static GradientDrawable stroke(int color,float radius,Context c){GradientDrawable g=shape(color,radius,c);g.setStroke(dp(c,.8f),LINE);return g;}
    public static GradientDrawable grad(int a,int b,float radius,Context c){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{a,b});g.setCornerRadius(dp(c,radius));return g;}
    public static TextView text(Context c,String s,int size,int color,boolean bold){TextView t=new TextView(c);t.setText(s==null?"":s);t.setTextColor(color);t.setTextSize(size);t.setTypeface(Typeface.create(customTypeface(c),bold?Typeface.BOLD:Typeface.NORMAL));t.setIncludeFontPadding(false);t.setLineSpacing(dp(c,3),1);return t;}
    public static TextView text(Context c,String s,int size,boolean bold){return text(c,s,size,bold?TEXT:MUTED,bold);}
    public static Typeface customTypeface(Context c){try{File f=new File(c.getFilesDir(),"tsuyu-font.ttf");if(f.exists()&&f.length()>0)return Typeface.createFromFile(f);}catch(Throwable ignored){}return Typeface.create("sans-serif",Typeface.NORMAL);} 
    public static EditText input(Context c,String hint,boolean password){EditText e=new EditText(c);e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setTextSize(15);e.setSingleLine(!hint.toLowerCase(Locale.ROOT).contains("опис"));e.setMinHeight(dp(c,50));e.setPadding(dp(c,14),0,dp(c,14),0);e.setTypeface(customTypeface(c));e.setHint(hint);e.setBackground(stroke(SURFACE,16,c));if(password)e.setInputType(0x00000081);return e;}
    public static EditText input(Context c,String hint){return input(c,hint,false);}
    public static TextView button(Context c,String title,boolean primary,Runnable r){TextView b=text(c,title,14,primary?0xff170f22:TEXT,true);b.setGravity(Gravity.CENTER);b.setMinHeight(dp(c,48));b.setPadding(dp(c,16),dp(c,12),dp(c,16),dp(c,12));b.setBackground(primary?grad(0xffffd5ea,ACCENT,18,c):stroke(SURFACE2,18,c));press(b);b.setOnClickListener(v->{if(r!=null)r.run();});return b;}
    public static Button button(Context c,String title){Button b=new Button(c);b.setText(title);b.setTextColor(0xff170f22);b.setTextSize(14);b.setTypeface(Typeface.DEFAULT_BOLD);b.setAllCaps(false);b.setBackground(grad(0xffffd5ea,ACCENT,18,c));press(b);return b;}
    public static TextView chip(Context c,String title,boolean active,Runnable r){TextView b=text(c,title,12,active?0xff170f22:MUTED,true);b.setSingleLine();b.setEllipsize(TextUtils.TruncateAt.END);b.setPadding(dp(c,13),dp(c,9),dp(c,13),dp(c,9));b.setBackground(active?grad(0xffffd5ea,ACCENT,18,c):stroke(SURFACE,18,c));press(b);b.setOnClickListener(v->{if(r!=null)r.run();});return b;}
    public static ImageButton icon(Context c,int res,String desc,Runnable r){ImageButton b=new ImageButton(c);b.setImageResource(res);b.setColorFilter(TEXT);b.setBackground(stroke(SURFACE,15,c));b.setPadding(dp(c,10),dp(c,10),dp(c,10),dp(c,10));b.setScaleType(ImageView.ScaleType.CENTER);b.setContentDescription(desc);press(b);b.setOnClickListener(v->{if(r!=null)r.run();});return b;}
    public static Button iconButton(Context c,int res){Button b=new Button(c);b.setText("");b.setCompoundDrawablesWithIntrinsicBounds(res,0,0,0);b.setCompoundDrawablePadding(0);b.setTextColor(TEXT);b.setAllCaps(false);b.setBackground(stroke(SURFACE,15,c));b.setPadding(dp(c,10),dp(c,10),dp(c,10),dp(c,10));press(b);return b;}
    public static void iconAccent(ImageButton b){b.setColorFilter(0xff170f22);b.setBackground(grad(0xffffd5ea,ACCENT,15,b.getContext()));}
    public static void press(View v){v.setClickable(true);v.setFocusable(true);v.setOnTouchListener((view,e)->{if(!view.isEnabled())return false;if(e.getAction()==android.view.MotionEvent.ACTION_DOWN)view.animate().scaleX(.965f).scaleY(.965f).alpha(.86f).setDuration(45).start();else if(e.getAction()==android.view.MotionEvent.ACTION_UP||e.getAction()==android.view.MotionEvent.ACTION_CANCEL)view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(70).start();return false;});}
    public static LinearLayout base(Activity a){a.getWindow().setStatusBarColor(BG);a.getWindow().setNavigationBarColor(BG);a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);LinearLayout root=col(a);root.setBackgroundColor(BG);a.setContentView(root);return root;}
    public static LinearLayout root(Activity a){LinearLayout r=base(a);r.setPadding(dp(a,12),dp(a,10),dp(a,12),dp(a,8));return r;}
    public static void toast(Context c,String s){Toast toast=new Toast(c.getApplicationContext());TextView t=text(c,s,12,TEXT,true);t.setPadding(dp(c,15),dp(c,11),dp(c,15),dp(c,11));t.setBackground(stroke(0xee171420,18,c));toast.setView(t);toast.setDuration(Toast.LENGTH_SHORT);toast.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL,0,dp(c,72));toast.show();}
    public static Dialog dialog(Activity a,String title,View content,String yes,Runnable onYes,String no){Dialog d=new Dialog(a);d.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout box=col(a);box.setPadding(dp(a,18),dp(a,18),dp(a,18),dp(a,16));box.setBackground(stroke(0xff14111d,24,a));box.addView(text(a,title,20,TEXT,true));if(content!=null){space(box,14);box.addView(content,new LinearLayout.LayoutParams(-1,-2));}space(box,16);LinearLayout actions=row(a);actions.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);if(no!=null&&!no.isEmpty())actions.addView(button(a,no,false,()->d.dismiss()),new LinearLayout.LayoutParams(0,-2,1));if(yes!=null&&!yes.isEmpty()){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.leftMargin=dp(a,8);actions.addView(button(a,yes,true,()->{d.dismiss();if(onYes!=null)onYes.run();}),p);}box.addView(actions);d.setContentView(box);d.setOnShowListener(x->{Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setLayout(Math.min(a.getResources().getDisplayMetrics().widthPixels-dp(a,28),dp(a,560)),WindowManager.LayoutParams.WRAP_CONTENT);WindowManager.LayoutParams p=w.getAttributes();p.dimAmount=.64f;w.setAttributes(p);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);}});d.show();return d;}
    public static ImageView avatar(Context c,String b64,String name,int size){ImageView iv=new ImageView(c);iv.setScaleType(ImageView.ScaleType.CENTER_CROP);iv.setBackground(oval(SURFACE2));iv.setClipToOutline(true);if(Build.VERSION.SDK_INT>=21)iv.setOutlineProvider(new ViewOutlineProvider(){@Override public void getOutline(View v,android.graphics.Outline o){o.setOval(0,0,v.getWidth(),v.getHeight());}});Bitmap bm=bitmap(b64);if(bm!=null)iv.setImageBitmap(bm);else iv.setImageDrawable(initials(c,name,size));return iv;}
    public static Drawable initials(Context c,String name,int size){Bitmap bm=Bitmap.createBitmap(Math.max(1,dp(c,size)),Math.max(1,dp(c,size)),Bitmap.Config.ARGB_8888);Canvas cn=new Canvas(bm);Paint p=new Paint(3);LinearGradient lg=new LinearGradient(0,0,bm.getWidth(),bm.getHeight(),ACCENT2,ACCENT,Shader.TileMode.CLAMP);p.setShader(lg);cn.drawCircle(bm.getWidth()/2f,bm.getHeight()/2f,bm.getWidth()/2f,p);p.setShader(null);p.setColor(0xff170f22);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(bm.getWidth()*.34f);String s=(name==null||name.trim().isEmpty()?"T":name.trim().substring(0,1)).toUpperCase(Locale.ROOT);Paint.FontMetrics fm=p.getFontMetrics();cn.drawText(s,bm.getWidth()/2f,bm.getHeight()/2f-(fm.ascent+fm.descent)/2,p);return new BitmapDrawable(c.getResources(),bm);} 
    public static Bitmap bitmap(String b64){try{if(b64==null||b64.isEmpty())return null;byte[] data=Base64.decode(b64,Base64.DEFAULT);return BitmapFactory.decodeByteArray(data,0,data.length);}catch(Throwable e){return null;}}
    public static String time(long t){if(t<=0)t=System.currentTimeMillis();Calendar c=Calendar.getInstance();c.setTimeInMillis(t);return String.format(Locale.ROOT,"%02d:%02d",c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE));}
    public static String day(long t){Calendar c=Calendar.getInstance();c.setTimeInMillis(t);return String.format(Locale.ROOT,"%02d.%02d %02d:%02d",c.get(Calendar.DAY_OF_MONTH),c.get(Calendar.MONTH)+1,c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE));}
    public static void fade(View v){v.setAlpha(0f);v.setTranslationY(dp(v.getContext(),8));v.animate().alpha(1f).translationY(0).setInterpolator(new DecelerateInterpolator()).setDuration(170).start();}
    public static void bounce(View v){v.animate().scaleX(1.07f).scaleY(1.07f).setDuration(70).withEndAction(()->v.animate().scaleX(1f).scaleY(1f).setDuration(90).start()).start();}
}
