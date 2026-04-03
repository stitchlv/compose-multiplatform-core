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

package androidx.compose.material3

internal class PlatformDateFormat(locale: CalendarLocale) {
    val firstDayOfWeek : Int
        get() = TODO("Not yet implemented")

    /**
     * Localized by platform weekdays
     * */
    val weekdayNames: List<Pair<String, String>>
        get() = TODO("Not yet implemented")
    fun formatWithPattern(
        utcTimeMillis: Long,
        pattern: String,
    ): String = TODO("Not yet implemented")

    fun formatWithSkeleton(
        utcTimeMillis: Long,
        skeleton: String,
    ): String = TODO("Not yet implemented")

    fun parse(date: String, pattern: String): CalendarDate? = TODO("Not yet implemented")

    fun getDateInputFormat(): DateInputFormat = TODO("Not yet implemented")

    fun is24HourFormat() : Boolean = TODO("Not yet implemented")
}