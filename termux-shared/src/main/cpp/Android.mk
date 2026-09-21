LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)
LOCAL_CFLAGS := -O2 -g0 -fvisibility=hidden -fvisibility-inlines-hidden -ffunction-sections -fdata-sections
LOCAL_CPPFLAGS := -O2 -g0 -fvisibility=hidden -fvisibility-inlines-hidden -ffunction-sections -fdata-sections
LOCAL_LDLIBS := -llog
LOCAL_LDFLAGS := -Wl,--gc-sections -Wl,--strip-debug
LOCAL_MODULE := local-socket
LOCAL_SRC_FILES := local-socket.cpp
include $(BUILD_SHARED_LIBRARY)
