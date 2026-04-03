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

package androidx.compose.foundation.gestures

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.util.fastFirstOrNull
import com.bytedance.kmp.harko.HarkoContext
import com.bytedance.kmp.harko.OHLogger
import com.bytedance.kmp.harko.model.TouchType
import kotlin.math.abs

private const val TAG = "NestedScrollGestureDetector"

private fun PointerEvent.isPointerUp(pointerId: PointerId): Boolean =
    changes.fastFirstOrNull { it.id == pointerId }?.pressed != true

private fun logIfDebug(msg: String) {
    if (HarkoContext.isDebug) {
        OHLogger.d(TAG, msg)
    }
}

suspend fun PointerInputScope.detectNestedScrollGestures(listener: NestedScrollOnTouchListener) {
    awaitEachGesture {
        logIfDebug("detectNestedScrollGestures: start")
        val down: PointerInputChange = awaitFirstDown()
        logIfDebug("detectNestedScrollGestures: get down event ${down.id}")
        listener.onTouch(
            down.id.value, TouchType.Down, down.position, down.pressure, down.uptimeMillis
        )
        if (currentEvent.isPointerUp(down.id)) {
            logIfDebug("detectNestedScrollGestures: Pointer up")
            listener.onTouch(
                down.id.value, TouchType.Cancel, down.position, 1F, down.uptimeMillis + 1
            )
            return@awaitEachGesture
        }
        var lastTouchPosition = down.position
        var lastTouchUptime = down.uptimeMillis
        var isConsumed = false
        while (true) {
            logIfDebug("detectNestedScrollGestures: await next event")
            val event = awaitPointerEvent()
            logIfDebug("detectNestedScrollGestures: get next event")
            val change = event.changes.fastFirstOrNull { it.id == down.id }
            if (change == null) {
                logIfDebug("detectNestedScrollGestures: no change")
                listener.onTouch(
                    down.id.value, TouchType.Cancel, lastTouchPosition, 1F, lastTouchUptime + 1
                )
                break
            }
            if (change.changedToUpIgnoreConsumed()) {
                logIfDebug("detectNestedScrollGestures: Pointer up ignore")
                listener.onTouch(
                    change.id.value, TouchType.Up, change.position, change.pressure,
                    change.uptimeMillis
                )
                break
            }
            val position = change.positionChange()
            if (!isConsumed && abs(position.x) <= abs(position.y)) {
                logIfDebug("detectNestedScrollGestures: Pointer invalid")
                listener.onTouch(
                    change.id.value, TouchType.Cancel, lastTouchPosition, 1F, lastTouchUptime + 1
                )
                break
            }
            isConsumed = true
            lastTouchPosition = Offset(change.position.x, down.position.y)
            lastTouchUptime = change.uptimeMillis
            listener.onTouch(
                change.id.value, TouchType.Move, lastTouchPosition, change.pressure, lastTouchUptime
            )
            change.consume()
        }
    }
}

interface NestedScrollOnTouchListener {
    fun onTouch(id: Long, type: Int, x: Float, y: Float, pressure: Float, uptimeMillis: Long)
}

fun NestedScrollOnTouchListener.onTouch(
    id: Long, type: TouchType, pos: Offset, pressure: Float, uptimeMillis: Long
) {
    onTouch(id, type.ordinal, pos.x, pos.y, pressure, uptimeMillis)
}