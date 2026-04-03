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

package androidx.compose.ui

import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException

internal actual fun areObjectsOfSameType(a: Any, b: Any): Boolean {
    return a::class == b::class
}

internal actual fun InspectorInfo.tryPopulateReflectively(
    element: ModifierNodeElement<*>
) {
}

internal actual abstract class PlatformOptimizedCancellationException actual constructor(
    message: String?
) : CancellationException(message)

private val threadCounter = atomic(0L)

@kotlin.native.concurrent.ThreadLocal
private var threadId: Long = threadCounter.addAndGet(1)

internal actual fun getCurrentThreadId(): Long = threadId