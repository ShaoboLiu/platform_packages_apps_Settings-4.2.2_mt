package com.android.settings;

import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.widget.ListView;

import com.android.settings.ext.IApnEditorExt;

import com.mediatek.xlog.Xlog;


public class ApnTypePreference extends DialogPreference
            implements DialogInterface.OnMultiChoiceClickListener {

    private int mApnTypeNum;
    private static final String TAG = "ApnTypePreference";
    private boolean[] mCheckState ;
    private String[] mApnTypeArray;        

    
    private String mTypeString;
    
    private ListView mListView;

    IApnEditorExt mExt;

    public ApnTypePreference(Context context, AttributeSet attrs) {
        super(context, attrs);              

        mExt = Utils.getApnEditorPlugin(context);
        mApnTypeArray = mExt.getApnTypeArray(getContext(), R.array.apn_type_orange,
                R.array.apn_type_cmcc, R.array.apn_type_generic);
        if (mApnTypeArray != null) {
            mApnTypeNum = mApnTypeArray.length;
        } 
        mCheckState = new boolean[mApnTypeNum];
    }
    
    public ApnTypePreference(Context context) {
        this(context, null);
    }
    
    @Override
    protected void onPrepareDialogBuilder(Builder builder) {
        super.onPrepareDialogBuilder(builder);
        
        builder.setMultiChoiceItems(mApnTypeArray, mCheckState, this);

        mListView = builder.create().getListView();
    }
    

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult) {
            updateRecord();
            callChangeListener(mTypeString);
        } else {
            intCheckState(mTypeString);
        }
    }

    public void setType(String mcc , String mnc, Intent intent) {
        String apnType = intent.getStringExtra(ApnSettings.APN_TYPE);
        boolean isTethering = ApnSettings.TETHER_TYPE.equals(apnType); 
        String numeric = mcc + mnc;
        mApnTypeArray = mExt.getApnTypeArrayByCard(getContext(), numeric, isTethering, R.array.apn_type_orange_tethering_only
                , R.array.apn_type_cmcc, R.array.apn_type_generic, mApnTypeArray);
        if (mApnTypeArray != null) {
            mApnTypeNum = mApnTypeArray.length;
        } 
        mCheckState = new boolean[mApnTypeNum];
    }

    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
        mCheckState[which] = isChecked;
    }

    private void updateRecord() {
        
        if (mListView != null) {
            
            StringBuilder strTemp = new StringBuilder("");
            
            for (int i = 0; i < mApnTypeNum; i++) {

                if (mCheckState[i]) {
                    strTemp.append(mApnTypeArray[i]).append(',');
                }
            }
            
            int length = strTemp.length();
            if (length > 1) {
                mTypeString = strTemp.substring(0, length - 1);
            } else {
                mTypeString = "";
            }
            Xlog.i(TAG, "mTypeString is " + mTypeString);

        }
        
    }


    public void intCheckState(String strType) {
        
        Xlog.d(TAG,"init CheckState: " + strType);
        if (strType == null) {          
            return;
        }
        
        mTypeString = strType;
        
        for (int i = 0; i < mApnTypeNum; i++) {
            mCheckState[i] = strType.contains(mApnTypeArray[i]);
        }
    }   
    
    public String getTypeString() {
        return mTypeString;
    }
}
