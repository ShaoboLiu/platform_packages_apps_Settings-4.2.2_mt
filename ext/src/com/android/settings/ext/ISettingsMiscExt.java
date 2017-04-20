package com.android.settings.ext;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;


public interface ISettingsMiscExt {

    /**
     * 
     * @param type
     * @param apn name to query
     * @param numeric
     * @return if the specified apn could be edited.
     */
     boolean isAllowEditPresetApn(String type, String apn, String numeric);
    
    /**
     * 
     * @param apnList list
     * @param count apn count
     * @param array
     * @return default apn.
     */
     Preference getApnPref(PreferenceGroup apnList, int count, int[] array);

    /**
     * remove tethering apn setting in not orange load.
     * @param prefSc
     * @param preference
     */
    void removeTetherApnSettings(PreferenceScreen prefSc, Preference preference);

    /**
     * 
     * @param ctx
     * @return if wifi toggle button could be disabled. 
     */
    boolean isWifiToggleCouldDisabled(Context ctx); 

    /**
     * 
     * @param ctx
     * @return tether wifi string.
     */
    String getTetherWifiSSID(Context ctx);
    
    /**
     * set screen timeout preference title
     * @param pref the screen timeout preference
     */
    void setTimeoutPrefTitle(Preference pref);
    
    /**
     * Update the Apn when receiving the OmacpApn broabcast
     * @param context
     * @param values
     * @param type
     * @param simId
     * @param uri
     * @param numeric
     * @param apnId
     * @param apn
     */
    void updateApn(Context context, ContentValues values, 
                        String type, int simId, Uri uri, 
                        String numeric, String apnId, String apn);
    
    /**
     * get the User id of Apn
     * @return mApnUserId
     */
    long getApnUserId();
    
    /**
     * get the Result
     * @return mResult
     */
    boolean getResult();
}
