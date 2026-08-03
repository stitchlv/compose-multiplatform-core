#include "arkui_utils_export.h"
#include <dlfcn.h>
#include <hilog/log.h>

typedef int32_t (*SetUiDvsyncSwitchFn)(ArkUI_ContextHandle, bool);

namespace {

constexpr const char* DV_SYNC_TAG = "DvSyncContext1";
constexpr const char* SET_UI_DVSYNC_SWITCH_SYMBOL = "OH_ArkUI_NodeUtils_SetUiDvsyncSwitch";

void* resolveAceLibHandle() {
    static void* handle = []() -> void* {
        void* resolved = dlopen("libace_" "n" "dk.z.so", RTLD_LAZY);
        if (resolved == nullptr) {
            OH_LOG_Print(LOG_APP, LOG_ERROR, 1000, DV_SYNC_TAG,
                         "SystemApi dlopenFailed library=libace_ndk.z.so");
        }
        return resolved;
    }();
    return handle;
}

SetUiDvsyncSwitchFn findSetUiDvsyncSwitch() {
    void* handle = resolveAceLibHandle();
    if (handle == nullptr) {
        return nullptr;
    }
    auto function = reinterpret_cast<SetUiDvsyncSwitchFn>(dlsym(handle, SET_UI_DVSYNC_SWITCH_SYMBOL));
    if (function != nullptr) {
        OH_LOG_Print(LOG_APP, LOG_INFO, 1000, DV_SYNC_TAG,
                     "SystemApi resolved function=%{public}p symbol=%{public}s",
                     reinterpret_cast<void*>(function), SET_UI_DVSYNC_SWITCH_SYMBOL);
        return function;
    }
    OH_LOG_Print(LOG_APP, LOG_ERROR, 1000, DV_SYNC_TAG,
                 "SystemApi unavailable symbol=%{public}s",
                 SET_UI_DVSYNC_SWITCH_SYMBOL);
    return nullptr;
}

} // namespace

int32_t androidx_compose_ui_arkui_utils_setUiDvsyncSwitch(ArkUI_ContextHandle context, bool enable) {
    static SetUiDvsyncSwitchFn function = NULL;
    if (function == NULL) {
        function = findSetUiDvsyncSwitch();
    }
    if (function == NULL) {
        OH_LOG_Print(LOG_APP, LOG_ERROR, 1000, DV_SYNC_TAG,
                     "SystemApi callSkipped reason=unavailable context=%{public}p enable=%{public}d",
                     context, enable);
        return -1;
    }
    int32_t result = function(context, enable);
    if (result != 0) {
        OH_LOG_Print(LOG_APP, LOG_ERROR, 1000, DV_SYNC_TAG,
                     "SystemApi callFailed context=%{public}p enable=%{public}d result=%{public}d",
                     context, enable, result);
    }
    return result;
}
