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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.remember
import androidx.compose.runtime.tooling.LocalInspectionTables
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.node.RootNodeOwner
import kotlin.native.ref.WeakReference


@OptIn(ExperimentalComposeUiApi::class)
internal fun RootNodeOwner.setContent(
    parent: CompositionContext,
    getCompositionLocalContext: () -> CompositionLocalContext? = { null },
    content: @Composable () -> Unit
): Composition {
    val composition = Composition(DefaultUiApplier(owner.root), parent)
    composition.setContent {
        if (isDebugInspectorInfoEnabled) {
            this.compositionDataSet.add(currentComposer.compositionData)
            currentComposer.collectParameterInformation()
            getCompositionLocalContext().provide(LocalInspectionTables provides this.compositionDataSet) {
                ProvideCommonCompositionLocals(
                    owner = owner,
                    uriHandler = remember { PlatformUriHandler() },
                ) {
                    ProvidePlatformCompositionLocals(
                        content = content
                    )
                }
            }
        } else {
            getCompositionLocalContext().provide {
                ProvideCommonCompositionLocals(
                    owner = owner,
                    uriHandler = remember { PlatformUriHandler() },
                ) {
                    ProvidePlatformCompositionLocals(
                        content = content
                    )
                }
            }
        }

    }
    return composition
}

@Composable
private fun CompositionLocalContext?.provide(
    value: ProvidedValue<*>,
    content: @Composable () -> Unit
) {
    if (this != null) {
        CompositionLocalProvider(this, value, content = content)
    } else {
        CompositionLocalProvider(value, content = content)
    }
}

@Composable
private fun CompositionLocalContext?.provide(content: @Composable () -> Unit) {
    if (this != null) {
        CompositionLocalProvider(this, content = content)
    } else {
        content()
    }
}

@Composable
private fun ProvidePlatformCompositionLocals(
    content: @Composable () -> Unit
) {
    content()
}

//仅本地包开启isDebugInspectorInfoEnabled才会用，其他勿用。
 class WeakHashSet<T : Any> : MutableSet<T> {
    private val set = HashSet<MyRef<T>>()

    private fun cleanup() {
        val it = set.iterator()
        while (it.hasNext()) {
            if (it.next().get() == null) it.remove()
        }
    }

    override val size: Int
        get() {
            cleanup()
            return set.size
        }

    override fun contains(element: T): Boolean {
        cleanup()
        return set.contains(MyRef(element))
    }

    override fun containsAll(elements: Collection<T>): Boolean {
        cleanup()
        return elements.all { contains(it) }
    }

    override fun isEmpty(): Boolean {
        cleanup()
        return set.isEmpty()
    }

    override fun add(element: T): Boolean {
        cleanup()
        return set.add(MyRef(element))
    }

    override fun addAll(elements: Collection<T>): Boolean {
        var modified = false
        elements.forEach { if (add(it)) modified = true }
        return modified
    }

    override fun clear() {
        set.clear()
    }

    override fun iterator(): MutableIterator<T> {
        cleanup()
        val live = set.mapNotNull { it.get() }.toMutableList()
        val it = live.listIterator()
        var last: T? = null
        return object : MutableIterator<T> {
            override fun hasNext(): Boolean = it.hasNext()
            override fun next(): T {
                val v = it.next()
                last = v
                return v
            }
            override fun remove() {
                val v = last ?: throw IllegalStateException()
                it.remove()
                this@WeakHashSet.remove(v)
                last = null
            }
        }
    }

    override fun remove(element: T): Boolean {
        cleanup()
        return set.remove(MyRef(element))
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        var modified = false
        elements.forEach { if (remove(it)) modified = true }
        return modified
    }

    override fun retainAll(elements: Collection<T>): Boolean {
        cleanup()
        var modified = false
        val it = set.iterator()
        while (it.hasNext()) {
            val v = it.next().get()
            if (v == null || v !in elements) {
                it.remove()
                modified = true
            }
        }
        return modified
    }

    private class MyRef<T : Any>(element: T) {
        private val ref = WeakReference(element)
        private val hc = element.hashCode()
        fun get(): T? = ref.value
        override fun hashCode(): Int = hc
        override fun equals(other: Any?): Boolean {
            if (other !is MyRef<*>) return false
            val a = get()
            val b = other.get()
            return a != null && a == b
        }
    }
}
