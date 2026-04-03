/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.compose.ui.text.platform

import androidx.compose.ui.text.PlatformStringDelegate
import androidx.compose.ui.text.intl.PlatformLocale

internal class OhosStringDelegate : PlatformStringDelegate {
    override fun toUpperCase(string: String, locale: PlatformLocale): String {
        // @TODO 字符串转换需要考虑当前语言 locale
        return string.uppercase()
    }

    override fun toLowerCase(string: String, locale: PlatformLocale): String {
        // @TODO 字符串转换需要考虑当前语言 locale
        return string.lowercase()
    }

    override fun capitalize(string: String, locale: PlatformLocale): String =
        // @TODO 字符串转换需要考虑当前语言 locale
        string.replaceFirstChar {
            if (it.isLowerCase()) {
                it.titlecase()
            } else it.toString()
        }

    override fun decapitalize(string: String, locale: PlatformLocale): String =
        // @TODO 字符串转换需要考虑当前语言 locale
        string.replaceFirstChar { it.lowercase() }
}

internal actual fun ActualStringDelegate(): PlatformStringDelegate = OhosStringDelegate()