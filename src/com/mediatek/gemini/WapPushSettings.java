package com.mediatek.gemini;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.provider.Telephony.SIMInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.android.settings.R;

import com.mediatek.xlog.Xlog;

public class WapPushSettings extends SimCheckboxEntrance {

    private static final String TAG = "WapPushSettings";
    private static final String INTENT_CANCEL_NOTIFICATION = "com.mediatek.cu_wap_push_permission_cancel";

    private static final int DIALOG_WAP_PERMISSION = 1001;

    private String mNewSimName;

    private int mNewSimNum;
    private int mNewSimColor = -1;
    private LayoutInflater mFlater;
    private boolean mIsShowDlg = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mFlater = LayoutInflater.from(getActivity());

    }

    protected void addSimInfoPreference() {
        super.addSimInfoPreference();
        updateNewSimInfo();
        if (mNewSimNum > 0) {
            mIsShowDlg = true;
            if (isResumed()) {
                removeDialog(DIALOG_WAP_PERMISSION);
                showDialog(DIALOG_WAP_PERMISSION);
                setCancelable(false);
            }

        }

    }

    private void updateNewSimInfo() {
        if (mSimList != null) {

            mNewSimNum = 0;

            for (SIMInfo siminfo : mSimList) {

                if ((siminfo != null) && (siminfo.mWapPush == -1)) {
                    mNewSimNum++;
                    mNewSimName = siminfo.mDisplayName;
                    mNewSimColor = siminfo.mSimBackgroundRes;

                }

            }
        }
    }

    @Override
    public Dialog onCreateDialog(int id) {
        switch (id) {
        case DIALOG_WAP_PERMISSION:
            View permissionDlgView = createPermissionDlgView();
            if (permissionDlgView == null) {
                return null;
            }
            Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(getResources().getString(
                    R.string.gemini_wap_push_permission_title));
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setPositiveButton(android.R.string.yes,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,
                                int whichButton) {
                            // use to judge whether the click is correctly done!
                            if (mSimList != null) {
                                for (SIMInfo siminfo : mSimList) {
                                    if ((siminfo != null)
                                            && (siminfo.mWapPush == -1)) {
                                        SIMInfo.setWAPPush(WapPushSettings.this
                                                .getActivity(), 1,
                                                siminfo.mSimId);
                                        Preference pref = findPreference(String
                                                .valueOf(siminfo.mSimId));
                                        if ((pref != null)
                                                && (pref instanceof SimInfoPreference)) {
                                            SimInfoPreference simInfoPref = (SimInfoPreference) pref;
                                            if (simInfoPref != null) {
                                                simInfoPref.setCheck(true);
                                            }
                                        }
                                    }
                                }
                                cancelPermissionNotification();
                            }
                            mIsShowDlg = false;
                        }
                    });
            builder.setNegativeButton(android.R.string.no,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,
                                int whichButton) {
                            // use to judge whether the click is correctly done!
                            if (mSimList != null) {
                                for (SIMInfo siminfo : mSimList) {
                                    if ((siminfo != null)
                                            && (siminfo.mWapPush == -1)) {
                                        SIMInfo.setWAPPush(WapPushSettings.this
                                                .getActivity(), 0,
                                                siminfo.mSimId);
                                        Preference pref = findPreference(String
                                                .valueOf(siminfo.mSimId));
                                        if ((pref != null)
                                                && (pref instanceof SimInfoPreference)) {
                                            SimInfoPreference simInfoPref = (SimInfoPreference) pref;
                                            if (simInfoPref != null) {
                                                simInfoPref.setCheck(false);
                                            }
                                        }
                                    }
                                }

                                cancelPermissionNotification();

                            }
                            mIsShowDlg = false;
                        }
                    });
            AlertDialog dlg = builder.create();

            if ((dlg != null) && (permissionDlgView != null)) {

                dlg.setView(permissionDlgView);
                return dlg;
            }
        default:
            return null;
        }

    }

    @Override
    public void onResume() {
        // TODO Auto-generated method stub
        super.onResume();

        if (mIsShowDlg) {
            showDialog(DIALOG_WAP_PERMISSION);
            setCancelable(false);
        }

    }

    @Override
    public void onPause() {
        // TODO Auto-generated method stub
        super.onPause();
        if (mIsShowDlg) {
            removeDialog(DIALOG_WAP_PERMISSION);
        }

    }

    private View createPermissionDlgView() {

        if (mFlater == null) {
            return null;
        }

        View permissionDlgView = mFlater.inflate(
                R.layout.wap_push_permission_dlg, null);

        if (permissionDlgView != null) {

            TextView textName = (TextView) (permissionDlgView
                    .findViewById(R.id.sim_name_info));
            if (textName != null) {
                if (mNewSimNum == 1) {

                    if (mNewSimName != null) {
                        textName.setText(mNewSimName);
                    }
                    if (mNewSimColor != -1) {
                        Drawable drawable = getResources().getDrawable(
                                mNewSimColor);

                        textName.setBackgroundDrawable(drawable);
                    }

                } else {
                    textName.setVisibility(View.GONE);
                }

            }

        }

        return permissionDlgView;

    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {

        long simID = Long.parseLong(preference.getKey());

        SIMInfo simInfo = SIMInfo.getSIMInfoById(getActivity(), simID);

        if (simInfo != null) {

            if (simInfo.mWapPush == -1) {
                Xlog.e(TAG, "user click on a SIM with default value");

                return true;
            }

            SIMInfo.setWAPPush(getActivity(), 1 - simInfo.mWapPush, simID);

            if (preference instanceof SimInfoPreference) {
                SimInfoPreference simInfoPref = (SimInfoPreference) preference;

                if (simInfoPref != null) {
                    simInfoPref
                            .setCheck((simInfo.mWapPush == 1) ? false : true);
                }
            }

            return true;
        }
        return false;
    }

    protected boolean shouldDisableWhenRadioOff() {
        return false;
    }

    protected void updateCheckState(SimInfoPreference pref, SIMInfo siminfo) {

        pref.setCheck(siminfo.mWapPush == 1);

        return;
    }

    private void cancelPermissionNotification() {
        Intent it = new Intent(INTENT_CANCEL_NOTIFICATION);
        getActivity().sendBroadcast(it);

        Xlog.i(TAG,
                "send broadcast of com.mediatek.cu_wap_push_permission_cancel");
    }
}
