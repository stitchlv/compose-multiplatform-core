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

import androidx.compose.ui.platform.OhosViewWrapper
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.isDebugInspectorInfoEnabled
import androidx.compose.ui.scene.ComposeSceneMediator
import androidx.compose.ui.util.FrameData
import androidx.compose.ui.util.FrameObserver
import androidx.compose.ui.util.KPerfComposeConfig
import androidx.compose.ui.util.MetaFrameData
import androidx.compose.ui.util.PreComposeProbe
import androidx.compose.ui.utils.currentNanoTime
import com.bytedance.kmp.harko.HarkoContext
import com.bytedance.kmp.harko.HarkoScope
import com.bytedance.kmp.harko.OHLogger
import com.bytedance.kmp.harko.ark.AbsRenderNode
import com.bytedance.kmp.harko.ark.NApiValue
import com.bytedance.kmp.harko.ark.addFinalizer
import com.bytedance.kmp.harko.ark.call
import com.bytedance.kmp.harko.ark.createRef
import com.bytedance.kmp.harko.ark.createWeakRef
import com.bytedance.kmp.harko.ark.deleteRef
import com.bytedance.kmp.harko.ark.getNapiNull
import com.bytedance.kmp.harko.ark.getRefValue
import com.bytedance.kmp.harko.ark.isNull
import com.bytedance.kmp.harko.ark.isUndefined
import com.bytedance.kmp.harko.ark.runInHandleScope
import com.bytedance.kmp.harko.ark.setNamedProperty
import com.bytedance.kmp.harko.impl.isNullPtr
import com.bytedance.kmp.harko.model.TouchEvent
import com.bytedance.kmp.harko.render.EmptyTouchEventInterceptor
import com.bytedance.kmp.harko.render.FrameExportApi
import com.bytedance.kmp.harko.render.FrameImportApi
import com.bytedance.kmp.harko.render.FrameRenderView
import com.bytedance.kmp.harko.render.SizeConstraint
import com.bytedance.kmp.harko.render.getRenderView
import com.bytedance.kmp.harko.skia.Canvas
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.staticCFunction
import kotlinx.coroutines.DisposableHandle
import kotlin.native.ref.WeakReference
import kotlinx.coroutines.SupervisorJob
import platform.ohos.napi.napi_value

private const val TAG = "RenderingUIView"

typealias FrameHolderGetter = () -> FrameExportApi

