package com.mediatek.gemini;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.provider.Telephony.SIMInfo;
import android.provider.Telephony.SimInfo;

import com.android.internal.telephony.ITelephony;
import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.ext.ISimRoamingExt;

import com.mediatek.xlog.Xlog;

public class SimDataRoamingSettings extends SimCheckboxEntrance {

    private static final String TAG = "SimDataRoamingSettings";

    private ITelephony mTelephony;

    private int mCurrentSimSlot;
    private long mCurrentSimID;
    private ISimRoamingExt mExt;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mTelephony = ITelephony.Stub.asInterface(ServiceManager
                .getService("phone"));
        mExt = Utils.getSimRoamingExtPlugin(this.getActivity());
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {

        // TODO Auto-generated method stub

        long simID = Long.parseLong(preference.getKey());

        SIMInfo simInfo = SIMInfo.getSIMInfoById(getActivity(), simID);

        if (simInfo != null) {
            int dataRoaming = simInfo.mDataRoaming;
            mCurrentSimSlot = simInfo.mSlot;
            mCurrentSimID = simInfo.mSimId;

            final SimInfoPreference simInfoPref = (SimInfoPreference) preference;
            if (dataRoaming == SimInfo.DATA_ROAMING_DISABLE) {
                String msg = mExt.getRoamingWarningMsg(this.getActivity(),
                        R.string.roaming_warning);
                Xlog.d(TAG, "msg=" + msg);
                new AlertDialog.Builder(getActivity()).setMessage(msg)
                        .setTitle(android.R.string.dialog_alert_title).setIcon(
                                android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog,
                                            int whichButton) {
                                        // TODO Auto-generated method stub
                                        // use to judge whether the click is
                                        // correctly done!

                                        try {
                                            if (mTelephony != null) {
                                                mTelephony
                                                        .setDataRoamingEnabledGemini(
                                                                true,
                                                                mCurrentSimSlot);

                                            }
                                        } catch (RemoteException e) {
                                            Xlog.e(TAG, "mTelephony exception");
                                            return;
                                        }
                                        SIMInfo.setDataRoaming(
                                                SimDataRoamingSettings.this
                                                        .getActivity(),
                                                SimInfo.DATA_ROAMING_ENABLE,
                                                mCurrentSimID);

                                        if (simInfoPref != null) {
                                            simInfoPref.setCheck(true);
                                        }
                                    }
                                }).setNegativeButton(android.R.string.no,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog,
                                            int whichButton) {
                                        // TODO Auto-generated method stub
                                        // use to judge whether the click is
                                        // correctly done!

                                        /*
                                         * if (simInfoPref != null) {
                                         * simInfoPref.setCheck(false); };
                                         */
                                    }
                                }).show();

            } else {

                try {
                    if (mTelephony != null) {
                        mTelephony.setDataRoamingEnabledGemini(false,
                                mCurrentSimSlot);

                    }
                } catch (RemoteException e) {
                    Xlog.e(TAG, "mTelephony exception");
                    return false;
                }
                SIMInfo.setDataRoaming(getActivity(),
                        SimInfo.DATA_ROAMING_DISABLE, mCurrentSimID);
                if (simInfoPref != null) {
                    simInfoPref.setCheck(false);
                }

            }
            return true;
        }
        return false;
    }

    @Override
    protected boolean shouldDisableWhenRadioOff() {
        return true;
    }

    protected void updateCheckState(SimInfoPreference pref, SIMInfo siminfo) {

        pref.setCheck(siminfo.mDataRoaming == SimInfo.DATA_ROAMING_ENABLE);

        return;
    }

}
