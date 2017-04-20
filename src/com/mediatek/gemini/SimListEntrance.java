package com.mediatek.gemini;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.provider.Telephony.SIMInfo;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.TelephonyIntents;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.xlog.Xlog;

import java.util.Collections;
import java.util.List;

public class SimListEntrance extends SettingsPreferenceFragment {

    private static final String TAG = "SimListEntrance";
    private static final int TWO_SIMCARD = 2;

    private static final String[] TARGETCLASSLIST = {
            "com.android.settings.IccLockSettings",
            "com.android.settings.deviceinfo.SimStatusGemini",
            "com.android.settings.ApnSettings",
            "com.mediatek.gemini.GeminiSIMTetherInfo" };

    private Context mContext;
    private IntentFilter mIntentFilter;
    private ITelephony mTelephony;

    private int mTargetClassIndex = -1;

    public static final int PIN_SETTING_INDEX = 0;
    public static final int SIM_STATUS_INDEX = 1;
    public static final int APN_SETTING_INDEX = 2;
    public static final int SIM_CONTACTS_SETTING_INDEX = 3;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.sim_data_roaming_settings);
        Bundle extras = getArguments();
        if (extras != null) {
            mTargetClassIndex = extras.getInt("type", -1);
            String title = extras.getString("title");
            if (title != null) {
                getActivity().setTitle(title);
            }
        }
        if (mTargetClassIndex < 0) {
            Xlog.e(TAG, "target class index is wrong");
        } else {
            mIntentFilter = new IntentFilter(
                    TelephonyIntents.ACTION_SIM_INFO_UPDATE);
            mIntentFilter.addAction(TelephonyIntents.ACTION_SIM_NAME_UPDATE);
            getActivity().registerReceiver(mSimReceiver, mIntentFilter);
            addSimInfoPreference();
        }
        mTelephony = ITelephony.Stub.asInterface(ServiceManager.getService("phone"));
    }

    @Override
    public void onResume() {
        // TODO Auto-generated method stub
        super.onResume();

        if ((FeatureOption.MTK_GEMINI_3G_SWITCH) && (mTelephony != null)) {

            try {

                GeminiUtils.sG3SlotID = mTelephony.get3GCapabilitySIM();

            } catch (RemoteException e) {
                Xlog.e(TAG, "mTelephony exception");
                return;
            }
        }
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {

        // TODO Auto-generated method stub

        String key = preference.getKey();
        Intent it = new Intent();
        it.setClassName("com.android.settings",
                TARGETCLASSLIST[mTargetClassIndex]);

        if (mTargetClassIndex == SIM_CONTACTS_SETTING_INDEX) {
            it.putExtra("simid", Long.valueOf(key));
        } else {

            int slot = SIMInfo.getSlotById(getActivity(), Long.valueOf(key));

            if (slot < 0) {
                return false;
            }
            it.putExtra("slotid", slot);
        }

        startActivity(it);

        return false;
    }

    private void addSimInfoPreference() {

        PreferenceScreen root = getPreferenceScreen();

        if (root != null) {
            root.removeAll();
            List<SIMInfo> simList = SIMInfo.getInsertedSIMList(getActivity());
            ///M: add for hot swap
            if (simList.size() == 0) {
                // Hot swap and no card so go to settings
                Xlog.d(TAG, "Hot swap_simList.size()=" + simList.size());
                Intent intent = new Intent(this.getActivity(),
                        com.android.settings.Settings.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            }
            // @}
            
            // Use the mSlot to determine the order of display. It is same as
            // SimManagement
            if (simList.size() == TWO_SIMCARD) {
                if (simList.get(0).mSlot > simList.get(1).mSlot) {
                    Collections.swap(simList, 0, 1);
                }
            }
            for (SIMInfo siminfo : simList) {
                int status = Phone.SIM_INDICATOR_ROAMING;
                SimInfoPreference simInfoPref = new SimInfoPreference(
                        getActivity(), siminfo.mDisplayName, siminfo.mNumber,
                        siminfo.mSlot, status, siminfo.mColor,
                        siminfo.mDispalyNumberFormat, siminfo.mSimId, false,
                        false);

                root.addPreference(simInfoPref);
            }

        }

    }

    @Override
    public void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        if (mTargetClassIndex >= 0) {
            getActivity().unregisterReceiver(mSimReceiver);

        }
    }

    private BroadcastReceiver mSimReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();

            if (action.equals(TelephonyIntents.ACTION_SIM_INFO_UPDATE)) {
                Xlog.i(TAG,"receiver: TelephonyIntents.ACTION_SIM_INFO_UPDATE");
                addSimInfoPreference();
            } else if (action.equals(TelephonyIntents.ACTION_SIM_NAME_UPDATE)) {
                Xlog.i(TAG,"receiver: TelephonyIntents.ACTION_SIM_NAME_UPDATE");
                int slotid = intent.getIntExtra("simId", -1);
                if (slotid < 0) {
                    return;
                }
                SIMInfo siminfo = SIMInfo.getSIMInfoBySlot(context, slotid);
                if (siminfo != null) {
                    SimInfoPreference pref = (SimInfoPreference) findPreference(String
                            .valueOf(siminfo.mSimId));
                    if (pref == null) {
                        return;
                    }
                    pref.setName(siminfo.mDisplayName);
                }

            }
        }
    };

}
