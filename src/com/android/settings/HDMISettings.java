package com.android.settings;


import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.provider.Settings;

import com.mediatek.hdmi.HDMILocalService;
import com.mediatek.xlog.Xlog;

public class HDMISettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {
    // private static final String TAG = "HDMISettings";
    private static final String TAG = "hdmi";
    private static final String KEY_TOGGLE_HDMI = "hdmi_toggler";
    private static final String KEY_TOGGLE_AUDIO = "audio_toggler";
    private static final String KEY_TOGGLE_VIDEO = "video_toggler";
    private static final String KEY_VIDEO_RESOLUTION = "video_resolution";
    private CheckBoxPreference mToggleHDMIPref;
    private CheckBoxPreference mToggleAudioPref;
    private CheckBoxPreference mToggleVideoPref;
    private ListPreference mVideoResolutionPref;
    private HDMILocalService mHDMIService = null;
    private boolean mIsHDMIEnabled = false;

    /**
     * Service connection observer for HDMI service
     */
    private ServiceConnection mHDMIServiceConn = new ServiceConnection() {
        public void onServiceDisconnected(ComponentName name) {
            mHDMIService = null;
        }

        public void onServiceConnected(ComponentName name, IBinder service) {
            mHDMIService = ((HDMILocalService.LocalBinder) service)
                    .getService();
            Xlog.w(TAG, "HDMISettings, HDMILocalService is connected");
            updateSettingsItemEnableStatus();
            updateSelectedResolution();
        }
    };

