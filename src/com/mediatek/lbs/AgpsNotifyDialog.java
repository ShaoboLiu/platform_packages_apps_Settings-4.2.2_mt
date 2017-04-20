/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

package com.mediatek.lbs;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import com.android.settings.R;
import com.mediatek.common.agps.MtkAgpsConfig;
import com.mediatek.common.agps.MtkAgpsManager;
import com.mediatek.xlog.Xlog;

import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

public class AgpsNotifyDialog extends Activity {

    private static final String XLOGTAG = "Settings/Agps";
    private static final String PREFERENCE_FILE = "com.android.settings_preferences";

    private static final String DISABLE_KEY = "disable_agps_on_reboot";
    private static final String AGPS_ENABLE_KEY = "location_agps_enable";
    private static final String EM_ENABLE_KEY = "EM_Indication";
    private static final String UNKNOWN_VALUE = "UNKNOWN_VALUE";

    private static final int SEC_TO_MILLSEC = 1000;
    private Handler mHandler;

    private String mMessage;
    private String mRequestId;
    private String mCliecntName;

    protected static MtkAgpsManager sAgpsMgr;

    private static final int IND_EM_DIALOG_ID = 0;
    private static final int NOTIFY_ALLOW_NO_ANSWER_DIALOG_ID = 1;
    private static final int NOTIFY_ALLOW_NO_DENY_DIALOG_ID = 2;
    private static final int IND_ERROR_DIALOG_ID = 3;
    private static final int NOTIFY_ONLY_DIALOG_ID = 4;

    private Timer mTimer = new Timer();
    private boolean mIsUserResponse = false;
    private boolean mGetOtherNotify = false;
    private Dialog mDialog = null;
    private String mTitle = null;

    /**
     * send Notification
     * 
     * @param context
     *            Context
     * @param icon
     *            int
     * @param ticker
     *            String
     * @param title
     *            String
     * @param content
     *            String
     * @param id
     *            int
     */
    public void sendNotification(Context context, int icon, String ticker, String title, String content, int id) {

        Intent intent = new Intent("");
        PendingIntent appIntent = PendingIntent.getBroadcast(context, 0, intent, 0);

        Notification notification = new Notification();
        notification.icon = icon;
        notification.tickerText = ticker;
        notification.defaults = 0;
        notification.flags = Notification.FLAG_AUTO_CANCEL;
        notification.setLatestEventInfo(context, title, content, appIntent);

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(id, notification);
    }

    /**
     * finish activity
     */
    public void finishActivity() {
        mTimer.cancel();
        final int randomNumber = 10000;
        if (!mIsUserResponse) {
            sendNotification(this, R.drawable.ic_btn_next, mTitle, mTitle, mMessage, new Random().nextInt(randomNumber));
        }
        if (mDialog != null) {
            mDialog.dismiss();
        }

        if (!mGetOtherNotify) {
            finish();
        } else {
            mGetOtherNotify = false;
        }
    }

    // type=0: notify only
    private void setTimerIfNeed(int type) {
        mTimer = new Timer();
        TimerTask task = new TimerTask() {
            public void run() {
                finishActivity();
            }
        };

        MtkAgpsConfig config = sAgpsMgr.getConfig();

        int notifyTimeout = config.notifyTimeout;
        int verifyTimeout = config.verifyTimeout;
        boolean timerEnabled = (config.niTimer == 1) ? true : false;

        log("notifyTimeout=" + notifyTimeout + " verifyTimeout=" + verifyTimeout + " timerEnabled=" + timerEnabled);

        if (timerEnabled) {
            int timeout = 0;
            if (type == 0) {
                timeout = notifyTimeout * SEC_TO_MILLSEC;
            } else {
                timeout = verifyTimeout * SEC_TO_MILLSEC;
            }
            mTimer.schedule(task, timeout);
        }

    }

