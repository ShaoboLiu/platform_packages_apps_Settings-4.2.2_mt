package com.mediatek.gemini;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.StatusBarManager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.sip.SipManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.provider.Telephony.SIMInfo;
import android.provider.Telephony.SimInfo;
import android.telephony.TelephonyManager;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CompoundButton;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.gemini.GeminiPhone;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;
import com.android.settings.ext.ISimManagementExt;

import com.mediatek.CellConnService.CellConnMgr;
import com.mediatek.common.featureoption.FeatureOption;
import com.mediatek.telephony.TelephonyManagerEx;
import com.mediatek.xlog.Xlog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class SimItem {
    public boolean mIsSim = true;
    public String mName = null;
    public String mNumber = null;
    public int mDispalyNumberFormat = 0;
    public int mColor = -1;
    public int mSlot = -1;
    public long mSimID = -1;
    public int mState = Phone.SIM_INDICATOR_NORMAL;

    /**
     * Construct of SimItem
     * 
     * @param name
     *            String name of sim card
     * @param color
     *            int color of sim card
     * @param simID
     *            long sim id of sim card
     */
    public SimItem(String name, int color, long simID) {
        mName = name;
        mColor = color;
        mIsSim = false;
        mSimID = simID;
    }

    /**
     * 
     * @param siminfo
     *            SIMInfo
     */
    public SimItem(SIMInfo siminfo) {
        mIsSim = true;
        mName = siminfo.mDisplayName;
        mNumber = siminfo.mNumber;
        mDispalyNumberFormat = siminfo.mDispalyNumberFormat;
        mColor = siminfo.mColor;
        mSlot = siminfo.mSlot;
        mSimID = siminfo.mSimId;
    }
}

