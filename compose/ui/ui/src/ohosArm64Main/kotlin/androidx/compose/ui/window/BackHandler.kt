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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import com.bytedance.kmp.harko.OHLogger

private const val TAG = "BackHandler"

internal fun interface UIViewBackPressedDelegate {
    fun onBackPressed(): Boolean
}

internal val BackPressedHandlers = staticCompositionLocalOf<BackPressedHandlerCollection?> {
    OHLogger.i(TAG, "BackPressedHandlers: default")
    null
}

internal class BackPressedHandlerCollection {
    private val headHandler: BackPressHandler = BackPressHandler { false }

    fun addCallback(handler: BackPressHandler) {
        OHLogger.i(TAG, "addCallback: start")
        handler.front = headHandler.front
        handler.next = headHandler
        headHandler.front.next = handler
        headHandler.front = handler
        OHLogger.i(TAG, "addCallback: end")
    }

    fun foreach(block: (UIViewBackPressedDelegate) -> Boolean): Boolean {
        OHLogger.i(TAG, "foreach: start")
        var current: BackPressHandler = headHandler.next
        while (current !== headHandler) {
            if (block(current.callback)) {
                OHLogger.i(TAG, "foreach: block")
                return true
            }
            current = current.next
        }
        OHLogger.i(TAG, "foreach: end")
        return false
    }
}

internal class BackPressHandler(val callback: UIViewBackPressedDelegate) {
    var front: BackPressHandler = this
    var next: BackPressHandler = this

    fun remove() {
        OHLogger.i(TAG, "remove: start")
        front.next = next
        next.front = front
        front = this
        next = this
        OHLogger.i(TAG, "remove: end")
    }
}

@Composable
fun BackHandler(onBack: () -> Boolean) {
    OHLogger.i(TAG, "BackHandler: start")
    val currentOnBack by rememberUpdatedState(onBack)
    val backCallback: BackPressHandler = remember {
        BackPressHandler(currentOnBack)
    }
    val backPressHandler = BackPressedHandlers.current ?: return
    DisposableEffect(backPressHandler, backCallback) {
        backPressHandler.addCallback(backCallback)
        onDispose {
            backCallback.remove()
        }
    }
}
