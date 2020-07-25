LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

$(info "LOCAL_PATH = ${LOCAL_PATH}")
$(warning "LOCAL_PATH = ${LOCAL_PATH}") #只是输出警告信息然后继续编译
#$(error "LOCAL_PATH = ${LOCAL_PATH}") # 输出信息并且停止编译

LIBVLC_LIBS := ${LOCAL_PATH}/libs/armeabi-v7a/

LOCAL_MODULE    := native

LOCAL_SRC_FILES := main.c

LOCAL_LDLIBS := -L${LIBVLC_LIBS} -llog -lvlcjni

LOCAL_C_INCLUDES := $(LOCAL_PATH)/include

include $(BUILD_SHARED_LIBRARY)
