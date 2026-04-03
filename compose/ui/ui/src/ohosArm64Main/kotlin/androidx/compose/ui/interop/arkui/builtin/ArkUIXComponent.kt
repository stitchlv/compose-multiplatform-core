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
import kotlinx.cinterop.CPointer
import platform.ohos.arkui.ARKUI_NODE_XCOMPONENT
import platform.ohos.node.OH_NativeXComponent_GetNativeXComponent
import cnames.structs.OH_NativeXComponent

class ArkUIXComponent: ArkUINode() {
    override val nodeType = ARKUI_NODE_XCOMPONENT

    fun getNativeXComponent(): CPointer<OH_NativeXComponent> {
        return OH_NativeXComponent_GetNativeXComponent(nodeHandle)!!
    }
}