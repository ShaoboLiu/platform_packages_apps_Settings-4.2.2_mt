/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

package com.android.settings.deviceinfo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemProperties;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.provider.Telephony.SIMInfo;
import android.telephony.PhoneNumberUtils;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.gemini.GeminiNetworkSubUtil;
import com.android.internal.telephony.gemini.GeminiPhone;
import com.android.settings.R;

import com.mediatek.CellConnService.CellConnMgr;
import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.xlog.Xlog;

import java.util.List;

public class SimStatusGemini extends PreferenceActivity {

    private static final String KEY_SIGNAL_STRENGTH = "signal_strength";
    private static final String KEY_NUMBER = "number";
    private static final String KEY_NETWORK_TYPE = "network_type";
    private static final String KEY_DATA_STATE = "data_state";
    private static final String KEY_SERVICE_STATE = "service_state";
    private static final String KEY_ROAMING_STATE = "roaming_state";
    private static final String KEY_OPERATOR_NAME = "operator_name";

    private static final int EVENT_SIGNAL_STRENGTH_CHANGED = 200;
    private static final int EVENT_SERVICE_STATE_CHANGED = 300;
    private GeminiPhone mGeminiPhone = null;
    private static Resources sRes;
    private Preference mSignalStrengthPreference;
    
    private SignalStrength mSignalStrength;
    private SignalStrength mSignalStrengthGemini;

    private static String sUnknown;
    
    private TelephonyManager mTelephonyManager;
    
    //SimId, get from the intent extra
    private static int sSimId = 0;
    private boolean mIsUnlockFollow = false;
    private boolean mIsShouldBeFinished = false;
    
    private static final String TAG = "Gemini_SimStatus";

    private int mServiceState;
    private Handler mHandler = new Handler();

