package app.yuro.guard;

import android.graphics.drawable.Drawable;

public class AppEntry {
    public String pkg;
    public String label;
    public int uid;
    public boolean system;
    public boolean selected;
    public long rx;
    public long tx;
    public long mobileRx;
    public long mobileTx;
    public long speedRx;
    public long speedTx;
    public Drawable icon;

    public long total() { return Math.max(0, rx) + Math.max(0, tx); }
    public long mobileTotal() { return Math.max(0, mobileRx) + Math.max(0, mobileTx); }
    public long speed() { return Math.max(0, speedRx) + Math.max(0, speedTx); }
}
