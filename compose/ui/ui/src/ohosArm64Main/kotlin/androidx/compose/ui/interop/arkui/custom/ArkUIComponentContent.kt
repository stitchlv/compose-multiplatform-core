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

package androidx.compose.ui.interop.arkui.custom

import androidx.compose.ui.interop.arkui.ArkUINode
import androidx.compose.ui.interop.arkui.arkUINativeNodeApi_
import com.bytedance.kmp.harko.HarkoContext
import com.bytedance.kmp.harko.HarkoContext.GlobalNApiEnv
import com.bytedance.kmp.harko.ark.NApiRef
import com.bytedance.kmp.harko.ark.NApiValue
import com.bytedance.kmp.harko.ark.createRef
import com.bytedance.kmp.harko.ark.deleteRef
import com.bytedance.kmp.harko.ark.getRefValue
import kotlinx.cinterop.alloc
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.ohos.arkui.ArkUI_AttributeItem
import platform.ohos.arkui.ArkUI_NodeHandle
import platform.ohos.arkui.ArkUI_NodeHandleVar
import platform.ohos.arkui.OH_ArkUI_GetNodeHandleFromNapiValue
import platform.ohos.napi.napi_call_function
import platform.ohos.napi.napi_get_named_property
import platform.ohos.napi.napi_value
import platform.ohos.napi.napi_valueVar

class ArkUIComponentContent(componentContent: napi_value, createRef: Boolean = false): CustomArkUINode(getNodeFromComponentContent(componentContent)) {

    companion object {
        fun getNodeFromComponentContent(componentContent: napi_value): ArkUI_NodeHandle {
            val nodeHandle = nativeHeap.alloc<ArkUI_NodeHandleVar>()
            OH_ArkUI_GetNodeHandleFromNapiValue(HarkoContext.GlobalNApiEnv, componentContent, nodeHandle.ptr)
            return nodeHandle.value!!
        }
    }

    private val componentContentRef: NApiRef? = if (createRef) createRef(componentContent) else null

    override fun dispose() {
        super.dispose()
        componentContentRef?.disposeComponentContent()
    }

    override fun addChild(arkUINode: ArkUINode) {
        error("addChild is not support for ArkUIComponentContent")
    }

    override fun insertChildAt(arkUINode: ArkUINode, index: Int) {
        error("insertChildAt is not support for ArkUIComponentContent")
    }

    override fun removeChild(arkUINode: ArkUINode) {
        error("removeChild is not support for ArkUIComponentContent")
    }

    override fun setAttribute(attribute: UInt, attributeItem: ArkUI_AttributeItem) {
        error("setAttribute is not support for ArkUIComponentContent")
    }
}

private fun NApiRef.disposeComponentContent() {
    getRefValue(this)?.disposeComponentContent()
    deleteRef(this)
}

private fun NApiValue.disposeComponentContent() {
    val disposeFunc = nativeHeap.alloc<napi_valueVar>()
    napi_get_named_property(HarkoContext.GlobalNApiEnv, this, "dispose", disposeFunc.ptr)
    val result = nativeHeap.alloc<napi_valueVar>()
    napi_call_function(HarkoContext.GlobalNApiEnv, this, disposeFunc.value, 0u, null, result.ptr)
}