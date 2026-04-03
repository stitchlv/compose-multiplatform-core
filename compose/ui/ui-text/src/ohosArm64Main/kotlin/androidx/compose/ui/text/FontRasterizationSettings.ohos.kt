/*
 * Copyright 2025 The Android Open Source Project
 *
 * Copyright (c) 2026 ByteDance Ltd. and/or its affiliates
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package androidx.compose.ui.text

import com.bytedance.kmp.harko.skia.FontHinting as SkFontHinting
import androidx.compose.ui.text.platform.Platform
import androidx.compose.ui.text.platform.currentPlatform
import com.bytedance.kmp.harko.skia.FontEdging
import com.bytedance.kmp.harko.skia.paragraph.FontRastrSettings

/**
 * Whether edge pixels draw opaque or with partial transparency.
 */
@ExperimentalTextApi
enum class FontSmoothing {
    /**
     * no transparent pixels on glyph edges
     */
    None,

    /**
     * change transparency of the pixels to fit the pixel grid
     */
    AntiAlias,

    /**
     * change transparency and color of the pixels to fit the RGB subpixel grid
     */
    SubpixelAntiAlias;
}

/**
 * Level of glyph outline adjustment
 */
@ExperimentalTextApi
enum class FontHinting {
    /**
     * glyph outlines unchanged
     */
    None,

    /**
     * minimal modification to improve constrast
     */
    Slight,

    /**
     * glyph outlines modified to improve constrast
     */
    Normal,

    /**
     * modifies glyph outlines for maximum constrast
     */
    Full;
}

@ExperimentalTextApi
class FontRasterizationSettings(
    val smoothing: FontSmoothing,
    val hinting: FontHinting,
    val subpixelPositioning: Boolean,
    val autoHintingForced: Boolean
) {
    companion object {
        val PlatformDefault by lazy {
            when (currentPlatform()) {
                Platform.Windows -> FontRasterizationSettings(
                    subpixelPositioning = true,
                    // Most UIs still use ClearType on Windows, so we should match this
                    // We temporarily disabled `SubpixelAntiAlias` until we figure out
                    // how to properly retrieve default OS settings
                    smoothing = FontSmoothing.AntiAlias,
                    hinting = FontHinting.Normal, // None would trigger some potentially unwanted behavior, but everything else is forced into Normal on Windows
                    autoHintingForced = false,
                )

                Platform.Linux, Platform.Unknown -> FontRasterizationSettings(
                    subpixelPositioning = true,
                    smoothing = FontSmoothing.AntiAlias,
                    hinting = FontHinting.Slight, // Most distributions use Slight now by default
                    autoHintingForced = false,
                )

                Platform.Android, Platform.OHOS -> FontRasterizationSettings(
                    subpixelPositioning = true,
                    smoothing = FontSmoothing.AntiAlias,
                    hinting = FontHinting.Slight,
                    autoHintingForced = false,
                )

                Platform.MacOS, Platform.IOS, Platform.TvOS, Platform.WatchOS -> FontRasterizationSettings(
                    subpixelPositioning = true,
                    smoothing = FontSmoothing.AntiAlias, // macOS doesn't support SubpixelAntiAlias anymore as of Catalina
                    hinting = FontHinting.Normal, // Completely ignored on macOS
                    autoHintingForced = false, // Completely ignored on macOS
                )
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as FontRasterizationSettings

        if (smoothing != other.smoothing) return false
        if (hinting != other.hinting) return false
        if (subpixelPositioning != other.subpixelPositioning) return false
        return autoHintingForced == other.autoHintingForced
    }

    override fun hashCode(): Int {
        var result = smoothing.hashCode()
        result = 31 * result + hinting.hashCode()
        result = 31 * result + subpixelPositioning.hashCode()
        result = 31 * result + autoHintingForced.hashCode()
        return result
    }

    override fun toString(): String {
        return "FontRasterizationSettings(smoothing=$smoothing, " +
            "hinting=$hinting, " +
            "subpixelPositioning=$subpixelPositioning, " +
            "autoHintingForced=$autoHintingForced)"
    }
}

internal fun FontSmoothing.toSkFontEdging() = when (this) {
    FontSmoothing.None -> FontEdging.ALIAS
    FontSmoothing.AntiAlias -> FontEdging.ANTI_ALIAS
    FontSmoothing.SubpixelAntiAlias -> FontEdging.SUBPIXEL_ANTI_ALIAS
}

internal fun FontHinting.toSkFontHinting() = when (this) {
    FontHinting.None -> SkFontHinting.NONE
    FontHinting.Slight -> SkFontHinting.SLIGHT
    FontHinting.Normal -> SkFontHinting.NORMAL
    FontHinting.Full -> SkFontHinting.FULL
}

internal fun FontRasterizationSettings.toSkFontRastrSettings() = FontRastrSettings(
    edging = smoothing.toSkFontEdging(),
    hinting = hinting.toSkFontHinting(),
    subpixel = subpixelPositioning
    // rasterizationSettings.autoHintingForced is ignored here for now because it's not supported in skia
)