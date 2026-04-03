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

import com.bytedance.kmp.harko.skia.ColorMatrix as HarkoColorMatrix

actual typealias NativeColorFilter = com.bytedance.kmp.harko.skia.ColorFilter

internal actual fun actualTintColorFilter(color: Color, blendMode: BlendMode): NativeColorFilter =
    NativeColorFilter.makeBlend(color.toArgb(), blendMode.toHarko())

internal actual fun actualColorMatrixColorFilter(colorMatrix: ColorMatrix): NativeColorFilter {
    val remappedValues = colorMatrix.values.copyOf()
    remappedValues[4] *= (1f / 255f)
    remappedValues[9] *= (1f / 255f)
    remappedValues[14] *= (1f / 255f)
    remappedValues[19] *= (1f / 255f)

    return NativeColorFilter.makeMatrix(HarkoColorMatrix(*remappedValues))
}

internal actual fun actualLightingColorFilter(multiply: Color, add: Color): NativeColorFilter =
    NativeColorFilter.makeLighting(multiply.toArgb(), add.toArgb())

internal actual fun actualColorMatrixFromFilter(filter: NativeColorFilter): ColorMatrix =
    ColorMatrix()

fun ColorFilter.toHarko(): NativeColorFilter = nativeColorFilter
