# Builds libhev-socks5-tunnel.so from the upstream submodule, with
# src/main/jni/patches applied to a copy in build/ (Gradle's prepareHev task
# passes its path as HEV_SRC). Upstream's JNI layer (src/hev-jni.c) registers
# natives on hev.htproxy.TProxyService.
# Fetch sources with: git submodule update --init --recursive
LOCAL_PATH := $(call my-dir)
include $(HEV_SRC)/Android.mk