public class SimManagement extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener,
        SimInfoEnablePreference.OnPreferenceClickCallback {

    private static final String TAG = "SimManagementSettings";
    // key of preference of sim managment
    private static final String KEY_SIM_INFO_CATEGORY = "sim_info";
    private static final String KEY_GENERAL_SETTINGS_CATEGORY = "general_settings";
    private static final String KEY_DEFAULT_SIM_SETTINGS_CATEGORY = "default_sim";
    private static final String KEY_VOICE_CALL_SIM_SETTING = "voice_call_sim_setting";
    private static final String KEY_VIDEO_CALL_SIM_SETTING = "video_call_sim_setting";
    private static final String KEY_SMS_SIM_SETTING = "sms_sim_setting";
    private static final String KEY_GPRS_SIM_SETTING = "gprs_sim_setting";
    private static final String KEY_SIM_CONTACTS_SETTINGS = "contacts_sim";
    private static final String KEY_3G_SERVICE_SETTING = "3g_service_settings";
    private static final String KEY_AUTO_WAP_PUSH = "wap_push_settings";

    private static boolean sVTCallSupport = true;
    private static boolean sIsVoipAvailable = true;
    private boolean mIs3gOff = false;
    private static final String TRANSACTION_START = "com.android.mms.transaction.START";
    private static final String TRANSACTION_STOP = "com.android.mms.transaction.STOP";
    private static final String MMS_TRANSACTION = "mms.transaction";

    // which simid is selected when switch video call
    private long mSelectedVideoSimId;
    private boolean mIsSlot1Insert = false;
    private boolean mIsSlot2Insert = false;

    // time out length
    private static final int DETACH_DATA_CONN_TIME_OUT = 10000;// in ms
    private static final int ATTACH_DATA_CONN_TIME_OUT = 30000;// in ms
    private static final int VIDEO_CALL_OFF = -1;
    // time out message event
    private static final int DATA_SWITCH_TIME_OUT_MSG = 2000;
    // Dialog Id for different task
    private static final int DIALOG_ACTIVATE = 1000;// radio on
    private static final int DIALOG_DEACTIVATE = 1001;// radio off
    private static final int DIALOG_WAITING = 1004;// loading when data conntion
                                                   // is switching
    private static final int DIALOG_3G_MODEM_SWITCHING = 1006;
    private static final int DIALOG_3G_MODEM_SWITCH_CONFIRM = 1007;
    private static final int DIALOG_GPRS_SWITCH_CONFIRM = 1008;
    // constant for current sim mode
    private static final int ALL_RADIO_OFF = 0;
    private static final int SIM_SLOT_1_RADIO_ON = 1;
    private static final int SIM_SLOT_2_RADIO_ON = 2;
    private static final int ALL_RADIO_ON = 3;

    private static final int SINGLE_SIM_CARD = 1;
    private static final int DOUBLE_SIM_CARD = 2;
    private static final int BASEBAND = 3;

    private DefaultSimPreference mVoiceCallSimSetting;
    private DefaultSimPreference mVideoCallSimSetting;
    private DefaultSimPreference mSmsSimSetting;
    private DefaultSimPreference mGprsSimSetting;
    private PreferenceScreen mSimAndContacts;

    private TelephonyManagerEx mTelephonyManagerEx;
    private TelephonyManager mTelephonyManager;
    private ITelephony mTelephony;
    private StatusBarManager mStatusBarManager;
    // a list store the siminfo variable
    private List<SIMInfo> mSiminfoList = new ArrayList<SIMInfo>();
    // record the current oncreate dialog id
    private int mDialogId;
    // when sim radio switch complete receive msg with this id
    private static final int EVENT_DUAL_SIM_MODE_CHANGED_COMPLETE = 1;

    private List<SimItem> mSimItemListVoice = new ArrayList<SimItem>();
    private List<SimItem> mSimItemListVideo = new ArrayList<SimItem>();
    private List<SimItem> mSimItemListSms = new ArrayList<SimItem>();
    private List<SimItem> mSimItemListGprs = new ArrayList<SimItem>();
    // to prevent click too fast to switch card 1 and 2 in radio on/off
    private boolean mIsSIMRadioSwitching = false;

    private IntentFilter mIntentFilter;
    private int mDataSwitchMsgIndex = -1;
    private CellConnMgr mCellConnMgr;
    private boolean mIsVoiceCapable = false;
    private boolean mIsSmsCapable = false;
    private boolean mIsDataConnectActing = false;
    private ISimManagementExt mExt;

    private ContentObserver mGprsDefaultSIMObserver = new ContentObserver(
            new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            updateDataConnPrefe();
        }
    };
    /*
     * Timeout handler to revent the dialog always showing if moderm not send
     * connected or disconnected intent
     */
    private Handler mTimerHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (DATA_SWITCH_TIME_OUT_MSG == msg.what) {
                Xlog.i(TAG, "reveive time out msg...");
                removeDialog(mDialogId);
                updateDataConnPrefe();
            }
        }
    };

    // receive when sim card radio switch complete
    private Messenger mSwitchRadioStateMsg = new Messenger(new Handler() {
        public void handleMessage(Message msg) {
            if (EVENT_DUAL_SIM_MODE_CHANGED_COMPLETE == msg.what) {
                Xlog.d(TAG, "dual sim mode changed");
                dealWithSwtichComplete();
            }
        }
    });
    // Receiver to handle different actions
    private BroadcastReceiver mSimReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Xlog.d(TAG, "mSimReceiver receive action=" + action);
            // Refresh the whole ui as below action all related to siminfo
            // updated
            if (action.equals(TelephonyIntents.ACTION_SIM_INFO_UPDATE)
                    || action.equals(Intent.SIM_SETTINGS_INFO_CHANGED)
                    || action.equals(TelephonyIntents.ACTION_SIM_NAME_UPDATE)
                    || action
                            .equals(TelephonyIntents.ACTION_SIM_INDICATOR_STATE_CHANGED)
                    || action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                getSimInfo();
                updatePreferenceUI();
            } else if (action.equals(TRANSACTION_START)) {
                // Disable dataconnection pref to prohibit to switch data
                // connection
                if (mGprsSimSetting.isEnabled()) {
                    mGprsSimSetting.setEnabled(false);
                }
                Dialog dlg = mGprsSimSetting.getDialog();
                if (dlg != null && dlg.isShowing()) {
                    Xlog
                            .d(TAG,
                                    "MMS starting dismiss GPRS selection dialog to prohbit data switch");
                    dlg.dismiss();
                }
            } else if (action.equals(TRANSACTION_STOP)) {
                /*
                 * if data connection is disable then able when mms transition
                 * stop if all radio is not set off
                 */
                if (!mGprsSimSetting.isEnabled()) {
                    mGprsSimSetting.setEnabled(!isRadioOff());
                }
                Dialog dlg = mGprsSimSetting.getDialog();
                if (dlg != null && dlg.isShowing()) {
                    Xlog.d(TAG, "MMS stopped dismiss GPRS selection dialog");
                    dlg.dismiss();
                }
            } else if (action.equals(GeminiPhone.EVENT_3G_SWITCH_DONE)) {
                // remove the loading dialog when 3g service switch done
                Xlog.d(TAG, "current dialog id is " + mDialogId);
                removeDialog(mDialogId);
                // restore the status bar state
                if (mStatusBarManager != null) {
                    mStatusBarManager.disable(StatusBarManager.DISABLE_NONE);
                }
                updateVideoCallDefaultSIM();
            } else if (action.equals(GeminiPhone.EVENT_3G_SWITCH_LOCK_CHANGED)) {
                boolean lockState = intent.getBooleanExtra(
                        GeminiPhone.EXTRA_3G_SWITCH_LOCKED, false);
                if (sVTCallSupport) {
                    mVideoCallSimSetting.setEnabled(!(mIs3gOff || lockState));
                    Xlog.d(TAG, "mIs3gOff=" + mIs3gOff + " lockState="
                            + lockState);
                }
            } else if (action
                    .equals(TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED)) {
                int slotId = intent.getIntExtra(Phone.GEMINI_SIM_ID_KEY, -1);
                String apnTypeList = intent
                        .getStringExtra(Phone.DATA_APN_TYPE_KEY);
                Phone.DataState state = getMobileDataState(intent);
                Xlog.i(TAG, "slotId=" + slotId);
                Xlog.i(TAG, "state=" + state);
                Xlog.i(TAG, "apnTypeList=" + apnTypeList);
                if ((state == Phone.DataState.CONNECTED)
                        || (state == Phone.DataState.DISCONNECTED)) {
                    if ((Phone.APN_TYPE_DEFAULT.equals(apnTypeList))) {
                        Xlog.d(TAG, "****the slot " + slotId + state
                                + " mIsDataConnectActing="
                                + mIsDataConnectActing);
                        if (mIsDataConnectActing) {
                            mTimerHandler
                                    .removeMessages(DATA_SWITCH_TIME_OUT_MSG);
                            removeDialog(mDialogId);
                            mIsDataConnectActing = false;
                        }

                    }
                }

            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.sim_management);
        Xlog.d(TAG, "onCreate Sim Management");
        String baseband = SystemProperties.get("gsm.baseband.capability");
        Xlog.i(TAG, "baseband is " + baseband);
        boolean support3G = false;
        if ((baseband != null) && (baseband.length() != 0)
                && (Integer.parseInt(baseband) > BASEBAND)) {
            support3G = true;
        }
        if ((!support3G) || (!FeatureOption.MTK_VT3G324M_SUPPORT)) {
            sVTCallSupport = false;
        }
        mTelephonyManagerEx = TelephonyManagerEx.getDefault();
        mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        mTelephony = ITelephony.Stub.asInterface(ServiceManager
                .getService("phone"));
        Xlog.d(TAG, "FeatureOption.MTK_GEMINI_3G_SWITCH="
                + FeatureOption.MTK_GEMINI_3G_SWITCH);
        PreferenceGroup parent = (PreferenceGroup) findPreference(KEY_GENERAL_SETTINGS_CATEGORY);
        // /M: plug in of sim management
        mExt = Utils.getSimManagmentExtPlugin(this.getActivity());
        mExt.updateSimManagementPref(parent);
        mStatusBarManager = (StatusBarManager) getSystemService(Context.STATUS_BAR_SERVICE);
        initIntentFilter();

        mVoiceCallSimSetting = (DefaultSimPreference) findPreference(KEY_VOICE_CALL_SIM_SETTING);
        mVideoCallSimSetting = (DefaultSimPreference) findPreference(KEY_VIDEO_CALL_SIM_SETTING);
        mSmsSimSetting = (DefaultSimPreference) findPreference(KEY_SMS_SIM_SETTING);
        mGprsSimSetting = (DefaultSimPreference) findPreference(KEY_GPRS_SIM_SETTING);
        mSimAndContacts = (PreferenceScreen) findPreference(KEY_SIM_CONTACTS_SETTINGS);

        mVoiceCallSimSetting.setType(GeminiUtils.TYPE_VOICECALL);
        mVoiceCallSimSetting.setOnPreferenceChangeListener(this);
        mVideoCallSimSetting.setType(GeminiUtils.TYPE_VIDEOCALL);
        mVideoCallSimSetting.setOnPreferenceChangeListener(this);
        mSmsSimSetting.setType(GeminiUtils.TYPE_SMS);
        mSmsSimSetting.setOnPreferenceChangeListener(this);
        mGprsSimSetting.setType(GeminiUtils.TYPE_GPRS);
        mGprsSimSetting.setOnPreferenceChangeListener(this);
        mCellConnMgr = new CellConnMgr();
        mCellConnMgr.register(getActivity());
        mGprsSimSetting.setCellConnMgr(mCellConnMgr);
    }

    private void initIntentFilter() {
        Xlog.d(TAG, "initIntentFilter");
        mIntentFilter = new IntentFilter(
                TelephonyIntents.ACTION_SIM_INDICATOR_STATE_CHANGED);
        mIntentFilter.addAction(Intent.SIM_SETTINGS_INFO_CHANGED);
        mIntentFilter
                .addAction(TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED);
        mIntentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        mIntentFilter.addAction(TRANSACTION_START);
        mIntentFilter.addAction(TRANSACTION_STOP);
        mIntentFilter.addAction(TelephonyIntents.ACTION_SIM_NAME_UPDATE);
        mIntentFilter.addAction(TelephonyIntents.ACTION_SIM_INFO_UPDATE);
        if (FeatureOption.MTK_GEMINI_3G_SWITCH) {
            mIntentFilter.addAction(GeminiPhone.EVENT_3G_SWITCH_DONE);
            mIntentFilter.addAction(GeminiPhone.EVENT_3G_SWITCH_LOCK_CHANGED);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Xlog.d(TAG, "onResume()");
        try {
            if (mTelephony != null) {
                mTelephony.registerForSimModeChange(mSwitchRadioStateMsg
                        .getBinder(), EVENT_DUAL_SIM_MODE_CHANGED_COMPLETE);
            }
        } catch (RemoteException e) {
            Xlog.e(TAG, "mTelephony exception");
            return;
        }
        // to init and define these boolean value in onresume since some values
        // may change onpause
        mIsSIMRadioSwitching = false;
        mIsDataConnectActing = false;
        mIsVoiceCapable = mTelephonyManager.isVoiceCapable();
        mIsSmsCapable = mTelephonyManager.isSmsCapable();
        sIsVoipAvailable = isVoipAvailable();
        Xlog.d(TAG, "mIsVoiceCapable=" + mIsVoiceCapable + " mIsSmsCapable="
                + mIsSmsCapable + " sVTCallSupport=" + sVTCallSupport
                + " sIsVoipAvailable=" + sIsVoipAvailable);

        // get new siminfo value
        getSimInfo();
        removeUnusedPref();
        updatePreferenceUI();
        getActivity().registerReceiver(mSimReceiver, mIntentFilter);
        getContentResolver()
                .registerContentObserver(
                        Settings.System
                                .getUriFor(Settings.System.GPRS_CONNECTION_SIM_SETTING),
                        false, mGprsDefaultSIMObserver);
    }

    private void removeUnusedPref() {
        Xlog.d(TAG, "removeUnusedPref()");
        if (!mIsVoiceCapable) {
            sVTCallSupport = false;
        }
        PreferenceGroup pref = (PreferenceGroup) findPreference(KEY_DEFAULT_SIM_SETTINGS_CATEGORY);
        if (!mIsVoiceCapable) {
            pref.removePreference(mVoiceCallSimSetting);
            pref.removePreference(mVideoCallSimSetting);
            if (!mIsSmsCapable) {
                pref.removePreference(mSmsSimSetting);
            }
        }
        // if not support vtcall feature then remove this feature
        if (!sVTCallSupport) {
            Xlog.d(TAG, "Video call is " + sVTCallSupport + " remove the pref");
            pref.removePreference(mVideoCallSimSetting);
        } else if ((!FeatureOption.MTK_GEMINI_3G_SWITCH) && (!mIsSlot1Insert)) {
            // if no 3G switch feature and slot 1 not sim,
            // since slot 2 not support 3G so disable video call
            mVideoCallSimSetting.setEnabled(false);
            Xlog.d(TAG, "mVideoCallSimSetting set disable");
        }
    }

    private void getSimInfo() {
        Xlog.d(TAG, "getSimInfo()");
        List<SIMInfo> simList = SIMInfo.getInsertedSIMList(getActivity());
        int simSlot = 0;
        SIMInfo tempSiminfo;
        mSiminfoList.clear();
        if (simList.size() == DOUBLE_SIM_CARD) {
            if (simList.get(0).mSlot > simList.get(1).mSlot) {
                Collections.swap(simList, 0, 1);
            }
            for (int i = 0; i < simList.size(); i++) {
                mSiminfoList.add(simList.get(i));
            }
            mIsSlot1Insert = true;
            mIsSlot2Insert = true;
        } else if (simList.size() == SINGLE_SIM_CARD) {
            mSiminfoList.add(simList.get(0));
            if (mSiminfoList.get(0).mSlot == Phone.GEMINI_SIM_1) {
                mIsSlot1Insert = true;
            } else {
                mIsSlot2Insert = true;
            }
        }
        // for debug purpose to show the actual sim information
        for (int i = 0; i < mSiminfoList.size(); i++) {
            tempSiminfo = mSiminfoList.get(i);
            Xlog.i(TAG, "siminfo.mDisplayName = " + tempSiminfo.mDisplayName);
            Xlog.i(TAG, "siminfo.mNumber = " + tempSiminfo.mNumber);
            Xlog.i(TAG, "siminfo.mSlot = " + tempSiminfo.mSlot);
            Xlog.i(TAG, "siminfo.mColor = " + tempSiminfo.mColor);
            Xlog.i(TAG, "siminfo.mDispalyNumberFormat = "
                    + tempSiminfo.mDispalyNumberFormat);
            Xlog.i(TAG, "siminfo.mSimId = " + tempSiminfo.mSimId);
        }
    }

    private void updatePreferenceUI() {
        Xlog.d(TAG, "updatePreferenceUI()");
        initDefaultSimPreference();
        setPreferenceProperty();
        if (mSiminfoList.size() > 0) {
            // there is sim card inserted so add sim card pref
            addSimInfoPreference();
        } else {
            setNoSimInfoUi();
        }
    }

    private void setNoSimInfoUi() {
        PreferenceGroup simInfoListCategory = (PreferenceGroup) findPreference(KEY_SIM_INFO_CATEGORY);
        simInfoListCategory.removeAll();
        Preference pref = new Preference(getActivity());
        if (pref != null) {
            pref.setTitle(R.string.gemini_no_sim_indicator);
            simInfoListCategory.addPreference(pref);
        }
        getPreferenceScreen().setEnabled(false);
        // for internet call to enable the voice call setting
        if (mIsVoiceCapable && isVoipAvailable()) {
            mVoiceCallSimSetting.setEnabled(true);
        }
    }

    private void addSimInfoPreference() {
        Xlog.d(TAG, "addSimInfoPreference()");
        boolean isRadioOn;// sim card is trurned on
        PreferenceGroup simInfoListCategory = (PreferenceGroup) findPreference(KEY_SIM_INFO_CATEGORY);
        simInfoListCategory.removeAll();
        for (final SIMInfo siminfo : mSiminfoList) {
            Xlog.i(TAG, "siminfo.mDisplayName = " + siminfo.mDisplayName);
            Xlog.i(TAG, "siminfo.mNumber = " + siminfo.mNumber);
            Xlog.i(TAG, "siminfo.mSlot = " + siminfo.mSlot);
            Xlog.i(TAG, "siminfo.mColor = " + siminfo.mColor);
            Xlog.i(TAG, "siminfo.mDispalyNumberFormat = "
                    + siminfo.mDispalyNumberFormat);
            Xlog.i(TAG, "siminfo.mSimId = " + siminfo.mSimId);
            // get current status of slot
            int status = mTelephonyManagerEx
                    .getSimIndicatorStateGemini(siminfo.mSlot);
            boolean isAirplaneModeOn = Settings.System.getInt(
                    getContentResolver(), Settings.System.AIRPLANE_MODE_ON, -1) == 1;
            final SimInfoEnablePreference simInfoPref = new SimInfoEnablePreference(
                    getActivity(), siminfo.mDisplayName, siminfo.mNumber,
                    siminfo.mSlot, status, siminfo.mColor,
                    siminfo.mDispalyNumberFormat, siminfo.mSimId,
                    isAirplaneModeOn);
            Xlog.i(TAG, "simid status is  " + status);
            if (simInfoPref != null) {
                simInfoPref.setClickCallback(this);
                if (mTelephony != null) {
                    try {
                        isRadioOn = mTelephony.isRadioOnGemini(siminfo.mSlot);
                        simInfoPref.setCheck(isRadioOn);
                        Xlog.d(TAG, "sim card " + siminfo.mSlot
                                + " radio state is isRadioOn=" + isRadioOn);
                    } catch (RemoteException e) {
                        Xlog.e(TAG, "mTelephony exception");

                    }
                }
                simInfoPref
                        .setCheckBoxClickListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                            @Override
                            public void onCheckedChanged(
                                    CompoundButton buttonView, boolean isChecked) {
                                Xlog.i(TAG, "receive slot " + siminfo.mSlot
                                        + " switch is clicking!");
                                if (!mIsSIMRadioSwitching) {
                                    Xlog.d(TAG, "start to turn radio in "
                                            + isChecked);
                                    mIsSIMRadioSwitching = true;
                                    simInfoPref.setCheck(isChecked);
                                    switchSimRadioState(siminfo.mSlot);
                                } else {
                                    Xlog
                                            .d(
                                                    TAG,
                                                    "Click too fast it is switching "
                                                            + "and set the switch to previous state");
                                    simInfoPref.setCheck(!isChecked);
                                }
                            }
                        });
                simInfoListCategory.addPreference(simInfoPref);
            }
        }
    }

    protected void initDefaultSimPreference() {
        Xlog.d(TAG, "initDefaultSimPreference()");
        mSimItemListVoice.clear();
        mSimItemListVideo.clear();
        mSimItemListSms.clear();
        mSimItemListGprs.clear();
        SimItem simitem;
        int k = 0;
        int simState = 0;
        for (SIMInfo siminfo : mSiminfoList) {
            if (siminfo != null) {
                simitem = new SimItem(siminfo);
                simState = mTelephonyManagerEx
                        .getSimIndicatorStateGemini(siminfo.mSlot);
                simitem.mState = simState;
                mSimItemListVoice.add(simitem);
                mSimItemListSms.add(simitem);
                mSimItemListGprs.add(simitem);
                // only when vt call support then enable to add sim info into
                // video call pref
                if (sVTCallSupport) {
                    // only sim slot 1 support vt call but if 3g switch
                    // is enable also slot 2 can be used to video call
                    if ((siminfo.mSlot == Phone.GEMINI_SIM_1)
                            || (FeatureOption.MTK_GEMINI_3G_SWITCH)) {
                        mSimItemListVideo.add(simitem);
                    }
                }
            }
        }
        // Add internet call item
        if (sIsVoipAvailable) {
            Xlog.d(TAG, "set internet call item");
            simitem = new SimItem(this.getString(R.string.gemini_intenet_call),
                    GeminiUtils.INTERNET_CALL_COLOR,
                    Settings.System.VOICE_CALL_SIM_SETTING_INTERNET);
            mSimItemListVoice.add(simitem);
        }
        simitem = new SimItem(this
                .getString(R.string.gemini_default_sim_always_ask),
                GeminiUtils.NO_COLOR,
                Settings.System.DEFAULT_SIM_SETTING_ALWAYS_ASK);
        mSimItemListVoice.add(simitem);
        mSimItemListSms.add(simitem);
        simitem = new SimItem(
                this.getString(R.string.gemini_default_sim_never),
                GeminiUtils.NO_COLOR,
                Settings.System.GPRS_CONNECTION_SIM_SETTING_NEVER);
        mSimItemListGprs.add(simitem);
        Xlog.d(TAG, "mSimItemListVoice.size=" + mSimItemListVoice.size()
                + " mSimItemListVideo.size=" + mSimItemListVideo.size()
                + " mSimItemListSms.size=" + mSimItemListSms.size()
                + " mSimItemListSms.size=" + mSimItemListGprs.size());
        // set adapter for each preference
        mSmsSimSetting.setInitData(mSimItemListSms);
        mGprsSimSetting.setInitData(mSimItemListGprs);
        if (mIsVoiceCapable) {
            mVoiceCallSimSetting.setInitData(mSimItemListVoice);
        }
        if (sVTCallSupport) {
            mVideoCallSimSetting.setInitData(mSimItemListVideo);
        }
    }

    private int current3GSlotId() {
        int slot3G = VIDEO_CALL_OFF;
        try {
            if (mTelephony != null) {
                slot3G = mTelephony.get3GCapabilitySIM();
            }
        } catch (RemoteException e) {
            Xlog.e(TAG, "mTelephony exception");
        }
        return slot3G;
    }

    private void setPreferenceProperty() {
        long voicecallID = getDataValue(Settings.System.VOICE_CALL_SIM_SETTING);
        long smsID = getDataValue(Settings.System.SMS_SIM_SETTING);
        long dataconnectionID = getDataValue(Settings.System.GPRS_CONNECTION_SIM_SETTING);
        int videocallSlotID = VIDEO_CALL_OFF;
        if (!FeatureOption.MTK_GEMINI_3G_SWITCH) {
            videocallSlotID = Phone.GEMINI_SIM_1;
        } else {
            videocallSlotID = current3GSlotId();
        }
        Xlog.i(TAG, "voicecallID =" + voicecallID + " smsID =" + smsID
                + " dataconnectionID =" + dataconnectionID
                + " videocallSlotID =" + videocallSlotID);
        int pos = 0;
        for (SIMInfo siminfo : mSiminfoList) {
            if (siminfo != null) {
                if (siminfo.mSimId == voicecallID) {
                    if (mIsVoiceCapable) {
                        mVoiceCallSimSetting.setInitValue(pos);
                        mVoiceCallSimSetting.setSummary(siminfo.mDisplayName);
                    }
                }
                if (siminfo.mSimId == smsID) {
                    mSmsSimSetting.setInitValue(pos);
                    mSmsSimSetting.setSummary(siminfo.mDisplayName);
                }
                if (siminfo.mSimId == dataconnectionID) {
                    mGprsSimSetting.setInitValue(pos);
                    mGprsSimSetting.setSummary(siminfo.mDisplayName);
                }
                if ((sVTCallSupport) && (siminfo.mSlot == videocallSlotID)) {
                    if (!FeatureOption.MTK_GEMINI_3G_SWITCH) {
                        // if not support 3G switch then only slot 1 which is
                        // number 0 can be set
                        mVideoCallSimSetting.setInitValue(0);
                    } else {
                        mVideoCallSimSetting.setInitValue(pos);
                    }
                    mVideoCallSimSetting.setSummary(siminfo.mDisplayName);
                }
            }
            pos++;
        }
        int nSim = mSiminfoList.size();
        if (mIsVoiceCapable) {
            if (voicecallID == Settings.System.VOICE_CALL_SIM_SETTING_INTERNET) {
                mVoiceCallSimSetting.setInitValue(nSim);
                mVoiceCallSimSetting.setSummary(R.string.gemini_intenet_call);
            } else if (voicecallID == Settings.System.DEFAULT_SIM_SETTING_ALWAYS_ASK) {
                mVoiceCallSimSetting.setInitValue(sIsVoipAvailable ? (nSim + 1)
                        : nSim);
                mVoiceCallSimSetting
                        .setSummary(R.string.gemini_default_sim_always_ask);
            } else if (voicecallID == Settings.System.DEFAULT_SIM_NOT_SET) {
                mVoiceCallSimSetting
                        .setInitValue((int) Settings.System.DEFAULT_SIM_NOT_SET);
                mVoiceCallSimSetting.setSummary(R.string.apn_not_set);
            }
        }
        if (smsID == Settings.System.DEFAULT_SIM_SETTING_ALWAYS_ASK) {
            mSmsSimSetting.setInitValue(nSim);
            mSmsSimSetting.setSummary(R.string.gemini_default_sim_always_ask);
        } else if (smsID == Settings.System.DEFAULT_SIM_NOT_SET) {
            mSmsSimSetting.setSummary(R.string.apn_not_set);
        }
        if (dataconnectionID == Settings.System.GPRS_CONNECTION_SIM_SETTING_NEVER) {
            mGprsSimSetting.setInitValue(nSim);
            mGprsSimSetting.setSummary(R.string.gemini_default_sim_never);
        } else if (dataconnectionID == Settings.System.DEFAULT_SIM_NOT_SET) {
            mGprsSimSetting.setSummary(R.string.apn_not_set);
        }
        if (sVTCallSupport) {
            if (FeatureOption.MTK_GEMINI_3G_SWITCH) {
                if (videocallSlotID == VIDEO_CALL_OFF) {
                    mIs3gOff = true;
                    mVideoCallSimSetting
                            .setSummary(R.string.gemini_default_sim_3g_off);
                } else {
                    mIs3gOff = false;
                }
                try {
                    if (mTelephony != null) {
                        mVideoCallSimSetting
                                .setEnabled(!(mIs3gOff || mTelephony
                                        .is3GSwitchLocked()));
                        Xlog.i(TAG, "mIs3gOff=" + mIs3gOff);
                        Xlog.i(TAG, "mTelephony.is3GSwitchLocked() is "
                                + mTelephony.is3GSwitchLocked());
                    }
                } catch (RemoteException e) {
                    Xlog.e(TAG, "mTelephony exception");
                    return;
                }
            } else {
                long videocallID = Settings.System.getLong(
                        getContentResolver(),
                        Settings.System.VIDEO_CALL_SIM_SETTING,
                        Settings.System.DEFAULT_SIM_NOT_SET);
                if (videocallID == Settings.System.DEFAULT_SIM_NOT_SET) {
                    mVideoCallSimSetting.setSummary(R.string.apn_not_set);
                }
            }
        }
        mGprsSimSetting.setEnabled(isGPRSEnable());
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {
        if (KEY_SIM_CONTACTS_SETTINGS.equals(preference.getKey())) {
            if (mSiminfoList.size() == 1) {
                SIMInfo siminfo = mSiminfoList.get(0);
                if (siminfo != null) {
                    Intent intent = new Intent();
                    intent.setClassName("com.android.settings",
                            "com.mediatek.gemini.GeminiSIMTetherInfo");
                    int slot = siminfo.mSlot;
                    Xlog.d(TAG, "Enter sim contanct of sim " + siminfo.mSlot);
                    if (slot >= 0) {
                        intent.putExtra("simid", siminfo.mSimId);
                        mSimAndContacts.setIntent(intent);
                    }
                }
            } else {
                // two sim cards inserted to lauch a simListEntrance
                // activity to select edit which sim card
                Bundle extras = new Bundle();
                extras.putInt("type",
                        SimListEntrance.SIM_CONTACTS_SETTING_INDEX);
                startFragment(this, SimListEntrance.class.getCanonicalName(),
                        -1, extras, R.string.gemini_contacts_sim_title);
                Xlog
                        .i(
                                TAG,
                                "startFragment(this, "
                                        + "SimListEntrance.class.getCanonicalName(), -1, extras);");
            }
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public boolean onPreferenceChange(Preference arg0, Object arg1) {
        final String key = arg0.getKey();
        Xlog.i(TAG, "Enter onPreferenceChange function with " + key);
        if (KEY_VOICE_CALL_SIM_SETTING.equals(key)) {
            Settings.System.putLong(getContentResolver(),
                    Settings.System.VOICE_CALL_SIM_SETTING, (Long) arg1);
            Intent intent = new Intent(
                    Intent.ACTION_VOICE_CALL_DEFAULT_SIM_CHANGED);
            intent.putExtra("simid", (Long) arg1);
            getActivity().sendBroadcast(intent);
            Xlog.d(TAG, "send broadcast voice call change with simid="
                    + (Long) arg1);
            updateDefaultSIMSummary(mVoiceCallSimSetting, (Long) arg1);
        } else if (KEY_VIDEO_CALL_SIM_SETTING.equals(key)) {
            if (FeatureOption.MTK_GEMINI_3G_SWITCH) {
                mSelectedVideoSimId = (Long) arg1;
                showDialog(DIALOG_3G_MODEM_SWITCH_CONFIRM);
                setOnCancelListener(new DialogInterface.OnCancelListener() {
                    public void onCancel(DialogInterface dialog) {
                        updateVideoCallDefaultSIM();
                    }
                });
            }
        } else if (KEY_SMS_SIM_SETTING.equals(key)) {
            Settings.System.putLong(getContentResolver(),
                    Settings.System.SMS_SIM_SETTING, (Long) arg1);
            Intent intent = new Intent(Intent.ACTION_SMS_DEFAULT_SIM_CHANGED);
            intent.putExtra("simid", (Long) arg1);
            getActivity().sendBroadcast(intent);
            Xlog.d(TAG, "send broadcast sms change with simid=" + (Long) arg1);
            updateDefaultSIMSummary(mSmsSimSetting, (Long) arg1);
        } else if (KEY_GPRS_SIM_SETTING.equals(key)) {
            long simid = ((Long) arg1).longValue();
            Xlog.d(TAG, "value=" + simid);
            // when set close data connection
            if (simid == Settings.System.GPRS_CONNECTION_SIM_SETTING_NEVER) {
                switchGprsDefautlSIM(simid);
                return true;
            }
            SIMInfo siminfo = findSIMInfoBySimId(simid);
            if (siminfo == null) {
                Xlog.d(TAG, "Error need to check reason...");
                return false;
            }
            mDataSwitchMsgIndex = dataSwitchConfirmDlgMsg(siminfo);
            if (mDataSwitchMsgIndex == -1) {
                switchGprsDefautlSIM(simid);
            } else {
                showDialog(DIALOG_GPRS_SWITCH_CONFIRM);
                setOnCancelListener(new DialogInterface.OnCancelListener() {
                    public void onCancel(DialogInterface dialog) {
                        updateDataConnPrefe();
                    }
                });
            }
        }
        return true;
    }

    @Override
    public void onPause() {
        super.onPause();
        Xlog.d(TAG, "OnPause()");
        removeDialog(mDialogId);
        getContentResolver().unregisterContentObserver(mGprsDefaultSIMObserver);

        getActivity().unregisterReceiver(mSimReceiver);
        try {
            if (mTelephony != null) {
                mTelephony.unregisterForSimModeChange(mSwitchRadioStateMsg
                        .getBinder());
            }
        } catch (RemoteException e) {
            Xlog.e(TAG, "mTelephony exception");
            return;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Xlog.d(TAG, "onDestroy()");
        mCellConnMgr.unregister();
        mTimerHandler.removeMessages(DATA_SWITCH_TIME_OUT_MSG);
    }

    private void updateDefaultSIMSummary(DefaultSimPreference pref, Long simid) {
        Xlog.d(TAG, "updateDefaultSIMSummary() with simid=" + simid);
        if (simid > 0) {
            SIMInfo siminfo = getSIMInfoById(simid);
            if (siminfo != null) {
                pref.setSummary(siminfo.mDisplayName);
            }
        } else if (simid == Settings.System.VOICE_CALL_SIM_SETTING_INTERNET) {
            pref.setSummary(R.string.gemini_intenet_call);
        } else if (simid == Settings.System.DEFAULT_SIM_SETTING_ALWAYS_ASK) {
            pref.setSummary(R.string.gemini_default_sim_always_ask);
        } else if (simid == Settings.System.GPRS_CONNECTION_SIM_SETTING_NEVER) {
            pref.setSummary(R.string.gemini_default_sim_never);
        }
    }

    /**
     * Get the corresponding siminfo by simid
     * 
     * @param simid
     *            is the sim card id
     */
    private SIMInfo getSIMInfoById(Long simid) {
        for (SIMInfo siminfo : mSiminfoList) {
            if (siminfo.mSimId == simid) {
                return siminfo;
            }
        }
        Xlog
                .d(TAG, "Error there is no correct siminfo found by simid "
                        + simid);
        return null;
    }

    private int dataSwitchConfirmDlgMsg(SIMInfo siminfo) {
        boolean isInRoaming = mTelephonyManager
                .isNetworkRoamingGemini(siminfo.mSlot);
        boolean isRoamingDataAllowed = (siminfo.mDataRoaming == SimInfo.DATA_ROAMING_ENABLE);
        Xlog.d(TAG, "isInRoaming=" + isInRoaming + " isRoamingDataAllowed="
                + isRoamingDataAllowed);
        // by support 3G switch when data connection switch
        // and to a slot not current set 3G service
        if (isInRoaming) {
            if (!isRoamingDataAllowed) {
                if (FeatureOption.MTK_GEMINI_3G_SWITCH) {
                    if (siminfo.mSlot != current3GSlotId()) {
                        // under roaming but not abled and switch card is not 3G
                        // slot, \
                        // to pormpt user turn on roaming and how to modify to
                        // 3G service
                        return R.string.gemini_3g_disable_warning_case3;
                    } else {
                        // switch card is 3G slot but not able to roaming
                        // so only prompt to turn on roaming
                        return R.string.gemini_3g_disable_warning_case0;
                    }
                } else {
                    // no support 3G service so only prompt user to turn on
                    // roaming
                    return R.string.gemini_3g_disable_warning_case0;
                }
            } else {
                if (FeatureOption.MTK_GEMINI_3G_SWITCH) {
                    if (siminfo.mSlot != current3GSlotId()) {
                        // by support 3g switch and switched sim is not
                        // 3g slot to prompt user how to modify 3G service
                        return R.string.gemini_3g_disable_warning_case1;
                    }
                }
            }
        } else {
            if (FeatureOption.MTK_GEMINI_3G_SWITCH
                    && siminfo.mSlot != current3GSlotId()) {
                // not in roaming but switched sim is not 3G
                // slot so prompt user to modify 3G service
                return R.string.gemini_3g_disable_warning_case1;
            }

        }
        return -1;
    }

    private int findListPosBySimId(long simid) {
        int size = mSiminfoList.size();
        int pos = -1;
        // if only one sim card inserted the pos must be 0
        if (size == SINGLE_SIM_CARD) {
            pos = 0;
        }
        // if two sim inserted then the pos would order as the slot sequence
        if (size == DOUBLE_SIM_CARD) {
            SIMInfo tempSIMInfo = findSIMInfoBySimId(simid);
            if (tempSIMInfo == null) {
                Xlog.d(TAG,
                        "Error can not find the sim id with related siminfo");
            } else {
                pos = tempSIMInfo.mSlot;
            }
        }
        Xlog.d(TAG, size + " sim card inserted and the sim is in pos with "
                + pos);
        return pos;
    }

    private SIMInfo findSIMInfoBySimId(long simid) {
        for (SIMInfo siminfo : mSiminfoList) {
            if (siminfo.mSimId == simid) {
                return siminfo;
            }
        }
        Xlog.d(TAG, "Error happend on findSIMInfoBySimId no siminfo find");
        return null;
    }

    private SIMInfo findSIMInofBySlotId(int mslot) {
        for (SIMInfo siminfo : mSiminfoList) {
            if (siminfo.mSlot == mslot) {
                return siminfo;
            }
        }
        Xlog.d(TAG, "Error happend on findSIMInofBySlotId no siminfo find");
        return null;
    }

    @Override
    public Dialog onCreateDialog(int id) {
        ProgressDialog dialog = new ProgressDialog(getActivity());
        Builder builder = new AlertDialog.Builder(getActivity());
        AlertDialog alertDlg;
        mDialogId = id;
        switch (id) {
        case DIALOG_ACTIVATE:
            dialog.setMessage(getResources().getString(
                    R.string.gemini_sim_mode_progress_activating_message));
            dialog.setIndeterminate(true);
            return dialog;
        case DIALOG_DEACTIVATE:
            dialog.setMessage(getResources().getString(
                    R.string.gemini_sim_mode_progress_deactivating_message));
            dialog.setIndeterminate(true);
            return dialog;
        case DIALOG_WAITING:
            dialog.setMessage(getResources().getString(
                    R.string.gemini_data_connection_progress_message));
            dialog.setIndeterminate(true);
            return dialog;
        case DIALOG_GPRS_SWITCH_CONFIRM:
            builder.setTitle(android.R.string.dialog_alert_title);
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setMessage(getResources().getString(mDataSwitchMsgIndex));
            builder.setPositiveButton(android.R.string.yes,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,
                                int whichButton) {
                            if ((mDataSwitchMsgIndex == R.string.gemini_3g_disable_warning_case0)
                                    || (mDataSwitchMsgIndex == R.string.gemini_3g_disable_warning_case0)) {
                                enableDataRoaming(mGprsSimSetting.getValue());
                            }
                            switchGprsDefautlSIM(mGprsSimSetting.getValue());
                        }
                    });
            builder.setNegativeButton(android.R.string.no,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,
                                int whichButton) {
                            updateDataConnPrefe();
                        }
                    });
            alertDlg = builder.create();
            return alertDlg;
        case DIALOG_3G_MODEM_SWITCH_CONFIRM:
            builder.setTitle(android.R.string.dialog_alert_title);
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setMessage(getResources().getString(
                    R.string.gemini_3g_modem_switch_confirm_message));
            builder.setPositiveButton(android.R.string.yes,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,
                                int whichButton) {
                            switchVideoCallDefaultSIM(mSelectedVideoSimId);
                        }
                    });
            builder.setNegativeButton(android.R.string.no,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,
                                int whichButton) {
                            updateVideoCallDefaultSIM();
                        }
                    });
            alertDlg = builder.create();
            return alertDlg;
        case DIALOG_3G_MODEM_SWITCHING:
            dialog.setMessage(getResources().getString(
                    R.string.gemini_3g_modem_switching_message));
            dialog.setIndeterminate(true);
            Window win = dialog.getWindow();
            WindowManager.LayoutParams lp = win.getAttributes();
            // lp.flags |= WindowManager.LayoutParams.FLAG_HOMEKEY_DISPATCHED;
            win.setAttributes(lp);
            return dialog;
        default:
            return null;
        }
    }

    private void switchSimRadioState(int slot) {
        int dualSimMode = Settings.System.getInt(this.getContentResolver(),
                Settings.System.DUAL_SIM_MODE_SETTING, -1);
        Xlog.i(TAG, "The current dual sim mode is " + dualSimMode);
        boolean isAirplaneOn = Settings.System
                .getInt(this.getContentResolver(),
                        Settings.System.AIRPLANE_MODE_ON, -1) > 0;
        Xlog.d(TAG, "Airplane mode is " + isAirplaneOn);
        if (isAirplaneOn) {
            if (mIsSlot1Insert || mIsSlot2Insert) {
                int dualMode = slot == Phone.GEMINI_SIM_1 ? SIM_SLOT_1_RADIO_ON
                        : SIM_SLOT_2_RADIO_ON;
                Settings.System.putInt(this.getContentResolver(),
                        Settings.System.AIRPLANE_MODE_ON, 0);
                Settings.System.putInt(this.getContentResolver(),
                        Settings.System.DUAL_SIM_MODE_SETTING, dualMode);
                Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
                intent.putExtra("state", false);
                getActivity().sendBroadcast(intent);
                showDialog(DIALOG_ACTIVATE);
            }
            return;
        }
        int dualState = 0;
        boolean isRadioOn = false;
        switch (dualSimMode) {
        case ALL_RADIO_OFF:
            if (slot == Phone.GEMINI_SIM_1) {
                dualState = SIM_SLOT_1_RADIO_ON;
            } else if (slot == Phone.GEMINI_SIM_2) {
                dualState = SIM_SLOT_2_RADIO_ON;
            }
            Xlog.d(TAG, "Turning on only sim " + slot);
            isRadioOn = true;
            break;
        case SIM_SLOT_1_RADIO_ON:
            if (slot == Phone.GEMINI_SIM_1) {
                dualState = ALL_RADIO_OFF;
                isRadioOn = false;
                Xlog.d(TAG, "Turning off sim " + slot
                        + " and all sim radio is off");
            } else if (slot == Phone.GEMINI_SIM_2) {
                if (mIsSlot1Insert) {
                    dualState = ALL_RADIO_ON;
                    Xlog.d(TAG, "sim 0 was radio on and now turning on sim "
                            + slot);
                } else {
                    dualState = SIM_SLOT_2_RADIO_ON;
                    Xlog.d(TAG, "Turning on only sim " + slot);
                }
                isRadioOn = true;
            }
            break;
        case SIM_SLOT_2_RADIO_ON:
            if (slot == Phone.GEMINI_SIM_2) {
                dualState = ALL_RADIO_OFF;
                isRadioOn = false;
                Xlog.d(TAG, "Turning off sim " + slot
                        + " and all sim radio is off");
            } else if (slot == Phone.GEMINI_SIM_1) {
                if (mIsSlot2Insert) {
                    dualState = ALL_RADIO_ON;
                    Xlog.d(TAG, "sim 1 was radio on and now turning on sim "
                            + slot);
                } else {
                    dualState = SIM_SLOT_1_RADIO_ON;
                    Xlog.d(TAG, "Turning on only sim " + slot);
                }
                isRadioOn = true;
            }
            break;
        case ALL_RADIO_ON:
            if (slot == Phone.GEMINI_SIM_1) {
                dualState = SIM_SLOT_2_RADIO_ON;
            } else if (slot == Phone.GEMINI_SIM_2) {
                dualState = SIM_SLOT_1_RADIO_ON;
            }
            Xlog.d(TAG, "Turning off only sim " + slot);
            isRadioOn = false;
            break;
        default:
            Xlog.d(TAG, "Error not correct values");
            return;
        }
        Xlog.d(TAG, "dualState=" + dualState + " isRadioOn=" + isRadioOn);
        Settings.System.putInt(this.getContentResolver(),
                Settings.System.DUAL_SIM_MODE_SETTING, dualState);
        Intent intent = new Intent(Intent.ACTION_DUAL_SIM_MODE_CHANGED);
        intent.putExtra(Intent.EXTRA_DUAL_SIM_MODE, dualState);
        getActivity().sendBroadcast(intent);
        if (isRadioOn) {
            showDialog(DIALOG_ACTIVATE);
        } else {
            showDialog(DIALOG_DEACTIVATE);
        }
        setCancelable(false);
    }

    private void dealWithSwtichComplete() {
        Xlog.i(TAG, "dealWithSwtichComplete()");
        removeDialog(mDialogId);
        Xlog.e(TAG, "mIsSIMModeSwitching is " + mIsSIMRadioSwitching);
        if (!mIsSIMRadioSwitching) {
            Xlog.i(TAG, "Error happend, should not be happened...");
        }
        mIsSIMRadioSwitching = false;
        mGprsSimSetting.setEnabled(!isRadioOff());
    }

    /**
     *show attach gprs dialog and revent time out to send a delay msg
     * 
     */
    private void showDataConnDialog(boolean isConnect) {
        long delaytime = 0;
        if (isConnect) {
            delaytime = ATTACH_DATA_CONN_TIME_OUT;
        } else {
            delaytime = DETACH_DATA_CONN_TIME_OUT;
        }
        mTimerHandler.sendEmptyMessageDelayed(DATA_SWITCH_TIME_OUT_MSG,
                delaytime);
        showDialog(DIALOG_WAITING);
        setCancelable(false);
        mIsDataConnectActing = true;
    }

    private static Phone.DataState getMobileDataState(Intent intent) {
        String str = intent.getStringExtra(Phone.STATE_KEY);
        if (str != null) {
            return Enum.valueOf(Phone.DataState.class, str);
        } else {
            return Phone.DataState.DISCONNECTED;
        }
    }

    /*
     * Update dataconnection prefe with new selected value and new sim name as
     * summary
     */
    private void updateDataConnPrefe() {
        long simid = Settings.System.getLong(getContentResolver(),
                Settings.System.GPRS_CONNECTION_SIM_SETTING,
                Settings.System.DEFAULT_SIM_NOT_SET);
        Xlog.i(TAG, "Gprs connection SIM changed with simid is " + simid);
        if (simid == Settings.System.GPRS_CONNECTION_SIM_SETTING_NEVER) {
            // data connection is set to close so the
            // initvalue would be the last one which is size of mSiminfoList
            mGprsSimSetting.setInitValue(mSiminfoList.size());
            mGprsSimSetting.setSummary(R.string.gemini_default_sim_never);
        } else if (simid > Settings.System.GPRS_CONNECTION_SIM_SETTING_NEVER) {
            // By usding the sim id to find corresponding
            // siminfo and position in gprs pref then update
            int pos = findListPosBySimId(simid);
            SIMInfo siminfo = findSIMInfoBySimId(simid);
            if (siminfo == null) {
                Xlog.d(TAG, "Error no correct siminfo get...");
                return;
            }
            mGprsSimSetting.setInitValue(pos);
            mGprsSimSetting.setSummary(siminfo.mDisplayName);
        } else {
            Xlog.d(TAG, "Error wrong simid need to check...");
        }
    }

    /**
     * update video call default SIM value and summary
     */

    private void updateVideoCallDefaultSIM() {
        Xlog.d(TAG, "updateVideoCallDefaultSIM()");
        if (mTelephony != null) {
            try {
                int slotId = mTelephony.get3GCapabilitySIM();
                Xlog.d(TAG, "updateVideoCallDefaultSIM()---slotId=" + slotId);
                if (slotId < 0) {
                    return;
                }
                SIMInfo siminfo = findSIMInofBySlotId(slotId);
                if (siminfo != null) {
                    int pos = findListPosBySimId(siminfo.mSimId);
                    if (pos >= 0) {
                        mVideoCallSimSetting.setInitValue(pos);
                        mVideoCallSimSetting.setSummary(siminfo.mDisplayName);
                    }
                } else {
                    Xlog.d(TAG, "mVideoCallSimSetting.setInitValue(-1)");
                    mVideoCallSimSetting.setInitValue(-1);
                }
            } catch (RemoteException e) {
                Xlog.e(TAG, "mTelephony exception");
            }
        }
    }

    /**
     * Check if voip is supported and is enabled
     */
    private boolean isVoipAvailable() {
        int isInternetCallEnabled = android.provider.Settings.System.getInt(
                getContentResolver(),
                android.provider.Settings.System.ENABLE_INTERNET_CALL, 0);
        return (SipManager.isVoipSupported(getActivity()))
                && (isInternetCallEnabled != 0);

    }

    /**
     * switch data connection default SIM
     * 
     * @param value
     *            : sim id of the new default SIM
     */
    private void switchGprsDefautlSIM(long simid) {
        Xlog.d(TAG, "switchGprsDefautlSIM() with simid=" + simid);
        if (simid < 0) {
            return;
        }
        long curConSimId = Settings.System.getLong(getContentResolver(),
                Settings.System.GPRS_CONNECTION_SIM_SETTING,
                Settings.System.DEFAULT_SIM_NOT_SET);
        if (simid == curConSimId) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_DATA_DEFAULT_SIM_CHANGED);
        intent.putExtra("simid", simid);
        // simid>0 means one of sim card is selected
        // and <0 is close id which is -1 so mean disconnect
        boolean isConnect = (simid > 0) ? true : false;
        showDataConnDialog(isConnect);
        getActivity().sendBroadcast(intent);
    }

    private void enableDataRoaming(long value) {
        Xlog.d(TAG, "enableDataRoaming with SimId=" + value);
        try {
            if (mTelephony != null) {
                mTelephony.setDataRoamingEnabledGemini(true, SIMInfo
                        .getSlotById(getActivity(), value));
            }
        } catch (RemoteException e) {
            Xlog.e(TAG, "mTelephony exception");
            return;
        }
        SIMInfo.setDataRoaming(getActivity(), SimInfo.DATA_ROAMING_ENABLE,
                value);
    }

    /**
     * switch video call prefer sim if 3G switch feature is enabled
     * 
     * @param slotID
     */
    private void switchVideoCallDefaultSIM(long simid) {
        Xlog.i(TAG, "switchVideoCallDefaultSIM to " + simid);
        if (mTelephony != null) {
            SIMInfo siminfo = findSIMInfoBySimId(simid);
            Xlog.i(TAG, "siminfo = " + siminfo);
            if (siminfo == null) {
                Xlog.d(TAG, "Error no corrent siminfo found");
                return;
            }
            try {
                Xlog.i(TAG, "set sim slot " + siminfo.mSlot
                        + " with 3G capability");
                if (mTelephony.set3GCapabilitySIM(siminfo.mSlot)) {
                    // during video call switch disable the status bar,
                    // prohibit to change data connection in status
                    if (mStatusBarManager != null) {
                        mStatusBarManager
                                .disable(StatusBarManager.DISABLE_EXPAND
                                        | StatusBarManager.DISABLE_HOME
                                        | StatusBarManager.DISABLE_RECENT
                                        | StatusBarManager.DISABLE_BACK);
                    }
                    showDialog(DIALOG_3G_MODEM_SWITCHING);
                    setCancelable(false);
                } else {
                    updateVideoCallDefaultSIM();
                }
            } catch (RemoteException e) {
                Xlog.e(TAG, "mTelephony exception");
                return;
            }

        }
    }

    private long getDataValue(String dataString) {
        return Settings.System.getLong(getContentResolver(), dataString,
                Settings.System.DEFAULT_SIM_NOT_SET);
    }

    /**
     * Returns whether is in airplance or mms is under transaction
     * 
     * @return is airplane or mms is in transaction
     * 
     */
    private boolean isGPRSEnable() {
        boolean isMMSProcess = (Settings.System.getInt(this
                .getContentResolver(), MMS_TRANSACTION, 0) > 0) ? true : false;
        boolean isRadioOff = isRadioOff();
        Xlog.d(TAG, "isMMSProcess=" + isMMSProcess + " isRadioOff="
                + isRadioOff);
        return !(isMMSProcess || isRadioOff);
    }

    /**
     * @return is airplane mode or all sim card is set on radio off
     * 
     */
    private boolean isRadioOff() {
        boolean isAllRadioOff = (Settings.System.getInt(getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, -1) == 1)
                || (Settings.System.getInt(getContentResolver(),
                        Settings.System.DUAL_SIM_MODE_SETTING, -1) == ALL_RADIO_OFF);
        Xlog.d(TAG, "isAllRadioOff=" + isAllRadioOff);
        return isAllRadioOff;
    }

    @Override
    public void onPreferenceClick(long simid) {
        Bundle extras = new Bundle();
        extras.putLong(GeminiUtils.EXTRA_SIMID, simid);
        startFragment(this, SimInfoEditor.class.getCanonicalName(), -1, extras,
                R.string.gemini_sim_info_title);
        Xlog.i(TAG, "startFragment " + SimInfoEditor.class.getCanonicalName());
    }
}
