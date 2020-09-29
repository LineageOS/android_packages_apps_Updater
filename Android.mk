LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_USE_AAPT2 := true

LOCAL_STATIC_ANDROID_LIBRARIES := \
    com.google.android.material_material \
    androidx.core_core \
    androidx.appcompat_appcompat \
    androidx.cardview_cardview \
    androidx.preference_preference \
    androidx.recyclerview_recyclerview \

LOCAL_RESOURCE_DIR := \
    $(LOCAL_PATH)/res

LOCAL_PACKAGE_NAME := Updater
LOCAL_PRIVATE_PLATFORM_APIS := true
LOCAL_PRIVILEGED_MODULE := true
LOCAL_CERTIFICATE := platform
LOCAL_PROGUARD_FLAG_FILES := proguard.flags

LOCAL_REQUIRED_MODULES := privapp_whitelist_org.lineageos.updater.xml

include $(BUILD_PACKAGE)

include $(CLEAR_VARS)
LOCAL_MODULE := privapp_whitelist_org.lineageos.updater.xml
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_PATH := $(TARGET_OUT_ETC)/permissions
LOCAL_SRC_FILES := $(LOCAL_MODULE)
include $(BUILD_PREBUILT)


include $(CLEAR_VARS)
LOCAL_MODULE := UpdaterStudio
LOCAL_MODULE_CLASS := FAKE
LOCAL_MODULE_SUFFIX := -timestamp
updater_system_deps := $(call java-lib-deps,framework)
updater_system_libs_path := $(abspath $(LOCAL_PATH))/system_libs

include $(BUILD_SYSTEM)/base_rules.mk

$(LOCAL_BUILT_MODULE): $(updater_system_deps)
	$(hide) mkdir -p $(updater_system_libs_path)
	$(hide) rm -rf $(updater_system_libs_path)/*.jar
	$(hide) cp $(updater_system_deps) $(updater_system_libs_path)/framework.jar
	$(hide) echo "Fake: $@"
	$(hide) mkdir -p $(dir $@)
	$(hide) touch $@
