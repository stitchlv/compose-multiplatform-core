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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastForEach
import com.bytedance.kmp.harko.impl.use
import com.bytedance.kmp.harko.skia.ClipMode
import com.bytedance.kmp.harko.skia.CubicResampler
import com.bytedance.kmp.harko.skia.FilterMipmap
import com.bytedance.kmp.harko.skia.FilterMode
import com.bytedance.kmp.harko.skia.Image
import com.bytedance.kmp.harko.skia.Matrix44
import com.bytedance.kmp.harko.skia.MipmapMode
import com.bytedance.kmp.harko.skia.Rect as NativeRect
import com.bytedance.kmp.harko.skia.RRect as NativeRRect
import com.bytedance.kmp.harko.skia.SamplingMode
import com.bytedance.kmp.harko.skia.VertexMode as NativeVertexMode

actual typealias NativeCanvas = com.bytedance.kmp.harko.skia.Canvas

internal actual fun ActualCanvas(image: ImageBitmap): Canvas {
    val bitmap = image.bitmapGetter()
    require(!bitmap.isImmutable()) {
        "Cannot draw on immutable ImageBitmap"
    }
    return HarkoCanvas(NativeCanvas(bitmap.get()))
}

fun NativeCanvas.asComposeCanvas(): Canvas = HarkoCanvas(this)

var Canvas.alphaMultiplier: Float
    get() = (this as HarkoCanvas).alphaMultiplier
    set(value) {
        (this as HarkoCanvas).alphaMultiplier = value
    }

actual val Canvas.nativeCanvas: NativeCanvas
    get() = (this as HarkoCanvas).native

internal class HarkoCanvas(val native: NativeCanvas) : Canvas {
    internal var alphaMultiplier: Float = 1.0f

    private val Paint.harko
        get() = (this as HarkoPaint).apply {
            this.alphaMultiplier = this@HarkoCanvas.alphaMultiplier
        }.nPaint

    override fun save() {
        native.save()
    }

    override fun restore() {
        native.restore()
    }

    override fun saveLayer(bounds: Rect, paint: Paint) {
        native.saveLayer(
            bounds.left, bounds.top, bounds.right, bounds.bottom, paint.harko
        )
    }

    override fun translate(dx: Float, dy: Float) {
        native.translate(dx, dy)
    }

    override fun scale(sx: Float, sy: Float) {
        native.scale(sx, sy)
    }

    override fun rotate(degrees: Float) {
        native.rotate(degrees)
    }

    override fun skew(sx: Float, sy: Float) {
        native.skew(sx, sy)
    }

    override fun concat(matrix: Matrix) {
        if (!matrix.isIdentity()) {
            native.concat(matrix.toHarko())
        }
    }

    override fun clipRect(left: Float, top: Float, right: Float, bottom: Float, clipOp: ClipOp) {
        val antiAlias = true
        native.clipRect(NativeRect.makeLTRB(left, top, right, bottom), clipOp.toHarko(), antiAlias)
    }

    override fun clipPath(path: Path, clipOp: ClipOp) {
        val antiAlias = true
        native.clipPath(path.toHarko(), clipOp.toHarko(), antiAlias)
    }

    override fun drawLine(p1: Offset, p2: Offset, paint: Paint) {
        native.drawLine(p1.x, p1.y, p2.x, p2.y, paint.harko)
    }

