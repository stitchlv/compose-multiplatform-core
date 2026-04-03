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

package androidx.compose.ui.pointer

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputFilter
import androidx.compose.ui.input.pointer.PointerInputModifier
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.utils.currentNanoTime
import com.bytedance.kmp.harko.model.TouchEvent
import com.bytedance.kmp.harko.model.TouchType

@ExperimentalComposeUiApi
fun Modifier.pointerInteropFilter(
    requestDisallowInterceptTouchEvent: (RequestDisallowInterceptTouchEvent)? = null,
    onTouchEvent: (TouchEvent) -> Boolean
): Modifier = composed(
    inspectorInfo = debugInspectorInfo {
        name = "pointerInteropFilter"
        properties["requestDisallowInterceptTouchEvent"] = requestDisallowInterceptTouchEvent
        properties["onTouchEvent"] = onTouchEvent
    }
) {
    val filter = remember { PointerInteropFilter() }
    filter.onTouchEvent = onTouchEvent
    filter.requestDisallowInterceptTouchEvent = requestDisallowInterceptTouchEvent
    filter
}


@ExperimentalComposeUiApi
class RequestDisallowInterceptTouchEvent : (Boolean) -> Unit {
    internal var pointerInteropFilter: PointerInteropFilter? = null

    override fun invoke(disallowIntercept: Boolean) {
        pointerInteropFilter?.disallowIntercept = disallowIntercept
    }
}

@ExperimentalComposeUiApi
internal class PointerInteropFilter : PointerInputModifier {

    lateinit var onTouchEvent: (TouchEvent) -> Boolean

    var requestDisallowInterceptTouchEvent: RequestDisallowInterceptTouchEvent? = null
        set(value) {
            field?.pointerInteropFilter = null
            field = value
            field?.pointerInteropFilter = this
        }
    internal var disallowIntercept = false

    private enum class DispatchToViewState {
        Unknown,
        Dispatching,
        NotDispatching
    }

    override val pointerInputFilter = object : PointerInputFilter() {
        private var state = DispatchToViewState.Unknown

        override val shareWithSiblings
            get() = true

        override fun onPointerEvent(
            pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize
        ) {
            val changes = pointerEvent.changes

            val dispatchDuringInitialTunnel = disallowIntercept ||
                changes.fastAny {
                    it.changedToDownIgnoreConsumed() || it.changedToUpIgnoreConsumed()
                }

            if (state !== DispatchToViewState.NotDispatching) {
                if (pass == PointerEventPass.Initial && dispatchDuringInitialTunnel) {
                    dispatchToView(pointerEvent)
                }
                if (pass == PointerEventPass.Final && !dispatchDuringInitialTunnel) {
                    dispatchToView(pointerEvent)
                }
            }
            if (pass == PointerEventPass.Final) {
                if (changes.fastAll { it.changedToUpIgnoreConsumed() }) {
                    reset()
                }
            }
        }

        override fun onCancel() {
            if (state === DispatchToViewState.Dispatching) {
                onTouchEvent(
                    TouchEvent(TouchType.Cancel, 0, emptyList())
                )
                reset()
            }
        }

        private fun reset() {
            state = DispatchToViewState.Unknown
            disallowIntercept = false
        }

        private fun dispatchToView(pointerEvent: PointerEvent) {

            val changes = pointerEvent.changes

            if (changes.fastAny { it.isConsumed }) {
                if (state === DispatchToViewState.Dispatching) {
                    (pointerEvent.nativeEvent as? TouchEvent)?.run {
                        onTouchEvent(
                            updateOffset(
                                layoutCoordinates?.localToRoot(Offset.Zero)
                                    ?: error("layoutCoordinates not set"), true
                            )
                        )
                    }
                }
                state = DispatchToViewState.NotDispatching
            } else {
                (pointerEvent.nativeEvent as? TouchEvent)?.run {
                    val updateEvent = updateOffset(
                        layoutCoordinates?.localToRoot(Offset.Zero)
                            ?: error("layoutCoordinates not set"), false
                    )
                    if (updateEvent.type == TouchType.Down) {
                        state = if (onTouchEvent(updateEvent)) {
                            DispatchToViewState.Dispatching
                        } else {
                            DispatchToViewState.NotDispatching
                        }
                    } else {
                        onTouchEvent(updateEvent)
                    }
                }
                if (state === DispatchToViewState.Dispatching) {
                    changes.fastForEach {
                        it.consume()
                    }
                }
            }
        }
    }
}

private fun TouchEvent.updateOffset(offset: Offset, cancel: Boolean): TouchEvent {
    if (offset == Offset.Zero) {
        if ((!cancel || type == TouchType.Cancel)) {
            return this
        }
        return TouchEvent(
            TouchType.Cancel, 0, emptyList()
        )
    }
    return TouchEvent(
        if (cancel) TouchType.Cancel else type, timestamp, points
    )
}
