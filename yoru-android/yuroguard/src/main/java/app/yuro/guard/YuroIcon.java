package app.yuro.guard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

public class YuroIcon extends View {
    public static final int SHIELD = 1, APPS = 2, CHART = 3, BROWSER = 4, MEDIA = 5, DPI = 6, POWER = 7;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int type;
    private int color = 0xfff3eaff;

    public YuroIcon(Context c, int type) { super(c); this.type = type; p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); }
    public void setColor(int color) { this.color = color; invalidate(); }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight(), s = Math.min(w, h), x = (w-s)/2f, y = (h-s)/2f;
        p.setColor(color); p.setStrokeWidth(s * 0.075f); p.setStyle(Paint.Style.STROKE);
        if (type == SHIELD) shield(c,x,y,s); else if (type == APPS) apps(c,x,y,s); else if (type == CHART) chart(c,x,y,s); else if (type == BROWSER) browser(c,x,y,s); else if (type == MEDIA) media(c,x,y,s); else if (type == DPI) dpi(c,x,y,s); else power(c,x,y,s);
    }

    private void shield(Canvas c,float x,float y,float s){ Path path=new Path(); path.moveTo(x+s*.5f,y+s*.08f); path.lineTo(x+s*.82f,y+s*.2f); path.lineTo(x+s*.76f,y+s*.62f); path.quadTo(x+s*.67f,y+s*.82f,x+s*.5f,y+s*.92f); path.quadTo(x+s*.33f,y+s*.82f,x+s*.24f,y+s*.62f); path.lineTo(x+s*.18f,y+s*.2f); path.close(); c.drawPath(path,p); c.drawLine(x+s*.43f,y+s*.42f,x+s*.43f,y+s*.68f,p); c.drawLine(x+s*.43f,y+s*.42f,x+s*.65f,y+s*.55f,p); c.drawLine(x+s*.43f,y+s*.68f,x+s*.65f,y+s*.55f,p); }
    private void apps(Canvas c,float x,float y,float s){ for(int iy=0;iy<2;iy++)for(int ix=0;ix<2;ix++)c.drawRoundRect(x+s*(.18f+ix*.34f),y+s*(.18f+iy*.34f),x+s*(.42f+ix*.34f),y+s*(.42f+iy*.34f),s*.07f,s*.07f,p); }
    private void chart(Canvas c,float x,float y,float s){ c.drawLine(x+s*.18f,y+s*.82f,x+s*.84f,y+s*.82f,p); c.drawLine(x+s*.24f,y+s*.74f,x+s*.24f,y+s*.44f,p); c.drawLine(x+s*.49f,y+s*.74f,x+s*.49f,y+s*.25f,p); c.drawLine(x+s*.74f,y+s*.74f,x+s*.74f,y+s*.55f,p); }
    private void browser(Canvas c,float x,float y,float s){ c.drawRoundRect(x+s*.14f,y+s*.18f,x+s*.86f,y+s*.82f,s*.12f,s*.12f,p); c.drawLine(x+s*.14f,y+s*.36f,x+s*.86f,y+s*.36f,p); c.drawCircle(x+s*.28f,y+s*.27f,s*.025f,p); c.drawCircle(x+s*.4f,y+s*.27f,s*.025f,p); c.drawLine(x+s*.36f,y+s*.62f,x+s*.64f,y+s*.62f,p); }
    private void media(Canvas c,float x,float y,float s){ c.drawRoundRect(x+s*.15f,y+s*.22f,x+s*.85f,y+s*.78f,s*.09f,s*.09f,p); Path play=new Path(); play.moveTo(x+s*.43f,y+s*.39f); play.lineTo(x+s*.43f,y+s*.61f); play.lineTo(x+s*.62f,y+s*.5f); play.close(); c.drawPath(play,p); c.drawLine(x+s*.2f,y+s*.84f,x+s*.8f,y+s*.16f,p); }
    private void dpi(Canvas c,float x,float y,float s){ c.drawCircle(x+s*.5f,y+s*.5f,s*.32f,p); c.drawCircle(x+s*.5f,y+s*.5f,s*.12f,p); c.drawLine(x+s*.5f,y+s*.08f,x+s*.5f,y+s*.24f,p); c.drawLine(x+s*.5f,y+s*.76f,x+s*.5f,y+s*.92f,p); c.drawLine(x+s*.08f,y+s*.5f,x+s*.24f,y+s*.5f,p); c.drawLine(x+s*.76f,y+s*.5f,x+s*.92f,y+s*.5f,p); }
    private void power(Canvas c,float x,float y,float s){ c.drawArc(x+s*.22f,y+s*.22f,x+s*.78f,y+s*.78f,130,280,false,p); c.drawLine(x+s*.5f,y+s*.12f,x+s*.5f,y+s*.48f,p); }
}
