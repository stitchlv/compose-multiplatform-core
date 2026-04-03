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

package androidx.compose.foundation.text
import com.bytedance.kmp.harko.skia.BreakIterator

private const val MIN_SUPPLEMENTARY_CODE_POINT: Int = 0x10000

internal actual fun StringBuilder.appendCodePointX(codePoint: Int): StringBuilder = apply {
    appendCodePoint(codePoint)
}

// Copy from https://github.com/JetBrains/kotlin/blob/7cd306950aad852e006715067435a4bbd9cd40d2/kotlin-native/runtime/src/main/kotlin/generated/_StringUppercase.kt#L26
private fun StringBuilder.appendCodePoint(codePoint: Int) {
    if (codePoint < MIN_SUPPLEMENTARY_CODE_POINT) {
        append(codePoint.toChar())
    } else {
        append(Char.MIN_HIGH_SURROGATE + ((codePoint - 0x10000) shr 10))
        append(Char.MIN_LOW_SURROGATE + (codePoint and 0x3ff))
    }
}

internal actual fun String.findPrecedingBreak(index: Int): Int {
    val it = BreakIterator.makeCharacterInstance()
    it.setText(this)
    return it.preceding(index)
}

internal actual fun String.findFollowingBreak(index: Int): Int {
    val it = BreakIterator.makeCharacterInstance()
    it.setText(this)
    return it.following(index)
}

// Copied from CharHelpers.skiko.kt
// TODO Remove once it's available in common stdlib https://youtrack.jetbrains.com/issue/KT-23251
internal typealias CodePoint = Int

// Copied from CharHelpers.skiko.kt
internal fun CodePoint.charCount(): Int = if (this >= MIN_SUPPLEMENTARY_CODE_POINT) 2 else 1

// Copied from CharHelpers.skiko.kt
/**
 * Returns the character (Unicode code point) at the specified index.
 */
internal fun CharSequence.codePointAt(index: Int): CodePoint {
    val high = this[index]
    if (high.isHighSurrogate() && index + 1 < this.length) {
        val low = this[index + 1]
        if (low.isLowSurrogate()) {
            return Char.toCodePoint(high, low)
        }
    }
    return high.code
}

/**
 * Returns the count of Unicode code points.
 */
internal fun CharSequence.codePointCount(): Int {
    var count = length
    var i = 0
    while (i < length - 1) {
        if (this[i].isHighSurrogate() && this[i + 1].isLowSurrogate()) {
            count--
            i += 2
        } else {
            i++
        }
    }
    return count
}