class RenderingUIView(
    renderNode: AbsRenderNode,
    importApi: FrameImportApi,
    param: NApiValue,
    rootContent: NApiValue?,
    interopBottomNodeContent: NApiValue,
    interopTopNodeContent: NApiValue,
    textToolbar: NApiValue?,
    nodeController: NApiValue,
    contentData: ContentData,
    private val frameHolderGetter: FrameHolderGetter?,
    constraint: SizeConstraint?,
    val hitTestMode: Int,
    isPreCompose: Boolean,
    private val preComposeProbe: PreComposeProbe?,
    extraValuesGetter: () -> Array<ProvidedValue<*>>,
    val frameNodeId: Int?,
    rootFrameNode: NApiValue?,
    uiContext: napi_value?
) : FrameRenderView(
    renderNode, constraint, importApi, param,
    contentData.touchInterceptor ?: EmptyTouchEventInterceptor
) {
    private var lastOnFrameTimeNanos: Long = 0L

    companion object {
        internal fun getMediator(id: Long): ComposeSceneMediator? {
            return (getRenderView(id) as? RenderingUIView)?.mediator
        }

        val renderingUIViewWeakMap = mutableMapOf<Int, WeakReference<RenderingUIView>>()


        private fun setTextToolBarFinalizer(view: RenderingUIView, textToolbar: NApiValue?): Boolean {
            textToolbar ?: return false

            // 监听 textToolbar 回收，执行 onDestroy 回调，修复特殊场景下系统未回调 aboutToDisappear 导致的内存泄漏
            addFinalizer(textToolbar, StableRef.create(view).asCPointer(), staticCFunction { env, data, hint ->
                runInHandleScope {
                    OHLogger.i(TAG, "onTextToolbar finalize")
                    data?.asStableRef<RenderingUIView>()?.apply {
                        val renderingUIView = get()
                        dispose()
                        if (renderingUIView.isReleased) {
                            OHLogger.i(TAG, "isReleased")
                            return@apply
                        }
                        renderingUIView.nodeControllerRef?.let {
                            OHLogger.i(TAG, "call controller onDispose")
                            getRefValue(it)?.call("onDispose")
                        }
                        renderingUIView.nodeControllerRef?.let {
                            deleteRef(it)
                        }
                        renderingUIView.disposeRenderNode()
                    }
                }
            })
            return true
        }
    }

    init {
        if (isDebugInspectorInfoEnabled) {
            frameNodeId?.let {
                renderingUIViewWeakMap[frameNodeId] = WeakReference(this)
            }
        }
    }
    override val frameHolder: FrameExportApi
        get() = frameHolderGetter?.invoke() ?: super.frameHolder

    var nodeControllerRef = createRef(nodeController)

    private var isReleased = false
    private val needBackgroundColor: Boolean = contentData.needBackground
    private val withOffscreenRender: Boolean = contentData.withOffscreenRender

    private val job = SupervisorJob()
    private val coroutineContext: CoroutineContext = HarkoScope.coroutineContext + job

    @Volatile
    private var onIdleListener: ((Long) -> Unit)? = null
    private var onIdleEventConsumed = false

    private val mediator = ComposeSceneMediator(
        id, coroutineContext, contentData, param, rootContent, interopBottomNodeContent, interopTopNodeContent, textToolbar, this::invalidate,
        this::resetSize, getOhosView(), isPreCompose, extraValuesGetter, frameNodeId, rootFrameNode, uiContext
    )

    private var hasSetTextToolbar: Boolean = setTextToolBarFinalizer(this, textToolbar)

    private var currentRootFrameNode: NApiValue? = rootFrameNode
    private var currentUiContext: napi_value? = uiContext

    fun getOhosView(): OhosViewWrapper {
        return object : OhosViewWrapper {
            override val visible: Boolean get() = mediator.isVisible()
            override fun onIdle(listener: (Long) -> Unit): DisposableHandle {
                onIdleListener = listener
                return object : DisposableHandle {
                    override fun dispose() {
                        onIdleListener = null
                    }
                }
            }
        }
    }

    private val frameDelegate = OhosFrameDelegate()

    override fun onTouchIntercept(x: Float, y: Float): Int {
        return mediator.onTouchIntercept(x, y, hitTestMode).toInt()
    }

    override fun onBackPressed(): Boolean {
        if (isReleased) return false
        return mediator.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isDebugInspectorInfoEnabled) {
            frameNodeId?.let {
                renderingUIViewWeakMap.remove(frameNodeId)
            }
        }

        if (isReleased) return
        isReleased = true

        mediator.dispose()
        job.cancel()

        nodeControllerRef?.let {
            deleteRef(it)
        }
        nodeControllerRef = null
    }

    override fun onDraw(canvas: Canvas) {
        if (isReleased) return
        val drawStartTimeNanos = currentNanoTime()
        val renderFrameTimeNanos = if (lastOnFrameTimeNanos > 0L) {
            lastOnFrameTimeNanos
        } else {
            drawStartTimeNanos
        }
        if (withOffscreenRender) {
            canvas.saveLayer(0F, 0F, width.toFloat(), height.toFloat(), null)
        }
        if (needBackgroundColor) {
            canvas.clear((if (HarkoContext.isDarkThemeFlow.value) 0xFF000000 else 0xFFFFFFFF).toInt())
        }
        frameDelegate.onFrameStart(drawStartTimeNanos, id.toString())
        mediator.onRender(canvas, width, height, renderFrameTimeNanos)
        frameDelegate.onFrameEnd(currentNanoTime(), id.toString(), preComposeProbe?.isActualLaunched())
        if (withOffscreenRender) {
            canvas.restore()
        }
    }

    override fun onHide() {
        if (isReleased) return
        mediator.onPageHide()
    }

    override fun onShow() {
        if (isReleased) return
        mediator.onPageShow()
    }

    override fun onAppear() {
        if (isReleased) return
        mediator.onPageAppear()
    }

    override fun onDispatchTouchEvent(event: TouchEvent): Boolean {
        if (isReleased) return false

        return mediator.handleTouchEvent(event)
    }

    override fun onSizeChanged(width: Int, height: Int, posChanged: Boolean) {
        if (isReleased) return
        if (this.width!=width || this.height!=height || (this.width == 0 && this.height == 0)) {
            OHLogger.i(TAG, "onSizeChanged: $id ($width, $height) $posChanged")
            mediator.onSurfaceChanged(width, height, constraint)
            super.onSizeChanged(width, height, false)
            mediator.updateInteropContainerSize(measureWidth(), measureHeight())
        }
        if (posChanged) {
            super.filterTouchEvent()
        }
    }

    override fun onMeasure(constraint: SizeConstraint): Pair<Int, Int> {
        return mediator.measureSize().also {
            OHLogger.i(TAG, "onMeasure: $id $it")
        }
    }

    override fun invalidate() {
        if (isReleased) return
        super.invalidate()
    }

    override fun onFrame(frameTime: Long) {
        if (isReleased) return
        lastOnFrameTimeNanos = frameTime
        onIdleEventConsumed = false
        super.onFrame(frameTime)
    }

    override fun onIdle(timeLeft: Long) {
        if (isReleased) return
        // 避免一帧内的多次 onIdle 回调
        if (!onIdleEventConsumed) {
            val listener = onIdleListener
            onIdleListener = null
            listener?.invoke(timeLeft)
            frameDelegate.onIdle(timeLeft)
            onIdleEventConsumed = true
        }
    }

    override fun onFocus() {
        if (isReleased) return
        mediator.onFocus()
    }

    override fun onBlur() {
        if (isReleased) return
        mediator.onBlur()
    }

    override fun measureWidth(): Int {
        if (isReleased) return 0
        return super.measureWidth
    }

    override fun measureHeight(): Int {
        if (isReleased) return 0
        return super.measureHeight
    }

    override fun updateTextToolbar(textToolbar: NApiValue) {
        if (hasSetTextToolbar) error("updateTextToolbar duplicately")
        mediator.updateTextToolbar(textToolbar)
        hasSetTextToolbar = setTextToolBarFinalizer(this, textToolbar)
    }

    override fun updateFrameNodeId(frameNodeId: Int) {
        mediator.updateRootFrameNode(currentRootFrameNode, frameNodeId, currentUiContext)
    }

    fun updateRootFrameNode(rootFrameNode: NApiValue?, frameNodeId: Int?, uiContext: napi_value?) {
        currentRootFrameNode = rootFrameNode
        currentUiContext = uiContext
        mediator.updateRootFrameNode(rootFrameNode, frameNodeId, uiContext)
    }


    fun getCompositionDataMap(): MutableSet<CompositionData> {
        return  mediator.getCompositionDataMap() // 委托给 MultiLayerComposeScene
    }

    fun getRootForTest(): RootForTest {
        return  mediator.getRootForTest()
    }
}

