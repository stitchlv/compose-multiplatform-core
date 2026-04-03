/*
 * Copyright 2025 The Android Open Source Project
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

package androidx.compose.ui.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

private const val TAG = "FirstFrameHandler"

typealias FirstFrameCallback = () -> Unit

internal val FirstFrameHandler = staticCompositionLocalOf<FirstFrameCallbackWrapper?> { null }

internal class FirstFrameCallbackWrapper : FirstFrameCallback {
    var receivedFirstFrame: Boolean = false
        private set
    var delegate: FirstFrameCallback? = null
        set(value) {
            if (receivedFirstFrame) return
            field = value
        }

    override fun invoke() {
        if (receivedFirstFrame) return

        println("$TAG invoke:")
        receivedFirstFrame = true
        delegate?.invoke()
        delegate = null
    }
}

@Composable
fun FirstFrameHandler(onFirstFrame: FirstFrameCallback) {
    FirstFrameHandler.current?.run {
        if (receivedFirstFrame) return

        delegate = onFirstFrame
    }
}