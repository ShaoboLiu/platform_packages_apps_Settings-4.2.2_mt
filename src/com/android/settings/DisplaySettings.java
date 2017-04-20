/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.settings;

import static android.provider.Settings.System.SCREEN_OFF_TIMEOUT;
import android.app.ActivityManagerNative;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;
import android.widget.ListView;

import com.android.internal.view.RotationPolicy;
import com.android.settings.ext.ISettingsMiscExt;
import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.xlog.Xlog;


import java.util.ArrayList;
import java.util.List;



public class DisplaySettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {
    private static final String TAG = "DisplaySettings";

    /** If there is no setting in the provider, use this. */
    private static final int FALLBACK_SCREEN_TIMEOUT_VALUE = 30000;

    private static final String KEY_SCREEN_TIMEOUT = "screen_timeout";
    private static final String KEY_ACCELEROMETER = "accelerometer";
    private static final String KEY_FONT_SIZE = "font_size";
    private static final String KEY_NOTIFICATION_PULSE = "notification_pulse";
    private static final String KEY_HDMI_SETTINGS = "hdmi_settings";
    private static final String KEY_TV_OUT = "tvout_settings";
    private static final String KEY_LANDSCAPE_LAUNCHER = "landscape_launcher";
    private static final String KEY_COLOR = "color";
    private static final String KEY_SCENES = "scenes";
    private static final String KEY_SCREEN_SAVER = "screensaver";

    private static final String DATA_STORE_NONE = "none";

    private CheckBoxPreference mAccelerometer;
    private ListPreference mFontSizePref;
    private CheckBoxPreference mNotificationPulse;

    private final Configuration mCurConfig = new Configuration();
    
    private ListPreference mScreenTimeoutPreference;
    private ListPreference mLandscapeLauncher;
    private Preference mScreenSaverPreference;
    private Preference mHDMISettings;
    private Preference mTVOut;
    private ISettingsMiscExt mExt;

    private boolean mIsUpdateFont;
    private final RotationPolicy.RotationPolicyListener mRotationPolicyListener =
            new RotationPolicy.RotationPolicyListener() {
        @Override
        public void onChange() {
            updateAccelerometerRotationCheckbox();
        }
    };
    
