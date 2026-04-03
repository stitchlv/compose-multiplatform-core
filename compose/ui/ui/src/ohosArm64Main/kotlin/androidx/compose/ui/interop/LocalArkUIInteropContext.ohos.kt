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

package androidx.compose.ui.interop

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

internal class ArkUIInteropContext(val requestRedraw: () -> Unit, val isDestroy: () -> Boolean) {

    private val lock = reentrantLock()
    private var actions = mutableListOf<() -> Unit>()

    fun deferAction(action: () -> Unit) {
        if (isDestroy()) {
            action()
        } else {
            requestRedraw()
            lock.withLock {
                actions.add(action)
            }
        }
    }

    fun retrieve(): List<() -> Unit> {
        return lock.withLock {
            val result = actions
            actions = mutableListOf<() -> Unit>()
            result
        }
    }
}

internal val LocalArkUIInteropContext = staticCompositionLocalOf<ArkUIInteropContext> {
    error("CompositionLocal ArkUIInteropContext not provided")
}