    BroadcastReceiver mLocalServiceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (HDMILocalService.ACTION_CABLE_STATE_CHANGED.equals(action)) {
                updateSettingsItemEnableStatus();
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Xlog.i(TAG, ">>HDMISettings.onCreate()");
        addPreferencesFromResource(R.xml.hdmi_settings);
        mToggleHDMIPref = (CheckBoxPreference) findPreference(KEY_TOGGLE_HDMI);
        mToggleHDMIPref.setOnPreferenceChangeListener(this);
        mToggleAudioPref = (CheckBoxPreference) findPreference(KEY_TOGGLE_AUDIO);
        mToggleAudioPref.setOnPreferenceChangeListener(this);
        mToggleVideoPref = (CheckBoxPreference) findPreference(KEY_TOGGLE_VIDEO);
        mToggleVideoPref.setOnPreferenceChangeListener(this);
        mVideoResolutionPref = (ListPreference) findPreference(KEY_VIDEO_RESOLUTION);
        mVideoResolutionPref.setOnPreferenceChangeListener(this);

        boolean bindHdmiServiceFlag = getActivity().bindService(
                new Intent(getActivity(), HDMILocalService.class),
                mHDMIServiceConn, Context.BIND_AUTO_CREATE);
        if (!bindHdmiServiceFlag) {
            Xlog.e(TAG, "HDMISettings fail to bind HDMI service");
            mToggleHDMIPref.setEnabled(false);
            mToggleAudioPref.setEnabled(false);
            mToggleVideoPref.setEnabled(false);
            mVideoResolutionPref.setEnabled(false);
        }

        if (mToggleVideoPref != null) {
            getPreferenceScreen().removePreference(mToggleVideoPref);
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(HDMILocalService.ACTION_CABLE_STATE_CHANGED);
        getActivity().registerReceiver(mLocalServiceReceiver, filter);
    }

    @Override
    public void onResume() {
        updatePref();
        updateSettingsItemEnableStatus();
        super.onResume();
    }

    private void updatePref() {

        mIsHDMIEnabled = Settings.System.getInt(getContentResolver(),
                HDMILocalService.KEY_HDMI_ENABLE_STATUS, 0) == 1;
        boolean isAudioEnabled = Settings.System.getInt(getContentResolver(),
                HDMILocalService.KEY_HDMI_AUDIO_STATUS, 1) == 1;
        boolean isVideoEnabled = Settings.System.getInt(getContentResolver(),
                HDMILocalService.KEY_HDMI_VIDEO_STATUS, 0) == 1;
        mToggleHDMIPref.setChecked(mIsHDMIEnabled);
        mToggleAudioPref.setChecked(isAudioEnabled);
        if (mToggleVideoPref != null) {
            mToggleVideoPref.setChecked(isVideoEnabled);
        }
        updateSelectedResolution();
    }

    /**
     * Update HDMI resolution selection Operation in this method will depend on
     * HDMILocalService
     */
    private void updateSelectedResolution() {
        Xlog.i(TAG, "HDMISettings>>updateSelectedResolution()");
        if (mHDMIService == null) {
            Xlog
                    .e(
                            TAG,
                            "HDMISettings>>updateSelectedResolution(), service have not been connected, wait");
            return;
        }
        String videoResolution = Settings.System.getString(
                getContentResolver(),
                HDMILocalService.KEY_HDMI_VIDEO_RESOLUTION);
        CharSequence[] resolutionValues = mVideoResolutionPref.getEntryValues();
        int selectIndex = -1;
        for (int i = 0; i < resolutionValues.length; i++) {
            if (resolutionValues[i].toString().equals(videoResolution)) {
                selectIndex = i;
                break;
            }
        }
        if (selectIndex != -1) {
            mVideoResolutionPref.setValueIndex(selectIndex);
        } else {
            Xlog
                    .i(TAG,
                            " set HDMI video resolution to default value, the first one");
            mVideoResolutionPref.setValueIndex(0);
            if (mHDMIService != null) {
                mHDMIService.setVideoResolution(Integer
                        .parseInt(resolutionValues[0].toString()));
            }
        }

    }

    @Override
    public void onDestroy() {
        getActivity().unbindService(mHDMIServiceConn);
        getActivity().unregisterReceiver(mLocalServiceReceiver);
        super.onDestroy();
    };

    /**
     * Each settings item's enabled status will depend on HDMI cable plug status
     */
    private void updateSettingsItemEnableStatus() {
        Xlog.i(TAG, "HDMISettings>>updateSettingsItemEnableStatus()");
        if (mHDMIService == null) {
            Xlog.i(TAG, "HDMI service has not connected, wait");
            return;
        }
        boolean isHDMICablePluged = mHDMIService.isCablePluged();
        mIsHDMIEnabled = Settings.System.getInt(getContentResolver(),
                HDMILocalService.KEY_HDMI_ENABLE_STATUS, 0) == 1;
        boolean shouldEnable = isHDMICablePluged && mIsHDMIEnabled;
        Xlog.d(TAG, "Is cable pluged?" + isHDMICablePluged + ", isHDMIEnabled?"
                + mIsHDMIEnabled);
        mToggleAudioPref.setEnabled(shouldEnable);
        if (mToggleVideoPref != null) {
            mToggleVideoPref.setEnabled(shouldEnable);
        }
        mVideoResolutionPref.setEnabled(shouldEnable);
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        if (mHDMIService == null) {
            Xlog
                    .e(TAG,
                            "HDMISettings  -- Connection to HDMI local service have not been established.");
            return false;
        }
        if (KEY_TOGGLE_HDMI.equals(key)) {
            boolean checked = ((Boolean) newValue).booleanValue();
            mHDMIService.enableHDMI(checked);
            Settings.System.putInt(getContentResolver(),
                    HDMILocalService.KEY_HDMI_ENABLE_STATUS, checked ? 1 : 0);
            updateSettingsItemEnableStatus();
        } else if (KEY_TOGGLE_AUDIO.equals(key)) {
            boolean checked = ((Boolean) newValue).booleanValue();
            mHDMIService.enableAudio(checked);
            Settings.System.putInt(getContentResolver(),
                    HDMILocalService.KEY_HDMI_AUDIO_STATUS, checked ? 1 : 0);
        } else if (KEY_TOGGLE_VIDEO.equals(key)) {
            boolean checked = ((Boolean) newValue).booleanValue();
            mHDMIService.enableVideo(checked);
            Settings.System.putInt(getContentResolver(),
                    HDMILocalService.KEY_HDMI_VIDEO_STATUS, checked ? 1 : 0);
        } else if (KEY_VIDEO_RESOLUTION.equals(key)) {
            String newResolution = (String) newValue;
            mHDMIService.setVideoResolution(Integer.parseInt(newResolution));
            Settings.System.putString(getContentResolver(),
                    HDMILocalService.KEY_HDMI_VIDEO_RESOLUTION, newResolution);
        }
        return true;
    }

}