class OhosFrameDelegate {
    private var lastTime: Long = -1L
    private var frameEndTime: Long = -1L
    private var lastTimeLeft: Long = -1L

    companion object {
        private const val TAG = "OhosFrameDelegate"
    }


    fun onFrameStart(currentTime: Long, id: String = "") {
        if (!KPerfComposeConfig.enableFrameMonitor) {
            return
        }
        // frameEndTime 没有回调，则把当前帧作为帧的结束，这种情况只有丢帧的时候会出现
        if (frameEndTime < lastTime) {
            frameEndTime = currentTime
//            OHLogger.i(TAG, "lost frame cost: ${currentTime - lastTime}")
        }
        FrameObserver.notify(FrameData(id, lastTime, frameEndTime, currentTime))
        lastTime = currentTime
    }

    fun onFrameEnd(currentTime: Long, id: String, launchTimeStampMs: Long?) {
        if (!KPerfComposeConfig.enableFrameMonitor) {
            return
        }
        frameEndTime = currentTime
        FrameObserver.notifyFrameEnd(
            FrameData(
                id,
                frameStartTimeNs = lastTime,
                frameEndTimeNs = currentTime,
                currentTimeNs = currentTime,
                launchTimeStampMs = launchTimeStampMs,
                metaFrameData = if(lastTimeLeft >= 0)
                    MetaFrameData(
                        timeLeft = lastTimeLeft
                    )
                else null
            )
        )
        lastTimeLeft = 0
    }

    fun onIdle(timeLeft: Long) {
        lastTimeLeft = timeLeft
    }
}
