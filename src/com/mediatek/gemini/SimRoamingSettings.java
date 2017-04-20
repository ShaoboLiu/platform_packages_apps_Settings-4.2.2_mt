package com.mediatek.gemini;

import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.provider.Settings;

import com.android.internal.telephony.ITelephony;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;
import com.android.settings.ext.ISimRoamingExt;

import com.mediatek.xlog.Xlog;

public class SimRoamingSettings extends SettingsPreferenceFragment implements
        OnPreferenceChangeListener {

    private static final String KEY_ROAING_REMINDER_SETTING = "roaming_reminder_settings";
    private static final String TAG = "SimRoamingSettings";
    private static final String KEY_ROAMING_ENTRANCE = "data_roaming_settings";
    private ListPreference mRoamReminder;
    private CharSequence[] mRoamingReminderSummary;
    private ITelephony mTelephony;
    private ISimRoamingExt mExt;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.sim_roaming_settings);

        mRoamReminder = (ListPreference) findPreference(KEY_ROAING_REMINDER_SETTING);
        mRoamReminder.setOnPreferenceChangeListener(this);
        mRoamingReminderSummary = getResources().getTextArray(
                R.array.gemini_sim_roaming_reminder_entries);
        mTelephony = ITelephony.Stub.asInterface(ServiceManager
                .getService("phone"));
        mExt = Utils.getSimRoamingExtPlugin(this.getActivity());

    }

    @Override
    public void onResume() {
        // TODO Auto-generated method stub
        super.onResume();

        int prevalue = Settings.System.getInt(getContentResolver(),
                Settings.System.ROAMING_REMINDER_MODE_SETTING, 0);

        Xlog.i(TAG, "prevalue is " + prevalue);
        mRoamReminder.setValueIndex(prevalue);
        mRoamReminder.setSummary(mRoamingReminderSummary[prevalue]);
        Preference p = this.findPreference(KEY_ROAMING_ENTRANCE);
        if (p != null) {
            mExt.setSummary(p);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference arg0, Object arg1) {

        final String key = arg0.getKey();
        // TODO Auto-generated method stub
        if (KEY_ROAING_REMINDER_SETTING.equals(key)) {

            Xlog.i(TAG, "KEY_ROAING_REMINDER_SETTING.equals(key)");

            int value = Integer.parseInt((String) arg1);
            mRoamReminder.setValueIndex(value);
            mRoamReminder.setSummary(mRoamReminder.getEntry());
            Settings.System.putInt(getContentResolver(),
                    Settings.System.ROAMING_REMINDER_MODE_SETTING, value);

            Intent intent = new Intent(
                    Intent.ACTION_ROAMING_REMINDER_SETTING_CHANGED);

            intent.putExtra("mode", value);

            getActivity().sendBroadcast(intent);

            if (value == 0) {

                if (mTelephony != null) {
                    try {
                        mTelephony
                                .setRoamingIndicatorNeddedProperty(true, true);

                    } catch (RemoteException e) {
                        Xlog.e(TAG, "mTelephony exception");

                    }

                }

            }

        }
        return false;
    }

}
