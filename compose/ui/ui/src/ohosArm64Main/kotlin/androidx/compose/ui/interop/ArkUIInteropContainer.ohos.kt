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

package androidx.compose.ui.interop

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.arkui.ArkUINode
import androidx.compose.ui.interop.arkui.builtin.ArkUIStack
import androidx.compose.ui.node.InteropContainer
import androidx.compose.ui.node.TrackInteropModifierElement
import androidx.compose.ui.node.TrackInteropModifierNode
import androidx.compose.ui.node.countInteropComponentsBefore
import com.bytedance.kmp.harko.OHLogger
import platform.ohos.arkui.ArkUI_NodeContentHandle

internal val LocalArkUIInteropContainer = staticCompositionLocalOf<ArkUIInteropContainer> {
    error("ArkUIInteropContainer not provided")
}

internal class ArkUIInteropContainer: InteropContainer<ArkUINode> {

    val bottomContainerView: ArkUIStack by lazy {
        OHLogger.dTime(ARKUI_INTEROP_TAG, "new bottom container view") {
            ArkUIStack().apply {
                setBackgroundColor(Color.Transparent)
                setClip(true)
            }
        }
    }

    val topContainerView: ArkUIStack by lazy {
        OHLogger.dTime(ARKUI_INTEROP_TAG, "new top container view") {
            ArkUIStack().apply {
                setBackgroundColor(Color.Transparent)
                setClip(true)
            }
        }
    }

    private val bottomInteropViews = mutableSetOf<ArkUINode>()
    private val topInteropViews = mutableSetOf<ArkUINode>()

    override fun getInteropView(layer: InteropLayer): Set<ArkUINode> {
        return if (layer == InteropLayer.Bottom) {
            bottomInteropViews
        } else {
            topInteropViews
        }
    }

    private var hasSetNodeContentHandle = false

    override var rootModifier: TrackInteropModifierNode<ArkUINode>? = null

    fun setNodeContentHandle(bottomContentNode: ArkUI_NodeContentHandle, topContentNode: ArkUI_NodeContentHandle) {
        if (hasSetNodeContentHandle) {
            return
        }
        hasSetNodeContentHandle = true
        OHLogger.dTime(ARKUI_INTEROP_TAG, "add To bottomNodeContent") {
            bottomContainerView.addToNodeContent(bottomContentNode)
        }
        OHLogger.dTime(ARKUI_INTEROP_TAG, "add To TopNodeContent") {
            topContainerView.addToNodeContent(topContentNode)
        }
    }

    override fun addInteropView(nativeView: ArkUINode, layer: InteropLayer) {
        val index = countInteropComponentsBefore(nativeView, layer)
        if (layer == InteropLayer.Bottom) {
            bottomInteropViews.add(nativeView)
            bottomContainerView.insertChildAt(nativeView, index)
        } else {
            topInteropViews.add(nativeView)
            topContainerView.insertChildAt(nativeView, index)
        }
    }

    override fun removeInteropView(nativeView: ArkUINode, layer: InteropLayer) {
        if (layer == InteropLayer.Bottom) {
            bottomContainerView.removeChild(nativeView)
            bottomInteropViews.remove(nativeView)
        } else {
            topContainerView.removeChild(nativeView)
            topInteropViews.remove(nativeView)
        }
    }
}

internal fun Modifier.trackArkUIInterop(
    view: ArkUINode,
    layer: InteropLayer
): Modifier = this then TrackInteropModifierElement(
    nativeView = view,
    layer = layer
)