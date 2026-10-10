# Builds libhev-socks5-tunnel.so from the upstream submodule. Upstream's JNI
# layer (src/hev-jni.c) registers natives on hev.htproxy.TProxyService.
# Fetch sources with: git submodule update --init --recursive
LOCAL_PATH := $(call my-dir)
include $(LOCAL_PATH)/hev-socks5-tunnel/Android.mk