    private ContentObserver mScreenTimeoutObserver = new ContentObserver(new Handler()){
            @Override
            public void onChange(boolean selfChange) {
                Xlog.d(TAG,"mScreenTimeoutObserver omChanged");
                int value=Settings.System.getInt(
                        getContentResolver(), SCREEN_OFF_TIMEOUT, FALLBACK_SCREEN_TIMEOUT_VALUE);
                mScreenTimeoutPreference.setValue(String.valueOf(value));
                updateTimeoutPreferenceDescription(value);
                AlertDialog dlg = (AlertDialog)mScreenTimeoutPreference.getDialog();
                if (dlg == null || !dlg.isShowing()) {
                    return;
                }
                ListView listview = dlg.getListView();
                int checkedItem = mScreenTimeoutPreference.findIndexOfValue(
                mScreenTimeoutPreference.getValue());
                if(checkedItem > -1) {
                    listview.setItemChecked(checkedItem, true);
                    listview.setSelection(checkedItem);
                }
            }
      
        };   
    private BroadcastReceiver mPackageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context1, Intent intent) {
                Xlog.d(TAG,"package changed, update list");
                updateLandscapeList();
            }
        };  
    

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mExt = Utils.getMiscPlugin(getActivity());
        ContentResolver resolver = getActivity().getContentResolver();

        addPreferencesFromResource(R.xml.display_settings);

        mAccelerometer = (CheckBoxPreference) findPreference(KEY_ACCELEROMETER);
        mAccelerometer.setPersistent(false);
        if (RotationPolicy.isRotationLockToggleSupported(getActivity())) {
            // If rotation lock is supported, then we do not provide this option in
            // Display settings.  However, is still available in Accessibility settings.
            getPreferenceScreen().removePreference(mAccelerometer);
        }

        mScreenSaverPreference = findPreference(KEY_SCREEN_SAVER);
        if (mScreenSaverPreference != null
                && getResources().getBoolean(
                        com.android.internal.R.bool.config_enableDreams) == false) {
            getPreferenceScreen().removePreference(mScreenSaverPreference);
        }
        
        mScreenTimeoutPreference = (ListPreference) findPreference(KEY_SCREEN_TIMEOUT);
        /**M: for fix bug ALPS00266723 @{*/
        final long currentTimeout = getTimoutValue();
        Xlog.d(TAG,"currentTimeout=" + currentTimeout);
        /**@}*/
        mScreenTimeoutPreference.setValue(String.valueOf(currentTimeout));
        mScreenTimeoutPreference.setOnPreferenceChangeListener(this);
        disableUnusableTimeouts(mScreenTimeoutPreference);
        updateTimeoutPreferenceDescription(currentTimeout);

        mExt.setTimeoutPrefTitle(mScreenTimeoutPreference);

        mFontSizePref = (ListPreference) findPreference(KEY_FONT_SIZE);
        //M: for solve a bug
        updateFontSize(mFontSizePref);
        mFontSizePref.setOnPreferenceChangeListener(this);
        mNotificationPulse = (CheckBoxPreference) findPreference(KEY_NOTIFICATION_PULSE);
        if (mNotificationPulse != null
                && getResources().getBoolean(
                        com.android.internal.R.bool.config_intrusiveNotificationLed) == false) {
            getPreferenceScreen().removePreference(mNotificationPulse);
        } else {
            try {
                mNotificationPulse.setChecked(Settings.System.getInt(resolver,
                        Settings.System.NOTIFICATION_LIGHT_PULSE) == 1);
                mNotificationPulse.setOnPreferenceChangeListener(this);
            } catch (SettingNotFoundException snfe) {
                Log.e(TAG, Settings.System.NOTIFICATION_LIGHT_PULSE + " not found");
            }
        }

        mLandscapeLauncher = (ListPreference)findPreference(KEY_LANDSCAPE_LAUNCHER);                    
        mLandscapeLauncher.setOnPreferenceChangeListener(this);

        mHDMISettings = findPreference(KEY_HDMI_SETTINGS);
        if (!FeatureOption.MTK_HDMI_SUPPORT && mHDMISettings != null) {
            getPreferenceScreen().removePreference(mHDMISettings);
        }
        mTVOut = (Preference) findPreference(KEY_TV_OUT);
        if (!FeatureOption.MTK_TVOUT_SUPPORT && mTVOut != null) {
            getPreferenceScreen().removePreference(mTVOut);
        }
        Preference colorPref = findPreference(KEY_COLOR);
        Preference scencePref = findPreference(KEY_SCENES);
        if (!FeatureOption.MTK_THEMEMANAGER_APP) {
            Xlog.d(TAG,
                    "remove color preference as FeatureOption.MTK_THEMEMANAGER_APP="
                            + FeatureOption.MTK_THEMEMANAGER_APP);
            getPreferenceScreen().removePreference(colorPref);
            getPreferenceScreen().removePreference(scencePref);
        }
        
    }
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Xlog.d(TAG,"onConfigurationChanged");
        super.onConfigurationChanged(newConfig);
        mCurConfig.updateFrom(newConfig);
    }

    private int getTimoutValue() {
        int currentValue = Settings.System.getInt(getActivity()
                .getContentResolver(), SCREEN_OFF_TIMEOUT,
                FALLBACK_SCREEN_TIMEOUT_VALUE);
        Xlog.d(TAG, "getTimoutValue()---currentValue=" + currentValue);
        int bestMatch = 0;
        int timeout = 0;
        final CharSequence[] valuesTimeout = mScreenTimeoutPreference
                .getEntryValues();
        for (int i = 0; i < valuesTimeout.length; i++) {
            timeout = Integer.parseInt(valuesTimeout[i].toString());
            if (currentValue == timeout) {
                return currentValue;
            } else {
                if (currentValue > timeout) {
                    bestMatch = i;
                }
            }
        }
        Xlog.d(TAG, "getTimoutValue()---bestMatch=" + bestMatch);
        return Integer.parseInt(valuesTimeout[bestMatch].toString());

    }
    private void updateTimeoutPreferenceDescription(long currentTimeout) {
        ListPreference preference = mScreenTimeoutPreference;
        String summary;
        if (currentTimeout < 0) {
            // Unsupported value
            summary = "";
        } else {
            final CharSequence[] entries = preference.getEntries();
            final CharSequence[] values = preference.getEntryValues();
            int best = 0;
            for (int i = 0; i < values.length; i++) {
                long timeout = Long.parseLong(values[i].toString());
                if (currentTimeout >= timeout) {
                    best = i;
                }
            }
            ///M: to prevent index out of bounds @{
            if (entries.length != 0) {
                summary = preference.getContext().getString(
                        R.string.screen_timeout_summary, entries[best]);
            } else {
                summary = "";
            }
           ///M: @}
        }
        preference.setSummary(summary);
    }

    private void disableUnusableTimeouts(ListPreference screenTimeoutPreference) {
        final DevicePolicyManager dpm =
                (DevicePolicyManager) getActivity().getSystemService(
                Context.DEVICE_POLICY_SERVICE);
        final long maxTimeout = dpm != null ? dpm.getMaximumTimeToLock(null) : 0;
        if (maxTimeout == 0) {
            return; // policy not enforced
        }
        final CharSequence[] entries = screenTimeoutPreference.getEntries();
        final CharSequence[] values = screenTimeoutPreference.getEntryValues();
        ArrayList<CharSequence> revisedEntries = new ArrayList<CharSequence>();
        ArrayList<CharSequence> revisedValues = new ArrayList<CharSequence>();
        for (int i = 0; i < values.length; i++) {
            long timeout = Long.parseLong(values[i].toString());
            if (timeout <= maxTimeout) {
                revisedEntries.add(entries[i]);
                revisedValues.add(values[i]);
            }
        }
        if (revisedEntries.size() != entries.length || revisedValues.size() != values.length) {
            screenTimeoutPreference.setEntries(
                    revisedEntries.toArray(new CharSequence[revisedEntries.size()]));
            screenTimeoutPreference.setEntryValues(
                    revisedValues.toArray(new CharSequence[revisedValues.size()]));
            final int userPreference = Integer.parseInt(screenTimeoutPreference.getValue());
            if (userPreference <= maxTimeout) {
                screenTimeoutPreference.setValue(String.valueOf(userPreference));
            } else {
                // There will be no highlighted selection since nothing in the list matches
                // maxTimeout. The user can still select anything less than maxTimeout.
                // TODO: maybe append maxTimeout to the list and mark selected.
            }
        }
        screenTimeoutPreference.setEnabled(revisedEntries.size() > 0);
    }

    /**
     *  Update font size from EM
     *  Add by mtk54043
     */    
        private void updateFontSize(ListPreference fontSizePreference) {

        final CharSequence[] values = fontSizePreference.getEntryValues();

        float small = Settings.System.getFloat(getContentResolver(),
                Settings.System.FONT_SCALE_SMALL, -1);
        float large = Settings.System.getFloat(getContentResolver(),
                Settings.System.FONT_SCALE_LARGE, -1);
        float extraLarge = Settings.System.getFloat(getContentResolver(),
                Settings.System.FONT_SCALE_EXTRALARGE, -1);
        
        if (small != -1 || large != -1 || extraLarge != -1) {

            if (null != values[0] && small != -1) {
                values[0] = small + "";
                Xlog.d(TAG, "update font size : " + values[0]);
            }
            if (null != values[2] && large != -1) {
                values[2] = large + "";
                Xlog.d(TAG, "update font size : " + values[2]);
            }
            if (null != values[3] && extraLarge != -1) {
                values[3] = extraLarge + "";
                Xlog.d(TAG, "update font size : " + values[3]);
            }

            if (null != values) {
                fontSizePreference.setEntryValues(values);
            }

            mIsUpdateFont = true;
        }
    }

    ///M: modify by MTK for EM
    int floatToIndex(float val) {
        if (mIsUpdateFont) {           
            final CharSequence[] indicesEntry = mFontSizePref.getEntryValues();
            Xlog.d(TAG, "current font size : " + val);
            for (int i = 0; i < indicesEntry.length; i++) {
                float thisVal = Float.parseFloat(indicesEntry[i].toString());
                if (val == thisVal) {
                    Xlog.d(TAG, "Select : " + i);
                    return i;
                }
            }                      
        } else {
            String[] indices = getResources().getStringArray(R.array.entryvalues_font_size);
            float lastVal = Float.parseFloat(indices[0]);
            for (int i = 1; i < indices.length; i++) {
                float thisVal = Float.parseFloat(indices[i]);
                if (val < (lastVal + (thisVal - lastVal) * .5f)) {
                    return i - 1;
                }
                lastVal = thisVal;
            }
            return indices.length - 1;
        }
        return 1;
    }
    
    public void readFontSizePreference(ListPreference pref) {
        try {
            mCurConfig.updateFrom(ActivityManagerNative.getDefault().getConfiguration());
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to retrieve font size");
        }

        // mark the appropriate item in the preferences list
        int index = floatToIndex(mCurConfig.fontScale);
        pref.setValueIndex(index);

        // report the current size in the summary text
        final Resources res = getResources();
        String[] fontSizeNames = res.getStringArray(R.array.entries_font_size);
        pref.setSummary(String.format(res.getString(R.string.summary_font_size),
                fontSizeNames[index]));
    }
    
    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG,"onResume of DisplaySettings");
        updateState();
        ///M: update the landscape list to remove or keep
        updateLandscapeList();
        RotationPolicy.registerRotationPolicyListener(getActivity(),
                mRotationPolicyListener);
        getContentResolver().registerContentObserver(Settings.System.getUriFor(SCREEN_OFF_TIMEOUT),
                false, mScreenTimeoutObserver);        
        
        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme("package");
        getActivity().registerReceiver(mPackageReceiver, filter);
    }

    @Override
    public void onPause() {
        super.onPause();

        RotationPolicy.unregisterRotationPolicyListener(getActivity(),
                mRotationPolicyListener);
        getContentResolver().unregisterContentObserver(mScreenTimeoutObserver);        
        getActivity().unregisterReceiver(mPackageReceiver);
        
    }

    private void updateState() {
        updateAccelerometerRotationCheckbox();
        readFontSizePreference(mFontSizePref);
        updateScreenSaverSummary();
    }

    private void updateScreenSaverSummary() {
        mScreenSaverPreference.setSummary(
            DreamSettings.isScreenSaverEnabled(mScreenSaverPreference.getContext())
                ? R.string.screensaver_settings_summary_on
                : R.string.screensaver_settings_summary_off);
    }

    private void updateAccelerometerRotationCheckbox() {
        if (getActivity() == null) return;

        mAccelerometer.setChecked(!RotationPolicy.isRotationLocked(getActivity()));
        if (mLandscapeLauncher != null) {
            if (mAccelerometer.isChecked()){
                mLandscapeLauncher.setEnabled(true);    
            } else {
                mLandscapeLauncher.setEnabled(false);    
            }    
        }
    }

    public void writeFontSizePreference(Object objValue) {
        try {
            mCurConfig.fontScale = Float.parseFloat(objValue.toString());
            ActivityManagerNative.getDefault().updatePersistentConfiguration(mCurConfig);
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to save font size");
        }
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {
        if (preference == mAccelerometer) {
            RotationPolicy.setRotationLockForAccessibility(getActivity(),
                    !mAccelerometer.isChecked());
            if (mLandscapeLauncher != null) {
                if (mAccelerometer.isChecked()) {
                    mLandscapeLauncher.setEnabled(true);
                } else {
                    mLandscapeLauncher.setEnabled(false);
                }
            }
        } else if (preference == mNotificationPulse) {
            boolean value = mNotificationPulse.isChecked();
            Settings.System.putInt(getContentResolver(),
                    Settings.System.NOTIFICATION_LIGHT_PULSE, value ? 1 : 0);
            return true;
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    public boolean onPreferenceChange(Preference preference, Object objValue) {
        final String key = preference.getKey();
        if (KEY_SCREEN_TIMEOUT.equals(key)) {
            int value = Integer.parseInt((String) objValue);
            try {
                Settings.System.putInt(getContentResolver(), SCREEN_OFF_TIMEOUT, value);
                updateTimeoutPreferenceDescription(value);
            } catch (NumberFormatException e) {
                Log.e(TAG, "could not persist screen timeout setting", e);
            }
        } else if (KEY_FONT_SIZE.equals(key)) {
            writeFontSizePreference(objValue);
        } else if (KEY_LANDSCAPE_LAUNCHER.equals(key)) {
            Xlog.d(TAG, "select landscape launcher   " + objValue);
            if (mLandscapeLauncher != null) {
                mLandscapeLauncher.setValue((String)objValue);
                mLandscapeLauncher.setSummary(mLandscapeLauncher.getEntry());   
            }
            Settings.System.putString(getContentResolver(), Settings.System.LANDSCAPE_LAUNCHER,(String)objValue);
        }
        

        return true;
    }
    
    private void updateLandscapeList() {
        int appListSize = 0;

        Intent intent = new Intent(Intent.ACTION_ROTATED_MAIN);
        List<ResolveInfo> mLandscapeLauncherApps = getPackageManager()
                .queryIntentActivities(intent, 0);

        if (mLandscapeLauncherApps != null
                && mLandscapeLauncherApps.size() != 0) {
            Xlog.d(TAG, "mLandscapeLauncherApps.size()="
                    + mLandscapeLauncherApps.size());
            appListSize = mLandscapeLauncherApps.size();
            // If it is already added , this will do nothing.
            getPreferenceScreen().addPreference(mLandscapeLauncher);
        } else {
            Xlog.d(TAG, "landscape launcher query return null or size 0 ");
            // There is no landscape launcher installed , remove the preference.
            getPreferenceScreen().removePreference(mLandscapeLauncher);
            Settings.System.putString(getContentResolver(),
                    Settings.System.LANDSCAPE_LAUNCHER, DATA_STORE_NONE);
            return;
        }
        CharSequence[] appStrs = new CharSequence[appListSize + 1];
        CharSequence[] appValues = new CharSequence[appListSize + 1];
        appStrs[0] = getString(R.string.landscape_launcher_none);
        appValues[0] = DATA_STORE_NONE;
        String current = Settings.System.getString(getContentResolver(),
                Settings.System.LANDSCAPE_LAUNCHER);
        if (current == null) {
            Settings.System.putString(getContentResolver(),
                    Settings.System.LANDSCAPE_LAUNCHER, DATA_STORE_NONE);
            current = DATA_STORE_NONE;
        }

        int i = 1;
        int setIdx = 0;
        if (mLandscapeLauncherApps != null) {

            PackageManager pm = getPackageManager();
            for (ResolveInfo info : mLandscapeLauncherApps) {
                Xlog.i(TAG, "resolve app : " + info.activityInfo.packageName
                        + " " + info.activityInfo.name);
                appStrs[i] = info.activityInfo.loadLabel(pm);
                appValues[i] = info.activityInfo.packageName + "/"
                        + info.activityInfo.name;
                if (current.equals(appValues[i])) {
                    setIdx = i;
                }
                ++i;
            }
        }
        if (setIdx == 0 && !current.equals(DATA_STORE_NONE)) {
            // Because current package maybe uninstalled, so no match found ,
            // set it back to None.
            Settings.System.putString(getContentResolver(),
                    Settings.System.LANDSCAPE_LAUNCHER, DATA_STORE_NONE);
        }
        mLandscapeLauncher.setEntries(appStrs);
        mLandscapeLauncher.setEntryValues(appValues);
        mLandscapeLauncher.setValueIndex(setIdx);
        mLandscapeLauncher.setSummary(appStrs[setIdx]);
    }
    

    
}
