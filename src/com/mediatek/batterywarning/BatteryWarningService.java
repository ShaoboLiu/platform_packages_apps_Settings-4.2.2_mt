package com.mediatek.batterywarning;

import android.app.AlertDialog;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.IBinder;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.settings.R;
import com.mediatek.xlog.Xlog;

import java.util.Timer;

/**
 * @author mtk80968
 * 
 */
public class BatteryWarningService extends Service {

    private static final String XLOGTAG = "Settings/BW";
    private static final String TAG = "WarningMessage:";
    // private static final String TAG = "BatteryWariningService";

    private static final String FILE_BATTERY_NOTIFY_CODE = "/sys/devices/platform/mt-battery/BatteryNotify";
    private Timer mTimer;
    private BatteryWarningMessageReceiver mReceiver;
    private static final Uri WARNING_SOUND_URI = Uri.parse("file:///system/media/audio/ui/VideoRecord.ogg");
    private static AlertDialog sChargerOverVoltageDialog;
    private static AlertDialog sBatteryOverTemperatureDialog;
    private static AlertDialog sOverCurrentProtectionDialog;
    private static AlertDialog sBatteryrOverVoltageDialog;
    private static AlertDialog sSatetyTimerTimeoutDialog;

    private static boolean sShowChargerOverVoltageDialog = true;
    private static boolean sShowBatteryOverTemperatureDialog = true;
    private static boolean sShowOverCurrentProtectionDialog = true;
    private static boolean sShowBatteryrOverVoltageDialog = true;
    private static boolean sShowSatetyTimerTimeoutDialog = true;
    private static Ringtone sRingtone;

    // private PowerManager mPowerManager;
    // private static boolean mIsScreenOn = true;
    enum WarningType {
        CHARGER_OVER_VOL, BATTERY_OVER_TEMP, OVER_CUR_PROTENCTION, BATTERY_OVER_VOL, SAFETY_TIMEOUT
    }

    // Note: Must match the sequence of the WarningType

    private static int[] sWarningTitle = new int[] { R.string.title_charger_over_voltage,
            R.string.title_battery_over_temperature, R.string.title_over_current_protection,
            R.string.title_battery_over_voltage, R.string.title_safety_timer_timeout };
    private static int[] sWarningMsg = new int[] { R.string.msg_charger_over_voltage, R.string.msg_battery_over_temperature,
            R.string.msg_over_current_protection, R.string.msg_battery_over_voltage, R.string.msg_safety_timer_timeout };

    @Override
    public void onCreate() {
        super.onCreate();
        Xlog.d(XLOGTAG, TAG + "Battery Warning Service: onCreate()");
        final int delayTime = 10000;
        final int periodTime = 10000;
        mTimer = new Timer();
        mTimer.schedule(new ReadCodeTask(BatteryWarningService.this, FILE_BATTERY_NOTIFY_CODE), delayTime, periodTime);

        Xlog.d(XLOGTAG, TAG + "Task schedule started: delay--" + delayTime + " period--" + periodTime);

        // mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);

        mReceiver = new BatteryWarningMessageReceiver();
        IntentFilter intentFilter = new IntentFilter(BatteryNotifyCodes.ACTION_CHARGER_OVER_VOLTAGE);
        intentFilter.addAction(BatteryNotifyCodes.ACTION_BATTER_OVER_TEMPERATURE);
        intentFilter.addAction(BatteryNotifyCodes.ACTION_OVER_CURRENT_PROTECTION);
        intentFilter.addAction(BatteryNotifyCodes.ACTION_BATTER_OVER_VOLTAGE);
        intentFilter.addAction(BatteryNotifyCodes.ACTION_SAFETY_TIMER_TIMEOUT);
        intentFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);

