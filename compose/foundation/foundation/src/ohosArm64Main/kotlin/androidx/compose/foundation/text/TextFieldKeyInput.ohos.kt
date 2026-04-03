/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.compose.foundation.text

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint

// AWT and Android have similar but different key event models. In android there are two main
// types of events: ACTION_DOWN and ACTION_UP. In AWT there is additional KEY_TYPED which should
// be used to get "typed character". By this simple function we are introducing common
// denominator for both systems: if KeyEvent.isTypedEvent then it's safe to use
// KeyEvent.utf16CodePoint
internal actual val KeyEvent.isTypedEvent: Boolean
    get() {
        return type == KeyEventType.KeyDown && !isControl(utf16CodePoint)
    }

private fun isControl(inputCode: Int): Boolean {
    // 键入事件判断。目前把 Unicode 设置为 0 的是功能键，有对应 Unicode 值的是文本输入。
    return inputCode == 0
}