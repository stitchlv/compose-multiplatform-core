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

package androidx.compose.ui.graphics

import com.bytedance.kmp.harko.skia.PathEffect as NativePathEffect

internal class HarkoPathEffect(val native: NativePathEffect) : PathEffect

fun PathEffect.toHarko(): NativePathEffect =
    (this as HarkoPathEffect).native

internal actual fun actualCornerPathEffect(radius: Float): PathEffect =
    HarkoPathEffect(NativePathEffect.makeCorner(radius))

internal actual fun actualDashPathEffect(intervals: FloatArray, phase: Float): PathEffect =
    HarkoPathEffect(NativePathEffect.makeDash(intervals, phase))

internal actual fun actualChainPathEffect(outer: PathEffect, inner: PathEffect): PathEffect =
    HarkoPathEffect(outer.toHarko().makeCompose(inner.toHarko()))

internal actual fun actualStampedPathEffect(
    shape: Path, advance: Float, phase: Float, style: StampedPathEffectStyle
): PathEffect = HarkoPathEffect(
    NativePathEffect.makePath1D(
        shape.toHarko(), advance, phase, style.toHarko()
    )
)

internal fun StampedPathEffectStyle.toHarko(): NativePathEffect.Style = when (this) {
    StampedPathEffectStyle.Morph -> NativePathEffect.Style.MORPH
    StampedPathEffectStyle.Rotate -> NativePathEffect.Style.ROTATE
    StampedPathEffectStyle.Translate -> NativePathEffect.Style.TRANSLATE
    else -> NativePathEffect.Style.TRANSLATE
}