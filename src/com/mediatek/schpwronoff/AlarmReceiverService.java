package com.mediatek.schpwronoff;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import com.mediatek.xlog.Xlog;

public class AlarmReceiverService extends Service {
    private static final String TAG = "AlarmReceiverService";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Xlog.i(TAG, "onCreate");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            Xlog.i(TAG, "action=" + action);
            if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
                Xlog.i(TAG, "onACTION_BOOT_COMPLETEDStartCommand----Intent.ACTION_BOOT_COMPLETED");
                Alarms.saveSnoozeAlert(this, -1, -1);
                Alarms.disableExpiredAlarms(this);
                Intent i = new Intent();
                i.setClass(this, SchPwrOnOffService.class);
                this.startService(i);
                Alarms.setNextAlert(this);
            } else {
                Xlog.i(TAG, "onStartCommand---Alarms.setNextAlert");
                Alarms.setNextAlert(this);
            }
        }
        return Service.START_NOT_STICKY;
    }

}
