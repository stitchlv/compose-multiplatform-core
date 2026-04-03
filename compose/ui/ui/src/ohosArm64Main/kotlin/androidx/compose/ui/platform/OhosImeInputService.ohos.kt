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

package androidx.compose.ui.platform

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.OhosKeyEvent
import androidx.compose.ui.text.input.CommitTextCommand
import androidx.compose.ui.text.input.DeleteSurroundingTextCommand
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.FinishComposingTextCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.MoveCursorCommand
import androidx.compose.ui.text.input.SetComposingRegionCommand
import androidx.compose.ui.text.input.SetComposingTextCommand
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.ComposeController
import cnames.structs.InputMethod_AttachOptions
import cnames.structs.InputMethod_InputMethodProxy
import cnames.structs.InputMethod_TextEditorProxy
import com.bytedance.kmp.harko.HarkoScope
import com.bytedance.kmp.harko.OHLogger
import kotlin.concurrent.Volatile
import kotlin.math.max
import kotlin.math.min
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.CArrayPointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.free
import kotlinx.cinterop.get
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.cinterop.wcstr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import platform.ohos.input.IME_DIRECTION_LEFT
import platform.ohos.input.IME_DIRECTION_RIGHT
import platform.ohos.input.IME_ENTER_KEY_DONE
import platform.ohos.input.IME_ENTER_KEY_GO
import platform.ohos.input.IME_ENTER_KEY_NEWLINE
import platform.ohos.input.IME_ENTER_KEY_NEXT
import platform.ohos.input.IME_ENTER_KEY_PREVIOUS
import platform.ohos.input.IME_ENTER_KEY_SEARCH
import platform.ohos.input.IME_ENTER_KEY_SEND
import platform.ohos.input.IME_ENTER_KEY_UNSPECIFIED
import platform.ohos.input.IME_ERR_OK
import platform.ohos.input.IME_KEYBOARD_STATUS_HIDE
import platform.ohos.input.IME_TEXT_INPUT_TYPE_EMAIL_ADDRESS
import platform.ohos.input.IME_TEXT_INPUT_TYPE_NEW_PASSWORD
import platform.ohos.input.IME_TEXT_INPUT_TYPE_NUMBER
import platform.ohos.input.IME_TEXT_INPUT_TYPE_NUMBER_DECIMAL
import platform.ohos.input.IME_TEXT_INPUT_TYPE_NUMBER_PASSWORD
import platform.ohos.input.IME_TEXT_INPUT_TYPE_PHONE
import platform.ohos.input.IME_TEXT_INPUT_TYPE_TEXT
import platform.ohos.input.IME_TEXT_INPUT_TYPE_URL
import platform.ohos.input.InputMethod_EnterKeyType
import platform.ohos.input.InputMethod_TextInputType
import platform.ohos.input.OH_AttachOptions_Create
import platform.ohos.input.OH_AttachOptions_Destroy
import platform.ohos.input.OH_InputMethodController_Attach
import platform.ohos.input.OH_InputMethodController_Detach
import platform.ohos.input.OH_InputMethodProxy_HideKeyboard
import platform.ohos.input.OH_InputMethodProxy_NotifySelectionChange
import platform.ohos.input.OH_InputMethodProxy_ShowKeyboard
import platform.ohos.input.OH_TextConfig_SetEnterKeyType
import platform.ohos.input.OH_TextConfig_SetInputType
import platform.ohos.input.OH_TextConfig_SetPreviewTextSupport
import platform.ohos.input.OH_TextEditorProxy_Create
import platform.ohos.input.OH_TextEditorProxy_DeleteBackwardFunc
import platform.ohos.input.OH_TextEditorProxy_DeleteForwardFunc
import platform.ohos.input.OH_TextEditorProxy_Destroy
import platform.ohos.input.OH_TextEditorProxy_FinishTextPreviewFunc
import platform.ohos.input.OH_TextEditorProxy_GetLeftTextOfCursorFunc
import platform.ohos.input.OH_TextEditorProxy_GetRightTextOfCursorFunc
import platform.ohos.input.OH_TextEditorProxy_GetTextConfigFunc
import platform.ohos.input.OH_TextEditorProxy_GetTextIndexAtCursorFunc
import platform.ohos.input.OH_TextEditorProxy_HandleExtendActionFunc
import platform.ohos.input.OH_TextEditorProxy_HandleSetSelectionFunc
import platform.ohos.input.OH_TextEditorProxy_InsertTextFunc
import platform.ohos.input.OH_TextEditorProxy_MoveCursorFunc
import platform.ohos.input.OH_TextEditorProxy_ReceivePrivateCommandFunc
import platform.ohos.input.OH_TextEditorProxy_SendEnterKeyFunc
import platform.ohos.input.OH_TextEditorProxy_SendKeyboardStatusFunc
import platform.ohos.input.OH_TextEditorProxy_SetDeleteBackwardFunc
import platform.ohos.input.OH_TextEditorProxy_SetDeleteForwardFunc
import platform.ohos.input.OH_TextEditorProxy_SetFinishTextPreviewFunc
import platform.ohos.input.OH_TextEditorProxy_SetGetLeftTextOfCursorFunc
import platform.ohos.input.OH_TextEditorProxy_SetGetRightTextOfCursorFunc
import platform.ohos.input.OH_TextEditorProxy_SetGetTextConfigFunc
import platform.ohos.input.OH_TextEditorProxy_SetGetTextIndexAtCursorFunc
import platform.ohos.input.OH_TextEditorProxy_SetHandleExtendActionFunc
import platform.ohos.input.OH_TextEditorProxy_SetHandleSetSelectionFunc
import platform.ohos.input.OH_TextEditorProxy_SetInsertTextFunc
import platform.ohos.input.OH_TextEditorProxy_SetMoveCursorFunc
import platform.ohos.input.OH_TextEditorProxy_SetPreviewTextFunc
import platform.ohos.input.OH_TextEditorProxy_SetReceivePrivateCommandFunc
import platform.ohos.input.OH_TextEditorProxy_SetSendEnterKeyFunc
import platform.ohos.input.OH_TextEditorProxy_SetSendKeyboardStatusFunc
import platform.ohos.input.OH_TextEditorProxy_SetSetPreviewTextFunc
import platform.ohos.input.char16_tVar
import platform.ohos.node.KEY_DEL
import platform.ohos.node.OH_NATIVEXCOMPONENT_KEY_ACTION_DOWN
import platform.posix.uint16_tVar

