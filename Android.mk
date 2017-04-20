LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_JAVA_LIBRARIES := bouncycastle \
                        mediatek-framework

LOCAL_STATIC_JAVA_LIBRARIES := guava android-support-v4 \
				  com.android.settings.ext \
				  CellConnUtil

LOCAL_EMMA_COVERAGE_FILTER := @$(LOCAL_PATH)/emma_filter.txt,--$(LOCAL_PATH)/emma_filter_method.txt

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src)

ifneq ($(MTK_BT_PROFILE_MANAGER), yes)
LOCAL_SRC_FILES := $(filter-out src/com/android/settings/bluetoothangel%, $(LOCAL_SRC_FILES))
else 
LOCAL_SRC_FILES := $(filter-out src/com/android/settings/bluetoothZ%, $(LOCAL_SRC_FILES))
endif


ifneq ($(PLATFORM_VERSION_V4_1_2), yes)
$(info * support 4.1.2 is no)
LOCAL_SRC_FILES := $(filter-out src/com/mediatek/lbs/LocationSettings.java \
                    src/com/mediatek/lbs/AgpsEpoSettings.java \
                    src/com/mediatek/lbs/CustomSwitchPreference.java, $(LOCAL_SRC_FILES))
else 
$(info * support 4.1.2 is yes)
LOCAL_SRC_FILES := $(filter-out src/com/android/settings/LocationSettings.java \
                    src/com/android/settings/GoogleLocationSettingHelper.java, $(LOCAL_SRC_FILES))
endif


LOCAL_PACKAGE_NAME := Settings
LOCAL_CERTIFICATE := platform

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

include $(BUILD_PACKAGE)

# Use the folloing include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))
