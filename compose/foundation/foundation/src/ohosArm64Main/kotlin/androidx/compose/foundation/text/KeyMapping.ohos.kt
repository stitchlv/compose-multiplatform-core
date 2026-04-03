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

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent

// each platform can define its own key mapping, on Android its just defaultKeyMapping, but on
// desktop, the value depends on the current OS
internal actual val platformDefaultKeyMapping = object : KeyMapping {
    override fun map(event: KeyEvent): KeyCommand? = defaultKeyMapping.map(event)
}

internal actual object MappedKeys {
    actual val A: Key = Key(Key.A.keyCode)
    actual val C: Key = Key(Key.C.keyCode)
    actual val H: Key = Key(Key.H.keyCode)
    actual val V: Key = Key(Key.V.keyCode)
    actual val X: Key = Key(Key.X.keyCode)
    actual val Y: Key = Key(Key.Y.keyCode)
    actual val Z: Key = Key(Key.Z.keyCode)
    actual val Backslash: Key = Key(Key.Backslash.keyCode)
    actual val DirectionLeft: Key = Key(Key.DirectionLeft.keyCode)
    actual val DirectionRight: Key = Key(Key.DirectionRight.keyCode)
    actual val DirectionUp: Key = Key(Key.DirectionUp.keyCode)
    actual val DirectionDown: Key = Key(Key.DirectionDown.keyCode)
    actual val PageUp: Key = Key(Key.PageUp.keyCode)
    actual val PageDown: Key = Key(Key.PageDown.keyCode)
    actual val MoveHome: Key = Key(Key.MoveHome.keyCode)
    actual val MoveEnd: Key = Key(Key.MoveEnd.keyCode)
    actual val Insert: Key = Key(Key.Insert.keyCode)
    actual val Enter: Key = Key(Key.Enter.keyCode)
    actual val NumPadEnter: Key = Key(Key.NumPadEnter.keyCode)
    actual val Backspace: Key = Key(Key.Backspace.keyCode)
    actual val Delete: Key = Key(Key.Delete.keyCode)
    actual val Paste: Key = Key(Key.Paste.keyCode)
    actual val Cut: Key = Key(Key.Cut.keyCode)
    actual val Tab: Key = Key(Key.Tab.keyCode)
    actual val Copy: Key = Key(Key.Copy.keyCode)
}