    override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
        native.drawRect(NativeRect.makeLTRB(left, top, right, bottom), paint.harko)
    }

    override fun drawRoundRect(
        left: Float, top: Float, right: Float, bottom: Float, radiusX: Float, radiusY: Float,
        paint: Paint
    ) {
        native.drawRRect(
            NativeRRect.makeLTRB(left, top, right, bottom, radiusX, radiusY),
            paint.harko
        )
    }

    override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
        native.drawOval(NativeRect.makeLTRB(left, top, right, bottom), paint.harko)
    }

    override fun drawCircle(center: Offset, radius: Float, paint: Paint) {
        native.drawCircle(center.x, center.y, radius, paint.harko)
    }

    override fun drawArc(
        left: Float, top: Float, right: Float, bottom: Float, startAngle: Float, sweepAngle: Float,
        useCenter: Boolean, paint: Paint
    ) {
        native.drawArc(left, top, right, bottom, startAngle, sweepAngle, useCenter, paint.harko)
    }

    override fun drawPath(path: Path, paint: Paint) {
        native.drawPath(path.toHarko(), paint.harko)
    }

    override fun drawImage(image: ImageBitmap, topLeftOffset: Offset, paint: Paint) {
        val size = Size(image.width.toFloat(), image.height.toFloat())
        drawImageRect(image, Offset.Zero, size, topLeftOffset, size, paint)
    }

    override fun drawImageRect(
        image: ImageBitmap, srcOffset: IntOffset, srcSize: IntSize, dstOffset: IntOffset,
        dstSize: IntSize, paint: Paint
    ) {
        drawImageRect(
            image,
            Offset(srcOffset.x.toFloat(), srcOffset.y.toFloat()),
            Size(srcSize.width.toFloat(), srcSize.height.toFloat()),
            Offset(dstOffset.x.toFloat(), dstOffset.y.toFloat()),
            Size(dstSize.width.toFloat(), dstSize.height.toFloat()),
            paint
        )
    }

    // TODO(demin): probably this method should be in the common Canvas
    private fun drawImageRect(
        image: ImageBitmap, srcOffset: Offset, srcSize: Size, dstOffset: Offset, dstSize: Size,
        paint: Paint
    ) {
        native.drawImageRect(
            image.toHarko(),
            NativeRect.makeXYWH(srcOffset.x, srcOffset.y, srcSize.width, srcSize.height),
            NativeRect.makeXYWH(dstOffset.x, dstOffset.y, dstSize.width, dstSize.height),
            paint.filterQuality.toHarko(),
            paint.harko,
            true
        )
    }

    override fun drawPoints(pointMode: PointMode, points: List<Offset>, paint: Paint) {
        when (pointMode) {
            // Draw a line between each pair of points, each point has at most one line
            // If the number of points is odd, then the last point is ignored.
            PointMode.Lines -> drawLines(points, paint, 2)

            // Connect each adjacent point with a line
            PointMode.Polygon -> drawLines(points, paint, 1)

            // Draw a point at each provided coordinate
            PointMode.Points -> drawPoints(points, paint)
        }
    }

    override fun enableZ() = Unit

    override fun disableZ() = Unit

    private fun drawPoints(points: List<Offset>, paint: Paint) {
        points.fastForEach { point ->
            native.drawPoint(point.x, point.y, paint.harko)
        }
    }

    /**
     * Draw lines connecting points based on the corresponding step.
     *
     * ex. 3 points with a step of 1 would draw 2 lines between the first and second points
     * and another between the second and third
     *
     * ex. 4 points with a step of 2 would draw 2 lines between the first and second and another
     * between the third and fourth. If there is an odd number of points, the last point is
     * ignored
     *
     * @see drawRawLines
     */
    private fun drawLines(points: List<Offset>, paint: Paint, stepBy: Int) {
        if (points.size >= 2) {
            for (i in 0 until points.size - 1 step stepBy) {
                val p1 = points[i]
                val p2 = points[i + 1]
                native.drawLine(p1.x, p1.y, p2.x, p2.y, paint.harko)
            }
        }
    }

    /**
     * @throws IllegalArgumentException if a non even number of points is provided
     */
    override fun drawRawPoints(pointMode: PointMode, points: FloatArray, paint: Paint) {
        if (points.size % 2 != 0) {
            throw IllegalArgumentException("points must have an even number of values")
        }
        when (pointMode) {
            PointMode.Lines -> drawRawLines(points, paint, 2)
            PointMode.Polygon -> drawRawLines(points, paint, 1)
            PointMode.Points -> drawRawPoints(points, paint, 2)
        }
    }

    private fun drawRawPoints(points: FloatArray, paint: Paint, stepBy: Int) {
        if (points.size % 2 == 0) {
            for (i in 0 until points.size - 1 step stepBy) {
                val x = points[i]
                val y = points[i + 1]
                native.drawPoint(x, y, paint.harko)
            }
        }
    }

    /**
     * Draw lines connecting points based on the corresponding step. The points are interpreted
     * as x, y coordinate pairs in alternating index positions
     *
     * ex. 3 points with a step of 1 would draw 2 lines between the first and second points
     * and another between the second and third
     *
     * ex. 4 points with a step of 2 would draw 2 lines between the first and second and another
     * between the third and fourth. If there is an odd number of points, the last point is
     * ignored
     *
     * @see drawLines
     */
    private fun drawRawLines(points: FloatArray, paint: Paint, stepBy: Int) {
        // Float array is treated as alternative set of x and y coordinates
        // x1, y1, x2, y2, x3, y3, ... etc.
        if (points.size >= 4 && points.size % 2 == 0) {
            for (i in 0 until points.size - 3 step stepBy * 2) {
                val x1 = points[i]
                val y1 = points[i + 1]
                val x2 = points[i + 2]
                val y2 = points[i + 3]
                native.drawLine(x1, y1, x2, y2, paint.harko)
            }
        }
    }

    override fun drawVertices(vertices: Vertices, blendMode: BlendMode, paint: Paint) {
        native.drawVertices(
            vertices.vertexMode.toHarko(),
            vertices.positions,
            vertices.colors,
            vertices.textureCoordinates,
            vertices.indices,
            blendMode.toHarko(),
            paint.asFrameworkPaint()
        )
    }
}

fun ClipOp.toHarko() = when (this) {
    ClipOp.Difference -> ClipMode.DIFFERENCE
    ClipOp.Intersect -> ClipMode.INTERSECT
    else -> ClipMode.INTERSECT
}

// These constants are chosen to correspond the old implementation of SkFilterQuality:
// https://github.com/google/skia/blob/1f193df9b393d50da39570dab77a0bb5d28ec8ef/src/image/SkImage.cpp#L809
// https://github.com/google/skia/blob/1f193df9b393d50da39570dab77a0bb5d28ec8ef/include/core/SkSamplingOptions.h#L86
private fun FilterQuality.toHarko(): SamplingMode = when (this) {
    FilterQuality.Low -> FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE)
    FilterQuality.Medium -> FilterMipmap(FilterMode.LINEAR, MipmapMode.NEAREST)
    FilterQuality.High -> CubicResampler(1 / 3.0f, 1 / 3.0f)
    else -> FilterMipmap(FilterMode.NEAREST, MipmapMode.NONE)
}

private fun Matrix.toHarko() = Matrix44(
    this[0, 0],
    this[1, 0],
    this[2, 0],
    this[3, 0],

    this[0, 1],
    this[1, 1],
    this[2, 1],
    this[3, 1],

    this[0, 2],
    this[1, 2],
    this[2, 2],
    this[3, 2],

    this[0, 3],
    this[1, 3],
    this[2, 3],
    this[3, 3]
)

internal fun VertexMode.toHarko(): NativeVertexMode = when (this) {
    VertexMode.Triangles -> NativeVertexMode.TRIANGLES
    VertexMode.TriangleStrip -> NativeVertexMode.TRIANGLE_STRIP
    VertexMode.TriangleFan -> NativeVertexMode.TRIANGLE_FAN
    else -> NativeVertexMode.TRIANGLES
}
