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

package androidx.compose.ui.util

import platform.ohos.hitrace.OH_HiTrace_FinishTrace
import platform.ohos.hitrace.OH_HiTrace_StartTrace
import kotlin.native.concurrent.ThreadLocal
actual inline fun <T> trace(sectionName: String, block: () -> T): T {
    OhosTraceDelegate.start(sectionName)
    try {
        return block()
    } finally {
        OhosTraceDelegate.end()
    }
}

@ThreadLocal
object OhosTraceDelegate {
    private var traceStack: ArrayDeque<TraceEventData> = ArrayDeque()

    fun start(sectionName: String) {
        if (KPerfComposeConfig.enableTraceDelegate) {
            this.traceStack.addLast(TraceEventData(sectionName, kotlin.system.getTimeNanos()))
        }
        OH_HiTrace_StartTrace(sectionName)
    }

    fun end() {
        if (KPerfComposeConfig.enableTraceDelegate) {
            if (traceStack.isNotEmpty()) {
                val data = traceStack.removeLastOrNull()
                if (data != null) {
                    data.durationNs += kotlin.system.getTimeNanos() - data.startTime
                    TraceObserver.notify(data)
                }
            }

        }
        OH_HiTrace_FinishTrace()
    }
}