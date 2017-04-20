/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IPowerManager;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.preference.SeekBarDialogPreference;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SeekBar;

public class BrightnessPreference extends SeekBarDialogPreference implements
        SeekBar.OnSeekBarChangeListener, CheckBox.OnCheckedChangeListener {

    private SeekBar mSeekBar;
    private CheckBox mCheckBox;

    private int mOldBrightness;
    private int mOldAutomatic;

    private boolean mAutomaticAvailable;
    private boolean mAutomaticMode;
    private int mCurBrightness = -1;
    private boolean mRestoredOldState;
    ///M: add for replace the android default method@{
    //brightness mode state modify by other app
    private boolean mModeChangeOut = false;
    //brightness mode state modify by self 
    private boolean mModeChangeSelf = false;
    private boolean mFirstLaunch = false;
    ///@}

    ///M: Add tag
    private static final String TAG = "BrightnessPreference";

    // Backlight range is from 0 - 255. Need to make sure that user
    // doesn't set the backlight to 0 and get stuck
    private int mScreenBrightnessDim =
        getContext().getResources().getInteger(com.android.internal.R.integer.config_screenBrightnessDim);
    private static final int MAXIMUM_BACKLIGHT = android.os.PowerManager.BRIGHTNESS_ON;

    private static final int SEEK_BAR_RANGE = 10000;

    private ContentObserver mBrightnessObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            /**M: remvoe this @{
            mCurBrightness = -1;
            @}*/
            onBrightnessChanged();
        }
    };

    private ContentObserver mBrightnessModeObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            onBrightnessModeChanged();
        }
    };

    public BrightnessPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        /**M: comment as this method is not good @{ 
        mAutomaticAvailable = context.getResources().getBoolean(
                com.android.internal.R.bool.config_automatic_brightness_available);
        @}**/
        ///M: use the sensor manager to judge whether the auto sensor is available @{
        SensorManager mgr = (SensorManager)context.getSystemService(
                Context.SENSOR_SERVICE);
        mAutomaticAvailable = mgr.getDefaultSensor(Sensor.TYPE_LIGHT) != null;
        Log.d(TAG,"mAutomaticAvailable=" + mAutomaticAvailable);
        ///@}
        setDialogLayoutResource(R.layout.preference_dialog_brightness);
        setDialogIcon(R.drawable.ic_settings_display);
    }

    @Override
    protected void showDialog(Bundle state) {
        ///M:       @{
        if (mAutomaticAvailable && getBrightnessMode(0) != 0) {
               mFirstLaunch = true;
           Log.i(TAG, "onBindDialogView---mFirstLaunch=" + mFirstLaunch);
           }
           ///@}
        super.showDialog(state);

        getContext().getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), true,
                mBrightnessObserver);

        getContext().getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE), true,
                mBrightnessModeObserver);
        /***M: comment on as it cause some issues@{ 
        mRestoredOldState = false;
        @}***/
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        mSeekBar = getSeekBar(view);
        /**M: keep use the old way to set seekbar@{*/
        mSeekBar.setMax(MAXIMUM_BACKLIGHT - mScreenBrightnessDim);
        mOldBrightness = getBrightness(0);
        mSeekBar.setProgress(mOldBrightness - mScreenBrightnessDim);
        /***@}/
        /**M: remove this method since it cause many other issues
        mSeekBar.setMax(SEEK_BAR_RANGE);
        mOldBrightness = getBrightness();
        mSeekBar.setProgress(mOldBrightness);
        @}**/
        mCheckBox = (CheckBox)view.findViewById(R.id.automatic_mode);
        if (mAutomaticAvailable) {
            mCheckBox.setOnCheckedChangeListener(this);
            mOldAutomatic = getBrightnessMode(0);
            mAutomaticMode = mOldAutomatic == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
            mCheckBox.setChecked(mAutomaticMode);
            mSeekBar.setEnabled(!mAutomaticMode);
        } else {
            mSeekBar.setEnabled(true);
            ///M: hide the check box if auto is not available @{
            mCheckBox.setVisibility(View.GONE);
            ///@}

        }
        mSeekBar.setOnSeekBarChangeListener(this);
    }

    public void onProgressChanged(SeekBar seekBar, int progress,
            boolean fromTouch) {
        /**M: use another function to modify the brightness @{ 
        setBrightness(progress, false);
        @}**/
        ///M: direct to set the brightness
        setBrightness(progress + mScreenBrightnessDim);

    }

    public void onStartTrackingTouch(SeekBar seekBar) {
        // NA
    }

    public void onStopTrackingTouch(SeekBar seekBar) {
        // NA
    }

    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        setModeChangeState();
        setMode(isChecked ? Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                : Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        mSeekBar.setEnabled(!mAutomaticMode);
        /// @}
        /**M: comment by using the old way to set brightness on mode changed
        mSeekBar.setProgress(getBrightness());
        mSeekBar.setEnabled(!mAutomaticMode);
        setBrightness(mSeekBar.getProgress(), false);
        @{**/

        ///M: add to set brightness @{
        if (!isChecked) {
            setBrightness(mSeekBar.getProgress() + mScreenBrightnessDim);
        }
    }
    /**M:
     * if onCheckedChanged() is called first set mModeChangeSelf =true
     * when getBrightnessMode() is called there is no need to update mOldAutomatic since
     * need to restore to old state if is interrupted by other process
     */
    private void setModeChangeState() {
        if (mFirstLaunch) {
            // when first time launch the dialog with checkbox is checked no need to set mModeChangeSelf=true as it
            // won't call getBrightnessMode() so jump over this when launch first time
            mFirstLaunch = false;
        } else {
            //if brightness mode change by self set mModeChangeSelf as true so when 
            //call onBrightnessModeChanged() it no need to change old state
            if (!mModeChangeOut) {
                mModeChangeSelf = true;
            }
        }
        //Reset if brightness mode changed by other app
        if (mModeChangeOut && !mModeChangeSelf) {
            mModeChangeOut = false;
        }
    }
    /**M: 
     * Get the current brightness value from data base
     * @param defaultValue 
     * @return current brightness in database
     */
    private int getBrightness(int defaultValue) {
        int brightness = defaultValue;
        try {
            brightness = Settings.System.getInt(getContext().getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS);
        } catch (SettingNotFoundException snfe) {
            Log.d(TAG,"SettingNotFoundException");
        }
        return brightness;
    }
    /**M: comment not use this android default as it cause many issues @{
    private int getBrightness() {
        int mode = getBrightnessMode(0);
        float brightness = 0;
        if (false && mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
            brightness = Settings.System.getFloat(getContext().getContentResolver(),
                    Settings.System.SCREEN_AUTO_BRIGHTNESS_ADJ, 0);
            brightness = (brightness+1)/2;
        } else {
            if (mCurBrightness < 0) {
                brightness = Settings.System.getInt(getContext().getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS, 100);
            } else {
                brightness = mCurBrightness;
            }
            brightness = (brightness - mScreenBrightnessDim)
                    / (MAXIMUM_BACKLIGHT - mScreenBrightnessDim);
        }
        return (int)(brightness*SEEK_BAR_RANGE);
    }
    @}**/
    
    private int getBrightnessMode(int defaultValue) {
        int brightnessMode = defaultValue;
        try {
            brightnessMode = Settings.System.getInt(getContext().getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE);
        } catch (SettingNotFoundException snfe) {
            Log.d(TAG,"SettingNotFoundException");
        }
        Log.d(TAG,"brightnessMode=" + brightnessMode);
        return brightnessMode;
    }

    private void onBrightnessChanged() {
        ///M: modify to keep old way @{
        Log.i(TAG, "onBrightnessChanged");
        int brightness = getBrightness(MAXIMUM_BACKLIGHT);
        mSeekBar.setProgress(brightness - mScreenBrightnessDim);
        mOldBrightness = brightness;
        ///@}
        /**M: comment on
        mSeekBar.setProgress(getBrightness());
        **/
    }

    private void onBrightnessModeChanged() {
        ///M: add to solve bug @{
        Log.i(TAG, "onBrightnessModeChanged");
        boolean checked = getBrightnessMode(0) != 0;
        updateOldAutomaticMode();
        mCheckBox.setChecked(checked);
        mSeekBar.setEnabled(!checked);
        ///@}
        /**M: comment to use other method
        boolean checked = getBrightnessMode(0)
                == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
        mCheckBox.setChecked(checked);
        mSeekBar.setProgress(getBrightness());
        mSeekBar.setEnabled(!checked);
        **/
    }
    private void updateOldAutomaticMode() {
        Log.i(TAG, "updateOldAutomaticMode");
        if (!mModeChangeSelf) {
            mModeChangeOut = true;
        }
        if (mModeChangeOut && !mModeChangeSelf) {
            //only if brightness mode changed by other app, need to change mOldAutomatic 
            mOldAutomatic = getBrightnessMode(0);
            Log.i(TAG, "updateOldAutomaticMode+mOldAutomatic=" + mOldAutomatic);
        }
        if (mModeChangeSelf && !mModeChangeOut) {
            //Reset if brightness mode changed by self 
            mModeChangeSelf = false;
        }
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        final ContentResolver resolver = getContext().getContentResolver();

        if (positiveResult) {
            /**M: update the brightness value into database @{*/
            Settings.System.putInt(resolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    mSeekBar.getProgress() + mScreenBrightnessDim);
            /**@}*/
            /**M: comment on @{
            setBrightness(mSeekBar.getProgress(), true);
            @}**/
        } else {
            restoreOldState();
        }

        resolver.unregisterContentObserver(mBrightnessObserver);
        resolver.unregisterContentObserver(mBrightnessModeObserver);
    }

    private void restoreOldState() {
        /**M: comment on it cause the bug
        if (mRestoredOldState) return;
        **/
        if (mAutomaticAvailable) {
            setMode(mOldAutomatic);
        }
        /**M: add to solve bug @{*/
        if (!mAutomaticAvailable || mOldAutomatic == 0) {
            setBrightness(mOldBrightness);
            updateSeekBarPos(mOldBrightness);
            Settings.System.putInt(getContext().getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS,
                    mOldBrightness);
        }
        /**@}*/
        /**M: comment @{
        setBrightness(mOldBrightness, false);
        mRestoredOldState = true;
        mCurBrightness = -1;
        @}**/
    }
    /**M: 
     * Update the seek bar position
     * @param value the position of seek bar to be
     */
    private void updateSeekBarPos(int value) {
        if (mSeekBar != null) {
            Log.i(TAG,"updateSeekBar position");
            mSeekBar.setProgress(value - mScreenBrightnessDim);
        }
    }
    /**M: comment to use another one to replace @{
    private void setBrightness(int brightness, boolean write) {
        if (mAutomaticMode) {
            if (false) {
                float valf = (((float)brightness*2)/SEEK_BAR_RANGE) - 1.0f;
                try {
                    IPowerManager power = IPowerManager.Stub.asInterface(
                            ServiceManager.getService("power"));
                    if (power != null) {
                        power.setAutoBrightnessAdjustment(valf);
                    }
                    if (write) {
                        final ContentResolver resolver = getContext().getContentResolver();
                        Settings.System.putFloat(resolver,
                                Settings.System.SCREEN_AUTO_BRIGHTNESS_ADJ, valf);
                    }
                } catch (RemoteException doe) {
                }
            }
        } else {
            int range = (MAXIMUM_BACKLIGHT - mScreenBrightnessDim);
            brightness = (brightness*range)/SEEK_BAR_RANGE + mScreenBrightnessDim;
            try {
                IPowerManager power = IPowerManager.Stub.asInterface(
                        ServiceManager.getService("power"));
                if (power != null) {
                    power.setBacklightBrightness(brightness);
                }
                if (write) {
                    mCurBrightness = -1;
                    final ContentResolver resolver = getContext().getContentResolver();
                    Settings.System.putInt(resolver,
                            Settings.System.SCREEN_BRIGHTNESS, brightness);
                } else {
                    mCurBrightness = brightness;
                }
            } catch (RemoteException doe) {
            }
        }
    }
    @}***/
    
    /**M:
     * 
     *Set the brightness of devices
     */
    private void setBrightness(int brightness) {
        try {
            IPowerManager power = IPowerManager.Stub.asInterface(
                    ServiceManager.getService("power"));
            //Only set backlight value when screen is on
            if (power != null && power.isScreenOn()) {
                power.setBacklightBrightness(brightness);
            }
        } catch (RemoteException doe) {
            
        }
    }

    private void setMode(int mode) {
        mAutomaticMode = mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
        Settings.System.putInt(getContext().getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE, mode);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (getDialog() == null || !getDialog().isShowing()) return superState;

        // Save the dialog state
        final SavedState myState = new SavedState(superState);
        myState.automatic = mCheckBox.isChecked();
        myState.progress = mSeekBar.getProgress();
        myState.oldAutomatic = mOldAutomatic == 1;
        myState.oldProgress = mOldBrightness;
        myState.curBrightness = mCurBrightness;

        // Restore the old state when the activity or dialog is being paused
        restoreOldState();
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state == null || !state.getClass().equals(SavedState.class)) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        mOldBrightness = myState.oldProgress;
        mOldAutomatic = myState.oldAutomatic ? 1 : 0;
        setMode(myState.automatic ? 1 : 0);
        /**M: by using the new method
        setBrightness(myState.progress, false);
        **/
        mCurBrightness = myState.curBrightness;
        ///M: set the brightness value when restored
        setBrightness(myState.progress + mScreenBrightnessDim);

    }

    private static class SavedState extends BaseSavedState {

        boolean automatic;
        boolean oldAutomatic;
        int progress;
        int oldProgress;
        int curBrightness;

        public SavedState(Parcel source) {
            super(source);
            automatic = source.readInt() == 1;
            progress = source.readInt();
            oldAutomatic = source.readInt() == 1;
            oldProgress = source.readInt();
            curBrightness = source.readInt();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(automatic ? 1 : 0);
            dest.writeInt(progress);
            dest.writeInt(oldAutomatic ? 1 : 0);
            dest.writeInt(oldProgress);
            dest.writeInt(curBrightness);
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {

            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }
}

