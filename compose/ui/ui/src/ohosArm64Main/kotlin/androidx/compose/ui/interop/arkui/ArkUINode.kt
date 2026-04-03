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

package androidx.compose.ui.interop.arkui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.interop.ARKUI_INTEROP_TAG
import androidx.compose.ui.unit.Constraints
import com.bytedance.kmp.harko.OHLogger
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import platform.ohos.arkui.ArkUI_AttributeItem
import platform.ohos.arkui.ArkUI_NativeAPIVariantKind
import platform.ohos.arkui.ArkUI_NativeNodeAPI_1
import platform.ohos.arkui.ArkUI_NodeContentHandle
import platform.ohos.arkui.ArkUI_NumberValue
import platform.ohos.arkui.NODE_BACKGROUND_COLOR
import platform.ohos.arkui.NODE_CLIP
import platform.ohos.arkui.NODE_HEIGHT
import platform.ohos.arkui.NODE_HEIGHT_PERCENT
import platform.ohos.arkui.NODE_POSITION
import platform.ohos.arkui.NODE_SIZE
import platform.ohos.arkui.NODE_WIDTH
import platform.ohos.arkui.NODE_WIDTH_PERCENT
import platform.ohos.arkui.OH_ArkUI_NodeContent_AddNode
import platform.ohos.arkui.OH_ArkUI_QueryModuleInterfaceByName
import platform.ohos.node.ArkUI_NodeHandle
import platform.ohos.node.OH_ArkUI_LayoutConstraint_Create
import platform.ohos.node.OH_ArkUI_LayoutConstraint_Dispose
import platform.ohos.node.OH_ArkUI_LayoutConstraint_SetMaxHeight
import platform.ohos.node.OH_ArkUI_LayoutConstraint_SetMaxWidth
import platform.ohos.node.OH_ArkUI_LayoutConstraint_SetMinHeight
import platform.ohos.node.OH_ArkUI_LayoutConstraint_SetMinWidth
import platform.ohos.node.OH_ArkUI_LayoutConstraint_SetPercentReferenceHeight
import platform.ohos.node.OH_ArkUI_LayoutConstraint_SetPercentReferenceWidth

internal val arkUINativeNodeApi_ by lazy {
    OH_ArkUI_QueryModuleInterfaceByName(ArkUI_NativeAPIVariantKind.ARKUI_NATIVE_NODE, "ArkUI_NativeNodeAPI_1")!!
        .reinterpret<ArkUI_NativeNodeAPI_1>()
}

abstract class ArkUINode {
    // https://developer.huawei.com/consumer/cn/doc/harmonyos-references-V13/_ark_u_i___native_module-V13
    abstract val nodeType: UInt
    open val nodeHandle: ArkUI_NodeHandle by lazy {
        arkUINativeNodeApi_.pointed.createNode!!.invoke(nodeType)!!
    }

    open fun dispose() {
        arkUINativeNodeApi_.pointed.disposeNode!!.invoke(nodeHandle)
    }

    fun addToNodeContent(nodeContentHandle: ArkUI_NodeContentHandle) {
        OH_ArkUI_NodeContent_AddNode(nodeContentHandle, nodeHandle)
    }

    open fun addChild(arkUINode: ArkUINode) {
        arkUINativeNodeApi_.pointed.addChild!!.invoke(nodeHandle, arkUINode.nodeHandle)
    }

    open fun insertChildAt(arkUINode: ArkUINode, index: Int) {
        arkUINativeNodeApi_.pointed.insertChildAt!!.invoke(nodeHandle, arkUINode.nodeHandle, index)
    }

    open fun removeChild(arkUINode: ArkUINode) {
        arkUINativeNodeApi_.pointed.removeChild!!.invoke(nodeHandle, arkUINode.nodeHandle)
    }

    fun measure(constraints: Constraints) {
        OH_ArkUI_LayoutConstraint_Create().apply {
            OH_ArkUI_LayoutConstraint_SetMinWidth(this, constraints.minWidth)
            OH_ArkUI_LayoutConstraint_SetMaxWidth(this, constraints.maxWidth)
            OH_ArkUI_LayoutConstraint_SetMinHeight(this, constraints.minHeight)
            OH_ArkUI_LayoutConstraint_SetMaxHeight(this, constraints.maxHeight)
            OH_ArkUI_LayoutConstraint_SetPercentReferenceWidth(this, constraints.maxWidth)
            OH_ArkUI_LayoutConstraint_SetPercentReferenceHeight(this, constraints.maxHeight)
            arkUINativeNodeApi_.pointed.measureNode!!.invoke(nodeHandle, this)
            OH_ArkUI_LayoutConstraint_Dispose(this)
        }
    }

