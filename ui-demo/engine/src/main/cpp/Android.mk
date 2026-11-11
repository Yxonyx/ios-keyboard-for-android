LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
include $(LOCAL_PATH)/upstream/NativeFileList.mk
LOCAL_MODULE := keyboard_latinime
LOCAL_SRC_FILES := bridge.cpp $(addprefix upstream/src/, $(LATIN_IME_CORE_SRC_FILES))
LOCAL_C_INCLUDES := $(LOCAL_PATH)/upstream $(LOCAL_PATH)/upstream/src
LOCAL_CPPFLAGS := -std=c++17 -fexceptions -frtti -Wno-register
LOCAL_LDLIBS := -llog
LOCAL_LDFLAGS := -Wl,-z,max-page-size=16384
include $(BUILD_SHARED_LIBRARY)
