package com.android.settings.ext;


import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.provider.Telephony;

import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.xlog.Xlog;

import java.util.ArrayList;
import java.util.HashMap;

/* Dummy implmentation , do nothing */
public class DefaultSettingsMiscExt implements ISettingsMiscExt {
    
    private static final String TAG = "DefaultSettingsMiscExt";

    private static final int SIM_CARD_1 = 0;
    private static final int SIM_CARD_2 = 1;
    
    private long mApnToUseId = -1;
    private boolean mResult;
    
    public boolean isAllowEditPresetApn(String type, String apn, String numeric) {
        return true;
    }

    public Preference getApnPref(PreferenceGroup apnList, int count, int[] array) {
        return apnList.getPreference(0);
    }
    
    public void removeTetherApnSettings(PreferenceScreen prefSc, Preference preference) {
        prefSc.removePreference(preference);
    }

    public boolean isWifiToggleCouldDisabled(Context context) {
        return true;
    }

    public String getTetherWifiSSID(Context ctx) {
        return ctx.getString(
                    com.android.internal.R.string.wifi_tether_configure_ssid_default);
    }

    public void setTimeoutPrefTitle(Preference pref) {
    }
    
    public void updateApn(Context context, ContentValues values, String type, int simId,
            Uri uri, String numeric, String apnId, String apn) {
        insertAPN(context, values, type, simId, uri, numeric, apnId);
        if (FeatureOption.MTK_GEMINI_SUPPORT) {
            forTheOtherCard(context, simId, values, numeric, apnId);  
        }
    }
    
    /**
     * Add APN record to database
     */
    private void insertAPN(Context context, ContentValues values, String type, int simId,
            Uri uri, String numeric, String apnId) {       
        boolean isApnExisted = false;
        boolean isMmsApn = "mms".equalsIgnoreCase(type);
        
        OmacpApn mOmacpApn = new OmacpApn(context, simId, uri, numeric);
        ArrayList<HashMap<String, String>> omcpIdList = mOmacpApn.getExistOmacpId();
        int sizeApn = omcpIdList.size();
        for (int i = 0; i < sizeApn; i++) {
            HashMap<String, String> map = omcpIdList.get(i);
            if (map.containsKey(apnId)) {
                
                isApnExisted = true;
                mResult = true;
                if (!isMmsApn) {
                    mApnToUseId = Long.parseLong(map.get(apnId));
                }
                break;
            }//apnid matches
        }// end of loop:for
        
        if (!isApnExisted) {
            
            long id = mOmacpApn.insert(context, values);
            if (id != -1) {
                mResult = true;
                if (!isMmsApn) {
                    mApnToUseId = id;
                    
                }
            }            
        }
    }
    
    public long getApnUserId() {
        return mApnToUseId;
    }
    
    public boolean getResult() {
        return mResult;
    }
    
    private boolean forTheOtherCard(Context context, int simId, ContentValues values,
            String numeric, String apnId) {
        Xlog.d(TAG,"for the other card");
        
        int theOtherSimId = 1 - simId;
        Uri theOtherUri = null;
        
        switch (theOtherSimId) {
            case SIM_CARD_1:
                theOtherUri = Telephony.Carriers.CONTENT_URI;
                break;
    
            case SIM_CARD_2:
                theOtherUri = Telephony.Carriers.GeminiCarriers.CONTENT_URI;
                break;
            default :
                break;
        }
        if (theOtherUri == null) {
            return false;
        }
        OmacpApn theOtherOmacpApn = new OmacpApn(context, theOtherSimId, theOtherUri, numeric);
        ArrayList<HashMap<String, String>> omcpIdList = theOtherOmacpApn.getExistOmacpId();
        int size = omcpIdList.size();
        for (int i = 0; i < size; i++) {
            HashMap<String, String> map = omcpIdList.get(i);
            if (map.containsKey(apnId)) {
                Xlog.d(TAG,"The other card: this apn already exists!");
                return true;
            }//apnid matches
        }// end of loop:for
        
        long theOtherId = -1;
        theOtherId = theOtherOmacpApn.insert(context, values);
        Xlog.d(TAG,"The other id = " + theOtherId);
        
        return theOtherId == -1 ? false : true;
    }
}

