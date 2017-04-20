package com.mediatek.hdmi;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.Phone;
import com.android.settings.R;

import com.mediatek.common.MediatekClassFactory;
import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.common.hdmi.IHDMINative;



public class HDMILocalService extends Service {
    private static final String TAG = "hdmi";
    private static final String LOCAL_TAG = " >> HDMILocalService.";
    // Action broadcast to HDMI settings and other App
    public static final String ACTION_CABLE_STATE_CHANGED = "com.mediatek.hdmi.localservice.action.CABLE_STATE_CHANGED";
    public static final String ACTION_IPO_SHUTDOWN = "android.intent.action.ACTION_SHUTDOWN_IPO";
    public static final String ACTION_IPO_BOOTUP = "android.intent.action.ACTION_BOOT_IPO";

    public static final String KEY_HDMI_ENABLE_STATUS = "hdmi_enable_status";
    public static final String KEY_HDMI_AUDIO_STATUS = "hdmi_audio_status";
    public static final String KEY_HDMI_VIDEO_STATUS = "hdmi_video_status";
    public static final String KEY_HDMI_VIDEO_RESOLUTION = "hdmi_video_resolution";
    private static final int SLEEP_TIME = 140;
    // add for MFR
    private IHDMINative mHdmiNative = null;
    private static boolean sIsRunning = true;
    private static boolean sIsCablePluged = false;
    private static int sWiredHeadSetPlugState = 0;
    private static boolean sIsCallStateIdle = true;

    /*
     * flag for cache audio status, when phone state changed, restore or load
     * cached status, enable(true) or disable(false)
     */
    private static boolean sHMDIAudioTargetState = true;
    private static boolean sHMDITargetState = true;
    private static boolean sHMDIVideoTargetState = true;

    private AudioManager mAudioManager = null;
    private TelephonyManager mTelephonyManager = null;
    private PowerManager.WakeLock mWakeLock = null;

    public class LocalBinder extends Binder {
        /**
         * 
         * @return the service
         */
        public HDMILocalService getService() {
            return HDMILocalService.this;
        }
    }

