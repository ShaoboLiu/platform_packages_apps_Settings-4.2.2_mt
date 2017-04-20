/*
 * Copyright (C) 2011 The Android Open Source Project
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


import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentQueryMap;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.Observable;
import java.util.Observer;

import com.android.internal.telephony.Phone;

import com.mediatek.lbs.AgpsSettings;
import com.mediatek.lbs.EPOSettings;

import com.mediatek.common.featureoption.FeatureOption;

import com.mediatek.common.agps.MtkAgpsManager;
import com.mediatek.common.epo.MtkEpoClientManager;

/**
 * Gesture lock pattern settings.
 */
public class LocationSettings extends SettingsPreferenceFragment
        implements OnPreferenceChangeListener {
    
    private static final String TAG = "LocationSettings";
    
    // Location Settings
    private static final String LOCATION_CATEGORY = "location_category";
    
    private static final String KEY_LOCATION_NETWORK = "location_network";
    private static final String KEY_LOCATION_GPS = "location_gps";
    private static final String KEY_USE_LOCATION = "location_use_for_services";
    
    /// M: epo enable and epo settings preference key
    private static final String KEY_EPO_ENABLER = "epo_enabler";
    private static final String KEY_EPO_SETTINGS = "epo_settings";
    
    /// M: Agps enable and agps settings preference key
    private static final String KEY_AGPS_ENABLER = "agps_enabler";
    private static final String KEY_AGPS_SETTINGS = "agps_settings";

    private CheckBoxPreference mNetwork;
    private CheckBoxPreference mGps;
    private CheckBoxPreference mUseLocation;
    
    /// M: epo enable and epo settings preferenc
    private MtkEpoClientManager mEpoMgr;
    private CheckBoxPreference  mEpoEnalberPref;
    private Preference          mEpoSettingPref;

    /// M: Agps enable and agps settings preference
    private MtkAgpsManager      mAgpsMgr;
    private CheckBoxPreference  mAgpsCB;
    private Preference          mAgpsPref;
    
    /// M: Epo and agps confirm dialog id
    private static final int CONFIRM_EPO_DIALOG_ID = 0;
    private static final int CONFIRM_AGPS_DIALOG_ID = 1;
    
    // These provide support for receiving notification when Location Manager settings change.
    // This is necessary because the Network Location Provider can change settings
    // if the user does not confirm enabling the provider.
    private ContentQueryMap mContentQueryMap;

    private Observer mSettingsObserver;

    @Override
    public void onStart() {
        super.onStart();
        // listen for Location Manager settings changes
        Cursor settingsCursor = getContentResolver().query(Settings.Secure.CONTENT_URI, null,
                "(" + Settings.System.NAME + "=?)",
                new String[]{Settings.Secure.LOCATION_PROVIDERS_ALLOWED},
                null);
        mContentQueryMap = new ContentQueryMap(settingsCursor, Settings.System.NAME, true, null);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mSettingsObserver != null) {
            mContentQueryMap.deleteObserver(mSettingsObserver);
        }
    }

    private PreferenceScreen createPreferenceHierarchy() {

        PreferenceScreen root = getPreferenceScreen();
        if (root != null) {
            root.removeAll();
        }
        addPreferencesFromResource(R.xml.location_settings);
        root = getPreferenceScreen();

        mNetwork = (CheckBoxPreference) root.findPreference(KEY_LOCATION_NETWORK);
        mGps = (CheckBoxPreference) root.findPreference(KEY_LOCATION_GPS);
        
        /// M: If not support EPO, remove EPO related preference @{
        PreferenceCategory category = (PreferenceCategory)root.findPreference(LOCATION_CATEGORY);
        mEpoEnalberPref = (CheckBoxPreference)findPreference(KEY_EPO_ENABLER);
        mEpoSettingPref = findPreference(KEY_EPO_SETTINGS);
        
        if(!FeatureOption.MTK_GPS_SUPPORT && !FeatureOption.MTK_EMULATOR_SUPPORT && category != null){
            if(mGps != null){
                category.removePreference(mGps);
            }
            if(mEpoEnalberPref != null){
                category.removePreference(mEpoEnalberPref);
            }
            if(mEpoSettingPref != null){
                category.removePreference(mEpoSettingPref);
            }
        }
        mEpoMgr = (MtkEpoClientManager)getSystemService(Context.MTK_EPO_CLIENT_SERVICE);
        /// @}

        /// M: If not support Agps, remove Agps related preference @{
        if(FeatureOption.MTK_AGPS_APP && (FeatureOption.MTK_GPS_SUPPORT || FeatureOption.MTK_EMULATOR_SUPPORT)){
            mAgpsMgr = (MtkAgpsManager)getSystemService(Context.MTK_AGPS_SERVICE);
        }        
        mAgpsPref = findPreference(KEY_AGPS_SETTINGS);//it is AGPS_Settings item
        mAgpsCB = (CheckBoxPreference)findPreference(KEY_AGPS_ENABLER);
        
        if (category != null && ((!FeatureOption.MTK_GPS_SUPPORT && !FeatureOption.MTK_EMULATOR_SUPPORT) || !FeatureOption.MTK_AGPS_APP)) {
            if(mAgpsPref != null){
                category.removePreference(mAgpsPref);
            }
            if(mAgpsCB != null){
                category.removePreference(mAgpsCB);
            }
        }
        /// @}
        
        if (GoogleLocationSettingHelper.isAvailable(getActivity())) {
            // GSF present, Add setting for 'Use My Location'
            CheckBoxPreference useLocation = new CheckBoxPreference(getActivity());
            useLocation.setKey(KEY_USE_LOCATION);
            useLocation.setTitle(R.string.use_location_title);
            useLocation.setSummary(R.string.use_location_summary);
            useLocation.setChecked(
                    GoogleLocationSettingHelper.getUseLocationForServices(getActivity())
                    == GoogleLocationSettingHelper.USE_LOCATION_FOR_SERVICES_ON);
            useLocation.setPersistent(false);
            useLocation.setOnPreferenceChangeListener(this);
            getPreferenceScreen().addPreference(useLocation);
            mUseLocation = useLocation;
        }

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();

        // Make sure we reload the preference hierarchy since some of these settings
        // depend on others...
        createPreferenceHierarchy();
        updateLocationToggles();
        
        if (mSettingsObserver == null) {
            mSettingsObserver = new Observer() {
                public void update(Observable o, Object arg) {
                    updateLocationToggles();
                }
            };
        }

        mContentQueryMap.addObserver(mSettingsObserver);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {

        if (preference == mNetwork) {
            Settings.Secure.setLocationProviderEnabled(getContentResolver(),
                    LocationManager.NETWORK_PROVIDER, mNetwork.isChecked());
        } else if (preference == mGps) {
            boolean enabled = mGps.isChecked();
            Settings.Secure.setLocationProviderEnabled(getContentResolver(),
                    LocationManager.GPS_PROVIDER, enabled);
        /// M: Click Epo/Agps enable pref and setting pref @{
        } else if(preference == mEpoEnalberPref){
            boolean flag = mEpoEnalberPref.isChecked();
            if(flag){
                mEpoEnalberPref.setChecked(false);
                showDialog(CONFIRM_EPO_DIALOG_ID);
            }else{
                mEpoMgr.disable();
            }
        } else if(preference == mEpoSettingPref){
            ((PreferenceActivity) getActivity()).startPreferencePanel(
                    EPOSettings.class.getName(), null,
                    R.string.epo_entrance_title, null, null, 0);
        }else if (preference == mAgpsCB) {
            if(mAgpsCB.isChecked()) {
                mAgpsCB.setChecked(false);
                showDialog(CONFIRM_AGPS_DIALOG_ID);
            } else {
                mAgpsMgr.disable();
            }
        } else if(preference == mAgpsPref){
            ((PreferenceActivity) getActivity()).startPreferencePanel(
                    AgpsSettings.class.getName(), null,
                    R.string.agps_settings_title, null, null, 0);
        }
        /// @}
        else {
            // If we didn't handle it, let preferences handle it.
            return super.onPreferenceTreeClick(preferenceScreen, preference);
        }

        return true;
    }

    /*
     * Creates toggles for each available location provider
     */
    private void updateLocationToggles() {
        ContentResolver res = getContentResolver();
        boolean gpsEnabled = Settings.Secure.isLocationProviderEnabled(
                res, LocationManager.GPS_PROVIDER);
        mNetwork.setChecked(Settings.Secure.isLocationProviderEnabled(
                res, LocationManager.NETWORK_PROVIDER));
        mGps.setChecked(gpsEnabled);
        
        /// M: Accoring o the Agps/Epo status, update the pref @{
        if(mAgpsCB != null && mAgpsMgr != null) {
            mAgpsCB.setChecked(mAgpsMgr.getStatus());
        }

        if(mEpoMgr != null) {
            mEpoEnalberPref.setChecked(mEpoMgr.getStatus());
        }
        /// @}
    }

    /**
     * see confirmPatternThenDisableAndClear
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        createPreferenceHierarchy();
    }

    public boolean onPreferenceChange(Preference preference, Object value) {
        if (preference == mUseLocation) {
            boolean newValue = (value == null ? false : (Boolean) value);
            GoogleLocationSettingHelper.setUseLocationForServices(getActivity(), newValue);
            // We don't want to change the value immediately here, since the user may click
            // disagree in the dialog that pops up. When the activity we just launched exits, this
            // activity will be restated and the new value re-read, so the checkbox will get its
            // new value then.
            return false;
        }
        return true;
    }

    /// M: create Agps/Epo confirm dialog @{
    public Dialog onCreateDialog(int id){
        
        Dialog dialog = null;
        switch(id){
            case CONFIRM_EPO_DIALOG_ID:
                dialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.epo_enable_confirm_title)
                .setIcon(com.android.internal.R.drawable.ic_dialog_alert)
                .setMessage(R.string.epo_enable_confirm_message)
                .setPositiveButton(R.string.epo_enable_confirm_allow,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialoginterface, int i) {                                           
                                mEpoEnalberPref.setChecked(true);
                                mEpoMgr.enable();
                            }
                }).setNegativeButton(R.string.epo_enable_confirm_deny,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialoginterface, int i) {
                                mEpoEnalberPref.setChecked(false);
                                Log.i(TAG, "User Deny Enbale EPO Service");
                            }
                }).create();
                break;
            case CONFIRM_AGPS_DIALOG_ID:
                dialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.agps_enable_confirm_title)
                .setMessage(R.string.agps_enable_confirm)
                .setIcon(com.android.internal.R.drawable.ic_dialog_alert)
                .setPositiveButton(R.string.agps_enable_confirm_allow,
                        new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialoginterface, int i) {
                        mAgpsCB.setChecked(true);
                        mAgpsMgr.enable();
                    }
                }).setNegativeButton(R.string.agps_enable_confirm_deny,
                        new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialoginterface, int i) {
                        /// M: see cr [alps00339992] enabe a-gps should be disabeld when you tap cancel
                        Log.d(TAG, "-->mAgpsMgr.getStatus()"+ mAgpsMgr.getStatus());
                        if( mAgpsMgr.getStatus() == false ){
                            mAgpsCB.setChecked(false);
                        }
                        Log.i(TAG, "DenyDenyDeny");
                    }
                }).create();  
                break;
        }
        return dialog;
    }
    /// @}
}
