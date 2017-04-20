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

package com.android.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

public class SmartSwitchReceiver extends BroadcastReceiver {
    
    private static final String TRANSACTION_START = "com.android.mms.transaction.START";
    private static final String TRANSACTION_STOP = "com.android.mms.transaction.STOP";
    /**
     * MMS_TRANSACTION: 
     *         1 if mms transaction started
     *         0 if mms transaction stoped
     */
    private static final String MMS_TRANSACTION = "mms.transaction";
    private static boolean sMmsTransaction = false;
//    private static final String TAG = "SmartSwitchReceiver";
    private static final String SS_TAG = "SmartSwitchReceiver";
    

    @Override
    public void onReceive(Context context, Intent intent) {

        String action = intent.getAction(); 
        if (action.equals(Intent.ACTION_BOOT_COMPLETED) || 
           action.equals("android.intent.action.ACTION_BOOT_IPO")) {
            Log.d(SS_TAG, "Boot completed, clear database, set MMS_TRANSACTION as 0");
            Settings.System.putInt(context.getContentResolver(), MMS_TRANSACTION, 0);
        } else if (action.equals(TRANSACTION_START)) {
            Log.d(SS_TAG, "receiver: TRANSACTION_START in SmartSwitchReceiver");
            if (!sMmsTransaction) {
                Settings.System.putInt(context.getContentResolver(), MMS_TRANSACTION, 1);
                sMmsTransaction = true;
                Log.d(SS_TAG, "set MMS_TRANSACTION as 1; set flag as true");
            }
         } else if (action.equals(TRANSACTION_STOP)) {
            Log.d(SS_TAG, "receiver: TRANSACTION_STOP in SmartSwitchReceiver");
            if (sMmsTransaction) {
                Settings.System.putInt(context.getContentResolver(), MMS_TRANSACTION, 0);
                sMmsTransaction = false;
                Log.d(SS_TAG, "set MMS_TRANSACTION as 0; set flag as false");
            }
         }
    }

}
