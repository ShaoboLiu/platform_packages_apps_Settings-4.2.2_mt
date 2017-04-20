/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2010. All rights reserved.
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

package com.mediatek.schpwronoff;

import android.app.TimePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TimePicker;
import android.widget.Toast;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.mediatek.xlog.Xlog;

/**
 * Manages each alarm
 */
public class SetAlarm extends SettingsPreferenceFragment 
    implements TimePickerDialog.OnTimeSetListener {
    
    private static final String TAG = "SetAlarm";
    private Preference mTimePref;
    private RepeatPreference mRepeatPref;
    private MenuItem mTestAlarmItem;

    private int mId;
    private boolean mEnabled;
    private int mHour;
    private int mMinutes;
    private static final int MENU_REVET = Menu.FIRST;
    private static final int MENU_SAVE = Menu.FIRST + 1;
    private String mPrevTitle;

    /**
     * Set an alarm. Requires an Alarms.ALARM_ID to be passed in as an extra. 
     * FIXME: Pass an Alarm object like every other Activity.
     * @param savedInstanceState Bundle
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        addPreferencesFromResource(R.xml.schpwr_alarm_prefs);

        PreferenceScreen view = getPreferenceScreen();
        mTimePref = view.findPreference("time");
        mRepeatPref = (RepeatPreference) view.findPreference("setRepeat");

        final Bundle arguments = getArguments();
        if (arguments != null) {
            mId = arguments.getInt(Alarms.ALARM_ID);
        }
        mPrevTitle = getActivity().getTitle().toString();
        if (mId == 1) {
            getActivity().setTitle(R.string.schedule_power_on_set);
        } else {
            getActivity().setTitle(R.string.schedule_power_off_set);
        }
        Xlog.d(TAG, "In SetAlarm, alarm id = " + mId);

        // load alarm details from database
        Alarm alarm = Alarms.getAlarm(getContentResolver(), mId);
        if (alarm != null) {
            mEnabled = alarm.mEnabled;
            mHour = alarm.mHour;
            mMinutes = alarm.mMinutes;
            if (mRepeatPref != null) {
                mRepeatPref.setDaysOfWeek(alarm.mDaysOfWeek);
            }
        }
        updateTime();
        setHasOptionsMenu(true);
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onDetach() {
        if (mPrevTitle != null) {
            getActivity().setTitle(mPrevTitle);
        }
        super.onDetach();
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mTimePref) {
            new TimePickerDialog(getActivity(), this, mHour, mMinutes, DateFormat.is24HourFormat(getActivity())).show();
        }

        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.add(Menu.NONE, MENU_REVET, 0, R.string.revert).setEnabled(true)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.add(Menu.NONE, MENU_SAVE, 0, R.string.done).setEnabled(true).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_REVET:
            finishFragment();
            return true;
        case MENU_SAVE:
            saveAlarm();
            finishFragment();
            return true;
        default:
            break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
        mHour = hourOfDay;
        mMinutes = minute;
        updateTime();
        // If the time has been changed, enable the alarm.
        mEnabled = true;
    }

    private void updateTime() {
        Xlog.d(TAG, "updateTime " + mId);
        mTimePref.setSummary(Alarms.formatTime(getActivity(), mHour, mMinutes, mRepeatPref.getDaysOfWeek()));
    }

    private void saveAlarm() {
        final String alert = Alarms.ALARM_ALERT_SILENT;
        mEnabled |= mRepeatPref.mIsPressedPositive;
        Alarms.setAlarm(getActivity(), mId, mEnabled, mHour, mMinutes, mRepeatPref.getDaysOfWeek(), true, "", alert);

        if (mEnabled) {
            popAlarmSetToast(getActivity(), mHour, mMinutes, mRepeatPref.getDaysOfWeek(), mId);
        }
    }

    /**
     * Write alarm out to persistent store and pops toast if alarm enabled
     */
    private static void saveAlarm(Context context, int id, boolean enabled, int hour, int minute,
            Alarm.DaysOfWeek daysOfWeek, boolean vibrate, String label, String alert, boolean popToast) {
        Xlog.d(TAG, "** saveAlarm " + id + " " + label + " " + enabled + " " + hour + " " + minute + " vibe " + vibrate);

        // Fix alert string first
        Alarms.setAlarm(context, id, enabled, hour, minute, daysOfWeek, vibrate, label, alert);

        if (enabled && popToast) {
            popAlarmSetToast(context, hour, minute, daysOfWeek, 1);
        }
    }

    /**
     * Display a toast that tells the user how long until the alarm goes off. This helps prevent "am/pm" mistakes.
     */
    static void popAlarmSetToast(Context context, int hour, int minute, Alarm.DaysOfWeek daysOfWeek, int mId) {
        String toastText = formatToast(context, hour, minute, daysOfWeek, mId);
        Toast.makeText(context, toastText, Toast.LENGTH_LONG).show();
    }

    /**
     * format "Alarm set for 2 days 7 hours and 53 minutes from now"
     */
    static String formatToast(Context context, int hour, int minute, Alarm.DaysOfWeek daysOfWeek, int id) {
        long alarm = Alarms.calculateAlarm(hour, minute, daysOfWeek).getTimeInMillis();
        long delta = alarm - System.currentTimeMillis();
        
        final int millisUnit = 1000;
        final int timeUnit = 60;
        final int dayOfHoursUnit = 24;
        
        long hours = delta / (millisUnit * timeUnit * timeUnit);
        long minutes = delta / (millisUnit * timeUnit) % timeUnit;
        long days = hours / dayOfHoursUnit;
        hours = hours % dayOfHoursUnit;

        String daySeq = (days == 0) ? "" : (days == 1) ? context.getString(R.string.day) : context.getString(R.string.days,
                Long.toString(days));

        String minSeq = (minutes == 0) ? "" : (minutes == 1) ? context.getString(R.string.minute) : context.getString(
                R.string.minutes, Long.toString(minutes));

        String hourSeq = (hours == 0) ? "" : (hours == 1) ? context.getString(R.string.hour) : context.getString(
                R.string.hours, Long.toString(hours));

        boolean dispDays = days > 0;
        boolean dispHour = hours > 0;
        boolean dispMinute = minutes > 0;

        final int dispMinutesOffset = 4;
        final int pwrOnOFFStringOffset = 8;
        
        int index = (dispDays ? 1 : 0) | (dispHour ? 2 : 0) | (dispMinute ? dispMinutesOffset : 0);

        String[] formats = context.getResources().getStringArray(R.array.alarm_set);
        if (id == 2) {
            index += pwrOnOFFStringOffset;
        }
        return String.format(formats[index], daySeq, hourSeq, minSeq);
    }

    /**
     * Test code: this is disabled for production build. Sets this alarm to go off on the next minute
     */
    void setTestAlarm() {
        final int timeUnit = 60;
        // start with now
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(System.currentTimeMillis());

        int nowHour = c.get(java.util.Calendar.HOUR_OF_DAY);
        int nowMinute = c.get(java.util.Calendar.MINUTE);

        int minutes = (nowMinute + 1) % timeUnit;
        int hour = nowHour + (nowMinute == 0 ? 1 : 0);

        saveAlarm(getActivity(), mId, true, hour, minutes, mRepeatPref.getDaysOfWeek(), true, "", Alarms.ALARM_ALERT_SILENT,
                true);
    }

}
