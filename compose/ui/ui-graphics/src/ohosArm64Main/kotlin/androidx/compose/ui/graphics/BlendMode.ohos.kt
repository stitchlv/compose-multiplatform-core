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

actual fun BlendMode.isSupported(): Boolean = true

typealias HarkoBlendMode = com.bytedance.kmp.harko.skia.BlendMode

internal fun BlendMode.toHarko() = when (this) {
    BlendMode.Clear -> HarkoBlendMode.CLEAR
    BlendMode.Src -> HarkoBlendMode.SRC
    BlendMode.Dst -> HarkoBlendMode.DST
    BlendMode.SrcOver -> HarkoBlendMode.SRC_OVER
    BlendMode.DstOver -> HarkoBlendMode.DST_OVER
    BlendMode.SrcIn -> HarkoBlendMode.SRC_IN
    BlendMode.DstIn -> HarkoBlendMode.DST_IN
    BlendMode.SrcOut -> HarkoBlendMode.SRC_OUT
    BlendMode.DstOut -> HarkoBlendMode.DST_OUT
    BlendMode.SrcAtop -> HarkoBlendMode.SRC_ATOP
    BlendMode.DstAtop -> HarkoBlendMode.DST_ATOP
    BlendMode.Xor -> HarkoBlendMode.XOR
    BlendMode.Plus -> HarkoBlendMode.PLUS
    BlendMode.Modulate -> HarkoBlendMode.MODULATE
    BlendMode.Screen -> HarkoBlendMode.SCREEN
    BlendMode.Overlay -> HarkoBlendMode.OVERLAY
    BlendMode.Darken -> HarkoBlendMode.DARKEN
    BlendMode.Lighten -> HarkoBlendMode.LIGHTEN
    BlendMode.ColorDodge -> HarkoBlendMode.COLOR_DODGE
    BlendMode.ColorBurn -> HarkoBlendMode.COLOR_BURN
    BlendMode.Hardlight -> HarkoBlendMode.HARD_LIGHT
    BlendMode.Softlight -> HarkoBlendMode.SOFT_LIGHT
    BlendMode.Difference -> HarkoBlendMode.DIFFERENCE
    BlendMode.Exclusion -> HarkoBlendMode.EXCLUSION
    BlendMode.Multiply -> HarkoBlendMode.MULTIPLY
    BlendMode.Hue -> HarkoBlendMode.HUE
    BlendMode.Saturation -> HarkoBlendMode.SATURATION
    BlendMode.Color -> HarkoBlendMode.COLOR
    BlendMode.Luminosity -> HarkoBlendMode.LUMINOSITY
    // Always fallback to default blendmode of src over
    else -> HarkoBlendMode.SRC_OVER
}