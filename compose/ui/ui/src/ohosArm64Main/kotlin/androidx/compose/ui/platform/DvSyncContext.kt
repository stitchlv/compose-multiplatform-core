/*
 * Copyright (c) 2026 ByteDance Ltd. and/or its affiliates
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.ui.platform

import com.bytedance.kmp.harko.HarkoContext
import com.bytedance.kmp.harko.OHLogger
import androidx.compose.ui.arkui.utils.androidx_compose_ui_arkui_utils_setUiDvsyncSwitch
import com.bytedance.kmp.harko.ark.NApiValue
import kotlinx.cinterop.alloc
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.ohos.arkui.ArkUI_NodeHandle
import platform.ohos.arkui.ArkUI_NodeHandleVar
import platform.ohos.arkui.OH_ArkUI_GetNodeHandleFromNapiValue

private const val TAG = "DvSyncContext1"
private const val BYTEKMP_DVSYNC_BUILD_MARKER = "ByteKMP-DvSync-WeakImport-Final-20260709-01"

@OptIn(ExperimentalForeignApi::class)
internal class DvSyncContext {

    companion object {
        private var hasLoggedBuildMarker = false
    }

    private var rootArkUINodeHandle: ArkUI_NodeHandle? = null

    private var rootFrameNodeId: UInt? = null
        private set

    private var uiDvsyncSwitchRequestCount = 0

    fun setRootFrameNode(rootFrameNode: NApiValue?, uniqueId: UInt?) {
        if (!hasLoggedBuildMarker) {
            hasLoggedBuildMarker = true
            OHLogger.i(TAG, "BuildMarker marker=$BYTEKMP_DVSYNC_BUILD_MARKER")
        }
        rootFrameNodeId = uniqueId
        if (rootFrameNode == null) {
            rootArkUINodeHandle = null
            OHLogger.w(TAG, "RootNodeBridge skipped reason=noRootFrameNode uniqueId=$uniqueId")
            return
        }
        val result = memScoped {
            val nodeHandleVar = alloc<ArkUI_NodeHandleVar>()
            val result = OH_ArkUI_GetNodeHandleFromNapiValue(
                HarkoContext.GlobalNApiEnv,
                rootFrameNode,
                nodeHandleVar.ptr,
            )
            rootArkUINodeHandle = if (result == 0) nodeHandleVar.value else null
            result
        }
        val handle = rootArkUINodeHandle
        if (handle == null) {
            OHLogger.w(TAG, "RootNodeBridge resolveFailed result=$result uniqueId=$uniqueId")
        } else {
            OHLogger.i(TAG, "RootNodeBridge resolved uniqueId=$uniqueId")
        }
    }

    fun setUiDvsyncSwitchForFling(enable: Boolean) {
        val handle = rootArkUINodeHandle ?: run {
            OHLogger.w(TAG, "FusionContext skipped reason=noRootArkUINodeHandle enable=$enable uniqueId=$rootFrameNodeId requestCount=$uiDvsyncSwitchRequestCount")
            return
        }
        val uniqueId = rootFrameNodeId
        if (enable) {
            if (uiDvsyncSwitchRequestCount == 0) {
                val beforeCount = uiDvsyncSwitchRequestCount
                val result = androidx_compose_ui_arkui_utils_setUiDvsyncSwitch(handle, true)
                if (result == 0) {
                    uiDvsyncSwitchRequestCount = 1
                    OHLogger.i(TAG, "FlingSwitch open result=0 before=$beforeCount after=$uiDvsyncSwitchRequestCount uniqueId=$uniqueId")
                } else {
                    OHLogger.w(TAG, "FlingSwitch openFailed result=$result before=$beforeCount after=$uiDvsyncSwitchRequestCount uniqueId=$uniqueId")
                }
            } else {
                val beforeCount = uiDvsyncSwitchRequestCount
                uiDvsyncSwitchRequestCount++
                OHLogger.i(TAG, "FlingSwitch openRef before=$beforeCount after=$uiDvsyncSwitchRequestCount uniqueId=$uniqueId")
            }
        } else {
            if (uiDvsyncSwitchRequestCount <= 0) {
                OHLogger.w(TAG, "FlingSwitch skipped reason=noActiveRequest enable=false uniqueId=$uniqueId requestCount=$uiDvsyncSwitchRequestCount handle=$handle")
                return
            }
            val beforeCount = uiDvsyncSwitchRequestCount
            uiDvsyncSwitchRequestCount--
            if (uiDvsyncSwitchRequestCount == 0) {
                val result = androidx_compose_ui_arkui_utils_setUiDvsyncSwitch(handle, false)
                if (result == 0) {
                    OHLogger.i(TAG, "FlingSwitch close result=0 before=$beforeCount after=$uiDvsyncSwitchRequestCount uniqueId=$uniqueId")
                } else {
                    OHLogger.w(TAG, "FlingSwitch closeFailed result=$result before=$beforeCount after=$uiDvsyncSwitchRequestCount uniqueId=$uniqueId")
                }
            } else {
                OHLogger.i(TAG, "FlingSwitch closeRef before=$beforeCount after=$uiDvsyncSwitchRequestCount uniqueId=$uniqueId")
            }
        }
    }

    fun onSurfaceDestroyed() {
        val handle = rootArkUINodeHandle
        val uniqueId = rootFrameNodeId
        if (handle != null && uiDvsyncSwitchRequestCount > 0) {
            val beforeCount = uiDvsyncSwitchRequestCount
            OHLogger.i(TAG, "SurfaceDestroyed cleanup uniqueId=$uniqueId requestCount=$beforeCount handle=$handle")
            val result = androidx_compose_ui_arkui_utils_setUiDvsyncSwitch(handle, false)
            OHLogger.i(TAG, "SurfaceDestroyed cleanupResult result=$result uniqueId=$uniqueId before=$beforeCount")
        }
        uiDvsyncSwitchRequestCount = 0
        rootArkUINodeHandle = null
        rootFrameNodeId = null
        OHLogger.i(TAG, "SurfaceDestroyed reset")
    }
}
