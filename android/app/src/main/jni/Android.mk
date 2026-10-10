LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := hev-socks5-tunnel
LOCAL_SRC_FILES := hev-socks5-tunnel-jni.c
LOCAL_LDLIBS := -llog
include $(BUILD_SHARED_LIBRARY)