    private void setup(Intent intent) {  //MTK_CS_IGNORE_THIS_LINE

        if (sAgpsMgr == null) {
            sAgpsMgr = (MtkAgpsManager) getSystemService(Context.MTK_AGPS_SERVICE);
        }
        if (mHandler == null) {
            mHandler = new Handler();
        }

        Bundle bundle = intent.getExtras();
        int mtype = -1;
        int mid = -1;

        if (bundle != null) {
            mtype = bundle.getInt("msg_type", MtkAgpsManager.AGPS_IND_ERROR);
            mid = bundle.getInt("msg_id", MtkAgpsManager.AGPS_CAUSE_NONE);
            mMessage = this.getString(getStringID(mtype, mid));
            mRequestId = bundle.getString("request_id");
            mCliecntName = bundle.getString("client_name");
        } else {
            log("Error: Bundle is null");
        }

        boolean requestIdAvail = mRequestId != null && !(mRequestId.equals(UNKNOWN_VALUE));
        boolean clientNameAvail = mCliecntName != null && !(mCliecntName.equals(UNKNOWN_VALUE));

        if (requestIdAvail && clientNameAvail) {
            mMessage = mMessage + "\n" + getString(R.string.NI_Request_ID) + ": " + mRequestId + "\n"
                    + getString(R.string.NI_Request_ClientName) + ": " + mCliecntName + "\n";
        } else if (requestIdAvail) {
            mMessage = mMessage + "\n" + getString(R.string.NI_Request_ID) + ": " + mRequestId;
        } else if (clientNameAvail) {
            mMessage = mMessage + "\n" + getString(R.string.NI_Request_ClientName) + ": " + mCliecntName;
        }

        switch (mtype) {
        case MtkAgpsManager.AGPS_IND_EM:
            showDialog(IND_EM_DIALOG_ID);
            break;
        case MtkAgpsManager.AGPS_IND_NOTIFY:
            if (mid == MtkAgpsManager.AGPS_NOTIFY_ALLOW_NO_ANSWER) {
                mTitle = getString(R.string.agps_str_verify);
                setTimerIfNeed(1);
                showDialog(NOTIFY_ALLOW_NO_ANSWER_DIALOG_ID);
            } else if (mid == MtkAgpsManager.AGPS_NOTIFY_DENY_NO_ANSWER) {
                mTitle = getString(R.string.agps_str_verify);
                setTimerIfNeed(1);
                showDialog(NOTIFY_ALLOW_NO_DENY_DIALOG_ID);
            } else if (mid == MtkAgpsManager.AGPS_NOTIFY_ONLY) {
                mTitle = getString(R.string.agps_str_notify);
                setTimerIfNeed(0);
                showDialog(NOTIFY_ONLY_DIALOG_ID);
            }
            break;
        case MtkAgpsManager.AGPS_IND_ERROR:
            showDialog(IND_ERROR_DIALOG_ID);
            break;
        default:
            log("Unrecongnized type is " + mtype);
        }

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mIsUserResponse = false;
        setup(getIntent());
    }

    @Override
    public void onNewIntent(Intent intent) {
        log("onNewIntent is called ");
        mGetOtherNotify = true;
        finishActivity();
        setup(intent);
    }

