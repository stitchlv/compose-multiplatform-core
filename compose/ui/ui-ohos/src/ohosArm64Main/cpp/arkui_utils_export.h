#ifndef ARKUI_UTILS_EXPORT_H
#define ARKUI_UTILS_EXPORT_H

#include <stdbool.h>
#include <stdint.h>
#include <arkui/native_node.h>
#include <arkui/native_type.h>

#ifdef __cplusplus
extern "C" {
#endif

int32_t androidx_compose_ui_arkui_utils_setUiDvsyncSwitch(ArkUI_ContextHandle context, bool enable);

#ifdef __cplusplus
}
#endif

#endif