internal class OhosImeInputService(private val id: Long) {
    companion object {
        const val TAG = "OhosImeInputService"

        // 当前预览文本
        private var curPreviewText: String? = null

        // 预览文本是否正在输入
        private val isPreviewInput: Boolean
            get() {
                return curPreviewText != null
            }

        // 结束预览
        private fun finishPreviewInput() {
            curPreviewText = null
        }

        private val deleteBackwardFunc: OH_TextEditorProxy_DeleteBackwardFunc =
            staticCFunction { _, length ->
                OHLogger.d(TAG, "OH_TextEditorProxy_DeleteBackwardFunc: $length")
                HarkoScope.launch(Dispatchers.Main.immediate) {
                    currentInputSrv?.attachData?.run {
                        for (i in 0 until length) {
                            onKeyboardEvent(
                                OhosKeyEvent(
                                    keyCode = KEY_DEL,
                                    keyUnicode = 0, // @TODO 有空写一个函数把鸿蒙 OH_NativeXComponent_KeyCode 类型转换为 unicode 码会好一点
                                    action = OH_NATIVEXCOMPONENT_KEY_ACTION_DOWN,
                                ).toComposeEvent()
                            )
                        }
                    }
                }
            }

        private val deleteForwardFunc: OH_TextEditorProxy_DeleteForwardFunc =
            staticCFunction { proxy, length ->
                OHLogger.d(TAG, "OH_TextEditorProxy_DeleteForwardFunc: $length")
            }

        private val finishTextPreviewFunc: OH_TextEditorProxy_FinishTextPreviewFunc =
            staticCFunction { proxy ->
                OHLogger.d(TAG, "OH_TextEditorProxy_FinishTextPreviewFunc")
                finishPreviewInput()
            }

        private val getLeftTextOfCursorFunc: OH_TextEditorProxy_GetLeftTextOfCursorFunc =
            staticCFunction { _, number, text, length ->
                OHLogger.d(TAG, "OH_TextEditorProxy_GetLeftTextOfCursorFunc $number")

                if (text == null || length == null) {
                    return@staticCFunction
                }
                val data = currentInputSrv?.attachData?.value ?: return@staticCFunction
                val cursorPos = data.getCursorPos() ?: return@staticCFunction
                data.text.substring(max(cursorPos - number, 0), cursorPos).also { str ->
                    str.fillChar16tArrayPointer(text)
                    length.pointed.value = str.length.toULong()
                }
            }

        private val getRightTextOfCursorFunc: OH_TextEditorProxy_GetRightTextOfCursorFunc =
            staticCFunction { _, number, text, length ->
                OHLogger.d(TAG, "OH_TextEditorProxy_GetRightTextOfCursorFunc $number")

                if (text == null || length == null) {
                    return@staticCFunction
                }
                val data = currentInputSrv?.attachData?.value ?: return@staticCFunction
                val cursorPos = data.getCursorPos() ?: return@staticCFunction
                data.text.run {
                    substring(cursorPos, min(cursorPos + number, this.length))
                }.also { str ->
                    str.fillChar16tArrayPointer(text)
                    length.pointed.value = str.length.toULong()
                }
            }

        private val getTextConfigFunc: OH_TextEditorProxy_GetTextConfigFunc =
            staticCFunction { _, config ->
                val data = currentInputSrv?.attachData
                val enterKeyType = data?.enterKeyType ?: IME_ENTER_KEY_UNSPECIFIED
                val textInputType = data?.textInputType ?: IME_TEXT_INPUT_TYPE_TEXT
                OHLogger.d(
                    TAG,
                    "OH_TextEditorProxy_GetTextConfigFunc $enterKeyType $textInputType"
                )
                OH_TextConfig_SetEnterKeyType(config, enterKeyType)
                OH_TextConfig_SetInputType(config, textInputType)
                OH_TextConfig_SetPreviewTextSupport(config, true)
            }

        private val getTextIndexAtCursorFunc: OH_TextEditorProxy_GetTextIndexAtCursorFunc =
            staticCFunction { proxy ->
                OHLogger.d(TAG, "OH_TextEditorProxy_GetTextIndexAtCursorFunc")
                0
            }

        private val handleExtendActionFunc: OH_TextEditorProxy_HandleExtendActionFunc =
            staticCFunction { proxy, action ->
                OHLogger.d(TAG, "OH_TextEditorProxy_HandleExtendActionFunc")
            }

        private val handleSetSelectionFunc: OH_TextEditorProxy_HandleSetSelectionFunc =
            staticCFunction { proxy, start, end ->
                OHLogger.d(
                    TAG,
                    "OH_TextEditorProxy_HandleSetSelectionFunc. start: $start, end: $end"
                )
            }

        private val insertTextFunc: OH_TextEditorProxy_InsertTextFunc =
            staticCFunction { proxy, text, length ->
                if (text == null) return@staticCFunction
                val prePreviewText = curPreviewText
                finishPreviewInput()
                val value = text.toKString(length.toInt())
                OHLogger.d(TAG, "OH_TextEditorProxy_InsertTextFunc. value: $value, length: $length")

                // 使用Compose协程调度器切换到UI线程
                HarkoScope.launch(Dispatchers.Main.immediate) {
                    currentInputSrv?.attachData?.run {
                        onEditCommand(listOf(
                            DeleteSurroundingTextCommand(prePreviewText?.length ?: 0, 0),
                            CommitTextCommand(value, 1)
                        ))
                    }
                }
            }

        private val moveCursorFunc: OH_TextEditorProxy_MoveCursorFunc =
            staticCFunction { proxy, direction ->
                OHLogger.d(TAG, "OH_TextEditorProxy_MoveCursorFunc $direction")
                val amount = when (direction) {
                    IME_DIRECTION_LEFT -> -1
                    IME_DIRECTION_RIGHT -> 1
                    else -> return@staticCFunction
                }
                HarkoScope.launch(Dispatchers.Main.immediate) {
                    currentInputSrv?.attachData?.run {
                        onEditCommand(listOf(MoveCursorCommand(amount)))
                    }
                }
            }

        private val receivePrivateCommandFunc: OH_TextEditorProxy_ReceivePrivateCommandFunc =
            staticCFunction { proxy, privateCommand, size ->
                OHLogger.d(TAG, "OH_TextEditorProxy_ReceivePrivateCommandFunc $size")
                0
            }

        private val sendEnterKeyFunc: OH_TextEditorProxy_SendEnterKeyFunc =
            staticCFunction { _, enterKeyType ->
                OHLogger.d(TAG, "OH_TextEditorProxy_SendEnterKeyType $enterKeyType")
                HarkoScope.launch(Dispatchers.Main.immediate) {
                    currentInputSrv?.attachData?.run {
                        onImeActionPerformed(imeOptions.imeAction)
                        if (imeOptions.imeAction == ImeAction.None) {
                            onEditCommand(listOf(
                                SetComposingRegionCommand(-1, -1),
                                SetComposingTextCommand("\n", 1),
                                FinishComposingTextCommand()
                            ))
                        }
                    }
                }
            }

        private val sendKeyboardStatusFunc: OH_TextEditorProxy_SendKeyboardStatusFunc =
            staticCFunction { proxy, keyboardStatus ->
                OHLogger.d(TAG, "OH_TextEditorProxy_SendKeyboardStatusFunc $keyboardStatus")
                if (keyboardStatus == IME_KEYBOARD_STATUS_HIDE) {
                    HarkoScope.launch(Dispatchers.Main.immediate) {
                        currentInputSrv?.detach()
                    }
                }
            }

        private val setPreviewTextFunc: OH_TextEditorProxy_SetPreviewTextFunc =
            staticCFunction { _, text, length, start, end ->
                OHLogger.d(
                    TAG,
                    "OH_TextEditorProxy_SetPreviewTextFunc. length: $length, start: $start, end: $end"
                )
                if (text == null) {
                    return@staticCFunction 1
                }

                val prePreviewText = curPreviewText
                curPreviewText = text.toKString(length.toInt())
                OHLogger.d(TAG, "OH_TextEditorProxy_SetPreviewTextFunc. preValue: $prePreviewText curValue: $curPreviewText")
                // delete old preview text and set new preview text
                HarkoScope.launch(Dispatchers.Main.immediate) {
                    curPreviewText?.let {
                        currentInputSrv?.attachData?.run {
                            onEditCommand(
                                listOf(
                                    DeleteSurroundingTextCommand(prePreviewText?.length ?: 0, 0),
                                    SetComposingRegionCommand(start, end),
                                    SetComposingTextCommand(it, 1),
                                    FinishComposingTextCommand(),
                                )
                            )
                        }
                    }
                }
                0
            }

        private val ImeAction.ohosEnterKeyType: InputMethod_EnterKeyType
            get() = when (this@ohosEnterKeyType) {
                ImeAction.None -> IME_ENTER_KEY_NEWLINE
                ImeAction.Go -> IME_ENTER_KEY_GO
                ImeAction.Search -> IME_ENTER_KEY_SEARCH
                ImeAction.Send -> IME_ENTER_KEY_SEND
                ImeAction.Previous -> IME_ENTER_KEY_PREVIOUS
                ImeAction.Next -> IME_ENTER_KEY_NEXT
                ImeAction.Done -> IME_ENTER_KEY_DONE
                else -> IME_ENTER_KEY_UNSPECIFIED
            }

        private val KeyboardType.ohosInputType: InputMethod_TextInputType
            get() = when (this@ohosInputType) {
                KeyboardType.Text, KeyboardType.Ascii -> IME_TEXT_INPUT_TYPE_TEXT
                KeyboardType.Number -> IME_TEXT_INPUT_TYPE_NUMBER
                KeyboardType.Phone -> IME_TEXT_INPUT_TYPE_PHONE
                KeyboardType.Uri -> IME_TEXT_INPUT_TYPE_URL
                KeyboardType.Email -> IME_TEXT_INPUT_TYPE_EMAIL_ADDRESS
                KeyboardType.Password -> IME_TEXT_INPUT_TYPE_NEW_PASSWORD
                KeyboardType.NumberPassword -> IME_TEXT_INPUT_TYPE_NUMBER_PASSWORD
                KeyboardType.Decimal -> IME_TEXT_INPUT_TYPE_NUMBER_DECIMAL
                else -> IME_TEXT_INPUT_TYPE_TEXT
            }

        private val currentInputSrvRef: AtomicRef<OhosImeInputService?> = atomic(null)
        private val currentInputSrv get() = currentInputSrvRef.value

        private fun TextFieldValue.getCursorPos(): Int? {
            val selection = this.selection
            if (selection.start == selection.end) {
                return selection.start
            }
            return null
        }
    }