    /**
     * onCreateDialog
     * 
     * @param id
     *            int
     * @return Dialog
     */
    public Dialog onCreateDialog(int id) {
        switch (id) {
        case IND_EM_DIALOG_ID:
            mDialog = new AlertDialog.Builder(AgpsNotifyDialog.this).setTitle(R.string.EM_NotifyDialog_title)
                    .setMessage(mMessage).setOnCancelListener(new DialogInterface.OnCancelListener() {
                        public void onCancel(DialogInterface dialog) {
                            log("EM_INDICATION click back key");
                            finishActivity();
                        }
                    }).setPositiveButton(R.string.agps_OK, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialoginterface, int i) {
                            log("EM_INDICATION ok");
                            finishActivity();
                        }
                    }).create();
            break;
        case NOTIFY_ALLOW_NO_ANSWER_DIALOG_ID:
            mDialog = new AlertDialog.Builder(AgpsNotifyDialog.this).setTitle(R.string.agps_str_verify).setMessage(mMessage)
                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        public void onCancel(DialogInterface dialog) {
                            log("NOTIFY_ALLOW_NO_ANSWER_DIALOG_ID click back key");
                            sAgpsMgr.niUserResponse(2);
                            mIsUserResponse = true;
                            finishActivity();
                        }
                    }).setPositiveButton(R.string.agps_str_allow, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialoginterface, int i) {
                            log("NOTIFY_ALLOW_NO_ANSWER_DIALOG_ID click allow");
                            sAgpsMgr.niUserResponse(1);
                            mIsUserResponse = true;
                            finishActivity();
                        }
                    }).setNegativeButton(R.string.agps_str_deny, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialoginterface, int i) {
                            log("NOTIFY_ALLOW_NO_ANSWER_DIALOG_ID click deny");
                            sAgpsMgr.niUserResponse(2);
                            mIsUserResponse = true;
                            finishActivity();
                        }
                    }).create();
            break;
        case NOTIFY_ALLOW_NO_DENY_DIALOG_ID:
            mDialog = new AlertDialog.Builder(AgpsNotifyDialog.this).setTitle(R.string.agps_str_verify).setMessage(mMessage)
                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        public void onCancel(DialogInterface dialog) {
                            log("NOTIFY_ALLOW_NO_DENY_DIALOG_ID click back key");
                            sAgpsMgr.niUserResponse(2);
                            mIsUserResponse = true;
                            finishActivity();
                        }
                    }).setPositiveButton(R.string.agps_str_allow, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialoginterface, int i) {
                            log("NOTIFY_ALLOW_NO_DENY_DIALOG_ID click allow");
                            sAgpsMgr.niUserResponse(1);
                            mIsUserResponse = true;
                            finishActivity();
                        }
                    }).setNegativeButton(R.string.agps_str_deny, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialoginterface, int i) {
                            log("NOTIFY_ALLOW_NO_DENY_DIALOG_ID click deny");
                            sAgpsMgr.niUserResponse(2);
                            mIsUserResponse = true;
                            finishActivity();
                        }
                    }).create();
            break;
        case IND_ERROR_DIALOG_ID:
            mDialog = new AlertDialog.Builder(AgpsNotifyDialog.this).setTitle(R.string.agps_str_error).setMessage(mMessage)
                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        public void onCancel(DialogInterface dialog) {
                            log("IND_ERROR_DIALOG_ID click back key");
                            finishActivity();
                        }
                    }).setPositiveButton(R.string.agps_OK, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialoginterface, int i) {
                            log("IND_ERROR_DIALOG_ID click ok");
                            finishActivity();
                        }
                    }).create();
            break;
        case NOTIFY_ONLY_DIALOG_ID:
            mDialog = new AlertDialog.Builder(AgpsNotifyDialog.this).setTitle(R.string.agps_str_notify).setMessage(mMessage)
                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        public void onCancel(DialogInterface dialog) {
                            log("NOTIFY_ONLY_DIALOG_ID click back key");
                            mIsUserResponse = true;
                            finishActivity();
                        }
                    }).setPositiveButton(R.string.agps_OK, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialoginterface, int i) {
                            log("NOTIFY_ONLY_DIALOG_ID click ok");
                            mIsUserResponse = true;
                            finishActivity();
                        }
                    }).create();
            break;
        default:
            log("WARNING: No such dialog");
        }
        return mDialog;
    }

    /**
     * get string id
     * 
     * @param type
     *            int
     * @param id
     *            int
     * @return int
     */
    public int getStringID(int type, int id) {
        int result = R.string.AGPS_DEFAULT_STRING;
        switch (type) {
        case MtkAgpsManager.AGPS_IND_INFO:
            result = getStringFromIndex(id, MtkAgpsManager.AGPS_INFO_CNT, INFO_STRING_LIST);
            break;
        case MtkAgpsManager.AGPS_IND_NOTIFY:
            result = getStringFromIndex(id, MtkAgpsManager.AGPS_NOTIFY_CNT, NOTIFY_STRING_LIST);
            break;
        case MtkAgpsManager.AGPS_IND_ERROR:
            result = getStringFromIndex(id, MtkAgpsManager.AGPS_CAUSE_CNT, ERROR_STRING_LIST);
            break;
        case MtkAgpsManager.AGPS_IND_EM:
            result = getStringFromIndex(id, MtkAgpsManager.AGPS_EM_CNT, EM_STRING_LIST);
            break;
        default:
            break;
        }
        return result;
    }

    private int getStringFromIndex(int id, int max, int[] list) {
        for (int index = 1; index < max; index++) {
            if (index == id) {
                return list[index];
            }
        }
        return R.string.AGPS_DEFAULT_STRING;
    }

    private static final int ERROR_STRING_LIST[] = { R.string.AGPS_DEFAULT_STRING, R.string.AGPS_CAUSE_NETWORK_CREATE_FAIL,
            R.string.AGPS_CAUSE_BAD_PUSH_CONTENT, R.string.AGPS_CAUSE_NOT_SUPPORTED, R.string.AGPS_CAUSE_REQ_NOT_ACCEPTED,
            R.string.AGPS_CAUSE_NO_RESOURCE, R.string.AGPS_CAUSE_NETWORK_DISCONN, R.string.AGPS_CAUSE_REMOTE_ABORT,
            R.string.AGPS_CAUSE_TIMER_EXPIRY, R.string.AGPS_CAUSE_REMOTE_MSG_ERROR, R.string.AGPS_CAUSE_USER_AGREE,
            R.string.AGPS_CAUSE_USER_DENY, R.string.AGPS_CAUSE_NO_POSITION, R.string.AGPS_CAUSE_TLS_AUTH_FAIL,
            R.string.USER_RESPONSE_TIMEOUT, R.string.AGPS_MODEM_RESET_HAPPEN };

    private static final int EM_STRING_LIST[] = { R.string.AGPS_DEFAULT_STRING, R.string.AGPS_EM_RECV_SI_REQ,
            R.string.AGPS_EM_POS_FIXED };

    private static final int NOTIFY_STRING_LIST[] = { R.string.AGPS_DEFAULT_STRING, R.string.AGPS_NOTIFY_ONLY,
            R.string.AGPS_NOTIFY_ALLOW_NO_ANSWER, R.string.AGPS_NOTIFY_DENY_NO_ANSWER, R.string.AGPS_NOTIFY_PRIVACY };

    private static final int INFO_STRING_LIST[] = { R.string.AGPS_DEFAULT_STRING };

    private void log(String info) {
        Xlog.d(XLOGTAG, "[AgpsNotify] " + info + " ");
    }

}
