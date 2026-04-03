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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bytedance.kmp.harko.skia.Rect

@InternalComposeApi
val OhosWindowInsetsLocal = compositionLocalOf { OhosWindowInsets() }

data class OhosWindowInsets(
    val safeArea: PlatformInsets = PlatformInsets.Zero,
    val displayCutout: PlatformInsets = PlatformInsets.Zero,
    val systemGestures: PlatformInsets = PlatformInsets.Zero,
)

fun Triple<Rect, Rect, Rect>.asWindowInsets(density: Density): OhosWindowInsets {
    return OhosWindowInsets(
        safeArea = first.asPlatformInsets(density),
        displayCutout = second.asPlatformInsets(density),
        systemGestures = third.asPlatformInsets(density)
    )
}

private fun Rect.asPlatformInsets(density: Density): PlatformInsets {
    return with(density) {
        PlatformInsets(
            top = top.toDp(),
            bottom = bottom.toDp(),
            left = left.toDp(),
            right = right.toDp()
        )
    }
}

@ExperimentalComposeUiApi
@Immutable
class PlatformInsets(
    @Stable
    val left: Dp = 0.dp,
    @Stable
    val top: Dp = 0.dp,
    @Stable
    val right: Dp = 0.dp,
    @Stable
    val bottom: Dp = 0.dp,
) {
    companion object {
        val Zero = PlatformInsets(0.dp, 0.dp, 0.dp, 0.dp)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlatformInsets) return false

        if (left != other.left) return false
        if (top != other.top) return false
        if (right != other.right) return false
        if (bottom != other.bottom) return false

        return true
    }

    override fun hashCode(): Int {
        var result = left.hashCode()
        result = 31 * result + top.hashCode()
        result = 31 * result + right.hashCode()
        result = 31 * result + bottom.hashCode()
        return result
    }

    override fun toString(): String {
        return "PlatformInsets(left=$left, top=$top, right=$right, bottom=$bottom)"
    }
}

internal fun PlatformInsets.union(insets: PlatformInsets) = PlatformInsets(
    left = maxOf(left, insets.left),
    top = maxOf(top, insets.top),
    right = maxOf(right, insets.right),
    bottom = maxOf(bottom, insets.bottom)
)

internal interface InsetsConfig {

    val safeInsets: PlatformInsets
        @Composable get

    val ime: PlatformInsets
        @Composable get

    @Composable
    fun excludeInsets(
        safeInsets: Boolean,
        ime: Boolean,
        content: @Composable () -> Unit
    )
}

@OptIn(InternalComposeApi::class)
internal var PlatformInsetsConfig: InsetsConfig = object : InsetsConfig {
    override val safeInsets: PlatformInsets
        @Composable get() = OhosWindowInsetsLocal.current.safeArea
    override val ime: PlatformInsets
        @Composable get() = PlatformInsets(bottom = LocalKeyboardOverlapHeight.current)

    @Composable
    override fun excludeInsets(
        safeInsets: Boolean,
        ime: Boolean,
        content: @Composable () -> Unit
    ) {
        val safeArea = OhosWindowInsetsLocal.current
        val keyboardOverlapHeight = LocalKeyboardOverlapHeight.current
        CompositionLocalProvider(
            OhosWindowInsetsLocal provides if (safeInsets) OhosWindowInsets() else safeArea,
            LocalKeyboardOverlapHeight provides if (ime) 0.dp else keyboardOverlapHeight,
            content = content
        )
    }
}
