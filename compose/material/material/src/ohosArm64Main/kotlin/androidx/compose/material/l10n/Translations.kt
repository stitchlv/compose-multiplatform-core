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

package androidx.compose.material.l10n

import androidx.compose.material.Translations

/**
 * Returns the translation for the given locale; `null` if there isn't one.
 */
internal fun translationFor(localeTag: String) = when(localeTag) {
    "" -> Translations.en()
    "en_AU" -> Translations.enAU()
    "en_CA" -> Translations.enCA()
    "en_GB" -> Translations.enGB()
    "en_IN" -> Translations.enIN()
    "zh_CN" -> Translations.zhCN()
    "zh_HK" -> Translations.zhHK()
    "zh_TW" -> Translations.zhTW()
    else -> null
}