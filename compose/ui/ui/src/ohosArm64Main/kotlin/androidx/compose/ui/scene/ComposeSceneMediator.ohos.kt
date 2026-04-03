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

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package androidx.compose.ui.scene

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.ui.LocalIsDarkTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.interop.ArkUIInteropContainer
import androidx.compose.ui.interop.ArkUIInteropContext
import androidx.compose.ui.interop.InteropArkUICatchPointerModifier
import androidx.compose.ui.interop.LocalArkUIInteropContainer
import androidx.compose.ui.interop.LocalArkUIInteropContext
import androidx.compose.ui.node.LargeDimension
import androidx.compose.ui.node.Owner.OnLayoutCompletedListener
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.node.TrackInteropContainer
import androidx.compose.ui.platform.DefaultInputModeManager
import androidx.compose.ui.platform.EmptyViewConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInternalViewModelStoreOwner
import androidx.compose.ui.platform.LocalKeyboardOverlapHeight
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.OhosLocalTextToolbar
import androidx.compose.ui.platform.OhosTextInputService
import androidx.compose.ui.platform.OhosViewWrapper
import androidx.compose.ui.platform.OhosWindowInsetsLocal
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.PlatformWindowContext
import androidx.compose.ui.platform.SystemFontId
import androidx.compose.ui.platform.SystemFontWeightScale
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.asWindowInsets
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.BackPressedHandlerCollection
import androidx.compose.ui.window.BackPressedHandlers
import androidx.compose.ui.window.ContentData
import androidx.compose.ui.window.FirstFrameCallbackWrapper
import androidx.compose.ui.window.FirstFrameHandler
import androidx.compose.ui.window.RenderNodeParams
import androidx.compose.ui.window.UIViewParam
import androidx.compose.ui.window.ViewControllerBasedLifecycleOwner
import androidx.lifecycle.Lifecycle
import com.bytedance.kmp.harko.HarkoContext
import com.bytedance.kmp.harko.HarkoScope
import com.bytedance.kmp.harko.OHLogger
import com.bytedance.kmp.harko.ark.NApiValue
import com.bytedance.kmp.harko.model.TouchEvent
import com.bytedance.kmp.harko.model.TouchType
import com.bytedance.kmp.harko.render.RenderDelegate
import com.bytedance.kmp.harko.render.SIZE_CONSTRAINT_MATCH_PARENT
import com.bytedance.kmp.harko.render.SIZE_CONSTRAINT_WRAP_CONTENT
import com.bytedance.kmp.harko.render.SizeConstraint
import com.bytedance.kmp.harko.skia.Canvas
import com.bytedance.kmp.harko.skia.Rect
import kotlin.coroutines.CoroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

