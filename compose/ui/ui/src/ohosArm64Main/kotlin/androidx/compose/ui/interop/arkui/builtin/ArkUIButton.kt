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

package androidx.compose.ui.interop.arkui.builtin

import androidx.compose.ui.interop.arkui.ArkUINode
import androidx.compose.ui.interop.arkui.arkUINativeNodeApi_
import cnames.structs.ArkUI_NodeEvent
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.staticCFunction
import platform.ohos.arkui.ARKUI_NODE_BUTTON
import platform.ohos.arkui.NODE_BUTTON_LABEL
import platform.ohos.arkui.NODE_ON_CLICK
import platform.ohos.arkui.OH_ArkUI_NodeEvent_GetEventType
import platform.ohos.arkui.OH_ArkUI_NodeEvent_GetUserData

class ArkUIButton: ArkUINode() {
    override val nodeType = ARKUI_NODE_BUTTON
    private var hasRegisterEvent = false
    private val onClickListeners = mutableListOf<() -> Unit>()

    fun setText(text: String) {
        memScoped {
            setAttribute(NODE_BUTTON_LABEL, text.createAttributeItem(this))
        }
    }

    internal fun onClick() {
        onClickListeners.forEach {
            it.invoke()
        }
    }

    private fun registerEvent() {
        if (hasRegisterEvent) {
            return
        }
        // TODO 解注册
        arkUINativeNodeApi_.pointed.addNodeEventReceiver!!.invoke(nodeHandle,
            staticCFunction<CPointer<ArkUI_NodeEvent>?, Unit> {
                event(it)
            })
        arkUINativeNodeApi_.pointed.registerNodeEvent!!.invoke(nodeHandle,
            NODE_ON_CLICK, 0, StableRef.create(this).asCPointer())
        hasRegisterEvent = true
    }

    fun addClickListener(listener: () -> Unit) {
        registerEvent()
        onClickListeners.add(listener)
    }
}

private fun event(event: CPointer<cnames.structs.ArkUI_NodeEvent>?) {
    val userData = OH_ArkUI_NodeEvent_GetUserData(event)
    val buttonRef = userData?.asStableRef<ArkUIButton>() ?: return
    val button = buttonRef.get()
    buttonRef.dispose()

    val eventType = OH_ArkUI_NodeEvent_GetEventType(event)
    when(eventType) {
        NODE_ON_CLICK -> {
            button.onClick()
        }
    }
}