    private var inputMethodProxy: CPointerVar<InputMethod_InputMethodProxy>? = null
        set(value) {
            field = value
            OHLogger.d(TAG, "inputMethodProxy: set ${value?.rawPtr}")
        }
    private var attachOptions: CPointer<InputMethod_AttachOptions>? = null
    private var textEditorProxy: CPointer<InputMethod_TextEditorProxy>? = null
    private var isDetaching = false

    private class IMEAttachData(
        @Volatile var value: TextFieldValue,
        val imeOptions: ImeOptions,
        val onEditCommand: (List<EditCommand>) -> Unit,
        val onImeActionPerformed: (ImeAction) -> Unit,
        val onKeyboardEvent: (keyEvent: KeyEvent) -> Boolean
    ) {
        val enterKeyType: InputMethod_EnterKeyType
            get() = imeOptions.imeAction.ohosEnterKeyType

        val textInputType: InputMethod_TextInputType
            get() = imeOptions.keyboardType.ohosInputType
    }

    @Volatile
    private var attachData: IMEAttachData? = null

    init {
        attachOptions = OH_AttachOptions_Create(true)
        textEditorProxy = OH_TextEditorProxy_Create()
        OH_TextEditorProxy_SetDeleteBackwardFunc(textEditorProxy, deleteBackwardFunc)
        OH_TextEditorProxy_SetDeleteForwardFunc(textEditorProxy, deleteForwardFunc)
        OH_TextEditorProxy_SetFinishTextPreviewFunc(textEditorProxy, finishTextPreviewFunc)
        OH_TextEditorProxy_SetGetLeftTextOfCursorFunc(textEditorProxy, getLeftTextOfCursorFunc)
        OH_TextEditorProxy_SetGetRightTextOfCursorFunc(textEditorProxy, getRightTextOfCursorFunc)
        OH_TextEditorProxy_SetGetTextConfigFunc(textEditorProxy, getTextConfigFunc)
        OH_TextEditorProxy_SetGetTextIndexAtCursorFunc(textEditorProxy, getTextIndexAtCursorFunc)
        OH_TextEditorProxy_SetHandleExtendActionFunc(textEditorProxy, handleExtendActionFunc)
        OH_TextEditorProxy_SetHandleSetSelectionFunc(textEditorProxy, handleSetSelectionFunc)
        OH_TextEditorProxy_SetInsertTextFunc(textEditorProxy, insertTextFunc)
        OH_TextEditorProxy_SetMoveCursorFunc(textEditorProxy, moveCursorFunc)
        OH_TextEditorProxy_SetReceivePrivateCommandFunc(textEditorProxy, receivePrivateCommandFunc)
        OH_TextEditorProxy_SetSendEnterKeyFunc(textEditorProxy, sendEnterKeyFunc)
        OH_TextEditorProxy_SetSendKeyboardStatusFunc(textEditorProxy, sendKeyboardStatusFunc)
        OH_TextEditorProxy_SetSetPreviewTextFunc(textEditorProxy, setPreviewTextFunc)
    }

