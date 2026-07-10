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
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.util.PreComposeProbe
import com.bytedance.kmp.harko.OHLogger
import com.bytedance.kmp.harko.ark.NApiValue
import com.bytedance.kmp.harko.ark.RenderNode
import com.bytedance.kmp.harko.ark.RenderNodeV2
import com.bytedance.kmp.harko.impl.isNullPtr
import com.bytedance.kmp.harko.render.FrameImportApi
import com.bytedance.kmp.harko.render.FrameRenderView
import com.bytedance.kmp.harko.render.RENDER_VIEW_SEQUENCE
import com.bytedance.kmp.harko.render.SizeConstraint
import com.bytedance.kmp.harko.render.TouchEventInterceptor
import kotlin.concurrent.Volatile
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import platform.ohos.node.ARKUI_HIT_TEST_MODE_DEFAULT

private const val TAG = "ComposeEntrance"

object ComposeController {
    private val lock = reentrantLock()
    private val contents: MutableMap<String, ContentData> = mutableMapOf()

    @Volatile
    var isUsePictureRecorder = true

    operator fun set(id: String, content: ContentData) {
        lock.withLock {
            contents[id] = content
        }
    }

    internal operator fun get(id: String?): ContentData? {
        id ?: return null
        return lock.withLock {
            contents[id]
        }
    }

    fun remove(id: String) {
        OHLogger.i(TAG, "remove: $id")
        lock.withLock {
            contents.remove(id)
        }
    }

    fun hasContent(id: String): Boolean {
        return lock.withLock {
            contents.containsKey(id)
        }
    }

    fun initRenderNode(
        id: String, rootContent: NApiValue?, importApi: FrameImportApi, param: NApiValue, interopBottomNodeContent: NApiValue, interopTopNodeContent: NApiValue,
        textToolbar: NApiValue?, nodeController: NApiValue, frameHolderGetter: FrameHolderGetter? = null,
        constraint: SizeConstraint? = null, hitTestMode: Int = ARKUI_HIT_TEST_MODE_DEFAULT.toInt(),
        isPreCompose: Boolean = false, preComposeProbe: PreComposeProbe? = null,
        extraValuesGetter: () -> Array<ProvidedValue<*>> = DefaultExtraValuesGetter, frameNodeId: Int?, rootFrameNode: NApiValue?
    ): FrameRenderView {
        OHLogger.i(TAG, "initRenderNode: $id ${param.rawValue}")
        val contentData: ContentData = contents[id] ?: run {
            OHLogger.w(TAG, "initRenderNode: failed to get content data for $id")
            EmptyContentData
        }
        val renderNode = if (rootContent == null || rootContent.rawValue.isNullPtr) {
            RenderNode()
        } else {
            RenderNodeV2(rootContent)
        }
        return RenderingUIView(
            renderNode, importApi, param, rootContent, interopBottomNodeContent, interopTopNodeContent, textToolbar, nodeController, contentData, frameHolderGetter, constraint,
            hitTestMode, isPreCompose, preComposeProbe, extraValuesGetter, frameNodeId, rootFrameNode
        ).apply {
            onCreate()
        }
    }

    fun getRenderingUIView(frameNodeId: Int): RenderingUIView? {
        val renderingUIViewWeakRef = RenderingUIView.renderingUIViewWeakMap[frameNodeId]
        if (renderingUIViewWeakRef != null) {
            val data = renderingUIViewWeakRef.get()
            data?.let {
                return it
            }
        }
        return null
    }

    internal fun onInputFocus(id: Long) {
        OHLogger.i(TAG, "onInputFocus: $id")
        RENDER_VIEW_SEQUENCE.map { it as? RenderingUIView }.filterNotNull().filter { it.id != id }
            .forEach {
                it.onBlur()
            }
    }
}

class ContentData(
    val transfer: (NApiValue) -> Any? = DefaultTransfer,
    val touchInterceptor: TouchEventInterceptor? = null,
    val needBackground: Boolean = true,
    val disableRecycleNode: Boolean = false,
    val withOffscreenRender: Boolean = false,
    val content: @Composable () -> Unit,
)

val DefaultTransfer: (NApiValue) -> Any? = { it }
internal val EmptyContentData = ContentData {}

internal val DefaultExtraValuesGetter: () -> Array<ProvidedValue<*>> = { emptyArray() }
