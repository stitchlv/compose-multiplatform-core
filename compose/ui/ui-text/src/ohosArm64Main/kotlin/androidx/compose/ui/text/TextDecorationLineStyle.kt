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

/**
 * Defines the style of the text decoration line (e.g. underline).
 *
 * The decoration itself is determined via [androidx.compose.ui.text.style.TextDecoration].
 */
@ExperimentalTextApi
value class TextDecorationLineStyle internal constructor(val value: Int) {

    override fun toString(): String {
        return when (this) {
            Solid -> "Solid"
            Double -> "Double"
            Dotted -> "Dotted"
            Dashed -> "Dashed"
            Wavy -> "Wavy"
            else -> "Invalid"
        }
    }

    companion object {
        /**
         * Solid line.
         */
        val Solid = TextDecorationLineStyle(1)

        /**
         * Double line.
         */
        val Double = TextDecorationLineStyle(2)

        /**
         * Dotted line.
         */
        val Dotted = TextDecorationLineStyle(3)

        /**
         * Dashed line.
         */
        val Dashed = TextDecorationLineStyle(4)

        /**
         * Wavy line.
         */
        val Wavy = TextDecorationLineStyle(5)
    }

}