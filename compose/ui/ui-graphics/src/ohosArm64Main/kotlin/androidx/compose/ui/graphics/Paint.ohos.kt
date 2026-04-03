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

import com.bytedance.kmp.harko.skia.PaintMode
import com.bytedance.kmp.harko.skia.PaintStrokeCap
import com.bytedance.kmp.harko.skia.PaintStrokeJoin

actual typealias NativePaint = com.bytedance.kmp.harko.skia.Paint

actual fun Paint(): Paint = HarkoPaint()

/**
 * Convert the [com.bytedance.kmp.harko.skia.Paint] instance into a Compose-compatible Paint
 */
fun com.bytedance.kmp.harko.skia.Paint.asComposePaint(): Paint = HarkoPaint(this)

internal class HarkoPaint(val nPaint: NativePaint = com.bytedance.kmp.harko.skia.Paint()) : Paint {
    override fun asFrameworkPaint(): NativePaint = nPaint

    private var mAlphaMultiplier = 1.0f

    var alphaMultiplier: Float
        get() = mAlphaMultiplier
        set(value) {
            val multiplier = value.coerceIn(0f, 1f)
            updateAlpha(multiplier = multiplier)
            mAlphaMultiplier = multiplier
        }

    private fun updateAlpha(alpha: Float = this.alpha, multiplier: Float = this.mAlphaMultiplier) {
        nPaint.color = Color(nPaint.color).copy(alpha = alpha * multiplier).toArgb()
    }

    override var alpha: Float
        get() = Color(nPaint.color).alpha
        set(value) {
            updateAlpha(alpha = value)
        }

    override var isAntiAlias: Boolean
        get() = nPaint.isAntiAlias
        set(value) {
            nPaint.isAntiAlias = value
        }

    override var color: Color
        get() = Color(nPaint.color)
        set(color) {
            nPaint.color = color.toArgb()
        }

    override var blendMode: BlendMode = BlendMode.SrcOver
        set(value) {
            nPaint.blendMode = value.toHarko()
            field = value
        }

    override var style: PaintingStyle = PaintingStyle.Fill
        set(value) {
            nPaint.mode = value.toHarko()
            field = value
        }

    override var strokeWidth: Float
        get() = nPaint.strokeWidth
        set(value) {
            nPaint.strokeWidth = value
        }

    override var strokeCap: StrokeCap = StrokeCap.Butt
        set(value) {
            nPaint.strokeCap = value.toHarko()
            field = value
        }

    override var strokeJoin: StrokeJoin = StrokeJoin.Round
        set(value) {
            nPaint.strokeJoin = value.toHarko()
            field = value
        }

    override var strokeMiterLimit: Float = 0f
        set(value) {
            nPaint.strokeMiter = value
            field = value
        }

    override var filterQuality: FilterQuality = FilterQuality.Medium

    override var shader: Shader? = null
        set(value) {
            nPaint.shader = value
            field = value
        }

    override var colorFilter: ColorFilter? = null
        set(value) {
            nPaint.colorFilter = value?.toHarko()
            field = value
        }

    override var pathEffect: PathEffect? = null
        set(value) {
            nPaint.pathEffect = value?.toHarko()
            field = value
        }

    private fun PaintingStyle.toHarko() = when (this) {
        PaintingStyle.Fill -> PaintMode.FILL
        PaintingStyle.Stroke -> PaintMode.STROKE
        else -> PaintMode.FILL
    }

    private fun StrokeCap.toHarko() = when (this) {
        StrokeCap.Butt -> PaintStrokeCap.BUTT
        StrokeCap.Round -> PaintStrokeCap.ROUND
        StrokeCap.Square -> PaintStrokeCap.SQUARE
        else -> PaintStrokeCap.BUTT
    }

    private fun StrokeJoin.toHarko() = when (this) {
        StrokeJoin.Miter -> PaintStrokeJoin.MITER
        StrokeJoin.Round -> PaintStrokeJoin.ROUND
        StrokeJoin.Bevel -> PaintStrokeJoin.BEVEL
        else -> PaintStrokeJoin.MITER
    }
}