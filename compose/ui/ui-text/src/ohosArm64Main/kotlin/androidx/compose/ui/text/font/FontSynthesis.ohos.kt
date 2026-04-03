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

package androidx.compose.ui.text.font

/**
 * Perform platform-specific font synthesis such as fake bold or fake italic.
 *
 * Platforms are not required to support synthesis, in which case they should return [typeface].
 *
 * Platforms that support synthesis should check [FontSynthesis.isWeightOn] and
 * [FontSynthesis.isStyleOn] in this method before synthesizing bold or italic, respectively.
 *
 * @param typeface a platform-specific typeface
 * @param font initial font that generated the typeface via loading
 * @param requestedWeight app-requested weight (may be different than the font's weight)
 * @param requestedStyle app-requested style (may be different than the font's style)
 * @return a synthesized typeface, or the passed [typeface] if synthesis is not needed or supported.
 */
internal actual fun FontSynthesis.synthesizeTypeface(
    typeface: Any,
    font: Font,
    requestedWeight: FontWeight,
    requestedStyle: FontStyle
): Any = typeface