internal class ComposeSceneMediator(
    private val id: Long,
    coroutineContext: CoroutineContext,
    private val contentData: ContentData,
    param: NApiValue,
    interopBottomNodeContent: NApiValue,
    interopTopNodeContent: NApiValue,
    textToolbar: NApiValue?,
    private val invalidate: () -> Unit,
    private val onSizeChanged: (Int, Int) -> Unit,
    private val ohosViewWrapper: OhosViewWrapper,
    private val isPreCompose: Boolean,
    private val extraValuesGetter: () -> Array<ProvidedValue<*>>
) : RenderDelegate, OnLayoutCompletedListener {
    companion object {
        private const val TAG = "ComposeSceneMediator"
        private val themeChangedCallbacks = HashMap<Long, (Boolean) -> Unit>()
        private val keyboardHeightChangedCallbacks = HashMap<Long, (Int) -> Unit>()
        private val windowInsetsChangedCallbacks =
            HashMap<Long, (Triple<Rect, Rect, Rect>) -> Unit>()
        private val fontWeightChangedCallbacks = HashMap<Long, (Double) -> Unit>()
        private val systemFontIdChangedCallbacks = HashMap<Long, (String) -> Unit>()

        init {
            HarkoScope.launch {
                HarkoContext.isDarkThemeFlow.collectLatest { isDark ->
                    themeChangedCallbacks.values.forEach {
                        try {
                            it(isDark)
                        } catch (e: Throwable) {
                            OHLogger.w(TAG, "themeChangedCallback: $isDark, error: $e")
                        }
                    }
                }
            }
            HarkoScope.launch {
                HarkoContext.keyboardHeightFlow.collectLatest { height ->
                    keyboardHeightChangedCallbacks.values.forEach {
                        try {
                            it(height)
                        } catch (e: Throwable) {
                            OHLogger.w(TAG, "keyboardHeightChangedCallback: $height, error: $e")
                        }
                    }
                }
            }
            HarkoScope.launch {
                HarkoContext.windowInsetsFlow.collectLatest { windowInsets ->
                    windowInsetsChangedCallbacks.values.forEach {
                        try {
                            it(windowInsets)
                        } catch (e: Throwable) {
                            OHLogger.w(
                                TAG,
                                "windowInsetsChangedCallbacks: $windowInsets, error: $e"
                            )
                        }
                    }
                }
            }
            HarkoScope.launch {
                HarkoContext.fontWeightScaleFlow.collectLatest { fontWeightScale ->
                    fontWeightChangedCallbacks.values.forEach {
                        try {
                            it(fontWeightScale)
                        } catch (e: Throwable) {
                            OHLogger.w(
                                TAG,
                                "fontWeightChangedCallbacks: $fontWeightScale, error: $e"
                            )
                        }
                    }
                }
            }
            HarkoScope.launch {
                HarkoContext.systemFontIdFlow.collectLatest { systemFontId ->
                    systemFontIdChangedCallbacks.values.forEach {
                        try {
                            it(systemFontId)
                        } catch (e: Throwable) {
                            OHLogger.w(
                                TAG,
                                "systemFontIdChangedCallbacks: $systemFontId, error: $e"
                            )
                        }
                    }
                }
            }
        }
    }

    private val immediateScope = CoroutineScope(Dispatchers.Main.immediate)

    /**
     * InteropContainer for ArkUI
     */
    private val interopViewContainer = ArkUIInteropContainer()

    private val interopContext: ArkUIInteropContext by lazy {
        ArkUIInteropContext(
            requestRedraw = {
                invalidate()
            },
            {
                lifecycleOwner.lifecycle.currentState == Lifecycle.State.DESTROYED
            }
        )
    }

    private val densityFlow = combine(
        HarkoContext.dpiFlow,
        HarkoContext.fontSizeScaleFlow
    ) { dpi, fontScale ->
        Density(density = dpi.toFloat(), fontScale = fontScale.toFloat())
    }
    private val densityFlowJob: Job
    private val displaySizeFlowJob: Job

    private val uiViewParam = UIViewParam(id, param, contentData.transfer, interopBottomNodeContent, interopTopNodeContent, textToolbar)
    private val lifecycleOwner = ViewControllerBasedLifecycleOwner()
    private val windowContext = PlatformWindowContext().apply {
        setWindowFocused(false)
    }
    private val ohosTextInputService = OhosTextInputService(
        onKeyboardEvent = ::onKeyboardEvent,
        densityProvider = ::getDensity,
        arkTsTextToolBar = {
            uiViewParam.getTextToolbar()
        },
        viewId = id,
    )
    private val viewConfiguration: ViewConfiguration =
        object : ViewConfiguration by EmptyViewConfiguration {
            override val touchSlop: Float
                get() = with(scene.density) {
                    10.dp.toPx()
                }
        }
    private val ohosPlatformContext = OhosPlatformContext()
    private val scene = MultiLayerComposeScene(
        density = Density(
            density = HarkoContext.dpiFlow.value.toFloat(),
            fontScale = HarkoContext.fontSizeScaleFlow.value.toFloat()
        ),
        layoutDirection = LayoutDirection.Ltr,
        coroutineContext = coroutineContext,
        composeSceneContext = ComposeSceneContextImpl(ohosPlatformContext),
        invalidate = invalidate,
        disableRecycleNode = contentData.disableRecycleNode
    )
    private var isConstraintSize = false
    var isAppeared = false
        private set

    private val backHandlers = BackPressedHandlerCollection()
    private val firstFrameWrapper = FirstFrameCallbackWrapper()

    private fun onKeyboardEvent(keyEvent: KeyEvent): Boolean = scene.sendKeyEvent(keyEvent)

    private fun handleFocusLost() {
        // 当失去全局焦点时，需要：
        // 1. 通知TextInputService该ComposeView失去焦点
        // 2. 清除当前场景中所有TextField的焦点
        // 3. 隐藏软键盘
        OHLogger.d(TAG, "Focus lost for view: $id")

        // 通知TextInputService该ComposeView失去焦点，这会自动停止活跃的TextField
        OhosTextInputService.setViewFocus(id, false)

        // 强制清除焦点，确保TextField失去焦点
        scene.focusManager.clearFocus(force = true)

        // 隐藏软键盘
        ohosTextInputService.hideSoftwareKeyboard()
    }

    override fun onRender(canvas: Canvas, width: Int, height: Int, nanoTime: Long): Boolean {
        if (scene.size == null) {
            val size = IntSize(width, height)
            scene.size = size
            if (!isPreCompose) {
                setContent()
                lifecycleOwner.lifecycle.run {
                    currentState = Lifecycle.State.RESUMED
                }
            }
        }
        if (isConstraintSize) {
            scene.mainOwner.owner.registerOnLayoutCompletedListener(this)
        }
        scene.render(canvas.asComposeCanvas(), nanoTime)
        firstFrameWrapper.invoke()
        immediateScope.launch {
            interopContext.retrieve().forEach { it() }
        }
        return false
    }

    fun onSurfaceChanged(width: Int, height: Int, constraint: SizeConstraint?) {
        val size = constraint?.let {
            IntSize(
                when (val constraintWidth = constraint.width()) {
                    SIZE_CONSTRAINT_WRAP_CONTENT -> Int.MAX_VALUE
                    SIZE_CONSTRAINT_MATCH_PARENT -> width
                    else -> constraintWidth
                },
                when (val constraintHeight = constraint.height()) {
                    SIZE_CONSTRAINT_WRAP_CONTENT -> Int.MAX_VALUE
                    SIZE_CONSTRAINT_MATCH_PARENT -> height
                    else -> constraintHeight
                }
            )
        } ?: IntSize(width, height)
        isConstraintSize = constraint != null
        val needSetContent = scene.size == null
        scene.size = size
        if (needSetContent && !isPreCompose) {
            setContent()
        }
        lifecycleOwner.lifecycle.run {
            if (currentState == Lifecycle.State.INITIALIZED) {
                currentState = Lifecycle.State.CREATED
            }
        }
        if (isConstraintSize) {
            OHLogger.i(TAG, "onSurfaceChanged: doLayout $size [$width:$height]")
            scene.doLayout()
        }
    }

    fun updateInteropContainerSize(measureWidth: Int, measureHeight: Int) {
        with(getDensity()) {
            OHLogger.d(TAG, "updateInteropContainerSize: $measureWidth $measureHeight")
            interopViewContainer.bottomContainerView.setSize(measureWidth.toDp().value, measureHeight.toDp().value)
            interopViewContainer.topContainerView.setSize(measureWidth.toDp().value, measureHeight.toDp().value)
        }
    }

    fun measureSize(): Pair<Int, Int> = with(scene.mainOwner.owner.root) {
        OHLogger.i(
            TAG,
            "doLayout: outer size = ${outerCoordinator.size} inner size = ${innerCoordinator.size}, width = $width, height = $height, childCnt = ${children.size}"
        )
        if (children.size == 1) {
            return@with children[0].let { it.width to it.height }
        }
        children.forEachIndexed { index, layoutNode ->
            OHLogger.i(TAG, "doLayout: child $index ${layoutNode.width} ${layoutNode.height}")
        }
        if (width != LargeDimension && height != LargeDimension) {
            return@with width to height
        }
        var measureWidth = if (width == LargeDimension) 0 else width
        var measureHeight = if (height == LargeDimension) 0 else height
        children.forEach { layoutNode ->
            measureWidth = max(measureWidth, layoutNode.width)
            measureHeight = max(measureHeight, layoutNode.height)
        }
        return min(width, measureWidth) to min(height, measureHeight)
    }

    override fun onLayoutComplete() {
        measureSize().let { (width, height) ->
            OHLogger.i(TAG, "onLayoutComplete: $width $height")
            onSizeChanged(width, height)
        }
    }

    fun onPageShow() {
        OHLogger.i(TAG, "onPageShow: $id")
        lifecycleOwner.lifecycle.run {
            currentState = Lifecycle.State.RESUMED
        }
        GlobalFocusCoordinator.registerView(id, windowContext, ::handleFocusLost)
        OhosTextInputService.setViewFocus(id, false)
    }

    fun onPageHide() {
        OHLogger.i(TAG, "onPageHide: $id")
        lifecycleOwner.lifecycle.run {
            currentState = Lifecycle.State.CREATED
        }
        GlobalFocusCoordinator.releaseFocus(id)
        GlobalFocusCoordinator.unregisterView(id)
        OhosTextInputService.setViewFocus(id, false)
    }

    fun onPageAppear() {
        OHLogger.i(TAG, "onPageAppear: $id")
        var needRedraw = false
        if (!isAppeared) {
            isAppeared = true
            needRedraw = isPreCompose
        }
        ohosTextInputService.startInputOnAppearIfNeed()
        if (needRedraw) {
            invalidate()
        }
    }

    fun onFocus() {
        OHLogger.i(TAG, "onFocus: $id")
    }

    fun onBlur() {
        OHLogger.i(TAG, "onBlur $id")
    }

    fun updateTextToolbar(textToolbar: NApiValue) {
        uiViewParam.updateTextToolbar(textToolbar)
    }

    fun handleTouchEvent(event: TouchEvent): Boolean {
        if (event.type == TouchType.Cancel) {
            if (HarkoContext.isDebug) {
                OHLogger.d(TAG, "handleTouchEvent: process cancel")
            }
            scene.mainOwner.onPointerCancel()
            return false
        }

        if (event.points.isEmpty()) return false

        val eventType: PointerEventType = when (event.type) {
            TouchType.Down -> PointerEventType.Press
            TouchType.Move -> PointerEventType.Move
            TouchType.Up -> PointerEventType.Release
            else -> null
        } ?: return false

        val result = scene.sendPointerEvent(
            eventType = eventType,
            pointers = event.points.map {
                ComposeScenePointer(
                    PointerId(it.id), Offset(it.x.toFloat(), it.y.toFloat()), it.pressed,
                    PointerType.Touch, it.pressure
                )
            },
            timeMillis = event.timestamp / 1_000_000,
            nativeEvent = event,
        )
        if (HarkoContext.isDebug) {
            OHLogger.d(
                TAG,
                "handleTouchEvent: eventType: $eventType, [${event.points.size}], ${result.dispatchedToAPointerInputModifier} ${result.anyMovementConsumed}"
            )
        }
        return result.dispatchedToAPointerInputModifier
    }

    fun onBackPressed(): Boolean = backHandlers.foreach { it.onBackPressed() }
    init {
        densityFlowJob = HarkoScope.launch(coroutineContext) {
            densityFlow.collect { density ->
                scene.density = density
            }
        }
        displaySizeFlowJob = HarkoScope.launch {
            HarkoContext.displaySizeFlow.collectLatest { (width, height) ->
                windowContext.setContainerSize(IntSize(width, height))
            }
        }
        if (isPreCompose) {
            HarkoScope.launch {
                setContent()
            }
        }
        scene.isInitFinish = true
    }
    fun onTouchIntercept(x: Float, y: Float, default: Int): UInt {
        // hit interop view bounds, return interop view hit test mode
        val hitTestMode = (scene.getHitTestInteropModifier(Offset(x, y)) as? InteropArkUICatchPointerModifier)?.run {
            hitTestMode().ordinal
        }
        return hitTestMode?.toUInt() ?: default.toUInt()
    }

    fun isVisible(): Boolean {
        return lifecycleOwner.lifecycle.currentState == Lifecycle.State.RESUMED
    }

    fun dispose() {
        lifecycleOwner.lifecycle.run {
            if (currentState == Lifecycle.State.INITIALIZED) {
                OHLogger.w(TAG, "onSurfaceDestroyed: lifecycle is INITIALIZED, maybe wrong")
                currentState = Lifecycle.State.CREATED
            }
            currentState = Lifecycle.State.DESTROYED
        }

        GlobalFocusCoordinator.unregisterView(id)
        ohosTextInputService.dispose()
        densityFlowJob.cancel()
        displaySizeFlowJob.cancel()
        uiViewParam.dispose()
        scene.close()
    }

    private fun setContent() {
        immediateScope.launch {
            uiViewParam.getInteropBottomNodeContentHandle()?.let { bottom ->
                uiViewParam.getInteropTopNodeContentHandle()?.let { top ->
                    interopViewContainer.setNodeContentHandle(bottom, top)
                }
            }
        }
        scene.setContent {
            provideLocals {
                interopViewContainer.TrackInteropContainer(
                    content = contentData.content
                )
            }
        }
    }
    fun getCompositionDataMap(): MutableSet<CompositionData> {
        return scene.getCurrentCompositionData()
    }

    fun getRootForTest(): RootForTest {
        return scene.getRootForTest()
    }

    @OptIn(InternalComposeApi::class)
    @Composable
    private fun provideLocals(content: @Composable () -> Unit) {
        var isDarkTheme by rememberSaveable { mutableStateOf(HarkoContext.isDarkThemeFlow.value) }
        val isDarkThemeCallback: (Boolean) -> Unit = remember {
            { it: Boolean ->
                OHLogger.d(TAG, "provideLocals: isDarkThemeCallback $it")
                isDarkTheme = it
            }.also {
                themeChangedCallbacks[id] = it
            }
        }
        OHLogger.d(TAG, "provideLocals: isDarkTheme=$isDarkTheme")

        val density = LocalDensity.current
        var keyboardOverlapHeightState by rememberSaveable { mutableStateOf(with(density) { HarkoContext.keyboardHeightFlow.value.toDp() }) }
        val keyboardHeightChangedCallback = remember {
            { it: Int ->
                keyboardOverlapHeightState = with(density) { it.toDp() }
            }.also {
                keyboardHeightChangedCallbacks[id] = it
            }
        }
        var windowInsetsState by rememberSaveable {
            mutableStateOf(
                HarkoContext.windowInsetsFlow.value.asWindowInsets(
                    density
                )
            )
        }
        val windowInsetsChangedCallback = remember {
            { it: Triple<Rect, Rect, Rect> ->
                windowInsetsState = it.asWindowInsets(density)
            }.also {
                windowInsetsChangedCallbacks[id] = it
            }
        }
        var fontWeightScale by rememberSaveable { mutableStateOf(HarkoContext.fontWeightScaleFlow.value) }
        val fontWeightCallback = remember {
            { it: Double ->
                OHLogger.d(TAG, "provideLocals: fontWeightCallback $it")
                fontWeightScale = it
            }.also {
                fontWeightChangedCallbacks[id] = it
            }
        }
        var systemFontId by rememberSaveable { mutableStateOf(HarkoContext.systemFontIdFlow.value) }
        val systemFontIdCallback = remember {
            { it: String ->
                OHLogger.d(TAG, "provideLocals: systemFontIdCallback $it")
                systemFontId = it
            }.also {
                systemFontIdChangedCallbacks[id] = it
            }
        }
        DisposableEffect(
            isDarkThemeCallback,
            keyboardHeightChangedCallback,
            windowInsetsChangedCallback,
            fontWeightCallback,
            systemFontIdCallback,
        ) {
            onDispose {
                OHLogger.d(TAG, "provideLocals: onDispose $id")
                themeChangedCallbacks.remove(id)
                keyboardHeightChangedCallbacks.remove(id)
                windowInsetsChangedCallbacks.remove(id)
                fontWeightChangedCallbacks.remove(id)
                systemFontIdChangedCallbacks.remove(id)
            }
        }

        CompositionLocalProvider(
            @OptIn(InternalComposeApi::class)
            LocalIsDarkTheme provides isDarkTheme,
            LocalLifecycleOwner provides lifecycleOwner,
            LocalInternalViewModelStoreOwner provides lifecycleOwner,
            LocalKeyboardOverlapHeight provides keyboardOverlapHeightState,
            BackPressedHandlers provides backHandlers,
            FirstFrameHandler provides firstFrameWrapper,
            RenderNodeParams provides uiViewParam,
            OhosLocalTextToolbar provides ohosTextInputService.textToolbarContext,
            OhosWindowInsetsLocal provides windowInsetsState,
            LocalArkUIInteropContainer provides interopViewContainer,
            LocalArkUIInteropContext provides interopContext,
            SystemFontWeightScale provides fontWeightScale,
            SystemFontId provides systemFontId,
            LocalView provides ohosViewWrapper,
            *extraValuesGetter(),
            content = content
        )
    }

    private inner class OhosPlatformContext : PlatformContext {
        // @TODO PlatformContext 哪些是需要补充的
        override val windowInfo get() = this@ComposeSceneMediator.windowContext.windowInfo

        override val textInputService get() = this@ComposeSceneMediator.ohosTextInputService

        override val textToolbar get() = this@ComposeSceneMediator.ohosTextInputService

        override val viewConfiguration get() = this@ComposeSceneMediator.viewConfiguration

        override val inputModeManager: InputModeManager = DefaultInputModeManager(InputMode.Touch)
    }

    private class ComposeSceneContextImpl(
        override val platformContext: PlatformContext
    ) : ComposeSceneContext {
        // @TODO SingleLayerComposeScene considered to be used instead of MultiLayerComposeScene, then PlatformContext should override fun createPlatformLayer()
    }

    fun getDensity(): Density = scene.density
}
