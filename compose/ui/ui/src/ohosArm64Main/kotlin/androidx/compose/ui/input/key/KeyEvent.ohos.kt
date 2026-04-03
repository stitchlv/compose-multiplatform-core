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

package androidx.compose.ui.input.key

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.isAltGraphPressed
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import platform.ohos.node.KEY_ALT_LEFT
import platform.ohos.node.KEY_ALT_RIGHT
import platform.ohos.node.KEY_CTRL_LEFT
import platform.ohos.node.KEY_CTRL_RIGHT
import platform.ohos.node.KEY_META_LEFT
import platform.ohos.node.KEY_META_RIGHT
import platform.ohos.node.KEY_SHIFT_LEFT
import platform.ohos.node.KEY_SHIFT_RIGHT
import platform.ohos.node.OH_NATIVEXCOMPONENT_KEY_ACTION_DOWN
import platform.ohos.node.OH_NATIVEXCOMPONENT_KEY_ACTION_UP
import platform.ohos.node.OH_NativeXComponent_KeyAction
import platform.ohos.node.OH_NativeXComponent_KeyCode

actual typealias NativeKeyEvent = Any

internal data class InternalKeyEvent(
    val key: Key,
    val type: KeyEventType,
    val codePoint: Int,
    val modifiers: PointerKeyboardModifiers, // Reuse pointer modifiers

    val nativeEvent: Any? = null
)

internal val KeyEvent.internal: InternalKeyEvent
    get() = nativeKeyEvent as InternalKeyEvent

actual val KeyEvent.key: Key
    get() = internal.key

actual val KeyEvent.utf16CodePoint: Int
    get() = internal.codePoint

actual val KeyEvent.type: KeyEventType
    get() = internal.type

actual val KeyEvent.isAltPressed: Boolean
    get() = internal.modifiers.isAltPressed || internal.modifiers.isAltGraphPressed

actual val KeyEvent.isCtrlPressed: Boolean
    get() = internal.modifiers.isCtrlPressed

actual val KeyEvent.isMetaPressed: Boolean
    get() = internal.modifiers.isMetaPressed

actual val KeyEvent.isShiftPressed: Boolean
    get() = internal.modifiers.isShiftPressed

@InternalComposeUiApi
fun KeyEvent(
    key: Key,
    type: KeyEventType,
    codePoint: Int = 0,
    isCtrlPressed: Boolean = false,
    isMetaPressed: Boolean = false,
    isAltPressed: Boolean = false,
    isShiftPressed: Boolean = false,
    nativeEvent: Any? = null
) = KeyEvent(
    nativeKeyEvent = InternalKeyEvent(
        key = key,
        type = type,
        codePoint = codePoint,
        modifiers = PointerKeyboardModifiers(
            isCtrlPressed = isCtrlPressed,
            isMetaPressed = isMetaPressed,
            isAltPressed = isAltPressed,
            isShiftPressed = isShiftPressed
        ),
        nativeEvent = nativeEvent
    )
)

internal fun KeyEvent.copy(
    key: Key = this.internal.key,
    type: KeyEventType = this.internal.type,
    codePoint: Int = this.internal.codePoint,
    modifiers: PointerKeyboardModifiers = this.internal.modifiers,
    nativeEvent: Any? = this.internal.nativeEvent
) = KeyEvent(
    nativeKeyEvent = InternalKeyEvent(
        key = key,
        type = type,
        codePoint = codePoint,
        modifiers = modifiers,
        nativeEvent = nativeEvent
    )
)

internal class OhosKeyEvent(
    private val keyCode: OH_NativeXComponent_KeyCode,
    private val action: OH_NativeXComponent_KeyAction,
    private val keyUnicode: Int, /* Unicode of Key Pressed.When it is Functional Key, use 0 */
    // OH_NativeXComponent_EventSourceType
    // deviceId
    // timeStamp
) {
    fun toComposeEvent(): KeyEvent {
        return KeyEvent(
            nativeKeyEvent = InternalKeyEvent(
                key = Key(keyCode),
                type = when (action) {
                    OH_NATIVEXCOMPONENT_KEY_ACTION_DOWN -> KeyEventType.KeyDown
                    OH_NATIVEXCOMPONENT_KEY_ACTION_UP -> KeyEventType.KeyUp
                    else -> KeyEventType.Unknown
                },
                codePoint = keyUnicode,
                modifiers = PointerKeyboardModifiers(
                    isAltPressed = keyCode == KEY_ALT_LEFT || keyCode == KEY_ALT_RIGHT,
                    isShiftPressed = keyCode == KEY_SHIFT_LEFT || keyCode == KEY_SHIFT_RIGHT,
                    isCtrlPressed = keyCode == KEY_CTRL_LEFT || keyCode == KEY_CTRL_RIGHT,
                    isMetaPressed = keyCode == KEY_META_LEFT || keyCode == KEY_META_RIGHT,
                ),
                nativeEvent = this
            )
        )
    }
}