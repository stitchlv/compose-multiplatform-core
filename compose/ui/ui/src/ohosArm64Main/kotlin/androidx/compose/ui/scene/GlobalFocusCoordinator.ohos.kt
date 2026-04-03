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

package androidx.compose.ui.scene

import androidx.compose.ui.AtomicReference
import androidx.compose.ui.platform.PlatformWindowContext
import com.bytedance.kmp.harko.OHLogger

/**
 * 全局焦点协调器，管理多个ComposeView之间的焦点状态
 * 确保同一时间只有一个ComposeView获得焦点
 */
internal object GlobalFocusCoordinator {
    private const val TAG = "GlobalFocusCoordinator"
    
    // 当前拥有焦点的ComposeView ID
    private val currentFocusedViewId = AtomicReference<Long?>(null)
    
    // 所有注册的ComposeView及其窗口上下文
    private val registeredViews = HashMap<Long, ViewFocusInfo>()
    
    private data class ViewFocusInfo(
        val windowContext: PlatformWindowContext,
        val onFocusLost: () -> Unit
    )
    
    /**
     * 注册一个ComposeView到全局焦点管理
     */
    fun registerView(
        viewId: Long,
        windowContext: PlatformWindowContext,
        onFocusLost: () -> Unit
    ) {
        OHLogger.i(TAG, "Registered view: $viewId")
        registeredViews[viewId] = ViewFocusInfo(windowContext, onFocusLost)
    }
    
    /**
     * 注销ComposeView
     */
    fun unregisterView(viewId: Long) {
        registeredViews.remove(viewId)
        if (currentFocusedViewId.get() == viewId) {
            currentFocusedViewId.set(null)
        }
        OHLogger.i(TAG, "Unregistered view: $viewId")
    }
    
    /**
     * 请求焦点，会自动清除其他ComposeView的焦点
     */
    fun requestFocus(viewId: Long): Boolean {
        val previousFocusedId = currentFocusedViewId.getAndSet(viewId)
        OHLogger.i(TAG, "requestFocus focus from view: $previousFocusedId")
        OHLogger.i(TAG, "requestFocus focus to view: $viewId")
        
        if (previousFocusedId == viewId) {
            // 已经是当前焦点视图，确保窗口焦点状态正确
            registeredViews[viewId]?.windowContext?.setWindowFocused(true)
            return true
        }
        
        // 清除之前获得焦点的视图
        previousFocusedId?.let { prevId ->
            registeredViews[prevId]?.let { viewInfo ->
                OHLogger.i(TAG, "Clearing focus from previous view: $prevId")
                viewInfo.windowContext.setWindowFocused(false)
                viewInfo.onFocusLost()
                OHLogger.i(TAG, "Cleared focus from view: $prevId")
            }
        }
        
        // 设置新的焦点视图
        registeredViews[viewId]?.let { viewInfo ->
            viewInfo.windowContext.setWindowFocused(true)
            OHLogger.i(TAG, "Granted focus to view: $viewId")
            return true
        }
        
        OHLogger.i(TAG, "Failed to grant focus to unregistered view: $viewId")
        return false
    }
    
    /**
     * 释放指定视图的焦点
     */
    fun releaseFocus(viewId: Long) {
        if (currentFocusedViewId.compareAndSet(viewId, null)) {
            registeredViews[viewId]?.let { viewInfo ->
                viewInfo.windowContext.setWindowFocused(false)
                viewInfo.onFocusLost()
                OHLogger.i(TAG, "Released focus from view: $viewId")
            }
        } else {
            OHLogger.i(TAG, "View $viewId was not the focused view, current focused: ${currentFocusedViewId.get()}")
        }
    }
    
    /**
     * 检查指定视图是否拥有焦点
     */
    fun hasFocus(viewId: Long): Boolean {
        return currentFocusedViewId.get() == viewId
    }
    
    /**
     * 获取当前拥有焦点的视图ID
     */
    fun getCurrentFocusedViewId(): Long? {
        return currentFocusedViewId.get()
    }
}