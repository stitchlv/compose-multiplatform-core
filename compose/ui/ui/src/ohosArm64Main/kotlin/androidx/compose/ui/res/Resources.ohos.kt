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

package androidx.compose.ui.res

/*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalDensity
import org.jetbrains.skia.Data
import org.jetbrains.skia.svg.SVGDOM
import org.jetbrains.skiko.EmptyImage
import org.jetbrains.skiko.OHLogger
import org.jetbrains.skiko.loadImageData
import org.jetbrains.skiko.loadSvgData
import org.jetbrains.skiko.ohos.MediaFileHandle
import org.jetbrains.skiko.ohos.OhosFileHandle
import org.jetbrains.skiko.ohos.RawFileHandle

private const val TAG = "Resources"

enum class ResourcesType(internal val fileHandleCreator: (String) -> OhosFileHandle) {
    RAW({
        RawFileHandle(it)
    }),
    MEDIA({
        MediaFileHandle(it)
    })
}

@Composable
fun painterSvgResource(name: String, type: ResourcesType): Painter {
    val density = LocalDensity.current
    return remember(density, name, type) {
        val fileHandle = type.fileHandleCreator(name)
        val svgDom: SVGDOM = loadSvgData(fileHandle) ?: run {
            OHLogger.w(TAG, "painterSvgResource: read failed $name $type")
            SVGDOM(Data.makeEmpty())
        }
        SVGPainter(svgDom, density)
    }
}

@Composable
fun painterBitmapResource(name: String, type: ResourcesType): Painter {
    val image = remember(name, type) {
        val fileHandle = type.fileHandleCreator(name)
        val image = loadImageData(fileHandle) ?: run {
            OHLogger.w(TAG, "painterBitmapResource: read failed $name $type")
            EmptyImage
        }
        image.toComposeImageBitmap()
    }
    return BitmapPainter(image)
}

@Composable
fun rawPainterResource(name: String): Painter {
    return if (name.endsWith(".svg", true)) {
        painterSvgResource(name, ResourcesType.RAW)
    } else {
        painterBitmapResource(name, ResourcesType.RAW)
    }
}
*/