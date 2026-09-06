package app.yuro.guard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (context == null) return;
        String action = intent == null ? "" : intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) || "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            try {
                if (GuardPrefs.monitorEnabled(context)) {
                    Intent m = new Intent(context, MonitorService.class).setAction(MonitorService.ACTION_START);
                    if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(m); else context.startService(m);
                }
                if (GuardPrefs.enabled(context)) {
                    Intent g = new Intent(context, GuardVpnService.class).setAction(GuardVpnService.ACTION_START);
                    if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(g); else context.startService(g);
                }
            } catch (Throwable ignored) {}
        }
    }
}
