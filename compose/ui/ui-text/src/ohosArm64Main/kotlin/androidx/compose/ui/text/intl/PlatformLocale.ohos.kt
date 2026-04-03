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

package androidx.compose.ui.text.intl

//import org.jetbrains.skiko.XComponentsContext

internal actual fun createPlatformLocaleDelegate(): PlatformLocaleDelegate =
    object : PlatformLocaleDelegate {
        override val current: LocaleList = LocaleList(Locale(OhosLocale("zh", "", "CN", "zhCN")))
//            get() {
//                val language = XComponentsContext.language.value
//                val region = XComponentsContext.region.value
//                return LocaleList(Locale(OhosLocale(language, "", region, "$language-$region")))
//            }

        override fun parseLanguageTag(languageTag: String): PlatformLocale {
            val l2r = languageTag.split("-")
            if (l2r.size != 2) {
                error("IllegalState languageTag: $languageTag")
            }
            val language = l2r.first()
            val region = l2r[1]
            return OhosLocale(language, "", region, languageTag)
        }
    }

// todo 默认不支持rtl，后面根据情况添加
internal fun PlatformLocale.isRtl(): Boolean = false