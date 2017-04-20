package com.mediatek.batterywarning;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.mediatek.xlog.Xlog;

public class InitBatteryObserverServiceReceiver extends BroadcastReceiver {
    private static final String XLOGTAG = "Settings/BW";
    private static final String TAG = "WarningMessage:";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Intent i = new Intent(context, BatteryWarningService.class);
        if ("android.intent.action.ACTION_SHUTDOWN_IPO".equals(action)) {
            Xlog.d(XLOGTAG, TAG + "IPO shut down, stop Battery Warning Service");
            context.stopService(i);
        } else if ((Intent.ACTION_BOOT_COMPLETED.equals(action)) 
                || ("android.intent.action.ACTION_BOOT_IPO".equals(action))) {
            Xlog.d(XLOGTAG, TAG + "boot completed, start Battery Warning Service");
            context.startService(i);
        }
    }
}
