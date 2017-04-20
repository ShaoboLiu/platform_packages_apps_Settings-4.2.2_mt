package com.android.settings.ext;

import android.app.Fragment;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioButton;
import android.widget.TextView;

import com.mediatek.audioprofile.AudioProfileManager;
import com.mediatek.audioprofile.AudioProfileManager.Scenario;

 public class DefaultAudioProfileExt  extends ContextWrapper implements IAudioProfileExt {
        
    private Fragment mContext;
    private LayoutInflater mInflater;
    
    private TextView mTextView = null;
    private RadioButton mCheckboxButton = null;
    private TextView mSummary = null;
    
    private View mLayout;
    
    private boolean mHasMoreRingtone = false;

    public DefaultAudioProfileExt(Context context) {
        super(context);
           mInflater = (LayoutInflater)getBaseContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
   }
    
    public void onPreferenceTreeClick(Scenario scenario, Fragment fragment, String key, String className) {
        if ((Scenario.GENERAL).equals(scenario) || (Scenario.CUSTOM).equals(scenario)) {
            Bundle args = new Bundle();
            mContext = fragment;
            args.putString("profileKey", key);
            ((PreferenceActivity) mContext.getActivity()).startPreferencePanel(
                      className, args, 0, null, null, 0);
        }
    }

    public View createView(int defaultLayoutId) {
        mLayout = mInflater.inflate(defaultLayoutId, null);
        return mLayout;
    }
    
    public View getPreferenceTitle(int defaultTitleId) {
        mTextView = (TextView) mLayout.findViewById(defaultTitleId);
        return mTextView;
    }
    
    public View getPreferenceSummary(int defaultSummaryId) {
        mSummary = (TextView) mLayout.findViewById(defaultSummaryId);
           return mSummary;
    }
    
    public View getPrefRadioButton(int defaultRBId) {
        mCheckboxButton = (RadioButton)mLayout.findViewById(defaultRBId);        
        return mCheckboxButton;
    }
    
    public void setRingtonePickerParams(Intent intent) {
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_MORE_RINGTONES, false);
        mHasMoreRingtone = true;
    }

    public void setRingerVolume(AudioManager audiomanager, int volume) {
        audiomanager.setStreamVolume(AudioProfileManager.STREAM_RING, volume, 0);
        audiomanager.setStreamVolume(AudioProfileManager.STREAM_NOTIFICATION, volume, 0);
    }

   public void setVolume(AudioManager audiomanager, int streamType, int volume) {
       audiomanager.setStreamVolume(streamType, volume, 0);
    }
}
