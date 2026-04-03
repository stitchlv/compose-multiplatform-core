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

// 路径：androidx/compose/runtime/RecompositionEventManager.kt
package androidx.compose.runtime


import kotlin.concurrent.Volatile
import kotlin.native.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


object RecompositionEventManager {

    private val lock = createSynchronizedObject()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())


    private val listeners = mutableListOf<WeakReference<RecompositionUpdateListener>>()


    var lastMethodKey: Int = 0

    @Volatile
    private var hasListeners: Boolean = false

    private val
        updateFlow = MutableSharedFlow<MutableMap<RecompositionKey, RecompositionStats>>(
        extraBufferCapacity = 100
    )

    init {
        coroutineScope.launch {
            updateFlow.collectLatest { map ->
                notifyListeners(statsCache)
            }
        }
    }

    internal val statsCache = ObservableMap(
        onUpdate = { map, key, value ->
            updateFlow.tryEmit(map)
        },
    )


    /**
     * App 模块调用：注册监听（Native 平台需传入强引用，内部包装为弱引用）
     */
    fun registerListener(listener: RecompositionUpdateListener) {
        synchronized(lock) {
            listeners.removeAll { it.get() == null }
            listeners.add(WeakReference(listener))
            hasListeners = listeners.any { it.get() != null }
        }
    }

    /**
     * App 模块调用：解绑监听
     */
    fun unregisterListener(listener: RecompositionUpdateListener) {
        synchronized(lock) {
            listeners.removeAll { it.get() == listener || it.get() == null }
            hasListeners = listeners.any { it.get() != null }
        }
    }

    fun updateStatsWhenReSartGroup(key: Int, anchorId: Int) {
        synchronized(lock) {
            val recompositionKey = RecompositionKey(key, anchorId)
            lastMethodKey = key
            val current = statsCache[recompositionKey]
            val updated = if (current == null) {
                RecompositionStats(count = 1, skips = 0)
            } else {
                current.copy(count = current.count + 1)
            }
            statsCache[recompositionKey] = updated
        }

    }

    fun updateStatsWhenSkip(anchorId: Int) {
        synchronized(lock) {
            val recompositionKey = RecompositionKey(lastMethodKey, anchorId)
            val current = statsCache[recompositionKey]
            val updated = if (current == null) {
                RecompositionStats(count = 0, skips = 1)
            } else {
                current.copy(count = current.count - 1, skips = current.skips + 1)
            }
            statsCache[recompositionKey] = updated
        }
    }


    private fun notifyListeners(map: MutableMap<RecompositionKey, RecompositionStats>) {
        val valid = listeners.mapNotNull { it.get() }
        valid.forEach { listener ->
            try {
                listener.onRecompositionUpdated(map)
            } catch (_: Exception) {
            }
        }
    }


    internal fun clearCache() {
        synchronized(lock) {
            statsCache.clear()
        }
    }

}


internal class ObservableMap<K, V>(
    private val map: MutableMap<K, V> = mutableMapOf(),
    private val onUpdate: (map: MutableMap<K, V>, key: K, value: V?) -> Unit
) : MutableMap<K, V> by map {
    override fun put(key: K, value: V): V? {
        val oldValue = map.put(key, value)
        onUpdate(map, key, value)
        return oldValue
    }

    override fun remove(key: K): V? {
        val oldValue = map.remove(key)
        onUpdate(map, key, null)
        return oldValue
    }

    override fun putAll(from: Map<out K, V>) {
        from.forEach { (key, value) -> onUpdate(map, key, value) }
        map.putAll(from)
    }

    override fun clear() {
        map.keys.forEach { key -> onUpdate(map, key, null) }
        map.clear()
    }
}
