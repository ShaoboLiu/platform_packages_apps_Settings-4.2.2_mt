package com.android.settings.ext;

import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;

import com.mediatek.xlog.Xlog;

public class DefaultSimManagementExt implements ISimManagementExt {
    private static final String TAG = "DefaultSimManagementExt";
    private static final String KEY_3G_SERVICE_SETTING = "3g_service_settings";
    private static final String KEY_AUTO_WAP_PUSH = "wap_push_settings";
    private static final String KEY_SIM_STATUS = "status_info";
    /**
     * update the preference screen of sim management
     * @param parent parent preference
     */
    public void updateSimManagementPref(PreferenceGroup parent) {
        Xlog.d(TAG,"updateSimManagementPref()");
        PreferenceScreen pref3GService = null;
        PreferenceScreen prefWapPush = null;
        PreferenceScreen prefStatus = null;
        if (parent != null) {
            pref3GService = (PreferenceScreen)parent.findPreference(KEY_3G_SERVICE_SETTING);
            prefWapPush = (PreferenceScreen)parent.findPreference(KEY_AUTO_WAP_PUSH);
            prefStatus = (PreferenceScreen)parent.findPreference(KEY_SIM_STATUS);
        }
        if (pref3GService != null) {
            Xlog.d(TAG,"updateSimManagementPref()---remove pref3GService");
            parent.removePreference(pref3GService);
        }
        if (prefWapPush != null) {
            Xlog.d(TAG,"updateSimManagementPref()---remove prefWapPush");
            parent.removePreference(prefWapPush);
        }
        if (prefStatus != null) {
            Xlog.d(TAG,"updateSimManagementPref()---remove prefStatus");
            parent.removePreference(prefStatus);
        }
    }
}