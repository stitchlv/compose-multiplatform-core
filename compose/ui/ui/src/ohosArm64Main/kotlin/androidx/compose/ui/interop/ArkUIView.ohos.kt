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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.InteropViewCatchPointerModifier
import androidx.compose.ui.interop.arkui.ArkUINode
import androidx.compose.ui.interop.arkui.builtin.ArkUIStack
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.toDpRect
import androidx.compose.ui.unit.toRect
import androidx.compose.ui.unit.width
import com.bytedance.kmp.harko.OHLogger
import kotlinx.atomicfu.atomic

/**
 * https://developer.huawei.com/consumer/cn/doc/harmonyos-references/ts-universal-attributes-hit-test-behavior#hittestmode枚举说明
 */
enum class ArkUIViewHitTestMode {
    DEFAULT, BLOCK, TRANSPARENT, NONE,
}

/**
 * ArkUI 混排组件
 * 混排容器在 Compose 底部
 */
@Composable
fun <T : ArkUINode> ArkUIView(
    factory: () -> T,
    modifier: Modifier,
    update: (T) -> Unit = {},
    background: Color = Color.Unspecified,
    onRelease: (T) -> Unit = {},
    interactive: Boolean = true,
    hitTestMode: () -> ArkUIViewHitTestMode = { ArkUIViewHitTestMode.NONE },
    onAddToHierarchy: (ArkUIStack) -> Unit = {},
) {
    _ArkUIView(
        factory,
        modifier,
        update,
        background,
        onRelease,
        interactive,
        hitTestMode,
        onAddToHierarchy,
        InteropLayer.Bottom
    )
}

/**
 * ArkUI 混排组件
 * 混排容器在 Compose 顶部
 */
@Composable
fun <T : ArkUINode> ArkUIViewTopLayer(
    factory: () -> T,
    modifier: Modifier,
    update: (T) -> Unit = {},
    background: Color = Color.Unspecified,
    onRelease: (T) -> Unit = {},
    onAddToHierarchy: (ArkUIStack) -> Unit = {},
) {
    _ArkUIView(
        factory,
        modifier,
        update,
        background,
        onRelease,
        true,
        { ArkUIViewHitTestMode.NONE },
        onAddToHierarchy,
        InteropLayer.Top
    )
}

@Composable
private fun <T : ArkUINode> _ArkUIView(
    factory: () -> T,
    modifier: Modifier,
    update: (T) -> Unit = {},
    background: Color = Color.Unspecified,
    onRelease: (T) -> Unit = {},
    interactive: Boolean = true,
    hitTestMode: () -> ArkUIViewHitTestMode = { ArkUIViewHitTestMode.NONE },
    onAddToHierarchy: (ArkUIStack) -> Unit = {},
    layer: InteropLayer = InteropLayer.Bottom,
) {

    val interopContainer = LocalArkUIInteropContainer.current
    val interopContext = LocalArkUIInteropContext.current
    val embeddedInteropComponent = remember {
        OHLogger.dTime(ARKUI_INTEROP_TAG, "create EmbeddedInteropView") {
            EmbeddedInteropView<T>(interopContainer, layer, onRelease)
        }
    }

    val density = LocalDensity.current
    var rectInPixels by remember { mutableStateOf(IntRect(0, 0, 0, 0)) }

    // 避免挖孔时 ArkUI 还没有添加到组件中从而漏出下个页面的内容
    var containerLayoutFinish by remember { mutableStateOf(false) }
    var hasAddedToHierarchy = remember { BooleanHolder(false) }

    Layout(
        content = {},
        modifier = modifier.onGloballyPositioned { coordinates ->
            val localToWindowOffset = coordinates.positionInRoot().round()
            val newRectInPixels = IntRect(localToWindowOffset, coordinates.size)
            println("$ARKUI_INTEROP_TAG: rectInPixels: $rectInPixels, newRectInPixels: $newRectInPixels")
            if (rectInPixels != newRectInPixels) {
                val rect = newRectInPixels.toRect().toDpRect(density)
                if (hasAddedToHierarchy.value) {
                    interopContext.deferAction {
                        OHLogger.dTime(ARKUI_INTEROP_TAG, "set position and size") {
                            embeddedInteropComponent.wrappingView.setSize(rect.width.value, rect.height.value)
                            embeddedInteropComponent.wrappingView.setPosition(rect.left.value, rect.top.value)
                        }
                    }
                } else {
                    println("$ARKUI_INTEROP_TAG: not add to hierarchy, delay update size")
                }

                println("$ARKUI_INTEROP_TAG: newRectInPixels $newRectInPixels")
                rectInPixels = newRectInPixels
            }
        }.drawBehind {
            if (layer == InteropLayer.Bottom) {
                if (containerLayoutFinish) {
                    // Clear interop area to make visible the component under our canvas.
                    drawRect(Color.Transparent, blendMode = BlendMode.Clear)
                } else {
                    // draw background when not digHole
                    drawRect(background)
                }
            } else {
                drawRect(background)
            }
        }.trackArkUIInterop(embeddedInteropComponent.wrappingView, layer).let {
            if (interactive && layer == InteropLayer.Bottom) {
                it.then(InteropArkUICatchPointerModifier(hitTestMode))
            } else {
                it
            }
        },
        measurePolicy = { _, constraints ->
            OHLogger.dTime(ARKUI_INTEROP_TAG, "measurePolicy") {
                println("$ARKUI_INTEROP_TAG: measured with constraints: $constraints")
                embeddedInteropComponent.component.measure(constraints)
                var (width, height) = embeddedInteropComponent.component.getMeasuredSize()

                println("$ARKUI_INTEROP_TAG: measured width: $width, measured height: $height")

                width = width.coerceIn(constraints.minWidth, constraints.maxWidth)
                height = height.coerceIn(constraints.minHeight, constraints.maxHeight)

                println("$ARKUI_INTEROP_TAG: final width: $width, fianl height: $height")
                layout(width, height) {}
            }
        }
    )

    DisposableEffect(Unit) {
        println("$ARKUI_INTEROP_TAG: create component")
        OHLogger.dTime(ARKUI_INTEROP_TAG, "create component") {
            embeddedInteropComponent.setComponent(factory())
        }
        embeddedInteropComponent.updater = Updater(embeddedInteropComponent.component, update) {
            interopContext.deferAction(action = it)
        }
        interopContext.deferAction {
            OHLogger.dTime(ARKUI_INTEROP_TAG, "add component to $layer hierarchy") {
                embeddedInteropComponent.addToHierarchy()
                onAddToHierarchy(embeddedInteropComponent.wrappingView)
                hasAddedToHierarchy.value = true
                // 首次添加到组件树中，需要设置一次 position 和 size
                OHLogger.dTime(ARKUI_INTEROP_TAG, "set position and size for add to hierarchy, ${rectInPixels}") {
                    val rect = rectInPixels.toRect().toDpRect(density)
                    embeddedInteropComponent.wrappingView.setSize(rect.width.value, rect.height.value)
                    embeddedInteropComponent.wrappingView.setPosition(rect.left.value, rect.top.value)
                }
                interopContext.deferAction {
                    containerLayoutFinish = true
                }
            }
        }
        onDispose {
            println("$ARKUI_INTEROP_TAG: onDispose")
            interopContext.deferAction {
                OHLogger.dTime(ARKUI_INTEROP_TAG, "remove component from hierarchy") {
                    embeddedInteropComponent.removeFromHierarchy()
                }
            }
        }
    }

    LaunchedEffect(background) {
        interopContext.deferAction {
            OHLogger.dTime(ARKUI_INTEROP_TAG, "setBackgroundColor for embeddedInteropComponent") {
                embeddedInteropComponent.setBackgroundColor(background)
            }
        }
    }

    SideEffect {
        interopContext.deferAction {
            embeddedInteropComponent.updater.update = update
        }
    }
}

