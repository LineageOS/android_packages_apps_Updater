LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_STATIC_JAVA_LIBRARIES := \
    android-support-design \
    android-support-v4 \
    android-support-v7-appcompat \
    android-support-v7-cardview \
    android-support-v7-preference \
    android-support-v7-recyclerview

LOCAL_RESOURCE_DIR := \
    $(TOP)/frameworks/support/design/res \
    $(TOP)/frameworks/support/v7/appcompat/res \
    $(TOP)/frameworks/support/v7/cardview/res \
    $(TOP)/frameworks/support/v7/preference/res \
    $(TOP)/frameworks/support/v7/recyclerview/res \
    $(LOCAL_PATH)/res

LOCAL_AAPT_FLAGS := \
    --auto-add-overlay \
    --extra-packages android.support.design \
    --extra-packages android.support.v7.appcompat \
    --extra-packages android.support.v7.cardview \
    --extra-packages android.support.v7.preference \
    --extra-packages android.support.v7.recyclerview

LOCAL_PACKAGE_NAME := Updater
LOCAL_PRIVILEGED_MODULE := true
LOCAL_PROGUARD_FLAG_FILES := proguard.flags

include $(BUILD_PACKAGE)


include $(CLEAR_VARS)
LOCAL_MODULE := UpdaterStudio
LOCAL_MODULE_CLASS := FAKE
LOCAL_MODULE_SUFFIX := -timestamp
updater_framework_dep := $(call java-lib-deps,framework)
updater_system_libs_path := $(abspath $(LOCAL_PATH))/system_libs

include $(BUILD_SYSTEM)/base_rules.mk

.PHONY: $(LOCAL_BUILT_MODULE)
$(LOCAL_BUILT_MODULE): $(updater_framework_dep)
	$(hide) echo "Fake: $@"
	$(hide) mkdir -p $(dir $@)
	$(hide) touch $@
	$(hide) mkdir -p $(updater_system_libs_path)
	$(hide) rm -r $(updater_system_libs_path)/*.jar
	$(hide) cp $(updater_framework_dep) $(updater_system_libs_path)/framework.jar