    fun getMeasuredSize(): Pair<Int, Int> {
        val size = arkUINativeNodeApi_.pointed.getMeasuredSize!!.invoke(nodeHandle)
        return size.useContents {
            width to height
        }
    }

    fun setWidth(width: Float) {
        setAttribute(NODE_WIDTH, arrayOf(width).createAttributeItem())
    }

    fun setHeight(height: Float) {
        setAttribute(NODE_HEIGHT, arrayOf(height).createAttributeItem())
    }

    fun setWidthPercent(widthPercent: Float) {
        setAttribute(NODE_WIDTH_PERCENT, arrayOf(widthPercent).createAttributeItem())
    }

    fun setHeightPercent(heightPercent: Float) {
        setAttribute(NODE_HEIGHT_PERCENT, arrayOf(heightPercent).createAttributeItem())
    }

    fun setSize(width: Float, height: Float) {
        setAttribute(NODE_SIZE, arrayOf(width, height).createAttributeItem())
    }

    fun setPosition(start: Float, top: Float) {
        setAttribute(NODE_POSITION, arrayOf(start, top).createAttributeItem())
    }

    fun setBackgroundColor(color: Color) {
        setBackgroundColor(color.toArgb().toUInt())
    }

    fun setBackgroundColor(color: UInt) {
        setAttribute(NODE_BACKGROUND_COLOR, arrayOf(color).createAttributeItem())
    }

    fun setClip(clip: Boolean) {
        val value = if (clip) 1 else 0
        setAttribute(NODE_CLIP, arrayOf(value).createAttributeItem())
    }

    fun getBackgroundColor(): Color? {
        return getAttribute(NODE_BACKGROUND_COLOR)?.pointed?.value?.get(0)?.u32?.let {
            OHLogger.d(ARKUI_INTEROP_TAG, "background color is $it")
            Color(it.toInt())
        }
    }

    protected open fun setAttribute(attribute: UInt, attributeItem: ArkUI_AttributeItem) {
        arkUINativeNodeApi_.pointed.setAttribute!!.invoke(nodeHandle, attribute, attributeItem.ptr)
    }

    protected open fun getAttribute(attribute: UInt): CPointer<ArkUI_AttributeItem>? {
        return arkUINativeNodeApi_.pointed.getAttribute!!.invoke(nodeHandle, attribute)
    }

    protected fun Array<Float>.createAttributeItem(): ArkUI_AttributeItem {
        // 创建 ArkUI_NumberValue 数组
        val numberArray = nativeHeap.allocArray<ArkUI_NumberValue>(size)
        this.forEachIndexed { index, value ->
            numberArray.get(index).f32 = value
        }

        // 创建 ArkUI_AttributeItem
        val attributeItem = nativeHeap.alloc<ArkUI_AttributeItem>()
        attributeItem.size = size
        attributeItem.value = numberArray

        return attributeItem
    }

    protected fun String.createAttributeItem(memScope: MemScope): ArkUI_AttributeItem {
        val attributeItem = nativeHeap.alloc<ArkUI_AttributeItem>()
        attributeItem.string = cstr.getPointer(memScope)
        return attributeItem
    }

    protected fun Array<UInt>.createAttributeItem(): ArkUI_AttributeItem {
        // 创建 ArkUI_NumberValue 数组
        val numberArray = nativeHeap.allocArray<ArkUI_NumberValue>(size)
        this.forEachIndexed { index, value ->
            numberArray.get(index).u32 = value
        }

        // 创建 ArkUI_AttributeItem
        val attributeItem = nativeHeap.alloc<ArkUI_AttributeItem>()
        attributeItem.size = size
        attributeItem.value = numberArray

        return attributeItem
    }

    protected fun Array<Int>.createAttributeItem(): ArkUI_AttributeItem {
        // 创建 ArkUI_NumberValue 数组
        val numberArray = nativeHeap.allocArray<ArkUI_NumberValue>(size)
        this.forEachIndexed { index, value ->
            numberArray.get(index).i32 = value
        }

        // 创建 ArkUI_AttributeItem
        val attributeItem = nativeHeap.alloc<ArkUI_AttributeItem>()
        attributeItem.size = size
        attributeItem.value = numberArray

        return attributeItem
    }
}