    fun attach(
        value: TextFieldValue,
        imeOptions: ImeOptions,
        onEditCommand: (List<EditCommand>) -> Unit,
        onImeActionPerformed: (ImeAction) -> Unit,
        onKeyboardEvent: (keyEvent: KeyEvent) -> Boolean,
    ) {
        OHLogger.d(TAG, "attach: $id ${imeOptions.keyboardType} ${imeOptions.imeAction}")
        attachData = IMEAttachData(
            value, imeOptions, onEditCommand, onImeActionPerformed, onKeyboardEvent
        )
        attach()
        OHLogger.d(TAG, "attach: over")
    }

    private fun attach() {
        OHLogger.d(TAG, "attach: inner $id")
        if (currentInputSrv === this) {
            OHLogger.d(TAG, "attach: duplicately attach, ignored $id")
            return
        }
        while (!currentInputSrvRef.compareAndSet(null, this)) {
            currentInputSrv?.detach()
        }

        inputMethodProxy = nativeHeap.alloc<CPointerVar<InputMethod_InputMethodProxy>>()
        if (inputMethodProxy == null) return

        OH_InputMethodController_Attach(textEditorProxy, attachOptions, inputMethodProxy?.ptr).let {
            OHLogger.d(TAG, "attach: $id ret $it")
            if (it != IME_ERR_OK) {
                return OHLogger.d(TAG, "attach: OH_InputMethodController_Attach failed $it")
            }
            ComposeController.onInputFocus(id)
        }
    }

