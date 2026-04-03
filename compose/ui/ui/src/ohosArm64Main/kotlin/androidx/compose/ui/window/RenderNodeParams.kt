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

package androidx.compose.ui.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.bytedance.kmp.harko.HarkoContext
import com.bytedance.kmp.harko.ark.NApiRef
import com.bytedance.kmp.harko.ark.NApiValue
import com.bytedance.kmp.harko.ark.createRef
import com.bytedance.kmp.harko.ark.createWeakRef
import com.bytedance.kmp.harko.ark.deleteRef
import com.bytedance.kmp.harko.ark.getRefValue
import kotlinx.cinterop.alloc
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.ohos.arkui.OH_ArkUI_GetNodeContentFromNapiValue
import platform.ohos.node.ArkUI_NodeContentHandle
import platform.ohos.node.ArkUI_NodeContentHandleVar

class UIViewParam(
    val id:Long,
    origin: NApiValue,
    transfer: (NApiValue) -> Any?,
    interopBottomNodeContent: NApiValue?,
    interopTopNodeContent: NApiValue?,
    textToolbar: NApiValue?
) {
    val param: Any? = transfer(origin)
    private val interopBottomNodeContentRef: NApiRef? = interopBottomNodeContent?.let {
         createRef(it)
    }
    private val interopTopNodeContentRef: NApiRef? = interopTopNodeContent?.let {
        createRef(it)
    }
    private var textToolbarRef: NApiRef? = textToolbar?.let {
        createWeakRef(it)
    }

    private var isDestroyed = false

    fun getInteropBottomNodeContentHandle(): ArkUI_NodeContentHandle? {
        interopBottomNodeContentRef ?: return null
        val nodeContentHandle = nativeHeap.alloc<ArkUI_NodeContentHandleVar>()
        OH_ArkUI_GetNodeContentFromNapiValue(HarkoContext.GlobalNApiEnv, getRefValue(interopBottomNodeContentRef)!!, nodeContentHandle.ptr)
        return nodeContentHandle.value
    }

    fun getInteropTopNodeContentHandle(): ArkUI_NodeContentHandle? {
        interopTopNodeContentRef?: return null
        val nodeContentHandle = nativeHeap.alloc<ArkUI_NodeContentHandleVar>()
        OH_ArkUI_GetNodeContentFromNapiValue(HarkoContext.GlobalNApiEnv, getRefValue(interopTopNodeContentRef)!!, nodeContentHandle.ptr)
        return nodeContentHandle.value
    }

    fun getTextToolbar(): NApiValue? {
        textToolbarRef ?: return null
        return getRefValue(textToolbarRef!!)
    }

    fun updateTextToolbar(textToolbar: NApiValue) {
        textToolbarRef?.let {
            deleteRef(it)
        }
        textToolbarRef = createWeakRef(textToolbar)
    }

    fun dispose() {
        if (isDestroyed) return
        isDestroyed = true
        interopBottomNodeContentRef?.let {
            deleteRef(it)
        }
        interopTopNodeContentRef?.let {
            deleteRef(it)
        }
        textToolbarRef?.let {
            deleteRef(it)
        }
    }
}

internal val RenderNodeParams = staticCompositionLocalOf<UIViewParam?> {
    null
}

@Composable
fun <T> currentRenderNodeParams(): T {
    @Suppress("UNCHECKED_CAST")
    return RenderNodeParams.current?.param as T
}

@Composable
fun getRenderNodeId(): String {
    return RenderNodeParams.current?.id?.toString() ?: ""
}
