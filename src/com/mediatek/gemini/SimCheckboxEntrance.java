package com.mediatek.gemini;

import android.content.BroadcastReceiver;
import android.content.Context;

import android.content.Intent;
import android.content.IntentFilter;

import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.preference.PreferenceScreen;
import android.provider.Telephony.SIMInfo;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.TelephonyIntents;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.telephony.TelephonyManagerEx;
import com.mediatek.xlog.Xlog;

import java.util.List;

public class SimCheckboxEntrance extends SettingsPreferenceFragment {

    private static final String TAG = "SimCheckboxEntrance";
    protected List<SIMInfo> mSimList;

    private TelephonyManagerEx mTelephonyManager;
    private IntentFilter mIntentFilter;

    private ITelephony mTelephony;

    private boolean mDisableWhenRadioOff = false;

    private BroadcastReceiver mSimReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();
            if (action.equals(TelephonyIntents.ACTION_SIM_INDICATOR_STATE_CHANGED)) {
                int slotId = intent.getIntExtra(
                        TelephonyIntents.INTENT_KEY_ICC_SLOT, -1);
                int simStatus = intent.getIntExtra(
                        TelephonyIntents.INTENT_KEY_ICC_STATE, -1);
                Xlog.i(TAG, "receive notification: state of sim slot " + slotId
                        + " is " + simStatus);
                if ((slotId >= 0) && (simStatus >= 0)) {
                    updateSimState(slotId, simStatus);
                }
            } else if (action.equals(TelephonyIntents.ACTION_SIM_INFO_UPDATE)) {
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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.sim_checkbox_list);

        mTelephonyManager = TelephonyManagerEx.getDefault();

        mIntentFilter = new IntentFilter(
                TelephonyIntents.ACTION_SIM_INDICATOR_STATE_CHANGED);
        mIntentFilter.addAction(TelephonyIntents.ACTION_SIM_INFO_UPDATE);
        mIntentFilter.addAction(TelephonyIntents.ACTION_SIM_NAME_UPDATE);
        getActivity().registerReceiver(mSimReceiver, mIntentFilter);

        mDisableWhenRadioOff = shouldDisableWhenRadioOff();
        addSimInfoPreference();
        mTelephony = ITelephony.Stub.asInterface(ServiceManager
                .getService("phone"));
    }

    @Override
    public void onStart() {
        // TODO Auto-generated method stub
        super.onStart();
        getListView().setItemsCanFocus(false);
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
    public void onDestroy() {
        super.onDestroy();
        getActivity().unregisterReceiver(mSimReceiver);

    }

    protected boolean shouldDisableWhenRadioOff() {
        return false;
    }

    protected void addSimInfoPreference() {

        PreferenceScreen root = this.getPreferenceScreen();

        if (root == null) {
            return;
        }
        root.removeAll();
        mSimList = SIMInfo.getInsertedSIMList(getActivity());
        sortSimList();
        ///M: add for hot swap feature {
        if (mSimList.size() == 0) {
            //Hot swap and no card so go to settings
            Xlog.d(TAG,"Hot swap_mSimList.size()=" + mSimList.size());
            Intent intent = new Intent(this.getActivity(),com.android.settings.Settings.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        }
        ///@}
        if (mSimList != null) {
            for (SIMInfo siminfo : mSimList) {

                int status = mTelephonyManager
                        .getSimIndicatorStateGemini(siminfo.mSlot);
                /*
                 * Since this is checkbox entrance so call SimInfoPreference
                 * constructor with checkbox available
                 */
                SimInfoPreference simInfoPref = new SimInfoPreference(
                        getActivity(), siminfo.mDisplayName, siminfo.mNumber,
                        siminfo.mSlot, status, siminfo.mColor,
                        siminfo.mDispalyNumberFormat, siminfo.mSimId, true,
                        true, true);

                Xlog.i(TAG, "state of sim " + siminfo.mSimId + " is " + status);

                if (simInfoPref != null) {

                    updateCheckState(simInfoPref, siminfo);
                    if (mDisableWhenRadioOff) {
                        simInfoPref.setEnabled((status == Phone.SIM_INDICATOR_RADIOOFF) ? 
                                                false : true);
                        Xlog.i(TAG,"simInfoPref.setEnabled(); "
                                    + ((status == Phone.SIM_INDICATOR_RADIOOFF) ? false : true));
                    }

                    root.addPreference(simInfoPref);
                }
            }
        }

    }

    private void sortSimList() {
        int size = mSimList.size();
        Xlog.d(TAG, "sortSimList()+simList.size()=" + size);
        if (size == 2) {
            // 2 stands for two sim card
            SIMInfo temp1 = mSimList.get(0);
            SIMInfo temp2 = mSimList.get(1);
            if (temp1.mSlot > temp2.mSlot) {
                Xlog.d(TAG, "swap the position of simList");
                mSimList.clear();
                mSimList.add(temp2);
                mSimList.add(temp1);
            }
        }
    }

    /**
     * Update the checkbox state
     * 
     * @param pref
     *            The sim preference
     * @param siminfo
     *            Related information of this SIM card
     */
    protected void updateCheckState(SimInfoPreference pref, SIMInfo siminfo) {

        return;
    }

    private void updateSimState(int slotID, int state) {
        SIMInfo siminfo = SIMInfo.getSIMInfoBySlot(getActivity(), slotID);

        if (siminfo != null) {

            SimInfoPreference pref = (SimInfoPreference) findPreference(String
                    .valueOf(siminfo.mSimId));

            if (pref == null) {
                return;
            }
            pref.setStatus(state);

            if (mDisableWhenRadioOff) {
                pref.setEnabled((state == Phone.SIM_INDICATOR_RADIOOFF) ? false
                        : true);
                Xlog.i(TAG, "simInfoPref.setEnabled(); "
                        + ((state == Phone.SIM_INDICATOR_RADIOOFF) ? false
                                : true));
            }

            Xlog.i(TAG, "updateSimState of sim " + siminfo.mSimId + " is "
                    + state);
        }
    }

}
