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

package com.android.settings.deviceinfo;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.storage.IMountService;
import android.os.storage.StorageEventListener;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;
import com.mediatek.common.featureoption.FeatureOption;

import java.util.ArrayList;
import java.util.List;

public class Memory extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {
    private static final String TAG = "MemorySettings";

    private static final int DLG_CONFIRM_UNMOUNT = 1;
    private static final int DLG_ERROR_UNMOUNT = 2;
    private static final int DLG_CONFIRM_MOUNT = 3;

    public static final int H_UNMOUNT_ERROR = 11;

    private static final String DEFAULT_WRITE_CATEGORY_KEY = "memory_select";

    private Resources mResources;

    // The mountToggle Preference that has last been clicked.
    // Assumes no two successive unmount event on 2 different volumes are
    // performed before the first
    // one's preference is disabled
    private Preference mLastClickedMountToggle;
    private String mClickedMountPoint;

    // whether the current unmounting device is USB.
    private boolean mIsUnmountingUsb = false;

    // Access using getMountService()
    private IMountService mMountService = null;

    private StorageManager mStorageManager = null;

    private Handler mHandler = null;

    private StorageVolumePreferenceCategory mInternalStorageVolumePreferenceCategory;
    private StorageVolumePreferenceCategory[] mStorageVolumePreferenceCategories;

    private RadioButtonPreference[] mStorageWritePathGroup;
    private String mDefaultWritePath;
    private PreferenceCategory mDefaultWriteCategory;
    private RadioButtonPreference mDeafultWritePathPref;
    private boolean[] mDefaultWritePathAdded;

    private static final String USB_STORAGE_PATH = "/mnt/usbotg";

    private static final String KEY_APK_INSTALLER = "apk_installer";

    private static final String KEY_APP_INSTALL_LOCATION = "app_install_location";

    // App installation location. Default is ask the user.
    private static final int APP_INSTALL_AUTO = 0;
    private static final int APP_INSTALL_DEVICE = 1;
    private static final int APP_INSTALL_SDCARD = 2;

    private static final String APP_INSTALL_DEVICE_ID = "device";
    private static final String APP_INSTALL_SDCARD_ID = "sdcard";
    private static final String APP_INSTALL_AUTO_ID = "auto";
    // dynamic swap sd card
    private static final String ACTION_DYNAMIC_SD_SWAP = "com.mediatek.SD_SWAP";
    private static final String SD_EXIST = "SD_EXIST";
    private static final int SD_INDEX = 1;

    private ListPreference mInstallLocation;

    private ContentResolver mContentResolver;
    private StorageVolumePreferenceCategory mVolumePrefCategory;
    private static final String[] MTP_PROJECTION = { MediaStore.MTP_TRANSFER_FILE_PATH };
    private String mVolumeDescription;
    private boolean mIsInternalSD;

    private Handler mUiHandler;
    private static final int MESSAGE_DELAY_TIME = 200;

    BroadcastReceiver mDynSwapReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Receive dynamic sd swap broadcast");

            StorageVolume[] newVolumes = mStorageManager.getVolumeList();
            for (StorageVolume volume : newVolumes) {
                // update the storageVolumePreferenceCategory group
                for (int i = 0; i < mStorageVolumePreferenceCategories.length; i++) {
                    StorageVolumePreferenceCategory svpc = mStorageVolumePreferenceCategories[i];
                    if (volume.getPath().equals(
                            svpc.getStorageVolume().getPath())) {
                        svpc.setStorageVolume(volume);
                        svpc.updateStorageVolumePrefCategory();
                    }
                }

                // update the default write disk group
                for (RadioButtonPreference pref : mStorageWritePathGroup) {
                    if (volume.getPath().equals(pref.getPath())) {
                        pref.setTitle(volume.getDescription(getActivity()));
                    }
                }
            }
            // update the preferred install location
            boolean isExternalSD = intent.getBooleanExtra(SD_EXIST, false);
            mInstallLocation.setEnabled(isExternalSD);

