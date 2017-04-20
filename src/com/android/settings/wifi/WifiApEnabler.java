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

package com.android.settings.wifi;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.preference.CheckBoxPreference;
import android.provider.Settings;

import com.android.settings.ext.IWifiExt;
import com.android.settings.R;
import com.android.settings.TetherSettings;
import com.android.settings.Utils;
import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.xlog.Xlog;

import java.util.ArrayList;
public class WifiApEnabler {
    static final String TAG = "WifiApEnabler";
    private final Context mContext;
    private final CheckBoxPreference mCheckBox;
    private final CharSequence mOriginalSummary;

    private WifiManager mWifiManager;
    private final IntentFilter mIntentFilter;
    private TetherSettings mTetherSettings;
    private static final int WIFI_IPV4 = 0x0f;
    private static final int WIFI_IPV6 = 0xf0;

    ConnectivityManager mCm;
    private String[] mWifiRegexs;

    IWifiExt mExt;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiManager.WIFI_AP_STATE_CHANGED_ACTION.equals(action)) {
                handleWifiApStateChanged(intent.getIntExtra(
                        WifiManager.EXTRA_WIFI_AP_STATE, WifiManager.WIFI_AP_STATE_FAILED));
            } else if (ConnectivityManager.ACTION_TETHER_STATE_CHANGED.equals(action)) {
                ArrayList<String> available = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_AVAILABLE_TETHER);
                ArrayList<String> active = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_ACTIVE_TETHER);
                ArrayList<String> errored = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_ERRORED_TETHER);
                if (FeatureOption.MTK_TETHERINGIPV6_SUPPORT) {
                    updateTetherStateForIpv6(available.toArray(), active.toArray(), errored.toArray());
                } else {
                    updateTetherState(available.toArray(), active.toArray(), errored.toArray());
                }
            } else if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                enableWifiCheckBox();
            }

        }
    };

    public WifiApEnabler(Context context, CheckBoxPreference checkBox) {
        mContext = context;
        mCheckBox = checkBox;
        mOriginalSummary = checkBox.getSummary();
        checkBox.setPersistent(false);

        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        mCm = (ConnectivityManager)mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        mWifiRegexs = mCm.getTetherableWifiRegexs();

        mIntentFilter = new IntentFilter(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(ConnectivityManager.ACTION_TETHER_STATE_CHANGED);
        mIntentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
    }

    public void resume() {
        mContext.registerReceiver(mReceiver, mIntentFilter);
        enableWifiCheckBox();
    }

    public void pause() {
        mContext.unregisterReceiver(mReceiver);
    }

    private void enableWifiCheckBox() {
        boolean isAirplaneMode = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, 0) != 0;
        if (!isAirplaneMode) {
            mCheckBox.setEnabled(true);
        } else {
            mCheckBox.setSummary(mOriginalSummary);
            mCheckBox.setEnabled(false);
        }
    }

    public void setSoftapEnabled(boolean enable) {
        final ContentResolver cr = mContext.getContentResolver();
        /**
         * Disable Wifi if enabling tethering
         */
        int wifiState = mWifiManager.getWifiState();
        if (enable && ((wifiState == WifiManager.WIFI_STATE_ENABLING) ||
                    (wifiState == WifiManager.WIFI_STATE_ENABLED))) {
            mWifiManager.setWifiEnabled(false);
            Settings.Secure.putInt(cr, Settings.Secure.WIFI_SAVED_STATE, 1);
        }

        if (mWifiManager.setWifiApEnabled(null, enable)) {
            /* Disable here, enabled on receiving success broadcast */
            mCheckBox.setEnabled(false);
        } else {
            mCheckBox.setSummary(R.string.wifi_error);
        }

        /**
         *  If needed, restore Wifi on tether disable
         */
        if (!enable) {
            int wifiSavedState = 0;
            try {
                wifiSavedState = Settings.Secure.getInt(cr, Settings.Secure.WIFI_SAVED_STATE);
            } catch (Settings.SettingNotFoundException e) {
                Xlog.d(TAG, "SettingNotFoundException");
            }
            if (wifiSavedState == 1) {
                mWifiManager.setWifiEnabled(true);
                Settings.Secure.putInt(cr, Settings.Secure.WIFI_SAVED_STATE, 0);
            }
        }
    }

    public void updateConfigSummary(WifiConfiguration wifiConfig) {
        /// M: get plug in and get default ssid name @{
        String s = "";
        mExt = Utils.getWifiPlugin(mContext);
        s = mExt.getWifiApSsid();
        /// @}
        mCheckBox.setSummary(String.format(
                    mContext.getString(R.string.wifi_tether_enabled_subtext),
                    (wifiConfig == null) ? s : wifiConfig.SSID));
    }

    private void updateTetherStateForIpv6(Object[] available, Object[] tethered, Object[] errored) {
        boolean wifiTethered = false;
        boolean wifiErrored = false;

        int wifiErrorIpv4 = ConnectivityManager.TETHER_ERROR_NO_ERROR;
        int wifiErrorIpv6 = ConnectivityManager.TETHER_ERROR_IPV6_NO_ERROR;
        for (Object o : available) {
            String s = (String)o;
            for (String regex : mWifiRegexs) {
                if (s.matches(regex)) {
                    if (wifiErrorIpv4 == ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                        wifiErrorIpv4 = (mCm.getLastTetherError(s) & WIFI_IPV4);
                    }
                    if (wifiErrorIpv6 == ConnectivityManager.TETHER_ERROR_IPV6_NO_ERROR) {
                        wifiErrorIpv6 = (mCm.getLastTetherError(s) & WIFI_IPV6);
                    }
                }
            }
        }

        for (Object o : tethered) {
            String s = (String)o;
            for (String regex : mWifiRegexs) {
                if (s.matches(regex)) {
                    wifiTethered = true;
                    if (FeatureOption.MTK_TETHERINGIPV6_SUPPORT) {
                        if (wifiErrorIpv6 == ConnectivityManager.TETHER_ERROR_IPV6_NO_ERROR) {
                            wifiErrorIpv6 = (mCm.getLastTetherError(s) & WIFI_IPV6);
                        }
                    }
                }
            }
        }

        for (Object o: errored) {
            String s = (String)o;
            for (String regex : mWifiRegexs) {
                if (s.matches(regex)) {
                    wifiErrored = true;
                }
            }
        }

        if (wifiTethered) {
            WifiConfiguration wifiConfig = mWifiManager.getWifiApConfiguration();
            updateConfigSummary(wifiConfig);
            String s = mContext.getString(
                    com.android.internal.R.string.wifi_tether_configure_ssid_default);
            String tetheringActive = String.format(
                mContext.getString(R.string.wifi_tether_enabled_subtext),
                (wifiConfig == null) ? s : wifiConfig.SSID);

            if (mTetherSettings != null) {
                mCheckBox.setSummary(tetheringActive + mTetherSettings.getIPV6String(wifiErrorIpv4, wifiErrorIpv6));
            }
        } else if (wifiErrored) {
            mCheckBox.setSummary(R.string.wifi_error);
        }
    }


    /**
     * set the TetherSettings.
     * @param TetherSettings
     * @return void.
     */
    public void setTetherSettings(TetherSettings tetherSettings) {
        mTetherSettings = tetherSettings;
    }

    private void updateTetherState(Object[] available, Object[] tethered, Object[] errored) {
        boolean wifiTethered = false;
        boolean wifiErrored = false;

        for (Object o : tethered) {
            String s = (String)o;
            for (String regex : mWifiRegexs) {
                if (s.matches(regex)) {
                    wifiTethered = true;
                }
            }
        }
        for (Object o: errored) {
            String s = (String)o;
            for (String regex : mWifiRegexs) {
                if (s.matches(regex)) {
                    wifiErrored = true;
                }
            }
        }

        if (wifiTethered) {
            WifiConfiguration wifiConfig = mWifiManager.getWifiApConfiguration();
            updateConfigSummary(wifiConfig);
        } else if (wifiErrored) {
            mCheckBox.setSummary(R.string.wifi_error);
        }
    }

    private void handleWifiApStateChanged(int state) {
        switch (state) {
            case WifiManager.WIFI_AP_STATE_ENABLING:
                mCheckBox.setSummary(R.string.wifi_starting);
                mCheckBox.setEnabled(false);
                break;
            case WifiManager.WIFI_AP_STATE_ENABLED:
                /**
                 * Summary on enable is handled by tether
                 * broadcast notice
                 */
                mCheckBox.setChecked(true);
                /* Doesnt need the airplane check */
                mCheckBox.setEnabled(true);
                break;
            case WifiManager.WIFI_AP_STATE_DISABLING:
                mCheckBox.setSummary(R.string.wifi_stopping);
                mCheckBox.setEnabled(false);
                break;
            case WifiManager.WIFI_AP_STATE_DISABLED:
                mCheckBox.setChecked(false);
                mCheckBox.setSummary(mOriginalSummary);
                enableWifiCheckBox();
                break;
            default:
                mCheckBox.setChecked(false);
                mCheckBox.setSummary(R.string.wifi_error);
                enableWifiCheckBox();
        }
    }
}