    private final IBinder mBinder = new LocalBinder();
    private HDMIServiceReceiver mReceiver = null;

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, ">>HDMILocalService.onBind()");
        return mBinder;
    }

    private class HDMIServiceReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_HDMI_PLUG.equals(action)) {
                int hdmiCableState = intent.getIntExtra("state", 0);
                sIsCablePluged = (hdmiCableState == 1);
                dealWithCablePluged();
            } else if (Intent.ACTION_HEADSET_PLUG.equals(action)) {
                Log.e(TAG,"receive the headset plugin and do nothing");
                //sWiredHeadSetPlugState = intent.getIntExtra("state", 0);
                //dealWithHeadSetChanged();
            } else if (ACTION_IPO_BOOTUP.equals(action)) {
                Log.e(TAG,"HDMI local service receive IPO boot up broadcast");
                dealWithIPO(true);
            } else if (ACTION_IPO_SHUTDOWN.equals(action)) {
                dealWithIPO(false);
            }
        }
    }

    private PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        public void onCallStateChanged(int state, String incomingNumber) {
            Log.i(TAG, LOCAL_TAG + " Phone state changed, new state=" + state);
            if (state != TelephonyManager.CALL_STATE_IDLE) {
                sIsCallStateIdle = false;
                dealWithCallStateChanged();
                return;
            } else {
                if (FeatureOption.MTK_GEMINI_SUPPORT) {
                    if (mTelephonyManager == null) {
                        mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
                    }
                    int sim1State = mTelephonyManager
                            .getCallStateGemini(Phone.GEMINI_SIM_1);
                    int sim2State = mTelephonyManager
                            .getCallStateGemini(Phone.GEMINI_SIM_2);
                    Log.e(TAG, LOCAL_TAG + "phone state change, sim1="
                            + sim1State + ", sim2=" + sim2State);
                    if (sim1State != TelephonyManager.CALL_STATE_IDLE
                            || sim2State != TelephonyManager.CALL_STATE_IDLE) {
                        Log.e(TAG, LOCAL_TAG
                                + " phone is not idle for gemini phone");
                        sIsCallStateIdle = false;
                        dealWithCallStateChanged();
                        return;
                    }
                }
            }
            sIsCallStateIdle = true;
            dealWithCallStateChanged();
        }

    };

    @Override
    public void onCreate() {
        Log.i(TAG, ">>HDMILocalService.onCreate()");
        mHdmiNative = MediatekClassFactory.createInstance(IHDMINative.class);
        if (mHdmiNative == null) {
            Log.e(TAG, "Native is not created");
        }
        if (mReceiver == null) {
            mReceiver = new HDMIServiceReceiver();
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_HDMI_PLUG);
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        filter.addAction(ACTION_IPO_BOOTUP);
        filter.addAction(ACTION_IPO_SHUTDOWN);
        registerReceiver(mReceiver, filter);

        if (mTelephonyManager == null) {
            mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        }
        mTelephonyManager.listen(mPhoneStateListener,
                PhoneStateListener.LISTEN_CALL_STATE);
        if (mWakeLock == null) {
            PowerManager mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            mWakeLock = mPowerManager.newWakeLock(
                    PowerManager.SCREEN_DIM_WAKE_LOCK, "HDMILocalService");
        }
        initHDMITargetState();
        super.onCreate();
    }

    private void initHDMITargetState() {
        int initHDMIState = Settings.System.getInt(getContentResolver(),
                HDMILocalService.KEY_HDMI_ENABLE_STATUS, 0);
        int initHDMIAudioState = Settings.System.getInt(getContentResolver(),
                HDMILocalService.KEY_HDMI_AUDIO_STATUS, 1);
        int initHDMIVideoState = Settings.System.getInt(getContentResolver(),
                HDMILocalService.KEY_HDMI_VIDEO_STATUS, 0);
        Log.i(TAG, LOCAL_TAG + "initHDMITargetState(), initHDMIState="
                + initHDMIState + ", initHDMIAudioState=" + initHDMIAudioState
                + ", initHDMIVideoState=" + initHDMIVideoState);
        sHMDITargetState = (initHDMIState == 1);
        sHMDIAudioTargetState = (initHDMIAudioState == 1);
        sHMDIVideoTargetState = (initHDMIVideoState == 1);

        if (sHMDITargetState) {
            Log.i(TAG, LOCAL_TAG + " enable HDMI after boot up complete");
            enableHDMI(true);
        } else {
            Log.i(TAG, LOCAL_TAG + " disable HDMI after boot up complete");
            enableHDMI(false);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, ">>HDMILocalService.onStartCommand(), startId=" + startId
                + ": intent=" + intent);
        sIsRunning = true;
        if (intent != null) {
            String bootUpTypeAction = intent.getStringExtra("bootup_action");
            if (bootUpTypeAction != null) {
                // IPO boot up, need to resume HDMI driver
                if (bootUpTypeAction.equals(ACTION_IPO_BOOTUP)) {
                    Log
                            .i(TAG,
                                    "IPO boot up complete, try to resume HDMI driver status");
                    dealWithIPO(true);
                }
            }
        }
        return Service.START_STICKY_COMPATIBILITY;
    }

    @Override
    public void onDestroy() {
        sIsRunning = false;
        // enableHDMI(false);
        unregisterReceiver(mReceiver);
        mTelephonyManager.listen(mPhoneStateListener,
                PhoneStateListener.LISTEN_NONE);
        Log.i(TAG, ">>HDMILocalService.onDestroy()");
    }

    private static final String GET_HDMI_AUDIO_STATUS = "GetHDMIAudioStatus";
    private static final String HDMI_AUDIO_STATUS_ENABLED = "GetHDMIAudioStatus=true";
    private static final String HDMI_AUDIO_STATUS_DISABLED = "GetHDMIAudioStatus=false";
    private static final String SET_HDMI_AUDIO_ENABLED = "SetHDMIAudioEnable=1";
    private static final String SET_HDMI_AUDIO_DISABLED = "SetHDMIAudioEnable=0";

    /**
     * set whether enable the hdmi
     * 
     * @param enabled
     *            status
     * @return a native call enableHDMIImpl() to enable/disable hdmi
     */
    public boolean enableHDMI(boolean enabled) {
        Log.i(TAG, LOCAL_TAG + "enableHDMI(), new state=" + enabled);
        sHMDITargetState = enabled;
        return enableHDMIImpl(enabled);
    }

    private boolean enableHDMIImpl(boolean enabled) {
        Log.e(TAG, LOCAL_TAG + "enableHDMIImpl(), new state=" + enabled);
        return mHdmiNative.enableHDMI(enabled);
    }

    /**
     * Enable the audio or disable the auido
     * 
     * @param enabled
     *            the flag
     * @return update the audio state
     */
    public boolean enableAudio(boolean enabled) {
        Log.i(TAG, ">>HDMILocalService.enableAudio(), new state=" + enabled);
        sHMDIAudioTargetState = enabled;
        return updateAudioState();
    }

    private boolean enableAudioImp(boolean enabled) {
        Log.e(TAG, LOCAL_TAG + "enableAudioImp(" + enabled + ")");
        String state = null;
        if (mAudioManager == null) {
            mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        }
        if (mAudioManager != null) {
            state = mAudioManager.getParameters(GET_HDMI_AUDIO_STATUS);
            if ((enabled && HDMI_AUDIO_STATUS_ENABLED.equals(state))
                    || (!enabled && HDMI_AUDIO_STATUS_DISABLED.equals(state))) {
                Log.i(TAG, LOCAL_TAG
                        + "  audio driver status is already what we need ["
                        + state + "]");
                return true;
            } else {
                if (mAudioManager.enableHDMIAudio(enabled)) {
                    Log.i(TAG, LOCAL_TAG + "enableAudio(" + enabled
                            + ") success");
                    return true;
                } else {
                    Log.i(TAG, LOCAL_TAG + "enableAudio(" + enabled
                            + ") fail, current state=" + state);
                    return false;
                }
            }
        } else {
            Log
                    .e(TAG,
                            ">>HDMILocalService.enableAudio(), fail to get AudioManager service");
            return false;
        }
    }

    /**
     * 
     * @param enabled
     *            status of video
     * @return true for successfully enable the video
     */
    public boolean enableVideo(boolean enabled) {
        Log.i(TAG, ">>HDMILocalService.enableVideo(), new state=" + enabled);
        sHMDIVideoTargetState = enabled;
        return enableVideoImp(enabled);
    }

    private boolean enableVideoImp(boolean enabled) {
        Log.e(TAG, ">>HDMILocalService.enableVideoImp, new state=" + enabled);
        return mHdmiNative.enableVideo(enabled);
    }

    /**
     * Check whether HDMI audio is enabled
     * 
     * @return true for successful get the status of audio
     */
    public boolean getAudioEnabledStatus() {
        Log.i(TAG, ">>HDMILocalService.getAudioEnabledStatus");
        String state = null;
        if (mAudioManager == null) {
            mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        }
        if (mAudioManager != null) {
            state = mAudioManager.getParameters(GET_HDMI_AUDIO_STATUS);
            if (HDMI_AUDIO_STATUS_ENABLED.equals(state)) {
                Log.i(TAG, "HDMI audeo is enabled");
                return true;
            }
        }
        Log.i(TAG, "HDMI audio is disabled");
        return false;
    }

    /**
     * Set the resolution for the video
     * 
     * @param resolution
     *            the value of resolution
     * @return call native method to set config
     */
    public boolean setVideoResolution(int resolution) {
        Log.i(TAG, ">>HDMILocalService.setVideoResolution(), new resolution="
                + resolution);
        return mHdmiNative.setVideoConfig(resolution);
    }

    private void initVideoResolution() {
        String videoResolution = Settings.System.getString(this
                .getContentResolver(), KEY_HDMI_VIDEO_RESOLUTION);
        Log.i(TAG, ">>HDMILocalService.initVideoResolution(), init resolution="
                + videoResolution);
        if (videoResolution == null || "".equals(videoResolution)) {
            Log.e(TAG, ">>No init resolution, set it to Auto by default");
            videoResolution = "3";// auto by default
        }
        setVideoResolution(Integer.parseInt(videoResolution));
    }

    /**
     * 
     * @return true for cable is plugged in
     */
    public boolean isCablePluged() {
        Log.d(TAG, LOCAL_TAG + "isCablePluged?" + sIsCablePluged);
        return sIsCablePluged;
    }

    /**
     * when hdmi cable is pluged in or out, make next action for it
     */
    private void dealWithCablePluged() {
        Log.e(TAG, LOCAL_TAG + "dealWithCablePluged(), is cable pluged in?"
                + sIsCablePluged);
        Intent intent = new Intent(ACTION_CABLE_STATE_CHANGED);
        intent.putExtra("cable_pluged", sIsCablePluged);
        sendBroadcast(intent);
        showNotification(sIsCablePluged);

        if (sIsCablePluged) {
            updateAudioState();
            enableVideoImp(sHMDIVideoTargetState);
            initVideoResolution();
            if (mHdmiNative.ishdmiForceAwake() && (mWakeLock != null)) {
                mWakeLock.acquire();
            }
        } else {
            Log.e(TAG, LOCAL_TAG
                    + "dealWithCablePluged() sleep 140ms for audio");
            try {
                // sleep 140ms for notification data consumed in hardware buffer
                Thread.sleep(SLEEP_TIME);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            enableAudioImp(false);
            enableVideoImp(false);
            if (mHdmiNative.ishdmiForceAwake() && (mWakeLock != null)
                    && (mWakeLock.isHeld())) {
                mWakeLock.release();
            }
        }
    }

    private void showNotification(boolean hasCable) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            Log.e(TAG, "Fail to get NotificationManager instance");
            return;
        }
        if (hasCable) {
            Log.i(TAG, "HDMI cable is pluged in, give notification now");
            Notification notification = new Notification();
            String titleStr = getResources().getString(
                    R.string.hdmi_notification_title);
            String contentStr = getResources().getString(
                    R.string.hdmi_notification_content);
            notification.icon = R.drawable.ic_hdmi_notification;
            notification.tickerText = titleStr;
            /*
             * Remove the notification sound as this may cause many other
             * problems on audio notification.sound =
             * RingtoneManager.getActualDefaultRingtoneUri(this,
             * RingtoneManager.TYPE_NOTIFICATION);
             */
            notification.flags = Notification.FLAG_ONGOING_EVENT
                    | Notification.FLAG_NO_CLEAR
                    | Notification.FLAG_SHOW_LIGHTS;

            Intent intent = Intent
                    .makeRestartActivityTask(new ComponentName(
                            "com.android.settings",
                            "com.android.settings.HDMISettings"));
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                    intent, 0);
            notification.setLatestEventInfo(this, titleStr, contentStr,
                    pendingIntent);
            notificationManager.notify(R.drawable.ic_hdmi_notification,
                    notification);
        } else {
            Log.i(TAG, "HDMI cable is pluged out, clear notification now");
            notificationManager.cancel(R.drawable.ic_hdmi_notification);
        }
    }

    private void dealWithHeadSetChanged() {
        Log.e(TAG, LOCAL_TAG + "dealWithHeadSetChanged(), headset new state = "
                + sWiredHeadSetPlugState);
        if (sHMDIAudioTargetState) {
            updateAudioState();
        } else {
            Log.i(TAG, LOCAL_TAG + " audio was off, just return");
        }
    }

    /**
     * When phone state change, modify audio state synchronized
     */
    private void dealWithCallStateChanged() {
        Log.i(TAG, LOCAL_TAG + "updateAudioStateByPhone()");
        if (sHMDIAudioTargetState) {
            updateAudioState();
        } else {
            Log.i(TAG, LOCAL_TAG + " audio was off, just return");
        }
    }

    private boolean updateAudioState() {
        Log.i(TAG, LOCAL_TAG + "updateAudioState(), HDMI target state="
                + sHMDITargetState + "\n sIsCablePluged=" + sIsCablePluged
                + "\n audioTargetState=" + sHMDIAudioTargetState
                + "\n sIsCallStateIdle=" + sIsCallStateIdle
                + "\n sWiredHeadSetPlugState=" + sWiredHeadSetPlugState);
        // HDMI is disabled or no cable pluged now, no audio
        if (!sHMDITargetState || !sIsCablePluged) {
            return false;
        }
        if (sHMDIAudioTargetState && sIsCallStateIdle
                && (sWiredHeadSetPlugState != 1)) {
            return enableAudioImp(true);
        } else {
            return enableAudioImp(false);
        }
    }

    /**
     * send command to HDMI driver when IPO boot up or shut down, to
     * resume/pause HDMI
     * 
     * @param isBootUp
     */
    private void dealWithIPO(boolean isBootUp) {
        Log.i(TAG, "dealWithIPO(), is bootUp?" + isBootUp
                + ", sHMDITargetState=" + sHMDITargetState);
        if (sHMDITargetState) {
            mHdmiNative.enableHDMIIPO(isBootUp);
        }
        if (isBootUp) {
            Log.i(TAG, "reset audio state for IPO boot up");
            updateAudioState();
        } else {
            Log.i(TAG, "shut down audio for IPO shut down");
            enableAudioImp(false);
        }
    }
}
