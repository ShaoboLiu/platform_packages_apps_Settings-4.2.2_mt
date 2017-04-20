package com.android.settings.ext;


import android.preference.PreferenceGroup;

public interface ISimManagementExt {
    /**
     * Remove the Auto_wap push preference screen
     * @param parent parent preference to set
     */
   void updateSimManagementPref(PreferenceGroup parent);
}