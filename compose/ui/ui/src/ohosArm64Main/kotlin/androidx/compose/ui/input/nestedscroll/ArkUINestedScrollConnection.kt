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

import androidx.compose.ui.geometry.Offset
import com.bytedance.kmp.harko.HarkoContext
import com.bytedance.kmp.harko.OHLogger

private const val TAG = "ArkUINestedScrollConnection"

private fun logIfDebug(msg: String) {
    if (HarkoContext.isDebug) {
        OHLogger.d(TAG, msg)
    }
}

enum class ArkUIScrollState {
    DEFAULT, START, END
}

enum class ScrollDirection {
    VERTICAL, HORIZONTAL
}

enum class NestedScrollMode {
    SELF_FIRST, PARENT_FIRST
}

class ArkUINestedScrollConnection(
    private val direction: ScrollDirection,
    private val mode: NestedScrollMode,
    private val scrollable: ScrollableWithCallback,
) : NestedScrollConnection, NestedScrollCallback {
    private var state: ArkUIScrollState = ArkUIScrollState.START

    init {
        require(direction == ScrollDirection.VERTICAL) {
            "Nested scroll direction $direction is not supported."
        }
        scrollable.attachCallback(this)
    }

    override fun onReachStart() {
        OHLogger.i(TAG, "onReachStart")
        state = ArkUIScrollState.START
    }

    override fun onReachEnd() {
        OHLogger.i(TAG, "onReachEnd")
        state = ArkUIScrollState.END
    }

    override fun onDidScroll(offset: Int) {
        logIfDebug("onDidScroll $offset")
        if (offset == 0) return
        state = ArkUIScrollState.DEFAULT
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        logIfDebug("onPreScroll mode: $mode available: $available, source: $source, state: $state")
        when (mode) {
            NestedScrollMode.SELF_FIRST -> {
                if (state != ArkUIScrollState.END) {
                    if (available.y > 0) {
                        scrollable.scrollBy(0F, -available.y)
                        return available
                    }
                    val lastOffset = scrollable.getLastScrollOffset(false)
                    if (lastOffset >= -available.y) {
                        scrollable.scrollBy(0F, -available.y)
                        return available
                    }
                    scrollable.scrollBy(0F, lastOffset)
                    return Offset(0F, -lastOffset)
                }
            }

            NestedScrollMode.PARENT_FIRST -> {
                if (available.y > 0 && state != ArkUIScrollState.START) {
                    val lastOffset = scrollable.getLastScrollOffset(true)
                    if (lastOffset >= available.y) {
                        scrollable.scrollBy(0F, -available.y)
                        return available
                    }
                    scrollable.scrollBy(0F, -lastOffset)
                    return Offset(0F, lastOffset)
                }
            }
        }
        return super.onPreScroll(available, source)
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset {
        logIfDebug(
            "onPostScroll mode: $mode consumed: $consumed, available: $available, source: $source, state: $state"
        )
        when (mode) {
            NestedScrollMode.SELF_FIRST -> {
                if (available.y > 0) {
                    scrollable.scrollBy(0F, -available.y)
                    return available
                }
            }

            NestedScrollMode.PARENT_FIRST -> {
                if (available.y != 0F && state != ArkUIScrollState.END) {
                    scrollable.scrollBy(0F, -available.y)
                    return available
                }
            }
        }
        return super.onPostScroll(consumed, available, source)
    }

    fun onDispose() {
        scrollable.detachCallback()
    }
}

interface Scrollable {
    fun scrollBy(x: Float, y: Float)
    fun getLastScrollOffset(direction: Boolean): Float
}

interface NestedScrollCallback {
    fun onReachStart()
    fun onReachEnd()
    fun onDidScroll(offset: Int)
}

interface ScrollableWithCallback : Scrollable {
    fun attachCallback(callback: NestedScrollCallback)
    fun detachCallback()
}