            // reset the install location entries
            if (isExternalSD) {
                // get the SD description
                String sdDescription = "";
                for (int i = 0; i < newVolumes.length; i++) {
                    if (newVolumes[i].getPath()
                            .equals(Environment.getExternalStorageDirectory()
                                    .getPath())) {
                        sdDescription = newVolumes[i]
                                .getDescription(getActivity());
                        break;
                    }
                }
                CharSequence[] entries = mInstallLocation.getEntries();
                entries[SD_INDEX] = sdDescription;
                mInstallLocation.setEntries(entries);
            }
        }
    };

    private boolean mIsApkInstallerExist = false;
    private Preference mApkInstallePref;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        if (mStorageManager == null) {
            mStorageManager = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
            mStorageManager.registerListener(mStorageListener);
        }
        addPreferencesFromResource(R.xml.device_info_memory);

        mUiHandler = new Handler();

        mContentResolver = getActivity().getContentResolver();

        mDefaultWriteCategory = (PreferenceCategory) findPreference(DEFAULT_WRITE_CATEGORY_KEY);
        mResources = getResources();

        if (!Environment.isSomeStorageEmulated()) {
            // External storage is separate from internal storage; need to
            // show internal storage as a separate item.
            mInternalStorageVolumePreferenceCategory = new StorageVolumePreferenceCategory(
                    getActivity(), mResources, null, mStorageManager, false,
                    false);
            getPreferenceScreen().addPreference(
                    mInternalStorageVolumePreferenceCategory);
            mInternalStorageVolumePreferenceCategory.init();
        }

        // get the provided path list
        String[] mPathList = mStorageManager.getVolumePaths();
        StorageVolume[] volumes = mStorageManager.getVolumeList();
        String whereStr = "";
        for (int i = 0; i < volumes.length; i++) {
            if (volumes[i].getPath().equals(
                    Environment.getExternalStorageDirectory().getPath())) {
                whereStr = volumes[i].getDescription(getActivity());
                break;
            }
        }

        // take off the absence sd card path,and mVolumePathList is the
        // available path list
        List<String> mVolumePathList = new ArrayList<String>();
        List<StorageVolume> storageVolumes = new ArrayList<StorageVolume>();

        int len = mPathList.length;
        for (int i = 0; i < len; i++) {
            if (!mStorageManager.getVolumeState(mPathList[i]).equals(
                    "not_present")) {
                mVolumePathList.add(mPathList[i]);
                storageVolumes.add(volumes[i]);
            }
        }

        int length = storageVolumes.size();
        Log.d(TAG, "length = " + length);

        mStorageWritePathGroup = new RadioButtonPreference[length];
        mDefaultWritePathAdded = new boolean[length];
        mStorageVolumePreferenceCategories = new StorageVolumePreferenceCategory[length];
        for (int i = 0; i < length; i++) {
            StorageVolume storageVolume = storageVolumes.get(i);
            boolean isPrimary = i == 0;
            mStorageWritePathGroup[i] = new RadioButtonPreference(getActivity());
            mStorageWritePathGroup[i].setKey(mVolumePathList.get(i));
            mStorageWritePathGroup[i].setTitle(storageVolume
                    .getDescription(getActivity()));
            mStorageWritePathGroup[i].setPath(mVolumePathList.get(i));
            mStorageWritePathGroup[i].setOnPreferenceChangeListener(this);

            boolean isUsbStorage = mVolumePathList.get(i).equals(
                    USB_STORAGE_PATH);

            mStorageVolumePreferenceCategories[i] = new StorageVolumePreferenceCategory(
                    getActivity(), mResources, storageVolume, mStorageManager,
                    isPrimary, isUsbStorage);
            getPreferenceScreen().addPreference(
                    mStorageVolumePreferenceCategories[i]);
            mStorageVolumePreferenceCategories[i].init();
        }

        setHasOptionsMenu(true);

        mApkInstallePref = findPreference(KEY_APK_INSTALLER);
        mIsApkInstallerExist = isPkgInstalled("com.mediatek.apkinstaller");
        if (mIsApkInstallerExist) {
            Intent intent = new Intent();
            intent.setClassName("com.mediatek.apkinstaller",
                    "com.mediatek.apkinstaller.APKInstaller");
            intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            mApkInstallePref.setIntent(intent);
        } else {
            getPreferenceScreen().removePreference(mApkInstallePref);
        }

        // not ready for prime time yet
        if (false) {
            getPreferenceScreen().removePreference(mInstallLocation);
        }

        mInstallLocation = (ListPreference) findPreference(KEY_APP_INSTALL_LOCATION);
        CharSequence[] entries = mInstallLocation.getEntries();
        entries[SD_INDEX] = whereStr;
        mInstallLocation.setEntries(entries);
        // Is app default install location set?
        boolean userSetInstLocation = (Settings.System.getInt(
                getContentResolver(), Settings.Secure.SET_INSTALL_LOCATION, 0) != 0);
        if (!userSetInstLocation) {
            getPreferenceScreen().removePreference(mInstallLocation);
        } else {
            mInstallLocation.setValue(getAppInstallLocation());
            mInstallLocation
                    .setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                        public boolean onPreferenceChange(
                                Preference preference, Object newValue) {
                            String value = (String) newValue;
                            handleUpdateAppInstallLocation(value);
                            return false;
                        }
                    });
        }

        mHandler = (Handler) new Handler() {
            public void handleMessage(Message msg) {
                switch (msg.what) {
                case H_UNMOUNT_ERROR:
                    showDialogInner(DLG_ERROR_UNMOUNT);
                    break;
                default:
                    break;
                }
            }
        };

        // register a broadcast receiver for dynamic sd swap
        if (FeatureOption.MTK_2SDCARD_SWAP) {
            IntentFilter mFilter = new IntentFilter();
            mFilter.addAction(ACTION_DYNAMIC_SD_SWAP);
            getActivity().registerReceiver(mDynSwapReceiver, mFilter);
        }
    }

    private boolean isMassStorageEnabled() {
        // mass storage is enabled if primary volume supports it
        final StorageVolume[] storageVolumes = mStorageManager.getVolumeList();
        return (storageVolumes.length > 0 && storageVolumes[0]
                .allowMassStorage());
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        IntentFilter intentFilter = new IntentFilter(
                Intent.ACTION_MEDIA_SCANNER_STARTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        intentFilter.addDataScheme("file");
        getActivity().registerReceiver(mMediaScannerReceiver, intentFilter);

        dynamicUpdateUnmountDlg();

        // update the preferred_install_location preference
        Log.d(TAG, "dynamicShowInstallLocation");
        dynamicShowInstallLocation();

        // update the APK Installer preference
        if (mIsApkInstallerExist) {
            dynamicShowAPKInstaller();
        }

        dynamicShowDefaultWriteCategory();

        if (mInternalStorageVolumePreferenceCategory != null) {
            mInternalStorageVolumePreferenceCategory.onResume();
        }
        for (int i = 0; i < mStorageVolumePreferenceCategories.length; i++) {
            mStorageVolumePreferenceCategories[i].onResume();
        }

    }

    // query the MTP status and update UI
    private String getMtpPath() {
        Cursor cur = null;
        String path = null;

        cur = mContentResolver.query(MediaStore.getMtpTransferFileUri(),
                MTP_PROJECTION, null, null, null);
        if (cur != null) {
            if (cur.getCount() == 0 || cur.getCount() > 1) {
                Log.w(TAG, "no record or more than one record");
                path = "ERROR";
            } else {
                cur.moveToFirst();
                path = cur.getString(0);
            }
        }
        if (cur != null) {
            cur.close();
        }

        // if MTP transfer is over or there is no transfer, path = null;
        return path;
    }

    private boolean getMtpStatus() {
        String mtpPath = getMtpPath();
        Log.d(TAG, "Mtp transfer path" + mtpPath);

        // MTP is transferring
        if (mtpPath != null && !(mtpPath.equals("ERROR"))) {

            if (mClickedMountPoint == null) {
                Log.d(TAG, "mClickedMountPoint is null");
                return false;
            }

            if (mtpPath.contains(mClickedMountPoint + "/")) {
                return true;
            }
        }

        return false;
    }

    private void dynamicUpdateUnmountDlg() {
        for (int i = 0; i < mStorageWritePathGroup.length; i++) {
            Log.d(TAG,
                    mStorageWritePathGroup[i].getPath()
                            + mStorageManager
                                    .getVolumeState(mStorageWritePathGroup[i]
                                            .getPath()));
            if (mStorageManager.getVolumeState(
                    mStorageWritePathGroup[i].getPath()).equals(
                    Environment.MEDIA_SHARED)) {
                Log.d(TAG, "current status is UMS");
                removeDialog(DLG_CONFIRM_UNMOUNT);
                return;
            }
        }
        Log.d(TAG, "current status is MTP");
    }

    /**
     * dynamic update install location preference
     */
    public void dynamicShowInstallLocation() {

        for (int i = 0; i < mStorageWritePathGroup.length; i++) {
            Log.d(TAG,
                    mStorageWritePathGroup[i].getPath()
                            + mStorageManager
                                    .getVolumeState(mStorageWritePathGroup[i]
                                            .getPath()));
            if (mStorageManager.getVolumeState(
                    mStorageWritePathGroup[i].getPath()).equals(
                    Environment.MEDIA_SHARED)) {
                Log.d(TAG, "current status is UMS");
                mInstallLocation.setEnabled(false);
                return;
            }
        }
        Log.d(TAG, "current status is MTP");
        mInstallLocation.setEnabled(true);
        // when open the 2SDCARD SWAP feature and the external sd card is not
        // mounted ,
        // change the install location selection
        if (FeatureOption.MTK_2SDCARD_SWAP) {
            if (!isExSdcardInserted()) {
                Log.d(TAG,
                        "2SDCARD_SWAP feature , the external sd card is not mounted");
                mInstallLocation.setEnabled(false);
            }
        }
    }

    // MTK_2SDCARD_SWAP
    private static boolean isExSdcardInserted() {
        IBinder service = ServiceManager.getService("mount");
        Log.d(TAG, "Util:service is " + service);
        if (service != null) {
            IMountService mountService = IMountService.Stub
                    .asInterface(service);
            Log.d(TAG, "Util:mountService is " + mountService);
            if (mountService == null) {
                return false;
            }
            try {
                return mountService.isSDExist();
            } catch (RemoteException e) {
                Log.d(TAG, "Util:RemoteException when isSDExist: " + e);
                return false;
            }
        } else {
            return false;
        }
    }

    // MTK_2SDCARD_SWAP

    /**
     * dynamic update apk installer preference
     */
    public void dynamicShowAPKInstaller() {
        boolean flag = true;
        for (int i = 0; i < mStorageWritePathGroup.length; i++) {
            flag = flag
                    && !mStorageManager.getVolumeState(
                            mStorageWritePathGroup[i].getPath()).equals(
                            Environment.MEDIA_MOUNTED);
        }

        mApkInstallePref.setEnabled(!flag);
    }

    StorageEventListener mStorageListener = new StorageEventListener() {
        @Override
        public void onStorageStateChanged(String path, String oldState,
                String newState) {
            Log.i(TAG, "onStorageStateChanged");

            mUiHandler.removeCallbacks(mUpdateRunnable);
            mUiHandler.postDelayed(mUpdateRunnable, MESSAGE_DELAY_TIME);

            Log.i(TAG, "Received storage state changed notification that "
                    + path + " changed state from " + oldState + " to "
                    + newState);

            for (int i = 0; i < mStorageVolumePreferenceCategories.length; i++) {
                StorageVolumePreferenceCategory svpc = mStorageVolumePreferenceCategories[i];
                if (path.equals(svpc.getStorageVolume().getPath())) {
                    svpc.onStorageStateChanged();
                    break;
                }
            }

            // update the preferred_install_location preference
            Log.d(TAG, "dynamicShowInstallLocation");
            dynamicShowInstallLocation();

            if (mIsApkInstallerExist) {
                dynamicShowAPKInstaller();
            }

            // dynamicUpdateUnmountDlg();
        }
    };

    @Override
    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(mMediaScannerReceiver);

        if (mInternalStorageVolumePreferenceCategory != null) {
            mInternalStorageVolumePreferenceCategory.onPause();
        }
        for (int i = 0; i < mStorageVolumePreferenceCategories.length; i++) {
            mStorageVolumePreferenceCategories[i].onPause();
        }
    }

    @Override
    public void onDestroy() {
        mUiHandler.removeCallbacks(mUpdateRunnable);
        if (mStorageManager != null && mStorageListener != null) {
            mStorageManager.unregisterListener(mStorageListener);
        }

        if (FeatureOption.MTK_2SDCARD_SWAP) {
            getActivity().unregisterReceiver(mDynSwapReceiver);
        }
        super.onDestroy();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.storage, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        final MenuItem usb = menu.findItem(R.id.storage_usb);
        usb.setVisible(!isMassStorageEnabled());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.storage_usb:
            if (getActivity() instanceof PreferenceActivity) {
                ((PreferenceActivity) getActivity()).startPreferencePanel(
                        UsbSettings.class.getCanonicalName(), null,
                        R.string.storage_title_usb, null, this, 0);
            } else {
                startFragment(this, UsbSettings.class.getCanonicalName(), -1,
                        null);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private synchronized IMountService getMountService() {
        if (mMountService == null) {
            IBinder service = ServiceManager.getService("mount");
            if (service != null) {
                mMountService = IMountService.Stub.asInterface(service);
            } else {
                Log.e(TAG, "Can't get mount service");
            }
        }
        return mMountService;
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {
        for (int i = 0; i < mStorageVolumePreferenceCategories.length; i++) {
            StorageVolumePreferenceCategory svpc = mStorageVolumePreferenceCategories[i];
            final StorageVolume storageVolume = svpc.getStorageVolume();
            mClickedMountPoint = storageVolume.getPath();
            mVolumeDescription = storageVolume.getDescription(getActivity());
            mIsInternalSD = !storageVolume.isRemovable();

            // click the format preference
            if (svpc.formatPrefClicked(preference)) {
                Log.d(TAG, "click format preference");
                if (getMtpStatus()) {
                    Toast.makeText(
                            getActivity(),
                            getVolumeString(R.string.mtp_transfer_erase_error_text),
                            Toast.LENGTH_SHORT).show();
                    return true;
                }
            }

            // click the preference except mount/unmount/format, start other
            // activity
            Intent intent = svpc.intentForClick(preference);

            try {
                if (intent != null) {
                    // Don't go across app boundary if monkey is running
                    if (!Utils.isMonkeyRunning()) {
                        startActivity(intent);
                    }
                    return true;
                }
            } catch (ActivityNotFoundException e) {
                Toast.makeText(getActivity(), R.string.launch_error,
                        Toast.LENGTH_SHORT).show();
            }

            // click the mount/unmount preference
            if (svpc.mountToggleClicked(preference)) {
                mLastClickedMountToggle = preference;
                mIsUnmountingUsb = mClickedMountPoint.equals(USB_STORAGE_PATH);
                String state = mStorageManager.getVolumeState(storageVolume
                        .getPath());
                if (Environment.MEDIA_MOUNTED.equals(state)
                        || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
                    unmount();
                } else {
                    mount();
                }
                return true;
            }
        }

        return false;
    }

    private final BroadcastReceiver mMediaScannerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // mInternalStorageVolumePreferenceCategory is not affected by the
            // media scanner
            for (int i = 0; i < mStorageVolumePreferenceCategories.length; i++) {
                mStorageVolumePreferenceCategories[i].onMediaScannerFinished();
            }
        }
    };

    @Override
    public Dialog onCreateDialog(int id) {
        switch (id) {
        case DLG_CONFIRM_UNMOUNT:
            return new AlertDialog.Builder(getActivity())
                    .setTitle(
                            mIsUnmountingUsb ? R.string.dlg_confirm_unmount_usb_title
                                    : R.string.dlg_confirm_unmount_title)
                    .setPositiveButton(R.string.dlg_ok,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    doUnmount();
                                }
                            })
                    .setNegativeButton(R.string.cancel, null)
                    .setMessage(
                            mIsUnmountingUsb ? R.string.dlg_confirm_unmount_usb_text
                                    : R.string.dlg_confirm_unmount_text)
                    .create();
        case DLG_ERROR_UNMOUNT:
            return new AlertDialog.Builder(getActivity())
                    .setTitle(
                            mIsUnmountingUsb ? R.string.dlg_error_unmount_usb_title
                                    : R.string.dlg_error_unmount_title)
                    .setNeutralButton(R.string.dlg_ok, null)
                    .setMessage(
                            mIsUnmountingUsb ? R.string.dlg_error_unmount_usb_text
                                    : R.string.dlg_error_unmount_text).create();
        case DLG_CONFIRM_MOUNT:
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.dlg_mount_external_sd_title)
                    .setPositiveButton(R.string.dlg_ok,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    doMount();
                                }
                            }).setNegativeButton(R.string.cancel, null)
                    .setMessage(R.string.dlg_mount_external_sd_summary)
                    .create();
        }
        return null;
    }

    private void doUnmount() {
        // Present a toast here
        if (getMtpStatus()) {
            Log.d(TAG, "MTP is transferring");
            Toast.makeText(getActivity(),
                    getVolumeString(R.string.mtp_transfer_unmount_error_text),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (mIsUnmountingUsb) {
            Toast.makeText(getActivity(), R.string.unmount_usb_inform_text,
                    Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getActivity(), R.string.unmount_inform_text,
                    Toast.LENGTH_SHORT).show();
        }
        final IMountService mountService = getMountService();

        mLastClickedMountToggle.setEnabled(false);
        mLastClickedMountToggle.setTitle(mResources
                .getString(R.string.sd_ejecting_title));
        mLastClickedMountToggle.setSummary(mResources
                .getString(R.string.sd_ejecting_summary));

        new Thread() {
            @Override
            public void run() {
                Log.d(TAG, "unmountVolume" + mClickedMountPoint);
                try {
                    mountService.unmountVolume(mClickedMountPoint, true, false);
                } catch (RemoteException e) {
                    // Informative dialog to user that unmount failed.
                    mHandler.sendEmptyMessage(H_UNMOUNT_ERROR);
                }
            }
        }.start();

    }

    private void showDialogInner(int id) {
        removeDialog(id);
        showDialog(id);
    }

    private boolean hasAppsAccessingStorage() throws RemoteException {
        IMountService mountService = getMountService();
        int stUsers[] = mountService.getStorageUsers(mClickedMountPoint);
        if (stUsers != null && stUsers.length > 0) {
            return true;
        }
        // TODO FIXME Parameterize with mountPoint and uncomment.
        // On HC-MR2, no apps can be installed on sd and the emulated internal
        // storage is not
        // removable: application cannot interfere with unmount
        /*
         * ActivityManager am =
         * (ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
         * List<ApplicationInfo> list = am.getRunningExternalApplications(); if
         * (list != null && list.size() > 0) { return true; }
         */
        // Better safe than sorry. Assume the storage is used to ask for
        // confirmation.
        return true;
    }

    private void unmount() {
        // Check if external media is in use.
        try {
            if (hasAppsAccessingStorage()) {
                // Present dialog to user
                showDialogInner(DLG_CONFIRM_UNMOUNT);
            } else {
                doUnmount();
            }
        } catch (RemoteException e) {
            // Very unlikely. But present an error dialog anyway
            Log.e(TAG, "Is MountService running?");
            showDialogInner(DLG_ERROR_UNMOUNT);
        }
    }

    private void mount() {
        // in sd swap, mount the external sd card ,pop up a dialog to prompt
        // user
        // mount the external sd card will cause dynamic sd swap
        if (FeatureOption.MTK_2SDCARD_SWAP
                && !mClickedMountPoint.equals(USB_STORAGE_PATH)) {
            showDialogInner(DLG_CONFIRM_MOUNT);
        } else {
            doMount();
        }
    }

    private void doMount() {
        final IMountService mountService = getMountService();
        new Thread() {
            @Override
            public void run() {
                try {
                    if (mountService != null) {
                        Log.d(TAG, "mountVolume" + mClickedMountPoint);
                        mountService.mountVolume(mClickedMountPoint);
                    } else {
                        Log.e(TAG, "Mount service is null, can't mount");
                    }
                } catch (RemoteException e) {
                    // Not much can be done
                    Log.e(TAG, "Happen remote exception when do mount");
                }
            }

        }.start();

    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {

        if (preference != null && preference instanceof RadioButtonPreference) {
            if (mDeafultWritePathPref != null) {
                mDeafultWritePathPref.setChecked(false);
            }
            StorageManager.setDefaultPath(preference.getKey());
            mDeafultWritePathPref = (RadioButtonPreference) preference;
            return true;
        }
        return false;
    }

    private final Runnable mUpdateRunnable = new Runnable() {
        public void run() {
            dynamicShowDefaultWriteCategory();
        }
    };

    private void dynamicShowDefaultWriteCategory() {
        for (int i = 0; i < mStorageWritePathGroup.length; i++) {

            Log.d(TAG,
                    mStorageWritePathGroup[i].getPath()
                            + mStorageManager
                                    .getVolumeState(mStorageWritePathGroup[i]
                                            .getPath()));

            if (mStorageManager.getVolumeState(
                    mStorageWritePathGroup[i].getPath()).equals(
                    Environment.MEDIA_MOUNTED)) {
                if (!mDefaultWritePathAdded[i]) {
                    mDefaultWriteCategory
                            .addPreference(mStorageWritePathGroup[i]);
                    mDefaultWritePathAdded[i] = true;
                }
            } else {
                if (mDefaultWritePathAdded[i]) {
                    mStorageWritePathGroup[i].setChecked(false);
                    mDefaultWriteCategory
                            .removePreference(mStorageWritePathGroup[i]);
                    mDefaultWritePathAdded[i] = false;
                }
            }
        }

        mDefaultWritePath = StorageManager.getDefaultPath();
        Log.d(TAG, "get default path" + mDefaultWritePath);
        for (int i = 0; i < mStorageWritePathGroup.length; i++) {
            if (mStorageWritePathGroup[i].getPath().equals(mDefaultWritePath)) {
                mStorageWritePathGroup[i].setChecked(true);
                mDeafultWritePathPref = mStorageWritePathGroup[i];
            } else {
                mStorageWritePathGroup[i].setChecked(false);
            }
        }
    }

    // judge packageName apk is installed or not
    private boolean isPkgInstalled(String packageName) {
        if (packageName != null) {
            PackageManager pm = getPackageManager();
            try {
                pm.getPackageInfo(packageName, 0);
            } catch (NameNotFoundException e) {
                e.printStackTrace();
                return false;
            }
        } else {
            Log.i(TAG, "the package name cannot be null!!");
            // disable the preference ? and change summary?
            return false;
        }
        return true;
    }

    protected void handleUpdateAppInstallLocation(final String value) {
        if (APP_INSTALL_DEVICE_ID.equals(value)) {
            Settings.System.putInt(getContentResolver(),
                    Settings.Secure.DEFAULT_INSTALL_LOCATION,
                    APP_INSTALL_DEVICE);
        } else if (APP_INSTALL_SDCARD_ID.equals(value)) {
            Settings.System.putInt(getContentResolver(),
                    Settings.Secure.DEFAULT_INSTALL_LOCATION,
                    APP_INSTALL_SDCARD);
        } else if (APP_INSTALL_AUTO_ID.equals(value)) {
            Settings.System.putInt(getContentResolver(),
                    Settings.Secure.DEFAULT_INSTALL_LOCATION, APP_INSTALL_AUTO);
        } else {
            // Should not happen, default to prompt...
            Settings.System.putInt(getContentResolver(),
                    Settings.Secure.DEFAULT_INSTALL_LOCATION, APP_INSTALL_AUTO);
        }
        mInstallLocation.setValue(value);
    }

    private String getAppInstallLocation() {
        int selectedLocation = Settings.System.getInt(getContentResolver(),
                Settings.Secure.DEFAULT_INSTALL_LOCATION, APP_INSTALL_AUTO);
        if (selectedLocation == APP_INSTALL_DEVICE) {
            return APP_INSTALL_DEVICE_ID;
        } else if (selectedLocation == APP_INSTALL_SDCARD) {
            return APP_INSTALL_SDCARD_ID;
        } else if (selectedLocation == APP_INSTALL_AUTO) {
            return APP_INSTALL_AUTO_ID;
        } else {
            // Default value, should not happen.
            return APP_INSTALL_AUTO_ID;
        }
    }

    private String getVolumeString(int stringId) {
        if (mVolumeDescription == null || !mIsInternalSD) {
            return mResources.getString(stringId);
        }

        String sdCardString;
        if (mResources.getString(stringId).getBytes().length
                - mResources.getString(stringId).length() > 0) {
            sdCardString = "" + mResources.getString(R.string.sdcard_setting);
            Log.d(TAG, sdCardString);
        } else {
            sdCardString = mResources.getString(R.string.sdcard_setting);
        }

        String str = mResources.getString(stringId).replace(sdCardString,
                mVolumeDescription);
        if (str != null && str.equals(sdCardString)) {
            sdCardString = sdCardString.substring(0, 1).toLowerCase()
                    + sdCardString.substring(1);
            Log.d(TAG, "Upper + Lower sdcard string" + sdCardString);
            return mResources.getString(stringId).replace(sdCardString,
                    mVolumeDescription);
        } else {
            return str;
        }
    }
}