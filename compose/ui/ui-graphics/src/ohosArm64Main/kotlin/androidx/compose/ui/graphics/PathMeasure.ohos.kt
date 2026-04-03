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
import com.bytedance.kmp.harko.skia.PathMeasure as NativePathMeasure

internal class HarkoPathMeasure(val native: NativePathMeasure = NativePathMeasure()) : PathMeasure {
    override fun setPath(path: Path?, forceClosed: Boolean) {
        native.setPath(path?.toHarko(), forceClosed)
    }

    override fun getSegment(
        startDistance: Float, stopDistance: Float, destination: Path, startWithMoveTo: Boolean
    ) = native.getSegment(
        startDistance, stopDistance, destination.toHarko(), startWithMoveTo
    )

    override val length: Float
        get() = native.length

    override fun getPosition(
        distance: Float
    ): Offset {
        val result = native.getPosition(distance)
        return if (result != null) {
            Offset(result.x, result.y)
        } else {
            Offset.Unspecified
        }
    }

    override fun getTangent(
        distance: Float
    ): Offset {
        val result = native.getTangent(distance)
        return if (result != null) {
            Offset(result.x, result.y)
        } else {
            Offset.Unspecified
        }
    }
}

fun PathMeasure.toHarko(): NativePathMeasure = (this as HarkoPathMeasure).native

actual fun PathMeasure(): PathMeasure = HarkoPathMeasure()