    private final BroadcastReceiver mReceiver = new AirplaneModeBroadcastReceiver();
    // unlock sim pin/ me lock
    private Runnable mServiceComplete = new Runnable(){
       public void run() {
          int nRet = mCellMgr.getResult();
          if (mCellMgr.RESULT_OK != nRet && mCellMgr.RESULT_STATE_NORMAL != nRet){
              Xlog.d(TAG, "mCell Mgr Result is not OK");
              mIsShouldBeFinished = true;
              SimStatusGemini.this.finish();
              return;
          }

        mIsUnlockFollow = false;
      }
    };
    // create unlock object
    private CellConnMgr mCellMgr;// = new CellConnMgr(mServiceComplete);
    //related to mobile network type and mobile network state
    private PhoneStateListener mPhoneStateListener = new PhoneStateListener() {

        @Override
        public void onDataConnectionStateChanged(int state, int networkType) {
            updateDataState();
            updateNetworkType();
        }

        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            if (signalStrength.getMySimId() == sSimId) {
                mSignalStrength = signalStrength;
                updateSignalStrength(sSimId);
                Xlog.d(TAG, "message mGeminiPhone number is : " + 
                        signalStrength.getGsmSignalStrength() + " MySimId is" + 
                        signalStrength.getMySimId());
            }
        }

        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            if (serviceState.getMySimId() == sSimId) {
                mServiceState = serviceState.getState();
                updateServiceState(serviceState);
                updateSignalStrength(sSimId);
            }
        }
    };
    
    private PhoneStateListener mPhoneStateListenerGemini = new PhoneStateListener() {

        @Override
        public void onDataConnectionStateChanged(int state, int networkType) {
            updateDataState();
            updateNetworkType();
        }
        
        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            if (signalStrength.getMySimId() == sSimId) {
                mSignalStrengthGemini = signalStrength;
                updateSignalStrength(sSimId);
                Xlog.d(TAG, "message mGeminiPhone number is : " + 
                        signalStrength.getGsmSignalStrength() + " MySimId is" + 
                        signalStrength.getMySimId());
            }
        }
        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            if (serviceState.getMySimId() == sSimId) {
                mServiceState = serviceState.getState();
                updateServiceState(serviceState);
                updateSignalStrength(sSimId);
            }
        }
    };
    

    
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mCellMgr = new CellConnMgr(mServiceComplete);
        mCellMgr.register(this);
        addPreferencesFromResource(R.xml.device_info_sim_status_gemini);
        
        //get the correct simId according to the intent extra
        Intent it = getIntent();
        sSimId = it.getIntExtra("slotid", -1);
        Xlog.d(TAG, "sSimId is : " + sSimId);
        /* M: simId == -1 is not supproted happen and usually it is caused by
         * 3rd party app so to compatibilty add this catch
         * */
        if (sSimId == -1) {
            sSimId = dealWith3AppLaunch();
        }
        
        SIMInfo simInfo = SIMInfo.getSIMInfoBySlot(this, sSimId);
        int simCount = SIMInfo.getInsertedSIMCount(this);
        String simDisplayName = null;
        if (simCount > 1 && simInfo != null) {
            simDisplayName = simInfo.mDisplayName;
        }
        if (simDisplayName != null && !simDisplayName.equals("")) {
            setTitle(simDisplayName);
        }

        sRes = getResources();
        sUnknown = sRes.getString(R.string.device_info_default);
        mTelephonyManager = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
        
        mGeminiPhone = (GeminiPhone)PhoneFactory.getDefaultPhone();        
        
        // Note - missing in zaku build, be careful later...
        mSignalStrengthPreference = findPreference(KEY_SIGNAL_STRENGTH);
    }
    @Override
    protected void onDestroy() {
        mCellMgr.unregister();
        super.onDestroy();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (mIsShouldBeFinished) {
            finish();
            return;
        }
        if (!mIsUnlockFollow) {
            mIsUnlockFollow = true;
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mCellMgr.handleCellConn(SimStatusGemini.this.sSimId, CellConnMgr.REQUEST_TYPE_SIMLOCK);
                }
            },500);
        }
        IntentFilter intentFilter =
            new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intentFilter.addAction(Intent.ACTION_DUAL_SIM_MODE_CHANGED);
        registerReceiver(mReceiver, intentFilter);
        //related to my phone number
        String rawNumber = mGeminiPhone.getLine1NumberGemini(sSimId);  // may be null or empty
        String formattedNumber = null;
        if (!TextUtils.isEmpty(rawNumber)) {
            formattedNumber = PhoneNumberUtils.formatNumber(rawNumber);
        }
        // If formattedNumber is null or empty, it'll display as "Unknown".
        setSummaryText(KEY_NUMBER, formattedNumber);

        //after registerIntent, it will receive the message, so do not need to update signalStrength and service state
        updateDataState();
        ServiceState serviceState = mGeminiPhone.getServiceStateGemini(sSimId);
        updateServiceState(serviceState);
        mServiceState = serviceState.getState();
        SignalStrength signalStrength = mGeminiPhone.getSignalStrengthGemini(sSimId);
        if (sSimId == Phone.GEMINI_SIM_1) {
            mSignalStrength = signalStrength;
        } else if (sSimId == Phone.GEMINI_SIM_2) {
            mSignalStrengthGemini = signalStrength;
        }
        updateSignalStrength(sSimId);
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            mTelephonyManager.listenGemini(mPhoneStateListener, 
                  PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
                   | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
                   | PhoneStateListener.LISTEN_SERVICE_STATE, Phone.GEMINI_SIM_1);
            mTelephonyManager.listenGemini(mPhoneStateListenerGemini, 
                    PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
                     | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
                     | PhoneStateListener.LISTEN_SERVICE_STATE, Phone.GEMINI_SIM_2);
        } else {
            mTelephonyManager.listen(mPhoneStateListener,
                      PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
                      | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
                      | PhoneStateListener.LISTEN_SERVICE_STATE);
        }

    }
    
    @Override
    public void onPause() {
        super.onPause();
        if (mIsShouldBeFinished) {
            // this is add for CR 64523 by mtk80800
            finish();
            return;
        }
        unregisterReceiver(mReceiver);
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
            mTelephonyManager.listen(mPhoneStateListenerGemini, PhoneStateListener.LISTEN_NONE);
            
        } else {
            mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
    }
 
    /**
     * @param preference The key for the Preference item
     * @param property The system property to fetch
     * @param alt The default value, if the property doesn't exist
     */
    private void setSummary(String preference, String property, String alt) {
        try {
            String strSummary = SystemProperties.get(property, alt);

            //replace the "unknown" result with the string resource for MUI
            Preference p = findPreference(preference);
            if (p != null) {
                p.setSummary(
                        (strSummary.equals("unknown")) ? sUnknown : strSummary);
            }

        } catch (RuntimeException e) {
            Xlog.d(TAG,"fail to get system property");
        }
    }
    
    private void setSummaryText(String preference, String text) {
        if (TextUtils.isEmpty(text)) {
           text = this.getResources().getString(R.string.device_info_default);
         }
         // some preferences may be missing
        Preference p = findPreference(preference);
        if (p != null) {
             p.setSummary(text);
         }
    }

    private void updateNetworkType() {
        // Whether EDGE, UMTS, etc...
        if (sSimId == Phone.GEMINI_SIM_1) {
            setSummary(KEY_NETWORK_TYPE, TelephonyProperties.PROPERTY_DATA_NETWORK_TYPE, sUnknown);
        } else if (sSimId == Phone.GEMINI_SIM_2) {
            setSummary(KEY_NETWORK_TYPE, TelephonyProperties
                      .PROPERTY_DATA_NETWORK_TYPE_2, sUnknown);
        } else {
            setSummaryText(KEY_NETWORK_TYPE, sUnknown);
        }
    }

    private void updateDataState() {
        int state = TelephonyManager.DATA_DISCONNECTED;
        state = mTelephonyManager.getDataStateGemini(sSimId);
        
        String display = sRes.getString(R.string.radioInfo_unknown);
    
        switch (state) {
            case TelephonyManager.DATA_CONNECTED:
                display = sRes.getString(R.string.radioInfo_data_connected);
                break;
            case TelephonyManager.DATA_SUSPENDED:
                display = sRes.getString(R.string.radioInfo_data_suspended);
                break;
            case TelephonyManager.DATA_CONNECTING:
                display = sRes.getString(R.string.radioInfo_data_connecting);
                break;
            case TelephonyManager.DATA_DISCONNECTED:
                display = sRes.getString(R.string.radioInfo_data_disconnected);
                break;
            default:
                break;
        }
        
        setSummaryText(KEY_DATA_STATE, display);
    }

    private void updateServiceState(ServiceState serviceState) {
        
        int state = serviceState.getState();
        String display = sRes.getString(R.string.radioInfo_unknown);
        
        switch (state) {
            case ServiceState.STATE_IN_SERVICE:
                display = sRes.getString(R.string.radioInfo_service_in);
                break;
            case ServiceState.STATE_OUT_OF_SERVICE:
                display = sRes.getString(R.string.radioInfo_service_out);
                break;
            case ServiceState.STATE_EMERGENCY_ONLY:
                display = sRes.getString(R.string.radioInfo_service_emergency);
                break;
            case ServiceState.STATE_POWER_OFF:
                display = sRes.getString(R.string.radioInfo_service_off);
                break;
            default:
                break;
        }
        
        setSummaryText(KEY_SERVICE_STATE, display);
        
        if (serviceState.getRoaming()) {
            setSummaryText(KEY_ROAMING_STATE, sRes.getString(R.string.radioInfo_roaming_in));
        } else {
            setSummaryText(KEY_ROAMING_STATE, sRes.getString(R.string.radioInfo_roaming_not));
        }
        setSummaryText(KEY_OPERATOR_NAME, serviceState.getOperatorAlphaLong());
    }

    void updateSignalStrength(int simId) {
        // TODO PhoneStateIntentReceiver is deprecated and PhoneStateListener
        // should probably used instead.
        
        Xlog.d(TAG, "Enter updateSignalStrength function. simId is " + simId);
        SignalStrength signalStrength = null;

        if (simId == Phone.GEMINI_SIM_1) {
            signalStrength = mSignalStrength;
        } else if (simId == Phone.GEMINI_SIM_2) {
            signalStrength = mSignalStrengthGemini;
        }
        // not loaded in some versions of the code (e.g., zaku)
        if (mSignalStrengthPreference != null) {
            Resources r = getResources();
    
            int signalDbm = 0;
            int signalAsu = 0;
            boolean isNeedUpdate = true;
            Xlog.d(TAG, "ServiceState in  updateSignalStrength function." + mServiceState);
            if ((ServiceState.STATE_OUT_OF_SERVICE ==  mServiceState) ||
                    (ServiceState.STATE_POWER_OFF ==  mServiceState)) {

                isNeedUpdate = true;
            } else {
                if (signalStrength != null) {
                    signalDbm = signalStrength.getGsmSignalStrengthDbm();
                    signalAsu = signalStrength.getGsmSignalStrength();
                }
                if (-1 == signalDbm || 99 == signalAsu) {
                    isNeedUpdate = false;
                }
                    
            }
            Xlog.d(TAG, "SignalStrength is:" + signalDbm + "dbm," + signalAsu + "asu");
            if (isNeedUpdate) {
                mSignalStrengthPreference.setSummary(String.valueOf(signalDbm) + " "
                        + r.getString(R.string.radioInfo_display_dbm) + "   "
                        + String.valueOf(signalAsu) + " "
                        + r.getString(R.string.radioInfo_display_asu));
            }
        }
    }

    private class AirplaneModeBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                boolean airplaneMode = intent.getBooleanExtra("state", false);
                if (airplaneMode) {
                    mCellMgr.handleCellConn(sSimId,
                            CellConnMgr.REQUEST_TYPE_SIMLOCK);
                }
            } else if (Intent.ACTION_DUAL_SIM_MODE_CHANGED.equals(action)) {
                int dualMode = intent.getIntExtra(Intent.EXTRA_DUAL_SIM_MODE,
                        -1);
                if (dualMode == GeminiNetworkSubUtil.MODE_FLIGHT_MODE) {
                    mCellMgr.handleCellConn(sSimId,
                            CellConnMgr.REQUEST_TYPE_SIMLOCK);
                } else if (dualMode != GeminiNetworkSubUtil.MODE_DUAL_SIM
                        && dualMode != sSimId) {
                    mCellMgr.handleCellConn(sSimId,
                            CellConnMgr.REQUEST_TYPE_SIMLOCK);
                }
            }
        }
   }
    private int dealWith3AppLaunch() {
        List<SIMInfo> simList = SIMInfo.getInsertedSIMList(this);
        int slotID;
        if (simList.size() == 0) {
            slotID = -1;
        } else if (simList.size() == 1) {
            slotID = simList.get(0).mSlot;
        } else {
            slotID = simList.get(0).mSlot;
            for (SIMInfo temp : simList) {
                if (slotID > temp.mSlot) {
                    slotID = temp.mSlot;
                }
            }
        }
        Xlog.d(TAG, "dealWith3AppLaunch() slotID=" + slotID);
        return slotID;
    }
}
