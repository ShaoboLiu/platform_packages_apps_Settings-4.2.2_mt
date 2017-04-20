package com.android.settings.deviceinfo;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.gemini.GeminiPhone;
import com.android.settings.R;

public class ImeiInfoGemini extends PreferenceActivity {

    private static final String KEY_IMEI_SLOT1 = "imei_slot1";
    private static final String KEY_IMEI_SLOT2 = "imei_slot2";
    private static final String KEY_IMEI_SV_SLOT1 = "imei_sv_slot1";
    private static final String KEY_IMEI_SV_SLOT2 = "imei_sv_slot2";
    private static final String KEY_PRL_VERSION_SLOT1 = "prl_version_slot1";
    private static final String KEY_PRL_VERSION_SLOT2 = "prl_version_slot2";
    private static final String KEY_MEID_NUMBER_SLOT1 = "meid_number_slot1";
    private static final String KEY_MEID_NUMBER_SLOT2 = "meid_number_slot2";
    private static final String KEY_MIN_NUMBER_SLOT1 = "min_number_slot1";
    private static final String KEY_PRL_MIN_NUMBER_SLOT2 = "min_number_slot2";
    
    private GeminiPhone mGeminiPhone = null;
    private static final String CDMA = "CDMA";
    private static final String KEY_SLOT_STATUS = "slot_status";

    
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.device_info_imei_info_gemini);
        
        mGeminiPhone = (GeminiPhone) PhoneFactory.getDefaultPhone();
        
        setSlotStatus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
    
    private void setSlotStatus() {
        PreferenceScreen preference = getPreferenceScreen();
        Preference removablePref;
        // slot1: if it is not CDMA phone, deal with imei and imei sv, otherwise
        // deal with the min, prl version and meid info
        // NOTE "imei" is the "Device ID" since it represents the IMEI in GSM
        // and the MEID in CDMA
        if (mGeminiPhone.getPhoneNameGemini(Phone.GEMINI_SIM_1).equals(CDMA)) {
            setSummaryText(KEY_MEID_NUMBER_SLOT1, mGeminiPhone
                .getMeidGemini(Phone.GEMINI_SIM_1));
            setSummaryText(KEY_MIN_NUMBER_SLOT1, mGeminiPhone
                .getCdmaMinGemini(Phone.GEMINI_SIM_1));
            setSummaryText(KEY_PRL_VERSION_SLOT1, mGeminiPhone
                .getCdmaPrlVersionGemini(Phone.GEMINI_SIM_1));

            // device is not GSM/UMTS, do not display GSM/UMTS features
            // check Null in case no specified preference in overlay xml
            removablePref = preference.findPreference(KEY_IMEI_SLOT1);
            preference.removePreference(removablePref);
            removablePref = preference.findPreference(KEY_IMEI_SV_SLOT1);
            preference.removePreference(removablePref);
        } else {
            setSummaryText(KEY_IMEI_SLOT1, mGeminiPhone
                .getDeviceIdGemini(Phone.GEMINI_SIM_1));
            setSummaryText(KEY_IMEI_SV_SLOT1,
                ((TelephonyManager) getSystemService(TELEPHONY_SERVICE))
                .getDeviceSoftwareVersion());

            // device is not CDMA, do not display CDMA features
            // check Null in case no specified preference in overlay xml
            removablePref = preference.findPreference(KEY_PRL_VERSION_SLOT1);
            preference.removePreference(removablePref);
            removablePref = preference.findPreference(KEY_MEID_NUMBER_SLOT1);
            preference.removePreference(removablePref);
            removablePref = preference.findPreference(KEY_MIN_NUMBER_SLOT1);
            preference.removePreference(removablePref);
        }

        // slot2: if it is not CDMA phone, deal with imei and imei sv, otherwise
        // deal with the min, prl version and meid info
        // NOTE "imei" is the "Device ID" since it represents the IMEI in GSM
        // and the MEID in CDMA
        if (mGeminiPhone.getPhoneNameGemini(Phone.GEMINI_SIM_2).equals(CDMA)) {
            setSummaryText(KEY_MEID_NUMBER_SLOT2, mGeminiPhone
                .getMeidGemini(Phone.GEMINI_SIM_2));
            setSummaryText(KEY_PRL_MIN_NUMBER_SLOT2, mGeminiPhone
                .getCdmaMinGemini(Phone.GEMINI_SIM_2));
            setSummaryText(KEY_PRL_VERSION_SLOT2, mGeminiPhone
                .getCdmaPrlVersionGemini(Phone.GEMINI_SIM_2));

            // device is not GSM/UMTS, do not display GSM/UMTS features
            // check Null in case no specified preference in overlay xml
            removablePref = preference.findPreference(KEY_IMEI_SLOT2);
            preference.removePreference(removablePref);
            removablePref = preference.findPreference(KEY_IMEI_SV_SLOT2);
            preference.removePreference(removablePref);
        } else {
            setSummaryText(KEY_IMEI_SLOT2, mGeminiPhone
                .getDeviceIdGemini(Phone.GEMINI_SIM_2));
            setSummaryText(KEY_IMEI_SV_SLOT2,
                ((TelephonyManager) getSystemService(TELEPHONY_SERVICE))
                .getDeviceSoftwareVersion());
            // device is not CDMA, do not display CDMA features
            // check Null in case no specified preference in overlay xml
            removablePref = preference.findPreference(KEY_PRL_VERSION_SLOT2);
            preference.removePreference(removablePref);
            removablePref = preference.findPreference(KEY_MEID_NUMBER_SLOT2);
            preference.removePreference(removablePref);
            removablePref = preference.findPreference(KEY_PRL_MIN_NUMBER_SLOT2);
            preference.removePreference(removablePref);
        }

    }
 
    private void setSummaryText(String preference, String text) {
        Preference p = getPreferenceScreen().findPreference(preference);
    
        if (TextUtils.isEmpty(text)) {
            p.setSummary(getResources().getString(R.string.device_info_default));
        } else {
            p.setSummary(text);
        }
       
    }

}