        registerReceiver(mReceiver, intentFilter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mTimer.cancel();
        mTimer = null;
        unregisterReceiver(mReceiver);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static class BatteryWarningMessageReceiver extends BroadcastReceiver {
        // PowerManager mPowerManager;
        // boolean mIsScreenOn = true;
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(BatteryNotifyCodes.ACTION_CHARGER_OVER_VOLTAGE)) {
                Xlog.d(XLOGTAG, TAG + "receiver: over charger voltage, please disconnect charger");
                showWarningDialog(context, sShowChargerOverVoltageDialog, sChargerOverVoltageDialog,
                        WarningType.CHARGER_OVER_VOL);
            } else if (action.equals(BatteryNotifyCodes.ACTION_BATTER_OVER_TEMPERATURE)) {
                Xlog.d(XLOGTAG, TAG + "receiver: over battery temperature, please remove battery");
                showWarningDialog(context, sShowBatteryOverTemperatureDialog, sBatteryOverTemperatureDialog,
                        WarningType.BATTERY_OVER_TEMP);
            } else if (action.equals(BatteryNotifyCodes.ACTION_OVER_CURRENT_PROTECTION)) {
                Xlog.d(XLOGTAG, TAG + "receiver: over current-protection, please disconnect charger"
                        + sShowOverCurrentProtectionDialog);
                showWarningDialog(context, sShowOverCurrentProtectionDialog, sOverCurrentProtectionDialog,
                        WarningType.OVER_CUR_PROTENCTION);
            } else if (action.equals(BatteryNotifyCodes.ACTION_BATTER_OVER_VOLTAGE)) {
                Xlog.d(XLOGTAG, TAG + "receiver: over battery voltage, please remove battery");
                showWarningDialog(context, sShowBatteryrOverVoltageDialog, sBatteryrOverVoltageDialog,
                        WarningType.BATTERY_OVER_VOL);
            } else if (action.equals(BatteryNotifyCodes.ACTION_SAFETY_TIMER_TIMEOUT)) {
                Xlog.d(XLOGTAG, TAG + "receiver: over 12 hours, battery does not charge full, please disconnect charger");
                showWarningDialog(context, sShowSatetyTimerTimeoutDialog, sSatetyTimerTimeoutDialog,
                        WarningType.SAFETY_TIMEOUT);
            } else if (action.equals(Intent.ACTION_POWER_DISCONNECTED)) {
                if (sChargerOverVoltageDialog != null && sChargerOverVoltageDialog.isShowing()) {
                    sChargerOverVoltageDialog.dismiss();
                }
                if (sSatetyTimerTimeoutDialog != null && sSatetyTimerTimeoutDialog.isShowing()) {
                    sSatetyTimerTimeoutDialog.dismiss();
                }
            }

        }// end of onReceive()

    }

    /**
     * 
     * @param context
     *            The Context that had been passed to {@link #warningMessageDialog(Context, int, int, int)}
     * @param titleResId
     *            Set the title using the given resource id.
     * @param messageResId
     *            Set the message using the given resource id.
     * @param imageResId
     *            Set the image using the given resource id.
     * @return Creates a {@link AlertDialog} with the arguments supplied to this builder.
     */
    static AlertDialog warningMessageDialog(Context context, int titleResId, int messageResId, int imageResId) {

        View view = View.inflate(context, R.layout.battery_warning, null);
        TextView mMessageView = (TextView) view.findViewById(R.id.subtitle);
        mMessageView.setText(messageResId);
        ImageView mImageView = (ImageView) view.findViewById(R.id.image);
        mImageView.setImageResource(imageResId);

        AlertDialog.Builder mBatteryWarning = new AlertDialog.Builder(context);
        mBatteryWarning.setCancelable(false);
        mBatteryWarning.setTitle(titleResId);
        mBatteryWarning.setView(view);
        mBatteryWarning.setIconAttribute(android.R.attr.alertDialogIcon);
        mBatteryWarning.setPositiveButton(R.string.btn_ok_msg, sOnPositiveButtonClickListener);
        mBatteryWarning.setNegativeButton(R.string.btn_cancel_msg, sOnNegativeButtonClickListener);

        return mBatteryWarning.create();
    }

    /**
     * 
     * @param context
     *            The Context that had been passed to {@link #warningMessageDialog(Context, Uri)}
     * @param defaultUri
     */

    static void playAlertSound(Context context, Uri defaultUri) {

        if (defaultUri != null) {
            sRingtone = RingtoneManager.getRingtone(context, defaultUri);
            if (sRingtone != null) {
                sRingtone.setStreamType(AudioManager.STREAM_SYSTEM);
                sRingtone.play();
            }
        }
    }

    static void showWarningDialog(Context context, boolean showDialog, AlertDialog warningDialog, WarningType dialogType) {
        Xlog.d(XLOGTAG, TAG + "showDialog:" + showDialog);
        WarningType mWarningType = dialogType;
        if (showDialog) {
            if (warningDialog != null && warningDialog.isShowing()) {
                warningDialog.dismiss();
            }
            warningDialog = warningMessageDialog(context, sWarningTitle[mWarningType.ordinal()],
                    sWarningMsg[mWarningType.ordinal()], R.drawable.battery_low_battery);
            warningDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            warningDialog.show();
            playAlertSound(context, WARNING_SOUND_URI);
            Xlog.d(XLOGTAG, TAG + "create & show dialog:" + dialogType);
        }
        switch (mWarningType) {
        case CHARGER_OVER_VOL:
            sChargerOverVoltageDialog = warningDialog;
            break;
        case BATTERY_OVER_TEMP:
            sBatteryOverTemperatureDialog = warningDialog;
            break;
        case OVER_CUR_PROTENCTION:
            sOverCurrentProtectionDialog = warningDialog;
            break;
        case BATTERY_OVER_VOL:
            sBatteryrOverVoltageDialog = warningDialog;
            break;
        case SAFETY_TIMEOUT:
            sSatetyTimerTimeoutDialog = warningDialog;
            break;
        default:
            break;
        }
    }

    private static OnClickListener sOnNegativeButtonClickListener = new OnClickListener() {
        public void onClick(DialogInterface a0, int a1) {
            Xlog.d(XLOGTAG, TAG + "Dismiss dialog" + a0);
            if (sRingtone != null) {
                sRingtone.stop();
            }
            if (a0.equals(sChargerOverVoltageDialog)) {
                sShowChargerOverVoltageDialog = false;
            } else if (a0.equals(sBatteryOverTemperatureDialog)) {
                sShowBatteryOverTemperatureDialog = false;
            } else if (a0.equals(sOverCurrentProtectionDialog)) {
                sShowOverCurrentProtectionDialog = false;
            } else if (a0.equals(sBatteryrOverVoltageDialog)) {
                sShowBatteryrOverVoltageDialog = false;
            } else if (a0.equals(sSatetyTimerTimeoutDialog)) {
                sShowSatetyTimerTimeoutDialog = false;
            }
        }
    };

    private static OnClickListener sOnPositiveButtonClickListener = new OnClickListener() {
        public void onClick(DialogInterface a0, int a1) {
            Xlog.d(XLOGTAG, TAG + "positive Button later nofitication dialog" + a0);
            if (sRingtone != null) {
                sRingtone.stop();
            }
        }
    };

}
