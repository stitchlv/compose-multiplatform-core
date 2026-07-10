#include "arkui_utils_export.h"
#include <hilog/log.h>

typedef int32_t (*SetUiDvsyncSwitchFn)(ArkUI_NodeHandle, bool);

extern "C" __attribute__((weak))
int32_t OH_ArkUI_NodeUtils_SetUiDvsyncSwitch(ArkUI_NodeHandle node, bool enable);

namespace {

constexpr const char* DV_SYNC_TAG = "DvSyncContext1";
constexpr const char* SET_UI_DVSYNC_SWITCH_SYMBOL = "OH_ArkUI_NodeUtils_SetUiDvsyncSwitch";

SetUiDvsyncSwitchFn findSetUiDvsyncSwitch() {
    auto function = reinterpret_cast<SetUiDvsyncSwitchFn>(OH_ArkUI_NodeUtils_SetUiDvsyncSwitch);
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

int32_t androidx_compose_ui_arkui_utils_setUiDvsyncSwitch(ArkUI_NodeHandle node, bool enable) {
    static SetUiDvsyncSwitchFn function = NULL;
    if (function == NULL) {
        function = findSetUiDvsyncSwitch();
    }
    if (function == NULL) {
        OH_LOG_Print(LOG_APP, LOG_ERROR, 1000, DV_SYNC_TAG,
                     "SystemApi callSkipped reason=unavailable node=%{public}p enable=%{public}d",
                     node, enable);
        return -1;
    }
    int32_t result = function(node, enable);
    if (result != 0) {
        OH_LOG_Print(LOG_APP, LOG_ERROR, 1000, DV_SYNC_TAG,
                     "SystemApi callFailed node=%{public}p enable=%{public}d result=%{public}d",
                     node, enable, result);
    }
    return result;
}
