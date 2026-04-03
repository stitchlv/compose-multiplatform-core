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


/**
 * Data class to hold information for a trace event.
 */
data class TraceEventData(
    val traceName: String, val startTime: Long = -1L, var durationNs: Long = -1L
)

data class MetaFrameData(
    val timeLeft: Long = -1L,
)

/**
 * Data class to hold timing information for a frame.
 */
data class FrameData(
    val id: String = "",
    val frameStartTimeNs: Long,
    val frameEndTimeNs: Long,
    val currentTimeNs: Long,
    val launchTimeStampMs:Long? = null,
    val metaFrameData: MetaFrameData? = null
)


/**
 * Data class to hold information for a scroll event.
 */
data class ScrollData(val isScrolling: Boolean, val isTouching: Boolean)

/**
 * Base class for monitors that manage a list of listeners and provide subscription mechanism.
 * @param C The type of the callback/listener.
 */
abstract class BaseObserver<D> {
    protected val listeners: MutableList<(D) -> Unit> = mutableListOf()

    /**
     * Adds a listener and returns a [AutoCloseable] to remove it later.
     */
    fun subscribe(callback: (D) -> Unit): AutoCloseable {
        listeners.add(callback)
        return object : AutoCloseable {
            override fun close() {
                listeners.remove(callback)
            }
        }
    }

    /**
     * Removes a specific listener.
     */
    fun unsubscribe(callback: (D) -> Unit) {
        listeners.remove(callback)
    }

    /**
     * Clears all listeners.
     */
    protected fun clearAll() {
        listeners.clear()
    }

    /**
     * Internal function to notify listeners.
     */
    fun notify(data: D) {
        listeners.forEach { it.invoke(data) }
    }
}

/**
 * 监听帧率
 */
object FrameObserver : BaseObserver<FrameData>() {
    private val frameEndListener: MutableList<(FrameData) -> Unit> = mutableListOf()
    fun notifyFrameEnd(data: FrameData) {
        frameEndListener.forEach { it(data) }
    }

    fun subScribeFrameEnd(cb: (FrameData) -> Unit): AutoCloseable {
        this.frameEndListener.add(cb)
        return object : AutoCloseable {
            override fun close() {
                frameEndListener.remove(cb)
            }
        }
    }


}

/**
 * 监听 trace
 */
object TraceObserver : BaseObserver<TraceEventData>()

/**
 * 性能采集配置开关
 */
object KPerfComposeConfig {
    var enableTraceDelegate = false
    var enableFrameMonitor = false
}

interface PreComposeProbe {
    fun isActualLaunched(): Long
}