    fun detach() {
        OHLogger.d(TAG, "detach $id $isDetaching")
        if (inputMethodProxy == null || isDetaching) return

        isDetaching = true
        inputMethodProxy?.let {
            OH_InputMethodController_Detach(it.value).let { ret ->
                OHLogger.d(TAG, "detach: OH_InputMethodController_Detach ret $ret")
                if (ret != IME_ERR_OK) {
                    OHLogger.w(TAG, "OH_InputMethodController_Detach failed $ret")
                }
            }
            nativeHeap.free(it)
            inputMethodProxy = null
        }
        currentInputSrvRef.compareAndSet(this, null)

        isDetaching = false
        OHLogger.d(TAG, "detach: $id over")
    }

    fun dispose() {
        OHLogger.d(TAG, "dispose $id")
        detach()
        attachOptions?.let {
            OH_AttachOptions_Destroy(it)
            attachOptions = null
        }
        textEditorProxy?.let {
            OH_TextEditorProxy_Destroy(it)
            textEditorProxy = null
        }
    }

    fun showKeyBoard() {
        OHLogger.d(TAG, "showKeyBoard start $id")
        inputMethodProxy?.let {
            OH_InputMethodProxy_ShowKeyboard(it.value).let { ret ->
                if (ret != IME_ERR_OK) {
                    OHLogger.w(TAG, "showKeyBoard: failed $ret")
                }
            }
        } ?: attachData?.let {
            OHLogger.d(TAG, "showKeyBoard: start attach")
            attach()
        } ?: run {
            OHLogger.w(TAG, "showKeyBoard: not attach and no attach data, maybe wrong")
        }
        OHLogger.d(TAG, "showKeyBoard end")
    }

