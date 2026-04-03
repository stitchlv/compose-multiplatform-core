/*
 * Copyright 2025 Bytedance Ltd. and/or its affiliates
 *
 * Copyright (c) 2026 ByteDance Ltd. and/or its affiliates
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.ui.input.nestedscroll

import androidx.compose.ui.interop.ArkUIViewHitTestMode
import com.bytedance.kmp.harko.ark.NApiValue

fun ArkUINestedScrollConnection(mode: NestedScrollMode, param: InteropNestedScrollParam) =
    ArkUINestedScrollConnection(param.direction, mode, param)

abstract class InteropNestedScrollParam(
    val direction: ScrollDirection,
    private val scrollable: Scrollable,
    val needGestureForward: Boolean
) : NestedScrollCallback, ScrollableWithCallback {

    private var scrollCallback: NestedScrollCallback? = null

    val hitTestMode: ArkUIViewHitTestMode =
        if (needGestureForward) ArkUIViewHitTestMode.BLOCK else ArkUIViewHitTestMode.TRANSPARENT
    abstract val componentContentValue: NApiValue

    override fun onReachStart() {
        scrollCallback?.onReachStart()
    }

    override fun onReachEnd() {
        scrollCallback?.onReachEnd()
    }

    override fun onDidScroll(offset: Int) {
        scrollCallback?.onDidScroll(offset)
    }

    override fun attachCallback(callback: NestedScrollCallback) {
        scrollCallback = callback
    }

    override fun detachCallback() {
        scrollCallback = null
    }

    override fun scrollBy(x: Float, y: Float) {
        scrollable.scrollBy(x, y)
    }

    override fun getLastScrollOffset(direction: Boolean): Float {
        return scrollable.getLastScrollOffset(direction)
    }
}