internal class EmbeddedInteropView<T: ArkUINode>(
    val interopContainer: ArkUIInteropContainer,
    val layer: InteropLayer,
    val onRelease: (T) -> Unit = {}
) {
    val wrappingView = ArkUIStack().apply {
        setWidth(0f)
        setHeight(0f)
    }
    lateinit var component: T
        private set
    lateinit var updater: Updater<T>

    fun setComponent(component: T) {
        this.component = component
    }

    /**
     * 在外面包一层是为了使 backgaround 生效，因为原始 Composable 已经设置为 Transparent
     */
    fun setBackgroundColor(color: Color) {
        if (color == Color.Unspecified) {
            interopContainer.bottomContainerView.getBackgroundColor()?.apply {
                wrappingView.setBackgroundColor(this)
            }
        } else {
            wrappingView.setBackgroundColor(color)
        }
    }

    fun addToHierarchy() {
        wrappingView.addChild(component)
        interopContainer.addInteropView(wrappingView, layer)
    }

    fun removeFromHierarchy() {
        wrappingView.removeChild(component)
        interopContainer.removeInteropView(wrappingView, layer)
        wrappingView.dispose()
        updater.dispose()
        onRelease(component)
    }
}

internal class Updater<T : Any>(
    private val component: T,
    update: (T) -> Unit,

    /**
     * Updater will not execute the [update] method by itself, but will pass it to this lambda
     */
    private val deferAction: (() -> Unit) -> Unit,
) {
    private var isDisposed = false
    private val isUpdateScheduled = atomic(false)
    private val snapshotObserver = SnapshotStateObserver { command ->
        command()
    }

    private val scheduleUpdate = { _: T ->
        if (!isUpdateScheduled.getAndSet(true)) {
            deferAction {
                isUpdateScheduled.value = false
                if (!isDisposed) {
                    performUpdate()
                }
            }
        }
    }

    var update: (T) -> Unit = update
        set(value) {
            if (field != value) {
                field = value
                performUpdate()
            }
        }

    private fun performUpdate() {
        // don't replace scheduleUpdate by lambda reference,
        // scheduleUpdate should always be the same instance
        snapshotObserver.observeReads(component, scheduleUpdate) {
            update(component)
        }
    }

    init {
        snapshotObserver.start()
        performUpdate()
    }

    fun dispose() {
        snapshotObserver.stop()
        snapshotObserver.clear()
        isDisposed = true
    }
}

private class BooleanHolder(var value: Boolean)

internal class InteropArkUICatchPointerModifier(val hitTestMode: () -> ArkUIViewHitTestMode): InteropViewCatchPointerModifier()