package com.android.settings.ext;

import android.preference.Preference;

public interface IDeviceInfoSettingsExt {
    /**
     * initialize preference summary 
     * @param preference The parent preference
     */
    void initSummary(Preference preference);

}
