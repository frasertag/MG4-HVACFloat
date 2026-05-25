package com.custom.hvacfloater;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(HvacTheme.PREFS, Context.MODE_PRIVATE);
        String mode = prefs.getString(HvacTheme.KEY_AUTOSTART, HvacTheme.AUTOSTART_OFF);
        if (HvacTheme.AUTOSTART_OFF.equals(mode)) {
            return;
        }

        Intent service = new Intent(context, OverlayService.class);
        service.putExtra(OverlayService.EXTRA_START_HIDDEN, HvacTheme.AUTOSTART_HIDDEN.equals(mode));
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(service);
        } else {
            context.startService(service);
        }
    }
}
