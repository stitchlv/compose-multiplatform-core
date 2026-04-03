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

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.scene.ComposeSceneMediator
import androidx.compose.ui.scene.GlobalFocusCoordinator
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.PlatformTextInputService
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.window.RenderingUIView
import com.bytedance.kmp.harko.HarkoScope
import com.bytedance.kmp.harko.OHLogger
import com.bytedance.kmp.harko.ark.NApiValue
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TextToolbarContext(
    val isShow: Boolean = false,
    var rect: Rect = Rect.Zero,
    var onCopyRequested: (() -> Unit)? = null,
    var onPasteRequested: (() -> Unit)? = null,
    var onCutRequested: (() -> Unit)? = null,
    var onSelectAllRequested: (() -> Unit)? = null,
    var onPasteResult: ((String) -> Unit)? = null,
    var arkTsTextToolBar: (() -> NApiValue?)? = null,
)

val OhosLocalTextToolbar = compositionLocalOf<TextToolbarContext> { error("OhosLocalTextToolbar not init") }

internal class OhosTextInputService(
    val onKeyboardEvent: (keyEvent: KeyEvent) -> Boolean,
    val densityProvider: () -> Density,
    var arkTsTextToolBar: (() -> NApiValue?)? = null,
    private val viewId: Long = 0,
): PlatformTextInputService, TextToolbar {
    companion object {
        const val TAG = "OhosTextInputService"

        private var currentActiveService: OhosTextInputService? = null

        // 跟踪ComposeView的焦点状态
        private val viewFocusMap = mutableMapOf<Long, Boolean>()

        private fun setActiveService(service: OhosTextInputService?) {
            currentActiveService?.let { oldService ->
                if (oldService != service) {
                    oldService.forceStopInput()
                    OHLogger.d(TAG, "Stopped previous active service: ${oldService.viewId}")
                }
            }
            currentActiveService = service
            service?.let {
                OHLogger.d(TAG, "Set active service: ${it.viewId}")
            }
        }

        /**
         * 设置ComposeView的焦点状态
         */
        fun setViewFocus(viewId: Long, hasFocus: Boolean): Boolean {
            val previousFocus = viewFocusMap[viewId] ?: false
            viewFocusMap[viewId] = hasFocus

            OHLogger.i(TAG, "setViewFocus viewId: $viewId, hasFocus: $hasFocus, previousFocus: $previousFocus")

            // 如果ComposeView失去焦点，且当前活跃服务属于该view，则停止服务
            if (!hasFocus && previousFocus && currentActiveService?.viewId == viewId) {
                OHLogger.i(TAG, "View $viewId lost focus, stopping active text input service")
                currentActiveService?.forceStopInput()
            }
            return previousFocus != hasFocus
        }

        /**
         * 检查ComposeView是否有焦点
         */
        fun hasViewFocus(viewId: Long): Boolean {
            return viewFocusMap[viewId] ?: false
        }

        private class StartInputParam(
            val value: TextFieldValue,
            val imeOptions: ImeOptions,
            val onEditCommand: (List<EditCommand>) -> Unit,
            val onImeActionPerformed: (ImeAction) -> Unit
        )
    }

    private val service = OhosImeInputService(id = viewId)
    private var isActive = false
    private var startInputParam: StartInputParam? = null

    var textToolbarContext by mutableStateOf(TextToolbarContext(arkTsTextToolBar = arkTsTextToolBar))

    fun update(
        isShow: Boolean,
        rect: Rect = Rect.Zero,
        onCopyRequested: (() -> Unit)? = null,
        onPasteRequested: (() -> Unit)? = null,
        onCutRequested: (() -> Unit)? = null,
        onSelectAllRequested: (() -> Unit)? = null,
        onPasteResult: ((String) -> Unit)? = null,
    ) {
        textToolbarContext = TextToolbarContext(isShow, rect, onCopyRequested, onPasteRequested, onCutRequested, onSelectAllRequested, onPasteResult, arkTsTextToolBar)
    }

    override fun startInput(
        value: TextFieldValue,
        imeOptions: ImeOptions,
        onEditCommand: (List<EditCommand>) -> Unit,
        onImeActionPerformed: (ImeAction) -> Unit
    ) {
        OHLogger.i(TAG, "startInput: begin $viewId")
        val mediator: ComposeSceneMediator = RenderingUIView.getMediator(viewId) ?: run {
            OHLogger.w(TAG, "startInput: no such view with id $viewId")
            return
        }
        if (!mediator.isAppeared) {
            OHLogger.i(TAG, "startInput: waiting for appeared $viewId")
            startInputParam = StartInputParam(
                value, imeOptions, onEditCommand, onImeActionPerformed
            )
            return
        }

        startInputParam = null
        // 设置当前ComposeView为有焦点状态
        if (!setViewFocus(viewId, true)) {
            OHLogger.w(TAG, "duplicately startInput for view: $viewId")
        }

        GlobalFocusCoordinator.requestFocus(viewId)

        setActiveService(this)
        isActive = true

        service.attach(value, imeOptions, onEditCommand, onImeActionPerformed, onKeyboardEvent)
    }

    fun startInputOnAppearIfNeed() {
        OHLogger.i(TAG, "startInputOnAppearIfNeed: begin $viewId ${startInputParam != null}")
        val param = startInputParam ?: return
        startInputParam = null
        HarkoScope.launch(Dispatchers.Main) {
            OHLogger.i(TAG, "startInputOnAppearIfNeed: launch $viewId")
            with(param) {
                startInput(value, imeOptions, onEditCommand, onImeActionPerformed)
            }
        }
    }

    override fun stopInput() {
        OHLogger.i(TAG, "compose stopInput for view: $viewId")
        isActive = false
        startInputParam = null

        if (currentActiveService == this) {
            service.detach()
            currentActiveService = null
        }
    }

    private fun forceStopInput() {
        OHLogger.i(TAG, "compose forceStopInput for view: $viewId")
        isActive = false
        startInputParam = null
        service.detach()
    }

    override fun showSoftwareKeyboard() {
        OHLogger.i(TAG, "compose showSoftwareKeyboard for view: $viewId")
        if (isActive && currentActiveService == this) {
            service.showKeyBoard()
        }
    }

    override fun hideSoftwareKeyboard() {
        OHLogger.i(TAG, "compose hideSoftwareKeyboard for view: $viewId")
        if (currentActiveService == this) {
            service.hideKeyBoard()
        }
    }

    override fun updateState(oldValue: TextFieldValue?, newValue: TextFieldValue) {
        OHLogger.i(TAG, "compose updateState for view: $viewId, oldValue: $oldValue, newValue: $newValue")
        if (isActive && currentActiveService == this) {
            service.updateKeyBoardState(newValue)
        }
    }

    fun dispose () {
        startInputParam = null
        if (currentActiveService == this) {
            currentActiveService = null
        }
        service.dispose()
    }

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
        onPasteResult: ((String) -> Unit)?
    ) {
        // rect 是指文本选中区域在窗口坐标系中的坐标位置，需要根据安全空间计算工具栏可以放置的位置
        OHLogger.i("OhosTextToolbar", "showMenu rect: ${rect}")
        update(
            true,
            rect = rect,
            onCopyRequested = onCopyRequested,
            onPasteRequested = onPasteRequested,
            onCutRequested = onCutRequested,
            onSelectAllRequested = onSelectAllRequested,
            onPasteResult = onPasteResult
        )
        status = TextToolbarStatus.Shown
    }

    override fun hide() {
        update(false)
        status = TextToolbarStatus.Hidden
    }

    override var status: TextToolbarStatus = TextToolbarStatus.Hidden

    private fun calculateTextToolbarOffset(rect: Rect): IntOffset {
        val selectionRect = rect.roundToIntRect()
        val margin = 60
        val density = densityProvider()
        val toolbarHeight = with(density) { 40.dp.toPx().roundToInt() }
        val topSafeArea = toolbarHeight + margin * 2
        val toolbarOffsetY = if (selectionRect.top >= topSafeArea) {
            // 工具栏在选中区域的上方出现
            selectionRect.top - (toolbarHeight + margin)
        } else {
            // 工具栏在选中区域的下方出现
            // 减去 25.dp 是因为 Compose 给 rect.bottom 加了 25.dp。源码位置：package androidx.compose.foundation.text.selection.TextFieldSelectionManager
            val composeMargin = with(density) { 25.dp.toPx().roundToInt() }
            selectionRect.bottom + margin - composeMargin
        }

        return IntOffset(selectionRect.left, toolbarOffsetY)
    }
}