    fun hideKeyBoard() {
        OHLogger.d(TAG, "hideKeyBoard")
        inputMethodProxy?.let {
            OH_InputMethodProxy_HideKeyboard(it.value).let { ret ->
                if (ret != IME_ERR_OK) {
                    OHLogger.w(TAG, "hideKeyBoard: failed $ret")
                }
            }
        }
    }

    fun updateKeyBoardState(nextValue: TextFieldValue) {
        attachData?.let { it ->
            it.value = nextValue
        }
        inputMethodProxy?.let {
            val text = nextValue.text
            val selection = nextValue.selection
            OH_InputMethodProxy_NotifySelectionChange(
                it.value,
                text.wcstr,
                text.length.toULong(),
                selection.start,
                selection.end
            )
        }
    }
}

fun String.fillChar16tArrayPointer(arrayPtr: CArrayPointer<uint16_tVar>) {
    val str = this
    if (str.isNotEmpty()) {
        str.usePinned {
            val ptr = it.addressOf(0).reinterpret<UShortVar>()
            for (i in str.indices) {
                arrayPtr[i] = ptr[i]
            }
        }
    }
}

fun CArrayPointer<char16_tVar>.toKString(textLen: Int): String {
    val cStr = this
    val charArray = CharArray(textLen)
    for (i in 0 until textLen) {
        charArray[i] = cStr[i].toInt().toChar()
    }
    return charArray.concatToString()
}