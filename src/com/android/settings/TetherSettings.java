/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDun;
import android.bluetooth.BluetoothPan;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemProperties;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.provider.Settings.System;
import android.webkit.WebView;

import com.android.settings.ext.ISettingsMiscExt;
import com.android.settings.wifi.WifiApDialog;
import com.android.settings.wifi.WifiApEnabler;
import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.xlog.Xlog;

import java.util.ArrayList;

/*
 * Displays preferences for Tethering.
 */
public class TetherSettings extends SettingsPreferenceFragment
        implements DialogInterface.OnClickListener, Preference.OnPreferenceChangeListener {
    private static final String TAG = "TetherSettings";
    private static final String USB_TETHER_SETTINGS = "usb_tether_settings";
    private static final String ENABLE_WIFI_AP = "enable_wifi_ap";
    private static final String ENABLE_BLUETOOTH_TETHERING = "enable_bluetooth_tethering";
    private static final String TETHERED_IPV6 = "tethered_ipv6";
    private static final String WIFI_AUTO_DISABLE = "wifi_auto_disable";
    /// M: @{
    private static final String TETHER_APN_SETTING = "tether_apn_settings";
    private static final int WIFI_AP_AUTO_CHANNEL_TEXT = R.string.wifi_tether_auto_channel_text;
    private static final int WIFI_AP_AUTO_CHANNEL_WIDTH_TEXT = R.string.wifi_tether_auto_channel_width_text;
    private static final int WIFI_AP_FIX_CHANNEL_WIDTH_TEXT = R.string.wifi_tether_fix_channel_width_text;
    /// @}
    private static final int DIALOG_AP_SETTINGS = 1;

    private WebView mView;
    private CheckBoxPreference mUsbTether;

    private WifiApEnabler mWifiApEnabler;
    private CheckBoxPreference mEnableWifiAp;
    private ListPreference mWifiAutoDisable;

    /// M:
    private Preference mTetherApnSetting;
    private ListPreference mTetherIpv6;
    private CheckBoxPreference mBluetoothTether;

    private BroadcastReceiver mTetherChangeReceiver;

    private String[] mUsbRegexs;

    private String[] mWifiRegexs;

    private String[] mBluetoothRegexs;
    private BluetoothPan mBluetoothPan;
    ///M:
    private BluetoothDun mBluetoothDun;

    private static final String WIFI_AP_SSID_AND_SECURITY = "wifi_ap_ssid_and_security";
    private static final int CONFIG_SUBTEXT = R.string.wifi_tether_configure_subtext;

    private String[] mSecurityType;
    private Preference mCreateNetwork;

    private WifiApDialog mDialog;
    private WifiManager mWifiManager;
    private WifiConfiguration mWifiConfig = null;

    private boolean mUsbConnected;
    private boolean mMassStorageActive;

    private boolean mBluetoothEnableForTether;
    /// M:  @{
    private boolean mUsbTethering = false;
    private boolean mUsbTetherCheckEnable = false;
    private boolean mUsbConfigured;
    /** M: for bug solving, ALPS00331223 */
    private boolean mUsbUnTetherDone = true; // must set to "true" for lauch setting case after startup
    private boolean mUsbTetherDone = true; // must set to "true" for lauch setting case after startup    
    private boolean mUsbTetherFail = false; // must set to "false" for lauch setting case after startup
    
    private boolean mUsbHwDisconnected;
    /// @}

    private static final int INVALID             = -1;
    private static final int WIFI_TETHERING      = 0;
    private static final int USB_TETHERING       = 1;
    private static final int BLUETOOTH_TETHERING = 2;

    /* One of INVALID, WIFI_TETHERING, USB_TETHERING or BLUETOOTH_TETHERING */
    private int mTetherChoice = INVALID;

    /* Stores the package name and the class name of the provisioning app */
    private String[] mProvisionApp;
    private static final int PROVISION_REQUEST = 0;

    ISettingsMiscExt mExt;
    private int mBtErrorIpv4;
    private int mBtErrorIpv6;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.tether_prefs);

        /// M: get plugin
        mExt = Utils.getMiscPlugin(getActivity());

        final Activity activity = getActivity();
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            adapter.getProfileProxy(activity.getApplicationContext(), mProfileServiceListener,
                    BluetoothProfile.PAN);
        }
        /// M:
        mBluetoothDun = new BluetoothDun(getActivity().getApplicationContext());

        mEnableWifiAp =
                (CheckBoxPreference) findPreference(ENABLE_WIFI_AP);
        /// M: auto disable preference
        mWifiAutoDisable = (ListPreference) findPreference(WIFI_AUTO_DISABLE);
        Preference wifiApSettings = findPreference(WIFI_AP_SSID_AND_SECURITY);
        mUsbTether = (CheckBoxPreference) findPreference(USB_TETHER_SETTINGS);
        mBluetoothTether = (CheckBoxPreference) findPreference(ENABLE_BLUETOOTH_TETHERING);
        /// M: apn settings
        mTetherApnSetting = (Preference) findPreference(TETHER_APN_SETTING);
        mTetherIpv6 = (ListPreference) findPreference(TETHERED_IPV6);
        ConnectivityManager cm =
                (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);

        mUsbRegexs = cm.getTetherableUsbRegexs();
        mWifiRegexs = cm.getTetherableWifiRegexs();
        mBluetoothRegexs = cm.getTetherableBluetoothRegexs();

        final boolean usbAvailable = mUsbRegexs.length != 0;
        final boolean wifiAvailable = mWifiRegexs.length != 0;
        final boolean bluetoothAvailable = mBluetoothRegexs.length != 0;

        if (!usbAvailable || Utils.isMonkeyRunning()) {
            getPreferenceScreen().removePreference(mUsbTether);
        }

        if (wifiAvailable && !Utils.isMonkeyRunning() && FeatureOption.MTK_WIFI_HOTSPOT_SUPPORT) {
            mWifiApEnabler = new WifiApEnabler(activity, mEnableWifiAp);
            initWifiTethering();
        } else {
            getPreferenceScreen().removePreference(mEnableWifiAp);
            /// M: remove auto disable preference if wifi not available or monkey is running
            getPreferenceScreen().removePreference(mWifiAutoDisable);
            getPreferenceScreen().removePreference(wifiApSettings);
        }

        if (!bluetoothAvailable) {
            getPreferenceScreen().removePreference(mBluetoothTether);
        } else {
            if (mBluetoothPan != null && mBluetoothPan.isTetheringOn()
                && mBluetoothDun != null && mBluetoothDun.isTetheringOn()) {
                mBluetoothTether.setChecked(true);
            } else {
                mBluetoothTether.setChecked(false);
            }
        }

        /// M: remove tether apn settings
        mExt.removeTetherApnSettings(getPreferenceScreen(), mTetherApnSetting);

        mProvisionApp = getResources().getStringArray(
                com.android.internal.R.array.config_mobile_hotspot_provision_app);

        mView = new WebView(activity);

        /// M: @{
        if (FeatureOption.MTK_TETHERINGIPV6_SUPPORT) {
            if (mTetherIpv6 != null) {
                mTetherIpv6.setOnPreferenceChangeListener(this);
            }
        } else {
            getPreferenceScreen().removePreference(mTetherIpv6);
        }
        /// @}
    }

    /*
     * M: update ipv4&ipv6 setting preference
     */
    private void updateIpv6Preference() {
        if (mTetherIpv6 != null) {
            mTetherIpv6.setEnabled(!mUsbTether.isChecked() 
                && !mBluetoothTether.isChecked() 
                && !mEnableWifiAp.isChecked());
            ConnectivityManager cm = 
                (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                int ipv6Value = cm.getTetheringIpv6Enable() ? 1 : 0;
                mTetherIpv6.setValueIndex(ipv6Value);
                mTetherIpv6.setSummary(getResources().getStringArray(
                    R.array.tethered_ipv6_entries)[ipv6Value]);
            }
        }
    }

    private void initWifiTethering() {
        final Activity activity = getActivity();
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mWifiConfig = mWifiManager.getWifiApConfiguration();
        mSecurityType = getResources().getStringArray(R.array.wifi_ap_security);

        mCreateNetwork = findPreference(WIFI_AP_SSID_AND_SECURITY);

        if (mWifiConfig == null) {
            String s = "";
            /// M: get hotspot SSID
            s = Utils.getMiscPlugin(activity).getTetherWifiSSID(activity);
            mCreateNetwork.setSummary(String.format(activity.getString(CONFIG_SUBTEXT),
                    s, mSecurityType[WifiApDialog.OPEN_INDEX]));
        } else {
            int index = WifiApDialog.getSecurityTypeIndex(mWifiConfig);
            mCreateNetwork.setSummary(String.format(activity.getString(CONFIG_SUBTEXT),
                    mWifiConfig.SSID,
                    mSecurityType[index]));
        }
    }

    private BluetoothProfile.ServiceListener mProfileServiceListener =
        new BluetoothProfile.ServiceListener() {
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mBluetoothPan = (BluetoothPan) proxy;
        }
        public void onServiceDisconnected(int profile) {
            mBluetoothPan = null;
        }
    };

    @Override
    public Dialog onCreateDialog(int id) {
        if (id == DIALOG_AP_SETTINGS) {
            final Activity activity = getActivity();
            mDialog = new WifiApDialog(activity, this, mWifiConfig);
            return mDialog;
        }

        return null;
    }

    private class TetherChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context content, Intent intent) {
            String action = intent.getAction();
            /// M: print log
            Xlog.d(TAG, "TetherChangeReceiver - onReceive, action is " + action);
            
            if (action.equals(ConnectivityManager.ACTION_TETHER_STATE_CHANGED)) {
                // TODO - this should understand the interface types
                ArrayList<String> available = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_AVAILABLE_TETHER);
                ArrayList<String> active = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_ACTIVE_TETHER);
                ArrayList<String> errored = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_ERRORED_TETHER);

                /** M: for bug solving, ALPS00331223 */
                mUsbUnTetherDone = intent.getBooleanExtra("UnTetherDone", false);
                mUsbTetherDone = intent.getBooleanExtra("TetherDone", false);
                mUsbTetherFail = intent.getBooleanExtra("TetherFail", false);
                     
                /// M: print log
                Xlog.d(TAG, "mUsbUnTetherDone? :" + mUsbUnTetherDone + " , mUsbTetherDonel? :" + 
                    mUsbTetherDone + " , tether fail? :" + mUsbTetherFail);                
                updateState(available.toArray(new String[available.size()]),
                        active.toArray(new String[active.size()]),
                        errored.toArray(new String[errored.size()]));
            } else if (action.equals(Intent.ACTION_MEDIA_SHARED)) {
                mMassStorageActive = true;
                updateState();
            } else if (action.equals(Intent.ACTION_MEDIA_UNSHARED)) {
                mMassStorageActive = false;
                updateState();
            } else if (action.equals(UsbManager.ACTION_USB_STATE)) {
                mUsbConnected = intent.getBooleanExtra(UsbManager.USB_CONNECTED, false);
                /// M: @{
                mUsbConfigured = intent.getBooleanExtra(UsbManager.USB_CONFIGURED, false);
                mUsbHwDisconnected = intent.getBooleanExtra("USB_HW_DISCONNECTED", false);
                Xlog.d(TAG, "TetherChangeReceiver - ACTION_USB_STATE mUsbConnected: " + mUsbConnected + 
                        ", mUsbConfigured:  " + mUsbConfigured + ", mUsbHwDisconnected: " + mUsbHwDisconnected);
                /// @}
                updateState();
            } else if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                if (mBluetoothEnableForTether) {
                    switch (intent
                            .getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                        case BluetoothAdapter.STATE_ON:
                            mBluetoothPan.setBluetoothTethering(true);
                            /// M:
                            mBluetoothDun.setBluetoothTethering(true);
                            mBluetoothEnableForTether = false;
                            break;

                        case BluetoothAdapter.STATE_OFF:
                        case BluetoothAdapter.ERROR:
                            mBluetoothEnableForTether = false;
                            break;

                        default:
                            // ignore transition states
                    }
                }
                updateState();
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        final Activity activity = getActivity();

        mMassStorageActive = Environment.MEDIA_SHARED.equals(Environment.getExternalStorageState());
        mTetherChangeReceiver = new TetherChangeReceiver();
        IntentFilter filter = new IntentFilter(ConnectivityManager.ACTION_TETHER_STATE_CHANGED);
        Intent intent = activity.registerReceiver(mTetherChangeReceiver, filter);

        filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_STATE);
        activity.registerReceiver(mTetherChangeReceiver, filter);

        filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_SHARED);
        filter.addAction(Intent.ACTION_MEDIA_UNSHARED);
        filter.addDataScheme("file");
        activity.registerReceiver(mTetherChangeReceiver, filter);

        filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        activity.registerReceiver(mTetherChangeReceiver, filter);

        if (intent != null) {
            mTetherChangeReceiver.onReceive(activity, intent);
        }
        if (mWifiApEnabler != null) {
            mEnableWifiAp.setOnPreferenceChangeListener(this);
            mWifiApEnabler.setTetherSettings(this);
            mWifiApEnabler.resume();
        }
        /// M: init auto disable wifi hotspot preference @{
        if (mWifiAutoDisable != null) {
            mWifiAutoDisable.setOnPreferenceChangeListener(this);
            int value = System.getInt(getContentResolver(),System.WIFI_HOTSPOT_AUTO_DISABLE,
                                System.WIFI_HOTSPOT_AUTO_DISABLE_FOR_FIVE_MINS);
            mWifiAutoDisable.setValue(String.valueOf(value));
        }
        /// @}
        updateState();
    }

    @Override
    public void onStop() {
        super.onStop();
        getActivity().unregisterReceiver(mTetherChangeReceiver);
        mTetherChangeReceiver = null;
        if (mWifiApEnabler != null) {
            mEnableWifiAp.setOnPreferenceChangeListener(null);
            /// M: set change listener
            mWifiAutoDisable.setOnPreferenceChangeListener(null);
            mWifiApEnabler.pause();
        }
    }

    private void updateState() {
        ConnectivityManager cm =
                (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);

        String[] available = cm.getTetherableIfaces();
        String[] tethered = cm.getTetheredIfaces();
        String[] errored = cm.getTetheringErroredIfaces();
        updateState(available, tethered, errored);
    }

    private void updateState(String[] available, String[] tethered,
            String[] errored) {

        Xlog.d(TAG, "=======> updateState - mUsbConnected: " + mUsbConnected + 
                ", mUsbConfigured:  " + mUsbConfigured + ", mUsbHwDisconnected: " + 
                mUsbHwDisconnected + ", checked: " + mUsbTether.isChecked() + 
                ", mUsbUnTetherDone: " + mUsbUnTetherDone + ", mUsbTetherDone: " + 
                mUsbTetherDone + ", tetherfail: " + mUsbTetherFail);

        /** M: for bug solving, ALPS00331223 */
        // turn on tethering case
        if (mUsbTether.isChecked()) {
            if (mUsbConnected && mUsbConfigured && !mUsbHwDisconnected) {
                if (mUsbTetherFail || mUsbTetherDone) {
                    mUsbTetherCheckEnable = true;
                }
            }
        } else { // turn off tethering case or first launch case
            if (mUsbConnected && mUsbConfigured && !mUsbHwDisconnected) {
                if (mUsbUnTetherDone || mUsbTetherFail) {
                    mUsbTetherCheckEnable = true;
                }
            }
        }
        
        updateUsbState(available, tethered, errored);
        updateBluetoothState(available, tethered, errored);
        /// M: @{
        if (FeatureOption.MTK_TETHERINGIPV6_SUPPORT) {
            updateIpv6Preference();
        }
        /// @}
    }

    private void updateUsbState(String[] available, String[] tethered,
            String[] errored) {
        ConnectivityManager cm =
                (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        boolean usbAvailable = mUsbConnected && !mMassStorageActive;

        int usbError = ConnectivityManager.TETHER_ERROR_NO_ERROR;
        /// M: @{
        int usbErrorIpv4;
        int usbErrorIpv6;
        if (FeatureOption.MTK_TETHERINGIPV6_SUPPORT) {
            usbErrorIpv4 = ConnectivityManager.TETHER_ERROR_NO_ERROR;
            usbErrorIpv6 = ConnectivityManager.TETHER_ERROR_IPV6_NO_ERROR;
        }
        /// @}
        for (String s : available) {
            for (String regex : mUsbRegexs) {
                if (s.matches(regex) && cm != null) {
                    if (FeatureOption.MTK_TETHERINGIPV6_SUPPORT) {
                        /// M: @{
                        if (usbErrorIpv4 == ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                            usbErrorIpv4 = (cm.getLastTetherError(s) & 0x0f);
                        }
                        if (usbErrorIpv6 == ConnectivityManager.TETHER_ERROR_IPV6_NO_ERROR) {
                            usbErrorIpv6 = (cm.getLastTetherError(s) & 0xf0);
                        }
                        /// @}
                    } else {
                        if (usbError == ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                            usbError = cm.getLastTetherError(s);
                        }
                    }
                }
            }
        }

        boolean usbTethered = false;
        for (String s : tethered) {
            for (String regex : mUsbRegexs) {
                if (s.matches(regex)) { 
                    usbTethered = true;
                    /// M: @{
                    if (FeatureOption.MTK_TETHERINGIPV6_SUPPORT && cm != null) {
                        if (usbErrorIpv6 == ConnectivityManager.TETHER_ERROR_IPV6_NO_ERROR) {
                            usbErrorIpv6 = (cm.getLastTetherError(s) & 0xf0);
                        }
                    }
                    /// @}
                }
            }
        }

        boolean usbErrored = false;
        for (String s: errored) {
            for (String regex : mUsbRegexs) {
                if (s.matches(regex)) {
                    usbErrored = true;
                }
            }
        }

        Xlog.d(TAG, "updateUsbState - usbTethered : " + usbTethered + " usbErrored: " + 
            usbErrored + " usbAvailable: " + usbAvailable);
        
        if (usbTethered) {
            Xlog.d(TAG, "updateUsbState: usbTethered ! mUsbTether checkbox setEnabled & checked ");
            mUsbTether.setEnabled(true);
            mUsbTether.setChecked(true);
            /// M: set usb tethering to false @{
            final String summary = getString(R.string.usb_tethering_active_subtext);
            if (FeatureOption.MTK_TETHERINGIPV6_SUPPORT) {
                mUsbTether.setSummary(summary + getIPV6String(usbErrorIpv4, usbErrorIpv6));
            } else {
                mUsbTether.setSummary(summary);
            }
            mUsbTethering = false;
            Xlog.d(TAG, "updateUsbState - usbTethered - mUsbTetherCheckEnable: "
                + mUsbTetherCheckEnable);
            /// @}
        } else if (usbAvailable) {
            /// M: update summary @{
            if (FeatureOption.MTK_TETHERINGIPV6_SUPPORT) {
                if (usbErrorIpv4 == ConnectivityManager.TETHER_ERROR_NO_ERROR
                    || usbErrorIpv4 == ConnectivityManager.TETHER_ERROR_IPV6_NO_ERROR) {
                    mUsbTether.setSummary(R.string.usb_tethering_available_subtext);
                } else {
                    mUsbTether.setSummary(R.string.usb_tethering_errored_subtext);
                }
            } else {
            ///  @}
                if (usbError == ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                    mUsbTether.setSummary(R.string.usb_tethering_available_subtext);
                } else {
                    mUsbTether.setSummary(R.string.usb_tethering_errored_subtext);
                }
            }
            if (mUsbTetherCheckEnable) {
                Xlog.d(TAG, "updateUsbState - mUsbTetherCheckEnable, " +
                    "mUsbTether checkbox setEnabled, and set unchecked ");
                mUsbTether.setEnabled(true);
                mUsbTether.setChecked(false);
                /// M:
                mUsbTethering = false;
            }
            Xlog.d(TAG, "updateUsbState - usbAvailable - mUsbConfigured:  " + mUsbConfigured + 
                    " mUsbTethering: " + mUsbTethering + 
                    " mUsbTetherCheckEnable: " + mUsbTetherCheckEnable);
        } else if (usbErrored) {
            mUsbTether.setSummary(R.string.usb_tethering_errored_subtext);
            mUsbTether.setEnabled(false);
            mUsbTether.setChecked(false);
            /// M: set usb tethering to false
            mUsbTethering = false;
        } else if (mMassStorageActive) {
            mUsbTether.setSummary(R.string.usb_tethering_storage_active_subtext);
            mUsbTether.setEnabled(false);
            mUsbTether.setChecked(false);
            /// M: set usb tethering to false
            mUsbTethering = false;
        } else {
            if (mUsbHwDisconnected || (!mUsbHwDisconnected && !mUsbConnected && !mUsbConfigured)) {
                mUsbTether.setSummary(R.string.usb_tethering_unavailable_subtext);
                mUsbTether.setEnabled(false);
                mUsbTether.setChecked(false);
                mUsbTethering = false;
            } else {
                /// M: update usb state @{
                Xlog.d(TAG, "updateUsbState - else, " + 
                    "mUsbTether checkbox setEnabled, and set unchecked ");
                mUsbTether.setSummary(R.string.usb_tethering_available_subtext);
                mUsbTether.setEnabled(true);
                mUsbTether.setChecked(false);
                mUsbTethering = false;
                /// @}
            }
            Xlog.d(TAG, "updateUsbState- usbAvailable- mUsbHwDisconnected:" + mUsbHwDisconnected);
        }
    }

    private void updateBluetoothState(String[] available, String[] tethered,
            String[] errored) {
        /// M:   @{
        ConnectivityManager cm =
                (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);

        if (FeatureOption.MTK_TETHERINGIPV6_SUPPORT) {
            mBtErrorIpv4 = ConnectivityManager.TETHER_ERROR_NO_ERROR;
            mBtErrorIpv6 = ConnectivityManager.TETHER_ERROR_IPV6_NO_ERROR;
            for (String s : available) {
                for (String regex : mBluetoothRegexs) {
                    if (s.matches(regex) && cm != null) {
                        if (mBtErrorIpv4 == ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                            mBtErrorIpv4 = (cm.getLastTetherError(s) & 0x0f);
                        }
                        if (mBtErrorIpv6 == ConnectivityManager.TETHER_ERROR_IPV6_NO_ERROR) {
                            mBtErrorIpv6 = (cm.getLastTetherError(s) & 0xf0);
                        }
                    }
                }
            }
        }
        /// @}
        int bluetoothTethered = 0;
        for (String s : tethered) {
            for (String regex : mBluetoothRegexs) {
                if (s.matches(regex)) {
                    /// M:   @{
                    bluetoothTethered++;
                    if (FeatureOption.MTK_TETHERINGIPV6_SUPPORT && cm != null) {
                        if (mBtErrorIpv6 == ConnectivityManager.TETHER_ERROR_IPV6_NO_ERROR) {
                            mBtErrorIpv6 = (cm.getLastTetherError(s) & 0xf0);
                        }
                    }
                    /// @}
                }
            }
        }

        boolean bluetoothErrored = false;
        for (String s: errored) {
            for (String regex : mBluetoothRegexs) {
                if (s.matches(regex)) {
                    bluetoothErrored = true;
                }
            }
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        int btState = adapter.getState();
        if (btState == BluetoothAdapter.STATE_TURNING_OFF) {
            mBluetoothTether.setEnabled(false);
            mBluetoothTether.setSummary(R.string.bluetooth_turning_off);
        } else if (btState == BluetoothAdapter.STATE_TURNING_ON) {
            mBluetoothTether.setEnabled(false);
            mBluetoothTether.setSummary(R.string.bluetooth_turning_on);
        } else if (btState == BluetoothAdapter.STATE_ON && mBluetoothPan.isTetheringOn() && 
            mBluetoothDun.isTetheringOn()) {
            mBluetoothTether.setChecked(true);
            mBluetoothTether.setEnabled(true);
            if (bluetoothTethered > 1) {
                String summary = getString(
                        R.string.bluetooth_tethering_devices_connected_subtext, bluetoothTethered);
                if (FeatureOption.MTK_TETHERINGIPV6_SUPPORT) {
                    /// M:
                    mBluetoothTether.setSummary(summary + getIPV6String(mBtErrorIpv4,mBtErrorIpv6));
                } else {
                    mBluetoothTether.setSummary(summary);
                }
            } else if (bluetoothTethered == 1) {
                /// M: @{
                String summary = getString(R.string.bluetooth_tethering_device_connected_subtext);
                if (FeatureOption.MTK_TETHERINGIPV6_SUPPORT) {
                    mBluetoothTether.setSummary(summary + getIPV6String(mBtErrorIpv4,mBtErrorIpv6));
                } else {
                    mBluetoothTether.setSummary(summary);
                }
                ///@}
            } else if (bluetoothErrored) {
                mBluetoothTether.setSummary(R.string.bluetooth_tethering_errored_subtext);
            } else {
                /// M: @{
                String summary = getString(R.string.bluetooth_tethering_available_subtext);
                if (FeatureOption.MTK_TETHERINGIPV6_SUPPORT) {
                    mBluetoothTether.setSummary(summary + getIPV6String(mBtErrorIpv4,mBtErrorIpv6));
                } else {
                    mBluetoothTether.setSummary(summary);
                }
                /// @}
            }
        } else {
            mBluetoothTether.setEnabled(true);
            mBluetoothTether.setChecked(false);
            mBluetoothTether.setSummary(R.string.bluetooth_tethering_off_subtext);
        }
    }
    /*
     * M: get ipv6 string
     */
    public String getIPV6String(int errorIpv4, int errorIpv6) {
        String text = "";
        if (mTetherIpv6 != null && "1".equals(mTetherIpv6.getValue())) {
            Xlog.d(TAG, "[errorIpv4 =" + errorIpv4 + "];" + "[errorIpv6 =" + errorIpv6 + "];");
            if (errorIpv4 == ConnectivityManager.TETHER_ERROR_NO_ERROR
                && errorIpv6 == ConnectivityManager.TETHER_ERROR_IPV6_AVAIABLE) {
                text = getResources().getString(R.string.tethered_ipv4v6);
            }
            if (errorIpv4 == ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                text = getResources().getString(R.string.tethered_ipv4);
            }
            if (errorIpv6 == ConnectivityManager.TETHER_ERROR_IPV6_AVAIABLE) {
                text = getResources().getString(R.string.tethered_ipv6);
            }
        }
        return text;
    }

    public boolean onPreferenceChange(Preference preference, Object value) {
        String key = preference.getKey();
        Xlog.d(TAG,"onPreferenceChange key=" + key);
        if (ENABLE_WIFI_AP.equals(key)) {
            boolean enable = (Boolean) value;
            if (enable) {
                startProvisioningIfNecessary(WIFI_TETHERING);
            } else {
                mWifiApEnabler.setSoftapEnabled(false);
            }
            /// M: @{
            if (FeatureOption.MTK_TETHERINGIPV6_SUPPORT) {
                mEnableWifiAp.setChecked(enable);
                updateIpv6Preference();
            }
            /// @}
            return false;
        } else if (WIFI_AUTO_DISABLE.equals(key)) {
            /// M: save value to provider @{
            System.putInt(getContentResolver(),
                    System.WIFI_HOTSPOT_AUTO_DISABLE, Integer.parseInt(((String) value)));
            Xlog.d(TAG,"onPreferenceChange auto disable value = " + 
                Integer.parseInt(((String) value)));
            /// @}
        } else if (TETHERED_IPV6.equals(key)) {
            /// M: save value to provider @{
            ConnectivityManager cm = 
                (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
            int ipv6Value = Integer.parseInt(String.valueOf(value));
            if (cm != null) {
                cm.setTetheringIpv6Enable(ipv6Value == 1);
            }
            mTetherIpv6.setValueIndex(ipv6Value);
            mTetherIpv6.setSummary(getResources().getStringArray(
                R.array.tethered_ipv6_entries)[ipv6Value]);
            /// @}
        }        
        return true;
    }

    boolean isProvisioningNeeded() {
        if (SystemProperties.getBoolean("net.tethering.noprovisioning", false)) {
            return false;
        }
        return mProvisionApp.length == 2;
    }

    private void startProvisioningIfNecessary(int choice) {
        mTetherChoice = choice;
        if (isProvisioningNeeded()) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName(mProvisionApp[0], mProvisionApp[1]);
            startActivityForResult(intent, PROVISION_REQUEST);
        } else {
            startTethering();
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == PROVISION_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                startTethering();
            } else {
                //BT and USB need checkbox turned off on failure
                //Wifi tethering is never turned on until afterwards
                switch (mTetherChoice) {
                    case BLUETOOTH_TETHERING:
                        mBluetoothTether.setChecked(false);
                        break;
                    case USB_TETHERING:
                        mUsbTether.setChecked(false);
                        break;
                    default:
                        break;
                }
                mTetherChoice = INVALID;
            }
        }
    }

    private void startTethering() {
        switch (mTetherChoice) {
            case WIFI_TETHERING:
                mWifiApEnabler.setSoftapEnabled(true);
                break;
            case BLUETOOTH_TETHERING:
                // turn on Bluetooth first
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter.getState() == BluetoothAdapter.STATE_OFF) {
                    mBluetoothEnableForTether = true;
                    adapter.enable();
                    mBluetoothTether.setSummary(R.string.bluetooth_turning_on);
                    mBluetoothTether.setEnabled(false);
                } else {
                    mBluetoothPan.setBluetoothTethering(true);
                    /// M: set blue tooth tethering to true @{
                    mBluetoothDun.setBluetoothTethering(true);
                    String summary = getString(R.string.bluetooth_tethering_available_subtext);
                    if (FeatureOption.MTK_TETHERINGIPV6_SUPPORT) {
                        mBluetoothTether.setSummary(summary + 
                            getIPV6String(mBtErrorIpv4, mBtErrorIpv6));
                    } else {
                        mBluetoothTether.setSummary(summary);
                    }
                    /// @}
                }
                break;
            case USB_TETHERING:
                setUsbTethering(true);
                break;
            default:
                //should not happen
                break;
        }
    }

    private void setUsbTethering(boolean enabled) {
        ConnectivityManager cm =
            (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm.setUsbTethering(enabled) != ConnectivityManager.TETHER_ERROR_NO_ERROR) {
            mUsbTether.setChecked(false);
            mUsbTether.setSummary(R.string.usb_tethering_errored_subtext);
            return;
        }
        mUsbTether.setSummary("");
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen screen, Preference preference) {
        ConnectivityManager cm =
                (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);

        if (preference == mUsbTether) {
            if (!mUsbTethering) {
                boolean newState = mUsbTether.isChecked();

                /// M: update usb tethering @{
                mUsbTether.setEnabled(false);
                mUsbTethering = true;
                mUsbTetherCheckEnable = false;
                if (newState){
                    mUsbTetherDone = false;
                }else{
                    mUsbUnTetherDone = false;
                }   
                mUsbTetherFail = false;
                    
                Xlog.d(TAG, "onPreferenceTreeClick - setusbTethering(" + newState +
                    ") mUsbTethering:  " + mUsbTethering);
                /// @}

                if (newState) {
                    startProvisioningIfNecessary(USB_TETHERING);
                } else {
                    setUsbTethering(newState);
                }
            } else {
                return true;
            }
        } else if (preference == mBluetoothTether) {
            boolean bluetoothTetherState = mBluetoothTether.isChecked();

            if (bluetoothTetherState) {
                startProvisioningIfNecessary(BLUETOOTH_TETHERING);
            } else {
                boolean errored = false;

                String [] tethered = cm.getTetheredIfaces();
                String bluetoothIface = findIface(tethered, mBluetoothRegexs);
                if (bluetoothIface != null &&
                        cm.untether(bluetoothIface) != ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                    errored = true;
                }

                mBluetoothPan.setBluetoothTethering(false);
                /// M: set bluetooth tethering to false
                mBluetoothDun.setBluetoothTethering(false);
                if (errored) {
                    mBluetoothTether.setSummary(R.string.bluetooth_tethering_errored_subtext);
                } else {
                    mBluetoothTether.setSummary(R.string.bluetooth_tethering_off_subtext);
                }
            }
            /// M: @{
            if (FeatureOption.MTK_TETHERINGIPV6_SUPPORT) {
                updateIpv6Preference();
            }
            /// @}
        } else if (preference == mCreateNetwork) {
            showDialog(DIALOG_AP_SETTINGS);
        } else if (preference == mTetherApnSetting) {
            /// M: save value to provider @{
            if (FeatureOption.MTK_GEMINI_SUPPORT) {
                Intent intent = new Intent();
                intent.setClassName("com.android.phone", 
                    "com.mediatek.settings.MultipleSimActivity");
                intent.putExtra("TARGET_CLASS", "com.android.settings.TetherApnSettings");
                startActivity(intent);
            } else {
                Intent intent = new Intent();
                intent.setClassName("com.android.settings", 
                    "com.android.settings.TetherApnSettings");
                startActivity(intent);
            }
            /// @}
        }

        return super.onPreferenceTreeClick(screen, preference);
    }

    private static String findIface(String[] ifaces, String[] regexes) {
        for (String iface : ifaces) {
            for (String regex : regexes) {
                if (iface.matches(regex)) {
                    return iface;
                }
            }
        }
        return null;
    }

    public void onClick(DialogInterface dialogInterface, int button) {
        if (button == DialogInterface.BUTTON_POSITIVE) {
            mWifiConfig = mDialog.getConfig();
            if (mWifiConfig != null) {
                /**
                 * if soft AP is stopped, bring up
                 * else restart with new config
                 * TODO: update config on a running access point when framework support is added
                 */
                if (mWifiManager.getWifiApState() == WifiManager.WIFI_AP_STATE_ENABLED) {
                    mWifiManager.setWifiApEnabled(null, false);
                    mWifiManager.setWifiApEnabled(mWifiConfig, true);
                } else {
                    mWifiManager.setWifiApConfiguration(mWifiConfig);
                }
                int index = WifiApDialog.getSecurityTypeIndex(mWifiConfig);
                mCreateNetwork.setSummary(String.format(getActivity().getString(CONFIG_SUBTEXT),
                        mWifiConfig.SSID,
                        mSecurityType[index]));
            }
        }
    }
    /** 
     * M: close spinner dialog if configuration changed
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mDialog != null) {
            mDialog.closeSpinnerDialog();
        }
    }

    @Override
    public int getHelpResource() {
        return R.string.help_url_tether;
    }
}
