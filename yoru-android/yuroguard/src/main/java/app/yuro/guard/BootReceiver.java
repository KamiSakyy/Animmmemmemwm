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
                if (GuardPrefs.dpiProxyEnabled(context)) {
                    Intent p = new Intent(context, DpiProxyService.class).setAction(DpiProxyService.ACTION_START);
                    if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(p); else context.startService(p);
                }
                // VPN intentionally never auto-starts: Android can keep only one VPN, so YURO connects only after explicit user tap.
            } catch (Throwable ignored) {}
        }
    }
}
